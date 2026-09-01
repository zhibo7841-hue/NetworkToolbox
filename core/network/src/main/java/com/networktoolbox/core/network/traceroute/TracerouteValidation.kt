package com.networktoolbox.core.network.traceroute

object TracerouteValidation {
    fun validate(request: TracerouteRequest): String? {
        val target = request.target.trim()
        if (target.isEmpty()) return "Traceroute target must not be empty."
        if (target.any(Char::isWhitespace) || target.any(Char::isISOControl)) {
            return "Traceroute target contains unsupported characters."
        }
        if (request.addressFamily != TracerouteAddressFamily.IPV4) {
            return "Only IPv4 traceroute is supported in Phase 1."
        }
        if (request.maxHops !in 1..TracerouteRequest.DEFAULT_MAX_HOPS) {
            return "Maximum hops must be between 1 and 30."
        }
        if (request.probesPerHop !in 1..TracerouteRequest.DEFAULT_PROBES_PER_HOP) {
            return "Probes per hop must be between 1 and 3."
        }
        if (request.timeoutMs !in MIN_TIMEOUT_MS..MAX_TIMEOUT_MS) {
            return "Probe timeout must be between 100 and 5000 ms."
        }
        if (request.destinationPort !in 1..MAX_PORT) {
            return "Destination port must be between 1 and 65535."
        }
        if (request.destinationPort == 53 || request.destinationPort == 123) {
            return "Traceroute must use a high UDP destination port."
        }
        val lastProbePort = request.destinationPort +
            (request.maxHops * request.probesPerHop) - 1
        if (lastProbePort > MAX_PORT) {
            return "Destination port is too high for the configured probe range."
        }
        if (target.contains(':')) {
            return "IPv6 traceroute is not supported in Phase 1."
        }
        if (normalizeIpv4Literal(target) == null && target.all { it.isDigit() || it == '.' }) {
            return "Invalid IPv4 address or hostname."
        }
        return null
    }

    fun normalizeIpv4Literal(value: String): String? {
        val parts = value.split('.')
        if (parts.size != 4 || parts.any { it.isEmpty() || it.length > 3 }) return null
        if (parts.any { part -> part.any { character -> !character.isDigit() } }) return null
        if (parts.any { part -> part.toIntOrNull() !in 0..255 }) return null
        return parts.joinToString(".") { it.toInt().toString() }
    }

    fun isValidHostname(value: String): Boolean {
        if (normalizeIpv4Literal(value) != null) return false
        if (value.length > MAX_HOSTNAME_LENGTH || value.endsWith('.')) return false
        val labels = value.split('.')
        if (labels.any { it.isEmpty() || it.length > MAX_LABEL_LENGTH }) return false
        if (labels.any { it.first() == '-' || it.last() == '-' }) return false
        if (labels.any { label -> label.any { character ->
                !(character.isLetterOrDigit() || character == '-')
            } }) {
            return false
        }
        return true
    }

    private const val MAX_PORT = 65_535
    private const val MIN_TIMEOUT_MS = 100
    private const val MAX_TIMEOUT_MS = 5_000
    private const val MAX_HOSTNAME_LENGTH = 253
    private const val MAX_LABEL_LENGTH = 63
}

object TracerouteFakeIpDetector {
    fun isFakeIp(value: String): Boolean {
        val address = TracerouteValidation.normalizeIpv4Literal(value.substringBefore('/'))
            ?: return false
        val firstTwo = address.split('.').take(2).map(String::toInt)
        return firstTwo[0] == 198 && firstTwo[1] in 18..19
    }
}
