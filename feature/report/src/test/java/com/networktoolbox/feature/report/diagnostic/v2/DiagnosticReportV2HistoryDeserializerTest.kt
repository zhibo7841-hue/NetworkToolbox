package com.networktoolbox.feature.report.diagnostic.v2

import com.networktoolbox.core.common.history.HistoryType
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiagnosticReportV2HistoryDeserializerTest {
    @Test
    fun roundTripRestoresTheVersionedReport() {
        val report = DiagnosticReportV2(
            timestamp = 1_700_000_000_000L,
            durationMs = 245L,
            overallStatus = DiagnosticOverallStatus.ATTENTION,
            overallSeverity = DiagnosticSeverity.WARNING,
            summary = "公网连接正常，但 DNS 查询失败。",
            networkSnapshot = NetworkContext(
                connectionType = ConnectionType.WIFI,
                ipv4Address = "192.0.2.10",
                ipv6Address = "2001:db8::10",
                gateway = "192.0.2.1",
                dnsServers = listOf("192.0.2.1", "2001:db8::53"),
                vpnActive = true,
                wifiName = "Lab Wi-Fi",
                wifiSignalLevel = 4,
                activeNetworkAvailable = true,
                validated = true,
            ),
            checks = listOf(
                DiagnosticCheck(
                    id = "DNS_RESOLUTION",
                    stage = DiagnosticStage.DNS,
                    name = "DNS 解析",
                    status = DiagnosticCheckStatus.FAIL,
                    severity = DiagnosticSeverity.ERROR,
                    summary = "DNS 查询未成功完成。",
                    target = "example.com",
                    method = "ANDROID_DNS_RESOLVER",
                    observedAt = 1_700_000_000_245L,
                    rawData = mapOf(
                        "error" to "Private DNS timeout",
                        "recordCount" to "0",
                    ),
                ),
            ),
            findings = listOf(
                DiagnosticFindingV2(
                    id = "DNS_FAILURE",
                    severity = DiagnosticSeverity.ERROR,
                    title = "DNS 查询未正常完成",
                    description = "当前 DNS 查询失败；问题可能与 DNS 服务有关。",
                    evidenceCheckIds = listOf("DNS_RESOLUTION"),
                ),
            ),
            recommendations = listOf(
                DiagnosticRecommendation(
                    priority = 1,
                    title = "重新进行 DNS 查询",
                    action = "再次查询以确认是否为暂时性异常。",
                    reason = "DNS 结果可能受瞬时网络变化影响。",
                ),
            ),
        )

        val record = DiagnosticReportV2HistorySerializer.toHistoryRecord(report)
        val restored = DiagnosticReportV2HistoryDeserializer.fromHistoryRecord(record)

        assertEquals(HistoryType.REPORT, record.type)
        assertEquals(report, restored)
    }

    @Test
    fun malformedOrLegacyPayloadIsIgnored() {
        assertNull(DiagnosticReportV2HistoryDeserializer.fromDetailJson("{broken"))
        assertNull(
            DiagnosticReportV2HistoryDeserializer.fromDetailJson(
                "{\"schemaVersion\":1}",
            ),
        )
    }
}
