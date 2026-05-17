package id.cachet.wallet.android.trusttrail

import id.cachet.wallet.android.trusttrail.ui.DIAL_TOTAL_SWEEP
import id.cachet.wallet.android.trusttrail.ui.strengthToSweep
import org.junit.Assert.assertEquals
import org.junit.Test

class TierDialGeometryTest {

    @Test
    fun `zero strength produces zero sweep`() {
        assertEquals(0f, strengthToSweep(0f), 0.01f)
    }

    @Test
    fun `full strength produces full C-arc sweep`() {
        assertEquals(DIAL_TOTAL_SWEEP, strengthToSweep(1f), 0.01f)
    }

    @Test
    fun `72 percent strength gives proportional sweep`() {
        val expected = 0.72f * DIAL_TOTAL_SWEEP // 194.4°
        assertEquals(expected, strengthToSweep(0.72f), 0.01f)
    }

    @Test
    fun `15 percent strength gives proportional sweep`() {
        val expected = 0.15f * DIAL_TOTAL_SWEEP // 40.5°
        assertEquals(expected, strengthToSweep(0.15f), 0.01f)
    }

    @Test
    fun `negative strength clamped to zero`() {
        assertEquals(0f, strengthToSweep(-0.5f), 0.01f)
    }

    @Test
    fun `strength above 1 clamped to max`() {
        assertEquals(DIAL_TOTAL_SWEEP, strengthToSweep(1.5f), 0.01f)
    }
}
