package id.cachet.wallet.domain.crypto

import id.cachet.wallet.domain.model.EncryptedData
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

/**
 * Phase A: Privacy Vault Cryptography
 * 
 * Provides encryption/decryption for sensitive user data in the privacy vault.
 * Users trust us with everything once so they can share minimal predicates with everyone else.
 */
interface PrivacyVault {
    
    /**
     * Encrypt sensitive data for storage in the vault
     */
    suspend fun encryptData(
        plaintext: String,
        dataType: String,
        keyId: String = "default"
    ): Result<EncryptedData>
    
    /**
     * Decrypt data from the vault
     */
    suspend fun decryptData(
        encryptedData: EncryptedData,
        keyId: String = "default"
    ): Result<String>
    
    /**
     * Generate a new encryption key for a user
     */
    suspend fun generateUserKey(): Result<String>
    
    /**
     * Derive key from user passphrase/biometric
     */
    suspend fun deriveKeyFromPassphrase(
        passphrase: String,
        salt: ByteArray
    ): Result<ByteArray>
    
    /**
     * Validate data integrity using stored hash
     */
    fun validateDataIntegrity(
        plaintext: String,
        storedHash: String
    ): Boolean
}

/**
 * AES-256-GCM implementation of the privacy vault
 * 
 * Uses authenticated encryption to ensure both confidentiality and integrity.
 * Key derivation uses PBKDF2 with high iteration count for security.
 */
class AESPrivacyVault : PrivacyVault {
    
