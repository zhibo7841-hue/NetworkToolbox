package com.networktoolbox.feature.report.diagnostic.v2

import com.networktoolbox.core.network.dns.DnsLookupStatus
import com.networktoolbox.core.network.dns.DnsRecordType

interface DiagnosticAnalyzerV2 {
    fun analyze(input: DiagnosticPipelineResult): DiagnosticReportV2
}

/**
 * Conservative, deterministic interpretation of the pipeline evidence.
 * Findings describe observed boundaries and possible causes; they do not claim
 * to identify a router, ISP, DNS provider, or service as definitely broken.
 */
class DefaultDiagnosticAnalyzerV2 : DiagnosticAnalyzerV2 {
    override fun analyze(input: DiagnosticPipelineResult): DiagnosticReportV2 {
        val findings = mutableListOf<DiagnosticFindingV2>()
        val context = input.networkContext
        val networkCheck = input.checks.byId(CHECK_NETWORK_CONTEXT)
        val gatewayCheck = input.checks.byId(CHECK_GATEWAY)
        val publicCheck = input.checks.byId(CHECK_PUBLIC)
        val dnsCheck = input.checks.byId(CHECK_DNS)
        val domainCheck = input.checks.byId(CHECK_DOMAIN)
        val publicPassed = publicCheck?.status == DiagnosticCheckStatus.PASS
        val publicFailed = publicCheck?.status == DiagnosticCheckStatus.FAIL
        val publicUncertain = publicCheck?.status == DiagnosticCheckStatus.UNKNOWN
        val gatewayFailed = gatewayCheck?.status == DiagnosticCheckStatus.FAIL
        val dnsResult = input.dnsResult
        val dnsHasA = dnsResult?.records.orEmpty().any { it.type == DnsRecordType.A }
        val dnsHasAaaa = dnsResult?.records.orEmpty().any { it.type == DnsRecordType.AAAA }
        val dnsActualFailure = dnsResult?.status?.isActualFailure() == true

        if (context?.activeNetworkAvailable == false) {
            findings += finding(
                id = FINDING_NO_NETWORK,
                severity = DiagnosticSeverity.ERROR,
                title = "没有活动网络",
                description = "设备当前未连接可用网络，后续网络探测未执行。",
                evidence = listOf(CHECK_NETWORK_CONTEXT),
            )
        }

        when {
            gatewayFailed && publicFailed -> findings += finding(
                id = FINDING_LOCAL_AND_PUBLIC,
                severity = DiagnosticSeverity.WARNING,
                title = "本地链路或公网路径需要关注",
                description = "本机有网络状态，但网关与公网 TCP 探测均未成功；问题可能位于本地链路、网关、路由器 WAN 或上游路径。",
                evidence = listOf(CHECK_GATEWAY, CHECK_PUBLIC),
            )

            gatewayFailed && publicPassed -> findings += finding(
                id = FINDING_PING_TCP_DIFFERENCE,
                severity = DiagnosticSeverity.NOTICE,
                title = "Ping 与 TCP 结果不一致",
                description = "网关的系统可达性检测未成功，但公网 TCP 连接可建立；Ping 可能受到过滤或检测方式限制，不能据此判断公网已断开。",
                evidence = listOf(CHECK_GATEWAY, CHECK_PUBLIC),
            )

            gatewayCheck?.status == DiagnosticCheckStatus.UNKNOWN && publicPassed ->
                findings += finding(
                    id = FINDING_PING_TCP_DIFFERENCE,
                    severity = DiagnosticSeverity.NOTICE,
                    title = "本地网关未确认",
                    description = "网关未响应可达性探测，但设备仍可正常访问公网。部分路由器可能不响应此类探测，这不一定表示网关异常。",
                    evidence = listOf(CHECK_GATEWAY, CHECK_PUBLIC),
                )

            gatewayFailed -> findings += finding(
                id = FINDING_GATEWAY,
                severity = DiagnosticSeverity.WARNING,
                title = "网关检测未成功",
                description = "本机有网络地址，但网关检测未成功；问题可能位于本地链路、接入点、VLAN 或网关路径。",
                evidence = listOf(CHECK_NETWORK_CONTEXT, CHECK_GATEWAY),
            )
        }

        if (publicFailed && !gatewayFailed) {
            findings += finding(
                id = FINDING_PUBLIC,
                severity = DiagnosticSeverity.WARNING,
                title = "公网连通性检测未通过",
                description = "本地网络信息不足以确认公网路径正常；多个公网 TCP 目标均未建立连接，问题可能与路由器 WAN、上游网络或目标策略有关。",
                evidence = listOf(CHECK_PUBLIC),
            )
        }

        if (publicUncertain) {
            findings += finding(
                id = FINDING_PUBLIC_UNCERTAIN,
                severity = DiagnosticSeverity.NOTICE,
                title = "公网连通性尚未确认",
                description = "固定公网探测和实际域名访问未能提供一致的成功证据，但当前结果不足以证明互联网不可用。",
                evidence = listOf(CHECK_PUBLIC, CHECK_DOMAIN).filter { id ->
                    input.checks.any { it.id == id }
                },
            )
        }

        if (publicPassed && input.publicConnectivity?.hasSuccessfulTarget != true &&
            domainCheck?.status == DiagnosticCheckStatus.PASS
        ) {
            findings += finding(
                id = FINDING_FIXED_TARGETS_INCONCLUSIVE,
                severity = DiagnosticSeverity.NOTICE,
                title = "部分公网探测目标未响应",
                description = "固定公网探测目标未响应，但实际域名访问正常；这可能与网络策略、路由或目标服务限制有关，不表示互联网连接异常。",
                evidence = listOf(CHECK_PUBLIC, CHECK_DNS, CHECK_DOMAIN),
            )
        }

        if (dnsActualFailure) {
            val dnsFindingSeverity = when {
                dnsResult?.status == DnsLookupStatus.PARTIAL -> DiagnosticSeverity.WARNING
                publicPassed -> DiagnosticSeverity.ERROR
                else -> DiagnosticSeverity.WARNING
            }
            findings += finding(
                id = FINDING_DNS,
                severity = dnsFindingSeverity,
                title = if (dnsResult?.status == DnsLookupStatus.NXDOMAIN) {
                    "域名被报告为不存在"
                } else {
                    "DNS 查询未正常完成"
                },
                description = if (publicPassed) {
                    "公网连接正常，但当前 DNS 查询失败；问题可能与 DNS 服务、Private DNS、VPN 或网络配置有关。"
                } else {
                    "当前 DNS 查询未正常完成，可能与 DNS 服务、网络路径或配置有关。"
                },
                evidence = listOf(CHECK_PUBLIC, CHECK_DNS).filter { id ->
                    input.checks.any { it.id == id }
                },
            )
        } else if (dnsResult?.status == DnsLookupStatus.NO_RECORDS) {
            findings += finding(
                id = FINDING_NO_DNS_RECORDS,
                severity = DiagnosticSeverity.NOTICE,
                title = "DNS 查询没有返回记录",
                description = "查询正常完成，但所请求的记录类型没有返回结果；这不等同于 DNS 传输失败。",
                evidence = listOf(CHECK_DNS),
            )
        }

        if (dnsResult != null && dnsHasA &&
            DnsRecordType.AAAA in dnsResult.requestedTypes && !dnsHasAaaa &&
            dnsResult.status == DnsLookupStatus.SUCCESS
        ) {
            findings += finding(
                id = FINDING_NO_IPV6_RECORD,
                severity = DiagnosticSeverity.NOTICE,
                title = "未发现 IPv6 记录",
                description = "域名有 IPv4 记录，但没有发布 IPv6 记录；这不一定是网络故障。",
                evidence = listOf(CHECK_DNS),
            )
        }

        if (dnsResult?.records.orEmpty().any { record ->
            DiagnosticAddressClassifier.isFakeIp(record.value)
        }) {
            findings += finding(
                id = FINDING_FAKE_IP,
                severity = DiagnosticSeverity.NOTICE,
                title = "检测到特殊用途地址",
                description = "结果包含 198.18.0.0/15 地址，可能存在 Fake-IP DNS 环境；这不等同于 DNS 错误，诊断流程仍会继续。",
                evidence = listOf(CHECK_DNS),
            )
        }

        if (context?.vpnActive == true) {
            findings += finding(
                id = FINDING_VPN,
                severity = DiagnosticSeverity.NOTICE,
                title = "检测到 VPN 网络",
                description = "以下结果可能反映 VPN 隧道后的网络环境，不一定代表物理网络的直接状态。",
                evidence = listOf(CHECK_NETWORK_CONTEXT),
            )
        }

        if (publicPassed && !dnsActualFailure && domainCheck?.status == DiagnosticCheckStatus.FAIL) {
            findings += finding(
                id = FINDING_DOMAIN_ACCESS,
                severity = DiagnosticSeverity.WARNING,
                title = "域名访问路径未建立",
                description = "公网 TCP 目标可达且域名已解析，但解析地址的 TCP 连接未建立；原因可能与目标服务、地址族或防火墙策略有关。",
                evidence = listOf(CHECK_PUBLIC, CHECK_DNS, CHECK_DOMAIN),
            )
        }

        if (input.networkChanged) {
            findings += finding(
                id = FINDING_NETWORK_CHANGED,
                severity = DiagnosticSeverity.NOTICE,
                title = "检测期间网络发生切换",
                description = "检测期间网络状态发生变化，部分结果可能来自不同网络；建议在稳定网络下重新运行诊断。",
                evidence = listOf(CHECK_NETWORK_CHANGED),
            )
        }

        val overallSeverity = findings.maxByOrNull { it.severity.rank }?.severity
            ?: if (publicUncertain || networkCheck?.status == DiagnosticCheckStatus.UNKNOWN) {
                DiagnosticSeverity.NOTICE
            } else {
                DiagnosticSeverity.HEALTHY
            }
        val overallStatus = when {
            input.checks.isEmpty() -> DiagnosticOverallStatus.UNKNOWN
            overallSeverity == DiagnosticSeverity.ERROR -> DiagnosticOverallStatus.LIMITED
            overallSeverity == DiagnosticSeverity.WARNING -> DiagnosticOverallStatus.ATTENTION
            publicUncertain -> DiagnosticOverallStatus.UNKNOWN
            networkCheck?.status == DiagnosticCheckStatus.UNKNOWN &&
                input.checks.none { it.status == DiagnosticCheckStatus.PASS } ->
                DiagnosticOverallStatus.UNKNOWN

            else -> DiagnosticOverallStatus.HEALTHY
        }

        return DiagnosticReportV2(
            timestamp = input.startedAt,
            durationMs = (input.endedAt - input.startedAt).coerceAtLeast(0L),
            overallStatus = overallStatus,
            overallSeverity = overallSeverity,
            summary = summary(
                context = context,
                gatewayFailed = gatewayFailed,
                publicFailed = publicFailed,
                publicPassed = publicPassed,
                publicUncertain = publicUncertain,
                dnsActualFailure = dnsActualFailure,
                domainFailed = domainCheck?.status == DiagnosticCheckStatus.FAIL,
                networkChanged = input.networkChanged,
                fixedTargetsInconclusive = publicPassed &&
                    input.publicConnectivity?.hasSuccessfulTarget != true &&
                    domainCheck?.status == DiagnosticCheckStatus.PASS,
                findings = findings,
            ),
            networkSnapshot = context,
            checks = input.checks,
            findings = findings,
            recommendations = recommendations(findings),
        )
    }

