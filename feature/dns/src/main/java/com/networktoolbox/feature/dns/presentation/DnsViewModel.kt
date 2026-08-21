package com.networktoolbox.feature.dns.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networktoolbox.core.network.dns.DnsMethod
import com.networktoolbox.core.network.dns.DnsResult
import com.networktoolbox.feature.dns.domain.LookupDnsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DnsUiState(
    val domainInput: String = "",
    val status: DnsStatus = DnsStatus.Idle,
)

sealed interface DnsStatus {
    data object Idle : DnsStatus

    data class Loading(val domain: String) : DnsStatus

    data class Success(val result: DnsResult) : DnsStatus

    data class Error(val result: DnsResult) : DnsStatus
}

@HiltViewModel
class DnsViewModel @Inject constructor(
    private val lookupDns: LookupDnsUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DnsUiState())
    val uiState: StateFlow<DnsUiState> = _uiState.asStateFlow()

    fun onDomainChanged(domain: String) {
        if (_uiState.value.status is DnsStatus.Loading) return

        _uiState.update {
            it.copy(
                domainInput = domain,
                status = DnsStatus.Idle,
            )
        }
    }

    fun lookup() {
        if (_uiState.value.status is DnsStatus.Loading) return

        val domain = _uiState.value.domainInput.trim()
        _uiState.update { it.copy(status = DnsStatus.Loading(domain)) }

        viewModelScope.launch {
            val result = runCatching { lookupDns(domain) }
                .getOrElse {
                    DnsResult(
                        domain = domain,
                        success = false,
                        records = emptyList(),
                        durationMs = null,
                        method = DnsMethod.UNAVAILABLE,
                        errorMessage = "DNS lookup unavailable.",
                    )
                }

            _uiState.update {
                it.copy(
                    status = if (result.success) {
                        DnsStatus.Success(result)
                    } else {
                        DnsStatus.Error(result)
                    },
                )
            }
        }
    }
}
