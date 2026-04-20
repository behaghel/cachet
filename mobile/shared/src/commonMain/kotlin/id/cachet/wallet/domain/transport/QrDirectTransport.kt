package id.cachet.wallet.domain.transport

/**
 * Proximity transport: two-QR dance for fully offline verification.
 *
 * 1. Verifier creates local session → displays session QR
 * 2. Holder scans QR → parses params → builds VP → displays VP QR
 * 3. Verifier scans VP QR → decrypts → verifies locally
 *
 * No network calls. No relay. Both devices can be in airplane mode.
 */
class QrDirectTransport(
    private val sessionManager: LocalSessionManager
) : VerificationTransport {

    companion object {
        const val SCHEME = "cachet://proximity"
        const val VP_PREFIX = "cachet-vp:"
        const val MAX_QR_PAYLOAD_BYTES = 2500
    }

    override suspend fun createSession(params: SessionParams): TransportSession {
        val session = sessionManager.createSession(params)

        val qr = buildProximityUri(session)

        return TransportSession(
            qrPayload = qr,
            responseHandle = session.nonce, // nonce identifies the session for proximity
            packId = params.packId,
            sessionNonce = session.nonce,
            verifierDid = "did:key:proximity-session", // local ephemeral identity
            ephemeralPrivKey = session.keyPair.privateKeyBase64URL,
            ephemeralPubKey = session.keyPair.publicKeyBase64URL
        )
    }

    override suspend fun receiveRequest(sessionData: String): TransportRequest {
        return parseProximityUri(sessionData)
    }

    override suspend fun sendResponse(sessionData: String, payload: ByteArray): String {
        val encoded = payload.encodeToBase64Url()
        val qrPayload = "$VP_PREFIX$encoded"

        if (qrPayload.encodeToByteArray().size > MAX_QR_PAYLOAD_BYTES) {
            throw PayloadTooLargeException(
                "Encrypted VP is ${qrPayload.encodeToByteArray().size} bytes, " +
                    "exceeds QR capacity ($MAX_QR_PAYLOAD_BYTES bytes). " +
                    "Use online verification instead."
            )
        }

        return qrPayload
    }

    override suspend fun awaitResponse(session: TransportSession): ByteArray {
        // Not used for proximity — the verifier scans the QR directly.
        // The QR content is passed to verifyProximityResponse() in the ViewModel.
        throw UnsupportedOperationException(
            "Proximity transport does not support polling. " +
                "Use decodeVpQrPayload() after scanning the holder's QR."
        )
    }
}

/** Thrown when the VP payload exceeds QR capacity. */
class PayloadTooLargeException(message: String) : Exception(message)

/** Build the proximity session URI for the verifier's QR. */
fun buildProximityUri(session: ProximitySession): String {
    val encodedQ = urlEncode(session.question)
    val preds = session.predicates.joinToString(",")
    return "${QrDirectTransport.SCHEME}?" +
        "n=${session.nonce}" +
        "&vk=${session.keyPair.publicKeyBase64URL}" +
        "&pack=${session.packId}" +
        "&q=$encodedQ" +
        "&p=$preds"
}

/** Parse a proximity session URI from the holder's QR scan. */
fun parseProximityUri(uri: String): TransportRequest {
    require(uri.startsWith("${QrDirectTransport.SCHEME}?")) {
        "Not a proximity URI: expected ${QrDirectTransport.SCHEME}?..."
    }

    val query = uri.substringAfter("?")
    val params = parseQueryParams(query)

    val nonce = params["n"] ?: throw IllegalArgumentException("Missing nonce (n) in proximity URI")
    val vk = params["vk"] ?: throw IllegalArgumentException("Missing ephemeral key (vk) in proximity URI")
    val packId = params["pack"] ?: throw IllegalArgumentException("Missing pack ID (pack) in proximity URI")
    val question = urlDecode(params["q"] ?: "")
    val predicates = params["p"]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()

    return TransportRequest(
        nonce = nonce,
        verifierDid = "did:key:proximity-session",
        packId = packId,
        question = question,
        predicates = predicates,
        verifierPubKey = vk,
        isVerified = false // proximity URIs are not signed (future: Phase 5)
    )
}

