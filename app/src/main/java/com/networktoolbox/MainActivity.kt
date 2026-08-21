package com.networktoolbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.networktoolbox.core.common.history.HistoryType
import com.networktoolbox.feature.dashboard.DashboardScreen
import com.networktoolbox.feature.dashboard.DashboardViewModel
import com.networktoolbox.feature.dashboard.RecentHistoryPreview
import com.networktoolbox.feature.dns.presentation.DnsViewModel
import com.networktoolbox.feature.dns.ui.DnsScreen
import com.networktoolbox.feature.history.presentation.HistoryViewModel
import com.networktoolbox.feature.history.presentation.HistoryUiState
import com.networktoolbox.feature.history.ui.HistoryScreen
import com.networktoolbox.feature.ping.presentation.PingViewModel
import com.networktoolbox.feature.ping.ui.PingScreen
import com.networktoolbox.feature.port.presentation.TcpViewModel
import com.networktoolbox.feature.port.ui.TcpScreen
import com.networktoolbox.feature.report.presentation.ReportViewModel
import com.networktoolbox.feature.report.ui.ReportScreen
import com.networktoolbox.feature.subnet.presentation.SubnetViewModel
import com.networktoolbox.feature.subnet.ui.SubnetScreen
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val uiState by dashboardViewModel.uiState.collectAsState()
            val dnsUiState by dnsViewModel.uiState.collectAsState()
            val historyUiState by historyViewModel.uiState.collectAsState()
            val pingUiState by pingViewModel.uiState.collectAsState()
            val tcpUiState by tcpViewModel.uiState.collectAsState()
            val reportUiState by reportViewModel.uiState.collectAsState()
            val subnetUiState by subnetViewModel.uiState.collectAsState()
            var currentScreen by rememberSaveable { mutableStateOf(AppScreen.DASHBOARD) }
            val recentHistory = (historyUiState as? HistoryUiState.Success)
                ?.records
                ?.firstOrNull()
                ?.let { record ->
                    RecentHistoryPreview(
                        type = record.type.displayName(),
                        title = record.title,
                        summary = record.summary,
                        timestamp = record.timestamp,
                    )
                }

            NetworkToolboxTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing,
                ) { contentPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentPadding),
                    ) {
                        when (currentScreen) {
                            AppScreen.DASHBOARD -> DashboardScreen(
                                uiState = uiState,
                                recentHistory = recentHistory,
                                onOpenSubnet = { currentScreen = AppScreen.SUBNET },
                                onOpenPing = { currentScreen = AppScreen.PING },
                                onOpenDns = { currentScreen = AppScreen.DNS },
                                onOpenTcp = { currentScreen = AppScreen.TCP },
                                onOpenReport = { currentScreen = AppScreen.REPORT },
                                onOpenHistory = { currentScreen = AppScreen.HISTORY },
                            )
                            AppScreen.SUBNET -> SubnetScreen(
                                uiState = subnetUiState,
                                onInputChanged = subnetViewModel::onInputChanged,
                                onCalculate = subnetViewModel::calculate,
                                onBack = { currentScreen = AppScreen.DASHBOARD },
                            )
                            AppScreen.PING -> PingScreen(
                                uiState = pingUiState,
                                onTargetChanged = pingViewModel::onTargetChanged,
                                onPing = pingViewModel::ping,
                                onBack = { currentScreen = AppScreen.DASHBOARD },
                            )
                            AppScreen.DNS -> DnsScreen(
                                uiState = dnsUiState,
                                onDomainChanged = dnsViewModel::onDomainChanged,
                                onLookup = dnsViewModel::lookup,
                                onBack = { currentScreen = AppScreen.DASHBOARD },
                            )
                            AppScreen.TCP -> TcpScreen(
                                uiState = tcpUiState,
                                onHostChanged = tcpViewModel::onHostChanged,
                                onPortChanged = tcpViewModel::onPortChanged,
                                onCheck = tcpViewModel::check,
                                onBack = { currentScreen = AppScreen.DASHBOARD },
                            )
                            AppScreen.REPORT -> ReportScreen(
                                uiState = reportUiState,
                                onRunCheck = reportViewModel::runCheck,
                                onBack = { currentScreen = AppScreen.DASHBOARD },
                            )
                            AppScreen.HISTORY -> HistoryScreen(
                                uiState = historyUiState,
                                onLoad = historyViewModel::load,
                                onDelete = historyViewModel::delete,
                                onClear = historyViewModel::clear,
                                onBack = { currentScreen = AppScreen.DASHBOARD },
                            )
                        }
                    }
                }
            }
    }
}
}

private enum class AppScreen {
    DASHBOARD,
    SUBNET,
    PING,
    DNS,
    TCP,
    REPORT,
    HISTORY,
}

private fun HistoryType.displayName(): String = when (this) {
    HistoryType.PING -> "Ping"
    HistoryType.DNS -> "DNS Lookup"
    HistoryType.TCP -> "TCP Port Check"
    HistoryType.REPORT -> "Network Diagnostic"
    HistoryType.UNKNOWN -> "Other"
}
