package id.cachet.wallet.domain.crypto

import id.cachet.wallet.testfixtures.FakeKeyManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class KBJWTBuilderTest {

    // ── build ──

    @Test
    fun `build produces three-part JWT`() {
        val km = FakeKeyManager()
        km.generateKeyPair("holder-key")

        val jwt = KBJWTBuilder.build(
            nonce = "test-nonce",
            audience = "did:web:verifier.example.com",
            sdJwtWithDisclosures = "issuerJwt~disc1~disc2~",
            keyManager = km,
            keyAlias = "holder-key"
        )

        val parts = jwt.split(".")
        assertEquals(3, parts.size, "KB-JWT must be header.payload.signature")
        assertTrue(parts.all { it.isNotEmpty() })
    }

    @Test
    fun `build header contains ES256 alg and kb+jwt typ`() {
        val km = FakeKeyManager()
        km.generateKeyPair("k1")

        val jwt = KBJWTBuilder.build("n", "aud", "jwt~d~", km, "k1")
        val headerJson = decodeJwtPart(jwt.split(".")[0])

        assertTrue(headerJson.contains("\"alg\":\"ES256\""))
        assertTrue(headerJson.contains("\"typ\":\"kb+jwt\""))
    }

    @Test
    fun `build payload contains nonce, aud, iat, sd_hash`() {
        val km = FakeKeyManager()
        km.generateKeyPair("k1")

        val jwt = KBJWTBuilder.build("my-nonce", "did:web:v", "jwt~d~", km, "k1")
        val payloadJson = decodeJwtPart(jwt.split(".")[1])

        assertTrue(payloadJson.contains("\"nonce\":\"my-nonce\""))
        assertTrue(payloadJson.contains("\"aud\":\"did:web:v\""))
        assertTrue(payloadJson.contains("\"iat\":"))
        assertTrue(payloadJson.contains("\"sd_hash\":"))
    }

    @Test
    fun `build throws when key alias does not exist`() {
        val km = FakeKeyManager()
        try {
            KBJWTBuilder.build("n", "aud", "jwt~d~", km, "nonexistent")
            assertTrue(false, "Should have thrown")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("No key"))
        }
    }

    private fun decodeJwtPart(base64url: String): String {
        return Base64Url.decode(base64url).decodeToString()
    }

    // ── computeSDHash ──

    @Test
    fun `computeSDHash returns base64url encoded value`() {
        val hash = KBJWTBuilder.computeSDHash("issuerJwt~disc1~disc2~")
        assertTrue(hash.isNotEmpty())
        // base64url must not contain +, /, or =
        assertFalse(hash.contains('+'))
        assertFalse(hash.contains('/'))
        assertFalse(hash.contains('='))
    }

    @Test
    fun `computeSDHash is deterministic`() {
        val input = "eyJhbGciOiJFUzI1NiJ9.payload.sig~disc1~disc2~"
        val hash1 = KBJWTBuilder.computeSDHash(input)
        val hash2 = KBJWTBuilder.computeSDHash(input)
        assertEquals(hash1, hash2)
    }

    @Test
    fun `computeSDHash differs for different inputs`() {
        val hash1 = KBJWTBuilder.computeSDHash("content-a~disc~")
        val hash2 = KBJWTBuilder.computeSDHash("content-b~disc~")
        assertNotEquals(hash1, hash2)
    }
}
