package com.networktoolbox.feature.report

import com.networktoolbox.core.network.dns.DnsMethod
import com.networktoolbox.core.network.dns.DnsResult
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.network.ping.PingMethod
import com.networktoolbox.core.network.ping.PingResult
import com.networktoolbox.core.network.repository.NetworkRepository
import com.networktoolbox.core.network.tcp.TcpProbeResult
import com.networktoolbox.feature.report.domain.DnsUseCase
import com.networktoolbox.feature.report.domain.PingUseCase
import com.networktoolbox.feature.report.domain.TcpUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeNetworkRepository(
    private val context: NetworkContext = testNetworkContext(),
) : NetworkRepository {
    var callCount: Int = 0
        private set

    override fun observeNetworkContext(): Flow<NetworkContext> {
        callCount += 1
        return flowOf(context)
    }
}

class FakePingUseCase(
    var response: PingResult = successfulPingResult(),
) : PingUseCase {
    var callCount: Int = 0
        private set
    var receivedTarget: String? = null
        private set

    override suspend fun invoke(target: String): PingResult {
        callCount += 1
        receivedTarget = target
        return response
    }
}

class FakeDnsUseCase(
    var response: DnsResult = successfulDnsResult(),
) : DnsUseCase {
    var callCount: Int = 0
        private set
    var receivedDomain: String? = null
        private set

    override suspend fun invoke(domain: String): DnsResult {
        callCount += 1
        receivedDomain = domain
        return response
    }
}

class FakeTcpUseCase(
    var response: TcpProbeResult = successfulTcpResult(),
) : TcpUseCase {
    var callCount: Int = 0
        private set
    var receivedHost: String? = null
        private set
    var receivedPort: String? = null
        private set

    override suspend fun invoke(host: String, port: String): TcpProbeResult {
        callCount += 1
        receivedHost = host
        receivedPort = port
        return response
    }
}

fun testNetworkContext(): NetworkContext = NetworkContext(
    connectionType = ConnectionType.WIFI,
    ipv4Address = "192.0.2.20",
    ipv6Address = null,
    gateway = "192.0.2.1",
    dnsServers = listOf("192.0.2.53"),
    vpnActive = false,
    wifiName = "TestWiFi",
    wifiSignalLevel = 3,
)

fun successfulPingResult(): PingResult = PingResult(
    target = "8.8.8.8",
    success = true,
    latencyMs = 20,
    method = PingMethod.SYSTEM_REACHABILITY,
    errorMessage = null,
)

fun successfulDnsResult(): DnsResult = DnsResult(
    domain = "example.com",
    success = true,
    records = emptyList(),
    durationMs = 12,
    method = DnsMethod.SYSTEM_RESOLVER,
    errorMessage = null,
)

fun successfulTcpResult(): TcpProbeResult = TcpProbeResult(
    host = "example.com",
    port = 443,
    success = true,
    latencyMs = 30,
    errorMessage = null,
)
