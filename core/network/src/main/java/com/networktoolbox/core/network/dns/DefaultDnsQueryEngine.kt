package com.networktoolbox.core.network.dns

import java.util.Locale
import kotlinx.coroutines.CancellationException

class DefaultDnsQueryEngine(
    private val transport: DnsRawQueryTransport,
    private val parser: DnsResponseParser = DnsResponseParser(),
    private val serverInfoProvider: DnsServerInfoProvider = DnsServerInfoProvider { null },
    private val queryMethod: DnsQueryMethod = DnsQueryMethod.ANDROID_DNS_RESOLVER,
    private val currentTimeMillis: () -> Long = { System.currentTimeMillis() },
    private val nanoTime: () -> Long = { System.nanoTime() },
) : DnsQueryEngine {
    override suspend fun lookup(request: DnsLookupRequest): DnsLookupResult {
        val normalizedName = DnsNameValidator.normalize(request.queryName)
        val startedAt = currentTimeMillis()
        val startedNanos = nanoTime()

        if (normalizedName == null) {
            return result(
                queryName = request.queryName.trim(),
                requestedTypes = request.recordTypes,
                server = null,
                method = DnsQueryMethod.UNAVAILABLE,
                status = DnsLookupStatus.INVALID_QUERY,
                durationMs = null,
                startTime = startedAt,
                endTime = currentTimeMillis(),
                errorMessage = "Invalid domain.",
            )
        }
        if (request.recordTypes.isEmpty() || request.timeoutMs <= 0) {
            return result(
                queryName = normalizedName,
                requestedTypes = request.recordTypes,
                server = null,
                method = DnsQueryMethod.UNAVAILABLE,
                status = DnsLookupStatus.INVALID_QUERY,
                durationMs = null,
                startTime = startedAt,
                endTime = currentTimeMillis(),
                errorMessage = "Invalid DNS lookup request.",
            )
        }

        val outcomes = mutableListOf<QueryOutcome>()
        try {
            request.recordTypes.forEach { recordType ->
                val outcome = try {
                    val response = transport.query(
                        queryName = normalizedName,
                        recordType = recordType,
                        timeoutMs = request.timeoutMs,
                    )
                    val parsed = parser.parse(response, normalizedName, recordType)
                    QueryOutcome(parsed.status, parsed.records, parsed.errorMessage)
                } catch (error: DnsTransportException) {
                    QueryOutcome(error.status, emptyList(), error.message)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    QueryOutcome(
                        status = DnsLookupStatus.NETWORK_ERROR,
                        records = emptyList(),
                        errorMessage = "DNS query failed.",
                    )
                }
                outcomes += outcome
            }
        } catch (error: CancellationException) {
            throw error
        }

        val records = deduplicateRecords(outcomes.flatMap { it.records })
        val status = aggregateStatus(outcomes, records)
        val errorMessage = if (status == DnsLookupStatus.SUCCESS) {
            null
        } else {
            outcomes.mapNotNull { it.errorMessage }.distinct().joinToString("; ").ifBlank {
                defaultErrorMessage(status)
            }
        }
        return result(
            queryName = normalizedName,
            requestedTypes = request.recordTypes,
            server = serverInfoProvider.current(),
            method = queryMethod,
            status = status,
            durationMs = elapsedMillis(startedNanos),
            startTime = startedAt,
            endTime = currentTimeMillis(),
            errorMessage = errorMessage,
            records = records,
        )
    }

    private fun aggregateStatus(
        outcomes: List<QueryOutcome>,
        records: List<DnsRecord>,
    ): DnsLookupStatus {
        if (records.isNotEmpty()) {
            val hasActualFailure = outcomes.any { outcome ->
                outcome.status != DnsLookupStatus.SUCCESS &&
                    outcome.status != DnsLookupStatus.NO_RECORDS
            }
            return if (hasActualFailure) {
                DnsLookupStatus.PARTIAL
            } else {
                DnsLookupStatus.SUCCESS
            }
        }
        if (outcomes.all { it.status == DnsLookupStatus.NO_RECORDS }) {
            return DnsLookupStatus.NO_RECORDS
        }
        return listOf(
            DnsLookupStatus.INVALID_RESPONSE,
            DnsLookupStatus.TIMEOUT,
            DnsLookupStatus.NETWORK_ERROR,
            DnsLookupStatus.NXDOMAIN,
            DnsLookupStatus.NO_RECORDS,
            DnsLookupStatus.FAILED,
        ).firstOrNull { status -> outcomes.any { it.status == status } }
            ?: DnsLookupStatus.FAILED
    }

    private fun deduplicateRecords(records: List<DnsRecord>): List<DnsRecord> {
        val uniqueRecords = linkedMapOf<RecordIdentity, DnsRecord>()
        records.forEach { record ->
            val identity = RecordIdentity(
                type = record.type,
                name = record.name.dnsIdentityValue(),
                value = record.value.identityValue(record.type),
                priority = record.priority,
            )
            val existing = uniqueRecords[identity]
            uniqueRecords[identity] = if (existing == null) {
                record
            } else {
                existing.copy(ttl = mergeTtl(existing.ttl, record.ttl))
            }
        }
        return uniqueRecords.values.toList()
    }

    private fun mergeTtl(first: Long?, second: Long?): Long? = when {
        first == null -> second
        second == null -> first
        else -> minOf(first, second)
    }

    private fun String.dnsIdentityValue(): String = trimEnd('.').lowercase(Locale.ROOT)

    private fun String.identityValue(type: DnsRecordType): String = when (type) {
        DnsRecordType.CNAME,
        DnsRecordType.MX,
        -> dnsIdentityValue()

        else -> this
    }

    private fun defaultErrorMessage(status: DnsLookupStatus): String = when (status) {
        DnsLookupStatus.INVALID_RESPONSE -> "Invalid DNS response."
        DnsLookupStatus.TIMEOUT -> "DNS query timed out."
        DnsLookupStatus.NETWORK_ERROR -> "DNS network request failed."
        DnsLookupStatus.NXDOMAIN -> "DNS response reported NXDOMAIN."
        DnsLookupStatus.NO_RECORDS -> "No requested DNS records found."
        else -> "DNS lookup failed."
    }

    private fun result(
        queryName: String,
        requestedTypes: Set<DnsRecordType>,
        server: DnsServerInfo?,
        method: DnsQueryMethod,
        status: DnsLookupStatus,
        durationMs: Long?,
        startTime: Long,
        endTime: Long,
        errorMessage: String?,
        records: List<DnsRecord> = emptyList(),
    ): DnsLookupResult = DnsLookupResult(
        queryName = queryName,
        requestedTypes = requestedTypes,
        records = records,
        server = server,
        method = method,
        status = status,
        durationMs = durationMs,
        startTime = startTime,
        endTime = endTime,
        errorMessage = errorMessage,
    )

    private fun elapsedMillis(startedNanos: Long): Long =
        ((nanoTime() - startedNanos).coerceAtLeast(0L) / NANOS_PER_MILLISECOND)

    private data class QueryOutcome(
        val status: DnsLookupStatus,
        val records: List<DnsRecord>,
        val errorMessage: String?,
    )

    private data class RecordIdentity(
        val type: DnsRecordType,
        val name: String,
        val value: String,
        val priority: Int?,
    )

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
