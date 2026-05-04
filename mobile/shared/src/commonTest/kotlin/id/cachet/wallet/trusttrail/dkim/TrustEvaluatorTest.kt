package id.cachet.wallet.trusttrail.dkim

import id.cachet.wallet.trusttrail.model.TrustLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrustEvaluatorTest {

    // --- Gmail (cryptographic provider) ---

    @Test
    fun `Gmail - DKIM pass yields cryptographic`() {
        val result = TrustEvaluator.evaluate(
            providerType = ProviderType.GMAIL,
            dkimResult = DkimResult.PASS,
            authenticationResultsHeaders = emptyList(),
        )
        assertEquals(TrustLevel.CRYPTOGRAPHIC, result.trustLevel)
        assertNull(result.rejectionReason)
    }

    @Test
    fun `Gmail - DKIM fail with AR dkim pass yields mta_attested`() {
        val result = TrustEvaluator.evaluate(
            providerType = ProviderType.GMAIL,
            dkimResult = DkimResult.FAIL,
            authenticationResultsHeaders = listOf(
                "mx.google.com; dkim=pass header.d=vinted.es"
            ),
        )
        assertEquals(TrustLevel.MTA_ATTESTED, result.trustLevel)
        assertNull(result.rejectionReason)
    }

    @Test
    fun `Gmail - DKIM fail without AR dkim pass yields rejected`() {
        val result = TrustEvaluator.evaluate(
            providerType = ProviderType.GMAIL,
            dkimResult = DkimResult.FAIL,
            authenticationResultsHeaders = listOf(
                "mx.google.com; dkim=fail header.d=vinted.es"
            ),
        )
        assertNull(result.trustLevel)
        assertEquals("broken_signature", result.rejectionReason)
    }

    @Test
    fun `Gmail - DKIM fail with no AR headers yields rejected`() {
        val result = TrustEvaluator.evaluate(
            providerType = ProviderType.GMAIL,
            dkimResult = DkimResult.FAIL,
            authenticationResultsHeaders = emptyList(),
        )
        assertNull(result.trustLevel)
        assertEquals("broken_signature", result.rejectionReason)
    }

    @Test
    fun `Gmail - DKIM not available falls back to AR`() {
        val result = TrustEvaluator.evaluate(
            providerType = ProviderType.GMAIL,
            dkimResult = DkimResult.NOT_CHECKED,
            authenticationResultsHeaders = listOf(
                "mx.google.com; dkim=pass header.d=vinted.es"
            ),
        )
        assertEquals(TrustLevel.MTA_ATTESTED, result.trustLevel)
    }

    // --- Outlook (mta_attested provider) ---

    @Test
    fun `Outlook - AR dkim pass yields mta_attested`() {
        val result = TrustEvaluator.evaluate(
            providerType = ProviderType.OUTLOOK,
            dkimResult = DkimResult.NOT_CHECKED,
            authenticationResultsHeaders = listOf(
                "protection.outlook.com; dkim=pass header.d=homeexchange.com"
            ),
        )
        assertEquals(TrustLevel.MTA_ATTESTED, result.trustLevel)
        assertNull(result.rejectionReason)
    }

    @Test
    fun `Outlook - no DKIM body hash verification attempted`() {
        // Even if we could verify DKIM, Outlook breaks body hashes
        // so we never attempt cryptographic verification
        val result = TrustEvaluator.evaluate(
            providerType = ProviderType.OUTLOOK,
            dkimResult = DkimResult.NOT_CHECKED, // never checked for Outlook
            authenticationResultsHeaders = listOf(
                "protection.outlook.com; dkim=pass header.d=care.com"
            ),
        )
        assertEquals(TrustLevel.MTA_ATTESTED, result.trustLevel)
    }

    @Test
    fun `Outlook - AR without dkim pass yields rejected`() {
        val result = TrustEvaluator.evaluate(
            providerType = ProviderType.OUTLOOK,
            dkimResult = DkimResult.NOT_CHECKED,
            authenticationResultsHeaders = listOf(
                "protection.outlook.com; spf=pass; dmarc=pass"
            ),
        )
        assertNull(result.trustLevel)
        assertEquals("broken_signature", result.rejectionReason)
    }

    @Test
    fun `Outlook - empty AR headers yields rejected`() {
        val result = TrustEvaluator.evaluate(
            providerType = ProviderType.OUTLOOK,
            dkimResult = DkimResult.NOT_CHECKED,
            authenticationResultsHeaders = emptyList(),
        )
        assertNull(result.trustLevel)
        assertEquals("broken_signature", result.rejectionReason)
    }
}
