package com.networktoolbox.feature.report.presentation

import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryRecorder
import com.networktoolbox.core.network.dns.DnsLookupResult
import com.networktoolbox.core.network.dns.DnsLookupStatus
import com.networktoolbox.core.network.dns.DnsQueryMethod
import com.networktoolbox.core.network.dns.DnsRecordType
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.network.ping.PingMethod
import com.networktoolbox.core.network.ping.PingMode
import com.networktoolbox.core.network.ping.PingProtocol
import com.networktoolbox.core.network.ping.PingSessionResult
import com.networktoolbox.core.network.tcp.TcpProbeResult
import com.networktoolbox.feature.report.diagnostic.v2.DefaultDiagnosticAnalyzerV2
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticCheck
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticCheckStatus
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticPipeline
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticPipelineResult
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticProbeTargets
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticSeverity
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticStage
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticStageProgress
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticStageState
import com.networktoolbox.feature.report.domain.RunDiagnosticV2UseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
        assertEquals(ReportStatus.Idle, viewModelFor().uiState.value.status)
    }

    @Test
    fun progressMapsPipelineStageStatesForTheRunningUi() {
        val running = ReportProgress()
            .apply(DiagnosticStageProgress(DiagnosticStage.DNS, DiagnosticStageState.RUNNING))
        val progress = running
            .apply(DiagnosticStageProgress(DiagnosticStage.DNS, DiagnosticStageState.COMPLETED))
            .apply(DiagnosticStageProgress(DiagnosticStage.GATEWAY, DiagnosticStageState.SKIPPED))

        assertEquals(DiagnosticStage.DNS, running.activeStage)
        assertEquals(ReportStageStatus.COMPLETED, progress.stageStates[DiagnosticStage.DNS])
        assertEquals(ReportStageStatus.SKIPPED, progress.stageStates[DiagnosticStage.GATEWAY])
        assertEquals(null, progress.activeStage)
    }

    @Test
    fun runningStateReflectsRealPipelineStagesAndEndsInSuccess() = runTest {
        val events = mutableListOf<DiagnosticStageProgress>()
        val viewModel = viewModelFor(
            pipeline = SuccessfulPipeline(onProgress = { events += it }),
        )

        viewModel.runCheck()

        assertTrue(viewModel.uiState.value.status is ReportStatus.Running)
        advanceUntilIdle()

        val status = viewModel.uiState.value.status
        assertTrue(status is ReportStatus.Success)
        assertTrue(events.any {
            it.stage == DiagnosticStage.DNS && it.state == DiagnosticStageState.RUNNING
        })
        assertEquals(
            DiagnosticStageState.COMPLETED,
            events.last { it.stage == DiagnosticStage.DNS }.state,
        )
    }

    @Test
    fun cancellationIsRestartableAndDoesNotSaveHistory() = runTest {
        val records = mutableListOf<HistoryRecord>()
        val viewModel = viewModelFor(
            pipeline = object : DiagnosticPipeline {
                override suspend fun run(
                    onStageChanged: (DiagnosticStageProgress) -> Unit,
                ): DiagnosticPipelineResult {
                    onStageChanged(
                        DiagnosticStageProgress(
                            DiagnosticStage.NETWORK_CONTEXT,
                            DiagnosticStageState.RUNNING,
                        ),
                    )
                    delay(Long.MAX_VALUE)
                    error("unreachable")
                }
            },
            records = records,
        )

        viewModel.runCheck()
        viewModel.stopCheck()
        advanceUntilIdle()

        assertEquals(ReportStatus.Cancelled, viewModel.uiState.value.status)
        assertTrue(records.isEmpty())

        viewModel.runCheck()
        assertTrue(viewModel.uiState.value.status is ReportStatus.Running)
    }

    @Test
    fun pipelineFailureBecomesUserFacingError() = runTest {
        val viewModel = viewModelFor(
            pipeline = object : DiagnosticPipeline {
                override suspend fun run(
                    onStageChanged: (DiagnosticStageProgress) -> Unit,
                ): DiagnosticPipelineResult = error("network adapter unavailable")
            },
        )

        viewModel.runCheck()
        advanceUntilIdle()

        assertEquals(
            ReportStatus.Error("network adapter unavailable"),
            viewModel.uiState.value.status,
        )
    }

    private fun viewModelFor(
        pipeline: DiagnosticPipeline = SuccessfulPipeline(),
        records: MutableList<HistoryRecord> = mutableListOf(),
    ): ReportViewModel {
        val useCase = RunDiagnosticV2UseCase(
            pipeline = pipeline,
            analyzer = DefaultDiagnosticAnalyzerV2(),
            historyRecorder = HistoryRecorder { records += it },
        )
        return ReportViewModel(useCase)
    }
}

