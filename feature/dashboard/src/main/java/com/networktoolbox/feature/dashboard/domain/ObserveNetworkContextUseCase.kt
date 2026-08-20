package com.networktoolbox.feature.dashboard.domain

import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.network.repository.NetworkRepository
import kotlinx.coroutines.flow.Flow

class ObserveNetworkContextUseCase(
    private val repository: NetworkRepository,
) {
    operator fun invoke(): Flow<NetworkContext> = repository.observeNetworkContext()
}
