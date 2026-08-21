package com.networktoolbox.core.common.history

import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryRecordFactoryTest {
    @Test
    fun pingRecordContainsRequiredProbeFields() {
        val record = HistoryRecordFactory.ping(
            timestamp = 1L,
            target = "example.com",
            success = false,
            latencyMs = null,
            method = "SYSTEM_REACHABILITY",
            errorMessage = "Target is not reachable.",
        )

        assertTrue(record.type == HistoryType.PING)
        assertTrue(record.detailJson.contains("\"target\":\"example.com\""))
        assertTrue(record.detailJson.contains("\"success\":false"))
        assertTrue(record.detailJson.contains("\"method\":\"SYSTEM_REACHABILITY\""))
        assertTrue(record.detailJson.contains("Target is not reachable."))
    }

    @Test
    fun dnsRecordContainsBothAddressRecordGroups() {
        val record = HistoryRecordFactory.dns(
            timestamp = 1L,
            domain = "example.com",
            success = true,
            aRecords = listOf("192.0.2.1"),
            aaaaRecords = listOf("2001:db8::1"),
            durationMs = 12L,
        )

        assertTrue(record.type == HistoryType.DNS)
        assertTrue(record.detailJson.contains("\"aRecords\":[\"192.0.2.1\"]"))
        assertTrue(record.detailJson.contains("\"aaaaRecords\":[\"2001:db8::1\"]"))
        assertTrue(record.detailJson.contains("\"durationMs\":12"))
    }

    @Test
    fun tcpAndReportRecordsPreserveDetails() {
        val tcp = HistoryRecordFactory.tcp(
            timestamp = 1L,
            host = "example.com",
            port = 443,
            success = false,
            latencyMs = null,
            errorMessage = "Connection refused",
        )
        val report = HistoryRecordFactory.report(
            timestamp = 1L,
            summary = "基础网络连接正常",
            findings = listOf(HistoryFinding("INFO", "Connectivity", "Observed")),
            suggestions = listOf("Check application configuration."),
        )

        assertTrue(tcp.type == HistoryType.TCP)
        assertTrue(tcp.detailJson.contains("\"port\":443"))
        assertTrue(tcp.detailJson.contains("Connection refused"))
        assertTrue(report.type == HistoryType.REPORT)
        assertTrue(report.detailJson.contains("\"findings\":[{"))
        assertTrue(report.detailJson.contains("Check application configuration."))
    }
}