    private fun summary(
        context: com.networktoolbox.core.network.model.NetworkContext?,
        gatewayFailed: Boolean,
        publicFailed: Boolean,
        publicPassed: Boolean,
        publicUncertain: Boolean,
        dnsActualFailure: Boolean,
        domainFailed: Boolean,
        networkChanged: Boolean,
        fixedTargetsInconclusive: Boolean,
        findings: List<DiagnosticFindingV2>,
    ): String = when {
        context?.activeNetworkAvailable == false -> "设备当前未连接可用网络。"
        gatewayFailed && publicFailed -> "网关与公网连通性检测均未通过，问题可能位于本地或上游网络路径。"
        publicPassed && dnsActualFailure -> "公网连接正常，但 DNS 查询失败。"
        publicPassed && domainFailed -> "公网连接和 DNS 正常，但域名访问路径未建立。"
        networkChanged -> "检测期间网络发生切换，请谨慎解读当前结果并重新运行诊断。"
        publicUncertain -> "公网探测证据存在冲突，暂时无法确认公网连接状态。"
        fixedTargetsInconclusive -> "实际域名访问正常，但部分固定公网探测目标未响应。"
        findings.any { it.severity == DiagnosticSeverity.WARNING } -> "检测完成，发现需要关注的网络现象。"
        else -> "基础网络连接正常。"
    }

