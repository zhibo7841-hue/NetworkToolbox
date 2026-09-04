package com.networktoolbox.feature.report.diagnostic.v4

import com.networktoolbox.core.common.diagnostic.DiagnosticCheck
import com.networktoolbox.core.common.diagnostic.DiagnosticCheckCode
import com.networktoolbox.core.common.diagnostic.DiagnosticCheckStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticConfidence
import com.networktoolbox.core.common.diagnostic.DiagnosticDiagnosis
import com.networktoolbox.core.common.diagnostic.DiagnosticDiagnosisStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticDnsOutcome
import com.networktoolbox.core.common.diagnostic.DiagnosticEvidenceLevel
import com.networktoolbox.core.common.diagnostic.DiagnosticFinding
import com.networktoolbox.core.common.diagnostic.DiagnosticFindingCode
import com.networktoolbox.core.common.diagnostic.DiagnosticObservation
import com.networktoolbox.core.common.diagnostic.DiagnosticObservationCode
import com.networktoolbox.core.common.diagnostic.DiagnosticObservationValue
import com.networktoolbox.core.common.diagnostic.DiagnosticRecommendation
import com.networktoolbox.core.common.diagnostic.DiagnosticRecommendationCode
import com.networktoolbox.core.common.diagnostic.DiagnosticRecommendationPriority
import com.networktoolbox.core.common.diagnostic.DiagnosticRunStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticSeverity
import com.networktoolbox.core.common.diagnostic.DiagnosticStage
import com.networktoolbox.core.common.diagnostic.DiagnosticTcpOutcome
import com.networktoolbox.feature.report.diagnostic.v2.orchestration.DiagnosticRunEvidence

/** Pure Kotlin input/output boundary for the v0.4 conservative rule engine. */
interface DiagnosticAnalyzerV4 {
    fun analyze(evidence: DiagnosticRunEvidence): DiagnosticAnalysisResult
}

data class DiagnosticAnalysisResult(
    val findings: List<DiagnosticFinding>,
    val diagnosis: DiagnosticDiagnosis?,
    val recommendations: List<DiagnosticRecommendation>,
) {
    init {
        require(findings.size <= MAX_FINDINGS) { "Too many diagnostic findings." }
        require(recommendations.size <= MAX_RECOMMENDATIONS) {
            "Too many diagnostic recommendations."
        }
    }

    private companion object {
        const val MAX_FINDINGS = 16
        const val MAX_RECOMMENDATIONS = 3
    }
}

/**
 * Deterministic, evidence-only analysis for Automatic Diagnostics v2.
 *
 * This class deliberately has no Android, network, coroutine, database, or UI
 * dependency. It never turns one timeout, one failed Ping, a VPN, or a
 * Fake-IP observation into a definitive network fault.
 */
class DefaultDiagnosticAnalyzerV4 : DiagnosticAnalyzerV4 {
    override fun analyze(evidence: DiagnosticRunEvidence): DiagnosticAnalysisResult = when (
        evidence.runStatus
    ) {
        DiagnosticRunStatus.RUNNING -> DiagnosticAnalysisResult(
            findings = emptyList(),
            diagnosis = null,
            recommendations = emptyList(),
        )

        DiagnosticRunStatus.CANCELLED -> DiagnosticAnalysisResult(
            findings = emptyList(),
            diagnosis = null,
            recommendations = emptyList(),
        )

        DiagnosticRunStatus.NETWORK_CHANGED -> analyzeNetworkChanged()
        DiagnosticRunStatus.FAILED -> analyzeFailed(evidence)
        DiagnosticRunStatus.COMPLETED -> analyzeCompleted(evidence)
    }

    private fun analyzeNetworkChanged(): DiagnosticAnalysisResult {
        val recommendation = recommendation(
            code = DiagnosticRecommendationCode.RETRY_DIAGNOSTIC,
            priority = DiagnosticRecommendationPriority.PRIMARY,
            title = "重新运行诊断",
            action = "请在网络稳定后重新运行诊断。",
            reason = "本次检测跨越了不同网络环境，不能合并为一个强结论。",
        )
        return DiagnosticAnalysisResult(
            findings = emptyList(),
            diagnosis = diagnosis(
                status = DiagnosticDiagnosisStatus.UNKNOWN,
                title = "检测结果无法合并判断",
                explanation = "检测过程中网络发生变化，当前结果可能来自不同网络环境。",
                confidence = DiagnosticConfidence.HIGH,
            ),
            recommendations = listOf(recommendation),
        )
    }

    private fun analyzeFailed(evidence: DiagnosticRunEvidence): DiagnosticAnalysisResult =
        DiagnosticAnalysisResult(
            findings = emptyList(),
            diagnosis = diagnosis(
                status = DiagnosticDiagnosisStatus.UNKNOWN,
                title = "诊断未完整完成",
                explanation = "诊断流程本身未能完成，因此无法形成可靠的网络结论。",
                confidence = DiagnosticConfidence.LOW,
            ),
            recommendations = listOf(
                recommendation(
                    code = DiagnosticRecommendationCode.RETRY_DIAGNOSTIC,
                    priority = DiagnosticRecommendationPriority.PRIMARY,
                    title = "重新运行诊断",
                    action = "请稍后重新运行诊断。",
                    reason = "本次诊断流程没有完整收集所需证据。",
                ),
            ),
        )

