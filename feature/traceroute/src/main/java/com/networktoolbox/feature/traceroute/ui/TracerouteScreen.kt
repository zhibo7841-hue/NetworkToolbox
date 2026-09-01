package com.networktoolbox.feature.traceroute.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.networktoolbox.core.network.traceroute.TracerouteHop
import com.networktoolbox.core.network.traceroute.TracerouteHopStatus
import com.networktoolbox.core.network.traceroute.TracerouteProbeResult
import com.networktoolbox.core.network.traceroute.TracerouteResult
import com.networktoolbox.core.network.traceroute.TracerouteStatus
import com.networktoolbox.feature.traceroute.presentation.TraceroutePresentationMapper
import com.networktoolbox.feature.traceroute.presentation.TracerouteUiState
import com.networktoolbox.feature.traceroute.presentation.TracerouteUiStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TracerouteScreen(
    uiState: TracerouteUiState,
    onTargetChanged: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRunning = uiState.status is TracerouteUiStatus.Running

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("路由追踪") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = contentPadding.calculateTopPadding() + 4.dp,
                end = 20.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Traceroute", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "查看数据包经过的 IPv4 网络路径。",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!isRunning) {
                item {
                    TargetInputCard(
                        target = uiState.targetInput,
                        errorMessage = (uiState.status as? TracerouteUiStatus.Error)?.message,
                        onTargetChanged = onTargetChanged,
                        onStart = onStart,
                    )
                }
                item {
                    Text(
                        "检测在本机完成，不会上传网络数据。当前阶段仅支持 IPv4。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            when (val status = uiState.status) {
                TracerouteUiStatus.Idle -> Unit
                is TracerouteUiStatus.Running -> {
                    item {
                        RunningCard(status, onStop)
                    }
                    if (status.hops.isNotEmpty()) {
                        item { SectionTitle("实时路径") }
                        items(status.hops, key = { it.hopNumber }) { hop ->
                            HopRow(hop)
                        }
                    }
                }

                is TracerouteUiStatus.Completed -> {
                    item { ResultCard(status.result, status.presentation) }
                    if (status.result.hops.isNotEmpty()) {
                        item { SectionTitle("路由路径") }
                        items(status.result.hops, key = { it.hopNumber }) { hop ->
                            HopRow(hop)
                        }
                    }
                }

                is TracerouteUiStatus.Cancelled -> item {
                    MessageCard(
                        title = "追踪已停止",
                        message = "本次路由追踪已取消，可以重新开始。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is TracerouteUiStatus.Error -> item {
                    MessageCard(
                        title = "无法开始追踪",
                        message = status.message,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun TargetInputCard(
    target: String,
    errorMessage: String?,
    onTargetChanged: (String) -> Unit,
    onStart: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("目标地址或域名", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = target,
                onValueChange = onTargetChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("IPv4 地址或域名") },
                placeholder = { Text("例如：1.1.1.1 或 example.com") },
                singleLine = true,
                isError = errorMessage != null,
            )
            errorMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("开始追踪")
            }
        }
    }
}

@Composable
private fun RunningCard(status: TracerouteUiStatus.Running, onStop: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("正在追踪", style = MaterialTheme.typography.titleLarge)
            Text(status.target, fontWeight = FontWeight.Medium)
            status.resolvedAddress?.let {
                Text(
                    "解析地址：$it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(
                progress = { (status.hops.size / 30f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("已获取 ${status.hops.size} 跳", style = MaterialTheme.typography.bodyMedium)
            Text(
                "正在等待后续路径结果。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                Text("停止追踪")
            }
        }
    }
}

@Composable
private fun ResultCard(
    result: TracerouteResult,
    presentation: com.networktoolbox.feature.traceroute.presentation.TracerouteResultPresentation,
) {
    val statusColor = when (result.status) {
        TracerouteStatus.REACHED -> Color(0xFF2E7D32)
        TracerouteStatus.PARTIAL,
        TracerouteStatus.NETWORK_CHANGED,
        TracerouteStatus.CANCELLED,
        TracerouteStatus.RUNNING -> MaterialTheme.colorScheme.primary
        TracerouteStatus.FAILED -> MaterialTheme.colorScheme.error
    }
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(presentation.heading, style = MaterialTheme.typography.titleLarge)
            Text(
                "${result.targetInput} · ${presentation.statusLabel}",
                style = MaterialTheme.typography.titleMedium,
                color = statusColor,
                fontWeight = FontWeight.SemiBold,
            )
            result.resolvedAddress?.let {
                DetailLine("解析地址", it)
            }
            result.durationMs?.let {
                DetailLine("耗时", formatDuration(it))
            }
            Text(presentation.summary, style = MaterialTheme.typography.bodyLarge)
            presentation.explanation?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            presentation.notice?.let {
                HorizontalDivider()
                Text(
                    "提示：$it",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MessageCard(title: String, message: String, color: Color) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = color)
            Text(message, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun HopRow(hop: TracerouteHop) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                hop.hopNumber.toString(),
                modifier = Modifier.width(32.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(hop.address ?: "*", style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    probeSlots(hop).forEach { probe ->
                        Text(
                            probe?.let {
                                TraceroutePresentationMapper.probeText(it.status, it.latencyMs)
                            } ?: "*",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                when {
                    hop.address == null -> "无响应"
                    hop.status == TracerouteHopStatus.DESTINATION_REACHED -> "目标"
                    else -> "响应"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun probeSlots(hop: TracerouteHop): List<TracerouteProbeResult?> =
    hop.probes.take(3) + List((3 - hop.probes.size).coerceAtLeast(0)) { null }

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            modifier = Modifier.weight(0.35f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(value, modifier = Modifier.weight(0.65f))
    }
}

private fun formatDuration(durationMs: Long): String = when {
    durationMs < 1_000L -> "$durationMs ms"
    else -> "${(durationMs / 1_000.0f).formatOneDecimal()} 秒"
}

private fun Float.formatOneDecimal(): String = String.format(java.util.Locale.US, "%.1f", this)