    private fun recommendations(
        findings: List<DiagnosticFindingV2>,
    ): List<DiagnosticRecommendation> {
        val ids = findings.mapTo(hashSetOf()) { it.id }
        val recommendations = buildList {
            when {
                FINDING_NO_NETWORK in ids -> {
                    add(recommendation(1, "检查网络连接", "检查 Wi-Fi 或移动网络，并确认未开启飞行模式。"))
                    add(recommendation(2, "确认其他设备", "检查同一网络中的其他设备是否可以联网。"))
                    add(recommendation(3, "重新运行诊断", "恢复网络后重新运行一次完整诊断。"))
                }

                FINDING_LOCAL_AND_PUBLIC in ids -> {
                    add(recommendation(1, "检查本地网络", "确认接入点、路由器和本机网络配置正常。"))
                    add(recommendation(2, "检查路由器 WAN", "查看路由器 WAN 状态及上游连接是否正常。"))
                    add(recommendation(3, "对比其他网络", "尝试使用移动网络或其他 Wi-Fi 进行对比。"))
                }

                FINDING_PUBLIC in ids -> {
                    add(recommendation(1, "检查上游连接", "检查路由器 WAN 状态，并尝试访问其他网站。"))
                    add(recommendation(2, "对比其他设备", "确认其他设备是否也无法访问公网。"))
                    add(recommendation(3, "重新运行诊断", "在网络稳定后重试，避免单个目标的瞬时异常影响判断。"))
                }

                FINDING_DNS in ids -> {
                    add(recommendation(1, "重新进行 DNS 查询", "再次查询以确认是否为暂时性异常。"))
                    add(recommendation(2, "检查 DNS 环境", "检查 Private DNS、VPN 或代理设置。"))
                    add(recommendation(3, "切换网络对比", "尝试切换 Wi-Fi 与移动网络后重试。"))
                }

                FINDING_DOMAIN_ACCESS in ids -> {
                    add(recommendation(1, "尝试其他网站", "确认问题是否只影响当前域名或服务。"))
                    add(recommendation(2, "查看详细结果", "对比解析地址、地址族、端口和错误类型。"))
                }
            }
            if (FINDING_NETWORK_CHANGED in ids) {
                add(recommendation(1, "重新运行诊断", "在网络连接稳定后重新运行，避免混合不同网络状态的结果。"))
            }
            if (FINDING_PUBLIC_UNCERTAIN in ids) {
                add(recommendation(1, "重新运行诊断", "重新检测或切换网络进行对比，确认公网连通性是否稳定。"))
            }
        }
        return recommendations.distinctBy { it.action }.take(MAX_RECOMMENDATIONS)
    }

