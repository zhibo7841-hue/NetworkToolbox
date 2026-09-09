package com.networktoolbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import com.networktoolbox.core.designsystem.NetworkToolAccent
import com.networktoolbox.core.designsystem.NetworkToolboxSpacing
import com.networktoolbox.core.designsystem.SettingsInfoRow
import com.networktoolbox.core.designsystem.SettingsRow
import com.networktoolbox.core.designsystem.SettingsSection
import com.networktoolbox.core.designsystem.ToolIconContainer
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
                .padding(
                    start = NetworkToolboxSpacing.XL,
                    top = NetworkToolboxSpacing.LG,
                    end = NetworkToolboxSpacing.XL,
                    bottom = NetworkToolboxSpacing.XXL,
                ),
            verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.LG),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.XS)) {
                Text(
                    SettingsPresentation.screenTitle,
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    SettingsPresentation.screenDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingsSection(SettingsPresentation.aboutSectionTitle) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = NetworkToolboxSpacing.XS),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.MD),
                ) {
                    ToolIconContainer(
                        icon = Icons.Outlined.Lan,
                        accent = NetworkToolAccent.PRIMARY,
                        contentDescription = SettingsPresentation.appName,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.XS),
                    ) {
                        Text(
                            SettingsPresentation.appName,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            SettingsPresentation.appDescription,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider()
                SettingsInfoRow(
                    title = SettingsPresentation.versionTitle,
                    supportingText = SettingsPresentation.versionSupport,
                    trailingValue = SettingsPresentation.versionValue(BuildConfig.VERSION_NAME),
                )
            }

            SettingsSection(SettingsPresentation.dataSectionTitle) {
                SettingsInfoRow(
                    title = SettingsPresentation.historyTitle,
                    supportingText = SettingsPresentation.historySupport,
                )
                HorizontalDivider()
                SettingsRow(
                    title = SettingsPresentation.clearHistoryTitle,
                    supportingText = SettingsPresentation.clearHistorySupport,
                    icon = Icons.Outlined.DeleteOutline,
                    trailingValue = SettingsPresentation.clearActionLabel(isClearing),
                    destructive = true,
                    enabled = !isClearing,
                    onClick = { showClearDialog = true },
                )
                if (historyUiState is HistoryUiState.Error) {
                    Text(
                        "状态：${historyUiState.message}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            SettingsSection(SettingsPresentation.privacySectionTitle) {
                SettingsInfoRow(
                    title = SettingsPresentation.localFirstTitle,
                    supportingText = SettingsPresentation.localFirstSupport,
                )
                HorizontalDivider()
                SettingsInfoRow(
                    title = SettingsPresentation.privacyTitle,
                    supportingText = SettingsPresentation.privacySupport,
                )
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(SettingsPresentation.clearDialogTitle) },
            text = { Text(SettingsPresentation.clearDialogText) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        onClearHistory()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(SettingsPresentation.clearHistoryLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(SettingsPresentation.cancelLabel)
                }
            },
        )
    }
}
