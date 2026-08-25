package com.networktoolbox.feature.subnet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.networktoolbox.core.common.ipv4.SubnetResult
import com.networktoolbox.feature.subnet.presentation.SubnetUiState

@Composable
fun SubnetScreen(
    uiState: SubnetUiState,
    onInputChanged: (String) -> Unit,
    onCalculate: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            TextButton(onClick = onBack) {
                Text("返回工具")
            }
            Text(
                text = "IPv4 子网计算器",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "根据 IPv4 地址和 CIDR 计算网络范围",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("输入", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = uiState.input,
                        onValueChange = onInputChanged,
                        label = { Text("IPv4 地址/CIDR") },
                        singleLine = true,
                        isError = uiState.errorMessage != null,
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onCalculate,
                    ) {
                        Text("计算")
                    }
                }
            }

            uiState.errorMessage?.let { message ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "状态：失败",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            uiState.result?.let { result ->
                Text("结果", style = MaterialTheme.typography.titleLarge)
                SubnetResultCard(result)
            }
        }
    }
}

@Composable
private fun SubnetResultCard(result: SubnetResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text("状态：已完成", style = MaterialTheme.typography.titleMedium)
            ResultRow("IP", result.ipAddress)
            ResultRow("CIDR", "/${result.prefixLength}")
            ResultRow("子网掩码", result.subnetMask)
            ResultRow("网络地址", result.networkAddress)
            ResultRow("广播地址", result.broadcastAddress)
            ResultRow(
                "可用范围",
                "${result.usableRangeStart} - ${result.usableRangeEnd}",
            )
            ResultRow("主机数量", result.hostCount.toString())
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(0.45f),
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            modifier = Modifier.weight(0.55f),
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