    private fun analyzeCompleted(evidence: DiagnosticRunEvidence): DiagnosticAnalysisResult {
        val view = EvidenceView(evidence)
        val findings = mutableListOf<DiagnosticFinding>()

        val activeNetwork = view.activeNetwork
        val networkCheck = view.firstCheck(DiagnosticCheckCode.NETWORK_STATE)
        val explicitNoNetwork = activeNetwork == false && view.activeObservation != null
        if (explicitNoNetwork) {
            findings += finding(
                code = DiagnosticFindingCode.NO_ACTIVE_NETWORK,
                title = "没有可用的活动网络",
                description = "设备当前没有可用的活动网络连接。",
                severity = DiagnosticSeverity.ERROR,
                evidenceLevel = DiagnosticEvidenceLevel.CONFIRMED,
                confidence = DiagnosticConfidence.HIGH,
                observations = listOfNotNull(view.activeObservation),
                checks = listOfNotNull(networkCheck),
                possibleCauses = listOf("Wi-Fi 或移动数据未连接", "飞行模式或 SIM/APN 状态异常"),
                recommendedActionCodes = listOf(
                    DiagnosticRecommendationCode.CHECK_WIFI_OR_MOBILE_NETWORK,
                    DiagnosticRecommendationCode.RETRY_DIAGNOSTIC,
                ),
            )
            return resultFor(findings, view)
        }

        val networkStateUnconfirmed = activeNetwork == null || networkCheck?.status ==
            DiagnosticCheckStatus.UNKNOWN
        if (networkStateUnconfirmed) {
            findings += finding(
                code = DiagnosticFindingCode.NETWORK_STATE_UNCONFIRMED,
                title = "网络状态未确认",
                description = "当前无法可靠读取活动网络状态，因此不能判断设备是否已连接网络。",
                severity = DiagnosticSeverity.NOTICE,
                evidenceLevel = DiagnosticEvidenceLevel.INCONCLUSIVE,
                confidence = DiagnosticConfidence.LOW,
                observations = listOfNotNull(view.activeObservation),
                checks = listOfNotNull(networkCheck),
                recommendedActionCodes = listOf(DiagnosticRecommendationCode.RETRY_DIAGNOSTIC),
            )
        }

        val ipCheck = view.firstCheck(DiagnosticCheckCode.IP_CONFIGURATION)
        val localAddresses = view.observationsFor(DiagnosticObservationCode.LOCAL_ADDRESS)
            .filter { it.value is DiagnosticObservationValue.AddressValue }
        val usableAddress = localAddresses.firstOrNull()
        if (!networkStateUnconfirmed && activeNetwork == true && usableAddress == null &&
            ipCheck?.status != DiagnosticCheckStatus.PASS
        ) {
            findings += finding(
                code = DiagnosticFindingCode.IP_CONFIGURATION_UNCONFIRMED,
                title = "IP 配置未确认",
                description = "设备已显示有活动网络，但当前没有可靠的 IPv4 或 IPv6 地址证据。",
                severity = DiagnosticSeverity.NOTICE,
                evidenceLevel = DiagnosticEvidenceLevel.INCONCLUSIVE,
                confidence = DiagnosticConfidence.LOW,
                observations = view.observationsFor(DiagnosticObservationCode.LOCAL_ADDRESS),
                checks = listOfNotNull(ipCheck, networkCheck),
                recommendedActionCodes = listOf(DiagnosticRecommendationCode.RETRY_DIAGNOSTIC),
            )
        }

        val public = view.publicEvidence()
        val validated = view.booleanObservation(DiagnosticObservationCode.VALIDATED_NETWORK)
        if (!networkStateUnconfirmed && public.checks.isNotEmpty() && !public.positive) {
            val validatedConflict = validated == true
            val strongNegative = public.outcomes.any {
                it == DiagnosticTcpOutcome.NO_ROUTE ||
                    it == DiagnosticTcpOutcome.NETWORK_UNREACHABLE
            }
            findings += finding(
                code = DiagnosticFindingCode.PUBLIC_CONNECTIVITY_UNCONFIRMED,
                title = if (validatedConflict) "公网连通性证据存在冲突" else "公网连接尚未确认",
                description = when {
                    validatedConflict -> "系统联网验证已通过，但本次公网 TCP 探测没有成功证据，当前无法确认公网连接状态。"
                    strongNegative -> "本次公网探测出现无路由或网络不可达结果，当前未能确认公网连接可用。"
                    else -> "当前未能确认公网连接可用；超时或适配器限制不等同于互联网已断开。"
                },
                severity = if (strongNegative && !validatedConflict) {
                    DiagnosticSeverity.WARNING
                } else {
                    DiagnosticSeverity.NOTICE
                },
                evidenceLevel = if (strongNegative && !validatedConflict) {
                    DiagnosticEvidenceLevel.SUPPORTED
                } else {
                    DiagnosticEvidenceLevel.INCONCLUSIVE
                },
                confidence = when {
                    validatedConflict -> DiagnosticConfidence.LOW
                    strongNegative -> DiagnosticConfidence.MEDIUM
                    else -> DiagnosticConfidence.MEDIUM
                },
                observations = public.observations,
                checks = public.checks,
                possibleCauses = listOf("公网路径或上游网络暂时不可用", "探测目标策略或网络策略限制"),
                recommendedActionCodes = listOf(
                    DiagnosticRecommendationCode.RETRY_DIAGNOSTIC,
                    DiagnosticRecommendationCode.COMPARE_ANOTHER_NETWORK,
                ),
            )
        }

        val gateway = view.firstCheck(DiagnosticCheckCode.GATEWAY)
        if (gateway?.status == DiagnosticCheckStatus.FAIL) {
            if (public.positive) {
                findings += finding(
                    code = DiagnosticFindingCode.GATEWAY_PROBE_NO_RESPONSE,
                    title = "默认网关未响应当前探测",
                    description = "默认网关没有响应当前探测，但公网连接正常。部分设备可能不响应此类探测，因此不能据此判断网关故障。",
                    severity = DiagnosticSeverity.NOTICE,
                    evidenceLevel = DiagnosticEvidenceLevel.CONTRADICTED,
                    confidence = DiagnosticConfidence.HIGH,
                    observations = view.observationsFor(DiagnosticObservationCode.GATEWAY_PROBE_OUTCOME) +
                        public.observations,
                    checks = listOfNotNull(gateway) + public.checks,
                    recommendedActionCodes = listOf(DiagnosticRecommendationCode.RETRY_DIAGNOSTIC),
                )
            } else if (public.checks.isNotEmpty()) {
                findings += finding(
                    code = DiagnosticFindingCode.LOCAL_OR_UPSTREAM_PATH_UNCONFIRMED,
                    title = "本地或上游网络路径未确认",
                    description = "网关与公网探测均未提供成功证据，问题可能位于本地链路、接入点、VLAN、网关、WAN 或上游网络。",
                    severity = DiagnosticSeverity.WARNING,
                    evidenceLevel = DiagnosticEvidenceLevel.SUPPORTED,
                    confidence = DiagnosticConfidence.MEDIUM,
                    observations = view.observationsFor(DiagnosticObservationCode.GATEWAY_PROBE_OUTCOME) +
                        public.observations,
                    checks = listOfNotNull(gateway) + public.checks,
                    possibleCauses = listOf("本地链路或接入点", "网关或路由器 WAN", "上游网络路径"),
                    recommendedActionCodes = listOf(
                        DiagnosticRecommendationCode.CHECK_ROUTER_WAN,
                        DiagnosticRecommendationCode.COMPARE_ANOTHER_NETWORK,
                    ),
                )
            }
        }

        addDnsFindings(evidence, view, public, findings)
        addTargetFindings(evidence, view, public, findings)
        addContextFindings(view, findings)

        val normalTransport = !networkStateUnconfirmed && activeNetwork == true &&
            usableAddress != null && public.positive && view.baselineDnsIsUsable()
        val materialFinding = findings.any { it.isMaterial() }
        if (normalTransport && !materialFinding) {
            findings += finding(
                code = DiagnosticFindingCode.NETWORK_APPEARS_NORMAL,
                title = "基础网络连接正常",
                description = "在本次检测范围内，基础网络连接表现正常。",
                severity = DiagnosticSeverity.HEALTHY,
                evidenceLevel = DiagnosticEvidenceLevel.CONFIRMED,
                confidence = DiagnosticConfidence.HIGH,
                observations = listOfNotNull(
                    view.activeObservation,
                    usableAddress,
                ) + public.observations + view.baselineDnsObservations,
                checks = listOfNotNull(
                    networkCheck,
                    ipCheck,
                    gateway,
                    view.baselineDnsCheck,
                ) + public.checks,
            )
        }

        return resultFor(findings, view)
    }

