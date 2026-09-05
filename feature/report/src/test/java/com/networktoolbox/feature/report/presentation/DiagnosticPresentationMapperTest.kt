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
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticCheck as LegacyCheck
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticCheckStatus as LegacyCheckStatus
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticFindingV2 as LegacyFinding
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticOverallStatus
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticReportV2
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticSeverity as LegacySeverity
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticStage as LegacyStage
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

    @Test
    fun historyMapsIpConfigurationByStableIdInsteadOfLegacyDisplayStage() {
        val presentation = DiagnosticPresentationMapper.forHistory(
            historyReport(
                checks = listOf(
                    legacyCheck("NETWORK_CONTEXT", LegacyStage.NETWORK_CONTEXT),
                    // Older adapter versions stored both checks under the same
                    // legacy display stage. The stable ID must win here.
                    legacyCheck("IP_CONFIGURATION", LegacyStage.NETWORK_CONTEXT),
                ),
            ),
        )

        assertEquals(
            listOf(DiagnosticStage.NETWORK_STATE, DiagnosticStage.IP_CONFIGURATION),
            DiagnosticPresentationMapper
                .stageSummariesForPresentation(presentation.checks)
                .map { it.stage },
        )
    }

    @Test
    fun historyAggregatesAllPublicChecksIntoOneSummary() {
        val presentation = DiagnosticPresentationMapper.forHistory(
            historyReport(
                checks = listOf(
                    legacyCheck(
                        id = "PUBLIC_CONNECTIVITY",
                        stage = LegacyStage.PUBLIC_CONNECTIVITY,
                        target = "223.5.5.5",
                    ),
                    legacyCheck(
                        id = "PUBLIC_CONNECTIVITY",
                        stage = LegacyStage.PUBLIC_CONNECTIVITY,
                        target = "1.1.1.1",
                    ),
                ),
            ),
        )

        val summary = DiagnosticPresentationMapper
            .stageSummariesForPresentation(presentation.checks)
            .single()

        assertEquals(DiagnosticStage.INTERNET, summary.stage)
        assertEquals("2 个公网探测目标均获得有效响应。", summary.summary)
        assertEquals(2, summary.checks.size)
    }

    @Test
    fun historyMixedPublicChecksStayOneNoticeSummary() {
        val presentation = DiagnosticPresentationMapper.forHistory(
            historyReport(
                checks = listOf(
                    legacyCheck(
                        id = "PUBLIC_CONNECTIVITY",
                        stage = LegacyStage.PUBLIC_CONNECTIVITY,
                        target = "223.5.5.5",
                    ),
                    legacyCheck(
                        id = "PUBLIC_CONNECTIVITY",
                        stage = LegacyStage.PUBLIC_CONNECTIVITY,
                        status = LegacyCheckStatus.UNKNOWN,
                        severity = LegacySeverity.NOTICE,
                        target = "1.1.1.1",
                    ),
                ),
            ),
        )

        val summary = DiagnosticPresentationMapper
            .stageSummariesForPresentation(presentation.checks)
            .single()

        assertEquals(DiagnosticSeverity.NOTICE, summary.severity)
        assertTrue(summary.summary.contains("部分探测结果存在差异"))
    }

    @Test
    fun historyKeepsBothPublicTechnicalTargetsAndAddsPort() {
        val presentation = DiagnosticPresentationMapper.forHistory(
            historyReport(
                checks = listOf(
                    legacyCheck(
                        id = "PUBLIC_CONNECTIVITY",
                        stage = LegacyStage.PUBLIC_CONNECTIVITY,
                        target = "223.5.5.5",
                    ),
                    legacyCheck(
                        id = "PUBLIC_CONNECTIVITY",
                        stage = LegacyStage.PUBLIC_CONNECTIVITY,
                        target = "1.1.1.1",
                    ),
                ),
            ),
        )

        assertEquals(
            listOf("223.5.5.5:443", "1.1.1.1:443"),
            presentation.checks.mapNotNull { check ->
                DiagnosticPresentationMapper.targetDisplayName(check)
            },
        )
    }

    @Test
    fun historyDoesNotReuseMachineSummary() {
        val presentation = DiagnosticPresentationMapper.forHistory(
            historyReport(
                checks = listOf(
                    legacyCheck(
                        id = "NETWORK_CONTEXT",
                        stage = LegacyStage.NETWORK_CONTEXT,
                        summary = "CONNECT_SUCCESS",
                    ),
                ),
            ),
        )

        assertEquals("已发现活动网络。", presentation.checks.single().summary)
        assertFalse(presentation.checks.single().summary.contains("CONNECT_SUCCESS"))
    }

    @Test
    fun healthyNormalFindingIsHiddenButRetainedForTechnicalDetails() {
        val presentation = DiagnosticPresentationMapper.forHistory(
            historyReport(
                findings = listOf(
                    LegacyFinding(
                        id = "NETWORK_APPEARS_NORMAL",
                        severity = LegacySeverity.HEALTHY,
                        title = "基础网络连接正常",
                        description = "检测结果正常。",
                    ),
                ),
            ),
        )

        assertEquals(1, presentation.findings.size)
        assertTrue(
            DiagnosticPresentationMapper
                .visibleFindingPresentations(presentation.findings)
                .isEmpty(),
        )
    }

    @Test
    fun noticeFindingUsesEnvironmentFramingForHistory() {
        val presentation = DiagnosticPresentationMapper.forHistory(
            historyReport(
                findings = listOf(
                    LegacyFinding(
                        id = "VPN_ACTIVE",
                        severity = LegacySeverity.NOTICE,
                        title = "检测到 VPN",
                        description = "当前结果可能来自 VPN 隧道后的网络环境。",
                    ),
                ),
            ),
        )

        assertEquals(
            "网络环境提示",
            DiagnosticPresentationMapper.findingsTitleForPresentation(
                DiagnosticPresentationMapper.visibleFindingPresentations(presentation.findings),
            ),
        )
    }

    @Test
    fun noMaterialFindingUsesConservativeWording() {
        assertEquals(
            "未发现明确的网络故障。",
            DiagnosticPresentationMapper.noMaterialFindingMessage(),
        )
    }

    @Test
    fun healthyHistoryUsesNormalRecommendationFraming() {
        val presentation = DiagnosticPresentationMapper.forHistory(
            historyReport(overallStatus = DiagnosticOverallStatus.HEALTHY),
        )

        assertEquals(
            "如果仍然遇到问题",
            DiagnosticPresentationMapper.recommendationSectionTitle(
                presentation.overallStatus,
            ),
        )
    }

    @Test
    fun legacySchemaTwoStageFallbackRemainsReadable() {
        val presentation = DiagnosticPresentationMapper.forHistory(
            historyReport(
                checks = listOf(
                    legacyCheck(
                        id = "LEGACY_NETWORK_CHECK",
                        stage = LegacyStage.NETWORK_CONTEXT,
                    ),
                ),
            ),
        )

        assertEquals(DiagnosticStage.NETWORK_STATE, presentation.checks.single().stage)
        assertEquals("已发现活动网络。", presentation.checks.single().summary)
    }

    @Test
    fun historyNetworkSnapshotRetainsVpnContextForConservativeFakeIpText() {
        val presentation = DiagnosticPresentationMapper.forHistory(
            historyReport(
                networkSnapshot = NetworkContext(
                    connectionType = ConnectionType.WIFI,
                    ipv4Address = "10.0.0.2",
                    ipv6Address = null,
                    gateway = "10.0.0.1",
                    dnsServers = listOf("10.0.0.1"),
                    vpnActive = true,
                    wifiName = null,
                    wifiSignalLevel = null,
                ),
            ),
        )

        assertTrue(
            DiagnosticPresentationMapper
                .fakeIpMessage(presentation.networkSummary?.vpnActive)
                .contains("VPN 隧道后的网络环境"),
        )
    }

    private fun historyReport(
        checks: List<LegacyCheck> = emptyList(),
        findings: List<LegacyFinding> = emptyList(),
        overallStatus: DiagnosticOverallStatus = DiagnosticOverallStatus.HEALTHY,
        overallSeverity: LegacySeverity = LegacySeverity.HEALTHY,
        networkSnapshot: NetworkContext? = null,
    ) = DiagnosticReportV2(
        timestamp = 1_000L,
        durationMs = 25L,
        overallStatus = overallStatus,
        overallSeverity = overallSeverity,
        summary = "基础网络连接正常",
        networkSnapshot = networkSnapshot,
        checks = checks,
        findings = findings,
        recommendations = emptyList(),
    )

    private fun legacyCheck(
        id: String,
        stage: LegacyStage,
        status: LegacyCheckStatus = LegacyCheckStatus.PASS,
        severity: LegacySeverity = LegacySeverity.HEALTHY,
        summary: String = "machine summary",
        target: String? = null,
        method: String? = "SYSTEM_REACHABILITY",
    ) = LegacyCheck(
        id = id,
        stage = stage,
        name = "legacy name",
        status = status,
        severity = severity,
        summary = summary,
        target = target,
        method = method,
    )

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
