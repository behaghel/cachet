package id.cachet.wallet.android.trusttrail

import id.cachet.wallet.android.trusttrail.ui.tierDisplayName
import id.cachet.wallet.trusttrail.strength.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TierBadgeTest {

    @Test
    fun `bronze tier displays BRONZE`() {
        assertEquals("BRONZE", tierDisplayName(Tier.BRONZE))
    }

    @Test
    fun `silver tier displays SILVER`() {
        assertEquals("SILVER", tierDisplayName(Tier.SILVER))
    }

    @Test
    fun `gold tier displays GOLD`() {
        assertEquals("GOLD", tierDisplayName(Tier.GOLD))
    }

    @Test
    fun `null tier returns null — badge hidden`() {
        assertNull(tierDisplayName(null))
    }
}