    private fun addDnsFindings(
        evidence: DiagnosticRunEvidence,
        view: EvidenceView,
        public: PublicEvidence,
        findings: MutableList<DiagnosticFinding>,
    ) {
        val baseline = view.baselineDnsOutcome
        if (baseline == DiagnosticDnsOutcome.NXDOMAIN) {
            findings += finding(
                code = DiagnosticFindingCode.DNS_NXDOMAIN,
                title = "域名被报告为不存在",
                description = "DNS 响应明确表示查询名称不存在；这只说明当前查询名称未找到，不表示 DNS 服务整体故障。",
                severity = DiagnosticSeverity.NOTICE,
                evidenceLevel = DiagnosticEvidenceLevel.CONFIRMED,
                confidence = DiagnosticConfidence.HIGH,
                observations = view.baselineDnsObservations,
                checks = listOfNotNull(view.baselineDnsCheck),
                recommendedActionCodes = listOf(DiagnosticRecommendationCode.RUN_TARGET_CHECK),
            )
        } else if (public.positive && baseline.isFailure()) {
            findings += dnsFailureFinding(
                check = view.baselineDnsCheck,
                observations = view.baselineDnsObservations,
                description = "公网连接正常，但当前 DNS 查询未正常完成。问题可能与 DNS 服务、Private DNS、VPN 或网络配置有关。",
            )
        }

        val targetDnsCheck = view.targetDnsCheck(evidence.intent.target)
        val targetDnsOutcome = targetDnsCheck?.let(view::dnsOutcome)
        if (evidence.intent.target?.kind == com.networktoolbox.core.common.diagnostic.DiagnosticTargetKind.DOMAIN &&
            targetDnsOutcome == DiagnosticDnsOutcome.NXDOMAIN &&
            targetDnsCheck != view.baselineDnsCheck
        ) {
            findings += finding(
                code = DiagnosticFindingCode.DNS_NXDOMAIN,
                title = "目标域名不存在",
                description = "DNS 响应明确表示目标查询名称不存在；这不是全局 DNS 故障结论。",
                severity = DiagnosticSeverity.NOTICE,
                evidenceLevel = DiagnosticEvidenceLevel.CONFIRMED,
                confidence = DiagnosticConfidence.HIGH,
                observations = view.observationsFor(targetDnsCheck),
                checks = listOfNotNull(targetDnsCheck),
                recommendedActionCodes = listOf(DiagnosticRecommendationCode.RUN_TARGET_CHECK),
            )
        } else if (public.positive && targetDnsOutcome.isFailure() &&
            targetDnsCheck != view.baselineDnsCheck
        ) {
            findings += dnsFailureFinding(
                check = targetDnsCheck,
                observations = view.observationsFor(targetDnsCheck),
                description = "公网连接正常，但目标域名 DNS 查询未正常完成；问题可能与当前 DNS 路径或目标名称配置有关。",
            )
        }
    }

