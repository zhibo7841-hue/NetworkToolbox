package com.networktoolbox.core.common.wol

/**
 * A validated, canonical MAC address used by Wake-on-LAN.
 *
 * The value is always uppercase and colon separated. Broadcast, all-zero and
 * multicast addresses are intentionally rejected because they are not valid
 * individual Wake-on-LAN targets. Locally administered unicast addresses are
 * valid and are therefore not rejected.
 */
@JvmInline
value class MacAddress private constructor(val value: String) {
    fun toByteArray(): ByteArray = value
        .split(':')
        .map { it.toInt(16).toByte() }
        .toByteArray()

    override fun toString(): String = value

    companion object {
        private const val OCTET_COUNT = 6
        private const val OCTET_HEX_LENGTH = 2

        /** Accepts colon-separated, hyphen-separated, or plain twelve-digit hex input. */
        fun parse(input: String): MacAddress? {
            val normalizedInput = input.filterNot(Char::isWhitespace)
            if (normalizedInput.isEmpty()) return null

            val hex = when {
                ':' in normalizedInput -> {
                    if ('-' in normalizedInput) return null
                    val parts = normalizedInput.split(':')
                    if (parts.size != OCTET_COUNT || parts.any { it.length != OCTET_HEX_LENGTH }) {
                        return null
                    }
                    parts.joinToString(separator = "")
                }

                '-' in normalizedInput -> {
                    val parts = normalizedInput.split('-')
                    if (parts.size != OCTET_COUNT || parts.any { it.length != OCTET_HEX_LENGTH }) {
                        return null
                    }
                    parts.joinToString(separator = "")
                }

                else -> normalizedInput
            }

            if (hex.length != OCTET_COUNT * OCTET_HEX_LENGTH ||
                hex.any { it.digitToIntOrNull(16) == null }
            ) {
                return null
            }

            val bytes = hex.chunked(OCTET_HEX_LENGTH).map { it.toInt(16) }
            val firstOctet = bytes.first()
            if (bytes.all { it == 0 } || bytes.all { it == 0xFF } || firstOctet and 0x01 != 0) {
                return null
            }

            return MacAddress(
                bytes.joinToString(":") { byte -> "%02X".format(byte) },
            )
        }
    }
}
