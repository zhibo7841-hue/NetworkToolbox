package com.networktoolbox.feature.dashboard

/** Pure display mapping for the Home recent-diagnosis preview. */
internal object HomePresentation {
    fun recentDiagnosticBody(preview: RecentHistoryPreview?): String =
        preview?.title ?: "暂无诊断记录"

    fun recentDiagnosticSummary(preview: RecentHistoryPreview?): String? =
        preview?.summary?.takeIf(String::isNotBlank)
}