/** Decode the VP from a holder's response QR payload. */
fun decodeVpQrPayload(qrContent: String): ByteArray {
    require(qrContent.startsWith(QrDirectTransport.VP_PREFIX)) {
        "Not a VP QR: expected ${QrDirectTransport.VP_PREFIX}..."
    }
    val encoded = qrContent.removePrefix(QrDirectTransport.VP_PREFIX)
    return encoded.decodeFromBase64Url()
}

/** Check if a QR payload is a proximity session URI. */
fun isProximityUri(qrContent: String): Boolean =
    qrContent.startsWith("${QrDirectTransport.SCHEME}?")

/** Check if a QR payload is a proximity VP response. */
fun isVpQrPayload(qrContent: String): Boolean =
    qrContent.startsWith(QrDirectTransport.VP_PREFIX)

// ── URL encoding helpers (no platform dependency) ──

private fun parseQueryParams(query: String): Map<String, String> {
    return query.split("&")
        .filter { it.contains("=") }
        .associate { param ->
            val (key, value) = param.split("=", limit = 2)
            key to value
        }
}

private fun urlEncode(value: String): String =
    value.replace(" ", "%20")
        .replace("&", "%26")
        .replace("=", "%3D")
        .replace("?", "%3F")
        .replace("#", "%23")

private fun urlDecode(value: String): String =
    value.replace("%20", " ")
        .replace("%26", "&")
        .replace("%3D", "=")
        .replace("%3F", "?")
        .replace("%23", "#")

// ── Base64url helpers (no padding) ──

private val BASE64URL_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

internal fun ByteArray.encodeToBase64Url(): String {
    val sb = StringBuilder()
    var i = 0
    while (i < size) {
        val b0 = this[i].toInt() and 0xFF
        sb.append(BASE64URL_CHARS[(b0 shr 2) and 0x3F])
        if (i + 1 < size) {
            val b1 = this[i + 1].toInt() and 0xFF
            sb.append(BASE64URL_CHARS[((b0 shl 4) or (b1 shr 4)) and 0x3F])
            if (i + 2 < size) {
                val b2 = this[i + 2].toInt() and 0xFF
                sb.append(BASE64URL_CHARS[((b1 shl 2) or (b2 shr 6)) and 0x3F])
                sb.append(BASE64URL_CHARS[b2 and 0x3F])
            } else {
                sb.append(BASE64URL_CHARS[(b1 shl 2) and 0x3F])
            }
        } else {
            sb.append(BASE64URL_CHARS[(b0 shl 4) and 0x3F])
        }
        i += 3
    }
    return sb.toString()
}

private val BASE64URL_DECODE = IntArray(128) { -1 }.also { arr ->
    BASE64URL_CHARS.forEachIndexed { idx, c -> arr[c.code] = idx }
}

internal fun String.decodeFromBase64Url(): ByteArray {
    val output = mutableListOf<Byte>()
    var i = 0
    while (i < length) {
        val c0 = BASE64URL_DECODE[this[i].code]
        val c1 = if (i + 1 < length) BASE64URL_DECODE[this[i + 1].code] else 0
        output.add(((c0 shl 2) or (c1 shr 4)).toByte())
        if (i + 2 < length) {
            val c2 = BASE64URL_DECODE[this[i + 2].code]
            output.add((((c1 and 0x0F) shl 4) or (c2 shr 2)).toByte())
            if (i + 3 < length) {
                val c3 = BASE64URL_DECODE[this[i + 3].code]
                output.add((((c2 and 0x03) shl 6) or c3).toByte())
            }
        }
        i += 4
    }
    return output.toByteArray()
}
