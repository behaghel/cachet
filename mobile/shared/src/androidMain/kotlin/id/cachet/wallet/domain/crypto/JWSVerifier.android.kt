package id.cachet.wallet.domain.crypto

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.jwk.ECKey

actual class JWSVerifier actual constructor() {

    actual fun verify(jwsCompact: String, publicKeyJWK: String): String {
        val jwsObject = JWSObject.parse(jwsCompact)

        // Verify typ header
        val typ = jwsObject.header.type?.type
        if (typ != null && typ != "oauth-authz-req+jwt") {
            throw SecurityException("Invalid JWS typ: expected oauth-authz-req+jwt, got $typ")
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

        // Verify expiration
        val payload = jwsObject.payload.toJSONObject()
        val exp = (payload["exp"] as? Number)?.toLong()
        if (exp != null && exp < System.currentTimeMillis() / 1000) {
            throw SecurityException("Request Object has expired")
        }

        return jwsObject.payload.toString()
    }
}
