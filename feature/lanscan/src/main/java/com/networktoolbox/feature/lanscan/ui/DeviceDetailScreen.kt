package com.networktoolbox.feature.lanscan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.networktoolbox.core.common.favorites.DeviceDisplayNameResolver
import com.networktoolbox.core.common.wol.MacAddress
import com.networktoolbox.core.common.wol.WakeOnLanConfig
import com.networktoolbox.feature.lanscan.presentation.WakeOnLanAvailability
import com.networktoolbox.core.designsystem.NetworkToolboxSpacing
import com.networktoolbox.core.designsystem.NetworkToolboxTextStyles
import com.networktoolbox.core.designsystem.OutlinedNetworkCard
import com.networktoolbox.core.designsystem.PrimaryActionButton
import com.networktoolbox.core.designsystem.SecondaryActionButton
import com.networktoolbox.core.designsystem.SecondaryInformationHeader
import com.networktoolbox.core.designsystem.ToolResultRow
import com.networktoolbox.core.designsystem.ToolScreenLayout
import com.networktoolbox.feature.lanscan.presentation.DeviceDetailPresentation
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DeviceDetailScreen(
    detail: DeviceDetailPresentation?,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSaveCustomName: (String) -> Unit = {},
    onRestoreAutomaticName: () -> Unit = {},
    favoriteErrorMessage: String? = null,
    customNameErrorMessage: String? = null,
    onSaveWakeOnLan: (String, String) -> Unit = { _, _ -> },
    onSendWakeOnLan: () -> Unit = {},
    wakeOnLanActionMessage: String? = null,
    wakeOnLanActionErrorMessage: String? = null,
    modifier: Modifier = Modifier,
) {
    var showNameDialog by rememberSaveable(detail?.detailKey) { mutableStateOf(false) }
    var showWakeOnLanDialog by rememberSaveable(detail?.detailKey) { mutableStateOf(false) }
    var draftName by rememberSaveable(detail?.detailKey) {
        mutableStateOf(detail?.customName.orEmpty())
    }
    var draftWakeOnLanMac by rememberSaveable(detail?.detailKey) {
        mutableStateOf(detail?.wakeOnLan?.config?.macAddress?.toString().orEmpty())
    }
    var draftWakeOnLanPort by rememberSaveable(detail?.detailKey) {
        mutableStateOf(
            detail?.wakeOnLan?.config?.udpPort?.toString()
                ?: WakeOnLanConfig.DEFAULT_UDP_PORT.toString(),
        )
    }
    LaunchedEffect(detail?.detailKey, detail?.customName) {
        draftName = detail?.customName.orEmpty()
    }
    LaunchedEffect(detail?.detailKey, detail?.wakeOnLan?.config) {
        draftWakeOnLanMac = detail?.wakeOnLan?.config?.macAddress?.toString().orEmpty()
        draftWakeOnLanPort = detail?.wakeOnLan?.config?.udpPort?.toString()
            ?: WakeOnLanConfig.DEFAULT_UDP_PORT.toString()
    }

    ToolScreenLayout(modifier = modifier) {
        SecondaryInformationHeader(
            title = stringResource(com.networktoolbox.feature.lanscan.R.string.device_detail_title),
            onBack = onBack,
            trailingContent = {
                if (detail != null) {
                    Row {
                        IconButton(onClick = { showNameDialog = true }) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = stringResource(
                                    com.networktoolbox.feature.lanscan.R.string.device_detail_edit_name_description,
                                ),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (detail.canToggleFavorite) {
                            IconButton(onClick = onToggleFavorite) {
                                Icon(
                                    imageVector = if (detail.isFavorite) {
                                        Icons.Filled.Star
                                    } else {
                                        Icons.Outlined.StarBorder
                                    },
                                    contentDescription = detail.favoriteToggleContentDescription,
                                    tint = if (detail.isFavorite) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                }
            },
        )

        if (detail == null) {
            OutlinedNetworkCard {
                Text(
                    stringResource(com.networktoolbox.feature.lanscan.R.string.device_detail_unavailable_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(com.networktoolbox.feature.lanscan.R.string.device_detail_unavailable_message),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@ToolScreenLayout
        }

        OutlinedNetworkCard {
            Text(detail.displayName, style = MaterialTheme.typography.headlineSmall)
            detail.ipAddress?.takeIf(String::isNotBlank)?.let { ip ->
                Text(ip, style = NetworkToolboxTextStyles.TechnicalData)
            }
            detail.role?.takeIf(String::isNotBlank)?.let { role ->
                Text(
                    role,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Text(
                detail.favoriteStatusLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            favoriteErrorMessage?.let { message ->
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            customNameErrorMessage?.let { message ->
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        DeviceDetailSection(title = "基本信息") {
            detail.ipAddress?.takeIf(String::isNotBlank)?.let { ToolResultRow("IPv4", it) }
            detail.macAddress?.takeIf(String::isNotBlank)?.let { ToolResultRow("MAC", it) }
            detail.vendor?.takeIf(String::isNotBlank)?.let { ToolResultRow("厂商", it) }
            detail.model?.takeIf(String::isNotBlank)?.let { ToolResultRow("型号", it) }
        }

        if (detail.hostname != null || detail.mdnsNames.isNotEmpty() || detail.upnpNames.isNotEmpty()) {
            DeviceDetailSection(title = "设备身份") {
                detail.hostname?.takeIf(String::isNotBlank)?.let { ToolResultRow("主机名", it) }
                if (detail.mdnsNames.isNotEmpty()) {
                    ToolResultRow("mDNS", detail.mdnsNames.joinToString("\n"))
                }
                if (detail.upnpNames.isNotEmpty()) {
                    ToolResultRow("UPnP", detail.upnpNames.joinToString("\n"))
                }
            }
        }

        DeviceDetailSection(title = "网络关系") {
            detail.role?.takeIf(String::isNotBlank)?.let { ToolResultRow("角色", it) }
            detail.networkScope?.takeIf(String::isNotBlank)?.let { ToolResultRow("范围", it) }
        }

        DeviceDetailSection(title = stringResource(com.networktoolbox.feature.lanscan.R.string.wol_section_title)) {
            detail.wakeOnLan.config?.let { config ->
                ToolResultRow(
                    stringResource(com.networktoolbox.feature.lanscan.R.string.wol_mac_label),
                    config.macAddress.toString(),
                )
                ToolResultRow(
                    stringResource(com.networktoolbox.feature.lanscan.R.string.wol_udp_port_label),
                    stringResource(
                        com.networktoolbox.feature.lanscan.R.string.wol_udp_port_value,
                        config.udpPort,
                    ),
                )
            } ?: Text(
                stringResource(com.networktoolbox.feature.lanscan.R.string.wol_not_configured),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(com.networktoolbox.feature.lanscan.R.string.wol_section_helper),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            wakeOnLanStatusMessage(detail.wakeOnLan.availability)?.let { message ->
                Text(
                    message,
                    color = if (detail.wakeOnLan.availability == WakeOnLanAvailability.SCOPE_MISMATCH) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            wakeOnLanActionMessage?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.primary)
            }
            wakeOnLanActionErrorMessage?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error)
            }
            SecondaryActionButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showWakeOnLanDialog = true },
                enabled = detail.wakeOnLan.canConfigure,
            ) {
                Text(
                    stringResource(
                        if (detail.wakeOnLan.config == null) {
                            com.networktoolbox.feature.lanscan.R.string.wol_configure
                        } else {
                            com.networktoolbox.feature.lanscan.R.string.wol_edit
                        },
                    ),
                )
            }
            if (detail.wakeOnLan.config != null) {
                PrimaryActionButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onSendWakeOnLan,
                    enabled = detail.wakeOnLan.canSend,
                ) {
                    Text(stringResource(com.networktoolbox.feature.lanscan.R.string.wol_send))
                }
            }
        }

        DeviceDetailSection(title = "观察状态") {
            ToolResultRow("本次扫描", if (detail.observedThisScan) "已发现" else "本次未发现")
            detail.lastSeenAt?.takeIf { it > 0L }?.let { timestamp ->
                ToolResultRow("最近发现", formatTimestamp(timestamp))
            }
        }

        if (showNameDialog) {
            val nameValidation = DeviceDisplayNameResolver.validateCustomName(draftName)
            AlertDialog(
                onDismissRequest = { showNameDialog = false },
                title = { Text(stringResource(com.networktoolbox.feature.lanscan.R.string.device_name_title)) },
                text = {
                    OutlinedTextField(
                        value = draftName,
                        onValueChange = { draftName = it },
                        placeholder = {
                            Text(stringResource(com.networktoolbox.feature.lanscan.R.string.device_name_placeholder))
                        },
                        supportingText = {
                            if (draftName.isNotEmpty() && nameValidation.isFailure) {
                                Text(stringResource(com.networktoolbox.feature.lanscan.R.string.device_name_invalid))
                            } else {
                                Text(stringResource(com.networktoolbox.feature.lanscan.R.string.device_name_helper))
                            }
                        },
                        isError = draftName.isNotEmpty() && nameValidation.isFailure,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = nameValidation.isSuccess,
                        onClick = {
                            onSaveCustomName(nameValidation.getOrThrow())
                            showNameDialog = false
                        },
                    ) {
                        Text(stringResource(com.networktoolbox.feature.lanscan.R.string.device_name_save))
                    }
                },
                dismissButton = {
                    Row {
                        if (detail.customName != null) {
                            TextButton(
                                onClick = {
                                    onRestoreAutomaticName()
                                    showNameDialog = false
                                },
                            ) {
                                Text(stringResource(com.networktoolbox.feature.lanscan.R.string.device_name_restore))
                            }
                        }
                        TextButton(onClick = { showNameDialog = false }) {
                            Text(stringResource(com.networktoolbox.feature.lanscan.R.string.device_name_cancel))
                        }
                    }
                },
            )
        }

        if (showWakeOnLanDialog) {
            val macValidation = MacAddress.parse(draftWakeOnLanMac)
            val port = draftWakeOnLanPort.trim().toIntOrNull()
            val portValid = port in WakeOnLanConfig.MIN_UDP_PORT..WakeOnLanConfig.MAX_UDP_PORT
            AlertDialog(
                onDismissRequest = { showWakeOnLanDialog = false },
                title = {
                    Text(
                        stringResource(
                            if (detail.wakeOnLan.config == null) {
                                com.networktoolbox.feature.lanscan.R.string.wol_dialog_title_configure
                            } else {
                                com.networktoolbox.feature.lanscan.R.string.wol_dialog_title_edit
                            },
                        ),
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM)) {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = draftWakeOnLanMac,
                            onValueChange = { draftWakeOnLanMac = it },
                            label = {
                                Text(stringResource(com.networktoolbox.feature.lanscan.R.string.wol_mac_label))
                            },
                            placeholder = {
                                Text(stringResource(com.networktoolbox.feature.lanscan.R.string.wol_mac_placeholder))
                            },
                            supportingText = {
                                Text(
                                    if (draftWakeOnLanMac.isNotBlank() && macValidation == null) {
                                        stringResource(com.networktoolbox.feature.lanscan.R.string.wol_mac_invalid)
                                    } else {
                                        stringResource(com.networktoolbox.feature.lanscan.R.string.wol_mac_helper)
                                    },
                                )
                            },
                            isError = draftWakeOnLanMac.isNotBlank() && macValidation == null,
                            singleLine = true,
                        )
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = draftWakeOnLanPort,
                            onValueChange = { draftWakeOnLanPort = it },
                            label = {
                                Text(stringResource(com.networktoolbox.feature.lanscan.R.string.wol_udp_port_label))
                            },
                            supportingText = {
                                if (draftWakeOnLanPort.isNotBlank() && !portValid) {
                                    Text(stringResource(com.networktoolbox.feature.lanscan.R.string.wol_port_invalid))
                                }
                            },
                            isError = draftWakeOnLanPort.isNotBlank() && !portValid,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = macValidation != null && portValid,
                        onClick = {
                            onSaveWakeOnLan(draftWakeOnLanMac, draftWakeOnLanPort)
                            showWakeOnLanDialog = false
                        },
                    ) {
                        Text(stringResource(com.networktoolbox.feature.lanscan.R.string.wol_dialog_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showWakeOnLanDialog = false }) {
                        Text(stringResource(com.networktoolbox.feature.lanscan.R.string.wol_dialog_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun DeviceDetailSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    OutlinedNetworkCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Column(
            verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
            content = content,
        )
    }
}

private fun formatTimestamp(timestamp: Long): String = DateFormat
    .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
    .format(Date(timestamp))

@Composable
private fun wakeOnLanStatusMessage(availability: WakeOnLanAvailability): String? = when (availability) {
    WakeOnLanAvailability.NOT_CONFIGURED,
    WakeOnLanAvailability.AVAILABLE,
    -> null

    WakeOnLanAvailability.NO_ACTIVE_NETWORK -> stringResource(
        com.networktoolbox.feature.lanscan.R.string.wol_no_active_network,
    )
    WakeOnLanAvailability.UNSUPPORTED_NETWORK -> stringResource(
        com.networktoolbox.feature.lanscan.R.string.wol_unsupported_network,
    )
    WakeOnLanAvailability.NO_IPV4 -> stringResource(
        com.networktoolbox.feature.lanscan.R.string.wol_no_ipv4,
    )
    WakeOnLanAvailability.SCOPE_MISMATCH -> stringResource(
        com.networktoolbox.feature.lanscan.R.string.wol_scope_mismatch,
    )
    WakeOnLanAvailability.BROADCAST_UNAVAILABLE -> stringResource(
        com.networktoolbox.feature.lanscan.R.string.wol_no_broadcast,
    )
}
