package com.networktoolbox.feature.lanscan.data

import android.content.Context
import com.networktoolbox.feature.lanscan.domain.NoOpUpnpDiagnosticLogger
import com.networktoolbox.feature.lanscan.domain.UpnpDescriptionFetcher
import com.networktoolbox.feature.lanscan.domain.UpnpDescriptionParser
import com.networktoolbox.feature.lanscan.domain.UpnpDescriptionRequest
import com.networktoolbox.feature.lanscan.domain.UpnpDeviceDescription
import com.networktoolbox.feature.lanscan.domain.UpnpDiagnosticEvent
import com.networktoolbox.feature.lanscan.domain.UpnpDiagnosticEventType
import com.networktoolbox.feature.lanscan.domain.UpnpDiagnosticLogger
import com.networktoolbox.feature.lanscan.domain.UpnpFailureCategory
import com.networktoolbox.feature.lanscan.domain.UpnpHttpResponsePolicy
import com.networktoolbox.feature.lanscan.domain.UpnpLocationValidator
import com.networktoolbox.feature.lanscan.domain.safeUpnpLocationForLog
import com.networktoolbox.feature.lanscan.domain.UpnpTransportFailureClassifier
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Fetches one explicitly advertised UPnP description without following redirects. */
class AndroidUpnpDescriptionFetcher(
    context: Context,
    private val diagnosticLogger: UpnpDiagnosticLogger = NoOpUpnpDiagnosticLogger,
) : UpnpDescriptionFetcher {
    private val networkSelector = AndroidLanNetworkSelector(context)

    override suspend fun fetch(request: UpnpDescriptionRequest): UpnpDeviceDescription? =
        withContext(Dispatchers.IO) {
            currentCoroutineContext().ensureActive()
            val validation = UpnpLocationValidator.validateDetailed(request.location, request.sourceIp)
            val location = validation.normalizedLocation
            if (location == null) {
                log(
                    UpnpDiagnosticEvent(
                        type = UpnpDiagnosticEventType.UPNP_LOCATION_REJECTED,
                        generation = request.generation,
                        sourceIp = request.sourceIp,
                        location = safeUpnpLocationForLog(request.location),
                        failureCategory = validation.failureCategory ?: UpnpFailureCategory.INVALID_LOCATION,
                    ),
                )
                return@withContext null
            }
            log(
                UpnpDiagnosticEvent(
                    type = UpnpDiagnosticEventType.UPNP_LOCATION_ACCEPTED,
                    generation = request.generation,
                    sourceIp = request.sourceIp,
                    location = safeUpnpLocationForLog(location),
                ),
            )
            val network = networkSelector.select(request)
            if (network == null) {
                logFailure(request, location, UpnpFailureCategory.NETWORK_UNREACHABLE, "LAN network unavailable")
                return@withContext null
            }
            val connection = try {
                network.openConnection(URL(location)) as? HttpURLConnection
            } catch (_: Exception) {
                null
            }
            if (connection == null) {
                logFailure(request, location, UpnpFailureCategory.UNKNOWN, "HTTP connection unavailable")
                return@withContext null
            }
            val cancellationHandle = currentCoroutineContext()[Job]?.invokeOnCompletion {
                runCatching { connection.disconnect() }
            }
            try {
                log(
                    UpnpDiagnosticEvent(
                        type = UpnpDiagnosticEventType.UPNP_FETCH_STARTED,
                        generation = request.generation,
                        sourceIp = request.sourceIp,
                        location = safeUpnpLocationForLog(location),
                        networkIdentity = request.networkIdentity,
                    ),
                )
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.requestMethod = "GET"
                connection.setRequestProperty(
                    "Accept",
                    "text/xml, application/xml, application/octet-stream, */*",
                )
                connection.setRequestProperty("Connection", "close")
                connection.setRequestProperty("User-Agent", "NetworkToolbox/0.2.0")
                try {
                    connection.connect()
                } catch (error: SocketTimeoutException) {
                    logFailure(request, location, UpnpFailureCategory.CONNECT_TIMEOUT, describeException(error))
                    return@withContext null
                } catch (error: Exception) {
                    logFailure(request, location, UpnpTransportFailureClassifier.connect(error), describeException(error))
                    return@withContext null
                }
                currentCoroutineContext().ensureActive()
                log(
                    UpnpDiagnosticEvent(
                        type = UpnpDiagnosticEventType.UPNP_FETCH_CONNECTED,
                        generation = request.generation,
                        sourceIp = request.sourceIp,
                        location = safeUpnpLocationForLog(location),
                    ),
                )
                val status = try {
                    connection.responseCode
                } catch (error: Exception) {
                    logFailure(request, location, UpnpTransportFailureClassifier.read(error), describeException(error))
                    return@withContext null
                }
                val contentLength = connection.contentLengthLong
                val contentType = connection.contentType
                log(
                    UpnpDiagnosticEvent(
                        type = UpnpDiagnosticEventType.UPNP_FETCH_HTTP_STATUS,
                        generation = request.generation,
                        sourceIp = request.sourceIp,
                        location = safeUpnpLocationForLog(location),
                        statusCode = status,
                        contentLength = contentLength,
                        contentType = contentType,
                    ),
                )
                if (!UpnpHttpResponsePolicy.acceptsContentType(contentType)) {
                    logFailure(
                        request,
                        location,
                        UpnpFailureCategory.HTTP_ERROR,
                        "Unsupported content type",
                        status,
                        contentLength,
                        contentType,
                    )
                    return@withContext null
                }
                if (status !in 200..299) {
                    logFailure(
                        request = request,
                        location = location,
                        category = requireNotNull(UpnpHttpResponsePolicy.failureCategory(status)),
                        detail = "HTTP $status",
                        statusCode = status,
                        contentLength = contentLength,
                        contentType = contentType,
                    )
                    return@withContext null
                }
                if (contentLength > MAX_XML_BYTES) {
                    logFailure(
                        request,
                        location,
                        UpnpFailureCategory.RESPONSE_TOO_LARGE,
                        "Content-Length exceeds limit",
                        status,
                        contentLength,
                        contentType,
                    )
                    return@withContext null
                }
                val body = try {
                    connection.inputStream.use(::readBounded)
                } catch (error: ResponseTooLargeException) {
                    logFailure(
                        request,
                        location,
                        UpnpFailureCategory.RESPONSE_TOO_LARGE,
                        "Read limit exceeded",
                        status,
                        contentLength,
                        contentType,
                        error.bytesRead,
                    )
                    return@withContext null
                } catch (error: SocketTimeoutException) {
                    logFailure(
                        request,
                        location,
                        UpnpFailureCategory.READ_TIMEOUT,
                        describeException(error),
                        status,
                        contentLength,
                        contentType,
                    )
                    return@withContext null
                } catch (error: Exception) {
                    logFailure(
                        request,
                        location,
                        UpnpTransportFailureClassifier.read(error),
                        describeException(error),
                        status,
                        contentLength,
                        contentType,
                    )
                    return@withContext null
                }
                currentCoroutineContext().ensureActive()
                log(
                    UpnpDiagnosticEvent(
                        type = UpnpDiagnosticEventType.UPNP_FETCH_BYTES,
                        generation = request.generation,
                        sourceIp = request.sourceIp,
                        location = safeUpnpLocationForLog(location),
                        statusCode = status,
                        contentLength = contentLength,
                        bytesRead = body.size,
                        contentType = contentType,
                    ),
                )
                if (body.isEmpty()) {
                    logFailure(
                        request,
                        location,
                        UpnpFailureCategory.EMPTY_RESPONSE,
                        "Empty response body",
                        status,
                        contentLength,
                        contentType,
                        body.size,
                    )
                    return@withContext null
                }
                log(
                    UpnpDiagnosticEvent(
                        type = UpnpDiagnosticEventType.UPNP_XML_PARSE_STARTED,
                        generation = request.generation,
                        sourceIp = request.sourceIp,
                        location = safeUpnpLocationForLog(location),
                        bytesRead = body.size,
                        contentType = contentType,
                    ),
                )
                val parseResult = UpnpDescriptionParser.parseWithDiagnostics(body)
                val parsed = parseResult.description
                if (parsed == null) {
                    log(
                        UpnpDiagnosticEvent(
                            type = UpnpDiagnosticEventType.UPNP_XML_PARSE_FAILED,
                            generation = request.generation,
                            sourceIp = request.sourceIp,
                            location = safeUpnpLocationForLog(location),
                            failureCategory = parseResult.failureCategory ?: UpnpFailureCategory.INVALID_XML,
                            detail = parseResult.failureDetail,
                        ),
                    )
                    return@withContext null
                }
                log(
                    UpnpDiagnosticEvent(
                        type = UpnpDiagnosticEventType.UPNP_XML_PARSE_SUCCESS,
                        generation = request.generation,
                        sourceIp = request.sourceIp,
                        location = safeUpnpLocationForLog(location),
                        failureCategory = parseResult.failureCategory,
                        detail = if (parseResult.embeddedDevicePresent) {
                            "EMBEDDED_DEVICE_PRESENT"
                        } else {
                            null
                        },
                    ),
                )
                if (parsed.hasIdentity) {
                    log(
                        UpnpDiagnosticEvent(
                            type = UpnpDiagnosticEventType.UPNP_DEVICE_PARSED,
                            generation = request.generation,
                            sourceIp = request.sourceIp,
                            location = safeUpnpLocationForLog(location),
                        ),
                    )
                } else {
                    log(
                        UpnpDiagnosticEvent(
                            type = UpnpDiagnosticEventType.UPNP_DEVICE_IGNORED,
                            generation = request.generation,
                            sourceIp = request.sourceIp,
                            location = safeUpnpLocationForLog(location),
                            failureCategory = UpnpFailureCategory.NO_IDENTITY_FIELDS,
                            detail = buildString {
                                append("ROOT_IDENTITY_EMPTY")
                                if (parseResult.embeddedDevicePresent) append(",EMBEDDED_DEVICE_PRESENT")
                            },
                        ),
                    )
                }
                parsed
            } catch (error: CancellationException) {
                logFailure(request, location, UpnpFailureCategory.CANCELLED, describeException(error))
                throw error
            } catch (error: Exception) {
                logFailure(request, location, UpnpTransportFailureClassifier.read(error), describeException(error))
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
            if (output.size() > MAX_XML_BYTES) throw ResponseTooLargeException(output.size())
        }
        return output.toByteArray()
    }

    private fun log(event: UpnpDiagnosticEvent) {
        runCatching { diagnosticLogger.log(event) }
    }

    private fun logFailure(
        request: UpnpDescriptionRequest,
        location: String,
        category: UpnpFailureCategory,
        detail: String? = null,
        statusCode: Int? = null,
        contentLength: Long? = null,
        contentType: String? = null,
        bytesRead: Int? = null,
    ) {
        log(
            UpnpDiagnosticEvent(
                type = UpnpDiagnosticEventType.UPNP_FETCH_FAILED,
                generation = request.generation,
                sourceIp = request.sourceIp,
                location = safeUpnpLocationForLog(location),
                networkIdentity = request.networkIdentity,
                statusCode = statusCode,
                contentLength = contentLength,
                bytesRead = bytesRead,
                contentType = contentType,
                failureCategory = category,
                detail = detail,
            ),
        )
    }

    private fun describeException(error: Throwable): String = buildString {
        append(error::class.java.simpleName)
        error.message
            ?.filter { character -> character.code >= 0x20 && character.code != 0x7F }
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { message -> append(": ").append(message.take(MAX_ERROR_DETAIL_LENGTH)) }
    }

    private class ResponseTooLargeException(
        val bytesRead: Int,
    ) : IOException("UPnP XML response exceeded the size limit.")

    private companion object {
        private const val CONNECT_TIMEOUT_MS = 1_800
        private const val READ_TIMEOUT_MS = 2_200
        private const val MAX_XML_BYTES = 384 * 1024
        private const val MAX_ERROR_DETAIL_LENGTH = 160
    }
}
