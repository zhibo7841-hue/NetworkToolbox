package com.networktoolbox.core.network.data

import com.networktoolbox.core.network.tcp.TcpPortChecker
import com.networktoolbox.core.network.tcp.TcpProbeResult
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun interface TcpConnection {
    fun connect(host: String, port: Int, timeoutMs: Int)
}

class AndroidTcpPortChecker(
    private val tcpConnection: TcpConnection = TcpConnection { host, port, timeoutMs ->
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), timeoutMs)
        }
    },
) : TcpPortChecker {
    override suspend fun check(host: String, port: Int, timeoutMs: Int): TcpProbeResult =
        withContext(Dispatchers.IO) {
            checkBlocking(host, port, timeoutMs)
        }

    private fun checkBlocking(host: String, port: Int, timeoutMs: Int): TcpProbeResult {
        val normalizedHost = host.trim()
        if (normalizedHost.isEmpty()) {
            return failed(normalizedHost, port, "Invalid host.")
        }
        if (port !in MIN_PORT..MAX_PORT) {
            return failed(normalizedHost, port, "Invalid port.")
        }
        if (timeoutMs <= 0) {
            return failed(normalizedHost, port, "Timeout must be greater than zero.")
        }

        val startedAt = System.nanoTime()
        return try {
            tcpConnection.connect(normalizedHost, port, timeoutMs)
            TcpProbeResult(
                host = normalizedHost,
                port = port,
                success = true,
                latencyMs = elapsedMillis(startedAt),
                errorMessage = null,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: SocketTimeoutException) {
            failed(normalizedHost, port, TIMEOUT)
        } catch (_: ConnectException) {
            failed(normalizedHost, port, CONNECTION_REFUSED)
        } catch (error: IOException) {
            failed(normalizedHost, port, classifyIOException(error))
        } catch (_: SecurityException) {
            failed(normalizedHost, port, UNKNOWN_ERROR)
        } catch (_: RuntimeException) {
            failed(normalizedHost, port, UNKNOWN_ERROR)
        }
    }

    private fun classifyIOException(error: IOException): String {
        val message = error.message.orEmpty().lowercase(Locale.ROOT)
        return when {
            message.contains("timeout") || message.contains("timed out") -> TIMEOUT
            message.contains("refused") -> CONNECTION_REFUSED
            else -> UNKNOWN_ERROR
        }
    }

    private fun failed(host: String, port: Int, message: String): TcpProbeResult = TcpProbeResult(
        host = host,
        port = port,
        success = false,
        latencyMs = null,
        errorMessage = message,
    )

    private fun elapsedMillis(startedAt: Long): Long =
        ((System.nanoTime() - startedAt).coerceAtLeast(0L) / NANOS_PER_MILLISECOND)

    private companion object {
        const val MIN_PORT = 1
        const val MAX_PORT = 65_535
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val CONNECTION_REFUSED = "Connection refused"
        const val TIMEOUT = "Timeout"
        const val UNKNOWN_ERROR = "Unknown error"
    }
}
