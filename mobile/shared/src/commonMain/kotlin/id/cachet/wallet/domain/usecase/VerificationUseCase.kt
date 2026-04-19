package id.cachet.wallet.domain.usecase

import id.cachet.wallet.config.AppConfig
import id.cachet.wallet.domain.cache.PackDefinitionCache
import id.cachet.wallet.domain.crypto.Base64Url
import id.cachet.wallet.domain.crypto.DIDResolver
import id.cachet.wallet.domain.crypto.JWEEncryptor
import id.cachet.wallet.domain.crypto.JWSVerifier
import id.cachet.wallet.domain.crypto.KBJWTBuilder
import id.cachet.wallet.domain.crypto.KeyManager
import id.cachet.wallet.domain.crypto.SDJWTParser
import id.cachet.wallet.domain.model.*
import id.cachet.wallet.domain.repository.CredentialRepository
import id.cachet.wallet.domain.verification.LocalVerificationResult
import id.cachet.wallet.domain.verification.LocalVerifier
import id.cachet.wallet.network.*
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Use case for verifying stored credentials against Trust Packs.
 *
 * Supports two flows:
 * - **Direct**: holder sends presentation straight to verifier backend (legacy/test)
 * - **Relay**: verifier creates relay session → holder fetches request, builds
 *   presentation, posts response → verifier polls relay and verifies (cross-device MVP)
 */
