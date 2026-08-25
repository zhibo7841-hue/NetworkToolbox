package com.networktoolbox.feature.ping.domain

import com.networktoolbox.core.common.history.HistoryRecordFactory
import com.networktoolbox.core.common.history.HistoryRecorder
import com.networktoolbox.core.network.ping.PingEngine
import com.networktoolbox.core.network.ping.PingResult
import javax.inject.Inject

class ExecutePingUseCase @Inject constructor(
    private val pingEngine: PingEngine,
    private val historyRecorder: HistoryRecorder,
) {
    suspend operator fun invoke(
        target: String,
        timeoutMs: Int = PingEngine.DEFAULT_TIMEOUT_MS,
        persistHistory: Boolean = true,
    ): PingResult = pingEngine.ping(target, timeoutMs)
        .also { result ->
            if (persistHistory) {
                historyRecorder.record(
                    HistoryRecordFactory.ping(
                        timestamp = System.currentTimeMillis(),
                        target = result.target,
                        success = result.success,
                        latencyMs = result.latencyMs,
                        method = result.method.name,
                        errorMessage = result.errorMessage,
                    ),
                )
            }
        }
}
