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
import org.junit.Assert.assertNull
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
    fun partialSummaryCountsRespondingHopsInsteadOfAllProbedHops() {
        val hops = listOf(
            responseHop(1),
            responseHop(2),
            responseHop(3),
            responseHop(4),
        ) + (5..30).map(::timeoutHop)

        val presentation = TraceroutePresentationMapper.from(
            result(TracerouteStatus.PARTIAL, hops = hops),
        )
        val statistics = TracerouteHopStatisticsCalculator.from(hops)

        assertEquals(30, statistics.totalProbedHops)
        assertEquals(4, statistics.respondedHopCount)
        assertEquals(26, statistics.timeoutOnlyHopCount)
        assertTrue(presentation.summary.contains("完成 30 跳探测"))
        assertTrue(presentation.summary.contains("4 跳有响应"))
        assertFalse(presentation.summary.contains("30 跳响应"))
    }

    @Test
    fun hopLabelsOnlyDescribeSpecialProbeOutcomes() {
        assertNull(TraceroutePresentationMapper.hopStatusLabel(responseHop(1)))
        assertEquals("部分响应", TraceroutePresentationMapper.hopStatusLabel(partialHop(2)))
        assertEquals("无响应", TraceroutePresentationMapper.hopStatusLabel(timeoutHop(3)))
        assertEquals("目标", TraceroutePresentationMapper.hopStatusLabel(destinationHop(4)))
        assertEquals("—", TraceroutePresentationMapper.hopAddress(timeoutHop(3)))
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
    fun fakeIpNoticeIsAvailableAsSoonAsResolvedAddressIsKnown() {
        val notice = TraceroutePresentationMapper.fakeIpNotice("198.18.0.9")

        assertTrue(notice.orEmpty().contains("可能使用 Fake-IP"))
        assertFalse(notice.orEmpty().contains("OpenClash"))
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
    fun genericLocalFailureUsesIncompleteWording() {
        val presentation = TraceroutePresentationMapper.from(
            result(TracerouteStatus.FAILED, errorMessage = "Traceroute operation failed at SENDTO (errno 113)."),
        )

        assertEquals("路由追踪未完成", presentation.heading)
        assertTrue(presentation.explanation.orEmpty().contains("本次追踪未能完成"))
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

    private fun partialHop(number: Int) = TracerouteHop(
        hopNumber = number,
        address = "192.0.2.$number",
        probes = listOf(
            TracerouteProbeResult(TracerouteProbeStatus.HOP, latencyMs = 18),
            TracerouteProbeResult(TracerouteProbeStatus.TIMEOUT),
            TracerouteProbeResult(TracerouteProbeStatus.HOP, latencyMs = 21),
        ),
        status = TracerouteHopStatus.RESPONDED,
    )

    private fun destinationHop(number: Int) = TracerouteHop(
        hopNumber = number,
        address = "1.1.1.1",
        probes = listOf(TracerouteProbeResult(TracerouteProbeStatus.DESTINATION_REACHED, latencyMs = 18)),
        status = TracerouteHopStatus.DESTINATION_REACHED,
    )
}
