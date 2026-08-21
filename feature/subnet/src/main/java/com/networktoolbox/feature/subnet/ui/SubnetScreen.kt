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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            TextButton(onClick = onBack) {
                Text("返回 Dashboard")
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
                    Text("Input", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = uiState.input,
                        onValueChange = onInputChanged,
                        label = { Text("IPv4 Address/CIDR") },
                        singleLine = true,
                        isError = uiState.errorMessage != null,
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onCalculate,
                    ) {
                        Text("Calculate")
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
                            "Status: Failed",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            uiState.result?.let { result ->
                Text("Result", style = MaterialTheme.typography.titleLarge)
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
            Text("Status: Completed", style = MaterialTheme.typography.titleMedium)
            ResultRow("IP", result.ipAddress)
            ResultRow("CIDR", "/${result.prefixLength}")
            ResultRow("Subnet Mask", result.subnetMask)
            ResultRow("Network Address", result.networkAddress)
            ResultRow("Broadcast Address", result.broadcastAddress)
            ResultRow(
                "Usable Range",
                "${result.usableRangeStart} - ${result.usableRangeEnd}",
            )
            ResultRow("Host Count", result.hostCount.toString())
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
