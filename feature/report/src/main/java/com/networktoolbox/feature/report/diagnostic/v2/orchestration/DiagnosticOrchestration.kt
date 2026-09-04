package com.networktoolbox.feature.report.diagnostic.v2.orchestration

import com.networktoolbox.core.common.diagnostic.DiagnosticAddressFamily
import com.networktoolbox.core.common.diagnostic.DiagnosticCheck
import com.networktoolbox.core.common.diagnostic.DiagnosticCheckCode
import com.networktoolbox.core.common.diagnostic.DiagnosticCheckStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticConnectionType
import com.networktoolbox.core.common.diagnostic.DiagnosticDnsOutcome
import com.networktoolbox.core.common.diagnostic.DiagnosticIntent
import com.networktoolbox.core.common.diagnostic.DiagnosticNetworkSummary
import com.networktoolbox.core.common.diagnostic.DiagnosticObservation
import com.networktoolbox.core.common.diagnostic.DiagnosticObservationCode
import com.networktoolbox.core.common.diagnostic.DiagnosticObservationSource
import com.networktoolbox.core.common.diagnostic.DiagnosticObservationState
import com.networktoolbox.core.common.diagnostic.DiagnosticObservationValue
import com.networktoolbox.core.common.diagnostic.DiagnosticRunStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticSeverity
import com.networktoolbox.core.common.diagnostic.DiagnosticStage
import com.networktoolbox.core.common.diagnostic.DiagnosticTarget
import com.networktoolbox.core.common.diagnostic.DiagnosticTargetKind
import com.networktoolbox.core.common.diagnostic.DiagnosticTcpOutcome
import com.networktoolbox.core.network.dns.DnsLookupRequest
import com.networktoolbox.core.network.dns.DnsLookupResult
import com.networktoolbox.core.network.dns.DnsLookupStatus
import com.networktoolbox.core.network.dns.DnsQueryEngine
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
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticAddressClassifier
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticProbeTargets
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first

interface DiagnosticOrchestrator {
    suspend fun run(
        intent: DiagnosticIntent = DiagnosticIntent(),
        onProgress: (DiagnosticStageProgress) -> Unit = {},
    ): DiagnosticRunEvidence
}

fun interface DiagnosticNetworkFingerprintProvider {
    fun fingerprint(context: NetworkContext): com.networktoolbox.core.common.diagnostic.NetworkFingerprint?
}

data class DiagnosticStageProgress(
    val stage: DiagnosticStage,
    val state: DiagnosticStageState,
)

enum class DiagnosticStageState {
    PENDING,
    RUNNING,
    COMPLETED,
    SKIPPED,
    NOT_APPLICABLE,
    UNKNOWN,
}

data class DiagnosticRunEvidence(
    val runStatus: DiagnosticRunStatus,
    val startedAt: Long,
    val finishedAt: Long,
    val durationMs: Long,
    val fingerprint: com.networktoolbox.core.common.diagnostic.NetworkFingerprint?,
    val networkContextSummary: DiagnosticNetworkSummary?,
    val observations: List<DiagnosticObservation>,
    val checks: List<DiagnosticCheck>,
    val intent: DiagnosticIntent,
)

/**
 * Default fingerprinting is deliberately opaque and derived only from the
 * current link identity. It is comparable within the app, not a hardware ID.
 */
class DefaultDiagnosticNetworkFingerprintProvider : DiagnosticNetworkFingerprintProvider {
    override fun fingerprint(context: NetworkContext): com.networktoolbox.core.common.diagnostic.NetworkFingerprint? {
        val identity = listOf(
            context.activeNetworkAvailable?.toString(),
            context.connectionType.name,
            context.interfaceName,
            context.ipv4Address,
            context.ipv4PrefixLength?.toString(),
            context.ipv6Addresses.sorted().joinToString(","),
            context.gateway,
            context.dnsServers.sorted().joinToString(","),
            context.vpnActive?.toString(),
        ).joinToString("|") { it.orEmpty() }

        val hasIdentity = context.activeNetworkAvailable != null ||
            context.connectionType != ConnectionType.UNKNOWN ||
            context.interfaceName != null ||
            context.ipv4Address != null ||
            context.ipv6Address != null ||
            context.ipv6Addresses.isNotEmpty() ||
            context.ipv4PrefixLength != null ||
            context.gateway != null ||
            context.dnsServers.isNotEmpty() ||
            context.vpnActive != null
        if (!hasIdentity) {
            return null
        }

        val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray())
        val value = buildString(digest.size * 2) {
            digest.forEach { byte ->
                append(HEX[(byte.toInt() ushr 4) and 0x0f])
                append(HEX[byte.toInt() and 0x0f])
            }
        }
        return com.networktoolbox.core.common.diagnostic.NetworkFingerprint(value)
    }

    private companion object {
        const val HEX = "0123456789abcdef"
    }
}

