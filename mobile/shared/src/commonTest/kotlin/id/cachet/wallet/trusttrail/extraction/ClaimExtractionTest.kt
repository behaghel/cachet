package id.cachet.wallet.trusttrail.extraction

import id.cachet.wallet.trusttrail.model.EmailEvidence
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ClaimExtractionTest {

    private val testDate = Instant.parse("2026-04-25T10:00:00Z")

    // --- Care.com ---

    @Test
    fun `care_com booking confirmation from subject`() {
        val evidence = ClaimExtractor.extract(
            from = "noreply@care.com",
            subject = "Booking Confirmed for Tuesday Jan 14",
            textBody = "Dear Alice,\n\nYour booking for January 14, 2026 has been confirmed.\n" +
                "This is your 5th booking with this family.\nAmount: \$150.00\n\nThank you,\nCare.com",
            htmlBody = "",
            date = testDate,
        )

        assertEquals("care.com", evidence.platform)
        assertEquals("care.com", evidence.fromDomain)
        assertFalse(evidence.rejected)

        val booking = evidence.claims.first { it.type == "booking_confirmation" }
        assertEquals("subject", booking.source)
        assertEquals(0.9, booking.confidence, 0.01)

        val payment = evidence.claims.first { it.type == "payment_amount" }
        assertEquals("150.00", payment.fields["amount"])
    }

    @Test
    fun `care_com payment receipt`() {
        val evidence = ClaimExtractor.extract(
            from = "billing@mail.care.com",
            subject = "Payment Receipt - January 2026",
            textBody = "Payment received.\nTotal: \$325.50\nDate: 01/15/2026",
            htmlBody = "",
            date = testDate,
        )

        assertEquals("care.com", evidence.platform)

        val subjectClaim = evidence.claims.first { it.type == "payment_receipt" && it.source == "subject" }
        assertNotNull(subjectClaim)

        val bodyClaim = evidence.claims.first { it.type == "payment_amount" && it.source == "body_text" }
        assertEquals("325.50", bodyClaim.fields["amount"])
    }

    @Test
    fun `care_com review notification`() {
        val evidence = ClaimExtractor.extract(
            from = "noreply@care.com",
            subject = "New Review from the Johnson Family",
            textBody = "You received a 5-star review!\n\n\"Alice is wonderful with our kids.\"",
            htmlBody = "",
            date = testDate,
        )

        assertEquals("care.com", evidence.platform)
        val review = evidence.claims.first { it.type == "review_notification" }
        assertEquals("subject", review.source)
    }

    // --- HomeExchange ---

    @Test
    fun `homeexchange full extraction`() {
        val evidence = ClaimExtractor.extract(
            from = "\"HomeExchange\" <notifications@info.homeexchange.com>",
            subject = "You have confirmed your exchange at Ana's home.",
            textBody = """Your exchange at Ana's is confirmed Hubert!

Great news, you've confirmed your GuestPoints exchange with Ana! 620 GP have
been transferred to their account. Here are the details of your stay:

Home: Ana's house
Address: Avinguda de Roma 88, L'Estartit, Girona, Espanya
Dates: from Wednesday, May 27, 2026 to Sunday, May 31, 2026
Number of guests: 2

This exchange is automatically covered by our guarantees (cancellation
protection, non-conformity guarantee), so you can prepare it with complete
peace of mind!""",
            htmlBody = "",
            date = testDate,
        )

        assertEquals("homeexchange.com", evidence.platform)
        assertEquals("info.homeexchange.com", evidence.fromDomain)

        val types = evidence.claims.map { it.type }.toSet()
        assertTrue("exchange_confirmation" in types, "should detect exchange confirmation")
        assertTrue("stay_dates" in types, "should extract stay dates")
        assertTrue("guest_count" in types, "should extract guest count")
        assertTrue("guestpoints_transfer" in types, "should extract GuestPoints transfer")

        val dates = evidence.claims.first { it.type == "stay_dates" }
        assertEquals("Wednesday, May 27, 2026", dates.fields["checkin"])
        assertEquals("Sunday, May 31, 2026", dates.fields["checkout"])

        val guests = evidence.claims.first { it.type == "guest_count" }
        assertEquals("2", guests.fields["count"])

        val gp = evidence.claims.first { it.type == "guestpoints_transfer" }
        assertEquals("620", gp.fields["points"])
    }

    // --- Vinted ---

    @Test
    fun `vinted sale notification French`() {
        val evidence = ClaimExtractor.extract(
            from = "L'equipe Vinted <no-reply@vinted.es>",
            subject = "Ton article s'est vendu !",
            textBody = "",
            htmlBody = """<p><strong>Bonjour maylismb,</strong></p>
<p><strong>sophieyann2006</strong> a acheté</p>
<div>Sac polochon personnalisé pour Sixtine</div>
<div>40,00 €</div>
<p>Nous transférerons le paiement sur ton porte-monnaie Vinted une fois la commande terminée.</p>
<p>Expédie cette commande au cours des 5 prochains jours.</p>""",
            date = testDate,
        )

        assertEquals("vinted", evidence.platform)
        assertEquals("vinted.es", evidence.fromDomain)
        assertFalse(evidence.rejected)

        val types = evidence.claims.map { it.type }.toSet()
        assertTrue("sale_notification" in types, "should detect sale notification")
        assertTrue("sale_amount" in types, "should extract sale amount")
        assertTrue("buyer_identity" in types, "should extract buyer username")
        assertTrue("item_name" in types, "should extract item name")

        val amount = evidence.claims.first { it.type == "sale_amount" }
        assertEquals("40,00", amount.fields["amount"])

        val buyer = evidence.claims.first { it.type == "buyer_identity" }
        assertEquals("sophieyann2006", buyer.fields["buyer"])

        val item = evidence.claims.first { it.type == "item_name" }
        assertEquals("Sac polochon personnalisé pour Sixtine", item.fields["item"])
    }

    @Test
    fun `vinted sale notification English`() {
        val evidence = ClaimExtractor.extract(
            from = "Vinted <no-reply@vinted.com>",
            subject = "Your item has been sold!",
            textBody = "",
            htmlBody = """<p><strong>Hi johndoe,</strong></p>
<p><strong>janedoe42</strong> has bought</p>
<div>Vintage leather jacket</div>
<div>25.00 €</div>
<p>We will transfer the payment to your Vinted wallet once the order is complete.</p>""",
            date = testDate,
        )

        assertEquals("vinted", evidence.platform)
        assertFalse(evidence.rejected)

        val types = evidence.claims.map { it.type }.toSet()
        assertTrue("sale_notification" in types, "should detect English sale notification")
    }

    // --- Generic / Unknown ---

    @Test
    fun `unknown platform uses generic patterns`() {
        val evidence = ClaimExtractor.extract(
            from = "noreply@unknown-platform.com",
            subject = "Booking Confirmation #12345",
            textBody = "Your appointment is confirmed.",
            htmlBody = "",
            date = testDate,
        )

        assertEquals("", evidence.platform)
        assertEquals("unknown-platform.com", evidence.fromDomain)

        val booking = evidence.claims.first { it.type == "booking_confirmation" }
        assertEquals(0.6, booking.confidence, 0.01, "generic match should have lower confidence")
    }

    @Test
    fun `personal email produces no claims`() {
        val evidence = ClaimExtractor.extract(
            from = "friend@gmail.com",
            subject = "Hey, how are you?",
            textBody = "Just checking in. Hope all is well!",
            htmlBody = "",
            date = testDate,
        )

        assertEquals("", evidence.platform)
        assertTrue(evidence.claims.isEmpty(), "generic personal email should produce no claims")
    }

    @Test
    fun `HTML body used when text body is empty`() {
        val evidence = ClaimExtractor.extract(
            from = "noreply@care.com",
            subject = "Booking Confirmed",
            textBody = "",
            htmlBody = "<html><body><h1>Booking Confirmed</h1><p>Amount: \$200.00</p>" +
                "<p>Scheduled for January 20, 2026</p></body></html>",
            date = testDate,
        )

        assertEquals("care.com", evidence.platform)
        val payment = evidence.claims.first { it.type == "payment_amount" }
        assertEquals("body_html", payment.source)
        assertEquals("200.00", payment.fields["amount"])
    }
}
