package id.cachet.wallet.android.verification

/**
 * Abstraction over identity verification (Veriff SDK in production, mock for dev).
 * Returns a session ID that can be bound to the OAuth token request.
 */
interface VeriffService {
    suspend fun startVerification(): VeriffResult
}

sealed class VeriffResult {
    data class Success(val sessionId: String) : VeriffResult()
    data class Failure(val reason: String) : VeriffResult()
    object Cancelled : VeriffResult()
}
