package com.networktoolbox.feature.lanscan.domain

import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryRecorder
import com.networktoolbox.core.common.history.HistoryType
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.network.repository.NetworkRepository
import com.networktoolbox.feature.lanscan.domain.model.LanDeviceEvidence
import com.networktoolbox.feature.lanscan.domain.model.LanDiscoveryMethod
import com.networktoolbox.feature.lanscan.domain.model.LanHostProbeResult
import com.networktoolbox.feature.lanscan.domain.model.LanScanSession
import com.networktoolbox.feature.lanscan.domain.model.LanScanStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunLanScanUseCaseTest {
    @Test
    fun `completed scan creates exactly one LAN history record`() = runTest {
        val records = mutableListOf<HistoryRecord>()
        val useCase = RunLanScanUseCase(
            networkRepository = FakeNetworkRepository(context()),
            discoveryEngine = DefaultLanDiscoveryEngine(
                hostProbe = LanHostProbe { ipAddress, _ ->
                    LanHostProbeResult(
                        ipAddress,
                        listOf(LanDeviceEvidence(LanDiscoveryMethod.REACHABILITY, latencyMs = 4)),
                    )
                },
            ),
            historyRecorder = HistoryRecorder { records += it },
        )

        val result = useCase()

        assertEquals(LanScanStatus.COMPLETED, result.status)
        assertEquals(1, records.size)
        assertEquals(HistoryType.LAN_SCAN, records.single().type)
        assertTrue(records.single().detailJson.contains("\"schemaVersion\":1"))
        assertTrue(records.single().detailJson.contains("\"devices\""))
    }

    @Test
    fun `cancelled scan does not create history`() = runTest {
        val records = mutableListOf<HistoryRecord>()
        val useCase = RunLanScanUseCase(
            networkRepository = FakeNetworkRepository(context()),
            discoveryEngine = object : LanDiscoveryEngine {
                override suspend fun scan(
                    request: com.networktoolbox.feature.lanscan.domain.model.LanScanRequest,
                    currentNetworkContext: suspend () -> NetworkContext,
                    onUpdate: (com.networktoolbox.feature.lanscan.domain.model.LanScanUpdate) -> Unit,
                ): LanScanSession = LanScanSession(
                    status = LanScanStatus.CANCELLED,
                    initialNetworkContext = request.networkContext,
                    range = null,
                    scannedHosts = 0,
                    totalHosts = 0,
                    discoveredDevices = emptyList(),
                    startedAt = 0,
                    finishedAt = 1,
                )
            },
            historyRecorder = HistoryRecorder { records += it },
        )

        useCase()

        assertFalse(records.any { it.type == HistoryType.LAN_SCAN })
    }

    @Test
    fun `history serializer preserves bounded scan summary and local markers`() {
        val context = context()
        val range = (LanScanRangeCalculator().calculate(context) as LanScanRangeResult.Ready).range
        val session = LanScanSession(
            status = LanScanStatus.COMPLETED,
            initialNetworkContext = context,
            range = range,
            scannedHosts = 254,
            totalHosts = 254,
            discoveredDevices = listOf(
                com.networktoolbox.feature.lanscan.domain.model.LanDevice(
                    ipAddress = "192.168.1.1",
                    isLocalDevice = false,
                    isGateway = true,
                    discoveryMethods = listOf(LanDiscoveryMethod.GATEWAY_CONTEXT),
                    discoveryEvidence = listOf(
                        LanDeviceEvidence(LanDiscoveryMethod.GATEWAY_CONTEXT),
                    ),
                    lastSeen = 2,
                ),
            ),
            startedAt = 0,
            finishedAt = 50,
        )

        val record = LanScanHistorySerializer.toHistoryRecord(session)

        assertEquals(HistoryType.LAN_SCAN, record.type)
        assertTrue(record.summary.contains("192.168.1.0/24"))
        assertTrue(record.detailJson.contains("\"isGateway\":true"))
        assertTrue(record.detailJson.contains("\"rangeWasLimited\":false"))
    }

    private class FakeNetworkRepository(
        private val context: NetworkContext,
    ) : NetworkRepository {
        override fun observeNetworkContext(): Flow<NetworkContext> = flowOf(context)
    }

    private fun context() = NetworkContext(
        connectionType = ConnectionType.WIFI,
        ipv4Address = "192.168.1.100",
        ipv6Address = null,
        gateway = "192.168.1.1",
        dnsServers = emptyList(),
        vpnActive = false,
        wifiName = null,
        wifiSignalLevel = null,
        activeNetworkAvailable = true,
        validated = true,
        ipv4PrefixLength = 24,
    )
}
