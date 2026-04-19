package id.cachet.wallet.domain.transport

/**
 * Abstraction over the transport layer for cross-device verification.
 *
 * Two implementations:
 * - [RelayTransport]: online, relay-mediated (existing flow)
 * - [QrDirectTransport]: offline, proximity-based (two-QR dance)
 *
 * The verification logic ([LocalVerifier], KB-JWT, SD-JWT] is transport-agnostic.
 * Only the session creation and VP exchange differ between implementations.
 */
interface VerificationTransport {
    /**
     * Verifier side: create a new verification session.
     * - Relay: calls verifier + relay backends, returns relay URIs
     * - Proximity: generates nonce + ephemeral key locally, returns QR payload
     */
    suspend fun createSession(params: SessionParams): TransportSession

    /**
     * Holder side: receive and parse the verification request.
     * - Relay: fetches signed Request Object from relay URL
     * - Proximity: parses session params from QR URI
     *
     * @param sessionData Opaque data from QR scan or deep link (relay URL or proximity URI)
     */
    suspend fun receiveRequest(sessionData: String): TransportRequest

    /**
     * Holder side: send the VP response back to the verifier.
     * - Relay: POSTs encrypted VP to relay response URL
     * - Proximity: returns the payload for the holder to display as QR
     *
     * @param sessionData Opaque session handle (relay response URL or proximity URI)
     * @param payload Encrypted VP bytes (JWE)
     * @return For proximity: the QR-encodable string. For relay: null (response was posted).
     */
    suspend fun sendResponse(sessionData: String, payload: ByteArray): String?

    /**
     * Verifier side: await the holder's response.
     * - Relay: polls relay GET until response arrives
     * - Proximity: not used (verifier scans QR directly)
     *
     * @return Encrypted VP bytes
     */
    suspend fun awaitResponse(session: TransportSession): ByteArray
}

/** Parameters for creating a new verification session. */
data class SessionParams(
    val packId: String,
    val question: String,
    val predicates: List<String>
)

/**
 * Result of creating a session — verifier side.
 * Contains everything needed to display the QR and await the response.
 */
data class TransportSession(
    /** QR payload for the holder to scan */
    val qrPayload: String,
    /** Opaque handle for awaiting the response (relay URI or proximity session ID) */
    val responseHandle: String,
    /** Pack ID for this session */
    val packId: String,
    /** Session nonce (for local verification after response) */
    val sessionNonce: String,
    /** Verifier DID (for local verification after response) */
    val verifierDid: String,
    /** Verifier's ephemeral X25519 private key base64url (proximity only, for decryption) */
    val ephemeralPrivKey: String? = null,
    /** Verifier's ephemeral X25519 public key base64url (for decryption) */
    val ephemeralPubKey: String? = null,
    /** Backend session ID (relay only, for fallback to backend verification) */
    val backendSessionId: String? = null
)

/**
 * Parsed verification request — holder side.
 * Same shape as [VerifiedRequest] but transport-agnostic.
 */
data class TransportRequest(
    val nonce: String,
    val verifierDid: String,
    val packId: String,
    val question: String,
    val predicates: List<String>,
    /** Verifier's ephemeral public key for E2E encryption (base64url) */
    val verifierPubKey: String?,
    /** Verifier display name (from signed Request Object, if available) */
    val verifierName: String? = null,
    /** Whether the request was cryptographically verified (signed Request Object) */
    val isVerified: Boolean = false
)
