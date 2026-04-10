package id.cachet.wallet.android.verification

/**
 * Placeholder VeriffService for production builds.
 * Returns Cancelled until the real Veriff SDK is integrated.
 */
class NoOpVeriffService : VeriffService {
    override suspend fun startVerification(): VeriffResult {
        return VeriffResult.Cancelled
    }
}
