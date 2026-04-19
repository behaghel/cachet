package id.cachet.wallet.domain.transport

import id.cachet.wallet.domain.crypto.EphemeralKeyGenerator
import id.cachet.wallet.domain.crypto.EphemeralKeyPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class LocalSessionManagerTest {

    @Test
    fun `createSession generates session with all parameters`() {
        val manager = createManager()

        val session = manager.createSession(
            SessionParams("childcare-v1", "Are you safe?", listOf("age_gte_18", "dbs_check"))
        )

        assertEquals("childcare-v1", session.packId)
        assertEquals("Are you safe?", session.question)
        assertEquals(listOf("age_gte_18", "dbs_check"), session.predicates)
        assertEquals("test-nonce-1", session.nonce)
        assertEquals("pub-1", session.keyPair.publicKeyBase64URL)
        assertEquals("priv-1", session.keyPair.privateKeyBase64URL)
    }

    @Test
    fun `each session gets a unique nonce and key pair`() {
        var nonceCount = 0
        var keyCount = 0
        val manager = LocalSessionManager(
            keyGenerator = object : EphemeralKeyGenerator {
                override fun generateX25519KeyPair(): EphemeralKeyPair {
                    keyCount++
                    return EphemeralKeyPair("pub-$keyCount", "priv-$keyCount")
                }
            },
            nonceGenerator = object : NonceGenerator {
                override fun generate(): String {
                    nonceCount++
                    return "nonce-$nonceCount"
                }
            }
        )

        val params = SessionParams("pack", "q", listOf("p"))
        val s1 = manager.createSession(params)
        val s2 = manager.createSession(params)

        assertNotEquals(s1.nonce, s2.nonce)
        assertNotEquals(s1.keyPair.publicKeyBase64URL, s2.keyPair.publicKeyBase64URL)
    }

    private fun createManager(): LocalSessionManager {
        val keyGen = object : EphemeralKeyGenerator {
            override fun generateX25519KeyPair() = EphemeralKeyPair("pub-1", "priv-1")
        }
        val nonceGen = object : NonceGenerator {
            override fun generate() = "test-nonce-1"
        }
        return LocalSessionManager(keyGen, nonceGen)
    }
}
