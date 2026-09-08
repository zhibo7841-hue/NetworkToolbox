package com.networktoolbox.feature.dns.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.networktoolbox.core.designsystem.NetworkCard
import com.networktoolbox.core.designsystem.NetworkToolAccent
import com.networktoolbox.core.designsystem.NetworkToolboxSpacing
import com.networktoolbox.core.designsystem.NetworkToolboxTextStyles
import com.networktoolbox.core.designsystem.NetworkStatusChip
import com.networktoolbox.core.designsystem.PrimaryActionButton
import com.networktoolbox.core.designsystem.StatusVisualState
import com.networktoolbox.core.designsystem.ToolInputSection
import com.networktoolbox.core.designsystem.ToolResultRow
import com.networktoolbox.core.designsystem.ToolResultSection
import com.networktoolbox.core.designsystem.ToolScreenHeader
import com.networktoolbox.core.designsystem.ToolScreenLayout
import com.networktoolbox.core.designsystem.ToolStatusSummary
import com.networktoolbox.core.network.dns.DnsLookupResult
import com.networktoolbox.core.network.dns.DnsLookupStatus
import com.networktoolbox.core.network.dns.DnsQueryMethod
import com.networktoolbox.core.network.dns.DnsRecord
import com.networktoolbox.core.network.dns.DnsRecordType
import com.networktoolbox.feature.dns.domain.IpAddressClassification
import com.networktoolbox.feature.dns.domain.IpAddressKind
import com.networktoolbox.feature.dns.presentation.DnsStatus
import com.networktoolbox.feature.dns.presentation.DnsUiState

@Composable
fun DnsScreen(
    uiState: DnsUiState,
    onDomainChanged: (String) -> Unit,
    onLookup: () -> Unit,
    onAdvancedSettingsToggle: () -> Unit,
    onRecordTypeToggle: (DnsRecordType) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLoading = uiState.status is DnsStatus.Loading
    val isInvalidInput = uiState.status.isInvalidInput()

    ToolScreenLayout(modifier = modifier) {
        ToolScreenHeader(
            title = "DNS Lookup",
            description = "检查域名解析结果",
            icon = Icons.Outlined.Dns,
            accent = NetworkToolAccent.CYAN,
            onBack = onBack,
            backEnabled = !isLoading,
        )

        ToolInputSection(title = "域名") {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = uiState.domainInput,
                onValueChange = onDomainChanged,
                placeholder = { Text("例如 www.baidu.com") },
                singleLine = true,
                enabled = !isLoading,
                isError = isInvalidInput,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(
                    onSearch = { if (!isLoading) onLookup() },
                ),
            )
            if (isInvalidInput) {
                Text(
                    "域名格式不正确，请检查输入。",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            PrimaryActionButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onLookup,
                enabled = !isLoading,
            ) {
                Text(if (isLoading) "正在查询 DNS…" else "查询")
            }
            TextButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onAdvancedSettingsToggle,
                enabled = !isLoading,
            ) {
                Text(if (uiState.advancedSettingsExpanded) "收起高级设置" else "高级设置 >")
            }
            if (uiState.advancedSettingsExpanded) {
                AdvancedSettings(
                    selectedRecordTypes = uiState.selectedRecordTypes,
                    onRecordTypeToggle = onRecordTypeToggle,
                )
            }
        }

        when (val status = uiState.status) {
            DnsStatus.Idle -> Unit
            is DnsStatus.Loading -> LoadingMessage(status.domain)
            is DnsStatus.Success -> DnsResultCard(
                result = status.result,
                addressClassifications = status.addressClassifications,
            )
            is DnsStatus.Error -> DnsResultCard(
                result = status.result,
                addressClassifications = status.addressClassifications,
            )
        }
    }
}

