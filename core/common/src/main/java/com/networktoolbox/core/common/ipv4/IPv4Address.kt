package com.networktoolbox.core.common.ipv4

class IPv4Address internal constructor(
    val value: Long,
) {
    fun toDottedDecimal(): String = listOf(
        (value shr 24) and OCTET_MASK,
        (value shr 16) and OCTET_MASK,
        (value shr 8) and OCTET_MASK,
        value and OCTET_MASK,
    ).joinToString(".")

    companion object {
        private const val OCTET_MASK = 0xFFL

        fun parse(input: String): IPv4Address? {
            val octets = input.trim().split('.')
            if (octets.size != 4) return null

            var value = 0L
            for (octetText in octets) {
                if (octetText.isEmpty() || octetText.any { it !in '0'..'9' }) {
                    return null
                }

                val octet = octetText.toLongOrNull() ?: return null
                if (octet !in 0..255) return null
                value = (value shl 8) or octet
            }

            return IPv4Address(value)
        }
    }
}
