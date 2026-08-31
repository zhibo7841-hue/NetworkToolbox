package com.networktoolbox.feature.lanscan.data

import android.content.Context
import com.networktoolbox.feature.lanscan.domain.SsdpDiscovery
import com.networktoolbox.feature.lanscan.domain.SsdpMessageBuilder
import com.networktoolbox.feature.lanscan.domain.SsdpDiscoveryRequest
import com.networktoolbox.feature.lanscan.domain.SsdpResponse
import com.networktoolbox.feature.lanscan.domain.SsdpResponseParser
import com.networktoolbox.feature.lanscan.domain.NoOpUpnpDiagnosticLogger
import com.networktoolbox.feature.lanscan.domain.UpnpDiagnosticEvent
import com.networktoolbox.feature.lanscan.domain.UpnpDiagnosticEventType
import com.networktoolbox.feature.lanscan.domain.UpnpDiagnosticLogger
import com.networktoolbox.feature.lanscan.domain.safeUpnpLocationForLog
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Small request/reply SSDP adapter. The socket is owned by this operation, so
 * closing it on cancellation is safe and distinct from shutting down a
 * framework-owned callback executor.
 */
class AndroidSsdpDiscovery(
    context: Context,
    private val diagnosticLogger: UpnpDiagnosticLogger = NoOpUpnpDiagnosticLogger,
) : SsdpDiscovery {
    private val networkSelector = AndroidLanNetworkSelector(context)

    override suspend fun discover(request: SsdpDiscoveryRequest): List<SsdpResponse> =
        withContext(Dispatchers.IO) {
            currentCoroutineContext().ensureActive()
            val network = networkSelector.select(request) ?: return@withContext emptyList()
            val socket = DatagramSocket()
            val cancellationHandle = currentCoroutineContext()[Job]?.invokeOnCompletion {
                runCatching { socket.close() }
            }
            try {
                network.bindSocket(socket)
                val searchBytes = SsdpMessageBuilder.mSearch(request.mxSeconds)
                    .toByteArray(StandardCharsets.US_ASCII)
                socket.send(
                    DatagramPacket(
                        searchBytes,
                        searchBytes.size,
                        InetSocketAddress(
                            InetAddress.getByName(SSDP_MULTICAST_ADDRESS),
                            SSDP_PORT,
                        ),
                    ),
                )
                log(
                    UpnpDiagnosticEvent(
                        type = UpnpDiagnosticEventType.SSDP_START,
                        generation = request.generation,
                        networkIdentity = request.networkIdentity,
                    ),
                )

                val deadlineNanos = System.nanoTime() + request.discoveryWindowMs * NANOS_PER_MILLISECOND
                val responses = mutableListOf<SsdpResponse>()
                while (currentCoroutineContext().isActive) {
                    currentCoroutineContext().ensureActive()
                    val remainingMs = ((deadlineNanos - System.nanoTime()) /
                        NANOS_PER_MILLISECOND).coerceAtLeast(0L)
                    if (remainingMs <= 0L) break
                    socket.soTimeout = min(RECEIVE_TIMEOUT_MS, remainingMs).toInt().coerceAtLeast(1)
                    val buffer = ByteArray(SsdpResponseParser.MAX_PACKET_BYTES)
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        continue
                    } catch (error: SocketException) {
                        if (!currentCoroutineContext().isActive) throw CancellationException(
                            "SSDP discovery was cancelled.",
                            error,
                        )
                        break
                    }
                    currentCoroutineContext().ensureActive()
                    val sourceIp = packet.address?.hostAddress.orEmpty()
                    val rawResponse = String(
                        packet.data,
                        packet.offset,
                        packet.length,
                        StandardCharsets.ISO_8859_1,
                    )
                    SsdpResponseParser.parse(sourceIp, rawResponse)?.let { response ->
                        responses += response
                        log(
                            UpnpDiagnosticEvent(
                                type = UpnpDiagnosticEventType.SSDP_RESPONSE_RECEIVED,
                                generation = request.generation,
                                sourceIp = sourceIp,
                                location = safeUpnpLocationForLog(response.location),
                            ),
                        )
                    }
                }
                if (responses.isEmpty()) {
                    log(
                        UpnpDiagnosticEvent(
                            type = UpnpDiagnosticEventType.SSDP_TIMEOUT,
                            generation = request.generation,
                        ),
                    )
                }
                responses
            } catch (error: CancellationException) {
                throw error
            } catch (_: IOException) {
                emptyList()
            } finally {
                cancellationHandle?.dispose()
                runCatching { socket.close() }
                log(
                    UpnpDiagnosticEvent(
                        type = UpnpDiagnosticEventType.SSDP_STOP,
                        generation = request.generation,
                    ),
                )
            }
        }

    private fun log(event: UpnpDiagnosticEvent) {
        runCatching { diagnosticLogger.log(event) }
    }

    private companion object {
        private const val SSDP_MULTICAST_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1_900
        private const val RECEIVE_TIMEOUT_MS = 250L
        private const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