    private fun dnsFailureFinding(
        check: DiagnosticCheck?,
        observations: List<DiagnosticObservation>,
        description: String,
    ): DiagnosticFinding = finding(
        code = DiagnosticFindingCode.DNS_RESOLUTION_FAILURE,
        title = "DNS 查询未正常完成",
        description = description,
        severity = DiagnosticSeverity.WARNING,
        evidenceLevel = DiagnosticEvidenceLevel.SUPPORTED,
        confidence = DiagnosticConfidence.HIGH,
        observations = observations,
        checks = listOfNotNull(check),
        possibleCauses = listOf("DNS 服务或当前 DNS 路径", "Private DNS、VPN 或代理配置"),
        recommendedActionCodes = listOf(
            DiagnosticRecommendationCode.RETRY_DIAGNOSTIC,
            DiagnosticRecommendationCode.CHECK_PRIVATE_DNS_VPN_PROXY,
            DiagnosticRecommendationCode.COMPARE_ANOTHER_NETWORK,
        ),
    )

    private fun addTargetFindings(
        evidence: DiagnosticRunEvidence,
        view: EvidenceView,
        public: PublicEvidence,
        findings: MutableList<DiagnosticFinding>,
    ) {
        if (evidence.intent.target == null) return
        val targetChecks = view.targetChecks
        if (targetChecks.isEmpty()) return
        val targetOutcomes = targetChecks.map { check ->
            view.tcpOutcome(check, DiagnosticObservationCode.TARGET_TCP_OUTCOME)
        }
        if (targetOutcomes.any { it == DiagnosticTcpOutcome.CONNECT_SUCCESS }) return

        val targetEvidence = targetChecks.flatMap(view::observationsFor)
        when {
            targetOutcomes.any { it == DiagnosticTcpOutcome.CONNECTION_REFUSED } -> findings += finding(
                code = DiagnosticFindingCode.TARGET_TCP_REFUSED,
                title = "目标端口未接受连接",
                description = "目标端口未接受连接，但目标地址路径存在明确响应；这不等同于路由或互联网故障。",
                severity = DiagnosticSeverity.WARNING,
                evidenceLevel = DiagnosticEvidenceLevel.SUPPORTED,
                confidence = DiagnosticConfidence.HIGH,
                observations = targetEvidence,
                checks = targetChecks,
                recommendedActionCodes = listOf(DiagnosticRecommendationCode.RUN_TARGET_CHECK),
            )

            targetOutcomes.any {
                it == DiagnosticTcpOutcome.NO_ROUTE ||
                    it == DiagnosticTcpOutcome.NETWORK_UNREACHABLE
            } -> findings += finding(
                code = DiagnosticFindingCode.TARGET_TCP_PATH_UNCONFIRMED,
                title = "目标地址路径未确认",
                description = if (public.positive) {
                    "公网路径已有成功证据，但目标地址返回无路由或网络不可达；问题可能与目标地址族或目标路径有关。"
                } else {
                    "目标地址返回无路由或网络不可达，但公网证据不足，无法将问题归因于目标服务。"
                },
                severity = if (public.positive) DiagnosticSeverity.WARNING else DiagnosticSeverity.NOTICE,
                evidenceLevel = DiagnosticEvidenceLevel.INCONCLUSIVE,
                confidence = if (public.positive) DiagnosticConfidence.MEDIUM else DiagnosticConfidence.LOW,
                observations = targetEvidence,
                checks = targetChecks,
                possibleCauses = listOf("目标地址族或访问路径", "目标网络策略"),
                recommendedActionCodes = listOf(DiagnosticRecommendationCode.RUN_TARGET_CHECK),
            )

            targetOutcomes.any { it == DiagnosticTcpOutcome.TIMEOUT } -> findings += finding(
                code = DiagnosticFindingCode.TARGET_TCP_TIMEOUT,
                title = "目标连接未及时响应",
                description = if (public.positive) {
                    "公网路径已有成功证据，但目标服务或访问路径未及时响应；不能据此判断网站或服务已停止。"
                } else {
                    "目标连接未及时响应，且当前公网证据不足；无法区分目标服务与网络路径问题。"
                },
                severity = if (public.positive) DiagnosticSeverity.WARNING else DiagnosticSeverity.NOTICE,
                evidenceLevel = DiagnosticEvidenceLevel.INCONCLUSIVE,
                confidence = if (public.positive) DiagnosticConfidence.MEDIUM else DiagnosticConfidence.LOW,
                observations = targetEvidence,
                checks = targetChecks,
                possibleCauses = listOf("目标服务或目标访问路径", "防火墙或网络策略"),
                recommendedActionCodes = listOf(DiagnosticRecommendationCode.RUN_TARGET_CHECK),
            )
        }
    }

