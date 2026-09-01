package com.networktoolbox.feature.traceroute.domain

import com.networktoolbox.core.network.traceroute.TracerouteEngine
import com.networktoolbox.core.network.traceroute.TracerouteProgress
import com.networktoolbox.core.network.traceroute.TracerouteRequest
import com.networktoolbox.core.network.traceroute.TracerouteResult
import javax.inject.Inject

class RunTracerouteUseCase @Inject constructor(
    private val engine: TracerouteEngine,
) {
    suspend operator fun invoke(
        request: TracerouteRequest,
        onProgress: suspend (TracerouteProgress) -> Unit = {},
    ): TracerouteResult = engine.run(request, onProgress)
}
