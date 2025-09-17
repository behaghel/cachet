package id.cachet.wallet.domain.model

import kotlinx.cinterop.*
import platform.Foundation.*
import platform.CommonCrypto.*
import platform.Security.*

/**
 * iOS-specific implementations of cryptographic functions for consent receipts
 */

/**
 * Compute SHA-256 hash using iOS CommonCrypto
 */
@OptIn(ExperimentalForeignApi::class)
actual fun sha256Hash(input: String): String {
    val data = input.encodeToByteArray()
    val digest = ByteArray(Int(CC_SHA256_DIGEST_LENGTH))
    
    data.usePinned { pinned ->
        digest.usePinned { digestPinned ->
            CC_SHA256(pinned.addressOf(0), data.size.toUInt(), digestPinned.addressOf(0))
        }
    }
    
    return digest.joinToString("") { "%02x".format(it.toUByte()) }
}

@OptIn(ExperimentalForeignApi::class)
actual fun signConsentReceipt(canonicalContent: String, privateKey: String): String {
    val data = canonicalContent.encodeToByteArray()
    val keyData = privateKey.encodeToByteArray()
    val mac = ByteArray(Int(CC_SHA256_DIGEST_LENGTH))

    keyData.usePinned { keyPinned ->
        data.usePinned { dataPinned ->
            mac.usePinned { macPinned ->
                CCHmac(
                    kCCHmacAlgSHA256,
                    keyPinned.addressOf(0),
                    keyData.size.toUInt(),
                    dataPinned.addressOf(0),
                    data.size.toUInt(),
                    macPinned.addressOf(0)
                )
            }
        }
    }

    return mac.joinToString("") { "%02x".format(it.toUByte()) }
}

@OptIn(ExperimentalForeignApi::class)
actual fun verifyConsentReceiptSignature(
    canonicalContent: String,
    signature: String,
    publicKey: String
): Boolean {
    val expected = signConsentReceipt(canonicalContent, publicKey)
    return expected.equals(signature, ignoreCase = true)
}

@OptIn(ExperimentalForeignApi::class)
actual fun secureRandomBytes(length: Int): ByteArray {
    require(length > 0) { "Byte length must be positive" }
    val bytes = ByteArray(length)
    bytes.usePinned { pinned ->
        val status = SecRandomCopyBytes(kSecRandomDefault, length.toULong(), pinned.addressOf(0))
        if (status != errSecSuccess) {
            throw IllegalStateException("Failed to generate secure random bytes: $status")
        }
    }
    return bytes
}
