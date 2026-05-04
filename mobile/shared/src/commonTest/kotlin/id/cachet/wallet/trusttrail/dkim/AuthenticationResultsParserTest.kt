package id.cachet.wallet.trusttrail.dkim

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthenticationResultsParserTest {

    @Test
    fun `detects dkim pass in standard AR header`() {
        val header = "mx.google.com; dkim=pass header.d=vinted.es; spf=pass"
        assertTrue(AuthenticationResultsParser.hasDkimPass(header))
    }

    @Test
    fun `detects dkim pass with verification detail`() {
        val header = "mx.google.com; dkim=pass (signature was verified) header.d=vinted.es"
        assertTrue(AuthenticationResultsParser.hasDkimPass(header))
    }

    @Test
    fun `detects dkim pass case insensitive`() {
        val header = "mx.google.com; DKIM=PASS header.d=example.com"
        assertTrue(AuthenticationResultsParser.hasDkimPass(header))
    }

    @Test
    fun `rejects dkim fail`() {
        val header = "mx.google.com; dkim=fail header.d=vinted.es; spf=pass"
        assertFalse(AuthenticationResultsParser.hasDkimPass(header))
    }

    @Test
    fun `rejects dkim none`() {
        val header = "mx.google.com; dkim=none; spf=pass"
        assertFalse(AuthenticationResultsParser.hasDkimPass(header))
    }

    @Test
    fun `rejects dkim temperror`() {
        val header = "mx.google.com; dkim=temperror header.d=example.com"
        assertFalse(AuthenticationResultsParser.hasDkimPass(header))
    }

    @Test
    fun `rejects empty header`() {
        assertFalse(AuthenticationResultsParser.hasDkimPass(""))
    }

    @Test
    fun `rejects header without dkim field`() {
        val header = "mx.google.com; spf=pass; dmarc=pass"
        assertFalse(AuthenticationResultsParser.hasDkimPass(header))
    }

    @Test
    fun `handles multiple AR headers - any pass is sufficient`() {
        val headers = listOf(
            "mx.google.com; spf=pass",
            "mx.google.com; dkim=pass header.d=vinted.es",
        )
        assertTrue(AuthenticationResultsParser.anyDkimPass(headers))
    }

    @Test
    fun `handles multiple AR headers - none pass`() {
        val headers = listOf(
            "mx.google.com; spf=pass",
            "mx.google.com; dkim=fail header.d=example.com",
        )
        assertFalse(AuthenticationResultsParser.anyDkimPass(headers))
    }
}
