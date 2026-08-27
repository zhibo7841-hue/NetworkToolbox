package com.networktoolbox.feature.lanscan.domain

import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.network.repository.NetworkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class LanScanReadiness(
    val networkContext: NetworkContext,
    val rangeResult: LanScanRangeResult,
)

fun interface ObserveLanScanReadiness {
    operator fun invoke(): Flow<LanScanReadiness>
}

class ObserveLanScanReadinessUseCase(
    private val networkRepository: NetworkRepository,
    private val rangeCalculator: LanScanRangeCalculator,
) : ObserveLanScanReadiness {
    override operator fun invoke(): Flow<LanScanReadiness> =
        networkRepository.observeNetworkContext().map { context ->
            LanScanReadiness(
                networkContext = context,
                rangeResult = rangeCalculator.calculate(context),
            )
        }
}
