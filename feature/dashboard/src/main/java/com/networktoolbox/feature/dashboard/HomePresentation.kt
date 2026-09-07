package com.networktoolbox.feature.dashboard

/** Pure display mapping for the Home recent-diagnosis preview. */
internal object HomePresentation {
    val sectionOrder: List<String> = listOf(
        "network",
        "diagnostic",
        "quick-tools",
        "recent-diagnosis",
    )

    fun recentDiagnosticBody(preview: RecentHistoryPreview?): String =
        preview?.title ?: "暂无诊断记录"

    fun recentDiagnosticSummary(preview: RecentHistoryPreview?): String? =
        preview?.summary?.takeIf(String::isNotBlank)

    fun networkDetailsContentDescription(expanded: Boolean): String =
        if (expanded) "收起网络详情" else "查看网络详情"
}
