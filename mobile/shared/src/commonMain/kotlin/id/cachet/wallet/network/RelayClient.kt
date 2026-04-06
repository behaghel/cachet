package id.cachet.wallet.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

/**
 * HTTP client for the relay service — a stateless message broker
 * that stores opaque request/response payloads for verification sessions.
 */
interface RelayClient {
    /** Create a relay session with the given request payload. */
    suspend fun createSession(requestPayload: ByteArray): RelaySession

    /** Fetch the request payload stored at the given URI. */
    suspend fun fetchRequest(requestUri: String): ByteArray

    /** Post the holder's response (SD-JWT presentation) to the relay. */
    suspend fun postResponse(responseUri: String, payload: ByteArray)

    /** Poll for the holder's response. Returns null if not yet posted (204). */
    suspend fun pollResponse(responseUri: String): ByteArray?
}

@Serializable
data class RelaySession(
    val sessionId: String,
    val requestUri: String,
    val responseUri: String
)

class RelayException(message: String, cause: Throwable? = null) : Exception(message, cause)

class KtorRelayClient(
    private val httpClient: HttpClient,
    private val baseUrl: String
) : RelayClient {

    override suspend fun createSession(requestPayload: ByteArray): RelaySession {
        try {
            val response: HttpResponse = httpClient.post("$baseUrl/sessions") {
                contentType(ContentType.Application.OctetStream)
                setBody(requestPayload)
            }
            if (response.status.isSuccess()) {
                return response.body<RelaySession>()
            }
            throw RelayException("Failed to create relay session: ${response.status}")
        } catch (e: Exception) {
            if (e is RelayException) throw e
            throw RelayException("Network error creating relay session", e)
        }
    }

    override suspend fun fetchRequest(requestUri: String): ByteArray {
        try {
            val url = toAbsoluteUrl(requestUri)
            val response: HttpResponse = httpClient.get(url)
            if (response.status.isSuccess()) {
                return response.body<ByteArray>()
            }
            throw RelayException("Failed to fetch request: ${response.status}")
        } catch (e: Exception) {
            if (e is RelayException) throw e
            throw RelayException("Network error fetching request", e)
        }
    }

    override suspend fun postResponse(responseUri: String, payload: ByteArray) {
        try {
            val url = toAbsoluteUrl(responseUri)
            val response: HttpResponse = httpClient.post(url) {
                contentType(ContentType.Application.OctetStream)
                setBody(payload)
            }
            if (!response.status.isSuccess()) {
                throw RelayException("Failed to post response: ${response.status}")
            }
        } catch (e: Exception) {
            if (e is RelayException) throw e
            throw RelayException("Network error posting response", e)
        }
    }

    override suspend fun pollResponse(responseUri: String): ByteArray? {
        try {
            val url = toAbsoluteUrl(responseUri)
            val response: HttpResponse = httpClient.get(url)
            return when (response.status) {
                HttpStatusCode.OK -> response.body<ByteArray>()
                HttpStatusCode.NoContent -> null // holder hasn't responded yet
                else -> throw RelayException("Failed to poll response: ${response.status}")
            }
        } catch (e: Exception) {
            if (e is RelayException) throw e
            throw RelayException("Network error polling response", e)
        }
    }

    /** Convert relative relay URIs to absolute URLs. */
    private fun toAbsoluteUrl(uri: String): String {
        return if (uri.startsWith("http")) uri else "$baseUrl$uri"
    }
}
