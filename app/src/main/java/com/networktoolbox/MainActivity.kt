package com.networktoolbox

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.ActivityNotFoundException
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import android.widget.Toast
import com.networktoolbox.core.common.diagnostic.DiagnosticDiagnosisStatus
import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryType
import com.networktoolbox.core.designsystem.networkToolboxNavigationItemColors
import com.networktoolbox.feature.dashboard.DashboardViewModel
import com.networktoolbox.feature.dashboard.HomeScreen
import com.networktoolbox.feature.dashboard.RecentHistoryPreview
import com.networktoolbox.feature.dashboard.RecentDiagnosticStatus
import com.networktoolbox.feature.dashboard.ToolsScreen
import com.networktoolbox.feature.dns.presentation.DnsViewModel
import com.networktoolbox.feature.dns.ui.DnsScreen
import com.networktoolbox.feature.history.presentation.HistoryUiState
import com.networktoolbox.feature.history.presentation.HistoryViewModel
import com.networktoolbox.feature.history.ui.HistoryScreen
import com.networktoolbox.feature.lanscan.presentation.LanScannerViewModel
import com.networktoolbox.feature.lanscan.presentation.LanScanRangeMode
import com.networktoolbox.feature.lanscan.ui.LanDeviceCenterScreen
import com.networktoolbox.feature.lanscan.ui.DeviceDetailScreen
import com.networktoolbox.feature.lanscan.ui.LanScannerScreen
import com.networktoolbox.feature.ping.presentation.PingViewModel
import com.networktoolbox.feature.ping.ui.PingScreen
import com.networktoolbox.feature.port.presentation.TcpViewModel
import com.networktoolbox.feature.port.ui.TcpScreen
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticReportV2
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticHistoryReportResolver
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticOverallStatus
import com.networktoolbox.feature.report.diagnostic.v2.ResolvedDiagnosticHistory
import com.networktoolbox.feature.report.domain.AutomaticDiagnosticResult
import com.networktoolbox.feature.report.presentation.ReportStatus
import com.networktoolbox.feature.report.presentation.ReportPresentationContext
import com.networktoolbox.feature.report.presentation.ReportViewModel
import com.networktoolbox.feature.report.ui.ReportScreen
import com.networktoolbox.feature.subnet.presentation.SubnetViewModel
import com.networktoolbox.feature.subnet.ui.SubnetScreen
import com.networktoolbox.feature.traceroute.presentation.TracerouteViewModel
import com.networktoolbox.feature.traceroute.ui.TracerouteScreen
import com.networktoolbox.core.designsystem.NetworkToolboxTheme
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private companion object {
        const val PDF_MIME_TYPE = "application/pdf"
    }

    private val dashboardViewModel: DashboardViewModel by viewModels()
    private val dnsViewModel: DnsViewModel by viewModels()
    private val historyViewModel: HistoryViewModel by viewModels()
    private val pingViewModel: PingViewModel by viewModels()
    private val tcpViewModel: TcpViewModel by viewModels()
    private val reportViewModel: ReportViewModel by viewModels()
    private val subnetViewModel: SubnetViewModel by viewModels()
    private val lanScannerViewModel: LanScannerViewModel by viewModels()
    private val tracerouteViewModel: TracerouteViewModel by viewModels()
    private var pendingPdfBytes: ByteArray? = null

    private val createPdfDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument(PDF_MIME_TYPE),
    ) { uri ->
        val bytes = pendingPdfBytes
        pendingPdfBytes = null
        if (uri == null || bytes == null) return@registerForActivityResult

        runCatching {
            val output = contentResolver.openOutputStream(uri)
                ?: error("Unable to open selected document")
            output.use { it.write(bytes) }
        }.onSuccess {
            Toast.makeText(this, "报告已保存为 PDF", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, "保存 PDF 失败，请重试。", Toast.LENGTH_LONG).show()
        }
    }

    private fun copyDiagnosticReport(text: String) {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("NetworkToolbox 报告", text))
    }

    private fun saveDiagnosticReportPdf(bytes: ByteArray, fileName: String) {
        pendingPdfBytes = bytes
        runCatching {
            createPdfDocumentLauncher.launch(fileName)
        }.onFailure {
            pendingPdfBytes = null
            Toast.makeText(this, "无法打开文件保存界面。", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareDiagnosticReportPdf(bytes: ByteArray, fileName: String) {
        runCatching {
            val reportDirectory = File(cacheDir, "reports")
            if (!reportDirectory.exists() && !reportDirectory.mkdirs()) {
                error("Unable to create temporary report directory")
            }
            reportDirectory.listFiles()
                ?.filter { it.isFile && it.extension.equals("pdf", ignoreCase = true) }
                ?.forEach { it.delete() }

            val reportFile = File(reportDirectory, fileName)
            reportFile.outputStream().use { it.write(bytes) }
            val contentUri = FileProvider.getUriForFile(
                this,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                reportFile,
            )
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = PDF_MIME_TYPE
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(sendIntent, "分享诊断 PDF"))
        }.onFailure { error ->
            val message = if (error is ActivityNotFoundException) {
                "没有可用的应用来分享 PDF。"
            } else {
                "分享 PDF 失败，请重试。"
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dashboardUiState by dashboardViewModel.uiState.collectAsState()
            val recentDiagnosisRecord by dashboardViewModel.recentDiagnosis.collectAsState()
            val dnsUiState by dnsViewModel.uiState.collectAsState()
            val historyUiState by historyViewModel.uiState.collectAsState()
            val pingUiState by pingViewModel.uiState.collectAsState()
            val tcpUiState by tcpViewModel.uiState.collectAsState()
            val reportUiState by reportViewModel.uiState.collectAsState()
            val subnetUiState by subnetViewModel.uiState.collectAsState()
            val lanScannerUiState by lanScannerViewModel.uiState.collectAsState()
            val favoriteDevices by lanScannerViewModel.favoriteDevices.collectAsState()
            val tracerouteUiState by tracerouteViewModel.uiState.collectAsState()
            var navigationState by rememberSaveable(stateSaver = AppNavigationState.Saver) {
                mutableStateOf(AppNavigationState())
            }
            val topLevelDestination = navigationState.topLevelDestination
            val toolScreen = navigationState.toolScreen
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val drawerScope = rememberCoroutineScope()
            var restoredDiagnosticReport by remember {
                mutableStateOf<DiagnosticReportV2?>(null)
            }
            var restoredAutomaticDiagnosticResult by remember {
                mutableStateOf<AutomaticDiagnosticResult?>(null)
            }
            val recentHistory = recentDiagnosisRecord?.let { record ->
                RecentHistoryPreview(
                    type = record.type.displayName(),
                    title = record.title,
                    summary = record.summary,
                    timestamp = record.timestamp,
                    status = DiagnosticHistoryReportResolver.resolve(record)
                        ?.recentDiagnosticStatus()
                    ?: RecentDiagnosticStatus.UNKNOWN,
                )
            }

            fun openDrawer() {
                drawerScope.launch { drawerState.open() }
            }

            fun closeDrawer() {
                drawerScope.launch { drawerState.close() }
            }

            fun openTool(screen: ToolScreen) {
                closeDrawer()
                if (toolScreen == ToolScreen.LAN_SCAN && screen != ToolScreen.LAN_SCAN) {
                    lanScannerViewModel.stopScan()
                }
                if (toolScreen == ToolScreen.TRACEROUTE && screen != ToolScreen.TRACEROUTE) {
                    tracerouteViewModel.stop()
                }
                restoredDiagnosticReport = null
                restoredAutomaticDiagnosticResult = null
                navigationState = navigationState.openTool(screen)
                if (screen == ToolScreen.HISTORY) {
                    historyViewModel.load()
                }
            }

            fun selectTopLevel(destination: TopLevelDestination) {
                closeDrawer()
                if (reportUiState.status is ReportStatus.Running) {
                    reportViewModel.stopCheck()
                }
                lanScannerViewModel.stopScan()
                tracerouteViewModel.stop()
                if (destination == TopLevelDestination.DEVICES) {
                    lanScannerViewModel.prepareDeviceCenter()
                }
                restoredDiagnosticReport = null
                restoredAutomaticDiagnosticResult = null
                navigationState = navigationState.selectTopLevel(destination)
            }

            fun goBack() {
                if (toolScreen == ToolScreen.NONE) return
                if (toolScreen == ToolScreen.DEVICE_DETAIL) {
                    restoredDiagnosticReport = null
                    restoredAutomaticDiagnosticResult = null
                    navigationState = navigationState.goBack()
                    return
                }
                if (reportUiState.status is ReportStatus.Running) {
                    reportViewModel.stopCheck()
                }
                lanScannerViewModel.stopScan()
                tracerouteViewModel.stop()
                restoredDiagnosticReport = null
                restoredAutomaticDiagnosticResult = null
                navigationState = navigationState.goBack()
            }

            fun openDrawerDestination(destination: ToolScreen) {
                closeDrawer()
                if (reportUiState.status is ReportStatus.Running) {
                    reportViewModel.stopCheck()
                }
                lanScannerViewModel.stopScan()
                tracerouteViewModel.stop()
                restoredDiagnosticReport = null
                restoredAutomaticDiagnosticResult = null
                navigationState = navigationState.openSecondaryDestination(destination)
                if (destination == ToolScreen.HISTORY) {
                    historyViewModel.load()
                }
            }

            fun openDiagnosticHistory(record: HistoryRecord) {
                when (val resolved = DiagnosticHistoryReportResolver.resolve(record)) {
                    is ResolvedDiagnosticHistory.Automatic -> {
                        restoredDiagnosticReport = null
                        restoredAutomaticDiagnosticResult = resolved.result
                        navigationState = navigationState.openTool(ToolScreen.REPORT)
                    }

                    is ResolvedDiagnosticHistory.Legacy -> {
                        restoredAutomaticDiagnosticResult = null
                        restoredDiagnosticReport = resolved.report
                        navigationState = navigationState.openTool(ToolScreen.REPORT)
                    }

                    null -> Unit
                }
            }

            BackHandler(enabled = drawerState.isOpen) {
                closeDrawer()
            }

            BackHandler(enabled = !drawerState.isOpen && toolScreen != ToolScreen.NONE) {
                goBack()
            }

            NetworkToolboxTheme {
                AppShellDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = AppShellPresentation.canShowDrawer(navigationState),
                    onOpenHistory = { openDrawerDestination(ToolScreen.HISTORY) },
                    onOpenPrivacy = { openDrawerDestination(ToolScreen.PRIVACY) },
                    onOpenAbout = { openDrawerDestination(ToolScreen.ABOUT) },
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        contentWindowInsets = WindowInsets.safeDrawing,
                        bottomBar = {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ) {
                                val navigationItemColors = networkToolboxNavigationItemColors()
                                TopLevelDestination.entries.forEach { destination ->
                                    NavigationBarItem(
                                        selected = topLevelDestination == destination,
                                        onClick = { selectTopLevel(destination) },
                                        colors = navigationItemColors,
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
                                    onOpenMenu = ::openDrawer,
                                    onOpenPing = { openTool(ToolScreen.PING) },
                                    onOpenDns = { openTool(ToolScreen.DNS) },
                                    onOpenReport = { openTool(ToolScreen.REPORT) },
                                    onOpenHistory = { openTool(ToolScreen.HISTORY) },
                                    onOpenTraceroute = { openTool(ToolScreen.TRACEROUTE) },
                                    onOpenLanScan = { openTool(ToolScreen.LAN_SCAN) },
                                )
                                TopLevelDestination.TOOLS -> ToolsScreen(
                                    onOpenMenu = ::openDrawer,
                                    onOpenPing = { openTool(ToolScreen.PING) },
                                    onOpenDns = { openTool(ToolScreen.DNS) },
                                    onOpenTcp = { openTool(ToolScreen.TCP) },
                                    onOpenTraceroute = { openTool(ToolScreen.TRACEROUTE) },
                                    onOpenSubnet = { openTool(ToolScreen.SUBNET) },
                                    onOpenLanScan = { openTool(ToolScreen.LAN_SCAN) },
                                    onOpenReport = { openTool(ToolScreen.REPORT) },
                                )
                                TopLevelDestination.DEVICES -> LanDeviceCenterScreen(
                                    uiState = lanScannerUiState,
                                    favorites = favoriteDevices,
                                    onStartScan = lanScannerViewModel::startCurrentNetworkScan,
                                    onStopScan = lanScannerViewModel::stopScan,
                                    onRescan = lanScannerViewModel::rescanCurrentNetwork,
                                    onOpenMenu = ::openDrawer,
                                    onOpenDevice = { key ->
                                        navigationState = navigationState.openDeviceDetail(key)
                                    },
                                )
                            }
                            ToolScreen.PRIVACY -> PrivacyScreen(onBack = ::goBack)
                            ToolScreen.ABOUT -> AboutScreen(onBack = ::goBack)
                            ToolScreen.SUBNET -> SubnetScreen(
                                uiState = subnetUiState,
                                onInputChanged = subnetViewModel::onInputChanged,
                                onCalculate = subnetViewModel::calculate,
                                onBack = ::goBack,
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
                                onBack = ::goBack,
                            )
                            ToolScreen.DNS -> DnsScreen(
                                uiState = dnsUiState,
                                onDomainChanged = dnsViewModel::onDomainChanged,
                                onLookup = dnsViewModel::lookup,
                                onAdvancedSettingsToggle = dnsViewModel::toggleAdvancedSettings,
                                onRecordTypeToggle = dnsViewModel::toggleRecordType,
                                onBack = ::goBack,
                            )
                            ToolScreen.TCP -> TcpScreen(
                                uiState = tcpUiState,
                                onHostChanged = tcpViewModel::onHostChanged,
                                onPortChanged = tcpViewModel::onPortChanged,
                                onCheck = tcpViewModel::check,
                                onBack = ::goBack,
                            )
                            ToolScreen.TRACEROUTE -> TracerouteScreen(
                                uiState = tracerouteUiState,
                                onTargetChanged = tracerouteViewModel::onTargetChanged,
                                onStart = tracerouteViewModel::start,
                                onStop = tracerouteViewModel::stop,
                                onBack = ::goBack,
                            )
                            ToolScreen.REPORT -> ReportScreen(
                                uiState = reportUiState,
                                context = if (
                                    restoredDiagnosticReport != null ||
                                        restoredAutomaticDiagnosticResult != null
                                ) {
                                    ReportPresentationContext.SAVED_REPORT
                                } else {
                                    ReportPresentationContext.LIVE_TOOL
                                },
                                restoredReport = restoredDiagnosticReport,
                                restoredAutomaticResult = restoredAutomaticDiagnosticResult,
                                onRunCheck = {
                                    restoredDiagnosticReport = null
                                    restoredAutomaticDiagnosticResult = null
                                    reportViewModel.runCheck()
                                },
                                onStopCheck = reportViewModel::stopCheck,
                                onBack = {
                                    goBack()
                                },
                                onCopyReport = ::copyDiagnosticReport,
                                onSavePdf = ::saveDiagnosticReportPdf,
                                onSharePdf = ::shareDiagnosticReportPdf,
                            )
                            ToolScreen.HISTORY -> HistoryScreen(
                                uiState = historyUiState,
                                onLoad = historyViewModel::load,
                                onDelete = historyViewModel::delete,
                                onClear = historyViewModel::clear,
                                onBack = ::goBack,
                                onOpenReport = ::openDiagnosticHistory,
                                canOpenReport = DiagnosticHistoryReportResolver::canOpen,
                            )
                            ToolScreen.LAN_SCAN -> LanScannerScreen(
                                uiState = lanScannerUiState,
                                onStartScan = lanScannerViewModel::startScan,
                                onStopScan = lanScannerViewModel::stopScan,
                                onRetry = lanScannerViewModel::rescan,
                                onModifyRange = lanScannerViewModel::modifyRange,
                                onBack = ::goBack,
                                onRangeModeChanged = lanScannerViewModel::selectRangeMode,
                                onCustomStartAddressChanged = lanScannerViewModel::onCustomStartAddressChanged,
                                onCustomEndAddressChanged = lanScannerViewModel::onCustomEndAddressChanged,
                            )
                            ToolScreen.DEVICE_DETAIL -> DeviceDetailScreen(
                                detail = lanScannerViewModel.resolveDeviceDetail(
                                    navigationState.deviceDetailKey,
                                ),
                                onBack = ::goBack,
                                onToggleFavorite = {
                                    lanScannerViewModel.toggleFavoriteByRouteKey(
                                        navigationState.deviceDetailKey,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

}

private fun HistoryType.displayName(): String = when (this) {
    HistoryType.PING -> "Ping"
    HistoryType.DNS -> "DNS 查询"
    HistoryType.TCP -> "TCP 端口检测"
    HistoryType.REPORT -> "网络诊断"
    HistoryType.LAN_SCAN -> "局域网扫描"
    HistoryType.UNKNOWN -> "其他"
}

private fun ResolvedDiagnosticHistory.recentDiagnosticStatus(): RecentDiagnosticStatus = when (this) {
    is ResolvedDiagnosticHistory.Automatic -> result.analysis.diagnosis?.status
        .toRecentDiagnosticStatus()
    is ResolvedDiagnosticHistory.Legacy -> report.overallStatus.toRecentDiagnosticStatus()
}

private fun DiagnosticDiagnosisStatus?.toRecentDiagnosticStatus(): RecentDiagnosticStatus = when (this) {
    DiagnosticDiagnosisStatus.NORMAL -> RecentDiagnosticStatus.NORMAL
    DiagnosticDiagnosisStatus.ATTENTION -> RecentDiagnosticStatus.WARNING
    DiagnosticDiagnosisStatus.LIMITED -> RecentDiagnosticStatus.NOTICE
    DiagnosticDiagnosisStatus.UNKNOWN,
    null,
    -> RecentDiagnosticStatus.UNKNOWN
}

private fun DiagnosticOverallStatus.toRecentDiagnosticStatus(): RecentDiagnosticStatus = when (this) {
    DiagnosticOverallStatus.HEALTHY -> RecentDiagnosticStatus.NORMAL
    DiagnosticOverallStatus.ATTENTION -> RecentDiagnosticStatus.WARNING
    DiagnosticOverallStatus.LIMITED -> RecentDiagnosticStatus.NOTICE
    DiagnosticOverallStatus.UNKNOWN -> RecentDiagnosticStatus.UNKNOWN
}
