package id.cachet.wallet.domain.crypto

import kotlinx.serialization.json.*

/**
 * Parses SD-JWT strings on the holder side.
 * The holder trusts the issuer (they received the credential from issuance),
 * so no signature verification is needed — just structure parsing for
 * display and selective disclosure during presentation.
 */
object SDJWTParser {

    /**
     * A parsed SD-JWT disclosure: [salt, claimName, value]
     */
    data class Disclosure(
        val encoded: String,
        val salt: String,
        val claimName: String,
        val value: JsonElement
    )

    /**
     * Parsed SD-JWT components.
     */
    data class ParsedSDJWT(
        val issuerJWT: String,
        val disclosures: List<Disclosure>,
        val claims: Map<String, JsonElement>
    )

    /**
     * Parse an SD-JWT string into its components.
     * Extracts the issuer JWT and decodes all disclosures to extract claim names and values.
     */
    fun parse(rawSdJwt: String): ParsedSDJWT {
        val parts = rawSdJwt.split("~")
        require(parts.size >= 2) { "Invalid SD-JWT: expected at least issuer JWT and delimiter" }

        val issuerJWT = parts[0]
        val disclosures = mutableListOf<Disclosure>()
        val claims = mutableMapOf<String, JsonElement>()

        for (i in 1 until parts.size) {
            val part = parts[i]
            if (part.isEmpty()) continue
            // Skip if it looks like a KB-JWT (3 dot-separated parts)
            if (part.count { it == '.' } == 2) continue

            val disclosure = decodeDisclosure(part) ?: continue
            disclosures.add(disclosure)
            claims[disclosure.claimName] = disclosure.value
        }

        return ParsedSDJWT(
            issuerJWT = issuerJWT,
            disclosures = disclosures,
            claims = claims
        )
    }

    /**
     * Select only the disclosures needed for the requested claims.
     * Returns the SD-JWT string with only those disclosures included.
     */
    fun selectivePresentation(
        parsed: ParsedSDJWT,
        requestedClaims: Set<String>
    ): String {
        val selected = parsed.disclosures.filter { it.claimName in requestedClaims }
        return parsed.issuerJWT +
            selected.joinToString("") { "~${it.encoded}" } +
            "~" // trailing ~ for KB-JWT slot
    }

    private fun decodeDisclosure(encoded: String): Disclosure? {
        return try {
            val jsonStr = base64UrlDecode(encoded).decodeToString()
            val arr = Json.parseToJsonElement(jsonStr).jsonArray
            if (arr.size != 3) return null
            Disclosure(
                encoded = encoded,
                salt = arr[0].jsonPrimitive.content,
                claimName = arr[1].jsonPrimitive.content,
                value = arr[2]
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun base64UrlDecode(input: String): ByteArray {
        val base64 = input
            .replace('-', '+')
            .replace('_', '/')
            .let { s ->
                when (s.length % 4) {
                    2 -> "$s=="
                    3 -> "$s="
                    else -> s
                }
            }
        return base64Decode(base64)
    }
}

// Simple multiplatform base64 decoder
internal fun base64Decode(input: String): ByteArray {
    val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val result = mutableListOf<Byte>()
    var i = 0
    while (i < input.length) {
        val c0 = table.indexOf(input[i])
        val c1 = if (i + 1 < input.length) table.indexOf(input[i + 1]) else 0
        val c2 = if (i + 2 < input.length && input[i + 2] != '=') table.indexOf(input[i + 2]) else -1
        val c3 = if (i + 3 < input.length && input[i + 3] != '=') table.indexOf(input[i + 3]) else -1

        result.add(((c0 shl 2) or (c1 shr 4)).toByte())
        if (c2 >= 0) result.add((((c1 and 0x0F) shl 4) or (c2 shr 2)).toByte())
        if (c3 >= 0) result.add((((c2 and 0x03) shl 6) or c3).toByte())
        i += 4
    }
    return result.toByteArray()
}
