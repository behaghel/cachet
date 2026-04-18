package id.cachet.wallet.android.ui.mapper

import id.cachet.wallet.android.ui.components.TrustStatus
import id.cachet.wallet.android.ui.model.ReceiptLogStatus
import id.cachet.wallet.domain.model.ConsentDetails
import id.cachet.wallet.domain.model.ConsentReceipt
import id.cachet.wallet.domain.model.SignedCertificateTimestamp
import id.cachet.wallet.domain.model.TransparencyLogEntry
import kotlin.time.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityMapperTest {

    private fun makeReceipt(
        id: String = "receipt-1",
        purpose: String = "Age verification for online purchase",
        rpDisplayName: String = "Example Shop",
        predicateCount: Int = 2,
        verifiedLog: Boolean = false,
        outcome: String = ConsentReceipt.OUTCOME_PASSED,
        totalPredicatesCount: Int = 0
    ): ConsentReceipt {
        val logEntry = if (verifiedLog) TransparencyLogEntry(
            logId = "log-1",
            logIndex = 0,
            sct = SignedCertificateTimestamp(
                logId = "log-1",
                timestamp = Clock.System.now(),
                signature = "sig"
            ),
            isVerified = true
        ) else null

        return ConsentReceipt(
            id = id,
            timestamp = Clock.System.now(),
            purpose = purpose,
            predicatesProven = (1..predicateCount).map { "predicate_$it" },
            rpIdentifier = "did:web:shop.example.com",
            rpDisplayName = rpDisplayName,
            userConsent = ConsentDetails(
                explicitConsent = true,
                dataMinimizationAcknowledged = true,
                retentionPeriodUnderstood = true,
                retentionPeriodDays = 90
            ),
            credentialId = "cred-1",
            outcome = outcome,
            totalPredicatesCount = totalPredicatesCount,
            transparencyLogEntry = logEntry
        )
    }

    // ── toReceiptItem ──

    @Test
    fun `toReceiptItem maps title from purpose`() {
        val receipt = makeReceipt(purpose = "Identity check for rental")
        val item = ActivityMapper.toReceiptItem(receipt)
        assertEquals("Identity check for rental", item.title)
    }

    @Test
    fun `toReceiptItem maps counterparty from rpDisplayName`() {
        val receipt = makeReceipt(rpDisplayName = "Airbnb Host")
        val item = ActivityMapper.toReceiptItem(receipt)
        assertEquals("Airbnb Host", item.counterparty)
    }

    @Test
    fun `toReceiptItem sets LOGGED status when log entry verified`() {
        val receipt = makeReceipt(verifiedLog = true)
        val item = ActivityMapper.toReceiptItem(receipt)
        assertEquals(ReceiptLogStatus.LOGGED, item.logStatus)
    }

    @Test
    fun `toReceiptItem sets PENDING status when no log entry`() {
        val receipt = makeReceipt(verifiedLog = false)
        val item = ActivityMapper.toReceiptItem(receipt)
        assertEquals(ReceiptLogStatus.PENDING, item.logStatus)
    }

    // ── toHistoryEntry ──

    @Test
    fun `toHistoryEntry maps subtitle with rpDisplayName`() {
        val receipt = makeReceipt(rpDisplayName = "Acme Corp")
        val entry = ActivityMapper.toHistoryEntry(receipt)
        assertTrue(entry.subtitle.contains("Acme Corp"))
    }

    @Test
    fun `toHistoryEntry sets proof summary count`() {
        val receipt = makeReceipt(predicateCount = 3)
        val entry = ActivityMapper.toHistoryEntry(receipt)
        assertEquals("3 proofs shared", entry.proofSummary)
    }

    // ── Status mapping from outcome ──

    @Test
    fun `toHistoryEntry maps PASSED outcome to PASSED status`() {
        val receipt = makeReceipt(outcome = ConsentReceipt.OUTCOME_PASSED)
        val entry = ActivityMapper.toHistoryEntry(receipt)
        assertEquals(TrustStatus.PASSED, entry.status)
    }

    @Test
    fun `toHistoryEntry maps INCOMPLETE outcome to INCOMPLETE status`() {
        val receipt = makeReceipt(outcome = ConsentReceipt.OUTCOME_INCOMPLETE)
        val entry = ActivityMapper.toHistoryEntry(receipt)
        assertEquals(TrustStatus.INCOMPLETE, entry.status)
    }

    @Test
    fun `toHistoryEntry maps unknown outcome to PENDING status`() {
        val receipt = makeReceipt(outcome = "unknown")
        val entry = ActivityMapper.toHistoryEntry(receipt)
        assertEquals(TrustStatus.PENDING, entry.status)
    }

    // ── Proof summary for INCOMPLETE ──

    @Test
    fun `toHistoryEntry shows X of Y proofs passed for INCOMPLETE with totalPredicatesCount`() {
        val receipt = makeReceipt(
            predicateCount = 2,
            outcome = ConsentReceipt.OUTCOME_INCOMPLETE,
            totalPredicatesCount = 4
        )
        val entry = ActivityMapper.toHistoryEntry(receipt)
        assertEquals("2 of 4 proofs passed", entry.proofSummary)
    }

    @Test
    fun `toHistoryEntry shows N proofs shared for PASSED even with totalPredicatesCount`() {
        val receipt = makeReceipt(
            predicateCount = 4,
            outcome = ConsentReceipt.OUTCOME_PASSED,
            totalPredicatesCount = 4
        )
        val entry = ActivityMapper.toHistoryEntry(receipt)
        assertEquals("4 proofs shared", entry.proofSummary)
    }
}
