package com.networktoolbox.core.network.data

/** A route gateway candidate extracted from Android's LinkProperties. */
internal data class DefaultGatewayCandidate(
    val address: String,
    val isIpv4: Boolean,
)

/**
 * Selects a stable diagnostic gateway without depending on route list order.
 * IPv4 is preferred because the current gateway probe cannot carry an IPv6
 * link-local zone/scope through NetworkContext reliably.
 */
internal object DefaultGatewaySelector {
    fun select(candidates: List<DefaultGatewayCandidate>): String? {
        val validCandidates = candidates.filter { it.address.isNotBlank() }
        return validCandidates.firstOrNull { it.isIpv4 }?.address
            ?: validCandidates.firstOrNull()?.address
    }
}
