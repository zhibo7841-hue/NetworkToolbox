package com.networktoolbox.feature.report.diagnostic.v2.orchestration

import com.networktoolbox.core.common.diagnostic.DiagnosticCheckCode
import com.networktoolbox.core.common.diagnostic.DiagnosticCheckStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticDnsOutcome
import com.networktoolbox.core.common.diagnostic.DiagnosticIntent
import com.networktoolbox.core.common.diagnostic.DiagnosticObservationCode
import com.networktoolbox.core.common.diagnostic.DiagnosticObservationState
import com.networktoolbox.core.common.diagnostic.DiagnosticRunStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticStage
import com.networktoolbox.core.common.diagnostic.DiagnosticTarget
import com.networktoolbox.core.common.diagnostic.DiagnosticTargetKind
import com.networktoolbox.core.common.diagnostic.DiagnosticTcpOutcome
import com.networktoolbox.core.network.dns.DnsLookupRequest
import com.networktoolbox.core.network.dns.DnsLookupResult
import com.networktoolbox.core.network.dns.DnsLookupStatus
import com.networktoolbox.core.network.dns.DnsQueryEngine
import com.networktoolbox.core.network.dns.DnsQueryMethod
import com.networktoolbox.core.network.dns.DnsRecord
import com.networktoolbox.core.network.dns.DnsRecordType
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.network.ping.PingMethod
import com.networktoolbox.core.network.ping.PingMode
import com.networktoolbox.core.network.ping.PingProtocol
import com.networktoolbox.core.network.ping.PingRequest
import com.networktoolbox.core.network.ping.PingSessionEngine
import com.networktoolbox.core.network.ping.PingSessionProgress
import com.networktoolbox.core.network.ping.PingSessionResult
import com.networktoolbox.core.network.ping.PingQualityLevel
import com.networktoolbox.core.network.repository.NetworkRepository
import com.networktoolbox.core.network.tcp.TcpPortChecker
import com.networktoolbox.core.network.tcp.TcpProbeResult
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticProbeTarget
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticProbeTargets
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticOrchestratorTest {
    @Test
    fun normalWifi_runsStagesAndProducesEvidenceOnly() = runTest {
        val dependencies = dependencies()

        val result = dependencies.orchestrator.run()

        assertEquals(DiagnosticRunStatus.COMPLETED, result.runStatus)
        assertEquals(DiagnosticCheckStatus.PASS, result.check(DiagnosticCheckCode.NETWORK_STATE).status)
        assertEquals(DiagnosticCheckStatus.PASS, result.check(DiagnosticCheckCode.GATEWAY).status)
        assertEquals(DiagnosticCheckStatus.PASS, result.checksFor(DiagnosticCheckCode.PUBLIC_CONNECTIVITY).first().status)
        assertEquals(DiagnosticCheckStatus.PASS, result.check(DiagnosticCheckCode.DNS_RESOLUTION).status)
        assertEquals(DiagnosticCheckStatus.SKIPPED, result.check(DiagnosticCheckCode.TARGET_CONNECTIVITY).status)
        assertTrue(result.observations.any { it.code == DiagnosticObservationCode.LOCAL_ADDRESS })
        assertTrue(result.checks.none { it.code.name.contains("FINDING") })
    }

    @Test
    fun cellularDoesNotProbeTraditionalGateway() = runTest {
        val ping = RecordingPingSessionEngine()
        val dependencies = dependencies(context = context(connectionType = ConnectionType.CELLULAR, gateway = "10.0.0.1"), ping = ping)

        val result = dependencies.orchestrator.run()

        assertEquals(DiagnosticCheckStatus.NOT_APPLICABLE, result.check(DiagnosticCheckCode.GATEWAY).status)
        assertTrue(ping.requests.isEmpty())
    }

    @Test
    fun ipv6OnlyLinkLocalGatewayIsUnknownWithoutScope() = runTest {
        val ping = RecordingPingSessionEngine()
        val dependencies = dependencies(
            context = context(ipv4 = null, ipv6 = "2001:db8::10", gateway = "fe80::1"),
            ping = ping,
        )

        val result = dependencies.orchestrator.run()

        assertEquals(DiagnosticCheckStatus.UNKNOWN, result.check(DiagnosticCheckCode.GATEWAY).status)
        assertEquals(0, ping.requests.size)
    }

    @Test
    fun noActiveNetworkSkipsDependentStagesWithoutCallingEngines() = runTest {
        val ping = RecordingPingSessionEngine()
        val tcp = RecordingTcpPortChecker()
        val dns = RecordingDnsQueryEngine()
        val dependencies = dependencies(
            context = NetworkContext.noActiveNetwork(),
            ping = ping,
            tcp = tcp,
            dns = dns,
        )

        val result = dependencies.orchestrator.run()

        assertEquals(DiagnosticRunStatus.COMPLETED, result.runStatus)
        assertEquals(DiagnosticCheckStatus.FAIL, result.check(DiagnosticCheckCode.NETWORK_STATE).status)
        assertTrue(result.checks.filter { it.code != DiagnosticCheckCode.NETWORK_STATE }.all { it.status == DiagnosticCheckStatus.SKIPPED })
        assertTrue(ping.requests.isEmpty())
        assertTrue(tcp.calls.isEmpty())
        assertTrue(dns.requests.isEmpty())
    }

    @Test
    fun unavailableNetworkReadIsUnknownAndNotNoNetwork() = runTest {
        val dependencies = dependencies(repository = ThrowingNetworkRepository())

        val result = dependencies.orchestrator.run()

        assertEquals(DiagnosticRunStatus.COMPLETED, result.runStatus)
        assertEquals(DiagnosticCheckStatus.UNKNOWN, result.check(DiagnosticCheckCode.NETWORK_STATE).status)
        assertTrue(result.observations.any {
            it.code == DiagnosticObservationCode.ACTIVE_NETWORK_AVAILABLE &&
                it.evidenceState == DiagnosticObservationState.UNAVAILABLE
        })
    }

    @Test
    fun gatewaySuccessUsesFiniteExistingDiagnosticPingParameters() = runTest {
        val ping = RecordingPingSessionEngine()
        val dependencies = dependencies(ping = ping)

        dependencies.orchestrator.run()

        val request = ping.requests.single()
        assertEquals("192.0.2.1", request.target)
        assertEquals(PingMode.CONTINUOUS, request.mode)
        assertEquals(3, request.count)
        assertEquals(100, request.intervalMs)
        assertEquals(2_000, request.timeoutMs)
    }

    @Test
    fun gatewayTimeoutRemainsEvidenceAndDoesNotCreateFinding() = runTest {
        val dependencies = dependencies(ping = RecordingPingSessionEngine(pingResult(false)))

        val result = dependencies.orchestrator.run()

        assertEquals(DiagnosticCheckStatus.FAIL, result.check(DiagnosticCheckCode.GATEWAY).status)
        assertTrue(result.checks.none { it.code.name.contains("FINDING") })
    }

    @Test
    fun gatewayPingFailureWithPublicTcpSuccessDoesNotMarkPublicStageFailed() = runTest {
        val dependencies = dependencies(ping = RecordingPingSessionEngine(pingResult(false)))

        val result = dependencies.orchestrator.run()

        assertEquals(DiagnosticCheckStatus.FAIL, result.check(DiagnosticCheckCode.GATEWAY).status)
        assertTrue(result.checksFor(DiagnosticCheckCode.PUBLIC_CONNECTIVITY).all { it.status == DiagnosticCheckStatus.PASS })
    }

    @Test
    fun publicTargetsAreIndependentAndOneSuccessKeepsEachResultTyped() = runTest {
        val tcp = RecordingTcpPortChecker { host, _, _ ->
            if (host == "223.5.5.5") tcpResult(host, outcome = DiagnosticTcpOutcome.CONNECT_SUCCESS)
            else tcpResult(host, outcome = DiagnosticTcpOutcome.TIMEOUT)
        }
        val dependencies = dependencies(tcp = tcp)

        val result = dependencies.orchestrator.run()
        val publicChecks = result.checksFor(DiagnosticCheckCode.PUBLIC_CONNECTIVITY)

        assertEquals(2, publicChecks.size)
        assertEquals(DiagnosticCheckStatus.PASS, publicChecks[0].status)
        assertEquals(DiagnosticCheckStatus.FAIL, publicChecks[1].status)
        assertEquals(2, result.observations.count { it.code == DiagnosticObservationCode.PUBLIC_TCP_OUTCOME })
    }

    @Test
    fun publicRefusedIsPositivePathEvidenceWhileTimeoutIsFailureEvidence() = runTest {
        val tcp = RecordingTcpPortChecker { host, _, _ -> tcpResult(host, outcome = if (host == "223.5.5.5") {
            DiagnosticTcpOutcome.CONNECTION_REFUSED
        } else {
            DiagnosticTcpOutcome.TIMEOUT
        }) }
        val result = dependencies(tcp = tcp).orchestrator.run()

        val checks = result.checksFor(DiagnosticCheckCode.PUBLIC_CONNECTIVITY)
        assertEquals(DiagnosticCheckStatus.PASS, checks[0].status)
        assertEquals(DiagnosticCheckStatus.FAIL, checks[1].status)
    }

    @Test
    fun allPublicTimeoutsRemainStageEvidenceWithoutOverallVerdict() = runTest {
        val result = dependencies(
            tcp = RecordingTcpPortChecker { host, _, _ -> tcpResult(host, outcome = DiagnosticTcpOutcome.TIMEOUT) },
        ).orchestrator.run()

        assertTrue(result.checksFor(DiagnosticCheckCode.PUBLIC_CONNECTIVITY).all { it.status == DiagnosticCheckStatus.FAIL })
        assertEquals(DiagnosticRunStatus.COMPLETED, result.runStatus)
    }

    @Test
    fun validatedIsRecordedAsContextAndDoesNotOverrideProbeEvidence() = runTest {
        val result = dependencies(
            context = context(validated = true),
            tcp = RecordingTcpPortChecker { host, _, _ -> tcpResult(host, outcome = DiagnosticTcpOutcome.TIMEOUT) },
        ).orchestrator.run()

        assertEquals(DiagnosticCheckStatus.FAIL, result.checksFor(DiagnosticCheckCode.PUBLIC_CONNECTIVITY).first().status)
        assertTrue(result.observations.any { it.code == DiagnosticObservationCode.VALIDATED_NETWORK })
    }

    @Test
    fun captivePortalIsMappedAsObservationOnly() = runTest {
        val result = dependencies(context = context(captivePortal = true)).orchestrator.run()

        assertTrue(result.observations.any { it.code == DiagnosticObservationCode.CAPTIVE_PORTAL })
        assertTrue(result.checks.none { it.code.name.contains("FINDING") })
    }

    @Test
    fun partialConnectivityCapabilityStaysUnknownWhenPlatformDoesNotExposeIt() = runTest {
        val result = dependencies(context = context(partialConnectivity = null)).orchestrator.run()

        val observation = result.observations.first { it.code == DiagnosticObservationCode.PARTIAL_CONNECTIVITY }
        assertEquals(DiagnosticObservationState.UNKNOWN, observation.evidenceState)
    }

    @Test
    fun aSuccessAndAaaaNoRecordsIsDnsSuccess() = runTest {
        val result = dependencies(dns = RecordingDnsQueryEngine(responses = listOf(dnsResult(
            status = DnsLookupStatus.SUCCESS,
            records = listOf(DnsRecord(DnsRecordType.A, "93.184.216.34")),
        )))).orchestrator.run()

        assertEquals(DiagnosticCheckStatus.PASS, result.check(DiagnosticCheckCode.DNS_RESOLUTION).status)
        assertEquals(DiagnosticDnsOutcome.SUCCESS, result.dnsOutcomes().single())
    }

    @Test
    fun nxdomainIsDistinctFromNoRecords() = runTest {
        val result = dependencies(dns = RecordingDnsQueryEngine(responses = listOf(dnsResult(status = DnsLookupStatus.NXDOMAIN, records = emptyList())))).orchestrator.run()

        assertEquals(DiagnosticCheckStatus.FAIL, result.check(DiagnosticCheckCode.DNS_RESOLUTION).status)
        assertEquals(DiagnosticDnsOutcome.NXDOMAIN, result.dnsOutcomes().single())
    }

    @Test
    fun dnsTimeoutIsFailureEvidence() = runTest {
        val result = dependencies(dns = RecordingDnsQueryEngine(responses = listOf(dnsResult(status = DnsLookupStatus.TIMEOUT, records = emptyList())))).orchestrator.run()

        assertEquals(DiagnosticCheckStatus.FAIL, result.check(DiagnosticCheckCode.DNS_RESOLUTION).status)
        assertEquals(DiagnosticDnsOutcome.TIMEOUT, result.dnsOutcomes().single())
    }

    @Test
    fun dnsPartialIsPreservedAsTypedEvidence() = runTest {
        val result = dependencies(dns = RecordingDnsQueryEngine(responses = listOf(dnsResult(
            status = DnsLookupStatus.PARTIAL,
            records = listOf(DnsRecord(DnsRecordType.A, "93.184.216.34")),
        )))).orchestrator.run()

        assertEquals(DiagnosticDnsOutcome.PARTIAL, result.dnsOutcomes().single())
        assertEquals(DiagnosticCheckStatus.FAIL, result.check(DiagnosticCheckCode.DNS_RESOLUTION).status)
    }

    @Test
    fun fakeIpIsRecordedWithoutChangingDnsCheckStatus() = runTest {
        val result = dependencies(dns = RecordingDnsQueryEngine(responses = listOf(dnsResult(
            records = listOf(DnsRecord(DnsRecordType.A, "198.18.0.1")),
        )))).orchestrator.run()

        assertEquals(DiagnosticCheckStatus.PASS, result.check(DiagnosticCheckCode.DNS_RESOLUTION).status)
        assertTrue(result.observations.any { it.code == DiagnosticObservationCode.FAKE_IP_RANGE_MATCH })
    }

    @Test
    fun vpnIsRecordedAsContextOnly() = runTest {
        val result = dependencies(context = context(vpnActive = true)).orchestrator.run()

        assertTrue(result.observations.any { it.code == DiagnosticObservationCode.VPN_ACTIVE })
        assertTrue(result.checks.none { it.code.name.contains("FINDING") })
    }

    @Test
    fun domainTargetUsesDnsV2ThenChecksReturnedAddresses() = runTest {
        val dns = RecordingDnsQueryEngine(
            responses = listOf(
                dnsResult(),
                dnsResult(records = listOf(DnsRecord(DnsRecordType.A, "203.0.113.10"), DnsRecord(DnsRecordType.AAAA, "2001:db8::10"))),
            ),
        )
        val tcp = RecordingTcpPortChecker()
        val intent = DiagnosticIntent(target = DiagnosticTarget("service.example", DiagnosticTargetKind.DOMAIN))

        val result = dependencies(dns = dns, tcp = tcp).orchestrator.run(intent)

        assertEquals(2, dns.requests.size)
        assertEquals(setOf(DnsRecordType.A, DnsRecordType.AAAA), dns.requests[1].recordTypes)
        assertEquals(listOf("203.0.113.10", "2001:db8::10"), tcp.calls.drop(2).map { it.host })
        assertTrue(result.checksFor(DiagnosticCheckCode.TARGET_CONNECTIVITY).any { it.target?.value == "203.0.113.10" })
    }

    @Test
    fun directIpTargetUsesTcpWithoutExtraDnsLookup() = runTest {
        val dns = RecordingDnsQueryEngine()
        val tcp = RecordingTcpPortChecker()
        val intent = DiagnosticIntent(target = DiagnosticTarget("203.0.113.20", DiagnosticTargetKind.IPV4))

        val result = dependencies(dns = dns, tcp = tcp).orchestrator.run(intent)

        assertEquals(1, dns.requests.size)
        assertEquals("203.0.113.20", tcp.calls.last().host)
        assertEquals(DiagnosticCheckStatus.PASS, result.checksFor(DiagnosticCheckCode.TARGET_CONNECTIVITY).last().status)
    }

    @Test
    fun targetRefusedAndTimeoutRemainTypedTargetOutcomes() = runTest {
        val tcp = RecordingTcpPortChecker { host, _, _ ->
            tcpResult(host, outcome = if (host == "203.0.113.20") DiagnosticTcpOutcome.CONNECTION_REFUSED else DiagnosticTcpOutcome.TIMEOUT)
        }
        val intent = DiagnosticIntent(target = DiagnosticTarget("203.0.113.20", DiagnosticTargetKind.IPV4))

        val result = dependencies(tcp = tcp).orchestrator.run(intent)

        assertEquals(DiagnosticCheckStatus.FAIL, result.checksFor(DiagnosticCheckCode.TARGET_CONNECTIVITY).last().status)
        assertEquals(DiagnosticTcpOutcome.CONNECTION_REFUSED, result.tcpOutcomes().last())
    }

    @Test
    fun noTargetIsSkippedAndDoesNotRunTracerouteOrLanStages() = runTest {
        val result = dependencies().orchestrator.run(DiagnosticIntent())

        assertEquals(DiagnosticCheckStatus.SKIPPED, result.check(DiagnosticCheckCode.TARGET_CONNECTIVITY).status)
        assertTrue(result.checks.none { it.stage == DiagnosticStage.ADVANCED_PATH })
    }

    @Test
    fun fingerprintChangeStopsFutureStages() = runTest {
        val first = context()
        val changed = context(connectionType = ConnectionType.CELLULAR, ipv4 = "198.51.100.20")
        val ping = RecordingPingSessionEngine()
        val tcp = RecordingTcpPortChecker()
        val dns = RecordingDnsQueryEngine()
        val repository = SequenceNetworkRepository(listOf(first, changed))

        val result = dependencies(repository = repository, ping = ping, tcp = tcp, dns = dns).orchestrator.run()

        assertEquals(DiagnosticRunStatus.NETWORK_CHANGED, result.runStatus)
        assertTrue(result.observations.any { it.code == DiagnosticObservationCode.NETWORK_CHANGED })
        assertTrue(ping.requests.isEmpty())
        assertTrue(tcp.calls.isEmpty())
        assertTrue(dns.requests.isEmpty())
    }

    @Test
    fun cancellationReturnsCancelledEvidenceAndDoesNotContinue() = runTest {
        val tcp = RecordingTcpPortChecker { _, _, _ -> throw CancellationException("cancelled") }

        val result = dependencies(tcp = tcp).orchestrator.run()

        assertEquals(DiagnosticRunStatus.CANCELLED, result.runStatus)
        assertEquals(1, tcp.calls.size)
    }

    @Test
    fun adapterExceptionBecomesUnknownStageEvidence() = runTest {
        val result = dependencies(
            tcp = RecordingTcpPortChecker { _, _, _ -> error("adapter unavailable") },
        ).orchestrator.run()

        assertTrue(result.checksFor(DiagnosticCheckCode.PUBLIC_CONNECTIVITY).all { it.status == DiagnosticCheckStatus.UNKNOWN })
    }

    @Test
    fun dnsAdapterExceptionBecomesUnknownWithoutAbortingRun() = runTest {
        val dns = object : DnsQueryEngine {
            override suspend fun lookup(request: DnsLookupRequest): DnsLookupResult =
                error("DNS adapter unavailable")
        }

        val result = dependencies(dns = dns).orchestrator.run()

        assertEquals(DiagnosticCheckStatus.UNKNOWN, result.check(DiagnosticCheckCode.DNS_RESOLUTION).status)
        assertEquals(DiagnosticRunStatus.COMPLETED, result.runStatus)
    }

    @Test
    fun networkChangeAfterGatewayStopsPublicAndLaterStages() = runTest {
        val first = context()
        val changed = context(ipv4 = "198.51.100.20", connectionType = ConnectionType.CELLULAR)
        val ping = RecordingPingSessionEngine()
        val tcp = RecordingTcpPortChecker()
        val repository = SequenceNetworkRepository(listOf(first, first, first, changed))

        val result = dependencies(repository = repository, ping = ping, tcp = tcp).orchestrator.run()

        assertEquals(DiagnosticRunStatus.NETWORK_CHANGED, result.runStatus)
        assertEquals(1, ping.requests.size)
        assertTrue(tcp.calls.isEmpty())
    }

    @Test
    fun stageProgressUsesRealStageStatesAndNoSyntheticStages() = runTest {
        val progress = mutableListOf<DiagnosticStageProgress>()

        dependencies().orchestrator.run(onProgress = progress::add)

        assertEquals(DiagnosticStageState.RUNNING, progress.first { it.stage == DiagnosticStage.NETWORK_STATE }.state)
        assertEquals(DiagnosticStageState.COMPLETED, progress.last { it.stage == DiagnosticStage.GATEWAY }.state)
        assertTrue(progress.none { it.stage == DiagnosticStage.ADVANCED_PATH })
    }

    @Test
    fun evidenceMetadataIsBoundedToOneRunAndHasNoHistorySideEffect() = runTest {
        val result = dependencies().orchestrator.run()

        assertEquals(10_000L, result.startedAt)
        assertEquals(10_000L, result.finishedAt)
        assertEquals(0L, result.durationMs)
        assertNull(result.intent.target)
        assertTrue(result.checks.isNotEmpty())
    }

    private fun dependencies(
        context: NetworkContext = context(),
        repository: NetworkRepository = SequenceNetworkRepository(List(8) { context }),
        ping: PingSessionEngine = RecordingPingSessionEngine(),
        dns: DnsQueryEngine = RecordingDnsQueryEngine(),
        tcp: TcpPortChecker = RecordingTcpPortChecker(),
    ): Dependencies {
        val targets = DiagnosticProbeTargets(
            publicTargets = listOf(DiagnosticProbeTarget("223.5.5.5"), DiagnosticProbeTarget("1.1.1.1")),
            domainName = "example.com",
        )
        return Dependencies(
            orchestrator = DefaultDiagnosticOrchestrator(
                networkRepository = repository,
                pingSessionEngine = ping,
                dnsQueryEngine = dns,
                tcpPortChecker = tcp,
                probeTargets = targets,
                now = { 10_000L },
            ),
        )
    }

    private fun context(
        connectionType: ConnectionType = ConnectionType.WIFI,
        ipv4: String? = "192.0.2.20",
        ipv6: String? = null,
        gateway: String? = "192.0.2.1",
        validated: Boolean? = true,
        vpnActive: Boolean? = false,
        captivePortal: Boolean? = false,
        partialConnectivity: Boolean? = null,
    ) = NetworkContext(
        connectionType = connectionType,
        ipv4Address = ipv4,
        ipv6Address = ipv6,
        gateway = gateway,
        dnsServers = listOf("192.0.2.1"),
        vpnActive = vpnActive,
        wifiName = "Test Wi-Fi",
        wifiSignalLevel = 3,
        activeNetworkAvailable = true,
        validated = validated,
        ipv6Addresses = listOfNotNull(ipv6),
        ipv4PrefixLength = 24,
        interfaceName = "wlan0",
        privateDnsActive = false,
        captivePortal = captivePortal,
        partialConnectivity = partialConnectivity,
    )

    private fun dnsResult(
        status: DnsLookupStatus = DnsLookupStatus.SUCCESS,
        records: List<DnsRecord> = listOf(
            DnsRecord(DnsRecordType.A, "93.184.216.34"),
            DnsRecord(DnsRecordType.AAAA, "2001:db8::34"),
        ),
    ) = DnsLookupResult(
        queryName = "example.com",
        requestedTypes = setOf(DnsRecordType.A, DnsRecordType.AAAA),
        records = records,
        server = null,
        method = DnsQueryMethod.ANDROID_DNS_RESOLVER,
        status = status,
        durationMs = 10L,
        startTime = 10_000L,
        endTime = 10_000L,
        errorMessage = if (status == DnsLookupStatus.SUCCESS) null else "DNS failure",
    )

    private fun pingResult(success: Boolean) = PingSessionResult(
        target = "192.0.2.1",
        address = "192.0.2.1",
        protocol = PingProtocol.AUTO,
        mode = PingMode.CONTINUOUS,
        startTime = 10_000L,
        endTime = 10_000L,
        sentPackets = 3,
        receivedPackets = if (success) 3 else 0,
        lostPackets = if (success) 0 else 3,
        packetLoss = if (success) 0.0 else 1.0,
        minLatencyMs = if (success) 1 else null,
        avgLatencyMs = if (success) 1.0 else null,
        maxLatencyMs = if (success) 1 else null,
        jitterMs = if (success) 0.0 else null,
        qualityLevel = if (success) PingQualityLevel.EXCELLENT else PingQualityLevel.POOR,
        summary = if (success) "ok" else "timeout",
        method = PingMethod.SYSTEM_REACHABILITY,
        errorMessage = if (success) null else "Timeout",
    )

    private fun tcpResult(host: String, outcome: DiagnosticTcpOutcome) = TcpProbeResult(
        host = host,
        port = 443,
        success = outcome == DiagnosticTcpOutcome.CONNECT_SUCCESS,
        latencyMs = if (outcome == DiagnosticTcpOutcome.CONNECT_SUCCESS) 5L else null,
        errorMessage = if (outcome == DiagnosticTcpOutcome.CONNECT_SUCCESS) null else outcome.name,
        outcome = outcome,
    )

    private data class Dependencies(val orchestrator: DiagnosticOrchestrator)

    private fun DiagnosticRunEvidence.check(code: DiagnosticCheckCode) = checks.first { it.code == code }
    private fun DiagnosticRunEvidence.checksFor(code: DiagnosticCheckCode) = checks.filter { it.code == code }
    private fun DiagnosticRunEvidence.dnsOutcomes() = observations.filter { it.code == DiagnosticObservationCode.DNS_OUTCOME }
        .map { (it.value as com.networktoolbox.core.common.diagnostic.DiagnosticObservationValue.DnsOutcomeValue).outcome }
    private fun DiagnosticRunEvidence.tcpOutcomes() = observations.filter { it.code == DiagnosticObservationCode.TARGET_TCP_OUTCOME }
        .map { (it.value as com.networktoolbox.core.common.diagnostic.DiagnosticObservationValue.TcpOutcomeValue).outcome }

    private class SequenceNetworkRepository(private val contexts: List<NetworkContext>) : NetworkRepository {
        private var index = 0
        override fun observeNetworkContext(): Flow<NetworkContext> = flow {
            emit(contexts.getOrElse(index++) { contexts.last() })
        }
    }

    private class ThrowingNetworkRepository : NetworkRepository {
        override fun observeNetworkContext(): Flow<NetworkContext> = flow { error("network read unavailable") }
    }

    private class RecordingPingSessionEngine(
        private val response: PingSessionResult = pingResultStatic(true),
    ) : PingSessionEngine {
        val requests = mutableListOf<PingRequest>()
        override suspend fun run(request: PingRequest, onProgress: (PingSessionProgress) -> Unit): PingSessionResult {
            requests += request
            return response.copy(target = request.target)
        }
    }

    private class RecordingDnsQueryEngine(
        private val responses: List<DnsLookupResult> = listOf(dnsResultStatic()),
    ) : DnsQueryEngine {
        val requests = mutableListOf<DnsLookupRequest>()
        private var index = 0
        override suspend fun lookup(request: DnsLookupRequest): DnsLookupResult {
            requests += request
            return responses.getOrElse(index++) { responses.last() }.copy(queryName = request.queryName)
        }
    }

    private class RecordingTcpPortChecker(
        private val action: (String, Int, Int) -> TcpProbeResult = { host, _, _ -> tcpResultStatic(host, DiagnosticTcpOutcome.CONNECT_SUCCESS) },
    ) : TcpPortChecker {
        data class Call(val host: String, val port: Int, val timeoutMs: Int)
        val calls = mutableListOf<Call>()
        override suspend fun check(host: String, port: Int, timeoutMs: Int): TcpProbeResult {
            calls += Call(host, port, timeoutMs)
            return action(host, port, timeoutMs)
        }
    }

    companion object {
        private fun dnsResultStatic() = DnsLookupResult(
            queryName = "example.com",
            requestedTypes = setOf(DnsRecordType.A, DnsRecordType.AAAA),
            records = listOf(DnsRecord(DnsRecordType.A, "93.184.216.34"), DnsRecord(DnsRecordType.AAAA, "2001:db8::34")),
            server = null,
            method = DnsQueryMethod.ANDROID_DNS_RESOLVER,
            status = DnsLookupStatus.SUCCESS,
            durationMs = 1L,
            startTime = 10_000L,
            endTime = 10_000L,
            errorMessage = null,
        )

        private fun pingResultStatic(success: Boolean) = PingSessionResult(
            target = "192.0.2.1",
            address = "192.0.2.1",
            protocol = PingProtocol.AUTO,
            mode = PingMode.CONTINUOUS,
            startTime = 10_000L,
            endTime = 10_000L,
            sentPackets = 3,
            receivedPackets = if (success) 3 else 0,
            lostPackets = if (success) 0 else 3,
            packetLoss = if (success) 0.0 else 1.0,
            minLatencyMs = if (success) 1 else null,
            avgLatencyMs = if (success) 1.0 else null,
            maxLatencyMs = if (success) 1 else null,
            jitterMs = if (success) 0.0 else null,
            qualityLevel = if (success) PingQualityLevel.EXCELLENT else PingQualityLevel.POOR,
            summary = if (success) "ok" else "timeout",
            method = PingMethod.SYSTEM_REACHABILITY,
            errorMessage = if (success) null else "Timeout",
        )

        private fun tcpResultStatic(host: String, outcome: DiagnosticTcpOutcome) = TcpProbeResult(
            host = host,
            port = 443,
            success = outcome == DiagnosticTcpOutcome.CONNECT_SUCCESS,
            latencyMs = if (outcome == DiagnosticTcpOutcome.CONNECT_SUCCESS) 5L else null,
            errorMessage = if (outcome == DiagnosticTcpOutcome.CONNECT_SUCCESS) null else outcome.name,
            outcome = outcome,
        )
    }
}
