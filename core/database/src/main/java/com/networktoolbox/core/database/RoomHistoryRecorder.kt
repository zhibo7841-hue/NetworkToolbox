package com.networktoolbox.core.database

import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryRecorder
import com.networktoolbox.core.common.history.HistoryRepository
import java.util.concurrent.CancellationException

/** Persists all network test history through the single local HistoryRepository. */
class RoomHistoryRecorder(
    private val historyRepository: HistoryRepository,
) : HistoryRecorder {
    override suspend fun record(record: HistoryRecord) {
        try {
            historyRepository.save(record)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // History is supplementary to the probe result; a local write failure
            // must not turn a completed network check into a failed check.
        }
    }
}
