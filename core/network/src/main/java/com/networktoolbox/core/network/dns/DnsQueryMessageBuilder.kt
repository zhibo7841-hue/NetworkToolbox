package com.networktoolbox.core.network.dns

import java.io.ByteArrayOutputStream

internal object DnsQueryMessageBuilder {
    fun build(
        queryName: String,
        recordType: DnsRecordType,
        transactionId: Int,
    ): ByteArray {
        val normalizedName = DnsNameValidator.normalize(queryName)
            ?: throw IllegalArgumentException("Invalid DNS query name.")
        val output = ByteArrayOutputStream()

        writeUnsignedShort(output, transactionId)
        writeUnsignedShort(output, RECURSION_DESIRED_FLAGS)
        writeUnsignedShort(output, 1)
        writeUnsignedShort(output, 0)
        writeUnsignedShort(output, 0)
        writeUnsignedShort(output, 0)

        normalizedName.split('.').forEach { label ->
            output.write(label.length)
            output.write(label.toByteArray(Charsets.US_ASCII))
        }
        output.write(0)
        writeUnsignedShort(output, recordType.wireCode)
        writeUnsignedShort(output, DNS_CLASS_IN)
        return output.toByteArray()
    }

    private fun writeUnsignedShort(output: ByteArrayOutputStream, value: Int) {
        output.write((value ushr 8) and 0xff)
        output.write(value and 0xff)
    }

    private const val RECURSION_DESIRED_FLAGS = 0x0100
    private const val DNS_CLASS_IN = 1
}

internal val DnsRecordType.wireCode: Int
    get() = when (this) {
        DnsRecordType.A -> 1
        DnsRecordType.AAAA -> 28
        DnsRecordType.CNAME -> 5
        DnsRecordType.MX -> 15
        DnsRecordType.TXT -> 16
    }
