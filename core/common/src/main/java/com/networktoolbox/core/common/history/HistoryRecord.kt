package com.networktoolbox.core.common.history

data class HistoryRecord(
    val id: Long = 0L,
    val timestamp: Long,
    val type: HistoryType,
    val title: String,
    val summary: String,
    val detailJson: String,
)

data class HistoryFinding(
    val level: String,
    val title: String,
    val description: String,
)
