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

    /** Base URL for the issuance gateway. */
    var baseUrl: String = DEFAULT_BASE_URL
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

    /**
     * Override configuration values. Call from Application.onCreate()
     * or test setup to inject environment-specific values.
     */
    fun configure(
        baseUrl: String? = null,
        requestTimeoutMs: Long? = null,
        oauthClientId: String? = null,
        oauthScope: String? = null
    ) {
        baseUrl?.let { this.baseUrl = it }
        requestTimeoutMs?.let { this.requestTimeoutMs = it }
        oauthClientId?.let { this.oauthClientId = it }
        oauthScope?.let { this.oauthScope = it }
    }

    /** Reset to defaults (for testing). */
    fun reset() {
        baseUrl = DEFAULT_BASE_URL
        requestTimeoutMs = 30_000L
        oauthClientId = "cachet-android-wallet"
        oauthScope = "credential_issuance"
    }

}
