package claims

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

var testDate = time.Date(2026, 4, 25, 10, 0, 0, 0, time.UTC)

func TestExtract_CareComBookingConfirmation(t *testing.T) {
	evidence := Extract(
		"noreply@care.com",
		"Booking Confirmed for Tuesday Jan 14",
		"Dear Alice,\n\nYour booking for January 14, 2026 has been confirmed.\nThis is your 5th booking with this family.\nAmount: $150.00\n\nThank you,\nCare.com",
		"",
		testDate,
	)

	assert.Equal(t, "care.com", evidence.Platform)
	assert.Equal(t, "care.com", evidence.FromDomain)
	require.NotEmpty(t, evidence.Claims)

	// Should detect booking confirmation from subject
	hasBooking := false
	for _, c := range evidence.Claims {
		if c.Type == "booking_confirmation" {
			hasBooking = true
			assert.Equal(t, "subject", c.Source)
			assert.InDelta(t, 0.9, c.Confidence, 0.01)
		}
	}
	assert.True(t, hasBooking, "should detect booking confirmation")

	// Should extract payment amount from body
	hasPayment := false
	for _, c := range evidence.Claims {
		if c.Type == "payment_amount" {
			hasPayment = true
			assert.Equal(t, "150.00", c.Fields["amount"])
		}
	}
	assert.True(t, hasPayment, "should extract payment amount")

	// Should extract repeat client count from body
	hasRepeat := false
	for _, c := range evidence.Claims {
		if c.Type == "repeat_client" {
			hasRepeat = true
			assert.Equal(t, "5", c.Fields["count"])
		}
	}
	assert.True(t, hasRepeat, "should detect repeat client")
}

func TestExtract_PaymentReceipt(t *testing.T) {
	evidence := Extract(
		"billing@mail.care.com",
		"Payment Receipt - January 2026",
		"Payment received.\nTotal: $325.50\nDate: 01/15/2026",
		"",
		testDate,
	)

	assert.Equal(t, "care.com", evidence.Platform)

	hasPaymentSubject := false
	hasPaymentBody := false
	for _, c := range evidence.Claims {
		if c.Type == "payment_receipt" && c.Source == "subject" {
			hasPaymentSubject = true
		}
		if c.Type == "payment_amount" && c.Source == "body_text" {
			hasPaymentBody = true
			assert.Equal(t, "325.50", c.Fields["amount"])
		}
	}
	assert.True(t, hasPaymentSubject, "should detect payment receipt from subject")
	assert.True(t, hasPaymentBody, "should extract payment amount from body")
}

func TestExtract_ReviewNotification(t *testing.T) {
	evidence := Extract(
		"noreply@care.com",
		"New Review from the Johnson Family",
		"You received a 5-star review!\n\n\"Alice is wonderful with our kids.\"",
		"",
		testDate,
	)

	assert.Equal(t, "care.com", evidence.Platform)
	hasReview := false
	for _, c := range evidence.Claims {
		if c.Type == "review_notification" {
			hasReview = true
			assert.Equal(t, "subject", c.Source)
		}
	}
	assert.True(t, hasReview, "should detect review notification")
}

func TestExtract_UnknownPlatform(t *testing.T) {
	evidence := Extract(
		"noreply@unknown-platform.com",
		"Booking Confirmation #12345",
		"Your appointment is confirmed.",
		"",
		testDate,
	)

	assert.Empty(t, evidence.Platform)
	assert.Equal(t, "unknown-platform.com", evidence.FromDomain)

	// Should still match generic patterns
	hasBooking := false
	for _, c := range evidence.Claims {
		if c.Type == "booking_confirmation" {
			hasBooking = true
			assert.InDelta(t, 0.6, c.Confidence, 0.01, "generic match should have lower confidence")
		}
	}
	assert.True(t, hasBooking, "generic patterns should catch booking confirmation")
}

func TestExtract_HTMLBody(t *testing.T) {
	evidence := Extract(
		"noreply@care.com",
		"Booking Confirmed",
		"", // no text body
		"<html><body><h1>Booking Confirmed</h1><p>Amount: $200.00</p><p>Scheduled for January 20, 2026</p></body></html>",
		testDate,
	)

	assert.Equal(t, "care.com", evidence.Platform)

	hasPayment := false
	for _, c := range evidence.Claims {
		if c.Type == "payment_amount" {
			hasPayment = true
			assert.Equal(t, "body_html", c.Source)
			assert.Equal(t, "200.00", c.Fields["amount"])
		}
	}
	assert.True(t, hasPayment, "should extract claims from HTML body")
}

