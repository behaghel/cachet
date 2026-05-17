package id.cachet.wallet.trusttrail.strength

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TierResolverTest {

    private val thresholds = TierThresholds(bronze = 0.3, silver = 0.6, gold = 0.85)

    @Test
    fun `zero strength yields no tier`() {
        assertNull(TierResolver.resolve(0.0, thresholds))
    }

    @Test
    fun `below bronze yields no tier`() {
        assertNull(TierResolver.resolve(0.29, thresholds))
    }

    @Test
    fun `at bronze threshold yields bronze`() {
        assertEquals(Tier.BRONZE, TierResolver.resolve(0.3, thresholds))
    }

    @Test
    fun `between bronze and silver yields bronze`() {
        assertEquals(Tier.BRONZE, TierResolver.resolve(0.5, thresholds))
    }

    @Test
    fun `at silver threshold yields silver`() {
        assertEquals(Tier.SILVER, TierResolver.resolve(0.6, thresholds))
    }

    @Test
    fun `between silver and gold yields silver`() {
        assertEquals(Tier.SILVER, TierResolver.resolve(0.8, thresholds))
    }

    @Test
    fun `at gold threshold yields gold`() {
        assertEquals(Tier.GOLD, TierResolver.resolve(0.85, thresholds))
    }

    @Test
    fun `perfect strength yields gold`() {
        assertEquals(Tier.GOLD, TierResolver.resolve(1.0, thresholds))
    }

    @Test
    fun `tier degradation detected`() {
        val current = TierResolver.resolve(0.55, thresholds)   // bronze
        val previous = TierResolver.resolve(0.65, thresholds)  // silver

        assertEquals(Tier.BRONZE, current)
        assertEquals(Tier.SILVER, previous)

        val degraded = TierResolver.isDegraded(
            currentStrength = 0.55,
            credentialTier = Tier.SILVER,
            thresholds = thresholds,
        )
        assertEquals(true, degraded)
    }

    @Test
    fun `no degradation when tier holds`() {
        val degraded = TierResolver.isDegraded(
            currentStrength = 0.70,
            credentialTier = Tier.SILVER,
            thresholds = thresholds,
        )
        assertEquals(false, degraded)
    }

    @Test
    fun `upgrade detected`() {
        val upgraded = TierResolver.isUpgraded(
            currentStrength = 0.90,
            credentialTier = Tier.SILVER,
            thresholds = thresholds,
        )
        assertEquals(true, upgraded)
    }

    @Test
    fun `no upgrade when tier holds`() {
        val upgraded = TierResolver.isUpgraded(
            currentStrength = 0.70,
            credentialTier = Tier.SILVER,
            thresholds = thresholds,
        )
        assertEquals(false, upgraded)
    }
}
