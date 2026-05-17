package id.cachet.wallet.android.trusttrail.auth

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages Google Sign-In for Gmail inbox access.
 * Requests only gmail.readonly scope — minimal access.
 */
object GmailSignIn {

    private const val WEB_CLIENT_ID =
        "144370899453-ftccujp0vki69duje10ug6h4fq2p1vur.apps.googleusercontent.com"

    private val GMAIL_READONLY_SCOPE = Scope("https://www.googleapis.com/auth/gmail.readonly")

    fun buildClient(context: Context): GoogleSignInClient {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(GMAIL_READONLY_SCOPE)
            .requestServerAuthCode(WEB_CLIENT_ID)
            .build()

        return GoogleSignIn.getClient(context, options)
    }

    fun getSignInIntent(client: GoogleSignInClient): Intent = client.signInIntent

    fun handleSignInResult(data: Intent?): GoogleSignInAccount? {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            task.getResult(ApiException::class.java)
        } catch (e: ApiException) {
            null
        }
    }

    fun getLastSignedInAccount(context: Context): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    /**
     * Get an OAuth2 access token for the Gmail API.
     * Must be called off the main thread.
     */
    suspend fun getAccessToken(context: Context, account: GoogleSignInAccount): String? {
        return withContext(Dispatchers.IO) {
            try {
                val scopes = "oauth2:https://www.googleapis.com/auth/gmail.readonly"
                com.google.android.gms.auth.GoogleAuthUtil.getToken(
                    context,
                    account.account!!,
                    scopes,
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun signOut(client: GoogleSignInClient) {
        withContext(Dispatchers.IO) {
            try {
                com.google.android.gms.tasks.Tasks.await(client.signOut())
            } catch (_: Exception) {
                // Best-effort sign out
            }
        }
    }
}
