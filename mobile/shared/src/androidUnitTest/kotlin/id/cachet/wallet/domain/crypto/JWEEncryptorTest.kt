package id.cachet.wallet.domain.crypto

import com.nimbusds.jose.JWEObject
import com.nimbusds.jose.crypto.X25519Decrypter
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.OctetKeyPair
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator
import com.nimbusds.jose.util.Base64URL
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JWEEncryptorTest {

    @BeforeTest
    fun setup() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    private fun generateX25519KeyPair(): OctetKeyPair =
        OctetKeyPairGenerator(Curve.X25519).generate()

    // ── encrypt ──

    @Test
    fun `encrypt produces valid JWE compact serialization`() {
        val keyPair = generateX25519KeyPair()
        val pubKeyBase64URL = keyPair.x.toString()
        val plaintext = "Hello, SD-JWT presentation".encodeToByteArray()

        val encryptor = JWEEncryptor()
        val jwe = encryptor.encrypt(plaintext, pubKeyBase64URL)

        // JWE compact format: 5 base64url parts separated by dots
        val parts = jwe.split(".")
        assertEquals(5, parts.size, "JWE must have 5 parts")
        assertTrue(parts.all { it.isNotEmpty() })
    }

    @Test
    fun `encrypt output can be decrypted with private key`() {
        val keyPair = generateX25519KeyPair()
        val pubKeyBase64URL = keyPair.x.toString()
        val plaintext = "SD-JWT~disc1~disc2~kb-jwt".encodeToByteArray()

        val encryptor = JWEEncryptor()
        val jweCompact = encryptor.encrypt(plaintext, pubKeyBase64URL)

        // Decrypt with the private key
        val jweObject = JWEObject.parse(jweCompact)
        jweObject.decrypt(X25519Decrypter(keyPair))
        val decrypted = jweObject.payload.toBytes()

        assertEquals(String(plaintext), String(decrypted))
    }

    @Test
    fun `encrypt uses ECDH-ES+A256KW algorithm`() {
        val keyPair = generateX25519KeyPair()
        val plaintext = "test".encodeToByteArray()

        val encryptor = JWEEncryptor()
        val jweCompact = encryptor.encrypt(plaintext, keyPair.x.toString())

        val jweObject = JWEObject.parse(jweCompact)
        assertEquals("ECDH-ES+A256KW", jweObject.header.algorithm.name)
        assertEquals("A256GCM", jweObject.header.encryptionMethod.name)
    }

    @Test
    fun `encrypt with different keys produces different ciphertext`() {
        val key1 = generateX25519KeyPair()
        val key2 = generateX25519KeyPair()
        val plaintext = "same plaintext".encodeToByteArray()

        val encryptor = JWEEncryptor()
        val jwe1 = encryptor.encrypt(plaintext, key1.x.toString())
        val jwe2 = encryptor.encrypt(plaintext, key2.x.toString())

        // Different recipient keys → different ciphertext (ECDH ephemeral key differs)
        assertTrue(jwe1 != jwe2)
    }

    @Test
    fun `decrypt fails with wrong private key`() {
        val correctKey = generateX25519KeyPair()
        val wrongKey = generateX25519KeyPair()
        val plaintext = "secret data".encodeToByteArray()

        val encryptor = JWEEncryptor()
        val jweCompact = encryptor.encrypt(plaintext, correctKey.x.toString())

        val jweObject = JWEObject.parse(jweCompact)
        try {
            jweObject.decrypt(X25519Decrypter(wrongKey))
            assertTrue(false, "Should have thrown")
        } catch (e: Exception) {
            // Expected: decryption fails with wrong key
        }
    }
}
