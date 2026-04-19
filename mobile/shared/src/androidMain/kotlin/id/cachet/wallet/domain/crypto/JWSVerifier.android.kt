package id.cachet.wallet.domain.crypto

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.jwk.ECKey

actual class JWSVerifier actual constructor() {

    actual fun verify(jwsCompact: String, publicKeyJWK: String): String {
        return verifyJWS(jwsCompact, publicKeyJWK, "oauth-authz-req+jwt")
    }

    actual fun verifyJWS(jwsCompact: String, publicKeyJWK: String, expectedTyp: String?): String {
        val jwsObject = JWSObject.parse(jwsCompact)

        // Verify typ header if expected
        if (expectedTyp != null) {
            val typ = jwsObject.header.type?.type
            if (typ != null && typ != expectedTyp) {
                throw SecurityException("Invalid JWS typ: expected $expectedTyp, got $typ")
            }
        }

        // Verify algorithm
        if (jwsObject.header.algorithm != JWSAlgorithm.ES256) {
            throw SecurityException("Invalid JWS algorithm: expected ES256, got ${jwsObject.header.algorithm}")
        }

        // Verify signature
        val ecKey = ECKey.parse(publicKeyJWK)
        val verifier = ECDSAVerifier(ecKey)
        if (!jwsObject.verify(verifier)) {
            throw SecurityException("JWS signature verification failed")
        }

        // Verify expiration (only for tokens that have exp)
        val payload = jwsObject.payload.toJSONObject()
        val exp = (payload["exp"] as? Number)?.toLong()
        if (exp != null && exp < System.currentTimeMillis() / 1000) {
            throw SecurityException("Token has expired")
        }

        return jwsObject.payload.toString()
    }
}
