package com.networktoolbox.feature.dns.ui

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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(onClick = onBack, enabled = !isLoading) {
                Text("返回 Dashboard")
            }
            Text(
                text = "DNS Lookup",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "查询域名的 A 和 AAAA 记录",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = uiState.domainInput,
                onValueChange = onDomainChanged,
                label = { Text("Domain") },
                singleLine = true,
                enabled = !isLoading,
                isError = uiState.status.isInvalidInput(),
            )
            Button(
                onClick = onLookup,
                enabled = !isLoading,
            ) {
                Text(if (isLoading) "正在查询..." else "Lookup")
            }

            when (val status = uiState.status) {
                DnsStatus.Idle -> Unit
                is DnsStatus.Loading -> Text("正在查询...")
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
            Text("DNS Result", style = MaterialTheme.typography.titleLarge)
            ResultRow("域名", result.domain.ifBlank { "未知" })
            ResultRow("状态", if (result.success) "成功" else "失败")
            ResultRow("查询方式", result.method.displayName())
            ResultRow("Query Method", result.method.name)
            ResultRow("解析耗时", result.durationMs?.let { "$it ms" } ?: "未知")
            RecordSection("A Records", DnsRecordType.A, result.records)
            RecordSection("AAAA Records", DnsRecordType.AAAA, result.records)
            result.errorMessage
                ?.takeIf { it.isNotBlank() }
                ?.let { errorMessage ->
                    ResultRow("原因", errorMessage.toDisplayError())
                }
            Spacer(modifier = Modifier.height(2.dp))
        }
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
    DnsMethod.SYSTEM_RESOLVER -> "System Resolver"
    DnsMethod.UNAVAILABLE -> "不可用"
}

private fun DnsStatus.isInvalidInput(): Boolean =
    this is DnsStatus.Error && result.errorMessage == "Invalid domain."

private fun String.toDisplayError(): String =
    if (this == "Invalid domain.") "输入无效。" else this
