package id.cachet.wallet.android.ui.mapper

import id.cachet.wallet.android.ui.components.CachetType
import id.cachet.wallet.android.ui.components.TrustStatus
import id.cachet.wallet.domain.model.*
import kotlin.time.Clock
import kotlinx.datetime.Instant
import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class CredentialMapperTest {

    // ── getCredentialDisplayName ──

    @Test
    fun `identity credential type maps to display name`() {
        assertEquals("Identity Credential", CredentialMapper.getCredentialDisplayName(listOf("VerifiableCredential", "IdentityCredential")))
    }

    @Test
    fun `proof of age type maps to display name`() {
        assertEquals("Age Verification", CredentialMapper.getCredentialDisplayName(listOf("VerifiableCredential", "ProofOfAge")))
    }

    @Test
    fun `unknown type falls back to last type`() {
        assertEquals("CustomType", CredentialMapper.getCredentialDisplayName(listOf("VerifiableCredential", "CustomType")))
    }

    @Test
    fun `empty types falls back to Credential`() {
        assertEquals("Credential", CredentialMapper.getCredentialDisplayName(emptyList()))
    }

    // ── getIssuerDisplayName ──

    @Test
    fun `veriff issuer`() {
        assertEquals("Veriff", CredentialMapper.getIssuerDisplayName("did:veriff:production"))
    }

    @Test
    fun `cachet issuer`() {
        assertEquals("Cachet", CredentialMapper.getIssuerDisplayName("https://cachet.id/issuers/main"))
    }

    @Test
    fun `did web issuer extracts domain`() {
        assertEquals("example.com", CredentialMapper.getIssuerDisplayName("did:web:example.com#key1"))
    }

    @Test
    fun `long unknown issuer is truncated`() {
        val long = "a".repeat(30)
        val result = CredentialMapper.getIssuerDisplayName(long)
        assertEquals(25, result.length)
        assertTrue(result.endsWith("…"))
    }

    @Test
    fun `short unknown issuer is not truncated`() {
        assertEquals("short", CredentialMapper.getIssuerDisplayName("short"))
    }

    // ── cachetTypeForTypes ──

    @Test
    fun `identity credential maps to IDENTITY cachet`() {
        assertEquals(CachetType.IDENTITY, CredentialMapper.cachetTypeForTypes(listOf("IdentityCredential")))
    }

    @Test
    fun `seller credential maps to SELLER cachet`() {
        assertEquals(CachetType.SELLER, CredentialMapper.cachetTypeForTypes(listOf("SellerCredential")))
    }

    @Test
    fun `unknown type returns null`() {
        assertNull(CredentialMapper.cachetTypeForTypes(listOf("SomethingElse")))
    }

    // ── getPredicates ──

    @Test
    fun `extracts predicates from fully populated subject`() {
        val subject = CredentialSubject(
            id = "did:example:123",
            personalData = PersonalData(age = 25, nationality = "ES"),
            verified = true,
            verificationMethod = "liveness-check"
        )
        val result = CredentialMapper.getPredicates(subject)
        assertEquals(listOf("Age 25+", "ID Verified", "Nationality", "Liveness"), result)
    }

    @Test
    fun `empty subject returns fallback`() {
        val subject = CredentialSubject(id = "did:example:123")
        assertEquals(listOf("Verified"), CredentialMapper.getPredicates(subject))
    }

    // ── getTierLabel ──

    @Test
    fun `null verification level returns null`() {
        assertNull(CredentialMapper.getTierLabel(null))
    }

    @Test
    fun `premium level`() {
        assertEquals("Premium tier", CredentialMapper.getTierLabel("premium"))
    }

    @Test
    fun `standard level`() {
        assertEquals("Standard tier", CredentialMapper.getTierLabel("STANDARD"))
    }

    @Test
    fun `custom level capitalizes`() {
        assertEquals("Custom", CredentialMapper.getTierLabel("custom"))
    }

    // ── getExpiryLabel ──

    @Test
    fun `null expiry returns null`() {
        assertNull(CredentialMapper.getExpiryLabel(null))
    }

    @Test
    fun `invalid date returns null`() {
        assertNull(CredentialMapper.getExpiryLabel("not-a-date"))
    }

    @Test
    fun `past date returns Expired`() {
        val past = (Clock.System.now() - 30.days).toString()
        assertEquals("Expired", CredentialMapper.getExpiryLabel(past))
    }

    @Test
    fun `future date returns formatted expiry`() {
        val future = (Clock.System.now() + 365.days).toString()
        val result = CredentialMapper.getExpiryLabel(future)
        assertNotNull(result)
        assertTrue(result!!.startsWith("Expires "))
    }

    // ── freshnessLabel ──

    @Test
    fun `created today shows today`() {
        assertEquals("today", CredentialMapper.freshnessLabel(Clock.System.now() - 2.hours))
    }

    @Test
    fun `created days ago shows days`() {
        assertEquals("5d", CredentialMapper.freshnessLabel(Clock.System.now() - 5.days))
    }

    @Test
    fun `created months ago shows months`() {
        assertEquals("2mo", CredentialMapper.freshnessLabel(Clock.System.now() - 60.days))
    }

    @Test
    fun `created years ago shows years`() {
        assertEquals("1y", CredentialMapper.freshnessLabel(Clock.System.now() - 400.days))
    }

    // ── buildIssuerLine ──

    @Test
    fun `issuer line with all parts`() {
        assertEquals(
            "Issued by Veriff  ·  Premium tier  ·  Expires Dec 2026",
            CredentialMapper.buildIssuerLine("Veriff", "Premium tier", "Expires Dec 2026")
        )
    }

    @Test
    fun `issuer line without tier and expiry`() {
        assertEquals("Issued by Veriff", CredentialMapper.buildIssuerLine("Veriff", null, null))
    }

    // ── toCardUi end-to-end ──

    @Test
    fun `toCardUi maps a full credential`() {
        val stored = StoredCredential(
            localId = "test-1",
            credential = VerifiableCredential(
                id = "urn:test:1",
                context = listOf("https://www.w3.org/2018/credentials/v1"),
                type = listOf("VerifiableCredential", "IdentityCredential"),
                issuer = "did:veriff:production",
                issuanceDate = Clock.System.now().toString(),
                credentialSubject = CredentialSubject(
                    id = "did:holder:1",
                    personalData = PersonalData(age = 30),
                    verified = true,
                    verificationLevel = "premium"
                )
            ),
            createdAt = Clock.System.now() - 3.days,
            isRevoked = false
        )

        val result = CredentialMapper.toCardUi(stored)
        assertEquals("Identity Credential", result.displayName)
        assertTrue(result.issuerLine.contains("Veriff"))
        assertTrue(result.issuerLine.contains("Premium tier"))
        assertEquals("3d", result.freshnessLabel)
        assertEquals(CachetType.IDENTITY, result.cachetType)
        assertEquals(TrustStatus.VERIFIED, result.trustStatus)
        assertEquals(listOf("Age 30+", "ID Verified"), result.predicates)
        assertFalse(result.isRevoked)
    }

    @Test
    fun `toCardUi handles revoked credential`() {
        val stored = StoredCredential(
            localId = "test-2",
            credential = VerifiableCredential(
                id = "urn:test:2",
                context = listOf("https://www.w3.org/2018/credentials/v1"),
                type = listOf("VerifiableCredential", "IdentityCredential"),
                issuer = "did:veriff:production",
                issuanceDate = Clock.System.now().toString(),
                credentialSubject = CredentialSubject(id = "did:holder:2")
            ),
            createdAt = Clock.System.now(),
            isRevoked = true
        )

        val result = CredentialMapper.toCardUi(stored)
        assertEquals(TrustStatus.REVOKED, result.trustStatus)
        assertTrue(result.isRevoked)
    }

    // ── toVaultSummary ──

    @Test
    fun `vault summary counts correctly`() {
        val creds = listOf(
            makeStored("1", isRevoked = false),
            makeStored("2", isRevoked = false),
            makeStored("3", isRevoked = true)
        )
        val summary = CredentialMapper.toVaultSummary(creds)
        assertEquals(3, summary.totalCount)
        assertEquals(2, summary.verifiedCount)
        assertEquals(1, summary.pendingCount)
    }

    private fun makeStored(id: String, isRevoked: Boolean) = StoredCredential(
        localId = id,
        credential = VerifiableCredential(
            id = "urn:$id",
            context = listOf("https://www.w3.org/2018/credentials/v1"),
            type = listOf("VerifiableCredential"),
            issuer = "did:test:issuer",
            issuanceDate = Clock.System.now().toString(),
            credentialSubject = CredentialSubject(id = "did:holder:$id")
        ),
        createdAt = Clock.System.now(),
        isRevoked = isRevoked
    )
}
