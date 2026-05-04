package id.cachet.wallet.android.trusttrail.provider

import id.cachet.wallet.trusttrail.model.EmailHeader
import id.cachet.wallet.trusttrail.provider.EmailProvider
import id.cachet.wallet.trusttrail.provider.GmailConfig
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.days

/**
 * Gmail API implementation of EmailProvider.
 * Uses a lambda for HTTP calls — the actual HTTP client (Ktor) is injected via Koin.
 * Token is obtained via Google Sign-In and stored in Android Keystore.
 */
class GmailEmailProvider(
    private val tokenProvider: () -> String?,
    private val httpGet: suspend (url: String, headers: Map<String, String>) -> String,
    private val clock: () -> Instant = { Instant.fromEpochMilliseconds(System.currentTimeMillis()) },
) : EmailProvider {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetchHeaders(scanDepthMonths: Int): List<EmailHeader> {
        val token = tokenProvider() ?: return emptyList()

        val now = clock()
        val cutoff = now - (scanDepthMonths * 30).days
        val query = "after:${cutoff.epochSeconds}"
        val authHeaders = mapOf("Authorization" to "Bearer $token")

        val headers = mutableListOf<EmailHeader>()
        var pageToken: String? = null

        do {
            val url = buildString {
                append("${GmailConfig.API_BASE_URL}/users/me/messages")
                append("?q=$query&maxResults=100")
                if (pageToken != null) append("&pageToken=$pageToken")
            }

            val responseBody = httpGet(url, authHeaders)
            val listResponse = json.decodeFromString<JsonObject>(responseBody)

            val messages = listResponse["messages"]?.jsonArray ?: break
            pageToken = listResponse["nextPageToken"]?.jsonPrimitive?.content

            for (msg in messages) {
                val msgId = msg.jsonObject["id"]?.jsonPrimitive?.content ?: continue
                val emailHeader = fetchMessageMetadata(token, msgId)
                if (emailHeader != null) {
                    headers.add(emailHeader)
                }
            }
        } while (pageToken != null)

        return headers
    }

    private suspend fun fetchMessageMetadata(token: String, messageId: String): EmailHeader? {
        val url = buildString {
            append("${GmailConfig.API_BASE_URL}/users/me/messages/$messageId")
            append("?format=${GmailConfig.HEADERS_ONLY_FORMAT}")
            for (h in GmailConfig.METADATA_HEADERS) {
                append("&metadataHeaders=$h")
            }
        }

        val responseBody = httpGet(url, mapOf("Authorization" to "Bearer $token"))
        val response = json.decodeFromString<JsonObject>(responseBody)

        val headersList = response["payload"]
            ?.jsonObject?.get("headers")
            ?.jsonArray ?: return null

        var from: String? = null
        var subject: String? = null
        var date: String? = null

        for (header in headersList) {
            val obj = header.jsonObject
            when (obj["name"]?.jsonPrimitive?.content) {
                "From" -> from = obj["value"]?.jsonPrimitive?.content
                "Subject" -> subject = obj["value"]?.jsonPrimitive?.content
                "Date" -> date = obj["value"]?.jsonPrimitive?.content
            }
        }

        if (from == null) return null

        val domain = from.substringAfterLast('@').trimEnd('>', ' ').lowercase()

        return EmailHeader(
            fromDomain = domain,
            subject = subject ?: "",
            date = parseEmailDate(date),
            messageId = messageId,
        )
    }

    override suspend fun fetchFullContent(messageId: String): EmailProvider.RawEmail? {
        val token = tokenProvider() ?: return null

        val url = "${GmailConfig.API_BASE_URL}/users/me/messages/$messageId?format=RAW"
        val responseBody = httpGet(url, mapOf("Authorization" to "Bearer $token"))
        val response = json.decodeFromString<JsonObject>(responseBody)

        val raw = response["raw"]?.jsonPrimitive?.content ?: return null
        return EmailProvider.RawEmail(
            messageId = messageId,
            from = "",
            subject = "",
            textBody = "",
            htmlBody = "",
            rawMime = decodeBase64Url(raw),
        )
    }

    override fun isConnected(): Boolean = tokenProvider() != null

    private fun parseEmailDate(dateStr: String?): Instant {
        if (dateStr == null) return clock()
        return try {
            Instant.parse(dateStr)
        } catch (_: Exception) {
            clock()
        }
    }

    private fun decodeBase64Url(encoded: String): ByteArray {
        val base64 = encoded.replace('-', '+').replace('_', '/')
        val padding = (4 - base64.length % 4) % 4
        val padded = base64 + "=".repeat(padding)
        return android.util.Base64.decode(padded, android.util.Base64.DEFAULT)
    }
}
