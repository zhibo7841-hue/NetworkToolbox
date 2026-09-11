package com.networktoolbox.feature.lanscan.domain

import com.networktoolbox.core.common.favorites.SavedDeviceProfile
import com.networktoolbox.core.common.wol.IPv4BroadcastResolution
import com.networktoolbox.core.common.wol.IPv4BroadcastResolver
import com.networktoolbox.core.common.wol.MagicPacketBuilder
import com.networktoolbox.core.common.wol.WakeOnLanFailureReason
import com.networktoolbox.core.common.wol.WakeOnLanResult
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.wol.LanNetworkBindingProvider
import com.networktoolbox.core.network.wol.NoOpLanNetworkBindingProvider
import com.networktoolbox.core.network.wol.WakeOnLanSender
import com.networktoolbox.core.network.wol.WakeOnLanTransportResult
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

fun interface SendWakeOnLan {
    suspend operator fun invoke(profile: SavedDeviceProfile): WakeOnLanResult
}

object NoOpSendWakeOnLan : SendWakeOnLan {
    override suspend fun invoke(profile: SavedDeviceProfile): WakeOnLanResult =
        WakeOnLanResult.Failed(WakeOnLanFailureReason.NO_ACTIVE_LAN)
}

/**
 * Validates the saved profile against a fresh physical LAN context and sends
 * one directed-broadcast magic packet. It never probes the target device.
 */
class SendWakeOnLanUseCase @Inject constructor(
    private val bindingProvider: LanNetworkBindingProvider = NoOpLanNetworkBindingProvider,
    private val sender: WakeOnLanSender,
) : SendWakeOnLan {
    override suspend fun invoke(profile: SavedDeviceProfile): WakeOnLanResult {
        val config = profile.wolConfig
            ?: return WakeOnLanResult.Failed(WakeOnLanFailureReason.NOT_CONFIGURED)
        val binding = bindingProvider.current()
            ?: return WakeOnLanResult.Failed(WakeOnLanFailureReason.NO_ACTIVE_LAN)
        val context = binding.networkContext

        if (context.activeNetworkAvailable == false) {
            return WakeOnLanResult.Failed(WakeOnLanFailureReason.NO_ACTIVE_LAN)
        }
        if (context.connectionType != ConnectionType.WIFI &&
            context.connectionType != ConnectionType.ETHERNET
        ) {
            return WakeOnLanResult.Failed(WakeOnLanFailureReason.UNSUPPORTED_NETWORK)
        }
        if (context.vpnActive == true) {
            return WakeOnLanResult.Failed(WakeOnLanFailureReason.UNSUPPORTED_NETWORK)
        }
        if (context.ipv4Address.isNullOrBlank()) {
            return WakeOnLanResult.Failed(WakeOnLanFailureReason.NO_IPV4)
        }
        val currentScope = LanNetworkScope.from(context)
        if (currentScope == null || currentScope != profile.networkScope) {
            return WakeOnLanResult.Failed(WakeOnLanFailureReason.NETWORK_SCOPE_MISMATCH)
        }

        val broadcast = IPv4BroadcastResolver.resolve(
            ipv4Address = context.ipv4Address,
            prefixLength = context.ipv4PrefixLength,
        )
        val destination = (broadcast as? IPv4BroadcastResolution.Available)?.address
            ?: return WakeOnLanResult.Failed(WakeOnLanFailureReason.BROADCAST_UNAVAILABLE)
        val payload = MagicPacketBuilder.build(config.macAddress)

        val transportResult = try {
            sender.send(
                binding = binding,
                destinationAddress = destination,
                udpPort = config.udpPort,
                payload = payload,
            )
        } catch (error: CancellationException) {
            throw error
        }
        return when (transportResult) {
            WakeOnLanTransportResult.Sent -> WakeOnLanResult.Sent(destination, config.udpPort)
            WakeOnLanTransportResult.PermissionDenied ->
                WakeOnLanResult.Failed(WakeOnLanFailureReason.PERMISSION_DENIED)

            WakeOnLanTransportResult.Failed ->
                WakeOnLanResult.Failed(WakeOnLanFailureReason.SEND_FAILED)
        }
    }
}