    private fun addContextFindings(view: EvidenceView, findings: MutableList<DiagnosticFinding>) {
        val captive = view.observationsFor(DiagnosticObservationCode.CAPTIVE_PORTAL)
            .firstOrNull { it.booleanValue() == true }
        if (captive != null) {
            findings += finding(
                code = DiagnosticFindingCode.CAPTIVE_PORTAL_CONTEXT,
                title = "当前网络可能需要登录认证",
                description = "当前网络可能需要完成系统登录认证；这不是路由器或 DNS 服务故障结论。",
                severity = DiagnosticSeverity.NOTICE,
                evidenceLevel = DiagnosticEvidenceLevel.CONFIRMED,
                confidence = DiagnosticConfidence.HIGH,
                observations = listOf(captive),
                checks = listOfNotNull(view.firstCheck(DiagnosticCheckCode.PUBLIC_CONNECTIVITY)),
                recommendedActionCodes = listOf(DiagnosticRecommendationCode.CHECK_CAPTIVE_PORTAL),
            )
        }

        val fakeIp = view.observations.flatMap { observation ->
            when (val value = observation.value) {
                is DiagnosticObservationValue.TextValue -> {
                    if (isFakeIp(value.value)) listOf(observation) else emptyList()
                }
                is DiagnosticObservationValue.DnsRecordValue -> {
                    if (isFakeIp(value.value)) listOf(observation) else emptyList()
                }
                else -> emptyList()
            }
        }.filter { it.code == DiagnosticObservationCode.FAKE_IP_RANGE_MATCH ||
            it.code == DiagnosticObservationCode.DNS_RECORD }
        if (fakeIp.isNotEmpty()) {
            findings += finding(
                code = DiagnosticFindingCode.FAKE_IP_CONTEXT,
                title = "检测到特殊用途地址",
                description = "检测到 198.18.0.0/15 特殊用途地址，可能存在 Fake-IP DNS 环境；这不等同于 DNS 错误。",
                severity = DiagnosticSeverity.NOTICE,
                evidenceLevel = DiagnosticEvidenceLevel.CONFIRMED,
                confidence = DiagnosticConfidence.HIGH,
                observations = fakeIp,
                checks = listOfNotNull(view.baselineDnsCheck),
                recommendedActionCodes = listOf(DiagnosticRecommendationCode.CHECK_PRIVATE_DNS_VPN_PROXY),
            )
        }

        val vpn = view.observationsFor(DiagnosticObservationCode.VPN_ACTIVE)
            .firstOrNull { it.booleanValue() == true }
        if (vpn != null) {
            findings += finding(
                code = DiagnosticFindingCode.VPN_ACTIVE,
                title = "检测到 VPN 网络",
                description = "当前通过 VPN 的联网路径在本次检测中表现正常或已被单独记录；以下结果可能反映 VPN 隧道后的网络环境。",
                severity = DiagnosticSeverity.NOTICE,
                evidenceLevel = DiagnosticEvidenceLevel.CONFIRMED,
                confidence = DiagnosticConfidence.HIGH,
                observations = listOf(vpn),
                checks = listOfNotNull(view.firstCheck(DiagnosticCheckCode.NETWORK_STATE)),
            )
        }
    }

    private fun resultFor(
        findings: MutableList<DiagnosticFinding>,
        view: EvidenceView,
    ): DiagnosticAnalysisResult {
        val normalFinding = findings.firstOrNull {
            it.code == DiagnosticFindingCode.NETWORK_APPEARS_NORMAL
        }
        val material = findings
            .filter { it.isMaterial() }
            .minByOrNull { it.primaryPriority() }
        val publicUnconfirmed = findings.any {
            it.code == DiagnosticFindingCode.PUBLIC_CONNECTIVITY_UNCONFIRMED
        }
        val networkUnknown = findings.any {
            it.code == DiagnosticFindingCode.NETWORK_STATE_UNCONFIRMED ||
                it.code == DiagnosticFindingCode.IP_CONFIGURATION_UNCONFIRMED
        }
        val captiveOnlyRestriction = findings.any {
            it.code == DiagnosticFindingCode.CAPTIVE_PORTAL_CONTEXT
        } && !view.publicEvidence().positive

        val diagnosis = when {
            normalFinding != null && material == null -> diagnosis(
                status = DiagnosticDiagnosisStatus.NORMAL,
                title = "基础网络连接正常",
                explanation = "在本次检测范围内，基础网络连接表现正常；这不代表所有应用或网站都一定正常。",
                primaryFindingCode = DiagnosticFindingCode.NETWORK_APPEARS_NORMAL,
                confidence = DiagnosticConfidence.HIGH,
            )

            material?.code == DiagnosticFindingCode.NO_ACTIVE_NETWORK -> diagnosis(
                status = DiagnosticDiagnosisStatus.ATTENTION,
                title = "设备当前没有可用网络",
                explanation = "设备当前没有可用的活动网络连接，请先检查 Wi-Fi 或移动数据。",
                primaryFindingCode = material.code,
                confidence = material.confidence,
            )

            captiveOnlyRestriction && material == null -> diagnosis(
                status = DiagnosticDiagnosisStatus.LIMITED,
                title = "网络访问可能受限",
                explanation = "当前网络可能需要完成登录认证，部分公网访问能力可能受到限制。",
                primaryFindingCode = DiagnosticFindingCode.CAPTIVE_PORTAL_CONTEXT,
                confidence = DiagnosticConfidence.HIGH,
            )

            material?.code == DiagnosticFindingCode.PUBLIC_CONNECTIVITY_UNCONFIRMED &&
                material.severity == DiagnosticSeverity.WARNING -> diagnosis(
                status = DiagnosticDiagnosisStatus.ATTENTION,
                title = "公网连接尚未确认",
                explanation = "当前未能确认公网连接可用，问题可能位于本地、WAN 或上游网络路径。",
                primaryFindingCode = material.code,
                confidence = material.confidence,
            )

            material != null -> diagnosis(
                status = DiagnosticDiagnosisStatus.ATTENTION,
                title = "发现需要关注的网络现象",
                explanation = material.description,
                primaryFindingCode = material.code,
                confidence = material.confidence,
                possibleCauses = material.possibleCauses,
            )

            networkUnknown || publicUnconfirmed -> diagnosis(
                status = DiagnosticDiagnosisStatus.UNKNOWN,
                title = "当前无法确认整体网络状态",
                explanation = "证据不足或存在冲突，当前结果不足以形成可靠的整体网络结论。",
                confidence = DiagnosticConfidence.LOW,
            )

            else -> diagnosis(
                status = DiagnosticDiagnosisStatus.UNKNOWN,
                title = "当前无法确认整体网络状态",
                explanation = "本次检测没有收集到足够的证据来形成可靠结论。",
                confidence = DiagnosticConfidence.LOW,
            )
        }

        return DiagnosticAnalysisResult(
            findings = findings.toList(),
            diagnosis = diagnosis,
            recommendations = recommendations(findings, view, diagnosis),
        )
    }

