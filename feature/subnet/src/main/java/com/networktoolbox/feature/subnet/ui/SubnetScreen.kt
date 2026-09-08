package com.networktoolbox.feature.subnet.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.networktoolbox.core.common.ipv4.SubnetResult
import com.networktoolbox.core.designsystem.NetworkToolAccent
import com.networktoolbox.core.designsystem.NetworkToolboxTextStyles
import com.networktoolbox.core.designsystem.PrimaryActionButton
import com.networktoolbox.core.designsystem.StatusVisualState
import com.networktoolbox.core.designsystem.ToolInputSection
import com.networktoolbox.core.designsystem.ToolResultRow
import com.networktoolbox.core.designsystem.ToolResultSection
import com.networktoolbox.core.designsystem.ToolScreenHeader
import com.networktoolbox.core.designsystem.ToolScreenLayout
import com.networktoolbox.core.designsystem.ToolStatusSummary
import com.networktoolbox.feature.subnet.presentation.SubnetUiState

@Composable
fun SubnetScreen(
    uiState: SubnetUiState,
    onInputChanged: (String) -> Unit,
    onCalculate: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolScreenLayout(modifier = modifier) {
        ToolScreenHeader(
            title = "IPv4 Subnet Calculator",
            description = "根据 IPv4 地址和 CIDR 计算网络范围",
            icon = Icons.Outlined.AccountTree,
            accent = NetworkToolAccent.CYAN,
            onBack = onBack,
        )

        ToolInputSection(title = "输入") {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = uiState.input,
                onValueChange = onInputChanged,
                label = { Text("IPv4 地址/CIDR") },
                singleLine = true,
                isError = uiState.errorMessage != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            )
            PrimaryActionButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onCalculate,
            ) {
                Text("计算")
            }
            uiState.errorMessage?.let { message ->
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        uiState.result?.let { result ->
            SubnetResultCard(result)
        }
    }
}

@Composable
private fun SubnetResultCard(result: SubnetResult) {
    ToolResultSection {
        ToolStatusSummary(
            title = "计算结果",
            status = StatusVisualState.NORMAL,
            label = "已完成",
        )
        ToolResultRow(
            "IP",
            result.ipAddress,
            valueStyle = NetworkToolboxTextStyles.TechnicalData,
        )
        ToolResultRow("CIDR", "/${result.prefixLength}")
        ToolResultRow(
            "子网掩码",
            result.subnetMask,
            valueStyle = NetworkToolboxTextStyles.TechnicalData,
        )
        ToolResultRow(
            "网络地址",
            result.networkAddress,
            valueStyle = NetworkToolboxTextStyles.TechnicalData,
        )
        ToolResultRow(
            "广播地址",
            result.broadcastAddress,
            valueStyle = NetworkToolboxTextStyles.TechnicalData,
        )
        ToolResultRow(
            "可用范围",
            "${result.usableRangeStart} - ${result.usableRangeEnd}",
            valueStyle = NetworkToolboxTextStyles.TechnicalData,
        )
        ToolResultRow("主机数量", result.hostCount.toString())
    }
}
