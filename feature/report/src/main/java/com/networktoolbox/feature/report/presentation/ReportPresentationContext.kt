package com.networktoolbox.feature.report.presentation

/** Identifies whether the report screen is showing the live tool or a saved artifact. */
enum class ReportPresentationContext(val title: String) {
    LIVE_TOOL("网络诊断"),
    SAVED_REPORT("网络诊断报告"),
}
