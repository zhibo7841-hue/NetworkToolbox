package com.networktoolbox.feature.report.diagnostic

import com.networktoolbox.core.network.dns.DnsResult
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.network.ping.PingResult
import com.networktoolbox.core.network.tcp.TcpProbeResult

interface DiagnosticAnalyzer {
    fun analyze(
        context: NetworkContext?,
        ping: PingResult?,
        dns: DnsResult?,
        tcp: TcpProbeResult?,
    ): DiagnosticReport
}