class DefaultDiagnosticOrchestrator(
    private val networkRepository: NetworkRepository,
    private val pingSessionEngine: PingSessionEngine,
    private val dnsQueryEngine: DnsQueryEngine,
    private val tcpPortChecker: TcpPortChecker,
    private val probeTargets: DiagnosticProbeTargets = DiagnosticProbeTargets.default(),
    private val fingerprintProvider: DiagnosticNetworkFingerprintProvider =
        DefaultDiagnosticNetworkFingerprintProvider(),
    private val now: () -> Long = { System.currentTimeMillis() },
) : DiagnosticOrchestrator {
    override suspend fun run(
        intent: DiagnosticIntent,
        onProgress: (DiagnosticStageProgress) -> Unit,
    ): DiagnosticRunEvidence {
        val startedAt = now()
        val builder = EvidenceBuilder(startedAt, intent)

        return try {
            currentCoroutineContext().ensureActive()
            listOf(
                DiagnosticStage.IP_CONFIGURATION,
                DiagnosticStage.GATEWAY,
                DiagnosticStage.INTERNET,
                DiagnosticStage.DNS,
                DiagnosticStage.TARGET,
            ).forEach { stage -> emit(onProgress, stage, DiagnosticStageState.PENDING) }
            emit(onProgress, DiagnosticStage.NETWORK_STATE, DiagnosticStageState.RUNNING)
            val initialRead = readNetworkContext()
            builder.context = initialRead.context
            builder.fingerprint = fingerprintProvider.fingerprint(initialRead.context)
            addNetworkObservations(builder, initialRead)
            val networkCheck = networkCheck(builder, initialRead)
            builder.addCheck(networkCheck)
            emitCompleted(onProgress, networkCheck)

            if (initialRead.context.activeNetworkAvailable == false) {
                addSkippedStage(builder, onProgress, DiagnosticStage.IP_CONFIGURATION, "没有活动网络，未执行 IP 配置检查。")
                addSkippedStage(builder, onProgress, DiagnosticStage.GATEWAY, "没有活动网络，未执行网关探测。")
                addSkippedStage(builder, onProgress, DiagnosticStage.INTERNET, "没有活动网络，未执行公网探测。")
                addSkippedStage(builder, onProgress, DiagnosticStage.DNS, "没有活动网络，未执行 DNS 查询。")
                addSkippedStage(builder, onProgress, DiagnosticStage.TARGET, "没有活动网络，未执行目标检查。")
                return builder.finish(DiagnosticRunStatus.COMPLETED, now())
            }

            if (!confirmStable(builder, onProgress)) return builder.finish(DiagnosticRunStatus.NETWORK_CHANGED, now())
            runIpConfiguration(builder, onProgress)

            if (!confirmStable(builder, onProgress)) return builder.finish(DiagnosticRunStatus.NETWORK_CHANGED, now())
            runGateway(builder, onProgress)

            if (!confirmStable(builder, onProgress)) return builder.finish(DiagnosticRunStatus.NETWORK_CHANGED, now())
            runPublicConnectivity(builder, onProgress)

            if (!confirmStable(builder, onProgress)) return builder.finish(DiagnosticRunStatus.NETWORK_CHANGED, now())
            runDnsHealth(builder, onProgress)

            if (!confirmStable(builder, onProgress)) return builder.finish(DiagnosticRunStatus.NETWORK_CHANGED, now())
            runTarget(builder, onProgress)

            if (builder.networkChanged) {
                builder.finish(DiagnosticRunStatus.NETWORK_CHANGED, now())
            } else {
                builder.finish(DiagnosticRunStatus.COMPLETED, now())
            }
        } catch (_: CancellationException) {
            builder.finish(DiagnosticRunStatus.CANCELLED, now())
        } catch (_: Exception) {
            builder.finish(DiagnosticRunStatus.FAILED, now())
        }
    }

    private suspend fun readNetworkContext(): ContextRead = try {
        ContextRead(networkRepository.observeNetworkContext().first(), available = true)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        ContextRead(NetworkContext.unknown(), available = false)
    }

    private fun addNetworkObservations(builder: EvidenceBuilder, read: ContextRead) {
        val context = read.context
        val activeNetworkAvailable = context.activeNetworkAvailable
        val source = DiagnosticObservationSource.NETWORK_REPOSITORY
        val state = if (read.available) DiagnosticObservationState.CONFIRMED else {
            DiagnosticObservationState.UNAVAILABLE
        }
        builder.addObservation(
            code = DiagnosticObservationCode.ACTIVE_NETWORK_AVAILABLE,
            stage = DiagnosticStage.NETWORK_STATE,
            source = source,
            value = when {
                !read.available -> DiagnosticObservationValue.TextValue("UNAVAILABLE")
                activeNetworkAvailable != null ->
                    DiagnosticObservationValue.BooleanValue(activeNetworkAvailable)

                else -> DiagnosticObservationValue.TextValue("UNKNOWN")
            },
            state = if (!read.available || activeNetworkAvailable == null) {
                if (!read.available) DiagnosticObservationState.UNAVAILABLE else DiagnosticObservationState.UNKNOWN
            } else {
                DiagnosticObservationState.CONFIRMED
            },
        )
        builder.addObservation(
            code = DiagnosticObservationCode.CONNECTION_TYPE,
            stage = DiagnosticStage.NETWORK_STATE,
            source = source,
            value = DiagnosticObservationValue.TextValue(context.connectionType.name),
            state = state,
        )

        val addresses = buildList {
            context.ipv4Address?.let { add(DiagnosticAddressFamily.IPV4 to it) }
            val ipv6 = context.ipv6Addresses.ifEmpty { listOfNotNull(context.ipv6Address) }
            ipv6.distinct().forEach { add(DiagnosticAddressFamily.IPV6 to it) }
        }
        addresses.forEachIndexed { index, (family, address) ->
            builder.addObservation(
                code = DiagnosticObservationCode.LOCAL_ADDRESS,
                stage = DiagnosticStage.IP_CONFIGURATION,
                source = DiagnosticObservationSource.LINK_PROPERTIES,
                value = DiagnosticObservationValue.AddressValue(address, family),
                state = state,
                suffix = "$index",
            )
        }
        context.ipv4PrefixLength?.let {
            builder.addObservation(
                code = DiagnosticObservationCode.IPV4_PREFIX_LENGTH,
                stage = DiagnosticStage.IP_CONFIGURATION,
                source = DiagnosticObservationSource.LINK_PROPERTIES,
                value = DiagnosticObservationValue.TextValue(it.toString()),
                state = state,
            )
        }
        context.interfaceName?.takeIf(String::isNotBlank)?.let {
            builder.addObservation(
                code = DiagnosticObservationCode.INTERFACE_NAME,
                stage = DiagnosticStage.NETWORK_STATE,
                source = DiagnosticObservationSource.LINK_PROPERTIES,
                value = DiagnosticObservationValue.TextValue(it),
                state = state,
            )
        }
        context.gateway?.takeIf(String::isNotBlank)?.let {
            builder.addObservation(
                code = DiagnosticObservationCode.GATEWAY_ADDRESS,
                stage = DiagnosticStage.GATEWAY,
                source = DiagnosticObservationSource.LINK_PROPERTIES,
                value = DiagnosticObservationValue.AddressValue(it, addressFamily(it)),
                state = state,
            )
        }
        val dnsSummary = context.dnsServers.take(MAX_DNS_SERVERS).joinToString(",").ifBlank { "NONE" }
        builder.addObservation(
            code = DiagnosticObservationCode.DNS_CONFIGURATION,
            stage = DiagnosticStage.NETWORK_STATE,
            source = DiagnosticObservationSource.LINK_PROPERTIES,
            value = DiagnosticObservationValue.TextValue(dnsSummary.take(MAX_TEXT_VALUE_LENGTH)),
            state = state,
        )
        addBooleanOrUnknown(builder, DiagnosticObservationCode.VALIDATED_NETWORK, DiagnosticStage.NETWORK_STATE, context.validated, state)
        addBooleanOrUnknown(builder, DiagnosticObservationCode.VPN_ACTIVE, DiagnosticStage.NETWORK_STATE, context.vpnActive, state)
        addBooleanOrUnknown(builder, DiagnosticObservationCode.CAPTIVE_PORTAL, DiagnosticStage.INTERNET, context.captivePortal, state)
        addBooleanOrUnknown(builder, DiagnosticObservationCode.PARTIAL_CONNECTIVITY, DiagnosticStage.INTERNET, context.partialConnectivity, state)
        addBooleanOrUnknown(builder, DiagnosticObservationCode.PRIVATE_DNS, DiagnosticStage.DNS, context.privateDnsActive, state)
        context.privateDnsServerName?.takeIf(String::isNotBlank)?.let {
            builder.addObservation(
                code = DiagnosticObservationCode.PRIVATE_DNS,
                stage = DiagnosticStage.DNS,
                source = DiagnosticObservationSource.LINK_PROPERTIES,
                value = DiagnosticObservationValue.TextValue(it),
                state = state,
            )
        }
    }

    private fun addBooleanOrUnknown(
        builder: EvidenceBuilder,
        code: DiagnosticObservationCode,
        stage: DiagnosticStage,
        value: Boolean?,
        state: DiagnosticObservationState,
    ) {
        builder.addObservation(
            code = code,
            stage = stage,
            source = DiagnosticObservationSource.NETWORK_CAPABILITIES,
            value = value?.let(DiagnosticObservationValue::BooleanValue)
                ?: DiagnosticObservationValue.TextValue("UNKNOWN"),
            state = if (value == null) {
                if (state == DiagnosticObservationState.UNAVAILABLE) {
                    DiagnosticObservationState.UNAVAILABLE
                } else {
                    DiagnosticObservationState.UNKNOWN
                }
            } else {
                state
            },
        )
    }

    private fun networkCheck(builder: EvidenceBuilder, read: ContextRead): DiagnosticCheck =
        DiagnosticCheck(
            // Keep the nullable platform value in a local before branching: the
            // model is supplied by another module and cannot be smart-cast inline.
            code = DiagnosticCheckCode.NETWORK_STATE,
            stage = DiagnosticStage.NETWORK_STATE,
            status = when {
                !read.available || read.context.activeNetworkAvailable == null -> DiagnosticCheckStatus.UNKNOWN
                read.context.activeNetworkAvailable == true -> DiagnosticCheckStatus.PASS
                else -> DiagnosticCheckStatus.FAIL
            },
            severity = when {
                !read.available || read.context.activeNetworkAvailable == null -> DiagnosticSeverity.NOTICE
                read.context.activeNetworkAvailable == true -> DiagnosticSeverity.HEALTHY
                else -> DiagnosticSeverity.ERROR
            },
            summary = when {
                !read.available -> "无法从系统网络接口读取当前网络状态。"
                read.context.activeNetworkAvailable == null -> "当前活动网络状态未能确认。"
                read.context.activeNetworkAvailable == true -> "已发现活动网络。"
                else -> "设备当前没有活动网络。"
            },
            networkFingerprint = builder.fingerprint,
            evidenceObservationIds = builder.observationIds(DiagnosticStage.NETWORK_STATE),
        )

    private fun runIpConfiguration(builder: EvidenceBuilder, onProgress: (DiagnosticStageProgress) -> Unit) {
        emit(onProgress, DiagnosticStage.IP_CONFIGURATION, DiagnosticStageState.RUNNING)
        val addresses = builder.context?.let { context ->
            listOfNotNull(context.ipv4Address) + context.ipv6Addresses + listOfNotNull(context.ipv6Address)
        }.orEmpty().distinct().filter(String::isNotBlank).take(MAX_LOCAL_ADDRESSES)
        val check = DiagnosticCheck(
            code = DiagnosticCheckCode.IP_CONFIGURATION,
            stage = DiagnosticStage.IP_CONFIGURATION,
            status = if (addresses.isNotEmpty()) DiagnosticCheckStatus.PASS else DiagnosticCheckStatus.UNKNOWN,
            severity = if (addresses.isNotEmpty()) DiagnosticSeverity.HEALTHY else DiagnosticSeverity.NOTICE,
            summary = if (addresses.isNotEmpty()) "已观察到本机 IP 地址。" else "本机 IP 配置未能确认。",
            networkFingerprint = builder.fingerprint,
            evidenceObservationIds = builder.observationIds(DiagnosticStage.IP_CONFIGURATION),
        )
        builder.addCheck(check)
        emitCompleted(onProgress, check)
    }

    private suspend fun runGateway(builder: EvidenceBuilder, onProgress: (DiagnosticStageProgress) -> Unit) {
        emit(onProgress, DiagnosticStage.GATEWAY, DiagnosticStageState.RUNNING)
        val context = builder.context ?: NetworkContext.unknown()
        val gateway = context.gateway
        val check = when {
            context.connectionType == ConnectionType.CELLULAR -> DiagnosticCheck(
                code = DiagnosticCheckCode.GATEWAY,
                stage = DiagnosticStage.GATEWAY,
                status = DiagnosticCheckStatus.NOT_APPLICABLE,
                severity = DiagnosticSeverity.NOTICE,
                summary = "移动网络不执行传统局域网网关探测。",
                target = gateway?.let { targetForHost(it) },
                networkFingerprint = builder.fingerprint,
                evidenceObservationIds = builder.observationIds(DiagnosticStage.GATEWAY),
            )

            gateway.isNullOrBlank() -> DiagnosticCheck(
                code = DiagnosticCheckCode.GATEWAY,
                stage = DiagnosticStage.GATEWAY,
                status = DiagnosticCheckStatus.NOT_APPLICABLE,
                severity = DiagnosticSeverity.NOTICE,
                summary = "当前网络未提供可直接检测的默认网关。",
                networkFingerprint = builder.fingerprint,
                evidenceObservationIds = builder.observationIds(DiagnosticStage.GATEWAY),
            )

            gateway.isUnscopedIpv6LinkLocal() -> DiagnosticCheck(
                code = DiagnosticCheckCode.GATEWAY,
                stage = DiagnosticStage.GATEWAY,
                status = DiagnosticCheckStatus.UNKNOWN,
                severity = DiagnosticSeverity.NOTICE,
                summary = "IPv6 链路本地网关缺少可可靠使用的接口 scope，未执行探测。",
                target = targetForHost(gateway),
                networkFingerprint = builder.fingerprint,
                evidenceObservationIds = builder.observationIds(DiagnosticStage.GATEWAY),
            )

            else -> gatewayCheck(builder, gateway, runGatewayProbe(gateway))
        }
        builder.addCheck(check)
        emitCompleted(onProgress, check)
    }

    private suspend fun runGatewayProbe(gateway: String): PingSessionResult? = try {
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

    private fun gatewayCheck(
        builder: EvidenceBuilder,
        gateway: String,
        result: PingSessionResult?,
    ): DiagnosticCheck {
        val responded = result?.receivedPackets?.let { it > 0 } == true
        val outcome = when {
            result == null -> "UNKNOWN"
            responded -> "RESPONDED"
            else -> "NO_RESPONSE"
        }
        val observationId = builder.addObservation(
            code = DiagnosticObservationCode.GATEWAY_PROBE_OUTCOME,
            stage = DiagnosticStage.GATEWAY,
            source = DiagnosticObservationSource.PING_ENGINE,
            value = DiagnosticObservationValue.TextValue(outcome),
            state = if (result == null) DiagnosticObservationState.UNKNOWN else DiagnosticObservationState.CONFIRMED,
        )
        return DiagnosticCheck(
            code = DiagnosticCheckCode.GATEWAY,
            stage = DiagnosticStage.GATEWAY,
            status = when {
                result == null -> DiagnosticCheckStatus.UNKNOWN
                responded -> DiagnosticCheckStatus.PASS
                else -> DiagnosticCheckStatus.FAIL
            },
            severity = if (responded) DiagnosticSeverity.HEALTHY else DiagnosticSeverity.NOTICE,
            summary = when {
                result == null -> "网关探测未能完成。"
                responded -> "网关可达性探测收到响应。"
                else -> "网关未响应当前系统可达性探测。"
            },
            target = targetForHost(gateway, DIAGNOSTIC_TIMEOUT_MS),
            method = result?.method?.name,
            observedAt = result?.endTime,
            networkFingerprint = builder.fingerprint,
            evidenceObservationIds = listOf(observationId),
        )
    }

    private suspend fun runPublicConnectivity(
        builder: EvidenceBuilder,
        onProgress: (DiagnosticStageProgress) -> Unit,
    ) {
        emit(onProgress, DiagnosticStage.INTERNET, DiagnosticStageState.RUNNING)
        if (probeTargets.publicTargets.isEmpty()) {
            val check = DiagnosticCheck(
                code = DiagnosticCheckCode.PUBLIC_CONNECTIVITY,
                stage = DiagnosticStage.INTERNET,
                status = DiagnosticCheckStatus.UNKNOWN,
                severity = DiagnosticSeverity.NOTICE,
                summary = "没有配置公网 TCP 探测目标。",
                networkFingerprint = builder.fingerprint,
            )
            builder.addCheck(check)
            emitCompleted(onProgress, check)
            return
        }

        probeTargets.publicTargets.forEachIndexed { index, target ->
            currentCoroutineContext().ensureActive()
            val result = try {
                tcpPortChecker.check(target.host, target.port, DIAGNOSTIC_TIMEOUT_MS)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            val outcome = result?.diagnosticOutcome() ?: DiagnosticTcpOutcome.UNKNOWN
            val observationId = builder.addObservation(
                code = DiagnosticObservationCode.PUBLIC_TCP_OUTCOME,
                stage = DiagnosticStage.INTERNET,
                source = DiagnosticObservationSource.TCP_CHECKER,
                value = DiagnosticObservationValue.TcpOutcomeValue(outcome),
                state = outcome.evidenceState(),
                suffix = index.toString(),
            )
            val check = DiagnosticCheck(
                code = DiagnosticCheckCode.PUBLIC_CONNECTIVITY,
                stage = DiagnosticStage.INTERNET,
                status = outcome.publicCheckStatus(),
                severity = if (outcome == DiagnosticTcpOutcome.CONNECT_SUCCESS) {
                    DiagnosticSeverity.HEALTHY
                } else {
                    DiagnosticSeverity.NOTICE
                },
                summary = "${target.host}:${target.port} TCP 探测结果为 ${outcome.name}。",
                target = targetForHost(target.host, target.port),
                method = "TCP_CONNECT",
                observedAt = result?.let { now() },
                networkFingerprint = builder.fingerprint,
                evidenceObservationIds = listOf(observationId),
            )
            builder.addCheck(check)
            emitCompleted(onProgress, check)
        }
    }

    private suspend fun runDnsHealth(
        builder: EvidenceBuilder,
        onProgress: (DiagnosticStageProgress) -> Unit,
    ) {
        emit(onProgress, DiagnosticStage.DNS, DiagnosticStageState.RUNNING)
        val result = try {
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
            null
        }
        val check = dnsCheck(builder, result, probeTargets.domainName, "baseline")
        builder.addCheck(check)
        emitCompleted(onProgress, check)
    }

    private suspend fun runTarget(
        builder: EvidenceBuilder,
        onProgress: (DiagnosticStageProgress) -> Unit,
    ) {
        emit(onProgress, DiagnosticStage.TARGET, DiagnosticStageState.RUNNING)
        val target = builder.intent.target
        if (target == null) {
            val check = DiagnosticCheck(
                code = DiagnosticCheckCode.TARGET_CONNECTIVITY,
                stage = DiagnosticStage.TARGET,
                status = DiagnosticCheckStatus.SKIPPED,
                severity = DiagnosticSeverity.NOTICE,
                summary = "未选择目标，未执行目标访问检查。",
                networkFingerprint = builder.fingerprint,
            )
            builder.addCheck(check)
            emit(onProgress, DiagnosticStage.TARGET, DiagnosticStageState.SKIPPED)
            return
        }

        if (target.kind == DiagnosticTargetKind.DOMAIN) {
            val dnsResult = try {
                dnsQueryEngine.lookup(
                    DnsLookupRequest(
                        queryName = target.value,
                        recordTypes = setOf(DnsRecordType.A, DnsRecordType.AAAA),
                        timeoutMs = DIAGNOSTIC_TIMEOUT_MS,
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            val dnsCheck = dnsCheck(builder, dnsResult, target.value, "target")
            builder.addCheck(dnsCheck)
            val addresses = dnsResult?.records.orEmpty()
                .filter { it.type == DnsRecordType.A || it.type == DnsRecordType.AAAA }
                .map { it.value }
                .distinct()
                .take(MAX_TARGET_ADDRESSES)
            if (addresses.isEmpty()) {
                val check = DiagnosticCheck(
                    code = DiagnosticCheckCode.TARGET_CONNECTIVITY,
                    stage = DiagnosticStage.TARGET,
                    status = DiagnosticCheckStatus.SKIPPED,
                    severity = DiagnosticSeverity.NOTICE,
                    summary = "目标域名没有可用于 TCP 检查的地址。",
                    target = target,
                    networkFingerprint = builder.fingerprint,
                    evidenceObservationIds = dnsCheck.evidenceObservationIds,
                )
                builder.addCheck(check)
                emit(onProgress, DiagnosticStage.TARGET, DiagnosticStageState.SKIPPED)
                return
            }
            addresses.forEachIndexed { index, address ->
                runTargetTcp(builder, targetForHost(address, target.port), onProgress, index)
            }
        } else {
            runTargetTcp(builder, target, onProgress, 0)
        }
    }

    private suspend fun runTargetTcp(
        builder: EvidenceBuilder,
        target: DiagnosticTarget,
        onProgress: (DiagnosticStageProgress) -> Unit,
        index: Int,
    ) {
        currentCoroutineContext().ensureActive()
        val result = try {
            tcpPortChecker.check(target.value, target.port, DIAGNOSTIC_TIMEOUT_MS)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        val outcome = result?.diagnosticOutcome() ?: DiagnosticTcpOutcome.UNKNOWN
        val observationId = builder.addObservation(
            code = DiagnosticObservationCode.TARGET_TCP_OUTCOME,
            stage = DiagnosticStage.TARGET,
            source = DiagnosticObservationSource.TCP_CHECKER,
            value = DiagnosticObservationValue.TcpOutcomeValue(outcome),
            state = outcome.evidenceState(),
            suffix = index.toString(),
        )
        val check = DiagnosticCheck(
            code = DiagnosticCheckCode.TARGET_CONNECTIVITY,
            stage = DiagnosticStage.TARGET,
            status = outcome.targetCheckStatus(),
            severity = if (outcome == DiagnosticTcpOutcome.CONNECT_SUCCESS) {
                DiagnosticSeverity.HEALTHY
            } else {
                DiagnosticSeverity.NOTICE
            },
            summary = "${target.value}:${target.port} TCP 目标探测结果为 ${outcome.name}。",
            target = target,
            method = "TCP_CONNECT",
            observedAt = result?.let { now() },
            networkFingerprint = builder.fingerprint,
            evidenceObservationIds = listOf(observationId),
        )
        builder.addCheck(check)
        emitCompleted(onProgress, check)
    }

    private fun dnsCheck(
        builder: EvidenceBuilder,
        result: DnsLookupResult?,
        queryName: String,
        label: String,
    ): DiagnosticCheck {
        if (result == null) {
            val observationId = builder.addObservation(
                code = DiagnosticObservationCode.DNS_OUTCOME,
                stage = DiagnosticStage.DNS,
                source = DiagnosticObservationSource.DNS_ENGINE,
                value = DiagnosticObservationValue.DnsOutcomeValue(DiagnosticDnsOutcome.UNKNOWN),
                state = DiagnosticObservationState.UNKNOWN,
                suffix = label,
            )
            return DiagnosticCheck(
                code = DiagnosticCheckCode.DNS_RESOLUTION,
                stage = DiagnosticStage.DNS,
                status = DiagnosticCheckStatus.UNKNOWN,
                severity = DiagnosticSeverity.NOTICE,
                summary = "DNS 查询结果未能确认。",
                target = DiagnosticTarget(queryName, DiagnosticTargetKind.DOMAIN),
                method = "SYSTEM_DNS",
                networkFingerprint = builder.fingerprint,
                evidenceObservationIds = listOf(observationId),
            )
        }

        val outcome = result.diagnosticOutcome()
        val observationIds = mutableListOf(
            builder.addObservation(
                code = DiagnosticObservationCode.DNS_OUTCOME,
                stage = DiagnosticStage.DNS,
                source = DiagnosticObservationSource.DNS_ENGINE,
                value = DiagnosticObservationValue.DnsOutcomeValue(outcome),
                state = outcome.evidenceState(),
                suffix = label,
            ),
        )
        result.records.forEachIndexed { index, record ->
            val recordObservationId = builder.addObservation(
                code = DiagnosticObservationCode.DNS_RECORD,
                stage = DiagnosticStage.DNS,
                source = DiagnosticObservationSource.DNS_ENGINE,
                value = DiagnosticObservationValue.DnsRecordValue(
                    recordType = record.type.name,
                    name = record.name.ifBlank { result.queryName }.take(MAX_DNS_NAME_LENGTH),
                    value = record.value.take(MAX_TEXT_VALUE_LENGTH),
                    ttlSeconds = record.ttl,
                    priority = record.priority,
                ),
                state = DiagnosticObservationState.CONFIRMED,
                suffix = "$label-record-$index",
            )
            observationIds += recordObservationId
            if (DiagnosticAddressClassifier.isFakeIp(record.value)) {
                observationIds += builder.addObservation(
                    code = DiagnosticObservationCode.FAKE_IP_RANGE_MATCH,
                    stage = DiagnosticStage.DNS,
                    source = DiagnosticObservationSource.DNS_ENGINE,
                    value = DiagnosticObservationValue.TextValue(record.value),
                    state = DiagnosticObservationState.CONFIRMED,
                    suffix = "$label-fake-$index",
                )
            }
        }
        return DiagnosticCheck(
            code = DiagnosticCheckCode.DNS_RESOLUTION,
            stage = DiagnosticStage.DNS,
            status = result.diagnosticCheckStatus(),
            severity = when (result.status) {
                DnsLookupStatus.SUCCESS -> DiagnosticSeverity.HEALTHY
                DnsLookupStatus.NO_RECORDS,
                DnsLookupStatus.NXDOMAIN,
                -> DiagnosticSeverity.NOTICE

                else -> DiagnosticSeverity.NOTICE
            },
            summary = "${queryName} DNS 查询结果为 ${result.status.name}。",
            target = DiagnosticTarget(queryName, DiagnosticTargetKind.DOMAIN),
            method = result.method.name,
            observedAt = result.endTime,
            networkFingerprint = builder.fingerprint,
            evidenceObservationIds = observationIds,
        )
    }

    private suspend fun confirmStable(
        builder: EvidenceBuilder,
        onProgress: (DiagnosticStageProgress) -> Unit,
    ): Boolean {
        if (builder.networkChanged) return false
        val read = readNetworkContext()
        if (!read.available) return true
        val current = fingerprintProvider.fingerprint(read.context)
        if (builder.fingerprint != null && current != null && builder.fingerprint != current) {
            builder.networkChanged = true
            val observationId = builder.addObservation(
                code = DiagnosticObservationCode.NETWORK_CHANGED,
                stage = DiagnosticStage.NETWORK_STATE,
                source = DiagnosticObservationSource.NETWORK_REPOSITORY,
                value = DiagnosticObservationValue.BooleanValue(true),
                state = DiagnosticObservationState.CONFIRMED,
                networkFingerprint = current,
            )
            val check = DiagnosticCheck(
                code = DiagnosticCheckCode.NETWORK_STABILITY,
                stage = DiagnosticStage.NETWORK_STATE,
                status = DiagnosticCheckStatus.UNKNOWN,
                severity = DiagnosticSeverity.NOTICE,
                summary = "检测期间网络指纹发生变化，已停止后续网络探测。",
                networkFingerprint = current,
                evidenceObservationIds = listOf(observationId),
            )
            builder.addCheck(check)
            emit(onProgress, DiagnosticStage.NETWORK_STATE, DiagnosticStageState.UNKNOWN)
            return false
        }
        return true
    }

    private fun addSkippedStage(
        builder: EvidenceBuilder,
        onProgress: (DiagnosticStageProgress) -> Unit,
        stage: DiagnosticStage,
        reason: String,
    ) {
        val code = when (stage) {
            DiagnosticStage.IP_CONFIGURATION -> DiagnosticCheckCode.IP_CONFIGURATION
            DiagnosticStage.GATEWAY -> DiagnosticCheckCode.GATEWAY
            DiagnosticStage.INTERNET -> DiagnosticCheckCode.PUBLIC_CONNECTIVITY
            DiagnosticStage.DNS -> DiagnosticCheckCode.DNS_RESOLUTION
            DiagnosticStage.TARGET -> DiagnosticCheckCode.TARGET_CONNECTIVITY
            else -> DiagnosticCheckCode.NETWORK_STABILITY
        }
        val check = DiagnosticCheck(
            code = code,
            stage = stage,
            status = DiagnosticCheckStatus.SKIPPED,
            severity = DiagnosticSeverity.NOTICE,
            summary = reason,
            networkFingerprint = builder.fingerprint,
        )
        builder.addCheck(check)
        emit(onProgress, stage, DiagnosticStageState.SKIPPED)
    }

    private fun emitCompleted(
        onProgress: (DiagnosticStageProgress) -> Unit,
        check: DiagnosticCheck,
    ) {
        val state = when (check.status) {
            DiagnosticCheckStatus.NOT_APPLICABLE -> DiagnosticStageState.NOT_APPLICABLE
            DiagnosticCheckStatus.SKIPPED -> DiagnosticStageState.SKIPPED
            DiagnosticCheckStatus.UNKNOWN -> DiagnosticStageState.UNKNOWN
            else -> DiagnosticStageState.COMPLETED
        }
        emit(onProgress, check.stage, state)
    }

    private fun emit(
        onProgress: (DiagnosticStageProgress) -> Unit,
        stage: DiagnosticStage,
        state: DiagnosticStageState,
    ) {
        onProgress(DiagnosticStageProgress(stage, state))
    }

    private fun targetForHost(host: String, port: Int = DiagnosticTarget.DEFAULT_PORT): DiagnosticTarget = DiagnosticTarget(
        value = host,
        kind = when {
            host.contains(':') -> DiagnosticTargetKind.IPV6
            host.matches(IPV4_PATTERN) -> DiagnosticTargetKind.IPV4
            else -> DiagnosticTargetKind.DOMAIN
        },
        port = port,
    )

    private fun addressFamily(value: String): DiagnosticAddressFamily = if (value.contains(':')) {
        DiagnosticAddressFamily.IPV6
    } else {
        DiagnosticAddressFamily.IPV4
    }

    private fun String.isUnscopedIpv6LinkLocal(): Boolean {
        if (contains('%') || !contains(':')) return false
        val firstHextet = substringBefore(':').toIntOrNull(16) ?: return false
        return firstHextet in 0xfe80..0xfe9f
    }

    private fun TcpProbeResult.diagnosticOutcome(): DiagnosticTcpOutcome =
        outcome ?: if (success) DiagnosticTcpOutcome.CONNECT_SUCCESS else DiagnosticTcpOutcome.UNKNOWN

    private fun DiagnosticTcpOutcome.evidenceState(): DiagnosticObservationState = when (this) {
        DiagnosticTcpOutcome.UNKNOWN,
        DiagnosticTcpOutcome.INTERNAL_ERROR,
        -> DiagnosticObservationState.UNKNOWN

        else -> DiagnosticObservationState.CONFIRMED
    }

    private fun DiagnosticTcpOutcome.publicCheckStatus(): DiagnosticCheckStatus = when (this) {
        DiagnosticTcpOutcome.CONNECT_SUCCESS,
        DiagnosticTcpOutcome.CONNECTION_REFUSED,
        -> DiagnosticCheckStatus.PASS

        DiagnosticTcpOutcome.UNKNOWN,
        DiagnosticTcpOutcome.INTERNAL_ERROR,
        -> DiagnosticCheckStatus.UNKNOWN

        else -> DiagnosticCheckStatus.FAIL
    }

    private fun DiagnosticTcpOutcome.targetCheckStatus(): DiagnosticCheckStatus = when (this) {
        DiagnosticTcpOutcome.CONNECT_SUCCESS -> DiagnosticCheckStatus.PASS
        DiagnosticTcpOutcome.UNKNOWN,
        DiagnosticTcpOutcome.INTERNAL_ERROR,
        -> DiagnosticCheckStatus.UNKNOWN

        else -> DiagnosticCheckStatus.FAIL
    }

    private fun DnsLookupResult.diagnosticOutcome(): DiagnosticDnsOutcome = when (status) {
        DnsLookupStatus.SUCCESS -> DiagnosticDnsOutcome.SUCCESS
        DnsLookupStatus.PARTIAL -> DiagnosticDnsOutcome.PARTIAL
        DnsLookupStatus.NO_RECORDS -> DiagnosticDnsOutcome.NO_RECORDS
        DnsLookupStatus.NXDOMAIN -> DiagnosticDnsOutcome.NXDOMAIN
        DnsLookupStatus.TIMEOUT -> DiagnosticDnsOutcome.TIMEOUT
        DnsLookupStatus.NETWORK_ERROR -> DiagnosticDnsOutcome.NETWORK_ERROR
        DnsLookupStatus.INVALID_RESPONSE -> DiagnosticDnsOutcome.INVALID_RESPONSE
        else -> DiagnosticDnsOutcome.UNKNOWN
    }

    private fun DiagnosticDnsOutcome.evidenceState(): DiagnosticObservationState = when (this) {
        DiagnosticDnsOutcome.UNKNOWN -> DiagnosticObservationState.UNKNOWN
        else -> DiagnosticObservationState.CONFIRMED
    }

    private fun DnsLookupResult.diagnosticCheckStatus(): DiagnosticCheckStatus = when (status) {
        DnsLookupStatus.SUCCESS -> DiagnosticCheckStatus.PASS
        DnsLookupStatus.NO_RECORDS -> DiagnosticCheckStatus.NO_RECORDS
        DnsLookupStatus.NXDOMAIN -> DiagnosticCheckStatus.FAIL
        DnsLookupStatus.TIMEOUT,
        DnsLookupStatus.NETWORK_ERROR,
        DnsLookupStatus.INVALID_RESPONSE,
        DnsLookupStatus.PARTIAL,
        DnsLookupStatus.INVALID_QUERY,
        DnsLookupStatus.FAILED,
        -> DiagnosticCheckStatus.FAIL
    }

    private data class ContextRead(
        val context: NetworkContext,
        val available: Boolean,
    )

    private class EvidenceBuilder(
        private val startedAt: Long,
        val intent: DiagnosticIntent,
    ) {
        var context: NetworkContext? = null
        var fingerprint: com.networktoolbox.core.common.diagnostic.NetworkFingerprint? = null
        var networkChanged: Boolean = false
        private val observations = mutableListOf<DiagnosticObservation>()
        private val checks = mutableListOf<DiagnosticCheck>()

        fun addObservation(
            code: DiagnosticObservationCode,
            stage: DiagnosticStage,
            source: DiagnosticObservationSource,
            value: DiagnosticObservationValue,
            state: DiagnosticObservationState,
            suffix: String? = null,
            networkFingerprint: com.networktoolbox.core.common.diagnostic.NetworkFingerprint? = fingerprint,
        ): String {
            val id = buildString {
                append(stage.name.lowercase())
                append('.')
                append(code.name.lowercase())
                suffix?.let {
                    append('.')
                    append(it)
                }
            }
            val uniqueId = if (observations.any { it.id == id }) "$id.${observations.size}" else id
            observations += DiagnosticObservation(
                id = uniqueId,
                code = code,
                stage = stage,
                source = source,
                value = value,
                observedAt = startedAt,
                networkFingerprint = networkFingerprint,
                evidenceState = state,
            )
            return uniqueId
        }

        fun addCheck(check: DiagnosticCheck) {
            checks += check
        }

        fun observationIds(stage: DiagnosticStage): List<String> =
            observations.filter { it.stage == stage }.map { it.id }

        fun finish(status: DiagnosticRunStatus, finishedAt: Long): DiagnosticRunEvidence =
            DiagnosticRunEvidence(
                runStatus = status,
                startedAt = startedAt,
                finishedAt = finishedAt.coerceAtLeast(startedAt),
                durationMs = (finishedAt - startedAt).coerceAtLeast(0L),
                fingerprint = fingerprint,
                networkContextSummary = context?.toSummary(),
                observations = observations.toList(),
                checks = checks.toList(),
                intent = intent,
            )

        private fun NetworkContext.toSummary(): DiagnosticNetworkSummary = DiagnosticNetworkSummary(
            connectionType = connectionType.toDiagnosticType(),
            localAddressSummary = buildList {
                ipv4Address?.let(::add)
                ipv6Addresses.ifEmpty { listOfNotNull(ipv6Address) }.distinct().forEach(::add)
            }.take(MAX_LOCAL_ADDRESSES),
            prefixLength = ipv4PrefixLength,
            gateway = gateway,
            configuredDnsServers = dnsServers.take(MAX_DNS_SERVERS),
            vpnActive = vpnActive,
            privateDnsActive = privateDnsActive,
            privateDnsServerName = privateDnsServerName,
            validated = validated,
        )

        private fun ConnectionType.toDiagnosticType(): DiagnosticConnectionType = when (this) {
            ConnectionType.WIFI -> DiagnosticConnectionType.WIFI
            ConnectionType.CELLULAR -> DiagnosticConnectionType.CELLULAR
            ConnectionType.ETHERNET -> DiagnosticConnectionType.ETHERNET
            ConnectionType.BLUETOOTH -> DiagnosticConnectionType.BLUETOOTH
            ConnectionType.VPN -> DiagnosticConnectionType.VPN
            ConnectionType.UNKNOWN -> DiagnosticConnectionType.UNKNOWN
        }
    }

    private companion object {
        const val DIAGNOSTIC_PING_COUNT = 3
        const val DIAGNOSTIC_PING_INTERVAL_MS = 100
        const val DIAGNOSTIC_TIMEOUT_MS = 2_000
        const val MAX_LOCAL_ADDRESSES = 16
        const val MAX_DNS_SERVERS = 16
        const val MAX_TARGET_ADDRESSES = 2
        const val MAX_DNS_NAME_LENGTH = 256
        const val MAX_TEXT_VALUE_LENGTH = 512
        val IPV4_PATTERN = Regex("\\d{1,3}(?:\\.\\d{1,3}){3}")
    }
}
