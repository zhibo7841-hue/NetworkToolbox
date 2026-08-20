package com.networktoolbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.networktoolbox.feature.dashboard.DashboardScreen
import com.networktoolbox.ui.theme.NetworkToolboxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NetworkToolboxTheme {
                DashboardScreen()
            }
        }
    }
}
