package id.cachet.wallet.domain.model

import id.cachet.wallet.testfixtures.makeCredential
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CredentialTest {

    // ── isExpired ──

    @Test
    fun `isExpired returns false when expirationDate is null`() {
        val cred = makeCredential(expirationDate = null)
        assertFalse(cred.isExpired())
    }

    @Test
    fun `isExpired returns false when expirationDate is in the future`() {
        val cred = makeCredential(expirationDate = "2099-12-31T23:59:59Z")
        assertFalse(cred.isExpired())
    }

    @Test
    fun `isExpired returns true when expirationDate is in the past`() {
        val cred = makeCredential(expirationDate = "2020-01-01T00:00:00Z")
        assertTrue(cred.isExpired())
    }

    @Test
    fun `isExpired returns false when expirationDate is unparseable`() {
        val cred = makeCredential(expirationDate = "not-a-date")
        assertFalse(cred.isExpired())
    }

    // ── getIssuanceInstant ──

    @Test
    fun `getIssuanceInstant parses valid ISO-8601 date`() {
        val cred = makeCredential(issuanceDate = "2026-01-15T10:00:00Z")
        val instant = cred.getIssuanceInstant()
        assertNotNull(instant)
    }

    @Test
    fun `getIssuanceInstant returns null for unparseable date`() {
        val cred = makeCredential(issuanceDate = "garbage")
        assertNull(cred.getIssuanceInstant())
    }

    // ── getSubjectId ──

    @Test
    fun `getSubjectId returns credentialSubject id`() {
        val cred = makeCredential(subjectId = "did:key:z6Mktest")
        assertEquals("did:key:z6Mktest", cred.getSubjectId())
    }

    // ── serialization ──

    @Test
    fun `data class serialization round-trips`() {
        val cred = makeCredential()
        val json = Json.encodeToString(VerifiableCredential.serializer(), cred)
        val decoded = Json.decodeFromString(VerifiableCredential.serializer(), json)
        assertEquals(cred, decoded)
    }
}
