package id.cachet.wallet.trusttrail.usecase

import id.cachet.wallet.trusttrail.model.DiscoveredPlatform
import id.cachet.wallet.trusttrail.model.EmailHeader
import id.cachet.wallet.trusttrail.provider.EmailProvider
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlatformDiscoveryTest {

    private val testDate = Instant.parse("2026-04-25T10:00:00Z")

    @Test
    fun `discovers vinted from headers`() = runTest {
        val provider = FakeEmailProvider(
            headers = listOf(
                EmailHeader("vinted.es", "Ton article s'est vendu !", testDate, "msg-1"),
                EmailHeader("vinted.es", "Ton article s'est vendu !", testDate, "msg-2"),
                EmailHeader("vinted.fr", "Votre article a ete vendu !", testDate, "msg-3"),
            )
        )
        val useCase = InboxScannerUseCase(provider)

        val platforms = useCase.discoverPlatforms()

        assertEquals(1, platforms.size)
        assertEquals("vinted", platforms[0].platform)
        assertEquals(3, platforms[0].emailCount)
    }

    @Test
    fun `discovers multiple platforms`() = runTest {
        val provider = FakeEmailProvider(
            headers = listOf(
                EmailHeader("vinted.es", "Sale", testDate, "msg-1"),
                EmailHeader("info.homeexchange.com", "Exchange confirmed", testDate, "msg-2"),
                EmailHeader("mail.care.com", "Booking Confirmed", testDate, "msg-3"),
                EmailHeader("mail.care.com", "Payment Receipt", testDate, "msg-4"),
            )
        )
        val useCase = InboxScannerUseCase(provider)

        val platforms = useCase.discoverPlatforms()

        assertEquals(3, platforms.size)

        val byPlatform = platforms.associateBy { it.platform }
        assertEquals(1, byPlatform["vinted"]?.emailCount)
        assertEquals(1, byPlatform["homeexchange.com"]?.emailCount)
        assertEquals(2, byPlatform["care.com"]?.emailCount)
    }

    @Test
    fun `unknown senders are ignored`() = runTest {
        val provider = FakeEmailProvider(
            headers = listOf(
                EmailHeader("gmail.com", "Hey how are you", testDate, "msg-1"),
                EmailHeader("outlook.com", "Meeting tomorrow", testDate, "msg-2"),
                EmailHeader("vinted.es", "Sale notification", testDate, "msg-3"),
            )
        )
        val useCase = InboxScannerUseCase(provider)

        val platforms = useCase.discoverPlatforms()

        assertEquals(1, platforms.size)
        assertEquals("vinted", platforms[0].platform)
    }

    @Test
    fun `empty inbox returns empty list`() = runTest {
        val provider = FakeEmailProvider(headers = emptyList())
        val useCase = InboxScannerUseCase(provider)

        val platforms = useCase.discoverPlatforms()

        assertTrue(platforms.isEmpty())
    }

    @Test
    fun `inbox with only unknown senders returns empty list`() = runTest {
        val provider = FakeEmailProvider(
            headers = listOf(
                EmailHeader("gmail.com", "Personal email", testDate, "msg-1"),
                EmailHeader("work.com", "Work email", testDate, "msg-2"),
            )
        )
        val useCase = InboxScannerUseCase(provider)

        val platforms = useCase.discoverPlatforms()

        assertTrue(platforms.isEmpty())
    }

    @Test
    fun `platforms are sorted by email count descending`() = runTest {
        val provider = FakeEmailProvider(
            headers = listOf(
                EmailHeader("vinted.es", "Sale 1", testDate, "msg-1"),
                EmailHeader("vinted.es", "Sale 2", testDate, "msg-2"),
                EmailHeader("vinted.es", "Sale 3", testDate, "msg-3"),
                EmailHeader("info.homeexchange.com", "Exchange 1", testDate, "msg-4"),
                EmailHeader("mail.care.com", "Booking 1", testDate, "msg-5"),
                EmailHeader("mail.care.com", "Booking 2", testDate, "msg-6"),
            )
        )
        val useCase = InboxScannerUseCase(provider)

        val platforms = useCase.discoverPlatforms()

        assertEquals("vinted", platforms[0].platform)
        assertEquals(3, platforms[0].emailCount)
        assertEquals("care.com", platforms[1].platform)
        assertEquals(2, platforms[1].emailCount)
        assertEquals("homeexchange.com", platforms[2].platform)
        assertEquals(1, platforms[2].emailCount)
    }

    @Test
    fun `provider fetchHeaders is called with headers-only flag`() = runTest {
        val provider = FakeEmailProvider(headers = emptyList())
        val useCase = InboxScannerUseCase(provider)

        useCase.discoverPlatforms()

        assertTrue(provider.fetchHeadersCalled, "fetchHeaders should have been called")
        assertEquals(0, provider.fetchFullContentCallCount,
            "full content should never be fetched during discovery")
    }

    @Test
    fun `discovered platform includes message IDs for later fetch`() = runTest {
        val provider = FakeEmailProvider(
            headers = listOf(
                EmailHeader("vinted.es", "Sale 1", testDate, "msg-1"),
                EmailHeader("vinted.es", "Sale 2", testDate, "msg-2"),
            )
        )
        val useCase = InboxScannerUseCase(provider)

        val platforms = useCase.discoverPlatforms()

        assertEquals(listOf("msg-1", "msg-2"), platforms[0].messageIds)
    }
}

/**
 * Fake email provider for testing the use case without real API calls.
 */
class FakeEmailProvider(
    private val headers: List<EmailHeader> = emptyList(),
) : EmailProvider {

    var fetchHeadersCalled = false
        private set
    var fetchFullContentCallCount = 0
        private set

    override suspend fun fetchHeaders(scanDepthMonths: Int): List<EmailHeader> {
        fetchHeadersCalled = true
        return headers
    }

    override suspend fun fetchFullContent(messageId: String): EmailProvider.RawEmail? {
        fetchFullContentCallCount++
        return null
    }

    override fun isConnected(): Boolean = true
}