func TestExtract_HomeExchangeConfirmation(t *testing.T) {
	evidence := Extract(
		`"HomeExchange" <notifications@info.homeexchange.com>`,
		"You have confirmed your exchange at Ana's home.",
		`Your exchange at Ana's is confirmed Hubert!

Great news, you've confirmed your GuestPoints exchange with Ana! 620 GP have
been transferred to their account. Here are the details of your stay:

Home: Ana's house
Address: Avinguda de Roma 88, L'Estartit, Girona, Espanya
Dates: from Wednesday, May 27, 2026 to Sunday, May 31, 2026
Number of guests: 2

This exchange is automatically covered by our guarantees (cancellation
protection, non-conformity guarantee), so you can prepare it with complete
peace of mind!`,
		"",
		testDate,
	)

	assert.Equal(t, "homeexchange.com", evidence.Platform)
	assert.Equal(t, "info.homeexchange.com", evidence.FromDomain)

	claimTypes := make(map[string]bool)
	for _, c := range evidence.Claims {
		claimTypes[c.Type] = true
	}

	assert.True(t, claimTypes["exchange_confirmation"], "should detect exchange confirmation")
	assert.True(t, claimTypes["stay_dates"], "should extract stay dates")
	assert.True(t, claimTypes["guest_count"], "should extract guest count")
	assert.True(t, claimTypes["guestpoints_transfer"], "should extract GuestPoints transfer")
	assert.True(t, claimTypes["host_identity"], "should extract host name")
	assert.True(t, claimTypes["guarantee_coverage"], "should detect guarantee coverage")

	// Check specific field values
	for _, c := range evidence.Claims {
		switch c.Type {
		case "stay_dates":
			assert.Equal(t, "Wednesday, May 27, 2026", c.Fields["checkin"])
			assert.Equal(t, "Sunday, May 31, 2026", c.Fields["checkout"])
		case "guest_count":
			assert.Equal(t, "2", c.Fields["count"])
		case "guestpoints_transfer":
			assert.Equal(t, "620", c.Fields["points"])
		case "host_identity":
			assert.Equal(t, "Ana", c.Fields["host"])
		}
	}
}

func TestExtract_VintedSaleNotification(t *testing.T) {
	evidence := Extract(
		`L'équipe Vinted <no-reply@vinted.es>`,
		"Ton article s'est vendu !",
		"",
		`<p><strong>Bonjour maylismb,</strong></p>
<p><strong>sophieyann2006</strong> a acheté</p>
<div>Sac polochon personnalisé pour Sixtine</div>
<div>40,00 €</div>
<p>Nous transférerons le paiement sur ton porte-monnaie Vinted une fois la commande terminée.</p>
<p>Expédie cette commande au cours des 5 prochains jours.</p>`,
		testDate,
	)

	assert.Equal(t, "vinted", evidence.Platform)
	assert.Equal(t, "vinted.es", evidence.FromDomain)
	assert.False(t, evidence.Rejected)

	claimTypes := make(map[string]bool)
	for _, c := range evidence.Claims {
		claimTypes[c.Type] = true
	}

	assert.True(t, claimTypes["sale_notification"], "should detect sale notification")
	assert.True(t, claimTypes["sale_amount"], "should extract sale amount")
	assert.True(t, claimTypes["buyer_identity"], "should extract buyer username")
	assert.True(t, claimTypes["item_name"], "should extract item name")

	// Check specific field values
	for _, c := range evidence.Claims {
		switch c.Type {
		case "sale_amount":
			assert.Equal(t, "40,00", c.Fields["amount"])
		case "buyer_identity":
			assert.Equal(t, "sophieyann2006", c.Fields["buyer"])
		case "item_name":
			assert.Equal(t, "Sac polochon personnalisé pour Sixtine", c.Fields["item"])
		}
	}
}

func TestExtract_VintedSaleEnglish(t *testing.T) {
	evidence := Extract(
		"Vinted <no-reply@vinted.com>",
		"Your item has been sold!",
		"",
		`<p><strong>Hi johndoe,</strong></p>
<p><strong>janedoe42</strong> has bought</p>
<div>Vintage leather jacket</div>
<div>25.00 €</div>
<p>We will transfer the payment to your Vinted wallet once the order is complete.</p>`,
		testDate,
	)

	assert.Equal(t, "vinted", evidence.Platform)
	assert.False(t, evidence.Rejected)

	claimTypes := make(map[string]bool)
	for _, c := range evidence.Claims {
		claimTypes[c.Type] = true
	}
	assert.True(t, claimTypes["sale_notification"], "should detect English sale notification")
}

