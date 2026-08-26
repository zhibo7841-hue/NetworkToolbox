package com.networktoolbox.core.network.dns

internal object DnsNameValidator {
    fun normalize(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || trimmed.any(Char::isWhitespace)) return null

        val withoutRootDot = trimmed.removeSuffix(".")
        if (withoutRootDot.isEmpty() || withoutRootDot.length > MAX_NAME_LENGTH) return null

        val labels = withoutRootDot.split('.')
        if (labels.any { label -> !isValidLabel(label) }) return null
        return withoutRootDot
    }

    private fun isValidLabel(label: String): Boolean {
        if (label.isEmpty() || label.length > MAX_LABEL_LENGTH) return false
        if (label.first() == '-' || label.last() == '-') return false
        return label.all { character ->
            character in 'a'..'z' ||
                character in 'A'..'Z' ||
                character in '0'..'9' ||
                character == '-'
        }
    }

    private const val MAX_LABEL_LENGTH = 63
    private const val MAX_NAME_LENGTH = 253
}
