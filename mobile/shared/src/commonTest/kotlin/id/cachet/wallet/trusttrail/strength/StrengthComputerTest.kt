package id.cachet.wallet.trusttrail.strength

import id.cachet.wallet.trusttrail.model.TrustLevel
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StrengthComputerTest {

    private val config = DecayConfig(windowMonths = 12, decayFunction = "linear")
    private val normalization = 10.0 // 10 perfect-score items = 1.0

    // --- Decay ---

    @Test
    fun `fresh evidence retains full score`() {
        val now = Instant.parse("2026-05-09T00:00:00Z")
        val item = makeItem(date = "2026-05-09", score = 0.95)

        val decayed = StrengthComputer.decayedScore(item, now, config)

        assertEquals(0.95, decayed, 0.01)
    }

    @Test
    fun `6-month-old evidence retains half score`() {
        val now = Instant.parse("2026-05-09T00:00:00Z")
        val item = makeItem(date = "2025-11-09", score = 0.90)

        val decayed = StrengthComputer.decayedScore(item, now, config)

        assertEquals(0.45, decayed, 0.05)
    }

    @Test
    fun `12-month-old evidence decays to zero`() {
        val now = Instant.parse("2026-05-09T00:00:00Z")
        val item = makeItem(date = "2025-05-09", score = 0.95)

        val decayed = StrengthComputer.decayedScore(item, now, config)

        assertEquals(0.0, decayed, 0.01)
    }

    @Test
    fun `evidence older than window is zero`() {
        val now = Instant.parse("2026-05-09T00:00:00Z")
        val item = makeItem(date = "2024-01-01", score = 0.95)

        val decayed = StrengthComputer.decayedScore(item, now, config)

        assertEquals(0.0, decayed, 0.01)
    }

    @Test
    fun `future evidence retains full score`() {
        val now = Instant.parse("2026-05-09T00:00:00Z")
        val item = makeItem(date = "2026-06-01", score = 0.95)

        val decayed = StrengthComputer.decayedScore(item, now, config)

        assertEquals(0.95, decayed, 0.01)
    }

    // --- Composite strength ---

    @Test
    fun `single fresh item normalized against factor`() {
        val now = Instant.parse("2026-05-09T00:00:00Z")
        val items = listOf(makeItem(date = "2026-05-01", score = 0.95))

        val strength = StrengthComputer.computeStrength(items, now, config, normalization)

        // 0.95 decayed slightly / 10.0 normalization
        assertTrue(strength > 0.08 && strength < 0.12)
    }

    @Test
    fun `10 fresh perfect items reach 1_0`() {
        val now = Instant.parse("2026-05-09T00:00:00Z")
        val items = (1..10).map { makeItem(date = "2026-05-0${it.coerceAtMost(9)}", score = 1.0) }

        val strength = StrengthComputer.computeStrength(items, now, config, normalization)

        assertEquals(1.0, strength, 0.05)
    }

    @Test
    fun `strength capped at 1_0 even with excess evidence`() {
        val now = Instant.parse("2026-05-09T00:00:00Z")
        val items = (1..20).map { makeItem(date = "2026-05-01", score = 1.0) }

        val strength = StrengthComputer.computeStrength(items, now, config, normalization)

        assertEquals(1.0, strength, 0.01)
    }

    @Test
    fun `empty evidence list yields zero`() {
        val now = Instant.parse("2026-05-09T00:00:00Z")

        val strength = StrengthComputer.computeStrength(emptyList(), now, config, normalization)

        assertEquals(0.0, strength, 0.01)
    }

    @Test
    fun `all expired evidence yields zero`() {
        val now = Instant.parse("2026-05-09T00:00:00Z")
        val items = listOf(
            makeItem(date = "2024-01-01", score = 0.95),
            makeItem(date = "2024-06-01", score = 0.90),
        )

        val strength = StrengthComputer.computeStrength(items, now, config, normalization)

        assertEquals(0.0, strength, 0.01)
    }

    @Test
    fun `mixed fresh and stale evidence`() {
        val now = Instant.parse("2026-05-09T00:00:00Z")
        val items = listOf(
            makeItem(date = "2026-05-01", score = 0.95), // fresh ~0.95
            makeItem(date = "2026-02-09", score = 0.90), // 3 months ~0.675
            makeItem(date = "2025-11-09", score = 0.85), // 6 months ~0.425
            makeItem(date = "2025-05-10", score = 0.80), // ~12 months ~0.0
        )

        val strength = StrengthComputer.computeStrength(items, now, config, normalization)

        // ~(0.95 + 0.675 + 0.425 + 0.0) / 10.0 = ~0.205
        assertTrue(strength > 0.15 && strength < 0.30,
            "expected ~0.2 but got $strength")
    }

    // --- Helpers ---

    private fun makeItem(date: String, score: Double) = EvidenceItem(
        type = "exchange_confirmation",
        platform = "homeexchange.com",
        date = Instant.parse("${date}T00:00:00Z"),
        trustLevel = TrustLevel.CRYPTOGRAPHIC,
        score = score,
        dkimDomain = "homeexchange.com",
    )
}
