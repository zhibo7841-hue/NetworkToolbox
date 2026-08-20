package com.networktoolbox.feature.subnet.presentation

import androidx.lifecycle.ViewModel
import com.networktoolbox.core.common.ipv4.SubnetResult
import com.networktoolbox.feature.subnet.domain.CalculateSubnetUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SubnetUiState(
    val input: String = "",
    val result: SubnetResult? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class SubnetViewModel @Inject constructor(
    private val calculateSubnet: CalculateSubnetUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SubnetUiState())
    val uiState: StateFlow<SubnetUiState> = _uiState.asStateFlow()

    fun onInputChanged(input: String) {
        _uiState.update {
            it.copy(
                input = input,
                result = null,
                errorMessage = null,
            )
        }
    }

    fun calculate() {
        val input = _uiState.value.input
        calculateSubnet(input)
            .onSuccess { result ->
                _uiState.update {
                    it.copy(result = result, errorMessage = null)
                }
            }
            .onFailure {
                _uiState.update {
                    it.copy(result = null, errorMessage = "输入无效。")
                }
            }
    }
}
