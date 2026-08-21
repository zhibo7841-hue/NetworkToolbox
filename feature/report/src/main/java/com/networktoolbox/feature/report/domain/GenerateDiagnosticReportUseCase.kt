package com.networktoolbox.feature.report.domain

import com.networktoolbox.core.network.dns.DnsMethod
import com.networktoolbox.core.network.dns.DnsResult
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.network.ping.PingMethod
import com.networktoolbox.core.network.ping.PingResult
import com.networktoolbox.core.network.repository.NetworkRepository
import com.networktoolbox.core.network.tcp.TcpProbeResult
import com.networktoolbox.feature.report.diagnostic.DiagnosticAnalyzer
import com.networktoolbox.feature.report.diagnostic.DiagnosticReport
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class GenerateDiagnosticReportUseCase @Inject constructor(
    private val networkRepository: NetworkRepository,
    private val ping: PingUseCase,
    private val dns: DnsUseCase,
    private val tcp: TcpUseCase,
    private val analyzer: DiagnosticAnalyzer,
) {
    suspend operator fun invoke(
        onStepChanged: (ReportStep) -> Unit = {},
    ): DiagnosticReport {
        onStepChanged(ReportStep.NETWORK_INFORMATION)
        val context = readNetworkContext()

        onStepChanged(ReportStep.PING)
        val pingResult = runProbe(
            fallback = PingResult(
                target = DEFAULT_PING_TARGET,
                success = false,
                latencyMs = null,
                method = PingMethod.UNAVAILABLE,
                errorMessage = "Ping unavailable.",
            ),
        ) {
            ping(DEFAULT_PING_TARGET)
        }

        onStepChanged(ReportStep.DNS)
        val dnsResult = runProbe(
            fallback = DnsResult(
                domain = DEFAULT_DNS_DOMAIN,
                success = false,
                records = emptyList(),
                durationMs = null,
                method = DnsMethod.UNAVAILABLE,
                errorMessage = "DNS lookup unavailable.",
            ),
        ) {
            dns(DEFAULT_DNS_DOMAIN)
        }

        onStepChanged(ReportStep.TCP)
        val tcpResult = runProbe(
            fallback = TcpProbeResult(
                host = DEFAULT_TCP_HOST,
                port = DEFAULT_TCP_PORT.toInt(),
                success = false,
                latencyMs = null,
                errorMessage = "Unknown error",
            ),
        ) {
            tcp(DEFAULT_TCP_HOST, DEFAULT_TCP_PORT)
        }

        return analyzer.analyze(
            context = context,
            ping = pingResult,
            dns = dnsResult,
            tcp = tcpResult,
        )
    }

    private suspend fun readNetworkContext(): NetworkContext = try {
        networkRepository.observeNetworkContext().first()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        NetworkContext.unknown()
    }

    private suspend fun <T> runProbe(
        fallback: T,
        block: suspend () -> T,
    ): T = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        fallback
    }

    companion object {
        const val DEFAULT_PING_TARGET = "8.8.8.8"
        const val DEFAULT_DNS_DOMAIN = "example.com"
        const val DEFAULT_TCP_HOST = "example.com"
        const val DEFAULT_TCP_PORT = "443"
    }
}
