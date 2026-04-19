package id.cachet.wallet.domain.cache

import id.cachet.wallet.domain.model.PackBadge
import id.cachet.wallet.domain.model.PackDefinition
import id.cachet.wallet.domain.model.PackPredicate
import id.cachet.wallet.testfixtures.FakePackDefinitionRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class PackDefinitionCacheTest {

    private val testPack = PackDefinition(
        id = "pack.identity.basic",
        version = "0.1.0",
        name = "Identity Verification",
        purpose = "Verify core identity attributes",
        jurisdictions = listOf("GLOBAL"),
        badge = PackBadge(label = "Identity Verified", ttl = "P180D", jurisdiction = "GLOBAL"),
        predicates = listOf(
            PackPredicate(
                id = "age.ge.18",
                claim = "age",
                operator = ">=",
                value = JsonPrimitive(18),
                issuersAccepted = listOf("did:veriff:*"),
                proofType = "sd-jwt"
            )
        )
    )

    private val loadBundled = { listOf(testPack) }

    @Test
    fun getAllPacks_emptyCache_seedsFromBundled() = runTest {
        val repo = FakePackDefinitionRepository()
        val cache = PackDefinitionCache(repo, loadBundled)

        val packs = cache.getAllPacks()

        assertEquals(1, packs.size)
        assertEquals("pack.identity.basic", packs[0].id)
    }

    @Test
    fun getAllPacks_freshCache_returnsCached() = runTest {
        val repo = FakePackDefinitionRepository()
        repo.storeAll(listOf(testPack))
        val cache = PackDefinitionCache(repo, loadBundled)

        val packs = cache.getAllPacks()

        assertEquals(1, packs.size)
        assertEquals("pack.identity.basic", packs[0].id)
    }

    @Test
    fun getPackById_existsInCache_returnsCached() = runTest {
        val repo = FakePackDefinitionRepository()
        repo.storePack(testPack)
        val cache = PackDefinitionCache(repo, loadBundled)

        val pack = cache.getPackById("pack.identity.basic")

        assertNotNull(pack)
        assertEquals("Identity Verification", pack.name)
    }

    @Test
    fun getPackById_notInCache_seedsAndReturns() = runTest {
        val repo = FakePackDefinitionRepository()
        val cache = PackDefinitionCache(repo, loadBundled)

        val pack = cache.getPackById("pack.identity.basic")

        assertNotNull(pack)
        assertEquals("Identity Verification", pack.name)
    }

    @Test
    fun getPackById_unknownPack_returnsNull() = runTest {
        val repo = FakePackDefinitionRepository()
        val cache = PackDefinitionCache(repo, loadBundled)

        val pack = cache.getPackById("pack.unknown")

        assertNull(pack)
    }

    @Test
    fun getAllPacks_staleCache_refreshesFromBundled() = runTest {
        val repo = FakePackDefinitionRepository()
        repo.storeAll(listOf(testPack))

        val staleClock = object : Clock {
            override fun now(): Instant = Clock.System.now() + 25.hours
        }
        val cache = PackDefinitionCache(repo, loadBundled, staleClock)

        val packs = cache.getAllPacks()

        assertEquals(1, packs.size)
    }

    @Test
    fun seedFromBundled_storesPacks() = runTest {
        val repo = FakePackDefinitionRepository()
        val cache = PackDefinitionCache(repo, loadBundled)

        val seeded = cache.seedFromBundled()

        assertEquals(1, seeded.size)
        val fromRepo = repo.getAllPacks().getOrNull()
        assertNotNull(fromRepo)
        assertEquals(1, fromRepo.size)
    }
}
