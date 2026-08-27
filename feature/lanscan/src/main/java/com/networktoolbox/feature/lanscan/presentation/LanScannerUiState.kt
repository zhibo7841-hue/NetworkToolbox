package com.networktoolbox.feature.lanscan.presentation

import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.feature.lanscan.domain.LanScanReadiness
import com.networktoolbox.feature.lanscan.domain.model.LanScanRange
import com.networktoolbox.feature.lanscan.domain.model.LanScanSession
import com.networktoolbox.feature.lanscan.domain.model.LanScanUpdate

sealed interface LanScannerUiState {
    data object Idle : LanScannerUiState

    data class Ready(
        val readiness: LanScanReadiness,
        val range: LanScanRange,
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