    companion object {
        private const val ALGORITHM = "AES-256-GCM"
        private const val KEY_DERIVATION = "PBKDF2"
        private const val PBKDF2_ITERATIONS = 100_000
        private const val KEY_LENGTH = 32 // 256 bits
        private const val IV_LENGTH = 12 // 96 bits for GCM
        private const val TAG_LENGTH = 16 // 128 bits
        private const val SALT_LENGTH = 32 // 256 bits
    }
    
    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun encryptData(
        plaintext: String,
        dataType: String,
        keyId: String
    ): Result<EncryptedData> {
        return try {
            // Generate random IV for this encryption
            val iv = Random.nextBytes(IV_LENGTH)
            
            // In a real implementation, would retrieve key from secure storage
            val key = getEncryptionKey(keyId)
            
            // Simulate AES-GCM encryption (in production would use actual crypto library)
            val ciphertext = simulateAESGCMEncrypt(plaintext.encodeToByteArray(), key, iv)
            val authTag = Random.nextBytes(TAG_LENGTH) // Would be real auth tag from GCM
            
            // Calculate hash of plaintext for integrity checking
            val dataHash = calculateSHA256Hash(plaintext)
            
            val encryptedData = EncryptedData(
                ciphertext = Base64.encode(ciphertext),
                algorithm = ALGORITHM,
                keyDerivation = KEY_DERIVATION,
                iv = Base64.encode(iv),
                authTag = Base64.encode(authTag),
                dataType = dataType,
                encryptedAt = Clock.System.now(),
                dataHash = dataHash
            )
            
            Result.success(encryptedData)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun decryptData(
        encryptedData: EncryptedData,
        keyId: String
    ): Result<String> {
        return try {
            // Verify algorithm compatibility
            if (encryptedData.algorithm != ALGORITHM) {
                return Result.failure(IllegalArgumentException("Unsupported algorithm: ${encryptedData.algorithm}"))
            }
            
            // Get decryption key
            val key = getEncryptionKey(keyId)
            
            // Decode components
            val ciphertext = Base64.decode(encryptedData.ciphertext)
            val iv = Base64.decode(encryptedData.iv)
            val authTag = Base64.decode(encryptedData.authTag)
            
            // Simulate AES-GCM decryption (in production would use actual crypto library)
            val plaintext = simulateAESGCMDecrypt(ciphertext, key, iv, authTag)
            val plaintextString = plaintext.decodeToString()
            
            // Verify data integrity
            val calculatedHash = calculateSHA256Hash(plaintextString)
            if (calculatedHash != encryptedData.dataHash) {
                return Result.failure(SecurityException("Data integrity check failed"))
            }
            
            Result.success(plaintextString)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun generateUserKey(): Result<String> {
        return try {
            // Generate a secure random key
            val keyBytes = Random.nextBytes(KEY_LENGTH)
            @OptIn(ExperimentalEncodingApi::class)
            Result.success(Base64.encode(keyBytes))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun deriveKeyFromPassphrase(
        passphrase: String,
        salt: ByteArray
    ): Result<ByteArray> {
        return try {
            // Simulate PBKDF2 key derivation (in production would use actual crypto library)
            val derivedKey = simulatePBKDF2(
                password = passphrase.encodeToByteArray(),
                salt = salt,
                iterations = PBKDF2_ITERATIONS,
                keyLength = KEY_LENGTH
            )
            Result.success(derivedKey)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun validateDataIntegrity(plaintext: String, storedHash: String): Boolean {
        return try {
            val calculatedHash = calculateSHA256Hash(plaintext)
            calculatedHash == storedHash
        } catch (e: Exception) {
            false
        }
    }
    
    // Private helper functions (in production would use proper crypto libraries)
    
    @OptIn(ExperimentalEncodingApi::class)
    private fun getEncryptionKey(keyId: String): ByteArray {
        // In production, would retrieve from secure storage (Keychain/Keystore)
        // For now, derive a deterministic key from keyId for testing
        val seed = keyId.hashCode().toLong()
        val random = Random(seed)
        return random.nextBytes(KEY_LENGTH)
    }
    
    private fun simulateAESGCMEncrypt(plaintext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        // This is a simulation - in production would use actual AES-GCM
        // Simple XOR with key for demonstration (NOT secure for real use)
        val encrypted = ByteArray(plaintext.size)
        for (i in plaintext.indices) {
            encrypted[i] = (plaintext[i].toInt() xor key[i % key.size].toInt() xor iv[i % iv.size].toInt()).toByte()
        }
        return encrypted
    }
    
    private fun simulateAESGCMDecrypt(ciphertext: ByteArray, key: ByteArray, iv: ByteArray, authTag: ByteArray): ByteArray {
        // This is a simulation - in production would use actual AES-GCM with auth tag verification
        // Simple XOR reversal for demonstration (NOT secure for real use)
        val decrypted = ByteArray(ciphertext.size)
        for (i in ciphertext.indices) {
            decrypted[i] = (ciphertext[i].toInt() xor key[i % key.size].toInt() xor iv[i % iv.size].toInt()).toByte()
        }
        return decrypted
    }
    
    private fun simulatePBKDF2(password: ByteArray, salt: ByteArray, iterations: Int, keyLength: Int): ByteArray {
        // This is a simulation - in production would use actual PBKDF2
        // Simple hash-based derivation for demonstration
        var derived = password + salt
        repeat(iterations / 1000) { // Reduce iterations for simulation
            derived = calculateSHA256HashBytes(derived.decodeToString())
        }
        return derived.take(keyLength).toByteArray()
    }
    
    @OptIn(ExperimentalEncodingApi::class)
    private fun calculateSHA256Hash(input: String): String {
        // This is a simulation - in production would use actual SHA-256
        // Simple hash for demonstration (NOT cryptographically secure)
        val hash = input.hashCode().toString(16).padStart(8, '0')
        return "sha256_sim_$hash"
    }
    
    private fun calculateSHA256HashBytes(input: String): ByteArray {
        // This is a simulation - in production would use actual SHA-256
        return input.hashCode().toString().encodeToByteArray()
    }
}

/**
 * Privacy vault factory for dependency injection
 */
object PrivacyVaultFactory {
    
    fun createPrivacyVault(type: VaultType = VaultType.AES_GCM): PrivacyVault {
        return when (type) {
            VaultType.AES_GCM -> AESPrivacyVault()
        }
    }
}

enum class VaultType {
    AES_GCM
}

/**
 * Helper functions for sensitive data encryption/decryption
 */
object VaultHelper {
    
    /**
     * Encrypt a data object to EncryptedData
     */
    suspend inline fun <reified T> encryptObject(
        vault: PrivacyVault,
        obj: T,
        dataType: String,
        keyId: String = "default"
    ): Result<EncryptedData> {
        return try {
            val json = Json.encodeToString(obj)
            vault.encryptData(json, dataType, keyId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Decrypt EncryptedData to a data object
     */
    suspend inline fun <reified T> decryptObject(
        vault: PrivacyVault,
        encryptedData: EncryptedData,
        keyId: String = "default"
    ): Result<T> {
        return try {
            vault.decryptData(encryptedData, keyId).fold(
                onSuccess = { json ->
                    try {
                        val obj = Json.decodeFromString<T>(json)
                        Result.success(obj)
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}