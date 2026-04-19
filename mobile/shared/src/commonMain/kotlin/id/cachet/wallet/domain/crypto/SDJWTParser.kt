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
     * A parsed SD-JWT presentation including the KB-JWT (used for verification).
     */
    data class ParsedPresentation(
        val issuerJWT: String,
        val disclosures: List<Disclosure>,
        val claims: Map<String, JsonElement>,
        val kbJwt: String?,
        /** The SD-JWT content without KB-JWT (issuerJWT~disc1~disc2~...~) for sd_hash computation. */
        val sdJwtForHash: String
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
     * Parse an SD-JWT presentation, extracting the KB-JWT if present.
     * Used by the local verifier to access all components for verification.
     */
    fun parsePresentation(rawPresentation: String): ParsedPresentation {
        val parts = rawPresentation.split("~")
        require(parts.size >= 2) { "Invalid SD-JWT: expected at least issuer JWT and delimiter" }

        val issuerJWT = parts[0]
        val disclosures = mutableListOf<Disclosure>()
        val claims = mutableMapOf<String, JsonElement>()
        var kbJwt: String? = null
        val sdJwtParts = mutableListOf(issuerJWT)

        for (i in 1 until parts.size) {
            val part = parts[i]
            if (part.isEmpty()) continue

            if (part.count { it == '.' } == 2) {
                // This is a KB-JWT
                kbJwt = part
                continue
            }

            val disclosure = decodeDisclosure(part) ?: continue
            disclosures.add(disclosure)
            claims[disclosure.claimName] = disclosure.value
            sdJwtParts.add(part)
        }

        // sdJwtForHash = issuerJWT~disc1~disc2~...~ (trailing ~, no KB-JWT)
        val sdJwtForHash = sdJwtParts.joinToString("~") + "~"

        return ParsedPresentation(
            issuerJWT = issuerJWT,
            disclosures = disclosures,
            claims = claims,
            kbJwt = kbJwt,
            sdJwtForHash = sdJwtForHash
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
            val jsonStr = Base64Url.decode(encoded).decodeToString()
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
}
