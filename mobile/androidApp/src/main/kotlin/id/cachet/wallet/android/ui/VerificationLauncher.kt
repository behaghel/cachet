package id.cachet.wallet.android.ui

import android.app.Activity

sealed class VerificationResult {
    data class Success(
        val sessionToken: String,
        val sessionId: String
    ) : VerificationResult()

    data class Error(val message: String) : VerificationResult()

    object Canceled : VerificationResult()
}

interface VerificationLauncher {
    suspend fun startVerification(
        activity: Activity,
        onResult: (VerificationResult) -> Unit
    )
}
