package com.networktoolbox.feature.report.presentation

import com.networktoolbox.core.network.dns.DnsMethod
import com.networktoolbox.core.network.dns.DnsResult
import com.networktoolbox.core.network.ping.PingMethod
import com.networktoolbox.core.network.ping.PingResult
import com.networktoolbox.core.network.tcp.TcpProbeResult
import com.networktoolbox.feature.report.FakeDnsUseCase
import com.networktoolbox.feature.report.FakeNetworkRepository
import com.networktoolbox.feature.report.FakePingUseCase
import com.networktoolbox.feature.report.FakeTcpUseCase
import com.networktoolbox.feature.report.diagnostic.BasicDiagnosticAnalyzer
import com.networktoolbox.feature.report.diagnostic.DiagnosticAnalyzer
import com.networktoolbox.feature.report.domain.GenerateDiagnosticReportUseCase
import com.networktoolbox.feature.report.domain.ReportStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateIsIdle() {
        val viewModel = viewModelFor()

        assertEquals(ReportStatus.Idle, viewModel.uiState.value.status)
    }

    @Test
    fun runCheckShowsRunningThenSuccess() = runTest {
        val viewModel = viewModelFor()

        viewModel.runCheck()

        assertTrue(viewModel.uiState.value.status is ReportStatus.Running)
        assertEquals(
            ReportStep.NETWORK_INFORMATION,
            (viewModel.uiState.value.status as ReportStatus.Running).progress.activeStep,
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.status is ReportStatus.Success)
    }

    @Test
    fun pingFailureStillProducesSuccessReportWithFinding() = runTest {
        val viewModel = viewModelFor(
            ping = FakePingUseCase(
                PingResult(
                    target = "8.8.8.8",
                    success = false,
                    latencyMs = null,
                    method = PingMethod.SYSTEM_REACHABILITY,
                    errorMessage = "Timeout",
                ),
            ),
        )

        viewModel.runCheck()
        advanceUntilIdle()

        val status = viewModel.uiState.value.status
        assertTrue(status is ReportStatus.Success)
        assertTrue((status as ReportStatus.Success).report.findings.any { it.title == "目标不可达" })
    }

    @Test
    fun dnsFailureAndTcpFailureProduceMultipleFindings() = runTest {
        val viewModel = viewModelFor(
            dns = FakeDnsUseCase(
                DnsResult(
                    domain = "example.com",
                    success = false,
                    records = emptyList(),
                    durationMs = null,
                    method = DnsMethod.SYSTEM_RESOLVER,
                    errorMessage = "Resolver failure",
                ),
            ),
            tcp = FakeTcpUseCase(
                TcpProbeResult(
                    host = "example.com",
                    port = 443,
                    success = false,
                    latencyMs = null,
                    errorMessage = "Connection refused",
                ),
            ),
        )

        viewModel.runCheck()
        advanceUntilIdle()

        val status = viewModel.uiState.value.status
        assertTrue(status is ReportStatus.Success)
        val titles = (status as ReportStatus.Success).report.findings.map { it.title }
        assertTrue("DNS解析失败" in titles)
        assertTrue("目标端口拒绝连接" in titles)
    }

    @Test
    fun unexpectedAnalyzerFailureProducesErrorState() = runTest {
        val viewModel = viewModelFor(
            analyzer = object : DiagnosticAnalyzer {
                override fun analyze(
                    context: com.networktoolbox.core.network.model.NetworkContext?,
                    ping: PingResult?,
                    dns: DnsResult?,
                    tcp: TcpProbeResult?,
                ) : com.networktoolbox.feature.report.diagnostic.DiagnosticReport =
                    error("Analyzer unavailable")
            },
        )

        viewModel.runCheck()
        advanceUntilIdle()

        assertEquals(
            ReportStatus.Error("Analyzer unavailable"),
            viewModel.uiState.value.status,
        )
    }

    private fun viewModelFor(
        networkRepository: FakeNetworkRepository = FakeNetworkRepository(),
        ping: FakePingUseCase = FakePingUseCase(),
        dns: FakeDnsUseCase = FakeDnsUseCase(),
        tcp: FakeTcpUseCase = FakeTcpUseCase(),
        analyzer: DiagnosticAnalyzer = BasicDiagnosticAnalyzer(),
    ): ReportViewModel = ReportViewModel(
        GenerateDiagnosticReportUseCase(
            networkRepository = networkRepository,
            ping = ping,
            dns = dns,
            tcp = tcp,
            analyzer = analyzer,
        ),
    )
}
