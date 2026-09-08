package com.networktoolbox.feature.traceroute.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.networktoolbox.core.designsystem.DestructiveActionButton
import com.networktoolbox.core.designsystem.NetworkToolAccent
import com.networktoolbox.core.designsystem.NetworkToolboxComponentShapes
import com.networktoolbox.core.designsystem.NetworkToolboxSpacing
import com.networktoolbox.core.designsystem.NetworkToolboxTextStyles
import com.networktoolbox.core.designsystem.OutlinedNetworkCard
import com.networktoolbox.core.designsystem.PrimaryActionButton
import com.networktoolbox.core.designsystem.StatusVisualState
import com.networktoolbox.core.designsystem.ToolInputSection
import com.networktoolbox.core.designsystem.ToolResultRow
import com.networktoolbox.core.designsystem.ToolResultSection
import com.networktoolbox.core.designsystem.ToolRunningSection
import com.networktoolbox.core.designsystem.ToolScreenHeader
import com.networktoolbox.core.designsystem.ToolScreenLazyLayout
import com.networktoolbox.core.designsystem.ToolStatusSummary
import com.networktoolbox.core.network.traceroute.TracerouteAddressFamily
import com.networktoolbox.core.network.traceroute.TracerouteHop
import com.networktoolbox.core.network.traceroute.TracerouteProbeResult
import com.networktoolbox.core.network.traceroute.TracerouteResult
import com.networktoolbox.core.network.traceroute.TracerouteStatus
import com.networktoolbox.feature.traceroute.presentation.TraceroutePresentationMapper
import com.networktoolbox.feature.traceroute.presentation.TracerouteResultPresentation
import com.networktoolbox.feature.traceroute.presentation.TracerouteUiState
import com.networktoolbox.feature.traceroute.presentation.TracerouteUiStatus

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

    ToolScreenLazyLayout(modifier = modifier) {
        item {
            ToolScreenHeader(
                title = "Traceroute",
                description = "查看数据包经过的 IPv4 网络路径",
                icon = Icons.Outlined.AccountTree,
                accent = NetworkToolAccent.CYAN,
                onBack = onBack,
                backEnabled = !isRunning,
            )
        }

        if (!isRunning) {
            item {
                TargetInputSection(
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
                item { RunningCard(status, onStop) }
                if (status.hops.isNotEmpty()) {
                    tracerouteHopList("实时路径", status.hops)
                }
            }

            is TracerouteUiStatus.Completed -> {
                item { ResultCard(status.result, status.presentation) }
                if (status.result.hops.isNotEmpty()) {
                    tracerouteHopList("路由路径", status.result.hops)
                }
            }

            is TracerouteUiStatus.Cancelled -> {
                item {
                    MessageCard(
                        title = "追踪已停止",
                        message = TraceroutePresentationMapper.cancelledSummary(status.hops.size),
                        status = StatusVisualState.CANCELLED,
                    )
                }
                tracerouteHopList("已获取路径", status.hops)
            }

            is TracerouteUiStatus.Error -> item {
                MessageCard(
                    title = "无法开始追踪",
                    message = status.message,
                    status = StatusVisualState.ERROR,
                )
            }
        }
    }
}

private fun LazyListScope.tracerouteHopList(
    title: String,
    hops: List<TracerouteHop>,
) {
    if (hops.isEmpty()) return

    item { SectionTitle(title) }
    items(hops, key = { it.hopNumber }) { hop ->
        HopRow(hop)
    }
}

@Composable
private fun TargetInputSection(
    target: String,
    errorMessage: String?,
    onTargetChanged: (String) -> Unit,
    onStart: () -> Unit,
) {
    ToolInputSection(title = "目标") {
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
        PrimaryActionButton(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("开始追踪")
        }
    }
}

@Composable
private fun RunningCard(status: TracerouteUiStatus.Running, onStop: () -> Unit) {
    ToolRunningSection {
        ToolStatusSummary(
            title = "正在追踪",
            status = StatusVisualState.RUNNING,
            label = "检测中",
        )
        Text(status.target, style = NetworkToolboxTextStyles.TechnicalData)
        status.resolvedAddress?.let {
            ToolResultRow(
                "解析地址",
                it,
                valueStyle = NetworkToolboxTextStyles.TechnicalData,
            )
        }
        TraceroutePresentationMapper.fakeIpNotice(status.resolvedAddress)?.let {
            Text(
                "提示：$it",
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
        DestructiveActionButton(
            onClick = onStop,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("停止追踪")
        }
    }
}

@Composable
private fun ResultCard(
    result: TracerouteResult,
    presentation: TracerouteResultPresentation,
) {
    ToolResultSection {
        ToolStatusSummary(
            title = presentation.heading,
            status = result.status.statusVisualState(),
            label = presentation.statusLabel,
        )
        ToolResultRow(
            "目标",
            result.targetInput,
            valueStyle = NetworkToolboxTextStyles.TechnicalData,
        )
        result.resolvedAddress?.let {
            ToolResultRow(
                "解析地址",
                it,
                valueStyle = NetworkToolboxTextStyles.TechnicalData,
            )
        }
        ToolResultRow("探测协议", result.addressFamily.displayName())
        result.durationMs?.let {
            ToolResultRow("耗时", formatDuration(it))
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

@Composable
private fun MessageCard(
    title: String,
    message: String,
    status: StatusVisualState,
) {
    OutlinedNetworkCard {
        ToolStatusSummary(title = title, status = status)
        Text(message, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun HopRow(hop: TracerouteHop) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = NetworkToolboxComponentShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NetworkToolboxSpacing.MD, vertical = NetworkToolboxSpacing.SM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                hop.hopNumber.toString(),
                modifier = Modifier.width(28.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.XS),
            ) {
                Text(
                    TraceroutePresentationMapper.hopAddress(hop),
                    style = NetworkToolboxTextStyles.TechnicalData,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM)) {
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
            TraceroutePresentationMapper.hopStatusLabel(hop)?.let { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun probeSlots(hop: TracerouteHop): List<TracerouteProbeResult?> =
    hop.probes.take(3) + List((3 - hop.probes.size).coerceAtLeast(0)) { null }

private fun TracerouteStatus.statusVisualState(): StatusVisualState = when (this) {
    TracerouteStatus.REACHED -> StatusVisualState.NORMAL
    TracerouteStatus.PARTIAL,
    TracerouteStatus.NETWORK_CHANGED,
    -> StatusVisualState.NOTICE

    TracerouteStatus.CANCELLED -> StatusVisualState.CANCELLED
    TracerouteStatus.FAILED -> StatusVisualState.ERROR
    TracerouteStatus.RUNNING -> StatusVisualState.RUNNING
}

private fun TracerouteAddressFamily.displayName(): String = when (this) {
    TracerouteAddressFamily.IPV4 -> "IPv4"
    TracerouteAddressFamily.IPV6 -> "IPv6"
}

private fun formatDuration(durationMs: Long): String = when {
    durationMs < 1_000L -> "$durationMs ms"
    else -> "${(durationMs / 1_000.0f).formatOneDecimal()} 秒"
}

private fun Float.formatOneDecimal(): String = String.format(java.util.Locale.US, "%.1f", this)