    private fun recommendations(
        findings: List<DiagnosticFinding>,
        view: EvidenceView,
        diagnosis: DiagnosticDiagnosis,
    ): List<DiagnosticRecommendation> {
        val codes = findings.mapTo(linkedSetOf()) { it.code }
        val candidates = buildList {
            fun addFor(
                code: DiagnosticRecommendationCode,
                priority: DiagnosticRecommendationPriority,
                title: String,
                action: String,
                reason: String,
                related: List<DiagnosticFindingCode>,
            ) = add(
                recommendation(
                    code = code,
                    priority = priority,
                    title = title,
                    action = action,
                    reason = reason,
                    relatedFindingCodes = related,
                ),
            )

            when {
                DiagnosticFindingCode.NO_ACTIVE_NETWORK in codes -> addFor(
                    DiagnosticRecommendationCode.CHECK_WIFI_OR_MOBILE_NETWORK,
                    DiagnosticRecommendationPriority.PRIMARY,
                    "检查网络连接",
                    "检查 Wi-Fi 或移动数据，并确认未开启飞行模式。",
                    "当前没有可用的活动网络。",
                    listOf(DiagnosticFindingCode.NO_ACTIVE_NETWORK),
                )

                DiagnosticFindingCode.DNS_RESOLUTION_FAILURE in codes -> addFor(
                    DiagnosticRecommendationCode.RETRY_DIAGNOSTIC,
                    DiagnosticRecommendationPriority.PRIMARY,
                    "重新进行 DNS 查询",
                    "再次执行诊断以确认 DNS 是否恢复。",
                    "公网路径已有成功证据，但 DNS 查询未正常完成。",
                    listOf(DiagnosticFindingCode.DNS_RESOLUTION_FAILURE),
                )

                DiagnosticFindingCode.LOCAL_OR_UPSTREAM_PATH_UNCONFIRMED in codes -> addFor(
                    DiagnosticRecommendationCode.CHECK_ROUTER_WAN,
                    DiagnosticRecommendationPriority.PRIMARY,
                    "检查本地与上游连接",
                    "检查接入点、路由器 WAN 和上游连接状态。",
                    "网关与公网探测均未提供成功证据。",
                    listOf(DiagnosticFindingCode.LOCAL_OR_UPSTREAM_PATH_UNCONFIRMED),
                )

                DiagnosticFindingCode.PUBLIC_CONNECTIVITY_UNCONFIRMED in codes -> addFor(
                    DiagnosticRecommendationCode.RETRY_DIAGNOSTIC,
                    DiagnosticRecommendationPriority.PRIMARY,
                    "重新运行诊断",
                    "在网络稳定后重新运行诊断，并尝试对比其他网络。",
                    "当前公网探测证据不足或与系统联网验证冲突。",
                    listOf(DiagnosticFindingCode.PUBLIC_CONNECTIVITY_UNCONFIRMED),
                )

                DiagnosticFindingCode.TARGET_TCP_REFUSED in codes ||
                    DiagnosticFindingCode.TARGET_TCP_TIMEOUT in codes ||
                    DiagnosticFindingCode.TARGET_TCP_PATH_UNCONFIRMED in codes ||
                    DiagnosticFindingCode.DNS_NXDOMAIN in codes -> addFor(
                    DiagnosticRecommendationCode.RUN_TARGET_CHECK,
                    DiagnosticRecommendationPriority.PRIMARY,
                    "核对目标",
                    "确认目标名称、地址族、端口和服务配置。",
                    "当前现象更接近目标特定问题，不能直接归因于整体网络。",
                    listOfNotNull(
                        DiagnosticFindingCode.TARGET_TCP_REFUSED.takeIf { it in codes },
                        DiagnosticFindingCode.TARGET_TCP_TIMEOUT.takeIf { it in codes },
                        DiagnosticFindingCode.TARGET_TCP_PATH_UNCONFIRMED.takeIf { it in codes },
                        DiagnosticFindingCode.DNS_NXDOMAIN.takeIf { it in codes },
                    ),
                )
            }

            if (DiagnosticFindingCode.NO_ACTIVE_NETWORK in codes) {
                addFor(
                    DiagnosticRecommendationCode.RETRY_DIAGNOSTIC,
                    DiagnosticRecommendationPriority.SECONDARY,
                    "重新运行诊断",
                    "连接网络后重新运行诊断。",
                    "重新检测可以确认网络状态是否已经恢复。",
                    listOf(DiagnosticFindingCode.NO_ACTIVE_NETWORK),
                )
            }

            if (DiagnosticFindingCode.CAPTIVE_PORTAL_CONTEXT in codes) {
                addFor(
                    DiagnosticRecommendationCode.CHECK_CAPTIVE_PORTAL,
                    DiagnosticRecommendationPriority.PRIMARY,
                    "完成网络认证",
                    "检查系统网络登录提示并完成认证。",
                    "系统报告当前网络可能需要登录认证。",
                    listOf(DiagnosticFindingCode.CAPTIVE_PORTAL_CONTEXT),
                )
            }

            if (DiagnosticFindingCode.DNS_RESOLUTION_FAILURE in codes ||
                DiagnosticFindingCode.FAKE_IP_CONTEXT in codes ||
                DiagnosticFindingCode.VPN_ACTIVE in codes
            ) {
                addFor(
                    DiagnosticRecommendationCode.CHECK_PRIVATE_DNS_VPN_PROXY,
                    DiagnosticRecommendationPriority.SECONDARY,
                    "检查 DNS 环境",
                    "检查 Private DNS、VPN 或代理设置。",
                    "这些上下文可能影响名称解析与访问路径。",
                    listOfNotNull(
                        DiagnosticFindingCode.DNS_RESOLUTION_FAILURE.takeIf {
                            it in codes
                        },
                        DiagnosticFindingCode.FAKE_IP_CONTEXT.takeIf { it in codes },
                        DiagnosticFindingCode.VPN_ACTIVE.takeIf { it in codes },
                    ),
                )
            }

            if (DiagnosticFindingCode.LOCAL_OR_UPSTREAM_PATH_UNCONFIRMED in codes ||
                DiagnosticFindingCode.PUBLIC_CONNECTIVITY_UNCONFIRMED in codes ||
                diagnosis.status == DiagnosticDiagnosisStatus.NORMAL
            ) {
                addFor(
                    DiagnosticRecommendationCode.COMPARE_ANOTHER_NETWORK,
                    DiagnosticRecommendationPriority.SECONDARY,
                    "对比其他网络",
                    "尝试使用另一 Wi-Fi 或移动网络进行对比。",
                    "不同网络的结果有助于区分本地环境与目标路径问题。",
                    listOfNotNull(
                        DiagnosticFindingCode.LOCAL_OR_UPSTREAM_PATH_UNCONFIRMED.takeIf {
                            it in codes
                        },
                        DiagnosticFindingCode.PUBLIC_CONNECTIVITY_UNCONFIRMED.takeIf {
                            it in codes
                        },
                        DiagnosticFindingCode.NO_ACTIVE_NETWORK.takeIf { it in codes },
                        DiagnosticFindingCode.NETWORK_APPEARS_NORMAL.takeIf {
                            diagnosis.status == DiagnosticDiagnosisStatus.NORMAL
                        },
                    ),
                )
            }

            if (diagnosis.status == DiagnosticDiagnosisStatus.NORMAL &&
                view.evidence.intent.target == null
            ) {
                addFor(
                    DiagnosticRecommendationCode.RUN_TARGET_CHECK,
                    DiagnosticRecommendationPriority.OPTIONAL,
                    "运行目标检测",
                    "如果仍然无法访问某个服务，可以运行目标检测。",
                    "基础检测正常不代表所有应用或网站都一定正常。",
                    listOf(DiagnosticFindingCode.NETWORK_APPEARS_NORMAL),
                )
            }
        }

        return candidates.distinctBy { it.code }.take(3)
    }

