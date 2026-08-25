package com.networktoolbox.feature.dns.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.networktoolbox.core.network.dns.DnsMethod
import com.networktoolbox.core.network.dns.DnsRecord
import com.networktoolbox.core.network.dns.DnsRecordType
import com.networktoolbox.core.network.dns.DnsResult
import com.networktoolbox.feature.dns.presentation.DnsStatus
import com.networktoolbox.feature.dns.presentation.DnsUiState

@Composable
fun DnsScreen(
    uiState: DnsUiState,
    onDomainChanged: (String) -> Unit,
    onLookup: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLoading = uiState.status is DnsStatus.Loading

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TextButton(onClick = onBack, enabled = !isLoading) {
                Text("返回工具")
            }
            Text(
                text = "DNS Lookup",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "检查域名解析结果",
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
                        value = uiState.domainInput,
                        onValueChange = onDomainChanged,
                        label = { Text("域名") },
                        singleLine = true,
                        enabled = !isLoading,
                        isError = uiState.status.isInvalidInput(),
                    )
                    if (uiState.status.isInvalidInput()) {
                        Text(
                            "输入无效。请输入有效域名。",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onLookup,
                        enabled = !isLoading,
                    ) {
                        Text(if (isLoading) "检测中..." else "查询")
                    }
                }
            }

            when (val status = uiState.status) {
                DnsStatus.Idle -> Unit
                is DnsStatus.Loading -> LoadingMessage()
                is DnsStatus.Success -> DnsResultCard(status.result)
                is DnsStatus.Error -> DnsResultCard(status.result)
            }
        }
    }
}

@Composable
private fun DnsResultCard(result: DnsResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("结果", style = MaterialTheme.typography.titleMedium)
            ResultRow("域名", result.domain.ifBlank { "未知" })
            ResultRow("状态", if (result.success) "已完成" else "失败")
            ResultRow("检测方式", result.method.displayName())
            ResultRow("技术方法", result.method.name)
            ResultRow("耗时", result.durationMs?.let { "$it ms" } ?: "未知")
            RecordSection("A 记录", DnsRecordType.A, result.records)
            RecordSection("AAAA 记录", DnsRecordType.AAAA, result.records)
            result.errorMessage
                ?.takeIf { it.isNotBlank() }
                ?.let { errorMessage ->
                    ResultRow("Reason", errorMessage)
                    errorMessage.toExplanation()?.let { explanation ->
                        ResultRow("说明", explanation)
                    }
                }
        }
    }
}

@Composable
private fun LoadingMessage() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            modifier = Modifier.padding(16.dp),
            text = "检测中...",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun RecordSection(
    title: String,
    type: DnsRecordType,
    records: List<DnsRecord>,
) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    val values = records.filter { it.type == type }.map { it.value }
    if (values.isEmpty()) {
        Text("未检测到", style = MaterialTheme.typography.bodyMedium)
    } else {
        values.forEach { value ->
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            modifier = Modifier.weight(0.4f),
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            modifier = Modifier.weight(0.6f),
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun DnsMethod.displayName(): String = when (this) {
    DnsMethod.SYSTEM_RESOLVER -> "系统解析器"
    DnsMethod.UNAVAILABLE -> "不可用"
}

private fun DnsStatus.isInvalidInput(): Boolean =
    this is DnsStatus.Error && result.errorMessage == "Invalid domain."

private fun String.toExplanation(): String? = when (this) {
    "Invalid domain." -> "请输入有效域名。"
    "Domain could not be resolved." -> "当前域名无法解析，请检查网络或 DNS 配置。"
    "No A or AAAA records found." -> "未找到 A 或 AAAA 记录。"
    "System resolver is unavailable.", "DNS lookup unavailable." ->
        "系统解析器暂时不可用。"
    else -> null
}
