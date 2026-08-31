package com.networktoolbox.feature.lanscan.domain

import java.net.URI
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale

/** Events used to diagnose the bounded SSDP -> UPnP description pipeline. */
enum class UpnpDiagnosticEventType {
    SSDP_START,
    SSDP_RESPONSE_RECEIVED,
    SSDP_TIMEOUT,
    SSDP_STOP,
    UPNP_LOCATION_ACCEPTED,
    UPNP_LOCATION_REJECTED,
    UPNP_FETCH_STARTED,
    UPNP_FETCH_CONNECTED,
    UPNP_FETCH_HTTP_STATUS,
    UPNP_FETCH_BYTES,
    UPNP_FETCH_FAILED,
    UPNP_XML_PARSE_STARTED,
    UPNP_XML_PARSE_SUCCESS,
    UPNP_XML_PARSE_FAILED,
    UPNP_DEVICE_PARSED,
    UPNP_DEVICE_ASSOCIATED,
    UPNP_DEVICE_IGNORED,
}

/** Stable, non-user-facing categories for fetch and parser failures. */
enum class UpnpFailureCategory {
    INVALID_LOCATION,
    LOCATION_HOST_MISMATCH,
    UNSUPPORTED_SCHEME,
    CONNECT_TIMEOUT,
    READ_TIMEOUT,
    CONNECTION_REFUSED,
    NETWORK_UNREACHABLE,
    HTTP_ERROR,
    REDIRECT_REJECTED,
    RESPONSE_TOO_LARGE,
    EMPTY_RESPONSE,
    INVALID_XML,
    XML_SECURITY_REJECTED,
    NO_ROOT_DEVICE,
    NO_IDENTITY_FIELDS,
    DEVICE_IP_NOT_IN_SCAN_RESULTS,
    CANCELLED,
    STALE_GENERATION,
    UNKNOWN,
}

data class UpnpDiagnosticEvent(
    val type: UpnpDiagnosticEventType,
    val generation: Long? = null,
    val sourceIp: String? = null,
    /** A bounded, sanitized LOCATION representation; never raw XML or headers. */
    val location: String? = null,
    val networkIdentity: String? = null,
    val statusCode: Int? = null,
    val contentLength: Long? = null,
    val bytesRead: Int? = null,
    val contentType: String? = null,
    val failureCategory: UpnpFailureCategory? = null,
    val detail: String? = null,
)

fun interface UpnpDiagnosticLogger {
    fun log(event: UpnpDiagnosticEvent)
}

object NoOpUpnpDiagnosticLogger : UpnpDiagnosticLogger {
    override fun log(event: UpnpDiagnosticEvent) = Unit
}

object UpnpHttpResponsePolicy {
    fun failureCategory(statusCode: Int): UpnpFailureCategory? = when (statusCode) {
        in 200..299 -> null
        in 300..399 -> UpnpFailureCategory.REDIRECT_REJECTED
        else -> UpnpFailureCategory.HTTP_ERROR
    }

    /** Content-Type is diagnostic metadata, not a reason to reject a bounded XML body. */
    fun acceptsContentType(contentType: String?): Boolean = true
}

object UpnpTransportFailureClassifier {
    fun connect(error: Throwable): UpnpFailureCategory = when {
        error is SocketTimeoutException -> UpnpFailureCategory.CONNECT_TIMEOUT
        error is NoRouteToHostException -> UpnpFailureCategory.NETWORK_UNREACHABLE
        error is UnknownHostException -> UpnpFailureCategory.NETWORK_UNREACHABLE
        error is ConnectException -> UpnpFailureCategory.CONNECTION_REFUSED
        error.message.orEmpty().containsAnyIgnoreCase("refused", "econnrefused") ->
            UpnpFailureCategory.CONNECTION_REFUSED
        error.message.orEmpty().containsAnyIgnoreCase("unreachable", "no route", "network is down") ->
            UpnpFailureCategory.NETWORK_UNREACHABLE
        else -> UpnpFailureCategory.UNKNOWN
    }

    fun read(error: Throwable): UpnpFailureCategory = when {
        error is SocketTimeoutException -> UpnpFailureCategory.READ_TIMEOUT
        error is NoRouteToHostException -> UpnpFailureCategory.NETWORK_UNREACHABLE
        error is UnknownHostException -> UpnpFailureCategory.NETWORK_UNREACHABLE
        error.message.orEmpty().containsAnyIgnoreCase("unreachable", "no route", "network is down") ->
            UpnpFailureCategory.NETWORK_UNREACHABLE
        else -> UpnpFailureCategory.UNKNOWN
    }
}

data class UpnpLocationValidationResult(
    val normalizedLocation: String?,
    val failureCategory: UpnpFailureCategory? = null,
) {
    val isAccepted: Boolean
        get() = normalizedLocation != null
}

data class UpnpParseResult(
    val description: UpnpDeviceDescription? = null,
    val failureCategory: UpnpFailureCategory? = null,
    val rootDevicePresent: Boolean = false,
    val embeddedDevicePresent: Boolean = false,
    val failureDetail: String? = null,
)

/** Returns only bounded URL metadata useful for logs, including the path. */
internal fun safeUpnpLocationForLog(location: String?): String {
    if (location.isNullOrBlank()) return "<missing>"
    val uri = runCatching { URI(location) }.getOrNull() ?: return "<invalid>"
    val scheme = uri.scheme?.lowercase(Locale.US) ?: return "<invalid>"
    val host = uri.host ?: return "<invalid>"
    val formattedHost = if (host.contains(':')) "[$host]" else host
    val port = if (uri.port >= 0) ":${uri.port}" else ""
    val path = uri.rawPath.orEmpty().take(MAX_LOGGED_LOCATION_PATH_LENGTH)
    return "$scheme://$formattedHost$port$path".take(MAX_LOGGED_LOCATION_LENGTH)
}

private const val MAX_LOGGED_LOCATION_LENGTH = 256
private const val MAX_LOGGED_LOCATION_PATH_LENGTH = 160

private fun String.containsAnyIgnoreCase(vararg values: String): Boolean =
    values.any { value -> contains(value, ignoreCase = true) }
