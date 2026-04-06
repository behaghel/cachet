package id.cachet.wallet.domain.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class KBJWTBuilderTest {

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
