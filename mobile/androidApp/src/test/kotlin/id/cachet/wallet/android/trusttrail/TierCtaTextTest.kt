package id.cachet.wallet.android.trusttrail

import id.cachet.wallet.android.trusttrail.ui.tierCtaText
import id.cachet.wallet.trusttrail.strength.Tier
import org.junit.Assert.assertEquals
import org.junit.Test

class TierCtaTextTest {

    @Test
    fun `below bronze suggests reaching bronze`() {
        assertEquals("Scan to reach Bronze", tierCtaText(null))
    }

    @Test
    fun `bronze suggests reaching silver`() {
        assertEquals("Scan to reach Silver", tierCtaText(Tier.BRONZE))
    }

    @Test
    fun `silver suggests reaching gold`() {
        assertEquals("Scan to reach Gold", tierCtaText(Tier.SILVER))
    }

    @Test
    fun `gold suggests staying gold`() {
        assertEquals("Scan to stay Gold", tierCtaText(Tier.GOLD))
    }
}
