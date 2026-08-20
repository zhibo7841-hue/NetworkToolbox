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
import com.networktoolbox.feature.subnet.presentation.SubnetViewModel
import com.networktoolbox.feature.subnet.ui.SubnetScreen
import com.networktoolbox.ui.theme.NetworkToolboxTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val dashboardViewModel: DashboardViewModel by viewModels()
    private val subnetViewModel: SubnetViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val uiState by dashboardViewModel.uiState.collectAsState()
            val subnetUiState by subnetViewModel.uiState.collectAsState()
            var showSubnet by rememberSaveable { mutableStateOf(false) }

            NetworkToolboxTheme {
                if (showSubnet) {
                    SubnetScreen(
                        uiState = subnetUiState,
                        onInputChanged = subnetViewModel::onInputChanged,
                        onCalculate = subnetViewModel::calculate,
                        onBack = { showSubnet = false },
                    )
                } else {
                    DashboardScreen(
                        uiState = uiState,
                        onOpenSubnet = { showSubnet = true },
                    )
                }
            }
        }
    }
}