    private fun finding(
        code: DiagnosticFindingCode,
        title: String,
        description: String,
        severity: DiagnosticSeverity,
        evidenceLevel: DiagnosticEvidenceLevel,
        confidence: DiagnosticConfidence,
        observations: List<DiagnosticObservation> = emptyList(),
        checks: List<DiagnosticCheck> = emptyList(),
        possibleCauses: List<String> = emptyList(),
        recommendedActionCodes: List<DiagnosticRecommendationCode> = emptyList(),
    ): DiagnosticFinding {
        val observationIds = observations.map { it.id }.distinct()
        val checkCodes = checks.map { it.code }.distinct()
        require(observationIds.isNotEmpty() || checkCodes.isNotEmpty()) {
            "Diagnostic finding must reference evidence."
        }
        return DiagnosticFinding(
            code = code,
            title = title,
            description = description,
            severity = severity,
            evidenceLevel = evidenceLevel,
            confidence = confidence,
            evidenceObservationIds = observationIds,
            evidenceCheckCodes = checkCodes,
            possibleCauses = possibleCauses,
            recommendedActionCodes = recommendedActionCodes,
        )
    }

    private fun diagnosis(
        status: DiagnosticDiagnosisStatus,
        title: String,
        explanation: String,
        primaryFindingCode: DiagnosticFindingCode? = null,
        confidence: DiagnosticConfidence,
        possibleCauses: List<String> = emptyList(),
    ) = DiagnosticDiagnosis(
        status = status,
        title = title,
        explanation = explanation,
        primaryFindingCode = primaryFindingCode,
        confidence = confidence,
        possibleCauses = possibleCauses,
    )

    private fun recommendation(
        code: DiagnosticRecommendationCode,
        priority: DiagnosticRecommendationPriority,
        title: String,
        action: String,
        reason: String,
        relatedFindingCodes: List<DiagnosticFindingCode> = emptyList(),
    ) = DiagnosticRecommendation(
        code = code,
        priority = priority,
        title = title,
        action = action,
        reason = reason,
        relatedFindingCodes = relatedFindingCodes,
    )

    private class EvidenceView(val evidence: DiagnosticRunEvidence) {
        val observations: List<DiagnosticObservation> = evidence.observations
        val checks: List<DiagnosticCheck> = evidence.checks
        val activeObservation: DiagnosticObservation? = observations.firstOrNull {
            it.code == DiagnosticObservationCode.ACTIVE_NETWORK_AVAILABLE
        }
        val activeNetwork: Boolean? = activeObservation?.let {
            (it.value as? DiagnosticObservationValue.BooleanValue)?.value
        }
        val targetChecks: List<DiagnosticCheck> = checks.filter {
            it.stage == DiagnosticStage.TARGET &&
                it.code == DiagnosticCheckCode.TARGET_CONNECTIVITY
        }
        val baselineDnsCheck: DiagnosticCheck? = checks.firstOrNull {
            it.stage == DiagnosticStage.DNS && it.code == DiagnosticCheckCode.DNS_RESOLUTION
        }
        val baselineDnsOutcome: DiagnosticDnsOutcome? = baselineDnsCheck?.let(::dnsOutcome)
        val baselineDnsObservations: List<DiagnosticObservation> =
            observationsFor(baselineDnsCheck)

        fun firstCheck(code: DiagnosticCheckCode): DiagnosticCheck? = checks.firstOrNull {
            it.code == code
        }

        fun observationsFor(code: DiagnosticObservationCode): List<DiagnosticObservation> =
            observations.filter { it.code == code }

        fun observationsFor(check: DiagnosticCheck?): List<DiagnosticObservation> {
            if (check == null) return emptyList()
            val referenced = check.evidenceObservationIds.toSet()
            return observations.filter { it.id in referenced }
        }

