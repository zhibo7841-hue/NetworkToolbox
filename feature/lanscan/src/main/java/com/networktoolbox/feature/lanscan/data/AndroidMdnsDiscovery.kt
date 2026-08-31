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
import android.util.Log
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
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.Executor
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
    /**
     * NsdManager can enqueue callbacks after an individual discovery session is
     * stopped. This executor therefore belongs to the application-scoped
     * adapter, not to a Session, and must remain alive for the adapter lifetime.
     */
    private val callbackExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "NetworkToolbox-mDNS")
    }

    override fun start(
        request: MdnsDiscoveryRequest,
        onEvent: (MdnsDiscoveryEvent) -> Unit,
    ): MdnsDiscoverySession = Session(
        nsdManager = nsdManager,
        connectivityManager = connectivityManager,
        wifiManager = wifiManager,
        callbackExecutor = callbackExecutor,
        request = request,
        onEvent = onEvent,
    ).also(Session::start)

    internal class Session(
        private val nsdManager: NsdManager?,
        private val connectivityManager: ConnectivityManager?,
        private val wifiManager: WifiManager?,
        private val callbackExecutor: Executor,
        private val request: MdnsDiscoveryRequest,
        private val onEvent: (MdnsDiscoveryEvent) -> Unit,
    ) : MdnsDiscoverySession {
        private val stateLock = Any()
        private val resolutionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val resolveSlots = Semaphore(MAX_CONCURRENT_RESOLUTIONS)
        private val pendingDiscoveryListeners =
            mutableMapOf<String, NsdManager.DiscoveryListener>()
        private val activeDiscoveryListeners =
            mutableMapOf<String, NsdManager.DiscoveryListener>()
        private val resolving = mutableMapOf<MdnsServiceKey, NsdManager.ResolveListener>()
        private val seenServices = mutableSetOf<MdnsServiceKey>()
        private val stoppedDiscoveryListeners =
            Collections.newSetFromMap(
                IdentityHashMap<NsdManager.DiscoveryListener, Boolean>(),
            )
        private val stoppedResolveListeners =
            Collections.newSetFromMap(
                IdentityHashMap<NsdManager.ResolveListener, Boolean>(),
            )
        private var multicastLock: WifiManager.MulticastLock? = null
        private var state = SessionState.NEW

        @SuppressLint("MissingPermission")
        fun start() {
            synchronized(stateLock) {
                if (state != SessionState.NEW) return
                state = SessionState.STARTED
            }
            logEvent("MDNS_START")
            try {
                if (nsdManager == null) {
                    emit(
                        MdnsDiscoveryEvent.DiscoveryStartFailed(
                            serviceType = "",
                            errorCode = NsdManager.FAILURE_INTERNAL_ERROR,
                        ),
                    )
                    failAndStop()
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
                    failAndStop()
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
                synchronized(stateLock) {
                    if (state == SessionState.STARTED) state = SessionState.FAILED
                }
                stop()
            }
        }

        override fun stop() {
            val listeners: List<Pair<String, NsdManager.DiscoveryListener>>
            val resolveListeners: List<NsdManager.ResolveListener>
            synchronized(stateLock) {
                if (state == SessionState.STOPPING || state == SessionState.STOPPED) return
                state = SessionState.STOPPING
                listeners = activeDiscoveryListeners.toList()
                resolveListeners = resolving.values.toList()
                activeDiscoveryListeners.clear()
                resolving.clear()
            }

            logEvent("MDNS_STOP_REQUESTED")
            runCatching { resolutionScope.cancel() }
                .onFailure { error -> logWarning("MDNS_STOP_FAILED", error) }
            resolveListeners.forEach { listener ->
                stopServiceResolutionSafely(listener)
            }
            listeners.forEach { (serviceType, listener) ->
                stopServiceDiscoverySafely(serviceType, listener)
            }
            releaseMulticastLock()
            synchronized(stateLock) {
                state = SessionState.STOPPED
            }
            logEvent("MDNS_SESSION_CLOSED")
        }

        private fun failAndStop() {
            synchronized(stateLock) {
                if (state == SessionState.STARTED) state = SessionState.FAILED
            }
            stop()
        }

        private fun startDiscovery(serviceType: String, network: Network) {
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(type: String) {
                    callbackBoundary("onDiscoveryStarted", serviceType) {
                        logEvent("MDNS_DISCOVERY_STARTED", serviceType)
                        emit(MdnsDiscoveryEvent.DiscoveryStarted(type))
                    }
                }

                override fun onDiscoveryStopped(type: String) {
                    callbackBoundary("onDiscoveryStopped", serviceType) {
                        removeDiscoveryListener(serviceType, this)
                        logEvent("MDNS_DISCOVERY_STOPPED", serviceType)
                        emit(MdnsDiscoveryEvent.DiscoveryStopped(type))
                    }
                }

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    callbackBoundary("onServiceFound", serviceType) {
                        val key = serviceInfo.toServiceKey() ?: return@callbackBoundary
                        val firstObservation = synchronized(stateLock) {
                            if (!isSessionActiveLocked() || !seenServices.add(key)) false else true
                        }
                        if (!firstObservation) return@callbackBoundary
                        logEvent("MDNS_SERVICE_FOUND", key.serviceType)
                        emit(
                            MdnsDiscoveryEvent.ServiceFound(
                                serviceName = key.serviceName,
                                serviceType = key.serviceType,
                            ),
                        )
                        resolveService(serviceInfo, key, network)
                    }
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    callbackBoundary("onServiceLost", serviceType) {
                        val key = serviceInfo.toServiceKey() ?: return@callbackBoundary
                        synchronized(stateLock) { seenServices.remove(key) }
                        stopResolution(key)
                        emit(
                            MdnsDiscoveryEvent.ServiceLost(
                                serviceName = key.serviceName,
                                serviceType = key.serviceType,
                            ),
                        )
                    }
                }

                override fun onStartDiscoveryFailed(type: String, errorCode: Int) {
                    callbackBoundary("onStartDiscoveryFailed", serviceType) {
                        // A failed start is not an active registration. Do not
                        // call stopServiceDiscovery for this listener.
                        removeDiscoveryListener(serviceType, this)
                        logWarning(
                            "MDNS_START_FAILED",
                            errorCode = errorCode,
                            serviceType = serviceType,
                        )
                        emit(MdnsDiscoveryEvent.DiscoveryStartFailed(type, errorCode))
                    }
                }

                override fun onStopDiscoveryFailed(type: String, errorCode: Int) {
                    callbackBoundary("onStopDiscoveryFailed", serviceType) {
                        removeDiscoveryListener(serviceType, this)
                        logWarning(
                            "MDNS_STOP_FAILED",
                            errorCode = errorCode,
                            serviceType = serviceType,
                        )
                        // Cleanup warnings are deliberately non-fatal. The LAN
                        // Scanner result was already produced by its core scan.
                        emit(MdnsDiscoveryEvent.DiscoveryStopFailed(type, errorCode))
                    }
                }
            }

            synchronized(stateLock) {
                if (!isSessionActiveLocked()) return
                pendingDiscoveryListeners[serviceType] = listener
            }
            try {
                logEvent("MDNS_DISCOVERY_REQUESTED", serviceType)
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
                val stopAfterStart = synchronized(stateLock) {
                    val stillPending = pendingDiscoveryListeners[serviceType] === listener
                    if (stillPending) pendingDiscoveryListeners.remove(serviceType)
                    if (stillPending && isSessionActiveLocked()) {
                        activeDiscoveryListeners[serviceType] = listener
                        false
                    } else {
                        stillPending && !isSessionActiveLocked()
                    }
                }
                if (stopAfterStart) stopServiceDiscoverySafely(serviceType, listener)
            } catch (error: Exception) {
                removeDiscoveryListener(serviceType, listener)
                logWarning("MDNS_START_FAILED", error = error, serviceType = serviceType)
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
                try {
                    resolveSlots.withPermit {
                        if (!isSessionActive()) return@withPermit
                        resolveOne(serviceInfo, key, network)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    logWarning("MDNS_RESOLVE_FAILED", error = error, serviceType = key.serviceType)
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
                    callbackBoundary("onServiceResolved", key.serviceType) {
                        try {
                            if (isSessionActive()) {
                                resolvedInfo.toObservation(request, network)?.let { observation ->
                                    logEvent("MDNS_RESOLVE_COMPLETED", key.serviceType)
                                    emit(MdnsDiscoveryEvent.ServiceResolved(observation))
                                }
                            }
                        } finally {
                            completeResolution(key, this, continuation)
                        }
                    }
                }

                override fun onResolveFailed(failedInfo: NsdServiceInfo, errorCode: Int) {
                    callbackBoundary("onResolveFailed", key.serviceType) {
                        try {
                            if (isSessionActive()) {
                                logWarning(
                                    "MDNS_RESOLVE_FAILED",
                                    errorCode = errorCode,
                                    serviceType = key.serviceType,
                                )
                                emit(
                                    MdnsDiscoveryEvent.ResolveFailed(
                                        serviceName = key.serviceName,
                                        serviceType = key.serviceType,
                                        errorCode = errorCode,
                                    ),
                                )
                            }
                        } finally {
                            completeResolution(key, this, continuation)
                        }
                    }
                }

                override fun onResolutionStopped(stoppedInfo: NsdServiceInfo) {
                    callbackBoundary("onResolutionStopped", key.serviceType) {
                        completeResolution(key, this, continuation)
                    }
                }

                override fun onStopResolutionFailed(stoppedInfo: NsdServiceInfo, errorCode: Int) {
                    callbackBoundary("onStopResolutionFailed", key.serviceType) {
                        logWarning(
                            "MDNS_STOP_FAILED",
                            errorCode = errorCode,
                            serviceType = key.serviceType,
                        )
                        completeResolution(key, this, continuation)
                    }
                }
            }
            val canStart = synchronized(stateLock) {
                if (!isSessionActiveLocked() || resolving.containsKey(key)) {
                    false
                } else {
                    resolving[key] = listener
                    true
                }
            }
            if (!canStart) {
                resumeIfActive(continuation)
                return@suspendCancellableCoroutine
            }
            continuation.invokeOnCancellation {
                removeResolution(key, listener)
                stopServiceResolutionSafely(listener)
            }
            try {
                val manager = nsdManager
                if (manager == null) {
                    completeResolution(key, listener, continuation)
                    return@suspendCancellableCoroutine
                }
                logEvent("MDNS_RESOLVE_STARTED", key.serviceType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    manager.resolveService(serviceInfo, callbackExecutor, listener)
                } else {
                    manager.resolveService(serviceInfo, listener)
                }
            } catch (error: Exception) {
                if (isSessionActive()) {
                    logWarning("MDNS_RESOLVE_FAILED", error = error, serviceType = key.serviceType)
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
            if (listener != null) stopServiceResolutionSafely(listener)
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
                if (pendingDiscoveryListeners[serviceType] === listener) {
                    pendingDiscoveryListeners.remove(serviceType)
                }
                if (activeDiscoveryListeners[serviceType] === listener) {
                    activeDiscoveryListeners.remove(serviceType)
                }
            }
        }

        private fun stopServiceDiscoverySafely(
            serviceType: String,
            listener: NsdManager.DiscoveryListener,
        ) {
            val shouldStop = synchronized(stateLock) {
                stoppedDiscoveryListeners.add(listener)
            }
            if (!shouldStop) return
            try {
                nsdManager?.stopServiceDiscovery(listener)
                logEvent("MDNS_DISCOVERY_STOPPED", serviceType)
            } catch (error: IllegalArgumentException) {
                logWarning("MDNS_STOP_FAILED", error = error, serviceType = serviceType)
            } catch (error: RuntimeException) {
                logWarning("MDNS_STOP_FAILED", error = error, serviceType = serviceType)
            }
        }

        private fun stopServiceResolutionSafely(listener: NsdManager.ResolveListener) {
            val shouldStop = synchronized(stateLock) {
                stoppedResolveListeners.add(listener)
            }
            if (!shouldStop || Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                return
            }
            try {
                nsdManager?.stopServiceResolution(listener)
            } catch (error: IllegalArgumentException) {
                logWarning("MDNS_STOP_FAILED", error = error)
            } catch (error: RuntimeException) {
                logWarning("MDNS_STOP_FAILED", error = error)
            }
        }

        private fun isSessionActive(): Boolean = synchronized(stateLock) {
            isSessionActiveLocked()
        }

        private fun isSessionActiveLocked(): Boolean = state == SessionState.STARTED

        private inline fun callbackBoundary(
            callbackName: String,
            serviceType: String,
            block: () -> Unit,
        ) {
            try {
                block()
            } catch (error: Exception) {
                logWarning(
                    "MDNS_CALLBACK_ERROR",
                    error = error,
                    serviceType = "$serviceType:$callbackName",
                )
            }
        }

        private fun emit(event: MdnsDiscoveryEvent) {
            if (!isSessionActive()) return
            try {
                onEvent(event)
            } catch (error: Exception) {
                logWarning("MDNS_CALLBACK_ERROR", error = error)
            }
        }

        private fun logEvent(event: String, serviceType: String = "") {
            runCatching {
                Log.d(
                    TAG,
                    "$event generation=${request.generation} serviceType=" +
                        serviceType.take(MAX_LOG_SERVICE_TYPE_LENGTH),
                )
            }
        }

        private fun logWarning(
            event: String,
            error: Throwable? = null,
            serviceType: String = "",
            errorCode: Int? = null,
        ) {
            val suffix = buildString {
                if (errorCode != null) append(" errorCode=$errorCode")
                if (error != null) append(" error=${error.javaClass.simpleName}")
            }
            runCatching {
                Log.w(
                    TAG,
                    "$event generation=${request.generation} serviceType=" +
                        serviceType.take(MAX_LOG_SERVICE_TYPE_LENGTH) + suffix,
                )
            }
        }

        private fun acquireMulticastLockIfRequired() {
            if (request.connectionType != com.networktoolbox.core.network.model.ConnectionType.WIFI) return
            if (!requiresManualMulticastLock()) return
            val lock = wifiManager?.createMulticastLock("NetworkToolbox.mDNS") ?: return
            try {
                lock.setReferenceCounted(false)
                lock.acquire()
                val keepLock = synchronized(stateLock) {
                    if (isSessionActiveLocked() && multicastLock == null) {
                        multicastLock = lock
                        true
                    } else {
                        false
                    }
                }
                if (!keepLock) releaseMulticastLockInstance(lock)
            } catch (error: Exception) {
                releaseMulticastLockInstance(lock)
                throw error
            }
        }

        private fun releaseMulticastLock() {
            val lock = synchronized(stateLock) {
                val current = multicastLock
                multicastLock = null
                current
            }
            if (lock != null) releaseMulticastLockInstance(lock)
        }

        private fun releaseMulticastLockInstance(lock: WifiManager.MulticastLock) {
            try {
                if (lock.isHeld) {
                    lock.release()
                }
            } catch (error: RuntimeException) {
                logWarning("MDNS_STOP_FAILED", error = error)
            }
        }

        private fun requiresManualMulticastLock(): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) < 7

        private fun errorCode(error: Throwable): Int = when (error) {
            is IllegalArgumentException -> NsdManager.FAILURE_BAD_PARAMETERS
            else -> NsdManager.FAILURE_INTERNAL_ERROR
        }

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

        private enum class SessionState {
            NEW,
            STARTED,
            STOPPING,
            STOPPED,
            FAILED,
        }

        companion object {
            const val MAX_CONCURRENT_RESOLUTIONS: Int = 2
            const val MAX_TXT_ATTRIBUTE_COUNT: Int = 16
            const val MAX_TXT_KEY_LENGTH: Int = 64
            const val MAX_TXT_VALUE_LENGTH: Int = 256
            const val MAX_TXT_TOTAL_LENGTH: Int = 1_024
        }
    }

    private companion object {
        private const val TAG = "NetworkToolbox.mDNS"
        private const val MAX_LOG_SERVICE_TYPE_LENGTH = 64
    }
}
