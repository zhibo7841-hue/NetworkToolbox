package com.networktoolbox.core.common.history

import java.util.Locale

/**
 * Provides the user-facing summary stored for a completed Ping session.
 * The quality level is produced by the Ping statistics layer; this helper
 * only translates that existing semantic result for local history.
 */
object PingHistorySummary {
    fun fromQualityLevel(
        qualityLevel: String,
        fallback: String,
    ): String = when (qualityLevel.uppercase(Locale.ROOT)) {
        "EXCELLENT" -> "网络连接稳定，未检测到明显丢包。"
        "GOOD", "FAIR" -> "网络可达，但存在一定延迟或波动。"
        "POOR" -> "网络质量较差，检测到明显延迟或丢包。"
        "UNKNOWN" -> "本次未能获得有效响应，暂时无法评价网络质量。"
        else -> when (fallback) {
            "Ping completed" -> "Ping 检测完成"
            "Ping failed" -> "Ping 检测失败"
            "Excellent observed network quality." -> "网络连接稳定，未检测到明显丢包。"
            else -> fallback
        }
    }
}
