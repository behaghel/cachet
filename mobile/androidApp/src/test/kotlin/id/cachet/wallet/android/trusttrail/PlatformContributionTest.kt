package id.cachet.wallet.android.trusttrail

import id.cachet.wallet.android.trusttrail.model.PlatformContributionUi
import id.cachet.wallet.android.trusttrail.ui.evidenceCountLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformContributionTest {

    @Test
    fun `single evidence item uses singular`() {
        assertEquals("1 evidence item", evidenceCountLabel(1))
    }

    @Test
    fun `multiple evidence items uses plural`() {
        assertEquals("7 evidence items", evidenceCountLabel(7))
    }

    @Test
    fun `zero evidence items uses plural`() {
        assertEquals("0 evidence items", evidenceCountLabel(0))
    }

    @Test
    fun `contribution percent formats correctly`() {
        val platform = PlatformContributionUi("HomeExchange", 7, 72)
        assertEquals("72%", "${platform.contributionPercent}%")
    }
}
