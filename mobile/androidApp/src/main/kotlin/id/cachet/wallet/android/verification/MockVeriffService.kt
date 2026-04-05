package id.cachet.wallet.android.verification

import android.util.Log
import id.cachet.wallet.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * Mock Veriff service for development.
 * Simulates a 2-second identity check, then POSTs a fake approved webhook
 * to the issuance gateway so a real session is stored for credential issuance.
 */
class MockVeriffService : VeriffService {

    companion object {
        private const val TAG = "MockVeriffService"
    }

    override suspend fun startVerification(): VeriffResult {
        return try {
            val sessionId = generateSessionId()
            Log.d(TAG, "Starting mock verification, sessionId=$sessionId")

            // Simulate Veriff UI delay
            delay(2000)

            // POST a fake approved webhook to the issuance gateway
            postMockWebhook(sessionId)

            Log.d(TAG, "Mock verification complete, sessionId=$sessionId")
            VeriffResult.Success(sessionId)
        } catch (e: Exception) {
            Log.e(TAG, "Mock verification failed", e)
            VeriffResult.Failure(e.message ?: "Unknown error")
        }
    }

    private suspend fun postMockWebhook(sessionId: String) = withContext(Dispatchers.IO) {
        val url = URL("${AppConfig.baseUrl}/webhooks/veriff")
        val body = buildWebhookPayload(sessionId)
        val bodyBytes = body.toByteArray(Charsets.UTF_8)

        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-HMAC-Signature", hmacSha256(bodyBytes, AppConfig.DEV_WEBHOOK_SECRET))
            conn.doOutput = true
            conn.outputStream.bufferedWriter().use { it.write(body) }
            val code = conn.responseCode
            Log.d(TAG, "Mock webhook response: $code")
            if (code != 200) {
                val error = conn.errorStream?.bufferedReader()?.readText() ?: "unknown"
                Log.w(TAG, "Webhook rejected: $error")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun hmacSha256(data: ByteArray, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(data).joinToString("") { "%02x".format(it) }
    }

    private fun buildWebhookPayload(sessionId: String): String {
        val now = Clock.System.now()
        return """
        {
            "session_id": "$sessionId",
            "status": "approved",
            "person": {
                "first_name": "Jane",
                "last_name": "Doe",
                "date_of_birth": "1995-06-15",
                "confidence": 0.96
            },
            "document": {
                "number": "AB1234567",
                "type": "PASSPORT",
                "country": "EE",
                "authenticity": 0.97
            },
            "verification": {
                "liveness_score": 0.93,
                "overall_confidence": 0.96,
                "risk_score": 0.03,
                "timestamp": "$now"
            }
        }
        """.trimIndent()
    }

    private fun generateSessionId(): String {
        val hex = "0123456789abcdef"
        fun segment(len: Int) = (1..len).map { hex[Random.nextInt(hex.length)] }.joinToString("")
        return "mock-${segment(8)}-${segment(4)}-${segment(4)}"
    }
}
