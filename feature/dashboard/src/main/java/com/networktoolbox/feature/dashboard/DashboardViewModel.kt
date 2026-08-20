package com.networktoolbox.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.feature.dashboard.domain.ObserveNetworkContextUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class DashboardUiState(
    val networkContext: NetworkContext = NetworkContext.unknown(),
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    observeNetworkContext: ObserveNetworkContextUseCase,
) : ViewModel() {
    val uiState: StateFlow<DashboardUiState> = observeNetworkContext()
        .map(::DashboardUiState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = DashboardUiState(),
        )
}
