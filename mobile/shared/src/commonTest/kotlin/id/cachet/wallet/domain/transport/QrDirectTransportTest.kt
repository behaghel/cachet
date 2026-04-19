package id.cachet.wallet.domain.transport

import id.cachet.wallet.domain.crypto.EphemeralKeyGenerator
import id.cachet.wallet.domain.crypto.EphemeralKeyPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QrDirectTransportTest {

    // ── URI building / parsing ──

    @Test
    fun `buildProximityUri produces valid URI with all params`() {
        val session = ProximitySession(
            nonce = "abc123",
            keyPair = EphemeralKeyPair("pubkey_b64", "privkey_b64"),
            packId = "childcare-readiness-v1",
            question = "Are you safe?",
            predicates = listOf("age_gte_18", "dbs_check")
        )

        val uri = buildProximityUri(session)

        assertTrue(uri.startsWith("cachet://proximity?"))
        assertTrue(uri.contains("n=abc123"))
        assertTrue(uri.contains("vk=pubkey_b64"))
        assertTrue(uri.contains("pack=childcare-readiness-v1"))
        assertTrue(uri.contains("q=Are%20you%20safe%3F"))
        assertTrue(uri.contains("p=age_gte_18,dbs_check"))
    }

    @Test
    fun `parseProximityUri round-trips correctly`() {
        val session = ProximitySession(
            nonce = "nonce_xyz",
            keyPair = EphemeralKeyPair("vk_pub", "vk_priv"),
            packId = "age-check-v1",
            question = "How old are you?",
            predicates = listOf("age_gte_18")
        )

        val uri = buildProximityUri(session)
        val parsed = parseProximityUri(uri)

        assertEquals("nonce_xyz", parsed.nonce)
        assertEquals("vk_pub", parsed.verifierPubKey)
        assertEquals("age-check-v1", parsed.packId)
        assertEquals("How old are you?", parsed.question)
        assertEquals(listOf("age_gte_18"), parsed.predicates)
        assertFalse(parsed.isVerified)
    }

    @Test
    fun `parseProximityUri rejects non-proximity URI`() {
        assertFailsWith<IllegalArgumentException> {
            parseProximityUri("cachet://verify?request_uri=http://relay/sessions/1/request")
        }
    }

    @Test
    fun `parseProximityUri rejects missing nonce`() {
        assertFailsWith<IllegalArgumentException> {
            parseProximityUri("cachet://proximity?vk=x&pack=p&q=q&p=p")
        }
    }

    @Test
    fun `parseProximityUri rejects missing ephemeral key`() {
        assertFailsWith<IllegalArgumentException> {
            parseProximityUri("cachet://proximity?n=x&pack=p&q=q&p=p")
        }
    }

    @Test
    fun `parseProximityUri handles empty predicates`() {
        val parsed = parseProximityUri("cachet://proximity?n=abc&vk=key&pack=p&q=q&p=")
        assertTrue(parsed.predicates.isEmpty())
    }

    // ── VP QR encoding / decoding ──

    @Test
    fun `VP QR round-trip preserves payload`() {
        val original = "test-payload-data".encodeToByteArray()
        val encoded = QrDirectTransport.VP_PREFIX + original.encodeToBase64Url()

        assertTrue(isVpQrPayload(encoded))
        val decoded = decodeVpQrPayload(encoded)
        assertEquals("test-payload-data", decoded.decodeToString())
    }

    @Test
    fun `isProximityUri detects proximity URIs`() {
        assertTrue(isProximityUri("cachet://proximity?n=abc&vk=key&pack=p"))
        assertFalse(isProximityUri("cachet://verify?request_uri=http://relay"))
        assertFalse(isProximityUri("cachet-vp:payload"))
    }

    @Test
    fun `isVpQrPayload detects VP QR payloads`() {
        assertTrue(isVpQrPayload("cachet-vp:something"))
        assertFalse(isVpQrPayload("cachet://proximity?n=abc"))
        assertFalse(isVpQrPayload("cachet://verify?request_uri=http://relay"))
    }

    @Test
    fun `decodeVpQrPayload rejects non-VP content`() {
        assertFailsWith<IllegalArgumentException> {
            decodeVpQrPayload("not-a-vp-payload")
        }
    }

    // ── Base64url encoding ──

    @Test
    fun `base64url round-trip preserves arbitrary bytes`() {
        val data = byteArrayOf(0, 1, 2, 127, -128, -1, 42, 99)
        val encoded = data.encodeToBase64Url()
        val decoded = encoded.decodeFromBase64Url()
        assertEquals(data.toList(), decoded.toList())
    }

    @Test
    fun `base64url encodes empty array`() {
        val encoded = byteArrayOf().encodeToBase64Url()
        assertEquals("", encoded)
    }

    // ── Transport: sendResponse ──

    @Test
    fun `sendResponse throws PayloadTooLargeException for oversized payload`() {
        val transport = createTestTransport()

        val largePayload = ByteArray(3000) { 42 }

        val exception = assertFailsWith<PayloadTooLargeException> {
            kotlinx.coroutines.test.runTest {
                transport.sendResponse("session-data", largePayload)
            }
        }
        assertTrue(exception.message!!.contains("exceeds QR capacity"))
    }

    @Test
    fun `sendResponse returns VP QR payload for small payloads`() {
        val transport = createTestTransport()

        kotlinx.coroutines.test.runTest {
            val payload = "small-vp-data".encodeToByteArray()
            val result = transport.sendResponse("session-data", payload)

            assertTrue(result!!.startsWith(QrDirectTransport.VP_PREFIX))
            val decoded = decodeVpQrPayload(result)
            assertEquals("small-vp-data", decoded.decodeToString())
        }
    }

    // ── Transport: createSession ──

    @Test
    fun `createSession produces valid proximity URI`() {
        val transport = createTestTransport()

        kotlinx.coroutines.test.runTest {
            val session = transport.createSession(
                SessionParams("age-check", "How old?", listOf("age_gte_18"))
            )

            assertTrue(session.qrPayload.startsWith("cachet://proximity?"))
            assertEquals("age-check", session.packId)
            assertEquals("test-nonce", session.sessionNonce)
            assertEquals("fake-pub-key", session.ephemeralPubKey)
            assertEquals("fake-priv-key", session.ephemeralPrivKey)
        }
    }

    // ── Helpers ──

    private fun createTestTransport(): QrDirectTransport {
        val fakeKeyGen = object : EphemeralKeyGenerator {
            override fun generateX25519KeyPair() = EphemeralKeyPair("fake-pub-key", "fake-priv-key")
        }
        val fakeNonceGen = object : NonceGenerator {
            override fun generate() = "test-nonce"
        }
        return QrDirectTransport(LocalSessionManager(fakeKeyGen, fakeNonceGen))
    }
}
