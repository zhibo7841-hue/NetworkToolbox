package com.networktoolbox.core.common.diagnostic

/** Opaque, comparable network identity; calculation belongs to the platform adapter. */
data class NetworkFingerprint(val value: String) {
    init {
        requireBoundedText(value, "network fingerprint", 256)
    }
}
data class DiagnosticNetworkSummary(
    val connectionType: DiagnosticConnectionType,
    val localAddressSummary: List<String> = emptyList(),
    val prefixLength: Int? = null,
    val gateway: String? = null,
    val configuredDnsServers: List<String> = emptyList(),
    val vpnActive: Boolean? = null,
    val privateDnsActive: Boolean? = null,
    val privateDnsServerName: String? = null,
    val validated: Boolean? = null,
) {
    init {
        requireBoundedList(localAddressSummary.size, "local addresses", 16)
        requireBoundedList(configuredDnsServers.size, "configured DNS servers", 16)
        localAddressSummary.forEach { address ->
            requireBoundedText(address, "local address", 128)
        }
        configuredDnsServers.forEach { address ->
            requireBoundedText(address, "configured DNS server", 128)
        }
        gateway?.let { requireBoundedText(it, "gateway", 128) }
        privateDnsServerName?.let { requireBoundedText(it, "Private DNS name", 256) }
        require(prefixLength == null || prefixLength in 0..128) {
            "Prefix length must be between 0 and 128."
        }
    }
}
