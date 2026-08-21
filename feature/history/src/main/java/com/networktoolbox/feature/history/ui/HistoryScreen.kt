package com.networktoolbox.feature.history.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryType
import com.networktoolbox.feature.history.presentation.HistoryUiState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(
    uiState: HistoryUiState,
    onLoad: () -> Unit,
    onDelete: (Long) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showClearDialog by rememberSaveable { mutableStateOf(false) }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TextButton(onClick = onBack, enabled = uiState !is HistoryUiState.Loading) {
                Text("返回 Dashboard")
            }
            Text("History", style = MaterialTheme.typography.headlineSmall)
            Text(
                "查看本机保存的网络检测记录。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when (val state = uiState) {
                HistoryUiState.Loading -> StatusCard("Loading...")
                HistoryUiState.Empty -> EmptyHistoryCard()
                is HistoryUiState.Error -> ErrorCard(state.message, onLoad)
                is HistoryUiState.Success -> {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showClearDialog = true },
                    ) {
                        Text("Clear History")
                    }
                    state.records.forEach { record ->
                        HistoryRecordCard(record = record, onDelete = onDelete)
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Delete all history?") },
            text = { Text("This removes all locally stored detection history.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        onClear()
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun HistoryRecordCard(
    record: HistoryRecord,
    onDelete: (Long) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(record.type.displayName(), style = MaterialTheme.typography.titleMedium)
                Text(
                    record.timestamp.toDisplayTime(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(record.title, style = MaterialTheme.typography.bodyLarge)
            Text(record.summary, style = MaterialTheme.typography.bodyMedium)
            TextButton(
                modifier = Modifier.align(Alignment.End),
                onClick = { onDelete(record.id) },
            ) {
                Text("Delete")
            }
        }
    }
}

@Composable
private fun EmptyHistoryCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("No history yet", style = MaterialTheme.typography.titleMedium)
            Text("Run a network check to create records.")
        }
    }
}

@Composable
private fun StatusCard(status: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(status, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onRetry: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Status: Failed", style = MaterialTheme.typography.titleMedium)
            Text(message)
            TextButton(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

private fun HistoryType.displayName(): String = when (this) {
    HistoryType.PING -> "Ping"
    HistoryType.DNS -> "DNS Lookup"
    HistoryType.TCP -> "TCP Port Check"
    HistoryType.REPORT -> "Network Diagnostic"
    HistoryType.UNKNOWN -> "Other"
}

private fun Long.toDisplayTime(): String {
    val zone = ZoneId.systemDefault()
    val dateTime = Instant.ofEpochMilli(this).atZone(zone)
    val today = LocalDate.now(zone)
    val time = DateTimeFormatter.ofPattern("HH:mm").format(dateTime)

    return when (dateTime.toLocalDate()) {
        today -> "Today $time"
        today.minusDays(1) -> "Yesterday $time"
        else -> DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(dateTime)
    }
}
