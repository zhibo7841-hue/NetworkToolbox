package com.networktoolbox.feature.lanscan.domain

import com.networktoolbox.core.common.ipv4.IPv4Address
import com.networktoolbox.feature.lanscan.domain.model.LanScanRange
import com.networktoolbox.feature.lanscan.domain.model.LanScanRangeSource

sealed interface LanCustomRangeResult {
    data object Incomplete : LanCustomRangeResult

    data class Invalid(
        val reason: LanCustomRangeError,
        val message: String,
    ) : LanCustomRangeResult

    data class Valid(val range: LanScanRange) : LanCustomRangeResult
}

enum class LanCustomRangeError {
    INVALID_START,
    INVALID_END,
    START_AFTER_END,
    TOO_LARGE,
    NON_PRIVATE_RANGE,
}

class LanCustomRangeCalculator(
    private val maxHostCount: Long = MAX_CUSTOM_HOST_COUNT,
) {
    init {
        require(maxHostCount in 1..MAX_CUSTOM_HOST_COUNT)
    }

    fun calculate(startInput: String, endInput: String): LanCustomRangeResult {
        if (startInput.isBlank() || endInput.isBlank()) {
            return LanCustomRangeResult.Incomplete
        }

        val start = IPv4Address.parse(startInput)
            ?: return LanCustomRangeResult.Invalid(
                reason = LanCustomRangeError.INVALID_START,
                message = "请输入有效的 IPv4 起始地址。",
            )
        val end = IPv4Address.parse(endInput)
            ?: return LanCustomRangeResult.Invalid(
                reason = LanCustomRangeError.INVALID_END,
                message = "请输入有效的 IPv4 结束地址。",
            )

        if (start.value > end.value) {
            return LanCustomRangeResult.Invalid(
                reason = LanCustomRangeError.START_AFTER_END,
                message = "结束地址不能小于起始地址。",
            )
        }

        if (!start.value.isRfc1918() || !end.value.isRfc1918()) {
            return LanCustomRangeResult.Invalid(
                reason = LanCustomRangeError.NON_PRIVATE_RANGE,
                message = "自定义扫描仅支持 RFC1918 私有 IPv4 地址范围。",
            )
        }

        val hostCount = end.value - start.value + 1L
        if (hostCount > maxHostCount) {
            return LanCustomRangeResult.Invalid(
                reason = LanCustomRangeError.TOO_LARGE,
                message = "自定义扫描单次最多支持 $MAX_CUSTOM_HOST_COUNT 个 IPv4 地址，请缩小扫描范围。",
            )
        }

        val firstHost = start.toDottedDecimal()
        val lastHost = end.toDottedDecimal()
        return LanCustomRangeResult.Valid(
            LanScanRange(
                networkAddress = firstHost,
                broadcastAddress = lastHost,
                firstHost = firstHost,
                lastHost = lastHost,
                hostCount = hostCount.toInt(),
                prefixLength = 32,
                originalNetworkAddress = firstHost,
                originalBroadcastAddress = lastHost,
                originalHostCount = hostCount,
                originalPrefixLength = 32,
                rangeWasLimited = false,
                rangeSource = LanScanRangeSource.CUSTOM,
            ),
        )
    }

    private fun Long.isRfc1918(): Boolean = when {
        this in 0x0A00_0000L..0x0AFF_FFFFL -> true
        this in 0xAC10_0000L..0xAC1F_FFFFL -> true
        this in 0xC0A8_0000L..0xC0A8_FFFFL -> true
        else -> false
    }

    private companion object {
        const val MAX_CUSTOM_HOST_COUNT = 254L
    }
}
