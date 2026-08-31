package com.networktoolbox.feature.lanscan.data

import android.content.Context
import android.util.Log
import com.networktoolbox.feature.lanscan.domain.UpnpDescriptionFetcher
import com.networktoolbox.feature.lanscan.domain.UpnpDescriptionParser
import com.networktoolbox.feature.lanscan.domain.UpnpDescriptionRequest
import com.networktoolbox.feature.lanscan.domain.UpnpDeviceDescription
import com.networktoolbox.feature.lanscan.domain.UpnpLocationValidator
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Fetches one explicitly advertised UPnP description without following redirects. */
class AndroidUpnpDescriptionFetcher(context: Context) : UpnpDescriptionFetcher {
    private val networkSelector = AndroidLanNetworkSelector(context)

    override suspend fun fetch(request: UpnpDescriptionRequest): UpnpDeviceDescription? =
        withContext(Dispatchers.IO) {
            currentCoroutineContext().ensureActive()
            if (UpnpLocationValidator.validate(request.location, request.sourceIp) == null) {
                return@withContext null
            }
            val network = networkSelector.select(request) ?: return@withContext null
            val connection = try {
                network.openConnection(URL(request.location)) as? HttpURLConnection
            } catch (_: Exception) {
                null
            } ?: return@withContext null
            val cancellationHandle = currentCoroutineContext()[Job]?.invokeOnCompletion {
                runCatching { connection.disconnect() }
            }
            try {
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "text/xml, application/xml")
                connection.connect()
                currentCoroutineContext().ensureActive()
                if (connection.responseCode !in 200..299) return@withContext null
                if (connection.contentLengthLong > MAX_XML_BYTES) return@withContext null
                val body = connection.inputStream.use(::readBounded)
                currentCoroutineContext().ensureActive()
                val parsed = UpnpDescriptionParser.parse(body)
                if (parsed != null) logDebug("UPNP_DESCRIPTION_PARSED generation=${request.generation}")
                parsed
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                logDebug("UPNP_DESCRIPTION_FAILED generation=${request.generation}")
                null
            } finally {
                cancellationHandle?.dispose()
                runCatching { connection.disconnect() }
            }
        }

    private fun readBounded(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
            if (output.size() > MAX_XML_BYTES) throw IllegalArgumentException("UPnP XML is too large.")
        }
        return output.toByteArray()
    }

    private fun logDebug(message: String) {
        runCatching { Log.d(TAG, message.take(MAX_LOG_LENGTH)) }
    }

    private companion object {
        private const val TAG = "NetworkToolbox.UPnP"
        private const val CONNECT_TIMEOUT_MS = 1_800
        private const val READ_TIMEOUT_MS = 2_200
        private const val MAX_XML_BYTES = 384 * 1024
        private const val MAX_LOG_LENGTH = 256
    }
}
