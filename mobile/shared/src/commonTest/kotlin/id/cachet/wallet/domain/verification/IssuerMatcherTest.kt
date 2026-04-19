package id.cachet.wallet.domain.verification

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IssuerMatcherTest {

    @Test
    fun exactMatch() {
        assertTrue(IssuerMatcher.matches("did:veriff:production", listOf("did:veriff:production")))
    }

    @Test
    fun wildcardMatch() {
        assertTrue(IssuerMatcher.matches("did:veriff:production", listOf("did:veriff:*")))
    }

    @Test
    fun wildcardMatchPrefix() {
        assertTrue(IssuerMatcher.matches("did:checks:germany-eu", listOf("did:checks:*-eu")))
    }

    @Test
    fun noMatch() {
        assertFalse(IssuerMatcher.matches("did:other:something", listOf("did:veriff:*")))
    }

    @Test
    fun emptyPatterns() {
        assertFalse(IssuerMatcher.matches("did:veriff:production", emptyList()))
    }

    @Test
    fun multiplePatterns_firstMatches() {
        assertTrue(IssuerMatcher.matches("did:veriff:x", listOf("did:veriff:x", "did:other:*")))
    }

    @Test
    fun multiplePatterns_secondMatches() {
        assertTrue(IssuerMatcher.matches("did:other:y", listOf("did:veriff:x", "did:other:*")))
    }

    @Test
    fun wildcardOnlyAsterisk() {
        // Pattern "*" matches everything — the prefix is ""
        assertTrue(IssuerMatcher.matches("anything", listOf("*")))
    }
}
