package com.networktoolbox.core.network.data

import com.networktoolbox.core.common.wol.MagicPacketBuilder
import com.networktoolbox.core.network.wol.LanNetworkBinding
import com.networktoolbox.core.network.wol.WakeOnLanSender
import com.networktoolbox.core.network.wol.WakeOnLanTransportResult
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidWakeOnLanSender : WakeOnLanSender {
    override suspend fun send(
        binding: LanNetworkBinding,
        destinationAddress: String,
        udpPort: Int,
        payload: ByteArray,
    ): WakeOnLanTransportResult = withContext(Dispatchers.IO) {
        if (payload.size != MagicPacketBuilder.PACKET_SIZE || udpPort !in 1..65535) {
            return@withContext WakeOnLanTransportResult.Failed
        }
        val address = runCatching { InetAddress.getByName(destinationAddress) }
            .getOrNull() as? Inet4Address
            ?: return@withContext WakeOnLanTransportResult.Failed

        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                binding.bindDatagramSocket(socket)
                socket.send(DatagramPacket(payload, payload.size, address, udpPort))
            }
            WakeOnLanTransportResult.Sent
        } catch (error: CancellationException) {
            throw error
        } catch (_: SecurityException) {
            WakeOnLanTransportResult.PermissionDenied
        } catch (_: IOException) {
            WakeOnLanTransportResult.Failed
        } catch (_: RuntimeException) {
            WakeOnLanTransportResult.Failed
        }
    }
}
