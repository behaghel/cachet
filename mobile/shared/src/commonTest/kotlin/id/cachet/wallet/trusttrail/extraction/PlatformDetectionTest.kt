package id.cachet.wallet.trusttrail.extraction

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlatformDetectionTest {

    @Test
    fun `care_com detected from bare domain`() {
        assertEquals("care.com", ClaimExtractor.detectPlatform("care.com"))
    }

    @Test
    fun `care_com detected from subdomain`() {
        assertEquals("care.com", ClaimExtractor.detectPlatform("mail.care.com"))
    }

    @Test
    fun `sittercity detected`() {
        assertEquals("sittercity.com", ClaimExtractor.detectPlatform("sittercity.com"))
    }

    @Test
    fun `urbansitter detected`() {
        assertEquals("urbansitter.com", ClaimExtractor.detectPlatform("urbansitter.com"))
    }

    @Test
    fun `vinted detected from multiple TLDs`() {
        val tlds = listOf("vinted.es", "vinted.com", "vinted.fr", "vinted.de",
            "vinted.nl", "vinted.be", "vinted.it", "vinted.pt", "vinted.pl",
            "vinted.lt", "vinted.co.uk")
        for (domain in tlds) {
            assertEquals("vinted", ClaimExtractor.detectPlatform(domain),
                message = "should detect vinted from $domain")
        }
    }

    @Test
    fun `homeexchange detected from all domains`() {
        val domains = listOf("homeexchange.com", "info.homeexchange.com", "bounces.homeexchange.com")
        for (domain in domains) {
            assertEquals("homeexchange.com", ClaimExtractor.detectPlatform(domain),
                message = "should detect homeexchange from $domain")
        }
    }

    @Test
    fun `unknown domain returns null`() {
        assertNull(ClaimExtractor.detectPlatform("gmail.com"))
    }

    @Test
    fun `similar domain not confused with known platform`() {
        assertNull(ClaimExtractor.detectPlatform("notcare.com"))
    }

    @Test
    fun `domain extraction from bare email`() {
        assertEquals("care.com", ClaimExtractor.extractDomain("noreply@care.com"))
    }

    @Test
    fun `domain extraction from angle bracket format`() {
        assertEquals("care.com", ClaimExtractor.extractDomain("Care.com <noreply@CARE.COM>"))
    }

    @Test
    fun `domain extraction from subdomain`() {
        assertEquals("mail.sittercity.com",
            ClaimExtractor.extractDomain("Alice <alice@mail.sittercity.com>"))
    }

    @Test
    fun `domain extraction from no-at returns empty`() {
        assertEquals("", ClaimExtractor.extractDomain("nodomain"))
    }
}
