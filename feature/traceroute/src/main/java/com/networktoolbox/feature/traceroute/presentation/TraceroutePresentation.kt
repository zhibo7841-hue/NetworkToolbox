package com.networktoolbox.feature.traceroute.presentation

import com.networktoolbox.core.network.traceroute.TracerouteHopStatus
import com.networktoolbox.core.network.traceroute.TracerouteProbeStatus
import com.networktoolbox.core.network.traceroute.TracerouteResult
import com.networktoolbox.core.network.traceroute.TracerouteStatus
import com.networktoolbox.core.network.traceroute.TracerouteFakeIpDetector

data class TracerouteHopStatistics(
    val totalProbedHops: Int,
    val respondedHopCount: Int,
    val timeoutOnlyHopCount: Int,
)

object TracerouteHopStatisticsCalculator {
    fun from(hops: List<com.networktoolbox.core.network.traceroute.TracerouteHop>): TracerouteHopStatistics =
        TracerouteHopStatistics(
            totalProbedHops = hops.size,
            respondedHopCount = hops.count { hop ->
                hop.status == TracerouteHopStatus.RESPONDED ||
                    hop.status == TracerouteHopStatus.DESTINATION_REACHED
            },
            timeoutOnlyHopCount = hops.count { it.status == TracerouteHopStatus.TIMEOUT },
        )
}

data class TracerouteResultPresentation(
    val heading: String,
    val statusLabel: String,
    val summary: String,
    val explanation: String? = null,
    val notice: String? = null,
)

object TraceroutePresentationMapper {
    fun from(result: TracerouteResult): TracerouteResultPresentation {
        val timeoutExplanation = result.hops.intermediateTimeoutExplanation()
        val notice = when {
            result.fakeIpDetected -> fakeIpNotice(result.resolvedAddress, detected = true)

            result.status == TracerouteStatus.NETWORK_CHANGED ->
                "检测过程中网络发生变化，部分结果可能来自不同网络环境。建议网络稳定后重新执行。"

            else -> null
        }

        return when (result.status) {
            TracerouteStatus.REACHED -> TracerouteResultPresentation(
                heading = "路由追踪完成",
                statusLabel = "已到达目标",
                summary = "已成功追踪到目标，共 ${result.hops.size} 跳。",
                explanation = timeoutExplanation
                    ?: "路径中的响应节点已记录，结果仅表示本次探测观察到的路径。",
                notice = notice,
            )

            TracerouteStatus.PARTIAL -> TracerouteResultPresentation(
                heading = "已获取部分路径",
                statusLabel = "未确认到达",
                summary = partialSummary(result.hops),
                explanation = timeoutExplanation
                    ?: "部分节点没有响应，剩余路径暂时无法确认；这不一定表示网络故障。",
                notice = notice,
            )

            TracerouteStatus.CANCELLED -> TracerouteResultPresentation(
                heading = "追踪已停止",
                statusLabel = "已取消",
                summary = "本次路由追踪已由用户停止。",
                explanation = "未生成完整的路径结论。",
                notice = notice,
            )

            TracerouteStatus.NETWORK_CHANGED -> TracerouteResultPresentation(
                heading = "网络发生变化",
                statusLabel = "结果未确认",
                summary = "检测期间网络环境发生变化，本次结果不适合做完整判断。",
                explanation = "请在网络稳定后重新执行路由追踪。",
                notice = notice,
            )

            TracerouteStatus.FAILED -> TracerouteResultPresentation(
                heading = if (isGenericLocalFailure(result.errorMessage)) "路由追踪未完成" else "路由追踪失败",
                statusLabel = if (isGenericLocalFailure(result.errorMessage)) "未完成" else "无法完成",
                summary = userErrorMessage(result.errorMessage),
                explanation = if (isGenericLocalFailure(result.errorMessage)) {
                    "本次追踪未能完成，请稍后重试。"
                } else {
                    "请检查目标地址、当前网络连接或系统对该探测方式的支持情况。"
                },
                notice = notice,
            )

            TracerouteStatus.RUNNING -> TracerouteResultPresentation(
                heading = "正在追踪",
                statusLabel = "检测中",
                summary = "正在收集路径节点。",
                notice = notice,
            )
        }
    }

    fun fakeIpNotice(resolvedAddress: String?, detected: Boolean = false): String? {
        if (!detected && !resolvedAddress.orEmpty().let(TracerouteFakeIpDetector::isFakeIp)) {
            return null
        }
        val address = resolvedAddress
            ?.takeIf(TracerouteFakeIpDetector::isFakeIp)
            ?.let { " $it" }
            .orEmpty()
        return "检测到特殊用途地址$address，当前网络可能使用 Fake-IP。路由追踪结果可能不代表目标服务器的真实公网路径。"
    }