    private fun finding(
        id: String,
        severity: DiagnosticSeverity,
        title: String,
        description: String,
        evidence: List<String>,
    ): DiagnosticFindingV2 = DiagnosticFindingV2(
        id = id,
        severity = severity,
        title = title,
        description = description,
        evidenceCheckIds = evidence,
    )

    private fun recommendation(
        priority: Int,
        title: String,
        action: String,
    ): DiagnosticRecommendation = DiagnosticRecommendation(
        priority = priority,
        title = title,
        action = action,
    )

    private fun List<DiagnosticCheck>.byId(id: String): DiagnosticCheck? =
        firstOrNull { it.id == id }

    private fun DnsLookupStatus.isActualFailure(): Boolean = when (this) {
        DnsLookupStatus.SUCCESS,
        DnsLookupStatus.NO_RECORDS,
        -> false

        else -> true
    }

    private val DiagnosticSeverity.rank: Int
        get() = when (this) {
            DiagnosticSeverity.HEALTHY -> 0
            DiagnosticSeverity.NOTICE -> 1
            DiagnosticSeverity.WARNING -> 2
            DiagnosticSeverity.ERROR -> 3
        }

    private companion object {
        const val CHECK_NETWORK_CONTEXT = "NETWORK_CONTEXT"
        const val CHECK_GATEWAY = "GATEWAY_REACHABILITY"
        const val CHECK_PUBLIC = "PUBLIC_CONNECTIVITY"
        const val CHECK_DNS = "DNS_RESOLUTION"
        const val CHECK_DOMAIN = "DOMAIN_ACCESS"
        const val CHECK_NETWORK_CHANGED = "NETWORK_CHANGED"
        const val FINDING_NO_NETWORK = "NO_ACTIVE_NETWORK"
        const val FINDING_LOCAL_AND_PUBLIC = "LOCAL_AND_PUBLIC_PATH"
        const val FINDING_GATEWAY = "GATEWAY_UNREACHABLE"
        const val FINDING_PUBLIC = "PUBLIC_CONNECTIVITY_FAILED"
        const val FINDING_PUBLIC_UNCERTAIN = "PUBLIC_CONNECTIVITY_UNCERTAIN"
        const val FINDING_FIXED_TARGETS_INCONCLUSIVE = "FIXED_PUBLIC_TARGETS_INCONCLUSIVE"
        const val FINDING_DNS = "DNS_FAILURE"
        const val FINDING_NO_DNS_RECORDS = "DNS_NO_RECORDS"
        const val FINDING_NO_IPV6_RECORD = "NO_IPV6_RECORD"
        const val FINDING_FAKE_IP = "POSSIBLE_FAKE_IP"
        const val FINDING_VPN = "VPN_ACTIVE"
        const val FINDING_DOMAIN_ACCESS = "DOMAIN_ACCESS_FAILED"
        const val FINDING_PING_TCP_DIFFERENCE = "PING_TCP_DIFFERENCE"
        const val FINDING_NETWORK_CHANGED = "NETWORK_CHANGED_DURING_RUN"
        const val MAX_RECOMMENDATIONS = 3
    }
}
