package id.cachet.wallet.domain.transport

import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped

actual class SecureNonceGenerator actual constructor() : NonceGenerator {
    @OptIn(ExperimentalForeignApi::class)
    actual override fun generate(): String {
        val bytes = ByteArray(16) // 128 bits
        memScoped {
            val buffer = allocArray<kotlinx.cinterop.ByteVar>(16)
            SecRandomCopyBytes(kSecRandomDefault, 16u, buffer)
            for (i in 0 until 16) {
                bytes[i] = buffer[i]
            }
        }
        return bytes.encodeToBase64UrlNoPadding()
    }
}

private val BASE64URL_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

private fun ByteArray.encodeToBase64UrlNoPadding(): String {
    val sb = StringBuilder()
    var i = 0
    while (i < size) {
        val b0 = this[i].toInt() and 0xFF
        sb.append(BASE64URL_CHARS[(b0 shr 2) and 0x3F])
        if (i + 1 < size) {
            val b1 = this[i + 1].toInt() and 0xFF
            sb.append(BASE64URL_CHARS[((b0 shl 4) or (b1 shr 4)) and 0x3F])
            if (i + 2 < size) {
                val b2 = this[i + 2].toInt() and 0xFF
                sb.append(BASE64URL_CHARS[((b1 shl 2) or (b2 shr 6)) and 0x3F])
                sb.append(BASE64URL_CHARS[b2 and 0x3F])
            } else {
                sb.append(BASE64URL_CHARS[(b1 shl 2) and 0x3F])
            }
        } else {
            sb.append(BASE64URL_CHARS[(b0 shl 4) and 0x3F])
        }
        i += 3
    }
    return sb.toString()
}
