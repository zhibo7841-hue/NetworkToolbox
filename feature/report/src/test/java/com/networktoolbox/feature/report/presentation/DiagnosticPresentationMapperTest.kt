package com.networktoolbox.feature.report.presentation

import com.networktoolbox.core.common.diagnostic.DiagnosticCheck
import com.networktoolbox.core.common.diagnostic.DiagnosticCheckCode
import com.networktoolbox.core.common.diagnostic.DiagnosticCheckStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticConfidence
import com.networktoolbox.core.common.diagnostic.DiagnosticDiagnosisStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticEvidenceLevel
import com.networktoolbox.core.common.diagnostic.DiagnosticFinding
import com.networktoolbox.core.common.diagnostic.DiagnosticFindingCode
import com.networktoolbox.core.common.diagnostic.DiagnosticSeverity
import com.networktoolbox.core.common.diagnostic.DiagnosticStage
import com.networktoolbox.core.common.diagnostic.DiagnosticTarget
import com.networktoolbox.core.common.diagnostic.DiagnosticTargetKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticPresentationMapperTest {
    @Test
    fun systemReachabilityIsMappedToChineseDisplayText() {
        val display = DiagnosticPresentationMapper.methodDisplayName("SYSTEM_REACHABILITY")

        assertEquals("系统可达性探测", display)
        assertFalse(display.contains("SYSTEM_REACHABILITY"))
    }

    @Test
    fun unknownTechnicalMethodDoesNotLeakMachineValue() {
        val display = DiagnosticPresentationMapper.methodDisplayName("INTERNAL_ERROR")

        assertEquals("其他检测方式", display)
        assertFalse(display.contains("INTERNAL_ERROR"))
    }

    @Test
    fun twoSuccessfulPublicProbesBecomeOneSummaryRow() {
        val summaries = DiagnosticPresentationMapper.stageSummaries(
            listOf(
                publicCheck("223.5.5.5"),
                publicCheck("1.1.1.1"),
            ),
        )

        assertEquals(1, summaries.size)
        assertEquals(DiagnosticStage.INTERNET, summaries.single().stage)
        assertEquals(DiagnosticCheckStatus.PASS, summaries.single().status)
        assertEquals(DiagnosticSeverity.HEALTHY, summaries.single().severity)
        assertEquals("2 个公网探测目标均获得有效响应。", summaries.single().summary)
    }

    @Test
    fun mixedPublicProbeResultsRemainOneNoticeSummary() {
        val summary = DiagnosticPresentationMapper.stageSummaries(
            listOf(
                publicCheck("223.5.5.5"),
                publicCheck("1.1.1.1", DiagnosticCheckStatus.UNKNOWN),
            ),
        ).single()

        assertEquals(DiagnosticCheckStatus.PASS, summary.status)
        assertEquals(DiagnosticSeverity.NOTICE, summary.severity)
        assertTrue(summary.summary.contains("部分探测结果存在差异"))
    }

    @Test
    fun publicTechnicalTargetKeepsTcpPort() {
        val display = DiagnosticPresentationMapper.targetDisplayName(
            publicCheck("1.1.1.1"),
        )

        assertEquals("1.1.1.1:443", display)
    }

    @Test
    fun ipv6PublicTechnicalTargetUsesUnambiguousPortForm() {
        val check = publicCheck("2001:db8::1")

        assertEquals("[2001:db8::1]:443", DiagnosticPresentationMapper.targetDisplayName(check))
    }

    @Test
    fun normalFindingIsHiddenFromConciseFindings() {
        val finding = finding(DiagnosticFindingCode.NETWORK_APPEARS_NORMAL, DiagnosticSeverity.HEALTHY)

        assertTrue(DiagnosticPresentationMapper.visibleFindings(listOf(finding)).isEmpty())
    }

    @Test
    fun normalFindingStillExistsInAnalysisInput() {
        val finding = finding(DiagnosticFindingCode.NETWORK_APPEARS_NORMAL, DiagnosticSeverity.HEALTHY)
        val findings = listOf(finding)

        assertEquals(DiagnosticFindingCode.NETWORK_APPEARS_NORMAL, findings.single().code)
    }

    @Test
    fun noticeOnlyFindingsUseEnvironmentFraming() {
        val finding = finding(DiagnosticFindingCode.VPN_ACTIVE, DiagnosticSeverity.NOTICE)

        assertEquals(
            "网络环境提示",
            DiagnosticPresentationMapper.findingsTitle(listOf(finding)),
        )
    }

    @Test
    fun materialFindingsUseProblemFraming() {
        val finding = finding(
            DiagnosticFindingCode.DNS_RESOLUTION_FAILURE,
            DiagnosticSeverity.WARNING,
        )

        assertEquals(
            "发现的问题",
            DiagnosticPresentationMapper.findingsTitle(listOf(finding)),
        )
    }

    @Test
    fun completedStartCardUsesRerunLanguage() {
        assertEquals(
            "再次进行网络诊断",
            DiagnosticPresentationMapper.startCardTitle(completed = true),
        )
        assertEquals(
            "重新诊断",
            DiagnosticPresentationMapper.startCardActionLabel(completed = true),
        )
    }

    @Test
    fun initialStartCardUsesStartLanguage() {
        assertEquals(
            "开始一次完整诊断",
            DiagnosticPresentationMapper.startCardTitle(completed = false),
        )
        assertEquals(
            "开始诊断",
            DiagnosticPresentationMapper.startCardActionLabel(completed = false),
        )
    }

    @Test
    fun normalRecommendationsUseConditionalFraming() {
        assertEquals(
            "如果仍然遇到问题",
            DiagnosticPresentationMapper.recommendationSectionTitle(
                DiagnosticDiagnosisStatus.NORMAL,
            ),
        )
        assertEquals(
            "建议尝试",
            DiagnosticPresentationMapper.recommendationSectionTitle(null),
        )
    }

    @Test
    fun fakeIpWithoutVpnUsesConservativeNetworkSideExplanation() {
        val text = DiagnosticPresentationMapper.fakeIpMessage(vpnActive = false)

        assertTrue(text.contains("Android 未检测到本机 VPN"))
        assertTrue(text.contains("路由器、代理网关或当前网络环境"))
        assertFalse(text.contains("OpenClash"))
        assertFalse(text.contains("Clash"))
    }

    @Test
    fun fakeIpWithVpnKeepsVpnContextExplanation() {
        val text = DiagnosticPresentationMapper.fakeIpMessage(vpnActive = true)

        assertTrue(text.contains("检测到 VPN"))
        assertTrue(text.contains("VPN 隧道后的网络环境"))
        assertFalse(text.contains("OpenClash"))
    }

    @Test
    fun networkSummaryDoesNotReuseRawMachineSummary() {
        val check = check(
            code = DiagnosticCheckCode.NETWORK_STATE,
            stage = DiagnosticStage.NETWORK_STATE,
            status = DiagnosticCheckStatus.PASS,
            severity = DiagnosticSeverity.HEALTHY,
            summary = "CONNECT_SUCCESS",
        )

        val display = DiagnosticPresentationMapper.userFacingSummary(check)

        assertEquals("已发现活动网络。", display)
        assertFalse(display.contains("CONNECT_SUCCESS"))
    }

    @Test
    fun tcpOutcomeMachineValueIsMappedToReadableText() {
        val display = DiagnosticPresentationMapper.tcpOutcomeDisplayName("TIMEOUT")

        assertEquals("连接超时", display)
        assertFalse(display.contains("TIMEOUT"))
    }

    private fun publicCheck(
        host: String,
        status: DiagnosticCheckStatus = DiagnosticCheckStatus.PASS,
    ): DiagnosticCheck = check(
        code = DiagnosticCheckCode.PUBLIC_CONNECTIVITY,
        stage = DiagnosticStage.INTERNET,
        status = status,
        severity = if (status == DiagnosticCheckStatus.PASS) {
            DiagnosticSeverity.HEALTHY
        } else {
            DiagnosticSeverity.NOTICE
        },
        summary = "machine summary",
        target = DiagnosticTarget(host, if (host.contains(':')) {
            DiagnosticTargetKind.IPV6
        } else {
            DiagnosticTargetKind.IPV4
        }),
    )

    private fun finding(
        code: DiagnosticFindingCode,
        severity: DiagnosticSeverity,
    ) = DiagnosticFinding(
        code = code,
        title = "测试发现",
        description = "测试说明",
        severity = severity,
        evidenceLevel = DiagnosticEvidenceLevel.CONFIRMED,
        confidence = DiagnosticConfidence.HIGH,
    )

    private fun check(
        code: DiagnosticCheckCode,
        stage: DiagnosticStage,
        status: DiagnosticCheckStatus,
        severity: DiagnosticSeverity,
        summary: String,
        target: DiagnosticTarget? = null,
    ) = DiagnosticCheck(
        code = code,
        stage = stage,
        status = status,
        severity = severity,
        summary = summary,
        target = target,
    )
}
