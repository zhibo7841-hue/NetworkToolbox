package com.networktoolbox.core.network.repository

import com.networktoolbox.core.network.model.NetworkContext
import kotlinx.coroutines.flow.Flow

interface NetworkRepository {
    fun observeNetworkContext(): Flow<NetworkContext>
}
