package id.cachet.wallet.domain.verification

import id.cachet.wallet.domain.cache.CachedDIDResolver
import id.cachet.wallet.domain.cache.StatusListCache
import id.cachet.wallet.domain.crypto.Base64Url
import id.cachet.wallet.domain.crypto.DIDResolver
import id.cachet.wallet.domain.crypto.JWSVerifier
import id.cachet.wallet.domain.crypto.KBJWTBuilder
import id.cachet.wallet.domain.crypto.SDJWTParser
import id.cachet.wallet.domain.model.PackDefinition
import id.cachet.wallet.domain.model.sha256Hash
import kotlinx.serialization.json.*

/**
 * Local SD-JWT verification pipeline, run entirely on the verifier's device.
 *
 * Port of Go `services/verifier/server.go` lines 172-311 and
 * `services/verifier/internal/eval/sdjwt_parser.go` VerifySDJWT.
 *
 * Steps:
 * 1. Parse SD-JWT → issuerJWT + disclosures + KB-JWT
 * 2. Decode issuer JWT payload → extract iss, _sd_alg, _sd, cnf, status, exp, iat
 * 3. Resolve issuer DID → public key (via CachedDIDResolver)
 * 4. Verify issuer JWS signature
 * 5. Verify _sd_alg == "sha-256"
 * 6. Compute + verify disclosure hashes against _sd array
 * 7. Merge verified claims
 * 8. If KB-JWT: verify via KBJWTVerifier
 * 9. Check freshness
 * 10. Check revocation (best-effort)
 * 11. Evaluate predicates
 */
