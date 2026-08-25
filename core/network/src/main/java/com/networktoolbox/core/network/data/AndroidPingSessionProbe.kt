package com.networktoolbox.core.network.data

import com.networktoolbox.core.network.ping.PingAttemptResult
import com.networktoolbox.core.network.ping.PingMethod
import com.networktoolbox.core.network.ping.PingProbe
import com.networktoolbox.core.network.ping.PingProtocol
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android adapter for the session-oriented Ping v2 engine.
 *
 * The measurement remains the platform's best-effort reachability check and is
 * deliberately reported as SYSTEM_REACHABILITY rather than ICMP.
 */
class AndroidPingSessionProbe(
    private val addressResolver: AddressResolver = AddressResolver { target ->
        InetAddress.getAllByName(target)
    },
    private val reachabilityProbe: ReachabilityProbe = ReachabilityProbe { address, timeoutMs ->
        address.isReachable(timeoutMs)
    },
) : PingProbe {
    override suspend fun probe(
        target: String,
        protocol: PingProtocol,
        timeoutMs: Int,
    ): PingAttemptResult = withContext(Dispatchers.IO) {
        val normalizedTarget = target.trim()
        if (!isValidTarget(normalizedTarget)) {
            return@withContext unavailable(normalizedTarget, protocol, "Invalid target.")
        }
        if (timeoutMs <= 0) {
            return@withContext unavailable(
                normalizedTarget,
                protocol,
                "Timeout must be greater than zero.",
            )
        }

        val addresses = try {
            addressResolver.resolve(normalizedTarget)
        } catch (_: UnknownHostException) {
            return@withContext unavailable(
                normalizedTarget,
                protocol,
                "Target could not be resolved.",
            )
        } catch (_: SecurityException) {
            return@withContext unavailable(
                normalizedTarget,
                protocol,
                "Target resolution is unavailable.",
            )
        } catch (_: RuntimeException) {
            return@withContext unavailable(
                normalizedTarget,
                protocol,
                "Target resolution is unavailable.",
            )
        }

        val address = addresses.firstOrNull { it.matches(protocol) }
            ?: return@withContext unavailable(
                normalizedTarget,
                protocol,
                protocolUnavailableMessage(protocol, addresses),
            )
        val actualProtocol = address.protocol()
        val startedAt = System.nanoTime()

        try {
            val success = reachabilityProbe.isReachable(address, timeoutMs)
            PingAttemptResult(
                target = normalizedTarget,
                address = address.hostAddress,
                protocol = actualProtocol,
                success = success,
                latencyMs = elapsedMillis(startedAt).takeIf { success },
                method = PingMethod.SYSTEM_REACHABILITY,
                errorMessage = if (success) null else "Target is not reachable.",
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            unavailable(
                normalizedTarget,
                actualProtocol,
                "System reachability is unavailable.",
                address.hostAddress,
            )
        } catch (_: SecurityException) {
            unavailable(
                normalizedTarget,
                actualProtocol,
                "System reachability is unavailable.",
                address.hostAddress,
            )
        } catch (_: RuntimeException) {
            unavailable(
                normalizedTarget,
                actualProtocol,
                "System reachability is unavailable.",
                address.hostAddress,
            )
        }
    }

    private fun InetAddress.matches(protocol: PingProtocol): Boolean = when (protocol) {
        PingProtocol.AUTO -> true
        PingProtocol.IPV4 -> this is Inet4Address
        PingProtocol.IPV6 -> this is Inet6Address
    }

    private fun InetAddress.protocol(): PingProtocol = when (this) {
        is Inet4Address -> PingProtocol.IPV4
        is Inet6Address -> PingProtocol.IPV6
        else -> PingProtocol.AUTO
    }

    private fun protocolUnavailableMessage(
        protocol: PingProtocol,
        addresses: Array<InetAddress>,
    ): String = when {
        addresses.isEmpty() -> "Target could not be resolved."
        protocol == PingProtocol.IPV4 -> "No IPv4 address available."
        protocol == PingProtocol.IPV6 -> "No IPv6 address available."
        else -> "Target could not be resolved."
    }

    private fun elapsedMillis(startedAt: Long): Long =
        ((System.nanoTime() - startedAt).coerceAtLeast(0L) / NANOS_PER_MILLISECOND)

    private fun unavailable(
        target: String,
        protocol: PingProtocol,
        message: String,
        address: String? = null,
    ): PingAttemptResult = PingAttemptResult(
        target = target,
        address = address,
        protocol = protocol,
        success = false,
        latencyMs = null,
        method = PingMethod.UNAVAILABLE,
        errorMessage = message,
    )

    private fun isValidTarget(target: String): Boolean = target.isNotEmpty() &&
        target.none { it.isWhitespace() } &&
        !target.startsWith('.') &&
        !target.endsWith('.') &&
        !target.contains("..")

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}

fun interface AddressResolver {
    fun resolve(target: String): Array<InetAddress>
}
