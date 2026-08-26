package com.networktoolbox.core.network.dns

import java.io.ByteArrayOutputStream

internal object DnsResponseFixtures {
    const val EXAMPLE_COM = "example.com"

    fun aResponse(
        address: ByteArray = byteArrayOf(93, 184.toByte(), 216.toByte(), 34),
        ttl: Long = 300,
    ): ByteArray = response(
        recordType = DnsRecordType.A,
        records = listOf(resourceRecord(DnsRecordType.A, ttl, address)),
    )

    fun aaaaResponse(
        address: ByteArray = byteArrayOf(
            0x20, 0x01, 0x0d, 0xb8.toByte(), 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 1,
        ),
        ttl: Long = 600,
    ): ByteArray = response(
        recordType = DnsRecordType.AAAA,
        records = listOf(resourceRecord(DnsRecordType.AAAA, ttl, address)),
    )

    fun cnameResponse(): ByteArray = response(
        recordType = DnsRecordType.CNAME,
        records = listOf(
            resourceRecord(
                type = DnsRecordType.CNAME,
                ttl = 120,
                data = encodedNameWithExamplePointer("alias"),
            ),
        ),
    )

    fun mxResponse(): ByteArray = response(
        recordType = DnsRecordType.MX,
        records = listOf(
            resourceRecord(
                type = DnsRecordType.MX,
                ttl = 900,
                data = byteArrayOf(0, 10) + encodedNameWithExamplePointer("mail"),
            ),
        ),
    )

    fun txtResponse(): ByteArray = response(
        recordType = DnsRecordType.TXT,
        records = listOf(
            resourceRecord(
                type = DnsRecordType.TXT,
                ttl = 1_800,
                data = byteArrayOf(5) + "hello".toByteArray() +
                    byteArrayOf(5) + "world".toByteArray(),
            ),
        ),
    )

    fun nxdomainResponse(): ByteArray = response(
        recordType = DnsRecordType.A,
        flags = 0x8183,
        records = emptyList(),
    )

    fun response(
        recordType: DnsRecordType,
        records: List<ByteArray>,
        flags: Int = 0x8180,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        writeUnsignedShort(output, 0x1234)
        writeUnsignedShort(output, flags)
        writeUnsignedShort(output, 1)
        writeUnsignedShort(output, records.size)
        writeUnsignedShort(output, 0)
        writeUnsignedShort(output, 0)
        writeName(output, EXAMPLE_COM)
        writeUnsignedShort(output, recordType.wireCode)
        writeUnsignedShort(output, 1)
        records.forEach { output.write(it) }
        return output.toByteArray()
    }

    fun resourceRecord(type: DnsRecordType, ttl: Long, data: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        output.write(0xc0)
        output.write(0x0c)
        writeUnsignedShort(output, type.wireCode)
        writeUnsignedShort(output, 1)
        writeUnsignedInt(output, ttl)
        writeUnsignedShort(output, data.size)
        output.write(data)
        return output.toByteArray()
    }

    fun encodedNameWithExamplePointer(prefix: String): ByteArray {
        val output = ByteArrayOutputStream()
        output.write(prefix.length)
        output.write(prefix.toByteArray())
        output.write(0xc0)
        output.write(0x0c)
        return output.toByteArray()
    }

    fun writeName(output: ByteArrayOutputStream, value: String) {
        value.split('.').forEach { label ->
            output.write(label.length)
            output.write(label.toByteArray())
        }
        output.write(0)
    }

    private fun writeUnsignedShort(output: ByteArrayOutputStream, value: Int) {
        output.write((value ushr 8) and 0xff)
        output.write(value and 0xff)
    }

    private fun writeUnsignedInt(output: ByteArrayOutputStream, value: Long) {
        output.write(((value ushr 24) and 0xff).toInt())
        output.write(((value ushr 16) and 0xff).toInt())
        output.write(((value ushr 8) and 0xff).toInt())
        output.write((value and 0xff).toInt())
    }
}