private class SuccessfulPipeline(
    private val onProgress: (DiagnosticStageProgress) -> Unit = {},
) : DiagnosticPipeline {
    override suspend fun run(
        onStageChanged: (DiagnosticStageProgress) -> Unit,
    ): DiagnosticPipelineResult {
        listOf(
            DiagnosticStage.NETWORK_CONTEXT,
            DiagnosticStage.GATEWAY,
            DiagnosticStage.PUBLIC_CONNECTIVITY,
            DiagnosticStage.DNS,
            DiagnosticStage.DOMAIN_CONNECTIVITY,
        ).forEach { stage ->
            onStageChanged(DiagnosticStageProgress(stage, DiagnosticStageState.RUNNING))
            onProgress(DiagnosticStageProgress(stage, DiagnosticStageState.RUNNING))
            onStageChanged(DiagnosticStageProgress(stage, DiagnosticStageState.COMPLETED))
            onProgress(DiagnosticStageProgress(stage, DiagnosticStageState.COMPLETED))
        }
        return DiagnosticPipelineResult(
            startedAt = 1_000L,
            endedAt = 1_100L,
            networkContext = NetworkContext(
                connectionType = ConnectionType.WIFI,
                ipv4Address = "192.0.2.20",
                ipv6Address = null,
                gateway = "192.0.2.1",
                dnsServers = listOf("192.0.2.1"),
                vpnActive = false,
                wifiName = "Test Wi-Fi",
                wifiSignalLevel = 3,
                activeNetworkAvailable = true,
                validated = true,
            ),
            gatewayResult = null,
            publicConnectivity = null,
            dnsResult = DnsLookupResult(
                queryName = "example.com",
                requestedTypes = setOf(DnsRecordType.A, DnsRecordType.AAAA),
                records = emptyList(),
                server = null,
                method = DnsQueryMethod.ANDROID_DNS_RESOLVER,
                status = DnsLookupStatus.NO_RECORDS,
                durationMs = 10L,
                startTime = 1_000L,
                endTime = 1_010L,
                errorMessage = null,
            ),
            domainResults = emptyList(),
            checks = listOf(
                check(DiagnosticStage.NETWORK_CONTEXT, DiagnosticCheckStatus.PASS),
                check(DiagnosticStage.GATEWAY, DiagnosticCheckStatus.NOT_APPLICABLE),
                check(DiagnosticStage.PUBLIC_CONNECTIVITY, DiagnosticCheckStatus.UNKNOWN),
                check(DiagnosticStage.DNS, DiagnosticCheckStatus.NO_RECORDS),
                check(DiagnosticStage.DOMAIN_CONNECTIVITY, DiagnosticCheckStatus.SKIPPED),
            ),
            networkChanged = false,
        )
    }

    private fun check(stage: DiagnosticStage, status: DiagnosticCheckStatus) = DiagnosticCheck(
        id = stage.name,
        stage = stage,
        name = stage.name,
        status = status,
        severity = if (status == DiagnosticCheckStatus.PASS) {
            DiagnosticSeverity.HEALTHY
        } else {
            DiagnosticSeverity.NOTICE
        },
        summary = "test",
    )
}