        fun booleanObservation(code: DiagnosticObservationCode): Boolean? =
            observationsFor(code).firstNotNullOfOrNull {
                (it.value as? DiagnosticObservationValue.BooleanValue)?.value
            }

        fun tcpOutcome(
            check: DiagnosticCheck,
            code: DiagnosticObservationCode,
        ): DiagnosticTcpOutcome = observations
            .filter { it.id in check.evidenceObservationIds && it.code == code }
            .firstNotNullOfOrNull { (it.value as? DiagnosticObservationValue.TcpOutcomeValue)?.outcome }
            ?: DiagnosticTcpOutcome.UNKNOWN

        fun dnsOutcome(check: DiagnosticCheck): DiagnosticDnsOutcome = observations
            .filter {
                it.id in check.evidenceObservationIds &&
                    it.code == DiagnosticObservationCode.DNS_OUTCOME
            }
            .firstNotNullOfOrNull { (it.value as? DiagnosticObservationValue.DnsOutcomeValue)?.outcome }
            ?: when (check.status) {
                DiagnosticCheckStatus.PASS -> DiagnosticDnsOutcome.SUCCESS
                DiagnosticCheckStatus.NO_RECORDS -> DiagnosticDnsOutcome.NO_RECORDS
                else -> DiagnosticDnsOutcome.UNKNOWN
            }

        fun publicEvidence(): PublicEvidence {
            val publicChecks = checks.filter {
                it.stage == DiagnosticStage.INTERNET &&
                    it.code == DiagnosticCheckCode.PUBLIC_CONNECTIVITY
            }
            val outcomes = publicChecks.map { tcpOutcome(it, DiagnosticObservationCode.PUBLIC_TCP_OUTCOME) }
            return PublicEvidence(
                checks = publicChecks,
                outcomes = outcomes,
                observations = publicChecks.flatMap(::observationsFor),
            )
        }

        fun baselineDnsIsUsable(): Boolean = baselineDnsOutcome == DiagnosticDnsOutcome.SUCCESS ||
            baselineDnsOutcome == DiagnosticDnsOutcome.NO_RECORDS

        fun targetDnsCheck(target: com.networktoolbox.core.common.diagnostic.DiagnosticTarget?): DiagnosticCheck? {
            if (target?.kind != com.networktoolbox.core.common.diagnostic.DiagnosticTargetKind.DOMAIN) {
                return null
            }
            return checks.filter {
                it.stage == DiagnosticStage.DNS && it.code == DiagnosticCheckCode.DNS_RESOLUTION
            }.drop(1).firstOrNull()
        }
    }

    private data class PublicEvidence(
        val checks: List<DiagnosticCheck>,
        val outcomes: List<DiagnosticTcpOutcome>,
        val observations: List<DiagnosticObservation>,
    ) {
        val positive: Boolean = outcomes.any {
            it == DiagnosticTcpOutcome.CONNECT_SUCCESS ||
                it == DiagnosticTcpOutcome.CONNECTION_REFUSED
        }
    }

    private fun DiagnosticFinding.isMaterial(): Boolean = when (code) {
        DiagnosticFindingCode.NO_ACTIVE_NETWORK,
        DiagnosticFindingCode.IP_CONFIGURATION_UNCONFIRMED,
        DiagnosticFindingCode.LOCAL_OR_UPSTREAM_PATH_UNCONFIRMED,
        DiagnosticFindingCode.DNS_RESOLUTION_FAILURE,
        DiagnosticFindingCode.DNS_NXDOMAIN,
        DiagnosticFindingCode.TARGET_TCP_REFUSED,
        DiagnosticFindingCode.TARGET_TCP_TIMEOUT,
        DiagnosticFindingCode.TARGET_TCP_PATH_UNCONFIRMED,
        -> true

        DiagnosticFindingCode.PUBLIC_CONNECTIVITY_UNCONFIRMED ->
            severity == DiagnosticSeverity.WARNING

        else -> false
    }

    private fun DiagnosticFinding.primaryPriority(): Int = when (code) {
        DiagnosticFindingCode.NO_ACTIVE_NETWORK -> 0
        DiagnosticFindingCode.LOCAL_OR_UPSTREAM_PATH_UNCONFIRMED -> 1
        DiagnosticFindingCode.DNS_RESOLUTION_FAILURE -> 2
        DiagnosticFindingCode.DNS_NXDOMAIN -> 3
        DiagnosticFindingCode.PUBLIC_CONNECTIVITY_UNCONFIRMED -> 4
        DiagnosticFindingCode.IP_CONFIGURATION_UNCONFIRMED -> 5
        DiagnosticFindingCode.TARGET_TCP_REFUSED,
        DiagnosticFindingCode.TARGET_TCP_TIMEOUT,
        DiagnosticFindingCode.TARGET_TCP_PATH_UNCONFIRMED,
        -> 6

        else -> 7
    }

    private fun DiagnosticDnsOutcome?.isFailure(): Boolean = when (this) {
        DiagnosticDnsOutcome.TIMEOUT,
        DiagnosticDnsOutcome.NETWORK_ERROR,
        DiagnosticDnsOutcome.INVALID_RESPONSE,
        DiagnosticDnsOutcome.PARTIAL,
        -> true

        else -> false
    }

    private fun DiagnosticObservation.booleanValue(): Boolean? =
        (value as? DiagnosticObservationValue.BooleanValue)?.value

    private fun isFakeIp(value: String): Boolean {
        val parts = value.substringBefore('%').split('.')
        if (parts.size != 4 || parts.any { it.toIntOrNull() == null }) return false
        val octets = parts.map { it.toInt() }
        if (octets.any { it !in 0..255 }) return false
        return octets[0] == 198 && octets[1] in 18..19
    }

}
