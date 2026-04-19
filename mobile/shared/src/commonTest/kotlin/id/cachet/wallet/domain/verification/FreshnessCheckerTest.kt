package id.cachet.wallet.domain.verification

import kotlin.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals

class FreshnessCheckerTest {

    private val now = Clock.System.now().epochSeconds

    @Test
    fun fresh_credential() {
        val iat = now - 3600 // 1 hour ago
        val exp = now + 86400 // expires tomorrow
        assertEquals("ok", FreshnessChecker.check(iat, exp))
    }

    @Test
    fun expired_credential() {
        val iat = now - 3600
        val exp = now - 60 // expired 1 minute ago
        assertEquals("expired", FreshnessChecker.check(iat, exp))
    }

    @Test
    fun stale_credential() {
        val iat = now - (91L * 24 * 60 * 60) // 91 days ago
        val exp = now + (365L * 24 * 60 * 60) // expires in a year
        assertEquals("stale", FreshnessChecker.check(iat, exp))
    }

    @Test
    fun no_expiration() {
        val iat = now - 3600
        assertEquals("ok", FreshnessChecker.check(iat, null))
    }

    @Test
    fun no_issuance_date() {
        val exp = now + 86400
        assertEquals("ok", FreshnessChecker.check(null, exp))
    }

    @Test
    fun both_null() {
        assertEquals("ok", FreshnessChecker.check(null, null))
    }

    @Test
    fun expired_takes_precedence_over_stale() {
        val iat = now - (100L * 24 * 60 * 60) // 100 days ago (stale)
        val exp = now - 60 // also expired
        assertEquals("expired", FreshnessChecker.check(iat, exp))
    }
}
