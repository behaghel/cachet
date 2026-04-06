package id.cachet.wallet.domain.crypto

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.json.*

/**
 * Resolves did:web DIDs to their verification public keys.
 *
 * did:web:example.com → https://example.com/.well-known/did.json
 * did:web:example.com%3A8081 → http://example.com:8081/.well-known/did.json (dev mode)
 */
class DIDResolver(private val httpClient: HttpClient) {

    /**
     * Resolve a did:web DID to its public key JWK JSON string.
     *
     * @param did The DID to resolve (e.g., "did:web:verifier.cachet.id")
     * @param kid The key ID to look up (e.g., "did:web:verifier.cachet.id#key-1"). Defaults to did#key-1.
     * @return The public key as a JWK JSON string
     */
    suspend fun resolvePublicKeyJWK(did: String, kid: String? = null): String {
        require(did.startsWith("did:web:")) { "Only did:web is supported, got: $did" }

        val hostPort = did.removePrefix("did:web:").replace("%3A", ":")
        // Use HTTP for local dev (ports like 10.0.2.2:8081), HTTPS for production
        val scheme = if (hostPort.contains(":") || hostPort.startsWith("10.") || hostPort.startsWith("localhost")) "http" else "https"
        val url = "$scheme://$hostPort/.well-known/did.json"

        val responseText: String = httpClient.get(url).body()
        val doc = Json.parseToJsonElement(responseText).jsonObject

        val methods = doc["verificationMethod"]?.jsonArray
            ?: throw Exception("No verificationMethod in DID document from $url")

        val targetKid = kid ?: "$did#key-1"
        val method = methods.firstOrNull {
            it.jsonObject["id"]?.jsonPrimitive?.content == targetKid
        } ?: throw Exception("Key $targetKid not found in DID document")

        return method.jsonObject["publicKeyJwk"]?.toString()
            ?: throw Exception("No publicKeyJwk in verification method $targetKid")
    }
}
