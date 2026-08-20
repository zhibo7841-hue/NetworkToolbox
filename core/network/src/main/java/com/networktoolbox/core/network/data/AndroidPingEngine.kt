package com.networktoolbox.core.network.data

import com.networktoolbox.core.network.ping.PingEngine
import com.networktoolbox.core.network.ping.PingMethod
import com.networktoolbox.core.network.ping.PingResult
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun interface ReachabilityProbe {
    fun isReachable(address: InetAddress, timeoutMs: Int): Boolean
}

class AndroidPingEngine(
    private val reachabilityProbe: ReachabilityProbe = ReachabilityProbe { address, timeoutMs ->
        // This delegates to the platform reachability check. It is not a direct ICMP API.
        address.isReachable(timeoutMs)
    },
) : PingEngine {
    override suspend fun ping(target: String, timeoutMs: Int): PingResult = withContext(Dispatchers.IO) {
        pingBlocking(target, timeoutMs)
    }

    private fun pingBlocking(target: String, timeoutMs: Int): PingResult {
        val normalizedTarget = target.trim()
        if (!isValidTarget(normalizedTarget)) {
            return unavailable(normalizedTarget, "Invalid target.")
        }
        if (timeoutMs <= 0) {
            return unavailable(normalizedTarget, "Timeout must be greater than zero.")
        }

        val address = try {
            InetAddress.getByName(normalizedTarget)
        } catch (_: UnknownHostException) {
            return unavailable(normalizedTarget, "Target could not be resolved.")
        } catch (_: SecurityException) {
            return unavailable(normalizedTarget, "Target resolution is unavailable.")
        }

        val startedAt = System.nanoTime()
        return try {
            val success = reachabilityProbe.isReachable(address, timeoutMs)
            val latencyMs = elapsedMillis(startedAt).takeIf { success }
            PingResult(
                target = normalizedTarget,
                success = success,
                latencyMs = latencyMs,
                method = PingMethod.SYSTEM_REACHABILITY,
                errorMessage = if (success) null else "Target is not reachable.",
            )
        } catch (_: IOException) {
            unavailable(normalizedTarget, "System reachability is unavailable.")
        } catch (_: SecurityException) {
            unavailable(normalizedTarget, "System reachability is unavailable.")
        } catch (_: RuntimeException) {
            unavailable(normalizedTarget, "System reachability is unavailable.")
        }
    }

    private fun isValidTarget(target: String): Boolean = target.isNotEmpty() &&
        target.none { it.isWhitespace() } &&
        !target.startsWith('.') &&
        !target.endsWith('.') &&
        !target.contains("..")

    private fun elapsedMillis(startedAt: Long): Long =
        ((System.nanoTime() - startedAt).coerceAtLeast(0L) / NANOS_PER_MILLISECOND)

    private fun unavailable(target: String, message: String): PingResult = PingResult(
        target = target,
        success = false,
        latencyMs = null,
        method = PingMethod.UNAVAILABLE,
        errorMessage = message,
    )

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
