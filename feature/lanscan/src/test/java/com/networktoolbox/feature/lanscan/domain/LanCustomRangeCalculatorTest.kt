package com.networktoolbox.feature.lanscan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanCustomRangeCalculatorTest {
    private val calculator = LanCustomRangeCalculator()

    @Test
    fun `custom range is inclusive`() {
        assertEquals(1, valid("10.0.1.1", "10.0.1.1").hostCount)
        assertEquals(50, valid("10.0.1.1", "10.0.1.50").hostCount)
        assertEquals(254, valid("10.0.1.1", "10.0.1.254").hostCount)
    }

    @Test
    fun `range ending at broadcast sized 255 addresses is rejected`() {
        val result = calculator.calculate("10.0.1.1", "10.0.1.255")

        assertEquals(LanCustomRangeError.TOO_LARGE, invalid(result).reason)
    }

    @Test
    fun `range arithmetic crosses the last octet`() {
        val range = valid("10.0.1.250", "10.0.2.3")

        assertEquals(10, range.hostCount)
        assertEquals(
            (250..255).map { "10.0.1.$it" } + (0..3).map { "10.0.2.$it" },
            range.hostAddresses(),
        )
    }

    @Test
    fun `invalid input is rejected without throwing`() {
        assertEquals(LanCustomRangeError.INVALID_START, invalid(calculator.calculate("999.1.1.1", "10.0.1.2")).reason)
        assertEquals(LanCustomRangeError.INVALID_END, invalid(calculator.calculate("10.0.1.1", "10.0.1.256")).reason)
        assertEquals(LanCustomRangeError.START_AFTER_END, invalid(calculator.calculate("10.0.1.100", "10.0.1.10")).reason)
        assertTrue(calculator.calculate("", "").isNotValid())
        assertTrue(calculator.calculate("10.0.1", "10.0.1.2").isNotValid())
        assertTrue(calculator.calculate("2001:db8::1", "2001:db8::2").isNotValid())
    }

    @Test
    fun `public range is rejected while private ranges are accepted`() {
        val publicResult = calculator.calculate("8.8.8.1", "8.8.8.10")
        val privateResult = calculator.calculate("172.16.1.20", "172.16.1.30")

        assertEquals(LanCustomRangeError.NON_PRIVATE_RANGE, invalid(publicResult).reason)
        assertTrue(privateResult is LanCustomRangeResult.Valid)
    }

    @Test
    fun `incomplete input remains non-startable`() {
        val result = calculator.calculate("10.0.1.1", "")

        assertTrue(result is LanCustomRangeResult.Incomplete)
        assertTrue(result.isNotValid())
    }

    private fun valid(start: String, end: String) =
        (calculator.calculate(start, end) as LanCustomRangeResult.Valid).range

    private fun invalid(result: LanCustomRangeResult) =
        result as LanCustomRangeResult.Invalid

    private fun LanCustomRangeResult.isNotValid(): Boolean =
        this !is LanCustomRangeResult.Valid
}
