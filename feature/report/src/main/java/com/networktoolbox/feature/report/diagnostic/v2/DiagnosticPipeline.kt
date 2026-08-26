package com.networktoolbox.feature.report.diagnostic.v2

import com.networktoolbox.core.network.dns.DnsLookupRequest
import com.networktoolbox.core.network.dns.DnsLookupResult
import com.networktoolbox.core.network.dns.DnsLookupStatus
import com.networktoolbox.core.network.dns.DnsQueryEngine
import com.networktoolbox.core.network.dns.DnsQueryMethod
import com.networktoolbox.core.network.dns.DnsRecordType
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.network.ping.PingMode
import com.networktoolbox.core.network.ping.PingProtocol
import com.networktoolbox.core.network.ping.PingRequest
import com.networktoolbox.core.network.ping.PingSessionEngine
import com.networktoolbox.core.network.ping.PingSessionResult
import com.networktoolbox.core.network.repository.NetworkRepository
import com.networktoolbox.core.network.tcp.TcpPortChecker
import com.networktoolbox.core.network.tcp.TcpProbeResult
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first

fun interface DiagnosticPipeline {
    suspend fun run(
        onStageChanged: (DiagnosticStageProgress) -> Unit,
    ): DiagnosticPipelineResult
}

/**
 * Runs a bounded, evidence-gathering diagnostic. It deliberately delegates
 * protocol work to the existing Ping, DNS, and TCP abstractions.
 */
