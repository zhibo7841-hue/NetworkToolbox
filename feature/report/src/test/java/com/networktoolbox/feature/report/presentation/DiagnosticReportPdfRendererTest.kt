package com.networktoolbox.feature.report.presentation

import com.networktoolbox.core.common.diagnostic.DiagnosticCheckStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticConfidence
import com.networktoolbox.core.common.diagnostic.DiagnosticConnectionType
import com.networktoolbox.core.common.diagnostic.DiagnosticEvidenceLevel
import com.networktoolbox.core.common.diagnostic.DiagnosticNetworkSummary
import com.networktoolbox.core.common.diagnostic.DiagnosticObservation
import com.networktoolbox.core.common.diagnostic.DiagnosticObservationCode
import com.networktoolbox.core.common.diagnostic.DiagnosticObservationSource
import com.networktoolbox.core.common.diagnostic.DiagnosticObservationValue
import com.networktoolbox.core.common.diagnostic.DiagnosticSeverity
import com.networktoolbox.core.common.diagnostic.DiagnosticStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticReportPdfRendererTest {
    @Test
    fun completeReportLayoutHasHeaderAndReadableChineseContent() {
        val pages = DiagnosticReportPdfLayout.pages(normalPresentation())
        val lines = pages.flatten()

        assertTrue(pages.isNotEmpty())
        assertEquals("NetworkToolbox 网络诊断完整报告", lines.first())
        assertContains(lines, "诊断结论")
        assertContains(lines, "建议")
        assertContains(lines, "网络类型：Wi-Fi")
        assertContains(lines, "完整报告可能包含本机地址")
        assertTrue(lines.none { it.contains("UNKNOWN") || it.contains("SYSTEM_") })
    }

    @Test
    fun longReportIsPaginatedWithoutDroppingTechnicalEvidence() {
        val report = normalPresentation(
            findings = (1..16).map { index ->
                DiagnosticFindingPresentation(
                    id = "FINDING_$index",
                    severity = DiagnosticSeverity.NOTICE,
                    title = "网络环境提示 $index",
                    description = "这是用于分页验证的中文说明。".repeat(8),
                    confidence = DiagnosticConfidence.MEDIUM,
                    evidenceLevel = DiagnosticEvidenceLevel.SUPPORTED,
                )
            },
        )

        val pages = DiagnosticReportPdfLayout.pages(report)

        assertTrue("Expected more than one A4 page", pages.size > 1)
        assertContains(pages.flatten(), "网络环境提示 16")
        assertContains(pages.flatten(), "198.18.13.240")
        assertContains(pages.flatten(), "TTL 300 秒")
        assertTrue(pages.flatten().all { it.length <= 44 })
    }

    @Test
    fun dnsRecordsIpv6FakeIpMobileNextHopAndRecommendationsAreIncluded() {
        val report = normalPresentation(
            networkSummary = normalNetworkSummary().copy(
                connectionType = DiagnosticConnectionType.CELLULAR,
                gateway = "10.0.0.1",
                vpnActive = true,
            ),
            recommendations = listOf(
                DiagnosticRecommendationPresentation(
                    priority = 1,
                    title = "重试",
                    action = "重新进行 DNS 查询。",
                ),
                DiagnosticRecommendationPresentation(
                    priority = 2,
                    title = "检查",
                    action = "检查 Private DNS 或 VPN 设置。",
                ),
            ),
        )

        val lines = DiagnosticReportPdfLayout.reportLines(report)

        assertContains(lines, "路由下一跳：10.0.0.1")
        assertContains(lines, "fe80::58cf:44ff:fe53:f11e")
        assertContains(lines, "2001:db8::53")
        assertContains(lines, "DNS 记录：A example.com → 198.18.13.240 · TTL 300 秒")
        assertContains(lines, "优先级 10")
        assertContains(lines, "可能存在 Fake-IP DNS 环境")
        assertContains(lines, "重新进行 DNS 查询")
        assertFalse(lines.any { it.contains("DiagnosticReportV2") || it.contains("schemaVersion") })
    }

    @Test
    fun fileNameIsTimestampedAndSafeForCreateDocument() {
        val fileName = DiagnosticReportPdfRenderer.fileName(1_700_000_000_000L)

        assertTrue(fileName.startsWith("NetworkToolbox-Diagnostic-"))
        assertTrue(fileName.endsWith(".pdf"))
        assertFalse(fileName.contains(':'))
        assertTrue(fileName.matches(Regex("NetworkToolbox-Diagnostic-\\d{8}-\\d{4}\\.pdf")))
    }

    private fun normalPresentation(
        networkSummary: DiagnosticNetworkSummary = normalNetworkSummary(),
        findings: List<DiagnosticFindingPresentation> = listOf(
            DiagnosticFindingPresentation(
                id = "FAKE_IP_CONTEXT",
                severity = DiagnosticSeverity.NOTICE,
                title = "检测到特殊用途地址",
                description = "检测到 198.18.0.0/15 特殊用途地址，可能存在 Fake-IP DNS 环境；这不等同于 DNS 错误。",
                confidence = DiagnosticConfidence.MEDIUM,
                evidenceLevel = DiagnosticEvidenceLevel.SUPPORTED,
            ),
        ),
        recommendations: List<DiagnosticRecommendationPresentation> = listOf(
            DiagnosticRecommendationPresentation(
                priority = 1,
                title = "继续观察",
                action = "如果应用仍无法访问，请检查应用配置。",
            ),
        ),
    ): DiagnosticReportPresentation {
        val dnsObservation = DiagnosticObservation(
            id = "dns-a",
            code = DiagnosticObservationCode.DNS_RECORD,
            stage = DiagnosticStage.DNS,
            source = DiagnosticObservationSource.DNS_ENGINE,
            value = DiagnosticObservationValue.DnsRecordValue(
                recordType = "A",
                name = "example.com",
                value = "198.18.13.240",
                ttlSeconds = 300L,
                priority = null,
            ),
            observedAt = 1_700_000_000_000L,
        )
        val mxObservation = dnsObservation.copy(
            id = "dns-mx",
            value = DiagnosticObservationValue.DnsRecordValue(
                recordType = "MX",
                name = "example.com",
                value = "mail.example.com",
                ttlSeconds = 299L,
                priority = 10,
            ),
        )
        return DiagnosticReportPresentation(
            timestamp = 1_700_000_000_000L,
            overallStatus = com.networktoolbox.core.common.diagnostic.DiagnosticDiagnosisStatus.NORMAL,
            overallSeverity = DiagnosticSeverity.NOTICE,
            summary = "基础网络连接正常",
            explanation = "当前网络可以完成基础检测，部分结果需要结合上下文理解。",
            checks = listOf(
                DiagnosticCheckPresentation(
                    id = "DNS",
                    stage = DiagnosticStage.DNS,
                    status = DiagnosticCheckStatus.PASS,
                    severity = DiagnosticSeverity.HEALTHY,
                    summary = "DNS 查询完成。",
                    method = "SYSTEM_RESOLVER",
                    rawData = mapOf(
                        "requestedTypes" to "A,AAAA,MX,TXT",
                        "recordCounts" to "A=1,AAAA=1,MX=1,TXT=1",
                        "durationMs" to "32",
                        "fakeIpObserved" to "true",
                    ),
                    observationIds = listOf("dns-a", "dns-mx"),
                ),
            ),
            findings = findings,
            recommendations = recommendations,
            networkSummary = networkSummary,
            observations = listOf(dnsObservation, mxObservation),
        )
    }

    private fun normalNetworkSummary() = DiagnosticNetworkSummary(
        connectionType = DiagnosticConnectionType.WIFI,
        localAddressSummary = listOf("10.0.1.206", "fe80::58cf:44ff:fe53:f11e"),
        prefixLength = 24,
        gateway = "10.0.1.1",
        configuredDnsServers = listOf("10.0.1.1", "2001:db8::53"),
        vpnActive = false,
        privateDnsActive = false,
        privateDnsServerName = null,
        validated = true,
    )

    private fun assertContains(lines: List<String>, expected: String) {
        val wrapped = lines.joinToString("")
        assertTrue(
            "Expected <$expected> in:\n${lines.joinToString("\n")}",
            lines.any { it.contains(expected) } || wrapped.contains(expected),
        )
    }
}
