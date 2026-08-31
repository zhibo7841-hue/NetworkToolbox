package com.networktoolbox.feature.lanscan.data

import com.networktoolbox.feature.lanscan.domain.ReverseDnsResolution
import com.networktoolbox.feature.lanscan.domain.ReverseDnsResolver
import java.net.InetAddress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun interface CanonicalHostNameLookup {
    fun lookup(ipAddress: String): String
}

/** Android/JVM system-resolver implementation for best-effort reverse DNS. */
class AndroidReverseDnsResolver(
    private val lookup: CanonicalHostNameLookup = CanonicalHostNameLookup { ipAddress ->
        InetAddress.getByName(ipAddress).canonicalHostName
    },
) : ReverseDnsResolver {
    override suspend fun resolve(ipAddress: String): ReverseDnsResolution = withContext(Dispatchers.IO) {
        try {
            lookup.lookup(ipAddress).toReverseDnsResolution(ipAddress)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            ReverseDnsResolution.Failed(error.message)
        }
    }
}

private fun String?.toReverseDnsResolution(ipAddress: String): ReverseDnsResolution {
    val hostname = this?.trim().orEmpty().removeSuffix(".")
    return if (hostname.isBlank() || hostname == ipAddress) {
        ReverseDnsResolution.NoResult
    } else {
        ReverseDnsResolution.Resolved(hostname)
    }
}
