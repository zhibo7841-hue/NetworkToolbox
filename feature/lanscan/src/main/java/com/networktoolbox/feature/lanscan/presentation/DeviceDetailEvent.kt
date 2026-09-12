package com.networktoolbox.feature.lanscan.presentation

/**
 * One-shot feedback emitted by actions on the Device Detail screen.
 *
 * These events deliberately do not belong to [LanScannerUiState] or a saved
 * profile. A newly composed detail screen must not replay an old action
 * result.
 */
sealed interface DeviceDetailEvent {
    val message: String

    data object WakePacketSent : DeviceDetailEvent {
        override val message: String = "唤醒包已发送"
    }

    data class WakePacketFailed(
        override val message: String,
    ) : DeviceDetailEvent

    data object WakeOnLanConfigurationSaved : DeviceDetailEvent {
        override val message: String = "Wake-on-LAN 配置已保存"
    }

    data class WakeOnLanConfigurationSaveFailed(
        override val message: String,
    ) : DeviceDetailEvent
}
