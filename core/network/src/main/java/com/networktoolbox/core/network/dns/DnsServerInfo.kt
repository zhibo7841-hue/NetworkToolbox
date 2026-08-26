package com.networktoolbox.core.network.dns

data class DnsServerInfo(
    val configuredAddresses: List<String> = emptyList(),
    val privateDnsActive: Boolean? = null,
    val privateDnsServerName: String? = null,
    val actualResponder: String? = null,
)
