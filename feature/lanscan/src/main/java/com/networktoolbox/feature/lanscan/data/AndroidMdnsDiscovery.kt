package com.networktoolbox.feature.lanscan.data

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.ext.SdkExtensions
import com.networktoolbox.feature.lanscan.domain.MdnsDiscovery
import com.networktoolbox.feature.lanscan.domain.MdnsDiscoveryEvent
import com.networktoolbox.feature.lanscan.domain.MdnsDiscoveryRequest
import com.networktoolbox.feature.lanscan.domain.MdnsDiscoverySession
import com.networktoolbox.feature.lanscan.domain.MdnsObservation
import com.networktoolbox.feature.lanscan.domain.MdnsServiceKey
import java.net.Inet4Address
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Framework DNS-SD adapter. It exposes callbacks only through the domain contract. */
class AndroidMdnsDiscovery(context: Context) : MdnsDiscovery {
    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    override fun start(
        request: MdnsDiscoveryRequest,
        onEvent: (MdnsDiscoveryEvent) -> Unit,
    ): MdnsDiscoverySession = Session(
        nsdManager = nsdManager,
        connectivityManager = connectivityManager,
        wifiManager = wifiManager,
        request = request,
        onEvent = onEvent,
    ).also(Session::start)

    private class Session(
        private val nsdManager: NsdManager?,
        private val connectivityManager: ConnectivityManager?,
        private val wifiManager: WifiManager?,
        private val request: MdnsDiscoveryRequest,
        private val onEvent: (MdnsDiscoveryEvent) -> Unit,
    ) : MdnsDiscoverySession {
        private val stateLock = Any()
        private val callbackExecutor: ExecutorService = Executors.newSingleThreadExecutor()
        private val resolutionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val resolveSlots = Semaphore(MAX_CONCURRENT_RESOLUTIONS)
        private val discoveryListeners = mutableMapOf<String, NsdManager.DiscoveryListener>()
        private val resolving = mutableMapOf<MdnsServiceKey, NsdManager.ResolveListener>()
        private val seenServices = mutableSetOf<MdnsServiceKey>()
        private var multicastLock: WifiManager.MulticastLock? = null
        private var stopped = false

        @SuppressLint("MissingPermission")
        fun start() {
            try {
                if (nsdManager == null) {
                    emit(
                        MdnsDiscoveryEvent.DiscoveryStartFailed(
                            serviceType = "",
                            errorCode = NsdManager.FAILURE_INTERNAL_ERROR,
                        ),
                    )
                    stop()
                    return
                }
                val activeNetwork = connectivityManager?.activeNetwork
                if (activeNetwork == null) {
                    emit(
                        MdnsDiscoveryEvent.DiscoveryStartFailed(
                            serviceType = "",
                            errorCode = NsdManager.FAILURE_BAD_PARAMETERS,
                        ),
                    )
                    stop()
                    return
                }
                acquireMulticastLockIfRequired()
                request.serviceTypes.forEach { serviceType ->
                    startDiscovery(serviceType, activeNetwork)
                }
            } catch (error: Exception) {
                emit(
                    MdnsDiscoveryEvent.DiscoveryStartFailed(
                        serviceType = "",
                        errorCode = errorCode(error),
                    ),
                )
                stop()
            }
        }

        override fun stop() {
            val listeners: List<Pair<String, NsdManager.DiscoveryListener>>
            val resolveListeners: List<NsdManager.ResolveListener>
            synchronized(stateLock) {
                if (stopped) return
                stopped = true
                listeners = discoveryListeners.toList()
                resolveListeners = resolving.values.toList()
                discoveryListeners.clear()
                resolving.clear()
            }

            resolutionScope.cancel()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                resolveListeners.forEach { listener ->
                    runCatching { nsdManager?.stopServiceResolution(listener) }
                }
            }
            listeners.forEach { (serviceType, listener) ->
                runCatching {
                    nsdManager?.stopServiceDiscovery(listener)
                }.onFailure { error ->
                    emit(
                        MdnsDiscoveryEvent.DiscoveryStopFailed(
                            serviceType = serviceType,
                            errorCode = errorCode(error),
                        ),
                    )
                }
            }
            releaseMulticastLock()
            callbackExecutor.shutdownNow()
        }

        private fun startDiscovery(serviceType: String, network: Network) {
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(type: String) {
                    emit(MdnsDiscoveryEvent.DiscoveryStarted(type))
                }

                override fun onDiscoveryStopped(type: String) {
                    removeDiscoveryListener(type, this)
                    emit(MdnsDiscoveryEvent.DiscoveryStopped(type))
                }

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    val key = serviceInfo.toServiceKey() ?: return
                    val firstObservation = synchronized(stateLock) {
                        if (stopped || !seenServices.add(key)) false else true
                    }
                    if (!firstObservation) return
                    emit(
                        MdnsDiscoveryEvent.ServiceFound(
                            serviceName = key.serviceName,
                            serviceType = key.serviceType,
                        ),
                    )
                    resolveService(serviceInfo, key, network)
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    val key = serviceInfo.toServiceKey() ?: return
                    synchronized(stateLock) { seenServices.remove(key) }
                    stopResolution(key)
                    emit(
                        MdnsDiscoveryEvent.ServiceLost(
                            serviceName = key.serviceName,
                            serviceType = key.serviceType,
                        ),
                    )
                }

