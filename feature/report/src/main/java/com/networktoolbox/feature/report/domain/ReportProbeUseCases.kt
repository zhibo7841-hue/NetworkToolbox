package com.networktoolbox.feature.report.domain

import com.networktoolbox.core.network.dns.DnsResult
import com.networktoolbox.core.network.ping.PingResult
import com.networktoolbox.core.network.tcp.TcpProbeResult

fun interface PingUseCase {
    suspend operator fun invoke(target: String): PingResult
}

fun interface DnsUseCase {
    suspend operator fun invoke(domain: String): DnsResult
}

fun interface TcpUseCase {
    suspend operator fun invoke(host: String, port: String): TcpProbeResult
}
