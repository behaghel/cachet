package id.cachet.wallet.domain.crypto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SDJWTParserTest {

    // Helper: base64url-encode a disclosure array [salt, claimName, value]
    private fun encodeDisclosure(salt: String, claim: String, value: String): String {
        val json = """["$salt","$claim","$value"]"""
        return json.encodeToByteArray().toBase64()
            .replace('+', '-')
            .replace('/', '_')
            .trimEnd('=')
    }

    private val issuerJwt = "eyJhbGciOiJFUzI1NiJ9.eyJpc3MiOiJkaWQ6d2ViOmlzc3VlciJ9.sig"
    private val disc1 = encodeDisclosure("salt1", "age", "30")
    private val disc2 = encodeDisclosure("salt2", "name", "Alice")
    private val disc3 = encodeDisclosure("salt3", "nationality", "FR")

    // ── parse ──

    @Test
    fun `parse splits issuer JWT and disclosures`() {
        val raw = "$issuerJwt~$disc1~$disc2~"
        val parsed = SDJWTParser.parse(raw)

        assertEquals(issuerJwt, parsed.issuerJWT)
        assertEquals(2, parsed.disclosures.size)
        assertEquals("age", parsed.disclosures[0].claimName)
        assertEquals("name", parsed.disclosures[1].claimName)
        assertTrue(parsed.claims.containsKey("age"))
        assertTrue(parsed.claims.containsKey("name"))
    }

    @Test
    fun `parse requires tilde-separated parts`() {
        try {
            SDJWTParser.parse("just-a-jwt-no-tilde")
            assertTrue(false, "Should have thrown")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Invalid SD-JWT"))
        }
    }

    @Test
    fun `parse ignores empty segments between tildes`() {
        val raw = "$issuerJwt~~$disc1~"
        val parsed = SDJWTParser.parse(raw)
        assertEquals(1, parsed.disclosures.size)
        assertEquals("age", parsed.disclosures[0].claimName)
    }

    @Test
    fun `parse skips KB-JWT segments`() {
        val kbJwt = "header.payload.signature"
        val raw = "$issuerJwt~$disc1~$kbJwt~"
        val parsed = SDJWTParser.parse(raw)
        assertEquals(1, parsed.disclosures.size)
        assertEquals("age", parsed.disclosures[0].claimName)
    }

    @Test
    fun `parse handles malformed base64 gracefully`() {
        val raw = "$issuerJwt~!!!invalid-base64!!!~$disc1~"
        val parsed = SDJWTParser.parse(raw)
        assertEquals(1, parsed.disclosures.size)
        assertEquals("age", parsed.disclosures[0].claimName)
    }

    // ── selectivePresentation ──

    @Test
    fun `selectivePresentation includes only requested claims`() {
        val raw = "$issuerJwt~$disc1~$disc2~$disc3~"
        val parsed = SDJWTParser.parse(raw)
        val presentation = SDJWTParser.selectivePresentation(parsed, setOf("age", "nationality"))

        assertTrue(presentation.startsWith(issuerJwt))
        // Count disclosure segments (between ~ delimiters, excluding issuerJwt and trailing empty)
        val parts = presentation.split("~").filter { it.isNotEmpty() }
        // issuerJwt + 2 disclosures = 3 non-empty parts
        assertEquals(3, parts.size)
    }

    @Test
    fun `selectivePresentation ends with trailing tilde`() {
        val raw = "$issuerJwt~$disc1~"
        val parsed = SDJWTParser.parse(raw)
        val presentation = SDJWTParser.selectivePresentation(parsed, setOf("age"))
        assertTrue(presentation.endsWith("~"))
    }
}
