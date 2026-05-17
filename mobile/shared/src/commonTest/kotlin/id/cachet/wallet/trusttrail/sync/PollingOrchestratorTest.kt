package id.cachet.wallet.trusttrail.sync

import id.cachet.wallet.trusttrail.model.DiscoveredPlatform
import id.cachet.wallet.trusttrail.model.EmailHeader
import id.cachet.wallet.trusttrail.provider.EmailProvider
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PollingOrchestratorTest {

    private val oldDate = Instant.parse("2026-03-01T10:00:00Z")
    private val newDate = Instant.parse("2026-04-25T10:00:00Z")
    private val newerDate = Instant.parse("2026-04-26T10:00:00Z")

    @Test
    fun `detects new emails from consented platform`() = runTest {
        val store = InMemoryScanStateStore()
        store.saveLastScanTimestamp("gmail", newDate)

        val provider = CursorAwareFakeProvider(listOf(
            EmailHeader("vinted.es", "Old sale", oldDate, "msg-old"),
            EmailHeader("vinted.es", "New sale", newerDate, "msg-new"),
        ))

        val orchestrator = PollingOrchestrator(
            provider = provider,
            stateStore = store,
            providerId = "gmail",
            consentedPlatforms = setOf("vinted"),
        )

        val result = orchestrator.poll()

        assertEquals(1, result.newHeaders.size)
        assertEquals("msg-new", result.newHeaders[0].messageId)
        assertTrue(result.newPlatformSuggestions.isEmpty())
    }

    @Test
    fun `suggests new unconsented platform`() = runTest {
        val store = InMemoryScanStateStore()
        store.saveLastScanTimestamp("gmail", oldDate)

        val provider = CursorAwareFakeProvider(listOf(
            EmailHeader("vinted.es", "Vinted sale", newDate, "msg-1"),
            EmailHeader("mail.care.com", "Booking confirmed", newDate, "msg-2"),
        ))

        val orchestrator = PollingOrchestrator(
            provider = provider,
            stateStore = store,
            providerId = "gmail",
            consentedPlatforms = setOf("vinted"),
        )

        val result = orchestrator.poll()

        // Vinted is consented — appears as new header
        assertEquals(1, result.newHeaders.size)
        // Care.com is not consented — appears as suggestion
        assertEquals(1, result.newPlatformSuggestions.size)
        assertEquals("care.com", result.newPlatformSuggestions[0].platform)
        assertEquals(1, result.newPlatformSuggestions[0].emailCount)
    }

    @Test
    fun `no suggestions for already consented platforms`() = runTest {
        val store = InMemoryScanStateStore()

        val provider = CursorAwareFakeProvider(listOf(
            EmailHeader("vinted.es", "Sale 1", newDate, "msg-1"),
            EmailHeader("vinted.fr", "Sale 2", newDate, "msg-2"),
        ))

        val orchestrator = PollingOrchestrator(
            provider = provider,
            stateStore = store,
            providerId = "gmail",
            consentedPlatforms = setOf("vinted"),
        )

        val result = orchestrator.poll()

        assertTrue(result.newPlatformSuggestions.isEmpty())
        assertEquals(2, result.newHeaders.size)
    }

    @Test
    fun `unknown senders produce neither headers nor suggestions`() = runTest {
        val store = InMemoryScanStateStore()

        val provider = CursorAwareFakeProvider(listOf(
            EmailHeader("gmail.com", "Personal email", newDate, "msg-1"),
            EmailHeader("work.com", "Work stuff", newDate, "msg-2"),
        ))

        val orchestrator = PollingOrchestrator(
            provider = provider,
            stateStore = store,
            providerId = "gmail",
            consentedPlatforms = setOf("vinted"),
        )

        val result = orchestrator.poll()

        assertTrue(result.newHeaders.isEmpty())
        assertTrue(result.newPlatformSuggestions.isEmpty())
    }

    @Test
    fun `empty inbox poll returns empty`() = runTest {
        val store = InMemoryScanStateStore()
        val provider = CursorAwareFakeProvider(emptyList())

        val orchestrator = PollingOrchestrator(
            provider = provider,
            stateStore = store,
            providerId = "gmail",
            consentedPlatforms = setOf("vinted"),
        )

        val result = orchestrator.poll()

        assertTrue(result.newHeaders.isEmpty())
        assertTrue(result.newPlatformSuggestions.isEmpty())
    }

    @Test
    fun `cursor advanced after successful poll`() = runTest {
        val store = InMemoryScanStateStore()

        val provider = CursorAwareFakeProvider(listOf(
            EmailHeader("vinted.es", "Sale", newerDate, "msg-1"),
        ))

        val orchestrator = PollingOrchestrator(
            provider = provider,
            stateStore = store,
            providerId = "gmail",
            consentedPlatforms = setOf("vinted"),
        )

        orchestrator.poll()

        assertEquals(newerDate, store.getLastScanTimestamp("gmail"))
    }
}
