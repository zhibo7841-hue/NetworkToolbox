package com.networktoolbox.core.network.data.traceroute

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.ParcelFileDescriptor
import com.networktoolbox.core.network.traceroute.TracerouteBindResult
import com.networktoolbox.core.network.traceroute.TracerouteNetwork
import com.networktoolbox.core.network.traceroute.TracerouteNetworkProvider
import java.io.IOException
import java.net.Inet4Address
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidTracerouteNetworkProvider(context: Context) : TracerouteNetworkProvider {
    private val connectivityManager =
        context.getSystemService(ConnectivityManager::class.java)

    override fun current(): TracerouteNetwork? {
        val manager = connectivityManager ?: return null
        val network = runCatching { manager.activeNetwork }.getOrNull() ?: return null
        val capabilities = runCatching { manager.getNetworkCapabilities(network) }.getOrNull()
        return AndroidTracerouteNetwork(
            network = network,
            vpnActive = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
        )
    }
}

private class AndroidTracerouteNetwork(
    private val network: Network,
    override val vpnActive: Boolean?,
) : TracerouteNetwork {
    override val fingerprint: String = network.networkHandle.toString()

    override suspend fun resolveIpv4(hostname: String): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            network.getAllByName(hostname)
                .filterIsInstance<Inet4Address>()
                .mapNotNull { it.hostAddress }
        }.getOrDefault(emptyList())
    }

    override fun bindSocket(socketFd: Int): TracerouteBindResult = try {
        // Duplicate the descriptor so closing ParcelFileDescriptor cannot close
        // the native descriptor that the probe continues to own.
        ParcelFileDescriptor.fromFd(socketFd).use { descriptor ->
            network.bindSocket(descriptor.fileDescriptor)
        }
        TracerouteBindResult(success = true)
    } catch (error: SecurityException) {
        TracerouteBindResult(
            success = false,
            operation = "BIND",
            errorMessage = error.message,
        )
    } catch (error: IOException) {
        TracerouteBindResult(
            success = false,
            operation = "BIND",
            errorMessage = error.message,
        )
    } catch (error: RuntimeException) {
        TracerouteBindResult(
            success = false,
            operation = "BIND",
            errorMessage = error.message,
        )
    }
}
