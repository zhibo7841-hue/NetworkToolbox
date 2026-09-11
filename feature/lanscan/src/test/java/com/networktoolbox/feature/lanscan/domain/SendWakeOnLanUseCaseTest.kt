package com.networktoolbox.feature.lanscan.domain

import com.networktoolbox.core.common.favorites.FavoriteIdentityType
import com.networktoolbox.core.common.favorites.SavedDeviceProfile
import com.networktoolbox.core.common.wol.MacAddress
import com.networktoolbox.core.common.wol.WakeOnLanConfig
import com.networktoolbox.core.common.wol.WakeOnLanFailureReason
import com.networktoolbox.core.common.wol.WakeOnLanResult
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.network.wol.LanNetworkBinding
import com.networktoolbox.core.network.wol.LanNetworkBindingProvider
import com.networktoolbox.core.network.wol.WakeOnLanSender
import com.networktoolbox.core.network.wol.WakeOnLanTransportResult
import java.net.DatagramSocket
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SendWakeOnLanUseCaseTest {
    @Test
    fun `valid profile sends directed broadcast on current binding`() = runBlocking {
        val binding = FakeBinding(networkContext())
        val sender = RecordingSender()
        val useCase = SendWakeOnLanUseCase(
            bindingProvider = LanNetworkBindingProvider { binding },
            sender = sender,
        )

        val result = useCase(profile(networkScope = LanNetworkScope.from(binding.networkContext)!!))

        assertEquals(WakeOnLanResult.Sent("192.168.1.255", 9), result)
        assertEquals("192.168.1.255", sender.destination)
        assertEquals(9, sender.port)
        assertEquals(102, sender.payload?.size)
        assertTrue(sender.boundTo === binding)
        assertArrayEquals(
            com.networktoolbox.core.common.wol.MagicPacketBuilder.build(
                MacAddress.parse("00:11:22:33:44:55")!!,
            ),
            sender.payload,
        )
    }

    @Test
    fun `missing config is rejected before binding or sender`() = runBlocking {
        val sender = RecordingSender()
        val useCase = SendWakeOnLanUseCase(
            bindingProvider = LanNetworkBindingProvider { error("must not bind") },
            sender = sender,
        )

        assertEquals(
            WakeOnLanResult.Failed(WakeOnLanFailureReason.NOT_CONFIGURED),
            useCase(profile(networkScope = "scope" ).copy(wolConfig = null)),
        )
        assertEquals(null, sender.payload)
    }

    @Test
    fun `no physical lan is not reported as a send failure`() = runBlocking {
        val useCase = SendWakeOnLanUseCase(
            bindingProvider = LanNetworkBindingProvider { null },
            sender = RecordingSender(),
        )

        assertEquals(
            WakeOnLanResult.Failed(WakeOnLanFailureReason.NO_ACTIVE_LAN),
            useCase(profile(networkScope = "scope")),
        )
    }

    @Test
    fun `network change is checked fresh for every send`() = runBlocking {
        val first = FakeBinding(networkContext(address = "192.168.1.20"))
        val second = FakeBinding(networkContext(address = "192.168.2.20", gateway = "192.168.2.1"))
        val bindings = ArrayDeque(listOf(first, second))
        val useCase = SendWakeOnLanUseCase(
            bindingProvider = LanNetworkBindingProvider { bindings.removeFirst() },
            sender = RecordingSender(),
        )
        val profile = profile(networkScope = LanNetworkScope.from(first.networkContext)!!)

        assertTrue(useCase(profile) is WakeOnLanResult.Sent)
        assertEquals(
            WakeOnLanResult.Failed(WakeOnLanFailureReason.NETWORK_SCOPE_MISMATCH),
            useCase(profile),
        )
    }

    @Test
    fun `cellular binding is rejected without broadcasting`() = runBlocking {
        val binding = FakeBinding(networkContext().copy(connectionType = ConnectionType.CELLULAR))
        val sender = RecordingSender()
        val useCase = SendWakeOnLanUseCase(
            bindingProvider = LanNetworkBindingProvider { binding },
            sender = sender,
        )

        assertEquals(
            WakeOnLanResult.Failed(WakeOnLanFailureReason.UNSUPPORTED_NETWORK),
            useCase(profile(networkScope = "scope")),
        )
        assertEquals(null, sender.payload)
    }

    @Test
    fun `ipv6 only binding is rejected without an ipv4 broadcast`() = runBlocking {
        val binding = FakeBinding(
            networkContext().copy(
                ipv4Address = null,
                ipv4PrefixLength = null,
                ipv6Address = "2001:db8::20",
            ),
        )
        val sender = RecordingSender()
        val useCase = SendWakeOnLanUseCase(
            bindingProvider = LanNetworkBindingProvider { binding },
            sender = sender,
        )

        assertEquals(
            WakeOnLanResult.Failed(WakeOnLanFailureReason.NO_IPV4),
            useCase(profile(networkScope = "scope")),
        )
        assertEquals(null, sender.payload)
    }

    private fun profile(networkScope: String) = SavedDeviceProfile(
        identityType = FavoriteIdentityType.NETWORK_IP,
        identityValue = "192.168.1.50",
        networkScope = networkScope,
        lastKnownIpv4 = "192.168.1.50",
        lastKnownDisplayName = null,
        lastKnownHostname = null,
        lastKnownMdnsName = null,
        lastKnownUpnpName = null,
        macAddress = null,
        vendor = null,
        model = null,
        createdAt = 1L,
        lastSeenAt = 1L,
        wolConfig = WakeOnLanConfig(MacAddress.parse("00:11:22:33:44:55")!!),
    )

    private fun networkContext(
        address: String = "192.168.1.20",
        gateway: String = "192.168.1.1",
    ) = NetworkContext(
        connectionType = ConnectionType.WIFI,
        ipv4Address = address,
        ipv6Address = null,
        gateway = gateway,
        dnsServers = listOf(gateway),
        vpnActive = false,
        wifiName = "Test",
        wifiSignalLevel = 3,
        activeNetworkAvailable = true,
        ipv4PrefixLength = 24,
        interfaceName = "wlan0",
    )
}

private class RecordingSender : WakeOnLanSender {
    var destination: String? = null
    var port: Int? = null
    var payload: ByteArray? = null
    var boundTo: LanNetworkBinding? = null

    override suspend fun send(
        binding: LanNetworkBinding,
        destinationAddress: String,
        udpPort: Int,
        payload: ByteArray,
    ): WakeOnLanTransportResult {
        boundTo = binding
        destination = destinationAddress
        port = udpPort
        this.payload = payload
        return WakeOnLanTransportResult.Sent
    }
}

private class FakeBinding(
    override val networkContext: NetworkContext,
) : LanNetworkBinding {
    override fun bindDatagramSocket(socket: DatagramSocket) = Unit
}
