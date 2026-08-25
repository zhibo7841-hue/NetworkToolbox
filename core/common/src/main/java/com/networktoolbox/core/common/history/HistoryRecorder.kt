package com.networktoolbox.core.common.history

fun interface HistoryRecorder {
    suspend fun record(record: HistoryRecord)
}
