package com.networktoolbox.feature.report.diagnostic

import com.networktoolbox.core.network.dns.DnsMethod
import com.networktoolbox.core.network.dns.DnsResult
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.network.ping.PingMethod
import com.networktoolbox.core.network.ping.PingResult
import com.networktoolbox.core.network.tcp.TcpProbeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BasicDiagnosticAnalyzerTest {
    private val analyzer = BasicDiagnosticAnalyzer()

    @Test
    fun allBasicChecksSuccessful_reportsNormalConnectivity() {
        val report = analyzer.analyze(
            context = networkContext(),
            ping = pingResult(success = true),
            dns = dnsResult(success = true),
            tcp = tcpResult(success = true),
        )

        assertEquals("基础网络连接正常", report.summary)
        assertTrue(report.findings.any {
            it.level == FindingLevel.INFO && it.title == "Network connectivity looks normal"
        })
        assertTrue(report.suggestions.isEmpty())
    }

    @Test
    fun dnsFailure_reportsDnsFindingAndSuggestion() {
        val report = analyzer.analyze(
            context = networkContext(),
            ping = pingResult(success = true),
            dns = dnsResult(success = false, errorMessage = "Resolver failure"),
            tcp = null,
        )

        assertTrue(report.findings.any { it.title == "DNS解析失败" })
        assertTrue(report.suggestions.any { it.contains("DNS") })
    }

    @Test
    fun pingFailure_reportsPossibleReachabilityProblem() {
        val report = analyzer.analyze(
            context = networkContext(),
            ping = pingResult(success = false, errorMessage = "Timeout"),
            dns = dnsResult(success = true),
            tcp = null,
        )

        val finding = report.findings.first { it.title == "目标不可达" }
        assertEquals(FindingLevel.WARNING, finding.level)
        assertTrue(report.suggestions.any { it.contains("目标地址") && it.contains("防火墙") })
    }

    @Test
    fun tcpConnectionRefused_reportsPortRefusalWithoutOverclaiming() {
        val report = analyzer.analyze(
            context = networkContext(),
            ping = pingResult(success = true),
            dns = dnsResult(success = true),
            tcp = tcpResult(success = false, errorMessage = "Connection refused"),
        )

        val finding = report.findings.first { it.title == "目标端口拒绝连接" }
        assertEquals(FindingLevel.WARNING, finding.level)
        assertTrue(finding.description.contains("可能"))
        assertTrue(report.suggestions.any { it.contains("服务状态") })
    }

    @Test
    fun dnsFailureAndPingSuccess_reportMultipleFindings() {
        val report = analyzer.analyze(
            context = networkContext(),
            ping = pingResult(success = true),
            dns = dnsResult(success = false),
            tcp = null,
        )

        assertTrue(report.findings.size >= 2)
        assertTrue(report.findings.any { it.title == "目标可达性检测通过" })
        assertTrue(report.findings.any { it.title == "DNS解析失败" })
    }

    private fun networkContext() = NetworkContext(
        connectionType = ConnectionType.WIFI,
        ipv4Address = "192.168.1.20",
        ipv6Address = null,
        gateway = "192.168.1.1",
        dnsServers = listOf("223.5.5.5"),
        vpnActive = false,
        wifiName = "TestWiFi",
        wifiSignalLevel = 3,
    )

    private fun pingResult(success: Boolean, errorMessage: String? = null) = PingResult(
        target = "test.example",
        success = success,
        latencyMs = if (success) 12L else null,
        method = PingMethod.SYSTEM_REACHABILITY,
        errorMessage = errorMessage,
    )

    private fun dnsResult(success: Boolean, errorMessage: String? = null) = DnsResult(
        domain = "test.example",
        success = success,
        records = emptyList(),
        durationMs = if (success) 8L else null,
        method = DnsMethod.SYSTEM_RESOLVER,
        errorMessage = errorMessage,
    )

    private fun tcpResult(success: Boolean, errorMessage: String? = null) = TcpProbeResult(
        host = "test.example",
        port = 443,
        success = success,
        latencyMs = if (success) 15L else null,
        errorMessage = errorMessage,
    )
}
