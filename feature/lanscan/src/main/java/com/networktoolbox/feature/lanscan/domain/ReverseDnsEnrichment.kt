package com.networktoolbox.feature.lanscan.domain

import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.domain.model.LanDeviceNameSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicBoolean

/** Resolves a discovered device address through the current system name resolver. */
fun interface ReverseDnsResolver {
    suspend fun resolve(ipAddress: String): ReverseDnsResolution
}

sealed interface ReverseDnsResolution {
    data class Resolved(val hostname: String) : ReverseDnsResolution

    data object NoResult : ReverseDnsResolution

    data class Failed(val message: String? = null) : ReverseDnsResolution
}

enum class ReverseDnsEnrichmentStatus {
    RESOLVED,
    NO_RESULT,
    TIMED_OUT,
    FAILED,
}

data class ReverseDnsEnrichmentResult(
    val ipAddress: String,
    val hostname: String? = null,
    val source: LanDeviceNameSource = LanDeviceNameSource.REVERSE_DNS,
    val status: ReverseDnsEnrichmentStatus,
)

fun interface ReverseDnsEnricher {
    suspend fun enrich(
        devices: List<LanDevice>,
        onResult: (ReverseDnsEnrichmentResult) -> Unit,
    )
}

/**
 * Bounded post-discovery hostname enrichment.
 *
 * A deadline reports that the current UI must not wait for a lookup. It cannot
 * force the system's blocking resolver to stop, so each worker remains occupied
 * until that call returns. This keeps the physical lookup count bounded.
 */
class DefaultReverseDnsEnricher(
    private val resolver: ReverseDnsResolver,
    private val maxConcurrentLookups: Int = DEFAULT_MAX_CONCURRENT_LOOKUPS,
    private val visibleDeadlineMs: Long = DEFAULT_VISIBLE_DEADLINE_MS,
) : ReverseDnsEnricher {
    init {
        require(maxConcurrentLookups > 0) {
            "Reverse DNS concurrency must be greater than zero."
        }
        require(visibleDeadlineMs > 0L) {
            "Reverse DNS visible deadline must be greater than zero."
        }
    }

    override suspend fun enrich(
        devices: List<LanDevice>,
        onResult: (ReverseDnsEnrichmentResult) -> Unit,
    ) = coroutineScope {
        val targets = devices
            .asSequence()
            .filter { it.hostName.isNullOrBlank() }
            .distinctBy(LanDevice::ipAddress)
            .toList()
        if (targets.isEmpty()) return@coroutineScope

        val work = Channel<LanDevice>(capacity = Channel.UNLIMITED)
        targets.forEach(work::trySend)
        work.close()

        List(minOf(maxConcurrentLookups, targets.size)) {
            launch {
                for (device in work) {
                    currentCoroutineContext().ensureActive()
                    enrichDevice(device, onResult)
                }
            }
        }.joinAll()
    }

    private suspend fun enrichDevice(
        device: LanDevice,
        onResult: (ReverseDnsEnrichmentResult) -> Unit,
    ) = coroutineScope {
        val lookupFinished = AtomicBoolean(false)
        val deadline = launch {
            delay(visibleDeadlineMs)
            if (!lookupFinished.get()) {
                onResult(
                    ReverseDnsEnrichmentResult(
                        ipAddress = device.ipAddress,
                        status = ReverseDnsEnrichmentStatus.TIMED_OUT,
                    ),
                )
            }
        }
        try {
            val result = resolver.resolve(device.ipAddress)
            lookupFinished.set(true)
            deadline.cancel()
            onResult(result.toEnrichmentResult(device.ipAddress))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            lookupFinished.set(true)
            deadline.cancel()
            onResult(
                ReverseDnsEnrichmentResult(
                    ipAddress = device.ipAddress,
                    status = ReverseDnsEnrichmentStatus.FAILED,
                ),
            )
        }
    }

    private fun ReverseDnsResolution.toEnrichmentResult(
        ipAddress: String,
    ): ReverseDnsEnrichmentResult = when (this) {
        is ReverseDnsResolution.Resolved -> ReverseDnsEnrichmentResult(
            ipAddress = ipAddress,
            hostname = hostname,
            status = ReverseDnsEnrichmentStatus.RESOLVED,
        )

        ReverseDnsResolution.NoResult -> ReverseDnsEnrichmentResult(
            ipAddress = ipAddress,
            status = ReverseDnsEnrichmentStatus.NO_RESULT,
        )

        is ReverseDnsResolution.Failed -> ReverseDnsEnrichmentResult(
            ipAddress = ipAddress,
            status = ReverseDnsEnrichmentStatus.FAILED,
        )
    }

    companion object {
        const val DEFAULT_MAX_CONCURRENT_LOOKUPS: Int = 4
        const val DEFAULT_VISIBLE_DEADLINE_MS: Long = 1_500L
    }
}
