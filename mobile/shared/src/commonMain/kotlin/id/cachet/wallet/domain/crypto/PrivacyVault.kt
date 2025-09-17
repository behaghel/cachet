package id.cachet.wallet.domain.crypto

import id.cachet.wallet.domain.model.EncryptedData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Phase A: Privacy Vault Cryptography
 *
 * Provides an abstraction for encrypting sensitive holder data. The current
 * implementation is a safe no-op that refuses to process data so we do not
 * accidentally ship with placebo cryptography.
 */
interface PrivacyVault {

    /** Encrypt sensitive data for storage in the vault. */
    suspend fun encryptData(
        plaintext: String,
        dataType: String,
        keyId: String = "default"
    ): Result<EncryptedData>

    /** Decrypt data from the vault. */
    suspend fun decryptData(
        encryptedData: EncryptedData,
        keyId: String = "default"
    ): Result<String>

    /** Generate a new encryption key for a user. */
    suspend fun generateUserKey(): Result<String>

    /** Derive a key from a passphrase/biometric factor. */
    suspend fun deriveKeyFromPassphrase(
        passphrase: String,
        salt: ByteArray
    ): Result<ByteArray>

    /** Validate data integrity using a stored hash/signature. */
    fun validateDataIntegrity(
        plaintext: String,
        storedHash: String
    ): Boolean
}

/**
 * Placeholder privacy vault that intentionally refuses to perform any
 * cryptographic operations until a reviewed implementation is available.
 */
class NoOpPrivacyVault : PrivacyVault {

    override suspend fun encryptData(
        plaintext: String,
        dataType: String,
        keyId: String
    ): Result<EncryptedData> =
        Result.failure(IllegalStateException("Privacy vault encryption not implemented"))

    override suspend fun decryptData(
        encryptedData: EncryptedData,
        keyId: String
    ): Result<String> =
        Result.failure(IllegalStateException("Privacy vault decryption not implemented"))

    override suspend fun generateUserKey(): Result<String> =
        Result.failure(IllegalStateException("Privacy vault key generation not implemented"))

    override suspend fun deriveKeyFromPassphrase(
        passphrase: String,
        salt: ByteArray
    ): Result<ByteArray> =
        Result.failure(IllegalStateException("Privacy vault key derivation not implemented"))

    override fun validateDataIntegrity(plaintext: String, storedHash: String): Boolean = false
}

/**
 * Privacy vault factory for dependency injection.
 */
object PrivacyVaultFactory {

    fun createPrivacyVault(type: VaultType = VaultType.NO_OP): PrivacyVault = when (type) {
        VaultType.NO_OP -> NoOpPrivacyVault()
    }
}

enum class VaultType {
    NO_OP
}

/**
 * Helper functions for sensitive data encryption/decryption.
 */
object VaultHelper {

    /** Encrypt a data object to `EncryptedData`. */
    suspend inline fun <reified T> encryptObject(
        vault: PrivacyVault,
        obj: T,
        dataType: String,
        keyId: String = "default"
    ): Result<EncryptedData> = try {
        val json = Json.encodeToString(obj)
        vault.encryptData(json, dataType, keyId)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Decrypt `EncryptedData` into a data object. */
    suspend inline fun <reified T> decryptObject(
        vault: PrivacyVault,
        encryptedData: EncryptedData,
        keyId: String = "default"
    ): Result<T> = try {
        vault.decryptData(encryptedData, keyId).fold(
            onSuccess = { json ->
                try {
                    Result.success(Json.decodeFromString<T>(json))
                } catch (decodeError: Exception) {
                    Result.failure(decodeError)
                }
            },
            onFailure = { error -> Result.failure(error) }
        )
    } catch (e: Exception) {
        Result.failure(e)
    }
}
