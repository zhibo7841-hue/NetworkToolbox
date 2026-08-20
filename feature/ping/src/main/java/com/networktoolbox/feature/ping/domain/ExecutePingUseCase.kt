package com.networktoolbox.feature.ping.domain

import com.networktoolbox.core.network.ping.PingEngine
import com.networktoolbox.core.network.ping.PingResult
import javax.inject.Inject

class ExecutePingUseCase @Inject constructor(
    private val pingEngine: PingEngine,
) {
    suspend operator fun invoke(
        target: String,
        timeoutMs: Int = PingEngine.DEFAULT_TIMEOUT_MS,
    ): PingResult = pingEngine.ping(target, timeoutMs)
}