                override fun onStartDiscoveryFailed(type: String, errorCode: Int) {
                    removeDiscoveryListener(type, this)
                    emit(MdnsDiscoveryEvent.DiscoveryStartFailed(type, errorCode))
                    runCatching { nsdManager?.stopServiceDiscovery(this) }
                }

                override fun onStopDiscoveryFailed(type: String, errorCode: Int) {
                    removeDiscoveryListener(type, this)
                    emit(MdnsDiscoveryEvent.DiscoveryStopFailed(type, errorCode))
                }
            }

            synchronized(stateLock) {
                if (stopped) return
                discoveryListeners[serviceType] = listener
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    nsdManager?.discoverServices(
                        serviceType,
                        NsdManager.PROTOCOL_DNS_SD,
                        network,
                        callbackExecutor,
                        listener,
                    )
                } else {
                    // API 31-32 have no Network overload; generation checks discard stale data.
                    nsdManager?.discoverServices(
                        serviceType,
                        NsdManager.PROTOCOL_DNS_SD,
                        listener,
                    )
                }
            } catch (error: Exception) {
                removeDiscoveryListener(serviceType, listener)
                emit(
                    MdnsDiscoveryEvent.DiscoveryStartFailed(
                        serviceType = serviceType,
                        errorCode = errorCode(error),
                    ),
                )
            }
        }

        private fun resolveService(
            serviceInfo: NsdServiceInfo,
            key: MdnsServiceKey,
            network: Network,
        ) {
            resolutionScope.launch {
                resolveSlots.withPermit {
                    if (!isSessionActive()) return@withPermit
                    resolveOne(serviceInfo, key, network)
                }
            }
        }

        @Suppress("DEPRECATION")
        private suspend fun resolveOne(
            serviceInfo: NsdServiceInfo,
            key: MdnsServiceKey,
            network: Network,
        ) = suspendCancellableCoroutine<Unit> { continuation ->
            val listener = object : NsdManager.ResolveListener {
                override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                    if (isSessionActive()) {
                        resolvedInfo.toObservation(request, network)?.let { observation ->
                            emit(MdnsDiscoveryEvent.ServiceResolved(observation))
                        }
                    }
                    completeResolution(key, this, continuation)
                }

                override fun onResolveFailed(failedInfo: NsdServiceInfo, errorCode: Int) {
                    if (isSessionActive()) {
                        emit(
                            MdnsDiscoveryEvent.ResolveFailed(
                                serviceName = key.serviceName,
                                serviceType = key.serviceType,
                                errorCode = errorCode,
                            ),
                        )
                    }
                    completeResolution(key, this, continuation)
                }

                override fun onResolutionStopped(stoppedInfo: NsdServiceInfo) {
                    completeResolution(key, this, continuation)
                }

                override fun onStopResolutionFailed(stoppedInfo: NsdServiceInfo, errorCode: Int) {
                    completeResolution(key, this, continuation)
                }
            }
            synchronized(stateLock) {
                if (stopped) {
                    continuation.resume(Unit)
                    return@suspendCancellableCoroutine
                }
                resolving[key] = listener
            }
            continuation.invokeOnCancellation {
                removeResolution(key, listener)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    runCatching { nsdManager?.stopServiceResolution(listener) }
                }
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    nsdManager?.resolveService(serviceInfo, callbackExecutor, listener)
                } else {
                    nsdManager?.resolveService(serviceInfo, listener)
                }
            } catch (error: Exception) {
                if (isSessionActive()) {
                    emit(
                        MdnsDiscoveryEvent.ResolveFailed(
                            serviceName = key.serviceName,
                            serviceType = key.serviceType,
                            errorCode = errorCode(error),
                        ),
                    )
                }
                completeResolution(key, listener, continuation)
            }
        }

        private fun stopResolution(key: MdnsServiceKey) {
            val listener = synchronized(stateLock) { resolving.remove(key) }
            if (listener != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                runCatching { nsdManager?.stopServiceResolution(listener) }
            }
        }

        private fun completeResolution(
            key: MdnsServiceKey,
            listener: NsdManager.ResolveListener,
            continuation: kotlinx.coroutines.CancellableContinuation<Unit>,
        ) {
            removeResolution(key, listener)
            resumeIfActive(continuation)
        }

        private fun removeResolution(
            key: MdnsServiceKey,
            listener: NsdManager.ResolveListener,
        ) {
            synchronized(stateLock) {
                if (resolving[key] === listener) resolving.remove(key)
            }
        }

        private fun removeDiscoveryListener(
            serviceType: String,
            listener: NsdManager.DiscoveryListener,
        ) {
            synchronized(stateLock) {
                if (discoveryListeners[serviceType] === listener) {
                    discoveryListeners.remove(serviceType)
                }
            }
        }

        private fun isSessionActive(): Boolean = synchronized(stateLock) { !stopped }

        private fun emit(event: MdnsDiscoveryEvent) {
            if (isSessionActive()) onEvent(event)
        }

        private fun acquireMulticastLockIfRequired() {
            if (request.connectionType != com.networktoolbox.core.network.model.ConnectionType.WIFI) return
            if (!requiresManualMulticastLock()) return
            val lock = wifiManager?.createMulticastLock("NetworkToolbox.mDNS") ?: return
            lock.setReferenceCounted(false)
            lock.acquire()
            multicastLock = lock
        }

        private fun releaseMulticastLock() {
            val lock = synchronized(stateLock) {
                val current = multicastLock
                multicastLock = null
                current
            }
            if (lock?.isHeld == true) runCatching { lock.release() }
        }

        private fun requiresManualMulticastLock(): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) < 7

        private fun errorCode(error: Throwable): Int =
            (error as? SecurityException)?.hashCode() ?: NsdManager.FAILURE_INTERNAL_ERROR

        private fun NsdServiceInfo.toServiceKey(): MdnsServiceKey? {
            val name = getServiceName()?.trim().orEmpty()
            val type = getServiceType()?.trim()?.removeSuffix(".").orEmpty()
            if (name.isBlank() || type.isBlank()) return null
            return MdnsServiceKey(serviceName = name, serviceType = type)
        }

        @Suppress("DEPRECATION")
        private fun NsdServiceInfo.toObservation(
            request: MdnsDiscoveryRequest,
            expectedNetwork: Network,
        ): MdnsObservation? {
            val key = toServiceKey() ?: return null
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    getNetwork() != null &&
                    getNetwork() != expectedNetwork
            ) {
                return null
            }
            val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                getHostAddresses()
            } else {
                listOfNotNull(getHost())
            }
            val ipv4 = addresses.filterIsInstance<Inet4Address>()
                .mapNotNull { it.hostAddress }
                .distinct()
            val ipv6 = addresses.asSequence()
                .filterNot { it is Inet4Address }
                .mapNotNull { it.hostAddress }
                .distinct()
                .toList()
            val hostName = getHost()?.hostName
                ?.trim()
                ?.takeIf { it.isNotBlank() && it !in ipv4 && it !in ipv6 }
            return MdnsObservation(
                serviceName = key.serviceName,
                serviceType = key.serviceType,
                hostname = hostName,
                ipv4Addresses = ipv4,
                ipv6Addresses = ipv6,
                port = getPort().takeIf { it in 1..65_535 },
                txtAttributes = boundedTxtAttributes(getAttributes()),
                observedAt = System.currentTimeMillis(),
                generation = request.generation,
                networkIdentity = request.networkIdentity,
            )
        }

        private fun boundedTxtAttributes(
            attributes: Map<String, ByteArray>?,
        ): Map<String, String> {
            if (attributes.isNullOrEmpty()) return emptyMap()
            var totalLength = 0
            return attributes.entries.asSequence()
                .mapNotNull { (rawKey, rawValue) ->
                    val key = rawKey.safeText(MAX_TXT_KEY_LENGTH)
                    val value = rawValue.decodeUtf8()?.safeText(MAX_TXT_VALUE_LENGTH)
                    if (key.isBlank() || value.isNullOrBlank()) null else key to value
                }
                .take(MAX_TXT_ATTRIBUTE_COUNT)
                .filter { (key, value) ->
                    totalLength += key.length + value.length
                    totalLength <= MAX_TXT_TOTAL_LENGTH
                }
                .toMap()
        }

        private fun String.safeText(maxLength: Int): String =
            filter { character -> character.code >= 0x20 && character.code != 0x7F }
                .trim()
                .take(maxLength)

        private fun ByteArray.decodeUtf8(): String? = try {
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(ByteBuffer.wrap(this)).toString()
        } catch (_: CharacterCodingException) {
            null
        }

        private fun resumeIfActive(
            continuation: kotlinx.coroutines.CancellableContinuation<Unit>,
        ) {
            if (continuation.isActive) continuation.resume(Unit)
        }

        companion object {
            const val MAX_CONCURRENT_RESOLUTIONS: Int = 2
            const val MAX_TXT_ATTRIBUTE_COUNT: Int = 16
            const val MAX_TXT_KEY_LENGTH: Int = 64
            const val MAX_TXT_VALUE_LENGTH: Int = 256
            const val MAX_TXT_TOTAL_LENGTH: Int = 1_024
        }
    }
}
