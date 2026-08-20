package com.networktoolbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.networktoolbox.feature.dashboard.DashboardScreen
import com.networktoolbox.feature.dashboard.DashboardViewModel
import com.networktoolbox.ui.theme.NetworkToolboxTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val dashboardViewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val uiState by dashboardViewModel.uiState.collectAsState()
            NetworkToolboxTheme {
                DashboardScreen(uiState = uiState)
            }
        }
    }
}
