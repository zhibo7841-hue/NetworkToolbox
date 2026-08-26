package com.networktoolbox.feature.report.diagnostic.v2

import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryRecorder
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
import com.networktoolbox.feature.report.domain.RunDiagnosticV2UseCase
import java.util.concurrent.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticV2CoreTest {
    @Test
    fun allStagesSuccessful_producesHealthyReportAndOneHistoryRecord() = runTest {
        val records = mutableListOf<HistoryRecord>()
        val pipeline = pipeline()
        val report = RunDiagnosticV2UseCase(
            pipeline = pipeline,
            analyzer = DefaultDiagnosticAnalyzerV2(),
            historyRecorder = HistoryRecorder { records += it },
        )()

        assertEquals(DiagnosticOverallStatus.HEALTHY, report.overallStatus)
        assertEquals(DiagnosticSeverity.HEALTHY, report.overallSeverity)
        assertTrue(report.findings.isEmpty())
        assertEquals(1, records.size)
        assertTrue(records.single().detailJson.contains("\"schemaVersion\":2"))
        assertTrue(records.single().detailJson.contains("\"checks\""))
    }

    @Test
    fun noActiveNetwork_isErrorAndDependentStagesAreSkipped() = runTest {
        val context = NetworkContext.noActiveNetwork()
        val result = pipeline(context = context).run {}
        val report = DefaultDiagnosticAnalyzerV2().analyze(result)

        assertEquals(DiagnosticOverallStatus.LIMITED, report.overallStatus)
        assertTrue(report.findings.any { it.id == "NO_ACTIVE_NETWORK" })
        assertTrue(result.checks.filter { it.id != "NETWORK_CONTEXT" }
            .all { it.status == DiagnosticCheckStatus.SKIPPED })
    }

    @Test
    fun gatewayOkAndPublicFail_reportsUpstreamPossibility() = runTest {
        val result = pipeline(
            context = networkContext(validated = false),
            tcpResponse = { host, _, _ -> tcpResult(host, success = false) },
        ).run {}
        val report = DefaultDiagnosticAnalyzerV2().analyze(result)

        assertTrue(report.findings.any { it.id == "PUBLIC_CONNECTIVITY_FAILED" })
        assertEquals(DiagnosticSeverity.WARNING, report.overallSeverity)
    }

    @Test
    fun cellularGateway_isNotApplicable_evenWhenSystemReportsGateway() = runTest {
        val ping = FakePingSessionEngine(pingResult(success = false))
        val context = networkContext(
            connectionType = ConnectionType.CELLULAR,
            gateway = "10.150.195.68",
        )

        val result = pipeline(context = context, pingEngine = ping).run {}
        val gateway = result.checks.first { it.id == "GATEWAY_REACHABILITY" }

        assertEquals(DiagnosticCheckStatus.NOT_APPLICABLE, gateway.status)
        assertEquals(DiagnosticSeverity.NOTICE, gateway.severity)
        assertEquals("10.150.195.68", gateway.target)
        assertEquals(0, ping.callCount)
    }

    @Test
    fun wifiGatewayFailureWithDomainAccess_downgradesGatewayToUnknown() = runTest {
        val result = pipeline(
            gatewayResult = pingResult(success = false),
        ).run {}
        val report = DefaultDiagnosticAnalyzerV2().analyze(result)
        val gateway = result.checks.first { it.id == "GATEWAY_REACHABILITY" }

        assertEquals(DiagnosticCheckStatus.UNKNOWN, gateway.status)
        assertEquals(DiagnosticSeverity.NOTICE, gateway.severity)
        assertTrue(report.overallSeverity != DiagnosticSeverity.WARNING)
        assertTrue(report.findings.any { it.id == "PING_TCP_DIFFERENCE" })
    }

    @Test
    fun fixedTargetsFailButDomainAccessSucceeds_marksPublicAsPassWithNotice() = runTest {
        val result = pipeline(
            tcpResponse = { host, port, _ ->
                tcpResult(host, port, success = host == "93.184.216.34")
            },
        ).run {}
        val report = DefaultDiagnosticAnalyzerV2().analyze(result)
        val public = result.checks.first { it.id == "PUBLIC_CONNECTIVITY" }

        assertEquals(DiagnosticCheckStatus.PASS, public.status)
        assertEquals("resolved_domain_tcp", public.rawData["effectiveEvidence"])
        assertTrue(report.findings.any { it.id == "FIXED_PUBLIC_TARGETS_INCONCLUSIVE" })
        assertTrue(report.recommendations.isEmpty())
        assertEquals(DiagnosticOverallStatus.HEALTHY, report.overallStatus)
    }

    @Test
    fun defaultPublicTargets_useReviewedDomesticAndGlobalEndpoints() {
        assertEquals(
            listOf("223.5.5.5", "1.1.1.1"),
            DiagnosticProbeTargets.default().publicTargets.map { it.host },
        )
    }

    @Test
    fun validatedConflict_keepsPublicUnknownInsteadOfFailure() = runTest {
        val result = pipeline(
            tcpResponse = { host, port, _ -> tcpResult(host, port, success = false) },
        ).run {}
        val report = DefaultDiagnosticAnalyzerV2().analyze(result)
        val public = result.checks.first { it.id == "PUBLIC_CONNECTIVITY" }

        assertEquals(DiagnosticCheckStatus.UNKNOWN, public.status)
        assertEquals(DiagnosticSeverity.NOTICE, public.severity)
        assertEquals(DiagnosticOverallStatus.UNKNOWN, report.overallStatus)
        assertTrue(report.findings.any { it.id == "PUBLIC_CONNECTIVITY_UNCERTAIN" })
        assertFalse(report.findings.any { it.id == "PUBLIC_CONNECTIVITY_FAILED" })
    }

    @Test
    fun publicOkAndDnsFailure_reportsDnsFinding() = runTest {
        val result = pipeline(
            dnsResult = dnsResult(status = DnsLookupStatus.TIMEOUT),
        ).run {}
        val report = DefaultDiagnosticAnalyzerV2().analyze(result)

        assertTrue(report.findings.any { it.id == "DNS_FAILURE" })
        assertEquals(DiagnosticSeverity.ERROR, report.overallSeverity)
        assertTrue(report.summary.contains("DNS"))
    }

    @Test
    fun partialDnsFailure_isWarningEvenWhenARecordExists() = runTest {
        val result = pipeline(
            dnsResult = dnsResult(
                status = DnsLookupStatus.PARTIAL,
                records = listOf(DnsRecord(type = DnsRecordType.A, value = "93.184.216.34")),
            ),
        ).run {}
        val report = DefaultDiagnosticAnalyzerV2().analyze(result)

        val finding = report.findings.first { it.id == "DNS_FAILURE" }
        assertEquals(DiagnosticSeverity.WARNING, finding.severity)
        assertEquals(DiagnosticOverallStatus.ATTENTION, report.overallStatus)
    }

    @Test
    fun pingFailureWithTcpSuccess_doesNotClassifyInternetAsDown() = runTest {
        val result = pipeline(
            gatewayResult = pingResult(success = false),
        ).run {}
        val report = DefaultDiagnosticAnalyzerV2().analyze(result)

        assertTrue(report.findings.any { it.id == "PING_TCP_DIFFERENCE" })
        assertFalse(report.findings.any { it.id == "PUBLIC_CONNECTIVITY_FAILED" })
        assertEquals(DiagnosticOverallStatus.HEALTHY, report.overallStatus)
    }

    @Test
    fun onePublicTargetFailureAndOneSuccess_keepsPublicCheckPassing() = runTest {
        val result = pipeline(
            tcpResponse = { host, port, _ ->
                tcpResult(host, port, success = host == "public-b")
            },
        ).run {}
        val report = DefaultDiagnosticAnalyzerV2().analyze(result)

        assertEquals(
            DiagnosticCheckStatus.PASS,
            result.checks.first { it.id == "PUBLIC_CONNECTIVITY" }.status,
        )
        assertFalse(report.findings.any { it.id == "PUBLIC_CONNECTIVITY_FAILED" })
    }

    @Test
    fun unavailablePublicTargets_produceUnknownPublicCheck() = runTest {
        val result = pipeline(
            tcpResponse = { _, _, _ -> error("adapter unavailable") },
        ).run {}

        val publicCheck = result.checks.first { it.id == "PUBLIC_CONNECTIVITY" }
        assertEquals(DiagnosticCheckStatus.UNKNOWN, publicCheck.status)
        assertEquals(DiagnosticSeverity.NOTICE, publicCheck.severity)
    }

    @Test
    fun successfulAWithMissingAaaa_isNoticeNotWarning() = runTest {
        val result = pipeline(
            dnsResult = dnsResult(
                status = DnsLookupStatus.SUCCESS,
                records = listOf(DnsRecord(type = DnsRecordType.A, value = "93.184.216.34")),
            ),
        ).run {}
        val report = DefaultDiagnosticAnalyzerV2().analyze(result)

        assertTrue(report.findings.any { it.id == "NO_IPV6_RECORD" })
        assertFalse(report.findings.any { it.severity == DiagnosticSeverity.WARNING })
        assertEquals(DiagnosticOverallStatus.HEALTHY, report.overallStatus)
    }

    @Test
    fun fakeIp_isNoticeAndDoesNotFailDns() = runTest {
        val result = pipeline(
            dnsResult = dnsResult(
                records = listOf(DnsRecord(type = DnsRecordType.A, value = "198.18.0.1")),
            ),
        ).run {}
        val report = DefaultDiagnosticAnalyzerV2().analyze(result)

        assertTrue(report.findings.any { it.id == "POSSIBLE_FAKE_IP" })
        assertEquals(DiagnosticSeverity.NOTICE, report.overallSeverity)
        assertEquals(DiagnosticOverallStatus.HEALTHY, report.overallStatus)
    }

    @Test
    fun vpn_isNoticeOnly() = runTest {
        val result = pipeline(context = networkContext(vpnActive = true)).run {}
        val report = DefaultDiagnosticAnalyzerV2().analyze(result)

        assertTrue(report.findings.any { it.id == "VPN_ACTIVE" })
        assertEquals(DiagnosticSeverity.NOTICE, report.overallSeverity)
        assertEquals(DiagnosticOverallStatus.HEALTHY, report.overallStatus)
    }

    @Test
    fun networkChangesDuringRun_addsCautionFinding() = runTest {
        val first = networkContext()
        val second = first.copy(
            connectionType = ConnectionType.CELLULAR,
            ipv4Address = "10.0.0.20",
        )
        val repository = SequenceNetworkRepository(listOf(first, first, second))
        val pipeline = pipeline(networkRepository = repository)
        val report = DefaultDiagnosticAnalyzerV2().analyze(pipeline.run {})

        assertTrue(report.findings.any { it.id == "NETWORK_CHANGED_DURING_RUN" })
        assertTrue(report.recommendations.any { it.action.contains("重新运行") })
    }

    @Test
    fun cancellation_doesNotSaveHistory() = runTest {
        val records = mutableListOf<HistoryRecord>()
        val pipeline = pipeline(
            dnsEngine = object : DnsQueryEngine {
                override suspend fun lookup(request: DnsLookupRequest): DnsLookupResult {
                    delay(Long.MAX_VALUE)
                    error("unreachable")
                }
            },
        )
        val job = launch {
            RunDiagnosticV2UseCase(
                pipeline = pipeline,
                analyzer = DefaultDiagnosticAnalyzerV2(),
                historyRecorder = HistoryRecorder { records += it },
            )()
        }

        job.cancel()
        job.join()

        assertTrue(job.isCancelled)
        assertTrue(records.isEmpty())
    }

    @Test
    fun dnsV2RequestUsesAAndAaaaAndInternalProbesDoNotRecordHistory() = runTest {
        val dns = RecordingDnsQueryEngine(dnsResult())
        val records = mutableListOf<HistoryRecord>()
        RunDiagnosticV2UseCase(
            pipeline = pipeline(dnsEngine = dns),
            analyzer = DefaultDiagnosticAnalyzerV2(),
            historyRecorder = HistoryRecorder { records += it },
        )()

        assertEquals(setOf(DnsRecordType.A, DnsRecordType.AAAA), dns.request?.recordTypes)
        assertEquals(1, records.size)
    }

    private fun pipeline(
        context: NetworkContext = networkContext(),
        networkRepository: NetworkRepository = SequenceNetworkRepository(listOf(context)),
        gatewayResult: PingSessionResult = pingResult(success = true),
        pingEngine: PingSessionEngine = FakePingSessionEngine(gatewayResult),
        dnsResult: DnsLookupResult = dnsResult(),
        dnsEngine: DnsQueryEngine = RecordingDnsQueryEngine(dnsResult),
        tcpResponse: (String, Int, Int) -> TcpProbeResult = { host, port, _ ->
            tcpResult(host, port, success = true)
        },
    ): DiagnosticPipeline = DefaultDiagnosticPipeline(
        networkRepository = networkRepository,
        pingSessionEngine = pingEngine,
        dnsQueryEngine = dnsEngine,
        tcpPortChecker = FakeTcpPortChecker(tcpResponse),
        probeTargets = DiagnosticProbeTargets(
            publicTargets = listOf(
                DiagnosticProbeTarget("public-a"),
                DiagnosticProbeTarget("public-b"),
            ),
            domainName = "test.example",
        ),
        now = { 1_000L },
    )

    private fun networkContext(
        vpnActive: Boolean = false,
        connectionType: ConnectionType = ConnectionType.WIFI,
        gateway: String? = "192.0.2.1",
        validated: Boolean? = true,
    ) = NetworkContext(
        connectionType = connectionType,
        ipv4Address = "192.0.2.20",
        ipv6Address = null,
        gateway = gateway,
        dnsServers = listOf("192.0.2.1"),
        vpnActive = vpnActive,
        wifiName = "Test Wi-Fi",
        wifiSignalLevel = 3,
        activeNetworkAvailable = true,
        validated = validated,
    )

    private fun pingResult(success: Boolean) = PingSessionResult(
        target = "192.0.2.1",
        address = "192.0.2.1",
        protocol = PingProtocol.IPV4,
        mode = PingMode.CONTINUOUS,
        startTime = 1_000L,
        endTime = 1_100L,
        sentPackets = 3,
        receivedPackets = if (success) 3 else 0,
        lostPackets = if (success) 0 else 3,
        packetLoss = if (success) 0.0 else 1.0,
        minLatencyMs = if (success) 1L else null,
        avgLatencyMs = if (success) 1.0 else null,
        maxLatencyMs = if (success) 1L else null,
        jitterMs = if (success) 0.0 else null,
        qualityLevel = if (success) PingQualityLevel.EXCELLENT else PingQualityLevel.POOR,
        summary = if (success) "ok" else "failed",
        method = PingMethod.SYSTEM_REACHABILITY,
        errorMessage = if (success) null else "Timeout",
    )

    private fun dnsResult(
        status: DnsLookupStatus = DnsLookupStatus.SUCCESS,
        records: List<DnsRecord> = listOf(
            DnsRecord(type = DnsRecordType.A, value = "93.184.216.34"),
            DnsRecord(type = DnsRecordType.AAAA, value = "2001:db8::34"),
        ),
    ) = DnsLookupResult(
        queryName = "test.example",
        requestedTypes = setOf(DnsRecordType.A, DnsRecordType.AAAA),
        records = records,
        server = null,
        method = DnsQueryMethod.ANDROID_DNS_RESOLVER,
        status = status,
        durationMs = 10L,
        startTime = 1_000L,
        endTime = 1_010L,
        errorMessage = if (status == DnsLookupStatus.SUCCESS) null else "DNS failure",
    )

    private fun tcpResult(
        host: String,
        port: Int = 443,
        success: Boolean,
    ) = TcpProbeResult(
        host = host,
        port = port,
        success = success,
        latencyMs = if (success) 5L else null,
        errorMessage = if (success) null else "Timeout",
    )

    private class SequenceNetworkRepository(
        private val contexts: List<NetworkContext>,
    ) : NetworkRepository {
        private var index = 0

        override fun observeNetworkContext(): Flow<NetworkContext> = flowOf(
            contexts.getOrElse(index++) { contexts.last() },
        )
    }

    private class FakePingSessionEngine(
        private val response: PingSessionResult,
    ) : PingSessionEngine {
        var callCount: Int = 0

        override suspend fun run(
            request: PingRequest,
            onProgress: (PingSessionProgress) -> Unit,
        ): PingSessionResult {
            callCount += 1
            return response.copy(target = request.target)
        }
    }

    private class FakeTcpPortChecker(
        private val response: (String, Int, Int) -> TcpProbeResult,
    ) : TcpPortChecker {
        override suspend fun check(host: String, port: Int, timeoutMs: Int): TcpProbeResult =
            response(host, port, timeoutMs)
    }

    private class RecordingDnsQueryEngine(
        private val response: DnsLookupResult,
    ) : DnsQueryEngine {
        var request: DnsLookupRequest? = null

        override suspend fun lookup(request: DnsLookupRequest): DnsLookupResult {
            this.request = request
            return response
        }
    }
}
