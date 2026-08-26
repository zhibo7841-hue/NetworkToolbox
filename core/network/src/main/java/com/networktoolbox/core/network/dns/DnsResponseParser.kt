package com.networktoolbox.core.network.dns

class DnsResponseParser {
    fun parse(
        response: ByteArray,
        queryName: String,
        recordType: DnsRecordType,
    ): DnsParsedResponse {
        return try {
            Parser(response, queryName, recordType).parse()
        } catch (error: DnsParseException) {
            DnsParsedResponse(
                status = DnsLookupStatus.INVALID_RESPONSE,
                records = emptyList(),
                errorMessage = error.message ?: "Invalid DNS response.",
            )
        } catch (error: Exception) {
            DnsParsedResponse(
                status = DnsLookupStatus.INVALID_RESPONSE,
                records = emptyList(),
                errorMessage = error.message ?: "Invalid DNS response.",
            )
        }
    }

    data class DnsParsedResponse(
        val status: DnsLookupStatus,
        val records: List<DnsRecord>,
        val errorMessage: String?,
    )

    private class Parser(
        private val data: ByteArray,
        private val expectedQueryName: String,
        private val expectedRecordType: DnsRecordType,
    ) {
        private val cursor = Cursor()

        fun parse(): DnsParsedResponse {
            require(data.size >= DNS_HEADER_LENGTH) { "DNS header is truncated." }

            cursor.readUnsignedShort() // Transaction ID is validated by the transport.
            val flags = cursor.readUnsignedShort()
            val questionCount = cursor.readUnsignedShort()
            val answerCount = cursor.readUnsignedShort()
            val authorityCount = cursor.readUnsignedShort()
            val additionalCount = cursor.readUnsignedShort()

            require((flags and FLAG_RESPONSE) != 0) { "DNS message is not a response." }
            require(questionCount == 1) { "DNS response must contain one question." }
            require(totalRecordCount(answerCount, authorityCount, additionalCount) <= MAX_RECORD_COUNT) {
                "DNS response contains too many records."
            }

            val question = cursor.readName()
            val questionType = cursor.readUnsignedShort()
            val questionClass = cursor.readUnsignedShort()
            require(questionClass == DNS_CLASS_IN) { "Unsupported DNS question class." }
            require(sameName(question.value, expectedQueryName)) {
                "DNS question name does not match the request."
            }
            require(questionType == expectedRecordType.wireCode) {
                "DNS question type does not match the request."
            }

            val responseCode = flags and RESPONSE_CODE_MASK
            if (responseCode == RESPONSE_CODE_NXDOMAIN) {
                return DnsParsedResponse(
                    status = DnsLookupStatus.NXDOMAIN,
                    records = emptyList(),
                    errorMessage = "DNS response reported NXDOMAIN.",
                )
            }
            if (responseCode != RESPONSE_CODE_NO_ERROR) {
                return DnsParsedResponse(
                    status = DnsLookupStatus.FAILED,
                    records = emptyList(),
                    errorMessage = "DNS response returned RCODE $responseCode.",
                )
            }
            if ((flags and FLAG_TRUNCATED) != 0) {
                return DnsParsedResponse(
                    status = DnsLookupStatus.FAILED,
                    records = emptyList(),
                    errorMessage = "DNS response was truncated; TCP fallback is not available.",
                )
            }

            val records = buildList {
                repeat(answerCount) {
                    val resourceRecord = cursor.readResourceRecord()
                    if (resourceRecord.recordClass == DNS_CLASS_IN && shouldRead(resourceRecord.type)) {
                        add(parseRecord(resourceRecord))
                    }
                }
                repeat(authorityCount + additionalCount) {
                    cursor.skipResourceRecord()
                }
            }

            return if (records.isEmpty()) {
                DnsParsedResponse(
                    status = DnsLookupStatus.NO_RECORDS,
                    records = emptyList(),
                    errorMessage = "No requested DNS records found.",
                )
            } else {
                DnsParsedResponse(
                    status = DnsLookupStatus.SUCCESS,
                    records = records,
                    errorMessage = null,
                )
            }
        }

        private fun shouldRead(type: Int): Boolean = type == expectedRecordType.wireCode ||
            (type == DnsRecordType.CNAME.wireCode &&
                expectedRecordType in setOf(DnsRecordType.A, DnsRecordType.AAAA))

        private fun parseRecord(record: RawResourceRecord): DnsRecord = when (record.type) {
            DnsRecordType.A.wireCode -> {
                require(record.dataLength == IPV4_LENGTH) { "Invalid A record length." }
                DnsRecord(
                    type = DnsRecordType.A,
                    name = record.name,
                    value = data.copyOfRange(record.dataOffset, record.dataEnd)
                        .joinToString(".") { byte -> (byte.toInt() and 0xff).toString() },
                    ttl = record.ttlSeconds,
                )
            }

            DnsRecordType.AAAA.wireCode -> {
                require(record.dataLength == IPV6_LENGTH) { "Invalid AAAA record length." }
                DnsRecord(
                    type = DnsRecordType.AAAA,
                    name = record.name,
                    value = formatIpv6(data.copyOfRange(record.dataOffset, record.dataEnd)),
                    ttl = record.ttlSeconds,
                )
            }

            DnsRecordType.CNAME.wireCode -> {
                val target = readName(record.dataOffset)
                require(target.nextOffset == record.dataEnd) { "Invalid CNAME record data." }
                DnsRecord(
                    type = DnsRecordType.CNAME,
                    name = record.name,
                    value = target.value,
                    ttl = record.ttlSeconds,
                )
            }

            DnsRecordType.MX.wireCode -> {
                require(record.dataLength >= MX_PRIORITY_LENGTH) { "Invalid MX record length." }
                val priority = readUnsignedShortAt(record.dataOffset)
                val exchange = readName(record.dataOffset + MX_PRIORITY_LENGTH)
                require(exchange.nextOffset == record.dataEnd) { "Invalid MX record data." }
                DnsRecord(
                    type = DnsRecordType.MX,
                    name = record.name,
                    value = exchange.value,
                    ttl = record.ttlSeconds,
                    priority = priority,
                )
            }

            DnsRecordType.TXT.wireCode -> {
                val segments = readTxtSegments(record.dataOffset, record.dataEnd)
                DnsRecord(
                    type = DnsRecordType.TXT,
                    name = record.name,
                    value = segments.joinToString(separator = ""),
                    ttl = record.ttlSeconds,
                    txtSegments = segments,
                )
            }

            else -> throw DnsParseException("Unsupported DNS record type.")
        }

        private fun readTxtSegments(start: Int, end: Int): List<String> {
            val segments = mutableListOf<String>()
            var offset = start
            while (offset < end) {
                val length = readUnsignedByteAt(offset)
                offset++
                val segmentEnd = checkedEnd(offset, length)
                require(segmentEnd <= end) { "TXT segment exceeds record length." }
                segments += data.copyOfRange(offset, segmentEnd)
                    .toString(Charsets.ISO_8859_1)
                offset = segmentEnd
            }
            return segments
        }

        private fun readName(start: Int): NameRead {
            require(start in data.indices) { "DNS name starts outside the response." }
            val labels = mutableListOf<String>()
            val visitedPointers = mutableSetOf<Int>()
            var offset = start
            var nextOffset = -1
            var jumped = false
            var jumpCount = 0
            var encodedLength = 0

            while (true) {
                require(offset in data.indices) { "DNS name exceeds the response." }
                val length = readUnsignedByteAt(offset)
                when {
                    length == 0 -> {
                        if (!jumped) nextOffset = offset + 1
                        return NameRead(
                            value = labels.joinToString("."),
                            nextOffset = nextOffset,
                        )
                    }

                    (length and POINTER_MASK) == POINTER_MASK -> {
                        require(offset + 1 < data.size) { "DNS name pointer is truncated." }
                        val pointer = ((length and POINTER_OFFSET_MASK) shl 8) or
                            readUnsignedByteAt(offset + 1)
                        require(pointer < data.size) { "DNS name pointer is out of bounds." }
                        require(visitedPointers.add(pointer)) { "DNS name pointer contains a loop." }
                        if (!jumped) {
                            nextOffset = offset + 2
                            jumped = true
                        }
                        offset = pointer
                        jumpCount++
                        require(jumpCount <= data.size) { "DNS name has too many pointers." }
                    }

                    (length and POINTER_MASK) != 0 -> {
                        throw DnsParseException("Invalid DNS label length.")
                    }

                    else -> {
                        val labelEnd = checkedEnd(offset + 1, length)
                        require(labelEnd <= data.size) { "DNS label exceeds the response." }
                        encodedLength += length + 1
                        require(encodedLength <= MAX_NAME_LENGTH) { "DNS name is too long." }
                        labels += data.copyOfRange(offset + 1, labelEnd)
                            .toString(Charsets.US_ASCII)
                        offset = labelEnd
                    }
                }
            }
        }

        private fun sameName(left: String, right: String): Boolean =
            left.trimEnd('.').equals(right.trimEnd('.'), ignoreCase = true)

        private fun totalRecordCount(answer: Int, authority: Int, additional: Int): Int =
            answer + authority + additional

        private fun formatIpv6(address: ByteArray): String {
            val groups = IntArray(8) { index ->
                ((address[index * 2].toInt() and 0xff) shl 8) or
                    (address[index * 2 + 1].toInt() and 0xff)
            }
            var bestStart = -1
            var bestLength = 0
            var index = 0
            while (index < groups.size) {
                if (groups[index] != 0) {
                    index++
                    continue
                }
                val start = index
                while (index < groups.size && groups[index] == 0) index++
                val length = index - start
                if (length >= 2 && length > bestLength) {
                    bestStart = start
                    bestLength = length
                }
            }

            val result = StringBuilder()
            index = 0
            while (index < groups.size) {
                if (index == bestStart) {
                    result.append("::")
                    index += bestLength
                    if (index == groups.size) break
                } else {
                    if (result.isNotEmpty() && result.last() != ':') result.append(':')
                    result.append(groups[index].toString(16))
                    index++
                }
            }
            return result.toString()
        }

        private fun checkedEnd(start: Int, length: Int): Int {
            require(start >= 0 && length >= 0) { "Invalid DNS data range." }
            val end = start.toLong() + length.toLong()
            require(end <= data.size) { "DNS data exceeds the response." }
            return end.toInt()
        }

        private fun readUnsignedShortAt(offset: Int): Int {
            require(offset >= 0 && offset + 1 < data.size) { "DNS integer is truncated." }
            return (readUnsignedByteAt(offset) shl 8) or readUnsignedByteAt(offset + 1)
        }

        private fun readUnsignedByteAt(offset: Int): Int {
            require(offset in data.indices) { "DNS byte is outside the response." }
            return data[offset].toInt() and 0xff
        }

        private fun Cursor.readName(): NameRead {
            val name = readName(offset)
            offset = name.nextOffset
            return name
        }

        private fun Cursor.readResourceRecord(): RawResourceRecord {
            val name = readName()
            val type = readUnsignedShort()
            val recordClass = readUnsignedShort()
            val ttlSeconds = readUnsignedInt()
            val dataLength = readUnsignedShort()
            val dataOffset = offset
            offset = checkedEnd(dataOffset, dataLength)
            return RawResourceRecord(
                name = name.value,
                type = type,
                recordClass = recordClass,
                ttlSeconds = ttlSeconds,
                dataOffset = dataOffset,
                dataLength = dataLength,
            )
        }

        private fun Cursor.skipResourceRecord() {
            readResourceRecord()
        }

        private inner class Cursor {
            var offset: Int = 0

            fun readUnsignedShort(): Int {
                val value = readUnsignedShortAt(offset)
                offset += 2
                return value
            }

            fun readUnsignedInt(): Long {
                val high = readUnsignedShort().toLong()
                val low = readUnsignedShort().toLong()
                return (high shl 16) or low
            }
        }

        private data class NameRead(
            val value: String,
            val nextOffset: Int,
        )

        private data class RawResourceRecord(
            val name: String,
            val type: Int,
            val recordClass: Int,
            val ttlSeconds: Long,
            val dataOffset: Int,
            val dataLength: Int,
        ) {
            val dataEnd: Int get() = dataOffset + dataLength
        }
    }

    private class DnsParseException(message: String) : IllegalArgumentException(message)

    private companion object {
        const val DNS_HEADER_LENGTH = 12
        const val DNS_CLASS_IN = 1
        const val IPV4_LENGTH = 4
        const val IPV6_LENGTH = 16
        const val MX_PRIORITY_LENGTH = 2
        const val FLAG_RESPONSE = 0x8000
        const val FLAG_TRUNCATED = 0x0200
        const val RESPONSE_CODE_MASK = 0x000f
        const val RESPONSE_CODE_NO_ERROR = 0
        const val RESPONSE_CODE_NXDOMAIN = 3
        const val POINTER_MASK = 0xc0
        const val POINTER_OFFSET_MASK = 0x3f
        const val MAX_NAME_LENGTH = 253
        const val MAX_RECORD_COUNT = 1024
    }
}

typealias DnsParsedResponse = DnsResponseParser.DnsParsedResponse
