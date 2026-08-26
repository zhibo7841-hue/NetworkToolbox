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
    fun pingSessionUsesChineseQualitySummaryForHistory() {
        val record = HistoryRecordFactory.pingSession(
            timestamp = 1L,
            target = "example.com",
            address = "192.0.2.1",
            protocol = "IPV4",
            mode = "CONTINUOUS",
            startTime = 1L,
            endTime = 2L,
            sentPackets = 5,
            receivedPackets = 5,
            lostPackets = 0,
            packetLoss = 0.0,
            minLatencyMs = 10L,
            avgLatencyMs = 20.6,
            maxLatencyMs = 31L,
            jitterMs = 8.2,
            qualityLevel = "EXCELLENT",
            method = "SYSTEM_REACHABILITY",
            summary = "Excellent observed network quality.",
            errorMessage = null,
        )

        assertTrue(record.summary == "网络连接稳定，未检测到明显丢包。")
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
