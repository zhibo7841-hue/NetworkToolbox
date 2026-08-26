package com.networktoolbox.feature.dns.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networktoolbox.core.network.dns.DnsLookupResult
import com.networktoolbox.core.network.dns.DnsLookupStatus
import com.networktoolbox.core.network.dns.DnsQueryMethod
import com.networktoolbox.core.network.dns.DnsRecordType
import com.networktoolbox.feature.dns.domain.IpAddressClassification
import com.networktoolbox.feature.dns.domain.IpAddressClassifier
import com.networktoolbox.feature.dns.domain.LookupDnsV2UseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DnsUiState(
    val domainInput: String = "",
    val selectedRecordTypes: Set<DnsRecordType> = LookupDnsV2UseCase.DEFAULT_RECORD_TYPES,
    val advancedSettingsExpanded: Boolean = false,
    val status: DnsStatus = DnsStatus.Idle,
)

sealed interface DnsStatus {
    data object Idle : DnsStatus

    data class Loading(val domain: String) : DnsStatus

    data class Success(
        val result: DnsLookupResult,
        val addressClassifications: List<IpAddressClassification> = emptyList(),
    ) : DnsStatus

    data class Error(
        val result: DnsLookupResult,
        val addressClassifications: List<IpAddressClassification> = emptyList(),
    ) : DnsStatus
}

@HiltViewModel
class DnsViewModel @Inject constructor(
    private val lookupDns: LookupDnsV2UseCase,
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

    fun toggleAdvancedSettings() {
        if (_uiState.value.status is DnsStatus.Loading) return
        _uiState.update { it.copy(advancedSettingsExpanded = !it.advancedSettingsExpanded) }
    }

    fun toggleRecordType(type: DnsRecordType) {
        if (_uiState.value.status is DnsStatus.Loading) return

        _uiState.update { state ->
            val selected = state.selectedRecordTypes.toMutableSet()
            if (!selected.add(type) && selected.size > 1) {
                selected.remove(type)
            }
            state.copy(selectedRecordTypes = selected, status = DnsStatus.Idle)
        }
    }

    fun lookup() {
        if (_uiState.value.status is DnsStatus.Loading) return

        val domain = _uiState.value.domainInput.trim()
        val recordTypes = _uiState.value.selectedRecordTypes
        _uiState.update { it.copy(status = DnsStatus.Loading(domain)) }

        viewModelScope.launch {
            val result = try {
                lookupDns(domain, recordTypes)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                unavailableResult(domain, recordTypes, error.message)
            }

            _uiState.update {
                it.copy(
                    status = if (result.status.isCompleted()) {
                        DnsStatus.Success(
                            result = result,
                            addressClassifications = result.addressClassifications(),
                        )
                    } else {
                        DnsStatus.Error(
                            result = result,
                            addressClassifications = result.addressClassifications(),
                        )
                    },
                )
            }
        }
    }

    private fun unavailableResult(
        domain: String,
        recordTypes: Set<DnsRecordType>,
        cause: String?,
    ): DnsLookupResult {
        val now = System.currentTimeMillis()
        return DnsLookupResult(
            queryName = domain,
            requestedTypes = recordTypes,
            records = emptyList(),
            server = null,
            method = DnsQueryMethod.UNAVAILABLE,
            status = DnsLookupStatus.NETWORK_ERROR,
            durationMs = null,
            startTime = now,
            endTime = now,
            errorMessage = cause?.takeIf { it.isNotBlank() } ?: "DNS 查询失败。",
        )
    }
}

private fun DnsLookupStatus.isCompleted(): Boolean = when (this) {
    DnsLookupStatus.SUCCESS,
    DnsLookupStatus.PARTIAL,
    DnsLookupStatus.NO_RECORDS,
    -> true

    else -> false
}

private fun DnsLookupResult.addressClassifications(): List<IpAddressClassification> =
    IpAddressClassifier.classifyAll(
        records
            .filter { record -> record.type == DnsRecordType.A || record.type == DnsRecordType.AAAA }
            .map { record -> record.value },
    )
