package id.cachet.wallet.domain.transport

import com.nimbusds.jose.util.Base64URL
import java.security.SecureRandom

actual class SecureNonceGenerator actual constructor() : NonceGenerator {
    private val random = SecureRandom()

    actual override fun generate(): String {
        val bytes = ByteArray(16) // 128 bits
        random.nextBytes(bytes)
        return Base64URL.encode(bytes).toString()
    }
}
