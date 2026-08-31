package com.networktoolbox.feature.lanscan.data

import android.util.Log
import com.networktoolbox.feature.lanscan.domain.UpnpDiagnosticEvent
import com.networktoolbox.feature.lanscan.domain.UpnpDiagnosticLogger

/** Debug-only structured logging for one bounded UPnP enrichment run. */
class AndroidUpnpDiagnosticLogger : UpnpDiagnosticLogger {
    override fun log(event: UpnpDiagnosticEvent) {
        val fields = buildList {
            event.generation?.let { add("generation=$it") }
            event.sourceIp?.let { add("source=$it") }
            event.location?.let { add("location=$it") }
            event.networkIdentity?.let { add("network=$it") }
            event.statusCode?.let { add("status=$it") }
            event.contentLength?.let { add("contentLength=$it") }
            event.bytesRead?.let { add("bytes=$it") }
            event.contentType?.let { add("contentType=${it.take(MAX_CONTENT_TYPE_LENGTH)}") }
            event.failureCategory?.let { add("category=$it") }
            event.detail?.let { add("detail=${it.take(MAX_DETAIL_LENGTH)}") }
        }.joinToString(separator = " ")
        runCatching {
            val tag = if (event.type.name.startsWith("SSDP")) SSDP_TAG else UPNP_TAG
            Log.d(tag, "${event.type} $fields".trim().take(MAX_LOG_LENGTH))
        }
    }

    private companion object {
        private const val SSDP_TAG = "NetworkToolbox.SSDP"
        private const val UPNP_TAG = "NetworkToolbox.UPnP"
        private const val MAX_CONTENT_TYPE_LENGTH = 128
        private const val MAX_DETAIL_LENGTH = 128
        private const val MAX_LOG_LENGTH = 768
    }
}
