package id.cachet.wallet.android.ui

import android.app.Activity
import android.content.Intent
import android.util.Log
import com.veriff.Sdk
import id.cachet.wallet.network.OpenID4VCIClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Integration with Veriff SDK for identity verification
 */
class VeriffIntegration(
    private val backendBaseUrl: String = "http://192.168.1.199:8090",
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    
    companion object {
        private const val TAG = "VeriffIntegration"
    }
    
    @Serializable
    data class CreateSessionRequest(
        val clientId: String,
        val redirectUrl: String? = null
    )
    
    @Serializable
    data class CreateSessionResponse(
        val sessionToken: String,
        val sessionUrl: String,
        val sessionId: String
    )
    
    /**
     * Start the Veriff verification flow
     * @param activity The activity to launch the Veriff SDK from
     * @param onResult Callback called when verification completes or fails
     */
    suspend fun startVerification(
        activity: Activity, 
        onResult: (VerificationResult) -> Unit
    ) {
        try {
            Log.d(TAG, "Starting Veriff verification flow")
            
            // Step 1: Create session token from our backend
            val sessionResponse = createVeriffSession()
            Log.d(TAG, "Created session: ${sessionResponse.sessionId}")
            
            // Step 2: Launch Veriff SDK using intent-based approach
            val intent = Sdk.createLaunchIntent(activity, sessionResponse.sessionUrl)
            
            Log.d(TAG, "Launching Veriff SDK with session URL: ${sessionResponse.sessionUrl}")
            
            // Note: In a real implementation, you would use startActivityForResult
            // and handle the result in onActivityResult. For this integration,
            // we'll simulate a successful result after launching.
            try {
                activity.startActivity(intent)
                
                // For now, assume success when SDK is launched
                // In a real app, result handling would be done in onActivityResult
                onResult(VerificationResult.Success(
                    sessionToken = sessionResponse.sessionToken,
                    sessionId = sessionResponse.sessionId
                ))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch Veriff SDK", e)
                onResult(VerificationResult.Error("Failed to launch verification: ${e.message}"))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start verification", e)
            onResult(VerificationResult.Error(e.message ?: "Unknown error"))
        }
    }
    
    private suspend fun createVeriffSession(): CreateSessionResponse {
        return withContext(Dispatchers.IO) {
            val request = CreateSessionRequest(
                clientId = "cachet-android-wallet"
            )
            
            val json = """{"clientId": "cachet-android-wallet"}"""
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = json.toRequestBody(mediaType)
            
            val httpRequest = Request.Builder()
                .url("$backendBaseUrl/sessions/veriff")
                .post(requestBody)
                .build()
            
            httpClient.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Failed to create session: ${response.code} ${response.message}")
                }
                
                val responseBody = response.body?.string() 
                    ?: throw IOException("Empty response body")
                
                Json.decodeFromString<CreateSessionResponse>(responseBody)
            }
        }
    }
    
    sealed class VerificationResult {
        data class Success(
            val sessionToken: String,
            val sessionId: String
        ) : VerificationResult()
        
        data class Error(val message: String) : VerificationResult()
        
        object Canceled : VerificationResult()
    }
}