@Composable
private fun AdvancedSettings(
    selectedRecordTypes: Set<DnsRecordType>,
    onRecordTypeToggle: (DnsRecordType) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM)) {
        Text("记录类型", style = MaterialTheme.typography.titleSmall)
        DnsRecordType.entries.chunked(3).forEach { types ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
            ) {
                types.forEach { type ->
                    FilterChip(
                        selected = type in selectedRecordTypes,
                        onClick = { onRecordTypeToggle(type) },
                        label = { Text(type.displayName()) },
                    )
                }
            }
        }
        Text("DNS 服务器", style = MaterialTheme.typography.titleSmall)
        Text(
            text = "系统 DNS",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DnsResultCard(
    result: DnsLookupResult,
    addressClassifications: List<IpAddressClassification>,
) {
    var showDetails by rememberSaveable(result.queryName, result.endTime) {
        mutableStateOf(false)
    }
    val isCompleted = result.status == DnsLookupStatus.SUCCESS ||
        result.status == DnsLookupStatus.PARTIAL ||
        result.status == DnsLookupStatus.NO_RECORDS

    ToolResultSection {
        ToolStatusSummary(
            title = "DNS 结果",
            status = result.status.statusVisualState(),
            label = result.status.headline(),
        )
        ToolResultRow(
            "域名",
            result.queryName.ifBlank { "未知" },
            valueStyle = NetworkToolboxTextStyles.TechnicalData,
        )
        ToolResultRow(
            "查询耗时",
            result.durationMs?.let { "$it ms" } ?: "未知",
        )
        Text(
            text = result.status.description(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        AddressRecordSection(
            title = "IPv4",
            type = DnsRecordType.A,
            records = result.records,
        )
        AddressRecordSection(
            title = "IPv6",
            type = DnsRecordType.AAAA,
            records = result.records,
        )

        addressClassifications.forEach { classification ->
            SpecialAddressNotice(classification)
        }

        if (result.errorMessage != null && !isCompleted) {
            Text(
                text = result.status.errorDescription(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        TextButton(onClick = { showDetails = !showDetails }) {
            Text(if (showDetails) "收起详细信息" else "查看详细信息 >")
        }
        if (showDetails) {
            HorizontalDivider()
            DnsDetails(result)
        }
    }
}

@Composable
private fun AddressRecordSection(
    title: String,
    type: DnsRecordType,
    records: List<DnsRecord>,
) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    val matchingRecords = records.filter { record -> record.type == type }
    if (matchingRecords.isEmpty()) {
        Text(
            "无记录",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        matchingRecords.forEach { record ->
            RecordValue(record.value, record.ttlSeconds)
        }
    }
}

@Composable
private fun RecordValue(
    value: String,
    ttlSeconds: Long?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.XS)) {
        Text(value, style = NetworkToolboxTextStyles.TechnicalData)
        ttlSeconds?.let {
            Text(
                "TTL $it 秒",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SpecialAddressNotice(classification: IpAddressClassification) {
    Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.XS)) {
        NetworkStatusChip(
            status = StatusVisualState.NOTICE,
            label = "特殊用途地址",
        )
        Text(
            text = classification.address,
            style = NetworkToolboxTextStyles.TechnicalData,
        )
        Text(
            text = classification.kind.description(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DnsDetails(result: DnsLookupResult) {
    Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM)) {
        Text("查询信息", style = MaterialTheme.typography.titleSmall)
        ToolResultRow(
            "域名",
            result.queryName,
            valueStyle = NetworkToolboxTextStyles.TechnicalData,
        )
        ToolResultRow(
            "查询类型",
            result.requestedTypes.joinToString(" / ") { it.displayName() },
        )
        ToolResultRow("解析方式", result.method.displayName())
        ToolResultRow("查询耗时", result.durationMs?.let { "$it ms" } ?: "未知")

        Text("DNS 环境", style = MaterialTheme.typography.titleSmall)
        Text("网络配置 DNS", style = MaterialTheme.typography.bodyMedium)
        val server = result.server
        if (server?.configuredAddresses.isNullOrEmpty()) {
            Text(
                "未知",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.XS)) {
                server?.configuredAddresses.orEmpty().forEach { address ->
                    Text(address, style = NetworkToolboxTextStyles.TechnicalData)
                }
            }
        }
        server?.privateDnsActive?.let { active ->
            ToolResultRow("Private DNS", if (active) "已启用" else "未启用")
        }
        server?.privateDnsServerName?.let { name ->
            ToolResultRow("Private DNS 名称", name)
        }

        Text("DNS 记录", style = MaterialTheme.typography.titleSmall)
        if (result.records.isEmpty()) {
            Text(
                "无记录",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            result.records.forEach { record -> DnsRecordDetails(record) }
        }
    }
}

@Composable
private fun DnsRecordDetails(record: DnsRecord) {
    Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.XS)) {
        Text(record.type.displayName(), style = MaterialTheme.typography.titleSmall)
        ToolResultRow("名称", record.name.ifBlank { "未知" })
        ToolResultRow(
            "值",
            record.value,
            valueStyle = NetworkToolboxTextStyles.TechnicalData,
        )
        record.ttlSeconds?.let { ttl -> ToolResultRow("TTL", "$ttl 秒") }
        record.priority?.let { priority -> ToolResultRow("优先级", priority.toString()) }
        if (record.txtSegments.size > 1) {
            ToolResultRow("文本分段", record.txtSegments.joinToString(" | "))
        }
    }
}

@Composable
private fun LoadingMessage(domain: String) {
    NetworkCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        ToolStatusSummary(
            title = "正在查询",
            status = StatusVisualState.RUNNING,
            label = "查询中",
        )
        Text(
            domain.ifBlank { "请输入域名" },
            style = NetworkToolboxTextStyles.TechnicalData,
        )
    }
}

private fun DnsLookupStatus.statusVisualState(): StatusVisualState = when (this) {
    DnsLookupStatus.SUCCESS -> StatusVisualState.NORMAL
    DnsLookupStatus.NO_RECORDS -> StatusVisualState.NOTICE
    DnsLookupStatus.PARTIAL,
    DnsLookupStatus.NXDOMAIN,
    -> StatusVisualState.WARNING

    DnsLookupStatus.TIMEOUT,
    DnsLookupStatus.NETWORK_ERROR,
    DnsLookupStatus.INVALID_RESPONSE,
    DnsLookupStatus.INVALID_QUERY,
    DnsLookupStatus.FAILED,
    -> StatusVisualState.ERROR
}

private fun DnsLookupStatus.headline(): String = when (this) {
    DnsLookupStatus.SUCCESS -> "解析成功"
    DnsLookupStatus.PARTIAL -> "部分解析成功"
    DnsLookupStatus.NO_RECORDS -> "查询完成，无记录"
    DnsLookupStatus.NXDOMAIN -> "域名不存在"
    DnsLookupStatus.TIMEOUT -> "DNS 查询超时"
    DnsLookupStatus.NETWORK_ERROR -> "网络异常"
    DnsLookupStatus.INVALID_QUERY -> "域名格式不正确"
    DnsLookupStatus.INVALID_RESPONSE -> "DNS 响应无效"
    DnsLookupStatus.FAILED -> "查询失败"
}

private fun DnsLookupStatus.description(): String = when (this) {
    DnsLookupStatus.SUCCESS -> "域名解析正常。"
    DnsLookupStatus.PARTIAL -> "部分记录解析正常，其他所选记录没有返回结果。"
    DnsLookupStatus.NO_RECORDS -> "查询完成，但没有找到所选记录。"
    DnsLookupStatus.NXDOMAIN -> "DNS 服务器返回该域名不存在，请检查域名是否输入正确。"
    DnsLookupStatus.TIMEOUT -> "当前 DNS 服务未在规定时间内响应。"
    DnsLookupStatus.NETWORK_ERROR -> "当前网络无法完成 DNS 查询。"
    DnsLookupStatus.INVALID_QUERY -> "请输入有效的域名。"
    DnsLookupStatus.INVALID_RESPONSE -> "收到的 DNS 响应无法识别。"
    DnsLookupStatus.FAILED -> "无法解析该域名，可能是 DNS 服务或网络暂时不可用。"
}

private fun DnsLookupStatus.errorDescription(): String = when (this) {
    DnsLookupStatus.NXDOMAIN -> "可能原因：域名不存在，或当前 DNS 服务报告该域名不存在。"
    DnsLookupStatus.TIMEOUT -> "请稍后重试，并检查当前网络或 DNS 服务是否可用。"
    DnsLookupStatus.NETWORK_ERROR -> "请检查当前网络连接。"
    DnsLookupStatus.INVALID_RESPONSE -> "请稍后重试；当前响应可能不完整或无法识别。"
    DnsLookupStatus.INVALID_QUERY -> "请检查域名格式。"
    else -> "请稍后重试。"
}

private fun DnsQueryMethod.displayName(): String = when (this) {
    DnsQueryMethod.ANDROID_DNS_RESOLVER -> "系统 DNS"
    DnsQueryMethod.SYSTEM_RESOLVER_ADDRESSES_ONLY -> "系统解析器（仅地址）"
    DnsQueryMethod.UNAVAILABLE -> "不可用"
}

private fun DnsRecordType.displayName(): String = when (this) {
    DnsRecordType.A -> "A"
    DnsRecordType.AAAA -> "AAAA"
    DnsRecordType.CNAME -> "CNAME"
    DnsRecordType.MX -> "MX"
    DnsRecordType.TXT -> "TXT"
}

private fun IpAddressKind.description(): String = when (this) {
    IpAddressKind.FAKE_IP_RANGE ->
        "该结果位于 198.18.0.0/15 地址段。此地址段常被代理软件的 Fake-IP 模式使用，因此该地址可能不是域名的真实公网地址。"
    IpAddressKind.RFC1918_PRIVATE ->
        "该地址属于 RFC1918 私有地址范围，通常用于本地网络。"
    IpAddressKind.LOOPBACK ->
        "该地址属于回环地址，仅指向本机。"
    IpAddressKind.LINK_LOCAL ->
        "该地址属于链路本地地址，通常只在本地链路有效。"
    IpAddressKind.IPV6_ULA ->
        "该地址属于 IPv6 ULA 私有地址范围，通常用于本地网络。"
    IpAddressKind.IPV6_LINK_LOCAL ->
        "该地址属于 IPv6 链路本地地址，通常只在本地链路有效。"
}

private fun DnsStatus.isInvalidInput(): Boolean =
    this is DnsStatus.Error && result.status == DnsLookupStatus.INVALID_QUERY
