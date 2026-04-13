package id.cachet.wallet.android.ui.mapper

import id.cachet.wallet.android.ui.components.CachetType
import id.cachet.wallet.android.ui.model.CachPackUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CachPackMapperTest {

    private fun makePack(type: CachetType) = CachPackUi(
        id = "pack-${type.name}",
        question = "test?",
        description = "test pack",
        proofCount = 1,
        cachetType = type
    )

    // ── toVerificationRequest ──

    @Test
    fun `childcare pack has 4 predicates`() {
        val request = CachPackMapper.toVerificationRequest(makePack(CachetType.CHILDCARE))
        assertEquals(4, request.predicates.size)
    }

    @Test
    fun `age pack has 1 predicate`() {
        val request = CachPackMapper.toVerificationRequest(makePack(CachetType.AGE))
        assertEquals(1, request.predicates.size)
    }

    @Test
    fun `seller pack has 4 predicates`() {
        val request = CachPackMapper.toVerificationRequest(makePack(CachetType.SELLER))
        assertEquals(4, request.predicates.size)
    }

    // ── toCachetResult ──

    @Test
    fun `all passed returns cachet name and full count`() {
        val pack = makePack(CachetType.CHILDCARE)
        val result = CachPackMapper.toCachetResult(pack, allPassed = true)
        assertEquals("Childcare Ready", result.cachetName)
        assertTrue(result.allPassed)
        assertEquals(4, result.passedCount)
        assertEquals(4, result.totalCount)
    }

    @Test
    fun `not all passed returns Incomplete with mixed predicates`() {
        val pack = makePack(CachetType.SELLER)
        val result = CachPackMapper.toCachetResult(pack, allPassed = false)
        assertEquals("Incomplete", result.cachetName)
        assertFalse(result.allPassed)
        // Seller: indices 0,1 pass; indices 2,3 fail
        assertEquals(2, result.passedCount)
        assertEquals(4, result.totalCount)
        assertTrue(result.predicates[0].passed)
        assertTrue(result.predicates[1].passed)
        assertFalse(result.predicates[2].passed)
        assertEquals("Fulfilment rate below threshold", result.predicates[2].failReason)
        assertFalse(result.predicates[3].passed)
        assertEquals("Chargeback data unavailable", result.predicates[3].failReason)
        assertNull(result.validityLabel)
    }

    @Test
    fun `childcare fail has 2 of 4 passed`() {
        val pack = makePack(CachetType.CHILDCARE)
        val result = CachPackMapper.toCachetResult(pack, allPassed = false)
        assertEquals(2, result.passedCount)
        assertTrue(result.predicates[0].passed)
        assertTrue(result.predicates[1].passed)
        assertFalse(result.predicates[2].passed)
        assertFalse(result.predicates[3].passed)
    }
}
