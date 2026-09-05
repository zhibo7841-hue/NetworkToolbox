package com.networktoolbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryType
import com.networktoolbox.feature.dashboard.DashboardViewModel
import com.networktoolbox.feature.dashboard.HomeScreen
import com.networktoolbox.feature.dashboard.RecentHistoryPreview
import com.networktoolbox.feature.dashboard.ToolsScreen
import com.networktoolbox.feature.dns.presentation.DnsViewModel
import com.networktoolbox.feature.dns.ui.DnsScreen
import com.networktoolbox.feature.history.presentation.HistoryUiState
import com.networktoolbox.feature.history.presentation.HistoryViewModel
import com.networktoolbox.feature.history.ui.HistoryScreen
import com.networktoolbox.feature.lanscan.presentation.LanScannerViewModel
import com.networktoolbox.feature.lanscan.presentation.LanScanRangeMode
import com.networktoolbox.feature.lanscan.ui.LanScannerScreen
import com.networktoolbox.feature.ping.presentation.PingViewModel
import com.networktoolbox.feature.ping.ui.PingScreen
import com.networktoolbox.feature.port.presentation.TcpViewModel
import com.networktoolbox.feature.port.ui.TcpScreen
import com.networktoolbox.feature.report.diagnostic.v2.AutomaticDiagnosticHistorySnapshotDeserializer
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticReportV2
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticReportV2HistoryDeserializer
import com.networktoolbox.feature.report.domain.AutomaticDiagnosticResult
import com.networktoolbox.feature.report.presentation.ReportStatus
import com.networktoolbox.feature.report.presentation.ReportViewModel
import com.networktoolbox.feature.report.ui.ReportScreen
import com.networktoolbox.feature.subnet.presentation.SubnetViewModel
import com.networktoolbox.feature.subnet.ui.SubnetScreen
import com.networktoolbox.feature.traceroute.presentation.TracerouteViewModel
import com.networktoolbox.feature.traceroute.ui.TracerouteScreen
import com.networktoolbox.ui.theme.NetworkToolboxTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val dashboardViewModel: DashboardViewModel by viewModels()
    private val dnsViewModel: DnsViewModel by viewModels()
    private val historyViewModel: HistoryViewModel by viewModels()
    private val pingViewModel: PingViewModel by viewModels()
    private val tcpViewModel: TcpViewModel by viewModels()
    private val reportViewModel: ReportViewModel by viewModels()
    private val subnetViewModel: SubnetViewModel by viewModels()
    private val lanScannerViewModel: LanScannerViewModel by viewModels()
    private val tracerouteViewModel: TracerouteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dashboardUiState by dashboardViewModel.uiState.collectAsState()
            val dnsUiState by dnsViewModel.uiState.collectAsState()
            val historyUiState by historyViewModel.uiState.collectAsState()
            val pingUiState by pingViewModel.uiState.collectAsState()
            val tcpUiState by tcpViewModel.uiState.collectAsState()
            val reportUiState by reportViewModel.uiState.collectAsState()
            val subnetUiState by subnetViewModel.uiState.collectAsState()
            val lanScannerUiState by lanScannerViewModel.uiState.collectAsState()
            val tracerouteUiState by tracerouteViewModel.uiState.collectAsState()
            var topLevelDestination by rememberSaveable {
                mutableStateOf(TopLevelDestination.HOME)
            }
            var toolScreen by rememberSaveable { mutableStateOf(ToolScreen.NONE) }
            var restoredDiagnosticReport by remember {
                mutableStateOf<DiagnosticReportV2?>(null)
            }
            var restoredAutomaticDiagnosticResult by remember {
                mutableStateOf<AutomaticDiagnosticResult?>(null)
            }
            val recentHistory = (historyUiState as? HistoryUiState.Success)
                ?.records
                ?.firstOrNull { it.type == HistoryType.REPORT }
                ?.let { record ->
                    RecentHistoryPreview(
                        type = record.type.displayName(),
                        title = record.title,
                        summary = record.summary,
                        timestamp = record.timestamp,
                    )
                }

            fun openTool(screen: ToolScreen) {
                if (toolScreen == ToolScreen.LAN_SCAN && screen != ToolScreen.LAN_SCAN) {
                    lanScannerViewModel.stopScan()
                }
                if (toolScreen == ToolScreen.TRACEROUTE && screen != ToolScreen.TRACEROUTE) {
                    tracerouteViewModel.stop()
                }
                restoredDiagnosticReport = null
                restoredAutomaticDiagnosticResult = null
                topLevelDestination = TopLevelDestination.TOOLS
                toolScreen = screen
                if (screen == ToolScreen.HISTORY) {
                    historyViewModel.load()
                }
            }

            fun openTopLevel(destination: TopLevelDestination) {
                if (reportUiState.status is ReportStatus.Running) {
                    reportViewModel.stopCheck()
                }
                lanScannerViewModel.stopScan()
                tracerouteViewModel.stop()
                restoredDiagnosticReport = null
                restoredAutomaticDiagnosticResult = null
                topLevelDestination = destination
                toolScreen = ToolScreen.NONE
            }

            fun openDiagnosticHistory(record: HistoryRecord) {
                AutomaticDiagnosticHistorySnapshotDeserializer.fromHistoryRecord(record)?.let { result ->
                    restoredDiagnosticReport = null
                    restoredAutomaticDiagnosticResult = result
                    topLevelDestination = TopLevelDestination.TOOLS
                    toolScreen = ToolScreen.REPORT
                    return
                }
                DiagnosticReportV2HistoryDeserializer.fromHistoryRecord(record)?.let { report ->
                    restoredAutomaticDiagnosticResult = null
                    restoredDiagnosticReport = report
                    topLevelDestination = TopLevelDestination.TOOLS
                    toolScreen = ToolScreen.REPORT
                }
            }

            BackHandler(enabled = toolScreen != ToolScreen.NONE) {
                openTopLevel(TopLevelDestination.TOOLS)
            }

            NetworkToolboxTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing,
                    bottomBar = {
                        NavigationBar {
                            TopLevelDestination.entries.forEach { destination ->
                                NavigationBarItem(
                                    selected = topLevelDestination == destination,
                                    onClick = { openTopLevel(destination) },
                                    icon = {
                                        Icon(
                                            imageVector = destination.icon,
                                            contentDescription = destination.label,
                                        )
                                    },
                                    label = { Text(destination.label) },
                                )
                            }
                        }
                    },
                ) { contentPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentPadding),
                    ) {
                        when (toolScreen) {
                            ToolScreen.NONE -> when (topLevelDestination) {
                                TopLevelDestination.HOME -> HomeScreen(
                                    uiState = dashboardUiState,
                                    recentHistory = recentHistory,
                                    onOpenPing = { openTool(ToolScreen.PING) },
                                    onOpenDns = { openTool(ToolScreen.DNS) },
                                    onOpenReport = { openTool(ToolScreen.REPORT) },
                                    onOpenHistory = { openTool(ToolScreen.HISTORY) },
                                )
                                TopLevelDestination.TOOLS -> ToolsScreen(
                                    onOpenPing = { openTool(ToolScreen.PING) },
                                    onOpenDns = { openTool(ToolScreen.DNS) },
                                    onOpenTcp = { openTool(ToolScreen.TCP) },
                                    onOpenTraceroute = { openTool(ToolScreen.TRACEROUTE) },
                                    onOpenSubnet = { openTool(ToolScreen.SUBNET) },
                                    onOpenLanScan = { openTool(ToolScreen.LAN_SCAN) },
                                    onOpenReport = { openTool(ToolScreen.REPORT) },
                                    onOpenHistory = { openTool(ToolScreen.HISTORY) },
                                )
                                TopLevelDestination.SETTINGS -> SettingsScreen(
                                    historyUiState = historyUiState,
                                    onClearHistory = historyViewModel::clear,
                                )
                            }
                            ToolScreen.SUBNET -> SubnetScreen(
                                uiState = subnetUiState,
                                onInputChanged = subnetViewModel::onInputChanged,
                                onCalculate = subnetViewModel::calculate,
                                onBack = { openTopLevel(TopLevelDestination.TOOLS) },
                            )
                            ToolScreen.PING -> PingScreen(
                                uiState = pingUiState,
                                onTargetChanged = pingViewModel::onTargetChanged,
                                onModeChanged = pingViewModel::onModeChanged,
                                onProtocolChanged = pingViewModel::onProtocolChanged,
                                onCountChanged = pingViewModel::onCountChanged,
                                onIntervalChanged = pingViewModel::onIntervalChanged,
                                onPing = pingViewModel::startPing,
                                onStop = pingViewModel::stopPing,
                                onBack = { openTopLevel(TopLevelDestination.TOOLS) },
                            )
                            ToolScreen.DNS -> DnsScreen(
                                uiState = dnsUiState,
                                onDomainChanged = dnsViewModel::onDomainChanged,
                                onLookup = dnsViewModel::lookup,
                                onAdvancedSettingsToggle = dnsViewModel::toggleAdvancedSettings,
                                onRecordTypeToggle = dnsViewModel::toggleRecordType,
                                onBack = { openTopLevel(TopLevelDestination.TOOLS) },
                            )
                            ToolScreen.TCP -> TcpScreen(
                                uiState = tcpUiState,
                                onHostChanged = tcpViewModel::onHostChanged,
                                onPortChanged = tcpViewModel::onPortChanged,
                                onCheck = tcpViewModel::check,
                                onBack = { openTopLevel(TopLevelDestination.TOOLS) },
                            )
                            ToolScreen.TRACEROUTE -> TracerouteScreen(
                                uiState = tracerouteUiState,
                                onTargetChanged = tracerouteViewModel::onTargetChanged,
                                onStart = tracerouteViewModel::start,
                                onStop = tracerouteViewModel::stop,
                                onBack = { openTopLevel(TopLevelDestination.TOOLS) },
                            )
                            ToolScreen.REPORT -> ReportScreen(
                                uiState = reportUiState,
                                restoredReport = restoredDiagnosticReport,
                                restoredAutomaticResult = restoredAutomaticDiagnosticResult,
                                onRunCheck = {
                                    restoredDiagnosticReport = null
                                    restoredAutomaticDiagnosticResult = null
                                    reportViewModel.runCheck()
                                },
                                onStopCheck = reportViewModel::stopCheck,
                                onBack = {
                                    restoredDiagnosticReport = null
                                    restoredAutomaticDiagnosticResult = null
                                    openTopLevel(TopLevelDestination.TOOLS)
                                },
                            )
                            ToolScreen.HISTORY -> HistoryScreen(
                                uiState = historyUiState,
                                onLoad = historyViewModel::load,
                                onDelete = historyViewModel::delete,
                                onClear = historyViewModel::clear,
                                onBack = { openTopLevel(TopLevelDestination.TOOLS) },
                                onOpenReport = ::openDiagnosticHistory,
                            )
                            ToolScreen.LAN_SCAN -> LanScannerScreen(
                                uiState = lanScannerUiState,
                                onStartScan = lanScannerViewModel::startScan,
                                onStopScan = lanScannerViewModel::stopScan,
                                onRetry = lanScannerViewModel::rescan,
                                onModifyRange = lanScannerViewModel::modifyRange,
                                onBack = { openTopLevel(TopLevelDestination.TOOLS) },
                                onRangeModeChanged = lanScannerViewModel::selectRangeMode,
                                onCustomStartAddressChanged = lanScannerViewModel::onCustomStartAddressChanged,
                                onCustomEndAddressChanged = lanScannerViewModel::onCustomEndAddressChanged,
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class TopLevelDestination(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    HOME("首页", Icons.Outlined.Home),
    TOOLS("工具", Icons.Outlined.Build),
    SETTINGS("设置", Icons.Outlined.Settings),
}

private enum class ToolScreen {
    NONE,
    SUBNET,
    PING,
    DNS,
    TCP,
    TRACEROUTE,
    REPORT,
    HISTORY,
    LAN_SCAN,
}

private fun HistoryType.displayName(): String = when (this) {
    HistoryType.PING -> "Ping"
    HistoryType.DNS -> "DNS Lookup"
    HistoryType.TCP -> "TCP Port Check"
    HistoryType.REPORT -> "Network Diagnostic"
    HistoryType.LAN_SCAN -> "LAN Scanner"
    HistoryType.UNKNOWN -> "Other"
}