    fun cancelledSummary(hopCount: Int): String = if (hopCount > 0) {
        "本次路由追踪已取消，已获取 $hopCount 跳。"
    } else {
        "本次路由追踪已取消，可以重新开始。"
    }

    fun hopStatusLabel(hop: com.networktoolbox.core.network.traceroute.TracerouteHop): String? {
        val probes = hop.probes
        return when {
            hop.status == TracerouteHopStatus.DESTINATION_REACHED -> "目标"
            probes.isEmpty() || probes.all { it.status == TracerouteProbeStatus.TIMEOUT } -> "无响应"
            probes.any { it.status == TracerouteProbeStatus.TIMEOUT } -> "部分响应"
            else -> null
        }
    }

    fun hopAddress(hop: com.networktoolbox.core.network.traceroute.TracerouteHop): String =
        hop.address ?: "—"

    fun inputErrorMessage(raw: String): String = when {
        raw.contains("must not be empty", ignoreCase = true) -> "请输入 IPv4 地址或域名。"
        raw.contains("only ipv4", ignoreCase = true) ||
            raw.contains("ipv6 traceroute", ignoreCase = true) ->
            "当前版本的路由追踪暂不支持 IPv6。"
        raw.contains("unsupported characters", ignoreCase = true) ||
            raw.contains("invalid ipv4", ignoreCase = true) -> "请输入有效的 IPv4 地址或域名。"
        else -> "请输入有效的 IPv4 地址或域名。"
    }

    private fun userErrorMessage(raw: String?): String {
        val error = raw.orEmpty()
        return when {
            error.contains("no active network", ignoreCase = true) -> "当前没有可用网络。"
            error.contains("no ipv4 address", ignoreCase = true) ||
                error.contains("resolution", ignoreCase = true) -> "无法解析目标域名，请检查域名或 DNS。"
            error.contains("bind", ignoreCase = true) -> "无法在当前网络上启动路由追踪。"
            error.contains("permission", ignoreCase = true) -> "当前系统限制了路由追踪所需的网络能力。"
            error.contains("unsupported", ignoreCase = true) -> "当前设备暂不支持此路由追踪方式。"
            else -> "无法完成路由追踪，请稍后重试。"
        }
    }

    private fun partialSummary(hops: List<com.networktoolbox.core.network.traceroute.TracerouteHop>): String {
        val stats = TracerouteHopStatisticsCalculator.from(hops)
        return if (stats.totalProbedHops == 0) {
            "尚未获取有效路径，无法确认到达目标。"
        } else {
            "已完成 ${stats.totalProbedHops} 跳探测，其中 ${stats.respondedHopCount} 跳有响应，但尚未确认到达目标。"
        }
    }

    private fun isGenericLocalFailure(raw: String?): Boolean {
        val error = raw.orEmpty()
        return (
            error.contains("traceroute operation failed", ignoreCase = true) ||
                error.contains("local_error", ignoreCase = true) ||
                error.contains("local error", ignoreCase = true)
            ) &&
            !error.contains("bind", ignoreCase = true) &&
            !error.contains("permission", ignoreCase = true) &&
            !error.contains("unsupported", ignoreCase = true)
    }

    private fun List<com.networktoolbox.core.network.traceroute.TracerouteHop>.intermediateTimeoutExplanation(): String? {
        val firstResponseAfterTimeout = indices.firstOrNull { index ->
            index > 0 && this[index - 1].status == TracerouteHopStatus.TIMEOUT &&
                this[index].status != TracerouteHopStatus.TIMEOUT
        } ?: -1
        if (firstResponseAfterTimeout >= 0) {
            return "中间节点没有响应，但后续节点仍有响应；该节点可能过滤或限制探测报文。"
        }
        val last = lastOrNull() ?: return null
        if (last.status == TracerouteHopStatus.TIMEOUT) {
            return "末端节点没有响应，剩余路径无法确认；这不一定表示路由器或网络发生故障。"
        }
        return null
    }

    fun probeText(status: TracerouteProbeStatus, latencyMs: Long?): String = when {
        status == TracerouteProbeStatus.TIMEOUT -> "*"
        latencyMs == null -> "*"
        latencyMs <= 0L -> "< 1 ms"
        else -> "$latencyMs ms"
    }
}
