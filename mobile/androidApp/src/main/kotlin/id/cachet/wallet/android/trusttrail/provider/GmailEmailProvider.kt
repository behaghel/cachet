package id.cachet.wallet.android.trusttrail.provider

import id.cachet.wallet.trusttrail.extraction.ClaimExtractor
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

        // Build a platform + subject keyword filter to only fetch transactional emails
        val platformFilter = ClaimExtractor.buildPlatformQuery()
        val query = "in:anywhere after:${cutoff.epochSeconds} ($platformFilter)"
        val authHeaders = mapOf("Authorization" to "Bearer $token")
        android.util.Log.d("TrustTrail", "Scanning with query: $query")

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

            val messages = listResponse["messages"]?.jsonArray
            if (messages == null) {
                android.util.Log.d("TrustTrail", "No messages in response. Full response: ${listResponse.toString().take(500)}")
                break
            }
            android.util.Log.d("TrustTrail", "Page: ${messages.size} messages, nextPage: ${listResponse["nextPageToken"]}")
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
        android.util.Log.d("TrustTrail", "Header: from=$domain subject=${subject?.take(40)}")

        return EmailHeader(
            fromDomain = domain,
            subject = subject ?: "",
            date = parseEmailDate(date),
            messageId = messageId,
        )
    }

    override suspend fun fetchFullContent(messageId: String): EmailProvider.RawEmail? {
        val token = tokenProvider() ?: return null

        // Use FULL format — Gmail returns parsed headers + body parts as JSON
        val url = "${GmailConfig.API_BASE_URL}/users/me/messages/$messageId?format=FULL"
        val responseBody = httpGet(url, mapOf("Authorization" to "Bearer $token"))
        val response = json.decodeFromString<JsonObject>(responseBody)

        val payload = response["payload"]?.jsonObject ?: return null

        // Extract headers
        val headersList = payload["headers"]?.jsonArray
        var from = ""
        var subject = ""
        if (headersList != null) {
            for (header in headersList) {
                val obj = header.jsonObject
                when (obj["name"]?.jsonPrimitive?.content) {
                    "From" -> from = obj["value"]?.jsonPrimitive?.content ?: ""
                    "Subject" -> subject = obj["value"]?.jsonPrimitive?.content ?: ""
                }
            }
        }

        // Extract body — walk the parts tree for text/plain and text/html
        var textBody = ""
        var htmlBody = ""
        extractBodyParts(payload) { mimeType, data ->
            when (mimeType) {
                "text/plain" -> if (textBody.isEmpty()) textBody = decodeBase64UrlString(data)
                "text/html" -> if (htmlBody.isEmpty()) htmlBody = decodeBase64UrlString(data)
            }
        }

        android.util.Log.d("TrustTrail", "FullContent: from=$from subject=${subject.take(40)} textLen=${textBody.length} htmlLen=${htmlBody.length}")

        return EmailProvider.RawEmail(
            messageId = messageId,
            from = from,
            subject = subject,
            textBody = textBody,
            htmlBody = htmlBody,
        )
    }

    /**
     * Recursively walk Gmail payload parts to find text/plain and text/html bodies.
     */
    private fun extractBodyParts(part: JsonObject, callback: (mimeType: String, data: String) -> Unit) {
        val mimeType = part["mimeType"]?.jsonPrimitive?.content ?: ""
        val bodyData = part["body"]?.jsonObject?.get("data")?.jsonPrimitive?.content

        if (bodyData != null && (mimeType == "text/plain" || mimeType == "text/html")) {
            callback(mimeType, bodyData)
        }

        // Recurse into parts (multipart messages)
        val parts = part["parts"]?.jsonArray
        if (parts != null) {
            for (subPart in parts) {
                extractBodyParts(subPart.jsonObject, callback)
            }
        }
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

    private fun decodeBase64UrlString(encoded: String): String {
        val bytes = android.util.Base64.decode(encoded, android.util.Base64.URL_SAFE)
        return String(bytes, Charsets.UTF_8)
    }
}
