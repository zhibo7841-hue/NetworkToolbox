package com.networktoolbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.networktoolbox.feature.dashboard.DashboardScreen
import com.networktoolbox.feature.dashboard.DashboardViewModel
import com.networktoolbox.feature.dns.presentation.DnsViewModel
import com.networktoolbox.feature.dns.ui.DnsScreen
import com.networktoolbox.feature.ping.presentation.PingViewModel
import com.networktoolbox.feature.ping.ui.PingScreen
import com.networktoolbox.feature.subnet.presentation.SubnetViewModel
import com.networktoolbox.feature.subnet.ui.SubnetScreen
import com.networktoolbox.ui.theme.NetworkToolboxTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val dashboardViewModel: DashboardViewModel by viewModels()
    private val dnsViewModel: DnsViewModel by viewModels()
    private val pingViewModel: PingViewModel by viewModels()
    private val subnetViewModel: SubnetViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val uiState by dashboardViewModel.uiState.collectAsState()
            val dnsUiState by dnsViewModel.uiState.collectAsState()
            val pingUiState by pingViewModel.uiState.collectAsState()
            val subnetUiState by subnetViewModel.uiState.collectAsState()
            var currentScreen by rememberSaveable { mutableStateOf(AppScreen.DASHBOARD) }

            NetworkToolboxTheme {
                when (currentScreen) {
                    AppScreen.DASHBOARD -> DashboardScreen(
                        uiState = uiState,
                        onOpenSubnet = { currentScreen = AppScreen.SUBNET },
                        onOpenPing = { currentScreen = AppScreen.PING },
                        onOpenDns = { currentScreen = AppScreen.DNS },
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
}
