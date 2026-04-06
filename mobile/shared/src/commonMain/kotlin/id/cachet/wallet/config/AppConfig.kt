package id.cachet.wallet.config

/**
 * Central configuration for the Cachet wallet.
 *
 * Values are resolved in order: build config override → defaults.
 * For Android emulator use the default (10.0.2.2 maps to host localhost).
 * For physical devices, override via BuildConfig or DI.
 */
object AppConfig {

    private const val DEFAULT_BASE_URL = "http://10.0.2.2:8090"
    private const val DEFAULT_VERIFIER_URL = "http://10.0.2.2:8081"
    private const val DEFAULT_RELAY_URL = "http://10.0.2.2:8084"

    /** Base URL for the issuance gateway. */
    var baseUrl: String = DEFAULT_BASE_URL
        private set

    /** Base URL for the verifier service. */
    var verifierUrl: String = DEFAULT_VERIFIER_URL
        private set

    /** Base URL for the relay service. */
    var relayUrl: String = DEFAULT_RELAY_URL
        private set

    /** HTTP request timeout in milliseconds. */
    var requestTimeoutMs: Long = 30_000L
        private set

    /** OAuth client ID for the wallet. */
    var oauthClientId: String = "cachet-android-wallet"
        private set

    /** OAuth scope for credential issuance. */
    var oauthScope: String = "credential_issuance"
        private set

    /** Webhook HMAC secret for mock Veriff service (dev only — matches devenv.nix). */
    const val DEV_WEBHOOK_SECRET = "dev-secret-do-not-use-in-production"

    /**
     * Override configuration values. Call from Application.onCreate()
     * or test setup to inject environment-specific values.
     */
    fun configure(
        baseUrl: String? = null,
        verifierUrl: String? = null,
        relayUrl: String? = null,
        requestTimeoutMs: Long? = null,
        oauthClientId: String? = null,
        oauthScope: String? = null
    ) {
        baseUrl?.let { this.baseUrl = it }
        verifierUrl?.let { this.verifierUrl = it }
        relayUrl?.let { this.relayUrl = it }
        requestTimeoutMs?.let { this.requestTimeoutMs = it }
        oauthClientId?.let { this.oauthClientId = it }
        oauthScope?.let { this.oauthScope = it }
    }

    /** Reset to defaults (for testing). */
    fun reset() {
        baseUrl = DEFAULT_BASE_URL
        verifierUrl = DEFAULT_VERIFIER_URL
        relayUrl = DEFAULT_RELAY_URL
        requestTimeoutMs = 30_000L
        oauthClientId = "cachet-android-wallet"
        oauthScope = "credential_issuance"
    }

}
