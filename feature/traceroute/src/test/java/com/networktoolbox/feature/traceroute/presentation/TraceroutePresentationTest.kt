package com.networktoolbox.feature.traceroute.presentation

import com.networktoolbox.core.network.traceroute.TracerouteAddressFamily
import com.networktoolbox.core.network.traceroute.TracerouteHop
import com.networktoolbox.core.network.traceroute.TracerouteHopStatus
import com.networktoolbox.core.network.traceroute.TracerouteProbeResult
import com.networktoolbox.core.network.traceroute.TracerouteProbeStatus
import com.networktoolbox.core.network.traceroute.TracerouteResult
import com.networktoolbox.core.network.traceroute.TracerouteStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TraceroutePresentationTest {
    @Test
    fun reachedResultUsesConservativeCompletedExplanation() {
        val presentation = TraceroutePresentationMapper.from(result(TracerouteStatus.REACHED))

        assertEquals("已到达目标", presentation.statusLabel)
        assertTrue(presentation.summary.contains("成功追踪"))
        assertFalse(presentation.summary.contains("故障"))
    }

    @Test
    fun partialResultIsNeutralAndExplainsUnconfirmedPath() {
        val presentation = TraceroutePresentationMapper.from(
            result(
                TracerouteStatus.PARTIAL,
                hops = listOf(timeoutHop(1), responseHop(2)),
            ),
        )

        assertEquals("未确认到达", presentation.statusLabel)
        assertTrue(presentation.explanation.orEmpty().contains("中间节点没有响应"))
    }

    @Test
    fun trailingTimeoutIsNotReportedAsRouterFailure() {
        val presentation = TraceroutePresentationMapper.from(
            result(TracerouteStatus.PARTIAL, hops = listOf(responseHop(1), timeoutHop(2))),
        )

        assertTrue(presentation.explanation.orEmpty().contains("不一定表示路由器或网络发生故障"))
    }

    @Test
    fun fakeIpIsNoticeNotFailure() {
        val presentation = TraceroutePresentationMapper.from(
            result(TracerouteStatus.REACHED, fakeIpDetected = true),
        )

        assertTrue(presentation.notice.orEmpty().contains("可能"))
        assertFalse(presentation.notice.orEmpty().contains("OpenClash"))
    }

    @Test
    fun networkChangeAndCancellationHaveSeparateUserStates() {
        val changed = TraceroutePresentationMapper.from(result(TracerouteStatus.NETWORK_CHANGED))
        val cancelled = TraceroutePresentationMapper.from(result(TracerouteStatus.CANCELLED))

        assertEquals("结果未确认", changed.statusLabel)
        assertTrue(changed.summary.contains("网络环境发生变化"))
        assertEquals("已取消", cancelled.statusLabel)
    }

    @Test
    fun errorsAreLocalizedWithoutLeakingNativeDetails() {
        val presentation = TraceroutePresentationMapper.from(
            result(TracerouteStatus.FAILED, errorMessage = "Traceroute operation failed at SENDTO (errno 113)."),
        )

        assertTrue(presentation.summary.contains("无法完成"))
        assertFalse(presentation.summary.contains("errno"))
    }

    @Test
    fun inputMessagesDistinguishIpv6AndInvalidTarget() {
        assertTrue(
            TraceroutePresentationMapper.inputErrorMessage("Only IPv4 traceroute is supported in Phase 1.")
                .contains("IPv6"),
        )
        assertTrue(
            TraceroutePresentationMapper.inputErrorMessage("Invalid IPv4 address or hostname.")
                .contains("有效"),
        )
    }

    @Test
    fun timeoutProbeIsDisplayedAsAsterisk() {
        assertEquals("*", TraceroutePresentationMapper.probeText(TracerouteProbeStatus.TIMEOUT, null))
        assertEquals("18 ms", TraceroutePresentationMapper.probeText(TracerouteProbeStatus.HOP, 18))
        assertEquals("< 1 ms", TraceroutePresentationMapper.probeText(TracerouteProbeStatus.HOP, 0))
    }

    private fun result(
        status: TracerouteStatus,
        hops: List<TracerouteHop> = listOf(responseHop(1)),
        fakeIpDetected: Boolean = false,
        errorMessage: String? = null,
    ) = TracerouteResult(
        targetInput = "example.com",
        resolvedAddress = "1.1.1.1",
        addressFamily = TracerouteAddressFamily.IPV4,
        hops = hops,
        status = status,
        durationMs = 1_234,
        fakeIpDetected = fakeIpDetected,
        errorMessage = errorMessage,
    )

    private fun responseHop(number: Int) = TracerouteHop(
        hopNumber = number,
        address = "192.0.2.$number",
        probes = listOf(
            TracerouteProbeResult(TracerouteProbeStatus.HOP, latencyMs = 18),
            TracerouteProbeResult(TracerouteProbeStatus.HOP, latencyMs = 20),
            TracerouteProbeResult(TracerouteProbeStatus.HOP, latencyMs = 21),
        ),
        status = TracerouteHopStatus.RESPONDED,
    )

    private fun timeoutHop(number: Int) = TracerouteHop(
        hopNumber = number,
        address = null,
        probes = listOf(TracerouteProbeResult(TracerouteProbeStatus.TIMEOUT)),
        status = TracerouteHopStatus.TIMEOUT,
    )
}
