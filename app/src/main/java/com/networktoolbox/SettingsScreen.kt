package com.networktoolbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.networktoolbox.feature.history.presentation.HistoryUiState

@Composable
fun SettingsScreen(
    historyUiState: HistoryUiState,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    val isClearing = historyUiState is HistoryUiState.Loading

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("设置", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "查看项目信息并管理本地数据。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingsCard(title = "关于") {
                Text("NetworkToolbox", style = MaterialTheme.typography.titleMedium)
                Text("Open Source Network Analyzer")
                Text("Version 0.1.0", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SettingsCard(title = "数据管理") {
                Text("检测历史仅保存在本机。")
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showClearDialog = true },
                    enabled = !isClearing,
                ) {
                    Text(if (isClearing) "清理中..." else "清空历史")
                }
                if (historyUiState is HistoryUiState.Error) {
                    Text(
                        "状态：${historyUiState.message}",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            SettingsCard(title = "隐私保护") {
                Text("所有网络数据都保留在本机。")
                Text(
                    "网络检测结果和历史记录不会上传。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空全部历史？") },
            text = { Text("这将删除本机保存的全部检测历史。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        onClearHistory()
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
