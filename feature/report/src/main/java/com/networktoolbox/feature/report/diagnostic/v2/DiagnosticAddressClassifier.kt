package com.networktoolbox.feature.report.diagnostic.v2

internal object DiagnosticAddressClassifier {
    /** RFC 2544 benchmarking space often used by local Fake-IP resolvers. */
    fun isFakeIp(value: String): Boolean {
        val octets = value.substringBefore('/').split('.')
        if (octets.size != 4) return false
        val numbers = octets.mapNotNull { it.toIntOrNull() }
        if (numbers.size != 4 || numbers.any { it !in 0..255 }) return false
        return numbers[0] == 198 && numbers[1] in 18..19
    }
}
