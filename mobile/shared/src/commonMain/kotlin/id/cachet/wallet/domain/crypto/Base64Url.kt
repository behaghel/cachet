package id.cachet.wallet.domain.crypto

/**
 * Multiplatform base64url encode/decode utility.
 * Consolidates the four separate implementations previously scattered across
 * KBJWTBuilder, SDJWTParser, VerificationUseCase, and StatusListCache.
 */
object Base64Url {

    private const val TABLE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    fun encode(bytes: ByteArray): String {
        val sb = StringBuilder()
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else 0
            val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else 0
            val remaining = bytes.size - i

            sb.append(TABLE[b0 shr 2])
            sb.append(TABLE[((b0 and 0x03) shl 4) or (b1 shr 4)])
            if (remaining > 1) sb.append(TABLE[((b1 and 0x0F) shl 2) or (b2 shr 6)]) else sb.append('=')
            if (remaining > 2) sb.append(TABLE[b2 and 0x3F]) else sb.append('=')
            i += 3
        }
        return sb.toString()
            .replace('+', '-')
            .replace('/', '_')
            .trimEnd('=')
    }

    fun decode(input: String): ByteArray {
        val padded = input
            .replace('-', '+')
            .replace('_', '/')
            .let { s ->
                when (s.length % 4) {
                    2 -> "$s=="
                    3 -> "$s="
                    else -> s
                }
            }
        return decodeStandard(padded)
    }

    private fun decodeStandard(input: String): ByteArray {
        val result = mutableListOf<Byte>()
        var i = 0
        while (i < input.length) {
            val c0 = TABLE.indexOf(input[i])
            val c1 = if (i + 1 < input.length) TABLE.indexOf(input[i + 1]) else 0
            val c2 = if (i + 2 < input.length && input[i + 2] != '=') TABLE.indexOf(input[i + 2]) else -1
            val c3 = if (i + 3 < input.length && input[i + 3] != '=') TABLE.indexOf(input[i + 3]) else -1

            result.add(((c0 shl 2) or (c1 shr 4)).toByte())
            if (c2 >= 0) result.add((((c1 and 0x0F) shl 4) or (c2 shr 2)).toByte())
            if (c3 >= 0) result.add((((c2 and 0x03) shl 6) or c3).toByte())
            i += 4
        }
        return result.toByteArray()
    }
}
