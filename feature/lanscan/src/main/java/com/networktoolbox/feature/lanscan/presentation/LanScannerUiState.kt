package com.networktoolbox.feature.lanscan.presentation

import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.feature.lanscan.domain.LanCustomRangeResult
import com.networktoolbox.feature.lanscan.domain.LanScanReadiness
import com.networktoolbox.feature.lanscan.domain.model.LanScanRange
import com.networktoolbox.feature.lanscan.domain.model.LanScanSession
import com.networktoolbox.feature.lanscan.domain.model.LanScanUpdate

enum class LanScanRangeMode {
    CURRENT_NETWORK,
    CUSTOM,
}

sealed interface LanScannerUiState {
    data object Idle : LanScannerUiState

    data class Ready(
        val readiness: LanScanReadiness,
        val range: LanScanRange,
        val rangeMode: LanScanRangeMode = LanScanRangeMode.CURRENT_NETWORK,
        val customStartAddress: String = "",
        val customEndAddress: String = "",
        val customRangeResult: LanCustomRangeResult = LanCustomRangeResult.Incomplete,
    ) : LanScannerUiState

    data class Scanning(
        val networkContext: NetworkContext,
        val range: LanScanRange,
        val startedAt: Long,
        val update: LanScanUpdate,
    ) : LanScannerUiState

    data class Completed(
        val session: LanScanSession,
    ) : LanScannerUiState

    data class Cancelled(
        val session: LanScanSession,
    ) : LanScannerUiState

    data class NetworkChanged(
        val session: LanScanSession,
    ) : LanScannerUiState

    data class UnsupportedNetwork(
        val readiness: LanScanReadiness,
        val message: String,
    ) : LanScannerUiState

    data class VpnBlocked(
        val readiness: LanScanReadiness,
        val message: String,
    ) : LanScannerUiState

    data class Error(
        val message: String,
        val readiness: LanScanReadiness? = null,
    ) : LanScannerUiState
}
