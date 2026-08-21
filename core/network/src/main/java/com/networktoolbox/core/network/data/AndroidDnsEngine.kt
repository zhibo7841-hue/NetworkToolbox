package com.networktoolbox.core.network.data

import com.networktoolbox.core.network.dns.DnsEngine
import com.networktoolbox.core.network.dns.DnsMethod
import com.networktoolbox.core.network.dns.DnsRecord
import com.networktoolbox.core.network.dns.DnsRecordType
import com.networktoolbox.core.network.dns.DnsResult
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun interface DnsResolver {
    fun resolve(domain: String): List<DnsRecord>
}

class AndroidDnsEngine(
    private val resolver: DnsResolver = DnsResolver { domain ->
        InetAddress.getAllByName(domain).mapNotNull { address ->
            when (address) {
                is Inet4Address -> address.hostAddress?.let { value ->
                    DnsRecord(DnsRecordType.A, value)
                }
                is Inet6Address -> address.hostAddress?.let { value ->
                    DnsRecord(DnsRecordType.AAAA, value)
                }
                else -> null
            }
        }
    },
) : DnsEngine {
    override suspend fun lookup(domain: String): DnsResult = withContext(Dispatchers.IO) {
        lookupBlocking(domain)
    }

    private fun lookupBlocking(domain: String): DnsResult {
        val normalizedDomain = domain.trim()
        if (!isValidDomain(normalizedDomain)) {
            return unavailable(normalizedDomain, "Invalid domain.")
        }

        val startedAt = System.nanoTime()
        return try {
            val records = resolver.resolve(normalizedDomain)
                .filter { it.value.isNotBlank() }
                .distinct()
            val durationMs = elapsedMillis(startedAt)
            if (records.isEmpty()) {
                DnsResult(
                    domain = normalizedDomain,
                    success = false,
                    records = emptyList(),
                    durationMs = durationMs,
                    method = DnsMethod.SYSTEM_RESOLVER,
                    errorMessage = "No A or AAAA records found.",
                )
            } else {
                DnsResult(
                    domain = normalizedDomain,
                    success = true,
                    records = records,
                    durationMs = durationMs,
                    method = DnsMethod.SYSTEM_RESOLVER,
                    errorMessage = null,
                )
            }
        } catch (_: UnknownHostException) {
            failedSystemLookup(normalizedDomain, startedAt, "Domain could not be resolved.")
        } catch (_: SecurityException) {
            unavailable(normalizedDomain, "System resolver is unavailable.")
        } catch (error: CancellationException) {
            throw error
        } catch (_: RuntimeException) {
            unavailable(normalizedDomain, "System resolver is unavailable.")
        }
    }

    private fun isValidDomain(domain: String): Boolean = domain.isNotEmpty() &&
        domain.none { it.isWhitespace() } &&
        !domain.startsWith('.') &&
        !domain.endsWith('.') &&
        !domain.contains("..")

    private fun failedSystemLookup(domain: String, startedAt: Long, message: String): DnsResult =
        DnsResult(
            domain = domain,
            success = false,
            records = emptyList(),
            durationMs = elapsedMillis(startedAt),
            method = DnsMethod.SYSTEM_RESOLVER,
            errorMessage = message,
        )

    private fun unavailable(domain: String, message: String): DnsResult = DnsResult(
        domain = domain,
        success = false,
        records = emptyList(),
        durationMs = null,
        method = DnsMethod.UNAVAILABLE,
        errorMessage = message,
    )

    private fun elapsedMillis(startedAt: Long): Long =
        ((System.nanoTime() - startedAt).coerceAtLeast(0L) / NANOS_PER_MILLISECOND)

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