class VerificationUseCase(
    private val credentialRepository: CredentialRepository,
    private val verifierClient: VerifierClient,
    private val relayClient: RelayClient,
    private val consentUseCase: ConsentUseCase,
    private val keyManager: KeyManager? = null,
    private val didResolver: DIDResolver? = null,
    private val localVerifier: LocalVerifier? = null,
    private val packDefinitionCache: PackDefinitionCache? = null
) {

    companion object {
        private const val POLL_INTERVAL_MS = 1000L
        private const val POLL_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes (matches relay TTL)
    }

    // ── Relay flow: verifier side ──

    /**
     * Verifier creates a verification + relay session and returns info for the QR code.
     */
    suspend fun startVerifierSession(packId: String, question: String, predicates: List<String>): VerifierSessionInfo {
        require(packId.isNotEmpty()) { "packId must not be empty — check CachPackUi.id is set" }

        val session = verifierClient.createSession(
            packId = packId,
            question = question,
            predicates = predicates
        )

        // Store signed Request Object in relay if available, otherwise fall back to plaintext JSON
        val payloadBytes: ByteArray = if (session.requestObject != null) {
            session.requestObject.encodeToByteArray()
        } else {
            val payload = VerificationRequestPayload(
                nonce = session.nonce,
                verifierDid = session.verifierDid,
                packId = packId,
                question = question,
                predicates = predicates
            )
            Json.encodeToString(VerificationRequestPayload.serializer(), payload).encodeToByteArray()
        }
        val relaySession = relayClient.createSession(payloadBytes)

        val requestUri = "${AppConfig.relayUrl}${relaySession.requestUri}"

        var qr = "cachet://verify?request_uri=$requestUri"
        if (session.ephemeralPubKey != null) {
            qr += "&vk=${session.ephemeralPubKey}"
        }

        return VerifierSessionInfo(
            qrPayload = qr,
            relayResponseUri = relaySession.responseUri,
            verificationSessionId = session.sessionId,
            packId = packId,
            sessionNonce = session.nonce,
            verifierDid = session.verifierDid
        )
    }

    /**
     * Verifier polls the relay for the holder's response, then verifies it.
     * Attempts local verification first; falls back to backend on failure.
     */
    suspend fun awaitAndVerifyRelayResponse(sessionInfo: VerifierSessionInfo): VerificationResult {
        val responseBytes = pollUntilResponse(sessionInfo.relayResponseUri)
        val presentation = responseBytes.decodeToString()

        // Attempt local verification first
        if (localVerifier != null && packDefinitionCache != null) {
            val packDef = packDefinitionCache.getPackById(sessionInfo.packId)
            if (packDef != null) {
                val localResult = localVerifier.verify(
                    sdJwtPresentation = presentation,
                    packDefinition = packDef,
                    sessionNonce = sessionInfo.sessionNonce,
                    verifierDid = sessionInfo.verifierDid
                )
                when (localResult) {
                    is LocalVerificationResult.Success -> return localResult.toVerificationResult(sessionInfo.packId)
                    is LocalVerificationResult.Degraded -> return localResult.result.toVerificationResult(sessionInfo.packId)
                    is LocalVerificationResult.VerificationFailed -> {
                        // Crypto failures are definitive — don't fall back to backend
                        return VerificationResult(
                            packId = sessionInfo.packId,
                            badge = "",
                            freshness = "ok",
                            predicateResults = emptyList(),
                            summary = null,
                            consentReceiptId = null,
                            holderBound = false
                        )
                    }
                }
            }
        }

        // Backend fallback (existing path)
        val response = verifierClient.verifySDJWTPresentation(
            policyId = sessionInfo.packId,
            sdJwtCredentials = listOf(presentation),
            sessionId = sessionInfo.verificationSessionId
        )

        return VerificationResult(
            packId = sessionInfo.packId,
            badge = response.cachet,
            freshness = response.freshness,
            predicateResults = response.predicateResults,
            summary = response.summary,
            consentReceiptId = null,
            holderBound = true
        )
    }

    private fun LocalVerificationResult.Success.toVerificationResult(packId: String) = VerificationResult(
        packId = packId,
        badge = badge,
        freshness = freshness,
        predicateResults = predicateResults,
        summary = summary,
        consentReceiptId = null,
        holderBound = holderBound
    )

    // ── Relay flow: holder side ──

    /**
     * Holder fetches the verification request from the relay.
     * If the content is a signed JWT (JWS), verifies the signature against the verifier's DID
     * and returns verified identity info. Otherwise parses as plaintext JSON.
     */
    suspend fun fetchVerificationRequest(requestUri: String): VerifiedRequest {
        val bytes = relayClient.fetchRequest(requestUri)
        val content = bytes.decodeToString()

        // Detect JWS: 3 dot-separated parts starting with eyJ (base64url JSON)
        if (content.count { it == '.' } == 2 && content.startsWith("eyJ")) {
            return verifyAndParseRequestObject(content)
        }

        // Fallback: plaintext JSON (backward compat)
        val payload = Json.decodeFromString(VerificationRequestPayload.serializer(), content)
        return VerifiedRequest(payload = payload, isVerified = false)
    }

    private suspend fun verifyAndParseRequestObject(jwsCompact: String): VerifiedRequest {
        // Extract client_id from unverified payload to know which DID to resolve
        val payloadPart = jwsCompact.split(".")[1]
        val payloadJson = Base64Url.decode(payloadPart).decodeToString()
        val unverified = Json.parseToJsonElement(payloadJson).jsonObject
        val clientId = unverified["client_id"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("Missing client_id in request object")

        // Extract kid from JWS header
        val headerPart = jwsCompact.split(".")[0]
        val headerJson = Base64Url.decode(headerPart).decodeToString()
        val header = Json.parseToJsonElement(headerJson).jsonObject
        val kid = header["kid"]?.jsonPrimitive?.content

        // Resolve verifier DID to public key
        val resolver = didResolver ?: throw IllegalStateException("DID resolver not configured")
        val publicKeyJWK = resolver.resolvePublicKeyJWK(clientId, kid)

        // Verify JWS signature
        val verifier = JWSVerifier()
        val verifiedPayload = verifier.verify(jwsCompact, publicKeyJWK)

        // Parse verified claims
        val claims = Json.parseToJsonElement(verifiedPayload).jsonObject
        val clientMetadata = claims["client_metadata"]?.jsonObject
        val presDefId = claims["presentation_definition"]?.jsonObject?.get("id")?.jsonPrimitive?.content

        val predicates = clientMetadata?.get("predicates")?.jsonArray
            ?.map { it.jsonPrimitive.content } ?: emptyList()

        return VerifiedRequest(
            payload = VerificationRequestPayload(
                nonce = claims["nonce"]?.jsonPrimitive?.content ?: "",
                verifierDid = clientId,
                packId = presDefId ?: "",
                question = clientMetadata?.get("question")?.jsonPrimitive?.content ?: "",
                predicates = predicates
            ),
            verifierName = clientMetadata?.get("client_name")?.jsonPrimitive?.content,
            isVerified = true
        )
    }

    /**
     * Holder builds an SD-JWT presentation and posts it to the relay.
     */
    suspend fun respondViaRelay(requestUri: String, credentialId: String, verifierPubKey: String? = null) {
        val request = fetchVerificationRequest(requestUri).payload

        val stored = credentialRepository.getCredentialById(credentialId)
            ?: throw Exception("Credential not found: $credentialId")

        val presentation = buildSDJWTPresentation(stored, request.nonce, request.verifierDid)

        // Encrypt to verifier's ephemeral key if available (E2E encryption)
        val payload: ByteArray = if (verifierPubKey != null) {
            val encryptor = JWEEncryptor()
            encryptor.encrypt(presentation.encodeToByteArray(), verifierPubKey).encodeToByteArray()
        } else {
            presentation.encodeToByteArray()
        }

        // Derive response URI from request URI: /sessions/{id}/request → /sessions/{id}/response
        val responseUri = requestUri.replace("/request", "/response")
        relayClient.postResponse(responseUri, payload)
    }

    // ── Direct flow (existing) ──

    suspend fun getAvailablePacks(): Result<List<PackSummary>> {
        return try {
            Result.success(verifierClient.listPacks())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyCredential(
        credentialId: String,
        packId: String
    ): Result<VerificationResult> {
        return try {
            val stored = credentialRepository.getCredentialById(credentialId)
                ?: return Result.failure(Exception("Credential not found: $credentialId"))

            val response: VerifyResponseDTO
            val isSDJWT: Boolean

            if (stored.rawSdJwt != null && stored.keyAlias != null && keyManager != null) {
                response = verifyWithSDJWT(stored, packId)
                isSDJWT = true
            } else {
                response = verifyWithLegacy(stored, packId)
                isSDJWT = false
            }

            val receipt = generateReceiptForVerification(stored.credential, packId, response)

            val result = VerificationResult(
                packId = packId,
                badge = response.cachet,
                freshness = response.freshness,
                predicateResults = response.predicateResults,
                summary = response.summary,
                consentReceiptId = receipt?.id,
                holderBound = isSDJWT
            )

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Internal ──

    private suspend fun verifyWithSDJWT(stored: StoredCredential, packId: String): VerifyResponseDTO {
        val session = verifierClient.createSession()
        val presentation = buildSDJWTPresentation(stored, session.nonce, session.verifierDid)
        return verifierClient.verifySDJWTPresentation(
            policyId = packId,
            sdJwtCredentials = listOf(presentation),
            sessionId = session.sessionId
        )
    }

    /**
     * Build an SD-JWT presentation with KB-JWT holder binding.
     * Shared between direct and relay flows.
     */
    private suspend fun buildSDJWTPresentation(
        stored: StoredCredential,
        nonce: String,
        verifierDid: String
    ): String {
        val km = keyManager ?: throw Exception("KeyManager required for SD-JWT presentation")
        val rawSdJwt = stored.rawSdJwt ?: throw Exception("No SD-JWT in credential")
        val keyAlias = stored.keyAlias ?: throw Exception("No key alias in credential")

        val parsed = SDJWTParser.parse(rawSdJwt)
        val presentation = SDJWTParser.selectivePresentation(parsed, parsed.claims.keys)

        val kbjwt = KBJWTBuilder.build(
            nonce = nonce,
            audience = verifierDid,
            sdJwtWithDisclosures = presentation,
            keyManager = km,
            keyAlias = keyAlias
        )

        return presentation + kbjwt
    }

    private suspend fun verifyWithLegacy(stored: StoredCredential, packId: String): VerifyResponseDTO {
        val credDTO = toCredentialDTO(stored.credential)
        return verifierClient.verifyPresentation(packId, listOf(credDTO))
    }

    private suspend fun pollUntilResponse(relayResponseUri: String): ByteArray {
        var elapsed = 0L
        while (elapsed < POLL_TIMEOUT_MS) {
            val response = relayClient.pollResponse(relayResponseUri)
            if (response != null) return response
            delay(POLL_INTERVAL_MS)
            elapsed += POLL_INTERVAL_MS
        }
        throw Exception("Verification timed out: holder did not respond within ${POLL_TIMEOUT_MS / 1000}s")
    }

    private suspend fun generateReceiptForVerification(
        credential: VerifiableCredential,
        packId: String,
        response: VerifyResponseDTO
    ): ConsentReceipt? {
        val predicates = response.predicates.orEmpty()
        if (predicates.isEmpty()) return null

        val outcome = if (response.summary?.cachetGranted == true) {
            ConsentReceipt.OUTCOME_PASSED
        } else {
            ConsentReceipt.OUTCOME_INCOMPLETE
        }

        // Only include predicates that were actually satisfied
        val satisfiedIds = response.predicateResults
            .filter { it.status == "satisfied" }
            .map { it.predicateId }
            .toSet()
        val provenPredicates = if (satisfiedIds.isNotEmpty()) {
            predicates.filter { it in satisfiedIds }
        } else if (outcome == ConsentReceipt.OUTCOME_PASSED) {
            predicates // backward compat: no predicate results means all passed
        } else {
            emptyList()
        }

        val request = PresentationRequest(
            rpIdentifier = "did:web:cachet.id:verifier",
            rpDisplayName = "Cachet Verifier",
            purpose = "Trust Pack verification: $packId",
            requestedPredicates = provenPredicates
        )
        val consent = ConsentDetails(
            explicitConsent = true,
            dataMinimizationAcknowledged = true,
            retentionPeriodUnderstood = true
        )

        return consentUseCase.generateConsentReceipt(
            credential, request, consent, outcome,
            totalPredicatesCount = predicates.size
        ).getOrNull()
    }

    private fun toCredentialDTO(vc: VerifiableCredential): VerifiableCredentialDTO {
        return VerifiableCredentialDTO(
            id = vc.id,
            type = vc.type,
            issuer = vc.issuer,
            issuanceDate = vc.issuanceDate,
            expirationDate = vc.expirationDate,
            credentialSubject = CredentialSubjectDTO(
                id = vc.credentialSubject.id,
                verified = vc.credentialSubject.verified,
                personalData = vc.credentialSubject.personalData?.let {
                    PersonalDataDTO(
                        age = it.age,
                        nationality = it.nationality,
                        documentType = it.documentType
                    )
                },
                verificationLevel = vc.credentialSubject.verificationLevel,
                verificationMethod = vc.credentialSubject.verificationMethod
            )
        )
    }
}

/** Payload stored in the relay — fetched by the holder after scanning the QR. */
@Serializable
data class VerificationRequestPayload(
    val nonce: String,
    val verifierDid: String,
    val packId: String,
    val question: String,
    val predicates: List<String>
)

/** Returned by [VerificationUseCase.startVerifierSession] — used by the verifier UI. */
data class VerifierSessionInfo(
    val qrPayload: String,
    val relayResponseUri: String,
    val verificationSessionId: String,
    val packId: String,
    val sessionNonce: String = "",
    val verifierDid: String = ""
)

/** Holder-side result of fetching and verifying the Request Object from the relay. */
data class VerifiedRequest(
    val payload: VerificationRequestPayload,
    val verifierName: String? = null,
    val isVerified: Boolean = false
)

/** Result of verifying a credential against a Trust Pack. */
data class VerificationResult(
    val packId: String,
    val badge: String,
    val freshness: String,
    val predicateResults: List<PredicateResultDTO>,
    val summary: VerificationSummaryDTO?,
    val consentReceiptId: String?,
    val holderBound: Boolean = false
)
