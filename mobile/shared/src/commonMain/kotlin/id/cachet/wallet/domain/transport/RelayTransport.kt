package id.cachet.wallet.domain.transport

import id.cachet.wallet.config.AppConfig
import id.cachet.wallet.domain.crypto.Base64Url
import id.cachet.wallet.domain.crypto.DIDResolver
import id.cachet.wallet.domain.crypto.JWSVerifier
import id.cachet.wallet.network.RelayClient
import id.cachet.wallet.network.VerifierClient
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Relay-based transport: the existing online verification flow.
 * Verifier creates backend session → posts to relay → holder fetches → responds → verifier polls.
 */
class RelayTransport(
    private val verifierClient: VerifierClient,
    private val relayClient: RelayClient,
    private val didResolver: DIDResolver? = null
) : VerificationTransport {

    companion object {
        private const val POLL_INTERVAL_MS = 1000L
        private const val POLL_TIMEOUT_MS = 5 * 60 * 1000L
    }

    override suspend fun createSession(params: SessionParams): TransportSession {
        require(params.packId.isNotEmpty()) { "packId must not be empty" }

        val session = verifierClient.createSession(
            packId = params.packId,
            question = params.question,
            predicates = params.predicates
        )

        val payloadBytes: ByteArray = if (session.requestObject != null) {
            session.requestObject.encodeToByteArray()
        } else {
            val payload = RelayRequestPayload(
                nonce = session.nonce,
                verifierDid = session.verifierDid,
                packId = params.packId,
                question = params.question,
                predicates = params.predicates
            )
            Json.encodeToString(RelayRequestPayload.serializer(), payload).encodeToByteArray()
        }
        val relaySession = relayClient.createSession(payloadBytes)

        val requestUri = "${AppConfig.relayUrl}${relaySession.requestUri}"
        var qr = "cachet://verify?request_uri=$requestUri"
        if (session.ephemeralPubKey != null) {
            qr += "&vk=${session.ephemeralPubKey}"
        }

        return TransportSession(
            qrPayload = qr,
            responseHandle = relaySession.responseUri,
            packId = params.packId,
            sessionNonce = session.nonce,
            verifierDid = session.verifierDid,
            ephemeralPubKey = session.ephemeralPubKey,
            backendSessionId = session.sessionId
        )
    }

    override suspend fun receiveRequest(sessionData: String): TransportRequest {
        // sessionData is the full QR URI: cachet://verify?request_uri=...&vk=...
        val requestUri = extractParam(sessionData, "request_uri")
            ?: throw IllegalArgumentException("Missing request_uri in relay QR")
        val vk = extractParam(sessionData, "vk")

        val bytes = relayClient.fetchRequest(requestUri)
        val content = bytes.decodeToString()

        // Detect JWS (signed Request Object): 3 dot-separated parts starting with eyJ
        if (content.count { it == '.' } == 2 && content.startsWith("eyJ")) {
            return verifyAndParseRequestObject(content, vk)
        }

        // Fallback: plaintext JSON
        val payload = Json.decodeFromString(RelayRequestPayload.serializer(), content)
        return TransportRequest(
            nonce = payload.nonce,
            verifierDid = payload.verifierDid,
            packId = payload.packId,
            question = payload.question,
            predicates = payload.predicates,
            verifierPubKey = vk,
            isVerified = false
        )
    }

    override suspend fun sendResponse(sessionData: String, payload: ByteArray): String? {
        // sessionData is the full QR URI — derive response URI from request URI
        val requestUri = extractParam(sessionData, "request_uri")
            ?: throw IllegalArgumentException("Missing request_uri")
        val responseUri = requestUri.replace("/request", "/response")
        relayClient.postResponse(responseUri, payload)
        return null // relay transport doesn't return a QR payload
    }

    override suspend fun awaitResponse(session: TransportSession): ByteArray {
        var elapsed = 0L
        while (elapsed < POLL_TIMEOUT_MS) {
            val response = relayClient.pollResponse(session.responseHandle)
            if (response != null) return response
            delay(POLL_INTERVAL_MS)
            elapsed += POLL_INTERVAL_MS
        }
        throw Exception("Verification timed out: holder did not respond within ${POLL_TIMEOUT_MS / 1000}s")
    }

    private suspend fun verifyAndParseRequestObject(jwsCompact: String, vk: String?): TransportRequest {
        val payloadPart = jwsCompact.split(".")[1]
        val payloadJson = Base64Url.decode(payloadPart).decodeToString()
        val unverified = Json.parseToJsonElement(payloadJson).jsonObject
        val clientId = unverified["client_id"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("Missing client_id in request object")

        val headerPart = jwsCompact.split(".")[0]
        val headerJson = Base64Url.decode(headerPart).decodeToString()
        val header = Json.parseToJsonElement(headerJson).jsonObject
        val kid = header["kid"]?.jsonPrimitive?.content

        val resolver = didResolver
            ?: throw IllegalStateException("DID resolver not configured")
        val publicKeyJWK = resolver.resolvePublicKeyJWK(clientId, kid)

        val verifier = JWSVerifier()
        val verifiedPayload = verifier.verify(jwsCompact, publicKeyJWK)

        val claims = Json.parseToJsonElement(verifiedPayload).jsonObject
        val clientMetadata = claims["client_metadata"]?.jsonObject
        val presDefId = claims["presentation_definition"]?.jsonObject?.get("id")?.jsonPrimitive?.content

        val predicates = clientMetadata?.get("predicates")?.jsonArray
            ?.map { it.jsonPrimitive.content } ?: emptyList()

        return TransportRequest(
            nonce = claims["nonce"]?.jsonPrimitive?.content ?: "",
            verifierDid = clientId,
            packId = presDefId ?: "",
            question = clientMetadata?.get("question")?.jsonPrimitive?.content ?: "",
            predicates = predicates,
            verifierPubKey = vk,
            verifierName = clientMetadata?.get("client_name")?.jsonPrimitive?.content,
            isVerified = true
        )
    }
}

@Serializable
internal data class RelayRequestPayload(
    val nonce: String,
    val verifierDid: String,
    val packId: String,
    val question: String,
    val predicates: List<String>
)

/** Extract a query parameter from a URI. */
private fun extractParam(uri: String, key: String): String? {
    val query = uri.substringAfter("?", "")
    return query.split("&")
        .firstOrNull { it.startsWith("$key=") }
        ?.substringAfter("=")
}
