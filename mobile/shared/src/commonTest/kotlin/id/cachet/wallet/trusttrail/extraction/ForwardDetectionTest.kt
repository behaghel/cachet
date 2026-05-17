package id.cachet.wallet.trusttrail.extraction

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ForwardDetectionTest {

    private val testDate = Instant.parse("2026-04-25T10:00:00Z")

    // --- Subject prefix detection ---

    @Test
    fun `rejects Fwd prefix`() {
        assertForwardRejected("Fwd: Your booking confirmed")
    }

    @Test
    fun `rejects Fw prefix`() {
        assertForwardRejected("Fw: Your booking confirmed")
    }

    @Test
    fun `rejects FWD uppercase`() {
        assertForwardRejected("FWD: Your booking confirmed")
    }

    @Test
    fun `rejects Fwd without space after colon`() {
        assertForwardRejected("Fwd:Your booking confirmed")
    }

    @Test
    fun `rejects TR prefix - French`() {
        assertForwardRejected("TR: Ton article s'est vendu !")
    }

    @Test
    fun `rejects WG prefix - German`() {
        assertForwardRejected("WG: Deine Buchung bestatigt")
    }

    @Test
    fun `rejects RV prefix - Spanish`() {
        assertForwardRejected("Rv: Reserva confirmada")
    }

    @Test
    fun `rejects VS prefix - Italian and Dutch`() {
        assertForwardRejected("VS: Prenotazione confermata")
    }

    @Test
    fun `rejects VL prefix - Finnish`() {
        assertForwardRejected("VL: Varaus vahvistettu")
    }

    @Test
    fun `rejects ENC prefix - Portuguese`() {
        assertForwardRejected("ENC: Reserva confirmada")
    }

    // --- Body marker detection ---

    @Test
    fun `rejects Gmail forwarded message marker`() {
        val body = "---------- Forwarded message ----------\n" +
            "From: noreply@vinted.es\nTo: someone@gmail.com\n" +
            "Subject: Your item sold!\n\nDetails here."
        assertForwardRejectedByBody(body)
    }

    @Test
    fun `rejects French forward marker`() {
        val body = "Debut du message transfere :\n\n" +
            "De: L'equipe Vinted <no-reply@vinted.es>\nDate: 13 avril 2026\n\n" +
            "Ton article s'est vendu !"
        assertForwardRejectedByBody(body)
    }

    @Test
    fun `rejects Outlook original message marker`() {
        val body = "-----Original Message-----\n" +
            "From: noreply@care.com\nSent: Monday, April 13, 2026\n" +
            "Subject: Booking Confirmed\n\nDetails."
        assertForwardRejectedByBody(body)
    }

    // --- Non-forwards ---

    @Test
    fun `direct email not rejected`() {
        val evidence = ClaimExtractor.extract(
            from = "noreply@care.com",
            subject = "Booking Confirmed for Tuesday Jan 14",
            textBody = "Your booking for January 14 has been confirmed.\nAmount: \$150.00",
            htmlBody = "",
            date = testDate,
        )

        assertFalse(evidence.rejected)
        assertTrue(evidence.rejectionReason.isNullOrEmpty())
        assertTrue(evidence.claims.isNotEmpty())
    }

    @Test
    fun `reply not confused with forward`() {
        val evidence = ClaimExtractor.extract(
            from = "noreply@care.com",
            subject = "Re: Your booking confirmed",
            textBody = "Thank you for confirming.\nAmount: \$150.00",
            htmlBody = "",
            date = testDate,
        )

        assertFalse(evidence.rejected, "replies should not be rejected as forwards")
    }

    // --- Helpers ---

    private fun assertForwardRejected(subject: String) {
        val evidence = ClaimExtractor.extract(
            from = "someone@hotmail.com",
            subject = subject,
            textBody = "Forwarded email body with booking details.",
            htmlBody = "",
            date = testDate,
        )

        assertTrue(evidence.rejected, "forwarded email should be rejected: $subject")
        assertEquals("forwarded_email", evidence.rejectionReason)
        assertTrue(evidence.claims.isEmpty(), "rejected email should have no claims")
    }

    private fun assertForwardRejectedByBody(body: String) {
        val evidence = ClaimExtractor.extract(
            from = "someone@hotmail.com",
            subject = "Sale notification",
            textBody = body,
            htmlBody = "",
            date = testDate,
        )

        assertTrue(evidence.rejected, "forwarded email should be rejected by body marker")
        assertEquals("forwarded_email", evidence.rejectionReason)
        assertTrue(evidence.claims.isEmpty())
    }
}
