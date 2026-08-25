package com.networktoolbox.feature.report.domain

import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryRecorder
import com.networktoolbox.core.common.history.HistoryType
import com.networktoolbox.feature.report.FakeDnsUseCase
import com.networktoolbox.feature.report.FakeNetworkRepository
import com.networktoolbox.feature.report.FakePingUseCase
import com.networktoolbox.feature.report.FakeTcpUseCase
import com.networktoolbox.feature.report.diagnostic.BasicDiagnosticAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateDiagnosticReportUseCaseTest {
    @Test
    fun allChecksSuccessful_generatesNormalReportAndUsesFixedTargets() = runTestUseCase {
        val report = useCase.invoke { steps += it }

        assertEquals("基础网络连接正常", report.summary)
        assertTrue(report.findings.any { it.title == "Network connectivity looks normal" })
        assertEquals(
            listOf(
                ReportStep.NETWORK_INFORMATION,
                ReportStep.PING,
                ReportStep.DNS,
                ReportStep.TCP,
            ),
            steps,
        )
        assertEquals(1, networkRepository.callCount)
        assertEquals(GenerateDiagnosticReportUseCase.DEFAULT_PING_TARGET, ping.receivedTarget)
        assertEquals(GenerateDiagnosticReportUseCase.DEFAULT_DNS_DOMAIN, dns.receivedDomain)
        assertEquals(GenerateDiagnosticReportUseCase.DEFAULT_TCP_HOST, tcp.receivedHost)
        assertEquals(GenerateDiagnosticReportUseCase.DEFAULT_TCP_PORT, tcp.receivedPort)
        assertEquals(HistoryType.REPORT, historyRecords.single().type)
        assertEquals(report.summary, historyRecords.single().summary)
    }

    @Test
    fun pingFailure_doesNotStopDnsOrTcpAndIncludesPingFinding() = runTestUseCase(
        pingResponse = com.networktoolbox.core.network.ping.PingResult(
            target = "8.8.8.8",
            success = false,
            latencyMs = null,
            method = com.networktoolbox.core.network.ping.PingMethod.SYSTEM_REACHABILITY,
            errorMessage = "Timeout",
        ),
    ) {
        val report = useCase()

        assertTrue(report.findings.any { it.title == "目标不可达" })
        assertEquals(1, dns.callCount)
        assertEquals(1, tcp.callCount)
    }

    @Test
    fun dnsFailure_doesNotStopTcpAndIncludesDnsFinding() = runTestUseCase(
        dnsResponse = com.networktoolbox.core.network.dns.DnsResult(
            domain = "example.com",
            success = false,
            records = emptyList(),
            durationMs = null,
            method = com.networktoolbox.core.network.dns.DnsMethod.SYSTEM_RESOLVER,
            errorMessage = "Resolver failure",
        ),
    ) {
        val report = useCase()

        assertTrue(report.findings.any { it.title == "DNS解析失败" })
        assertEquals(1, tcp.callCount)
    }

    @Test
    fun multipleFailures_generateMultipleFindings() = runTestUseCase(
        pingResponse = com.networktoolbox.core.network.ping.PingResult(
            target = "8.8.8.8",
            success = false,
            latencyMs = null,
            method = com.networktoolbox.core.network.ping.PingMethod.SYSTEM_REACHABILITY,
            errorMessage = "Timeout",
        ),
        dnsResponse = com.networktoolbox.core.network.dns.DnsResult(
            domain = "example.com",
            success = false,
            records = emptyList(),
            durationMs = null,
            method = com.networktoolbox.core.network.dns.DnsMethod.SYSTEM_RESOLVER,
            errorMessage = "Resolver failure",
        ),
        tcpResponse = com.networktoolbox.core.network.tcp.TcpProbeResult(
            host = "example.com",
            port = 443,
            success = false,
            latencyMs = null,
            errorMessage = "Connection refused",
        ),
    ) {
        val report = useCase()

        assertTrue(report.findings.size >= 3)
        assertTrue(report.findings.any { it.title == "目标不可达" })
        assertTrue(report.findings.any { it.title == "DNS解析失败" })
        assertTrue(report.findings.any { it.title == "目标端口拒绝连接" })
    }

    private fun runTestUseCase(
        pingResponse: com.networktoolbox.core.network.ping.PingResult =
            com.networktoolbox.feature.report.successfulPingResult(),
        dnsResponse: com.networktoolbox.core.network.dns.DnsResult =
            com.networktoolbox.feature.report.successfulDnsResult(),
        tcpResponse: com.networktoolbox.core.network.tcp.TcpProbeResult =
            com.networktoolbox.feature.report.successfulTcpResult(),
        block: suspend TestDependencies.() -> Unit,
    ) {
        kotlinx.coroutines.test.runTest {
            val dependencies = TestDependencies(
                networkRepository = FakeNetworkRepository(),
                ping = FakePingUseCase(pingResponse),
                dns = FakeDnsUseCase(dnsResponse),
                tcp = FakeTcpUseCase(tcpResponse),
                historyRecords = mutableListOf(),
            )
            dependencies.block()
        }
    }

    private class TestDependencies(
        val networkRepository: FakeNetworkRepository,
        val ping: FakePingUseCase,
        val dns: FakeDnsUseCase,
        val tcp: FakeTcpUseCase,
        val historyRecords: MutableList<HistoryRecord>,
    ) {
        val useCase: GenerateDiagnosticReportUseCase = GenerateDiagnosticReportUseCase(
            networkRepository = networkRepository,
            ping = ping,
            dns = dns,
            tcp = tcp,
            analyzer = BasicDiagnosticAnalyzer(),
            historyRecorder = HistoryRecorder { historyRecords += it },
        )
        val steps = mutableListOf<ReportStep>()
    }
}