func TestExtract_GenericEmail_NoClaims(t *testing.T) {
	evidence := Extract(
		"friend@gmail.com",
		"Hey, how are you?",
		"Just checking in. Hope all is well!",
		"",
		testDate,
	)

	assert.Empty(t, evidence.Platform)
	assert.Empty(t, evidence.Claims, "generic personal email should produce no claims")
}

// --- Forward detection tests ---

func TestExtract_RejectsForwardBySubjectPrefix(t *testing.T) {
	prefixes := []string{
		"Fwd: Your booking confirmed",
		"Fw: Your booking confirmed",
		"FWD: Your booking confirmed",
		"Fwd:Your booking confirmed", // no space
		"TR: Ton article s'est vendu !",
		"WG: Deine Buchung bestätigt",
		"Rv: Reserva confirmada",
	}

	for _, subj := range prefixes {
		t.Run(subj, func(t *testing.T) {
			evidence := Extract(
				"someone@hotmail.com",
				subj,
				"Forwarded email body with booking details.",
				"",
				testDate,
			)

			assert.True(t, evidence.Rejected, "forwarded email should be rejected")
			assert.Equal(t, "forwarded_email", evidence.RejectionReason)
			assert.Empty(t, evidence.Claims, "rejected email should have no claims")
		})
	}
}

func TestExtract_RejectsForwardByBodyMarkers(t *testing.T) {
	markers := []struct {
		name string
		body string
	}{
		{
			"Gmail forward marker",
			"---------- Forwarded message ----------\nFrom: noreply@vinted.es\nTo: someone@gmail.com\nSubject: Your item sold!\n\nDetails here.",
		},
		{
			"French forward marker",
			"Début du message transféré :\n\nDe: L'équipe Vinted <no-reply@vinted.es>\nDate: 13 avril 2026\n\nTon article s'est vendu !",
		},
		{
			"Outlook forward marker",
			"-----Original Message-----\nFrom: noreply@care.com\nSent: Monday, April 13, 2026\nSubject: Booking Confirmed\n\nDetails.",
		},
	}

	for _, tt := range markers {
		t.Run(tt.name, func(t *testing.T) {
			evidence := Extract(
				"someone@hotmail.com",
				"Sale notification",
				tt.body,
				"",
				testDate,
			)

			assert.True(t, evidence.Rejected, "forwarded email should be rejected")
			assert.Equal(t, "forwarded_email", evidence.RejectionReason)
			assert.Empty(t, evidence.Claims)
		})
	}
}

func TestExtract_DirectEmailNotRejected(t *testing.T) {
	evidence := Extract(
		"noreply@care.com",
		"Booking Confirmed for Tuesday Jan 14",
		"Your booking for January 14 has been confirmed.\nAmount: $150.00",
		"",
		testDate,
	)

	assert.False(t, evidence.Rejected)
	assert.Empty(t, evidence.RejectionReason)
	assert.NotEmpty(t, evidence.Claims)
}

func TestExtract_ReplyNotConfusedWithForward(t *testing.T) {
	evidence := Extract(
		"noreply@care.com",
		"Re: Your booking confirmed",
		"Thank you for confirming.\nAmount: $150.00",
		"",
		testDate,
	)

	assert.False(t, evidence.Rejected, "replies should not be rejected as forwards")
}

func TestExtractDomain(t *testing.T) {
	tests := []struct {
		from     string
		expected string
	}{
		{"noreply@care.com", "care.com"},
		{"Care.com <noreply@CARE.COM>", "care.com"},
		{"Alice <alice@mail.sittercity.com>", "mail.sittercity.com"},
		{"nodomain", ""},
	}
	for _, tt := range tests {
		t.Run(tt.from, func(t *testing.T) {
			assert.Equal(t, tt.expected, extractDomain(tt.from))
		})
	}
}

func TestDetectPlatform(t *testing.T) {
	tests := []struct {
		domain   string
		expected string
	}{
		{"care.com", "care.com"},
		{"mail.care.com", "care.com"},
		{"sittercity.com", "sittercity.com"},
		{"urbansitter.com", "urbansitter.com"},
		{"gmail.com", ""},
		{"notcare.com", ""},
	}
	for _, tt := range tests {
		t.Run(tt.domain, func(t *testing.T) {
			assert.Equal(t, tt.expected, detectPlatform(tt.domain))
		})
	}
}

func TestStripHTML(t *testing.T) {
	html := "<html><body><h1>Title</h1><p>Some <b>bold</b> text</p></body></html>"
	text := stripHTML(html)
	assert.Contains(t, text, "Title")
	assert.Contains(t, text, "Some")
	assert.Contains(t, text, "bold")
	assert.NotContains(t, text, "<")
}