class DefaultDiagnosticPipeline(
    private val networkRepository: NetworkRepository,
    private val pingSessionEngine: PingSessionEngine,
    private val dnsQueryEngine: DnsQueryEngine,
    private val tcpPortChecker: TcpPortChecker,
    private val probeTargets: DiagnosticProbeTargets = DiagnosticProbeTargets.default(),
    private val now: () -> Long = { System.currentTimeMillis() },
) : DiagnosticPipeline {
    override suspend fun run(
        onStageChanged: (DiagnosticStageProgress) -> Unit,
    ): DiagnosticPipelineResult {
        currentCoroutineContext().ensureActive()
        val startedAt = now()
        val checks = mutableListOf<DiagnosticCheck>()
        val initialContext = readNetworkContext() ?: NetworkContext.unknown()

        notifyStage(onStageChanged, DiagnosticStage.NETWORK_CONTEXT, DiagnosticStageState.RUNNING)
        val networkCheck = networkCheck(initialContext, startedAt)
        checks += networkCheck
        notifyStage(onStageChanged, networkCheck)

        if (initialContext.activeNetworkAvailable == false) {
            addSkippedChecks(
                checks = checks,
                onStageChanged = onStageChanged,
                reason = "没有活动网络，未执行依赖网络的探测。",
            )
            return result(
                startedAt = startedAt,
                networkContext = initialContext,
                gatewayResult = null,
                publicConnectivity = null,
                dnsResult = null,
                domainResults = emptyList(),
                checks = checks,
                networkChanged = false,
            )
        }

        var networkChanged = false

        currentCoroutineContext().ensureActive()
        notifyStage(onStageChanged, DiagnosticStage.GATEWAY, DiagnosticStageState.RUNNING)
        networkChanged = detectNetworkChange(initialContext) || networkChanged
        val gatewayResult: PingSessionResult?
        var gatewayCheck: DiagnosticCheck
        val gatewayAddress = initialContext.gateway
        if (initialContext.connectionType == ConnectionType.CELLULAR ||
            gatewayAddress.isNullOrBlank()
        ) {
            gatewayResult = null
            gatewayCheck = gatewayNotApplicableCheck(
                context = initialContext,
                gateway = gatewayAddress,
            )
        } else if (gatewayAddress.isUnscopedIpv6LinkLocal()) {
            gatewayResult = null
            gatewayCheck = gatewayNotConfirmedCheck(gatewayAddress)
        } else {
            gatewayResult = runGatewayCheck(gatewayAddress)
            gatewayCheck = gatewayCheck(gatewayResult, gatewayAddress)
        }
        checks += gatewayCheck
        notifyStage(onStageChanged, gatewayCheck)

        currentCoroutineContext().ensureActive()
        notifyStage(
            onStageChanged,
            DiagnosticStage.PUBLIC_CONNECTIVITY,
            DiagnosticStageState.RUNNING,
        )
        networkChanged = detectNetworkChange(initialContext) || networkChanged
        val publicConnectivity = runPublicConnectivity(initialContext)
        val publicCheck = publicCheck(publicConnectivity)
        checks += publicCheck
        notifyStage(onStageChanged, publicCheck)

        currentCoroutineContext().ensureActive()
        notifyStage(onStageChanged, DiagnosticStage.DNS, DiagnosticStageState.RUNNING)
        networkChanged = detectNetworkChange(initialContext) || networkChanged
        val dnsResult = runDnsCheck(startedAt)
        val dnsCheck = dnsCheck(dnsResult, initialContext)
        checks += dnsCheck
        notifyStage(onStageChanged, dnsCheck)

        currentCoroutineContext().ensureActive()
        notifyStage(
            onStageChanged,
            DiagnosticStage.DOMAIN_CONNECTIVITY,
            DiagnosticStageState.RUNNING,
        )
        networkChanged = detectNetworkChange(initialContext) || networkChanged
        val domainResults = runDomainConnectivity(dnsResult)
        val domainCheck = domainCheck(dnsResult, domainResults)
        checks += domainCheck
        notifyStage(onStageChanged, domainCheck)

        val effectivePublicCheck = reconcilePublicCheck(
            rawCheck = publicCheck,
            publicConnectivity = publicConnectivity,
            dnsResult = dnsResult,
            domainCheck = domainCheck,
        )
        replaceCheck(checks, effectivePublicCheck)

        if (gatewayCheck.status == DiagnosticCheckStatus.FAIL &&
            effectivePublicCheck.status == DiagnosticCheckStatus.PASS
        ) {
            gatewayCheck = gatewayCheck.copy(
                status = DiagnosticCheckStatus.UNKNOWN,
                severity = DiagnosticSeverity.NOTICE,
                summary = "网关未响应可达性探测，但设备仍可正常访问公网。部分路由器可能不响应此类探测，这不一定表示网关异常。",
                rawData = gatewayCheck.rawData + ("reconciledWithPublicConnectivity" to "true"),
            )
            replaceCheck(checks, gatewayCheck)
        }
        notifyStage(onStageChanged, effectivePublicCheck)
        if (gatewayCheck.status == DiagnosticCheckStatus.UNKNOWN &&
            gatewayCheck.rawData["reconciledWithPublicConnectivity"] == "true"
        ) {
            notifyStage(onStageChanged, gatewayCheck)
        }

        networkChanged = detectNetworkChange(initialContext) || networkChanged
        if (networkChanged) {
            val changedCheck = DiagnosticCheck(
                id = CHECK_NETWORK_CHANGED,
                stage = DiagnosticStage.NETWORK_CHANGED,
                name = "检测期间网络状态",
                status = DiagnosticCheckStatus.UNKNOWN,
                severity = DiagnosticSeverity.NOTICE,
                summary = "检测期间网络状态发生变化，部分结果可能来自不同网络。",
                observedAt = now(),
                rawData = mapOf("initialAndCurrentContextDiffer" to "true"),
            )
            checks += changedCheck
            notifyStage(onStageChanged, changedCheck)
        }

        return result(
            startedAt = startedAt,
            networkContext = initialContext,
            gatewayResult = gatewayResult,
            publicConnectivity = publicConnectivity,
            dnsResult = dnsResult,
            domainResults = domainResults,
            checks = checks,
            networkChanged = networkChanged,
        )
    }

    private suspend fun readNetworkContext(): NetworkContext? = try {
        networkRepository.observeNetworkContext().first()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private suspend fun detectNetworkChange(initial: NetworkContext): Boolean {
        val current = readNetworkContext() ?: return false
        return !sameNetworkIdentity(initial, current)
    }

    private suspend fun runGatewayCheck(gateway: String): PingSessionResult? = try {
        pingSessionEngine.run(
            request = PingRequest(
                target = gateway,
                protocol = PingProtocol.AUTO,
                mode = PingMode.CONTINUOUS,
                count = DIAGNOSTIC_PING_COUNT,
                intervalMs = DIAGNOSTIC_PING_INTERVAL_MS,
                timeoutMs = DIAGNOSTIC_TIMEOUT_MS,
            ),
        )
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private suspend fun runPublicConnectivity(
        context: NetworkContext,
    ): DiagnosticPublicConnectivityResult {
        if (probeTargets.publicTargets.isEmpty()) {
            return DiagnosticPublicConnectivityResult(
                validated = context.validated,
                targetResults = emptyList(),
            )
        }

        val results = probeTargets.publicTargets.map { target ->
            currentCoroutineContext().ensureActive()
            val probe = try {
                tcpPortChecker.check(
                    host = target.host,
                    port = target.port,
                    timeoutMs = DIAGNOSTIC_TIMEOUT_MS,
                ) to true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                TcpProbeResult(
                    host = target.host,
                    port = target.port,
                    success = false,
                    latencyMs = null,
                    errorMessage = "Public connectivity probe unavailable.",
                ) to false
            }
            DiagnosticPublicTargetResult(
                target = target,
                result = probe.first,
                probeCompleted = probe.second,
            )
        }
        return DiagnosticPublicConnectivityResult(
            validated = context.validated,
            targetResults = results,
        )
    }

    private suspend fun runDnsCheck(startedAt: Long): DnsLookupResult = try {
        dnsQueryEngine.lookup(
            DnsLookupRequest(
                queryName = probeTargets.domainName,
                recordTypes = setOf(DnsRecordType.A, DnsRecordType.AAAA),
                timeoutMs = DIAGNOSTIC_TIMEOUT_MS,
            ),
        )
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        DnsLookupResult(
            queryName = probeTargets.domainName,
            requestedTypes = setOf(DnsRecordType.A, DnsRecordType.AAAA),
            records = emptyList(),
            server = null,
            method = DnsQueryMethod.UNAVAILABLE,
            status = DnsLookupStatus.NETWORK_ERROR,
            durationMs = null,
            startTime = startedAt,
            endTime = now(),
            errorMessage = "DNS check unavailable.",
        )
    }

    private suspend fun runDomainConnectivity(
        dnsResult: DnsLookupResult,
    ): List<TcpProbeResult> {
        val addresses = dnsResult.records
            .filter { record ->
                record.type == DnsRecordType.A || record.type == DnsRecordType.AAAA
            }
            .map { record -> record.value }
            .distinct()
            .take(MAX_DOMAIN_ADDRESSES)

        if (addresses.isEmpty()) return emptyList()

        return addresses.map { address ->
            currentCoroutineContext().ensureActive()
            try {
                tcpPortChecker.check(
                    host = address,
                    port = probeTargets.domainPort,
                    timeoutMs = DIAGNOSTIC_TIMEOUT_MS,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                TcpProbeResult(
                    host = address,
                    port = probeTargets.domainPort,
                    success = false,
                    latencyMs = null,
                    errorMessage = "Domain connectivity probe unavailable.",
                )
            }
        }
    }

    private fun networkCheck(
        context: NetworkContext,
        observedAt: Long,
    ): DiagnosticCheck {
        val status = when (context.activeNetworkAvailable) {
            true -> DiagnosticCheckStatus.PASS
            false -> DiagnosticCheckStatus.FAIL
            null -> DiagnosticCheckStatus.UNKNOWN
        }
        val severity = when (status) {
            DiagnosticCheckStatus.PASS -> DiagnosticSeverity.HEALTHY
            DiagnosticCheckStatus.FAIL -> DiagnosticSeverity.ERROR
            else -> DiagnosticSeverity.NOTICE
        }
        return DiagnosticCheck(
            id = CHECK_NETWORK_CONTEXT,
            stage = DiagnosticStage.NETWORK_CONTEXT,
            name = "本机网络状态",
            status = status,
            severity = severity,
            summary = when (status) {
                DiagnosticCheckStatus.PASS -> "已发现活动网络。"
                DiagnosticCheckStatus.FAIL -> "设备当前没有活动网络。"
                else -> "无法确认当前活动网络状态。"
            },
            observedAt = observedAt,
            rawData = contextRawData(context),
        )
    }

    private fun gatewayCheck(
        result: PingSessionResult?,
        gateway: String,
    ): DiagnosticCheck {
        val reachable = result?.receivedPackets?.let { it > 0 } == true
        return DiagnosticCheck(
            id = CHECK_GATEWAY,
            stage = DiagnosticStage.GATEWAY,
            name = "网关可达性",
            status = if (result == null) DiagnosticCheckStatus.UNKNOWN else {
                if (reachable) DiagnosticCheckStatus.PASS else DiagnosticCheckStatus.FAIL
            },
            severity = when {
                result == null -> DiagnosticSeverity.NOTICE
                reachable -> DiagnosticSeverity.HEALTHY
                else -> DiagnosticSeverity.WARNING
            },
            summary = when {
                result == null -> "网关探测未能完成。"
                reachable -> "网关探测成功。"
                else -> "网关探测未成功，可能与本地链路或网关路径有关。"
            },
            target = gateway,
            method = result?.method?.name,
            observedAt = now(),
            rawData = result?.let {
                mapOf(
                    "sentPackets" to it.sentPackets.toString(),
                    "receivedPackets" to it.receivedPackets.toString(),
                    "packetLoss" to it.packetLoss.toString(),
                    "avgLatencyMs" to (it.avgLatencyMs?.toString() ?: "unknown"),
                )
            }.orEmpty(),
        )
    }

    private fun gatewayNotApplicableCheck(
        context: NetworkContext,
        gateway: String?,
    ): DiagnosticCheck {
        val cellular = context.connectionType == ConnectionType.CELLULAR
        return DiagnosticCheck(
            id = CHECK_GATEWAY,
            stage = DiagnosticStage.GATEWAY,
            name = "网关可达性",
            status = DiagnosticCheckStatus.NOT_APPLICABLE,
            severity = DiagnosticSeverity.NOTICE,
            summary = if (cellular) {
                "移动网络不执行传统局域网网关可达性判断。"
            } else {
                "当前网络未提供可直接检测的默认网关。"
            },
            target = gateway,
            observedAt = now(),
            rawData = buildMap {
                put("reason", if (cellular) "cellular_gateway_not_applicable" else "gateway_unavailable")
                gateway?.let { put("systemGateway", it) }
            },
        )
    }

    private fun gatewayNotConfirmedCheck(gateway: String): DiagnosticCheck = DiagnosticCheck(
        id = CHECK_GATEWAY,
        stage = DiagnosticStage.GATEWAY,
        name = "网关可达性",
        status = DiagnosticCheckStatus.UNKNOWN,
        severity = DiagnosticSeverity.NOTICE,
        summary = "当前网络使用 IPv6 链路本地网关，暂未执行可靠的网关可达性探测。",
        target = gateway,
        observedAt = now(),
        rawData = mapOf("reason" to "ipv6_link_local_scope_unavailable"),
    )

    private fun publicCheck(
        result: DiagnosticPublicConnectivityResult,
    ): DiagnosticCheck {
        val completedTargets = result.targetResults.count { it.probeCompleted }
        val successfulTargets = result.targetResults.count {
            it.probeCompleted && it.result.success
        }
        val passed = result.hasSuccessfulTarget
        return DiagnosticCheck(
            id = CHECK_PUBLIC,
            stage = DiagnosticStage.PUBLIC_CONNECTIVITY,
            name = "公网连通性",
            status = when {
                passed -> DiagnosticCheckStatus.PASS
                completedTargets > 0 -> DiagnosticCheckStatus.FAIL
                else -> DiagnosticCheckStatus.UNKNOWN
            },
            severity = when {
                passed -> DiagnosticSeverity.HEALTHY
                completedTargets > 0 -> DiagnosticSeverity.WARNING
                else -> DiagnosticSeverity.NOTICE
            },
            summary = when {
                passed -> "至少一个公网 TCP 443 目标可达。"
                completedTargets > 0 -> "所有已完成的公网 TCP 443 探测均未建立连接。"
                else -> "没有足够的公网连通性证据。"
            },
            method = "TCP_443_PROBES_WITH_VALIDATED_CONTEXT",
            observedAt = now(),
            rawData = mapOf(
                "validated" to (result.validated?.toString() ?: "unknown"),
                "targetCount" to result.targetResults.size.toString(),
                "completedTargetCount" to completedTargets.toString(),
                "successfulTargetCount" to successfulTargets.toString(),
                "targets" to result.targetResults.joinToString(",") {
                    "${it.target.host}:${it.target.port}"
                },
                "targetOutcomes" to result.targetResults.joinToString(";") { targetResult ->
                    "${targetResult.target.host}:${targetResult.target.port}=" +
                        "${if (targetResult.result.success) "PASS" else "FAIL"}" +
                        ":completed=${targetResult.probeCompleted}" +
                        ":latencyMs=${targetResult.result.latencyMs ?: "unknown"}" +
                        ":error=${targetResult.result.errorMessage ?: ""}"
                },
            ),
        )
    }

    private fun reconcilePublicCheck(
        rawCheck: DiagnosticCheck,
        publicConnectivity: DiagnosticPublicConnectivityResult,
        dnsResult: DnsLookupResult,
        domainCheck: DiagnosticCheck,
    ): DiagnosticCheck {
        val completedTargets = publicConnectivity.targetResults.count { it.probeCompleted }
        val fixedTargetSuccess = publicConnectivity.hasSuccessfulTarget
        val domainAccessSuccess = domainCheck.status == DiagnosticCheckStatus.PASS &&
            dnsResult.status in setOf(DnsLookupStatus.SUCCESS, DnsLookupStatus.PARTIAL)
        val effectivePass = fixedTargetSuccess || domainAccessSuccess
        val effectiveStatus = when {
            effectivePass -> DiagnosticCheckStatus.PASS
            publicConnectivity.validated == true -> DiagnosticCheckStatus.UNKNOWN
            publicConnectivity.validated == false && completedTargets > 0 ->
                DiagnosticCheckStatus.FAIL

            else -> DiagnosticCheckStatus.UNKNOWN
        }
        val evidence = when {
            fixedTargetSuccess -> "fixed_tcp_probe"
            domainAccessSuccess -> "resolved_domain_tcp"
            publicConnectivity.validated == true -> "validated_conflict"
            effectiveStatus == DiagnosticCheckStatus.FAIL -> "corroborated_failure"
            else -> "insufficient_evidence"
        }
        return rawCheck.copy(
            status = effectiveStatus,
            severity = when (effectiveStatus) {
                DiagnosticCheckStatus.PASS -> DiagnosticSeverity.HEALTHY
                DiagnosticCheckStatus.FAIL -> DiagnosticSeverity.WARNING
                else -> DiagnosticSeverity.NOTICE
            },
            summary = when {
                fixedTargetSuccess -> "至少一个公网 TCP 443 目标可达。"
                domainAccessSuccess -> "固定公网探测未全部成功，但实际域名访问可达。"
                publicConnectivity.validated == true ->
                    "固定公网探测与实际域名访问均未成功，但 Android 系统仍报告网络已验证，证据存在冲突。"

                effectiveStatus == DiagnosticCheckStatus.FAIL ->
                    "固定公网探测与实际域名访问均未建立连接。"

                else -> "没有足够的公网连通性证据。"
            },
            rawData = rawCheck.rawData + mapOf(
                "fixedTargetSuccess" to fixedTargetSuccess.toString(),
                "domainAccess" to domainCheck.status.name,
                "domainAccessSuccess" to domainAccessSuccess.toString(),
                "effectiveEvidence" to evidence,
            ),
        )
    }

    private fun replaceCheck(
        checks: MutableList<DiagnosticCheck>,
        replacement: DiagnosticCheck,
    ) {
        val index = checks.indexOfFirst { it.id == replacement.id }
        if (index >= 0) {
            checks[index] = replacement
        } else {
            checks += replacement
        }
    }

    private fun String.isUnscopedIpv6LinkLocal(): Boolean {
        if (contains('%') || !contains(':')) return false
        return runCatching { InetAddress.getByName(this) }
            .getOrNull()
            ?.let { it is Inet6Address && it.isLinkLocalAddress }
            ?: false
    }

    private fun dnsCheck(
        result: DnsLookupResult,
        context: NetworkContext,
    ): DiagnosticCheck {
        val status = when (result.status) {
            DnsLookupStatus.SUCCESS -> DiagnosticCheckStatus.PASS
            DnsLookupStatus.NO_RECORDS -> DiagnosticCheckStatus.NO_RECORDS
            else -> DiagnosticCheckStatus.FAIL
        }
        val fakeIp = result.records.any { record ->
            DiagnosticAddressClassifier.isFakeIp(record.value)
        }
        val severity = when {
            fakeIp && status == DiagnosticCheckStatus.PASS -> DiagnosticSeverity.NOTICE
            status == DiagnosticCheckStatus.PASS -> DiagnosticSeverity.HEALTHY
            status == DiagnosticCheckStatus.NO_RECORDS -> DiagnosticSeverity.NOTICE
            result.status == DnsLookupStatus.PARTIAL -> DiagnosticSeverity.WARNING
            else -> DiagnosticSeverity.ERROR
        }
        return DiagnosticCheck(
            id = CHECK_DNS,
            stage = DiagnosticStage.DNS,
            name = "DNS 解析",
            status = status,
            severity = severity,
            summary = when {
                result.status == DnsLookupStatus.SUCCESS -> "DNS 查询正常完成。"
                result.status == DnsLookupStatus.NO_RECORDS -> "查询正常完成，但没有返回请求的记录。"
                result.status == DnsLookupStatus.PARTIAL -> "部分 DNS 查询成功，部分查询未正常完成。"
                else -> "DNS 查询未成功完成。"
            },
            target = result.queryName,
            method = result.method.name,
            observedAt = result.endTime,
            rawData = mapOf(
                "status" to result.status.name,
                "requestedTypes" to result.requestedTypes.joinToString(",") { it.name },
                "recordCount" to result.records.size.toString(),
                "recordCounts" to result.requestedTypes
                    .sortedBy { it.name }
                    .joinToString(",") { type ->
                        "$type=${result.records.count { record -> record.type == type }}"
                    },
                "durationMs" to (result.durationMs?.toString() ?: "unknown"),
                "configuredDnsServers" to context.dnsServers.joinToString(","),
                "fakeIpObserved" to fakeIp.toString(),
                "error" to (result.errorMessage ?: ""),
            ),
        )
    }

    private fun domainCheck(
        dnsResult: DnsLookupResult,
        results: List<TcpProbeResult>,
    ): DiagnosticCheck {
        if (results.isEmpty()) {
            return DiagnosticCheck(
                id = CHECK_DOMAIN,
                stage = DiagnosticStage.DOMAIN_CONNECTIVITY,
                name = "域名访问路径",
                status = DiagnosticCheckStatus.SKIPPED,
                severity = DiagnosticSeverity.NOTICE,
                summary = "没有可用于 TCP 检测的 DNS 地址。",
                target = probeTargets.domainName,
                observedAt = now(),
                rawData = mapOf("reason" to "no_usable_dns_address"),
            )
        }
        val successful = results.count { it.success }
        return DiagnosticCheck(
            id = CHECK_DOMAIN,
            stage = DiagnosticStage.DOMAIN_CONNECTIVITY,
            name = "域名访问路径",
            status = if (successful > 0) {
                DiagnosticCheckStatus.PASS
            } else {
                DiagnosticCheckStatus.FAIL
            },
            severity = if (successful > 0) {
                DiagnosticSeverity.HEALTHY
            } else {
                DiagnosticSeverity.WARNING
            },
            summary = if (successful > 0) {
                "至少一个解析地址的 TCP 连接可建立。"
            } else {
                "域名已获得解析地址，但 TCP 连接未建立。"
            },
            target = "${probeTargets.domainName}:${probeTargets.domainPort}",
            method = "TCP_CONNECT_TO_RESOLVED_ADDRESS",
            observedAt = now(),
            rawData = mapOf(
                "dnsStatus" to dnsResult.status.name,
                "addressCount" to results.size.toString(),
                "successfulAddressCount" to successful.toString(),
                "addresses" to results.joinToString(",") { it.host },
            ),
        )
    }

    private fun addSkippedChecks(
        checks: MutableList<DiagnosticCheck>,
        onStageChanged: (DiagnosticStageProgress) -> Unit,
        reason: String,
    ) {
        listOf(
            Triple(DiagnosticStage.GATEWAY, CHECK_GATEWAY, "网关可达性"),
            Triple(DiagnosticStage.PUBLIC_CONNECTIVITY, CHECK_PUBLIC, "公网连通性"),
            Triple(DiagnosticStage.DNS, CHECK_DNS, "DNS 解析"),
            Triple(DiagnosticStage.DOMAIN_CONNECTIVITY, CHECK_DOMAIN, "域名访问路径"),
        ).forEach { (stage, id, name) ->
            val check = DiagnosticCheck(
                id = id,
                stage = stage,
                name = name,
                status = DiagnosticCheckStatus.SKIPPED,
                severity = DiagnosticSeverity.NOTICE,
                summary = reason,
                observedAt = now(),
                rawData = mapOf("reason" to "no_active_network"),
            )
            checks += check
            notifyStage(onStageChanged, check)
        }
    }

    private fun result(
        startedAt: Long,
        networkContext: NetworkContext,
        gatewayResult: PingSessionResult?,
        publicConnectivity: DiagnosticPublicConnectivityResult?,
        dnsResult: DnsLookupResult?,
        domainResults: List<TcpProbeResult>,
        checks: List<DiagnosticCheck>,
        networkChanged: Boolean,
    ): DiagnosticPipelineResult = DiagnosticPipelineResult(
        startedAt = startedAt,
        endedAt = now().coerceAtLeast(startedAt),
        networkContext = networkContext,
        gatewayResult = gatewayResult,
        publicConnectivity = publicConnectivity,
        dnsResult = dnsResult,
        domainResults = domainResults,
        checks = checks,
        networkChanged = networkChanged,
    )

    private fun contextRawData(context: NetworkContext): Map<String, String> = buildMap {
        put("connectionType", context.connectionType.name)
        put("activeNetworkAvailable", context.activeNetworkAvailable?.toString() ?: "unknown")
        put("validated", context.validated?.toString() ?: "unknown")
        context.ipv4Address?.let { put("ipv4Address", it) }
        context.ipv6Address?.let { put("ipv6Address", it) }
        context.gateway?.let { put("gateway", it) }
        if (context.dnsServers.isNotEmpty()) {
            put("configuredDnsServers", context.dnsServers.joinToString(","))
        }
        context.vpnActive?.let { put("vpnActive", it.toString()) }
    }

    private fun sameNetworkIdentity(first: NetworkContext, second: NetworkContext): Boolean =
        first.connectionType == second.connectionType &&
            first.ipv4Address == second.ipv4Address &&
            first.ipv6Address == second.ipv6Address &&
            first.gateway == second.gateway &&
            first.dnsServers == second.dnsServers &&
            first.vpnActive == second.vpnActive &&
            first.wifiName == second.wifiName &&
            first.activeNetworkAvailable == second.activeNetworkAvailable

    private fun notifyStage(
        callback: (DiagnosticStageProgress) -> Unit,
        stage: DiagnosticStage,
        state: DiagnosticStageState,
    ) {
        callback(DiagnosticStageProgress(stage = stage, state = state))
    }

    private fun notifyStage(
        callback: (DiagnosticStageProgress) -> Unit,
        check: DiagnosticCheck,
    ) {
        notifyStage(
            callback = callback,
            stage = check.stage,
            state = when (check.status) {
                DiagnosticCheckStatus.SKIPPED,
                DiagnosticCheckStatus.NOT_APPLICABLE,
                -> DiagnosticStageState.SKIPPED

                DiagnosticCheckStatus.FAIL -> DiagnosticStageState.FAILED
                else -> DiagnosticStageState.COMPLETED
            },
        )
    }

    private companion object {
        const val CHECK_NETWORK_CONTEXT = "NETWORK_CONTEXT"
        const val CHECK_GATEWAY = "GATEWAY_REACHABILITY"
        const val CHECK_PUBLIC = "PUBLIC_CONNECTIVITY"
        const val CHECK_DNS = "DNS_RESOLUTION"
        const val CHECK_DOMAIN = "DOMAIN_ACCESS"
        const val CHECK_NETWORK_CHANGED = "NETWORK_CHANGED"
        const val DIAGNOSTIC_PING_COUNT = 3
        const val DIAGNOSTIC_PING_INTERVAL_MS = 100
        const val DIAGNOSTIC_TIMEOUT_MS = 2_000
        const val MAX_DOMAIN_ADDRESSES = 2
    }
}
