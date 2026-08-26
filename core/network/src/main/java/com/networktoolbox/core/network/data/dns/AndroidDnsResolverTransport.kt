package com.networktoolbox.core.network.data.dns

import android.content.Context
import android.net.ConnectivityManager
import android.net.DnsResolver
import android.os.CancellationSignal
import com.networktoolbox.core.network.dns.DnsLookupStatus
import com.networktoolbox.core.network.dns.DnsQueryMessageBuilder
import com.networktoolbox.core.network.dns.DnsRawQueryTransport
import com.networktoolbox.core.network.dns.DnsRecordType
import com.networktoolbox.core.network.dns.DnsTransportException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

class AndroidDnsResolverTransport(
    context: Context,
    private val resolver: DnsResolver = DnsResolver.getInstance(),
    private val executor: java.util.concurrent.Executor = context.mainExecutor,
    private val connectivityManager: ConnectivityManager =
        requireNotNull(context.getSystemService(ConnectivityManager::class.java)),
) : DnsRawQueryTransport {
    override suspend fun query(
        queryName: String,
        recordType: DnsRecordType,
        timeoutMs: Int,
    ): ByteArray {
        if (timeoutMs <= 0) {
            throw DnsTransportException(
                status = DnsLookupStatus.TIMEOUT,
                message = "DNS query timed out.",
            )
        }

        val network = connectivityManager.activeNetwork
            ?: throw DnsTransportException(
                status = DnsLookupStatus.NETWORK_ERROR,
                message = "No active network is available.",
            )
        val query = DnsQueryMessageBuilder.build(
            queryName = queryName,
            recordType = recordType,
            transactionId = (System.nanoTime().toInt() and TRANSACTION_ID_MASK),
        )

        return try {
            withTimeout(timeoutMs.toLong()) {
                suspendCancellableCoroutine { continuation ->
                    val cancellationSignal = CancellationSignal()
                    continuation.invokeOnCancellation { cancellationSignal.cancel() }
                    try {
                        resolver.rawQuery(
                            network,
                            query,
                            DnsResolver.FLAG_EMPTY,
                            executor,
                            cancellationSignal,
                            object : DnsResolver.Callback<ByteArray> {
                                override fun onAnswer(answer: ByteArray, rcode: Int) {
                                    if (continuation.isActive) continuation.resume(answer)
                                }

                                override fun onError(error: DnsResolver.DnsException) {
                                    if (continuation.isActive) {
                                        continuation.resumeWithException(error.toTransportException())
                                    }
                                }
                            },
                        )
                    } catch (_: RuntimeException) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                DnsTransportException(
                                    status = DnsLookupStatus.NETWORK_ERROR,
                                    message = "DNS query could not be started.",
                                ),
                            )
                        }
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            throw DnsTransportException(
                status = DnsLookupStatus.TIMEOUT,
                message = "DNS query timed out.",
            )
        } catch (error: CancellationException) {
            throw error
        }
    }

    private fun DnsResolver.DnsException.toTransportException(): DnsTransportException {
        val status = if (code == DnsResolver.ERROR_PARSE) {
            DnsLookupStatus.INVALID_RESPONSE
        } else {
            DnsLookupStatus.NETWORK_ERROR
        }
        val message = if (status == DnsLookupStatus.INVALID_RESPONSE) {
            "DNS response could not be parsed."
        } else {
            "DNS system resolver request failed."
        }
        return DnsTransportException(status, message)
    }

    private companion object {
        const val TRANSACTION_ID_MASK = 0xffff
    }
}