class LocalVerifier(
    private val cachedDIDResolver: CachedDIDResolver,
    private val jwsVerifier: JWSVerifier = JWSVerifier(),
    private val kbJwtVerifier: KBJWTVerifier = KBJWTVerifier(jwsVerifier),
    private val statusListCache: StatusListCache? = null
) {

    suspend fun verify(
        sdJwtPresentation: String,
        packDefinition: PackDefinition,
        sessionNonce: String? = null,
        verifierDid: String? = null
    ): LocalVerificationResult {
        val warnings = mutableListOf<String>()

        // Step 1: Parse SD-JWT presentation
        val parsed = try {
            SDJWTParser.parsePresentation(sdJwtPresentation)
        } catch (e: Exception) {
            return LocalVerificationResult.VerificationFailed("Parse error: ${e.message}", VerificationStep.PARSE)
        }

        // Step 2: Decode issuer JWT payload (without verification)
        val issuerPayload = try {
            val parts = parsed.issuerJWT.split(".")
            if (parts.size != 3) throw IllegalArgumentException("Invalid JWT structure")
            val payloadJson = Base64Url.decode(parts[1]).decodeToString()
            Json.parseToJsonElement(payloadJson).jsonObject
        } catch (e: Exception) {
            return LocalVerificationResult.VerificationFailed("Failed to decode issuer JWT: ${e.message}", VerificationStep.PARSE)
        }

        val issuer = issuerPayload["iss"]?.jsonPrimitive?.content
            ?: return LocalVerificationResult.VerificationFailed("Missing iss claim", VerificationStep.PARSE)

        // Step 3: Resolve issuer DID to public key
        val publicKeyJWK = try {
            cachedDIDResolver.resolvePublicKeyJWK(issuer)
        } catch (e: Exception) {
            return LocalVerificationResult.VerificationFailed("Failed to resolve issuer DID: ${e.message}", VerificationStep.ISSUER_SIGNATURE)
        }

        // Step 4: Verify issuer JWS signature
        try {
            jwsVerifier.verifyJWS(parsed.issuerJWT, publicKeyJWK, null)
        } catch (e: Exception) {
            return LocalVerificationResult.VerificationFailed("Issuer signature invalid: ${e.message}", VerificationStep.ISSUER_SIGNATURE)
        }

        // Step 5: Verify _sd_alg == "sha-256"
        val sdAlg = issuerPayload["_sd_alg"]?.jsonPrimitive?.content
        if (sdAlg != "sha-256") {
            return LocalVerificationResult.VerificationFailed("Unsupported _sd_alg: $sdAlg (only sha-256 accepted)", VerificationStep.SD_ALG)
        }

        // Step 6: Compute + verify disclosure hashes against _sd array
        val sdArray = issuerPayload["_sd"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }?.toSet()
            ?: emptySet()

        val mergedClaims = mutableMapOf<String, JsonElement>()
        for (disc in parsed.disclosures) {
            val disclosureHash = computeDisclosureHash(disc.encoded)
            if (disclosureHash !in sdArray) {
                return LocalVerificationResult.VerificationFailed(
                    "Disclosure hash for claim '${disc.claimName}' not found in _sd array",
                    VerificationStep.DISCLOSURE_HASH
                )
            }
            mergedClaims[disc.claimName] = disc.value
        }

        // Step 7: Build verified claims
        val iat = issuerPayload["iat"]?.jsonPrimitive?.longOrNull
        val exp = issuerPayload["exp"]?.jsonPrimitive?.longOrNull
        val status = issuerPayload["status"]?.jsonObject
        var holderBound = false
        var kbNonce: String? = null
        var kbAud: String? = null

        // Step 8: Verify KB-JWT if present
        if (parsed.kbJwt != null) {
            val cnf = issuerPayload["cnf"]?.jsonObject
            if (cnf == null) {
                return LocalVerificationResult.VerificationFailed(
                    "KB-JWT present but credential has no cnf claim",
                    VerificationStep.KB_JWT
                )
            }

            val expectedSDHash = KBJWTBuilder.computeSDHash(parsed.sdJwtForHash)
            val kbResult = try {
                kbJwtVerifier.verify(parsed.kbJwt, cnf, expectedSDHash)
            } catch (e: Exception) {
                return LocalVerificationResult.VerificationFailed("KB-JWT verification failed: ${e.message}", VerificationStep.KB_JWT)
            }

            // Verify nonce if session context provided
            if (sessionNonce != null && kbResult.nonce != sessionNonce) {
                return LocalVerificationResult.VerificationFailed(
                    "Nonce mismatch: expected $sessionNonce, got ${kbResult.nonce}",
                    VerificationStep.NONCE
                )
            }

            // Verify audience if verifier DID provided
            if (verifierDid != null && kbResult.aud != verifierDid) {
                return LocalVerificationResult.VerificationFailed(
                    "Audience mismatch: expected $verifierDid, got ${kbResult.aud}",
                    VerificationStep.AUDIENCE
                )
            }

            holderBound = true
            kbNonce = kbResult.nonce
            kbAud = kbResult.aud
        }

        val verifiedClaims = VerifiedClaims(
            issuer = issuer,
            claims = mergedClaims,
            issuedAt = iat,
            expiration = exp,
            status = status,
            holderBound = holderBound,
            kbJwtNonce = kbNonce,
            kbJwtAud = kbAud
        )

        // Step 9: Check freshness
        val freshness = FreshnessChecker.check(iat, exp)
        if (freshness == "expired") {
            return LocalVerificationResult.VerificationFailed("Credential has expired", VerificationStep.EXPIRED)
        }

        // Step 10: Check revocation (best-effort)
        var revocationChecked = false
        if (status != null && statusListCache != null) {
            val statusListUrl = status["statusListCredential"]?.jsonPrimitive?.content
            val statusListIndex = status["statusListIndex"]?.jsonPrimitive?.content?.toIntOrNull()
            if (statusListUrl != null && statusListIndex != null) {
                try {
                    val isRevoked = statusListCache.isRevoked(statusListUrl, statusListIndex)
                    if (isRevoked == true) {
                        return LocalVerificationResult.VerificationFailed("Credential has been revoked", VerificationStep.REVOKED)
                    }
                    revocationChecked = (isRevoked != null)
                } catch (e: Exception) {
                    warnings.add("Revocation check failed: ${e.message}")
                }
            }
        } else if (status != null) {
            warnings.add("Status list cache unavailable, revocation check skipped")
        }

        // Step 11: Evaluate predicates
        val evaluation = PredicateEvaluator.evaluate(packDefinition, listOf(verifiedClaims))

        val badge = if (evaluation.summary.cachetGranted) packDefinition.badge.label else ""
        val success = LocalVerificationResult.Success(
            badge = badge,
            freshness = freshness,
            predicateResults = evaluation.predicateResults,
            summary = evaluation.summary,
            holderBound = holderBound,
            revocationChecked = revocationChecked
        )

        return if (warnings.isNotEmpty()) {
            LocalVerificationResult.Degraded(result = success, warnings = warnings)
        } else {
            success
        }
    }

    /**
     * Compute disclosure hash: base64url(sha256(encoded_disclosure))
     */
    private fun computeDisclosureHash(encodedDisclosure: String): String {
        val hexHash = sha256Hash(encodedDisclosure)
        val hashBytes = hexHash.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return Base64Url.encode(hashBytes)
    }
}
