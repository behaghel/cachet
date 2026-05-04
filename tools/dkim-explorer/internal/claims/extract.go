package claims

import (
	"regexp"
	"strings"
	"time"
)

// EmailEvidence holds structured claims extracted from an email.
type EmailEvidence struct {
	Platform        string    // detected platform (e.g., "care.com")
	FromDomain      string    // sending domain
	Subject         string    // original subject line
	ReceivedDate    time.Time // Date header
	Claims          []Claim   // extracted structured claims
	Rejected        bool      // true if the email was rejected as evidence
	RejectionReason string    // reason for rejection (e.g., "forwarded_email")
}

// Claim is a single piece of evidence extracted from an email.
type Claim struct {
	Type       string            // e.g., "booking_confirmation", "payment_receipt", "review_notification"
	Confidence float64           // 0.0-1.0 extraction confidence
	Fields     map[string]string // extracted key-value pairs
	Source     string            // "subject", "body_text", "body_html"
}

// Extract analyzes email content and extracts structured claims.
func Extract(from, subject, textBody, htmlBody string, date time.Time) *EmailEvidence {
	fromDomain := extractDomain(from)
	platform := detectPlatform(fromDomain)

	evidence := &EmailEvidence{
		Platform:     platform,
		FromDomain:   fromDomain,
		Subject:      subject,
		ReceivedDate: date,
	}

	// Reject forwarded emails — they break the DKIM chain
	body := textBody
	if body == "" && htmlBody != "" {
		body = stripHTML(htmlBody)
	}
	if isForwarded(subject, body) {
		evidence.Rejected = true
		evidence.RejectionReason = "forwarded_email"
		return evidence
	}

	// Try subject-based extraction
	if claims := extractFromSubject(subject, platform); len(claims) > 0 {
		evidence.Claims = append(evidence.Claims, claims...)
	}

	// Try body-based extraction (prefer text, fall back to HTML)
	bodyForClaims := textBody
	source := "body_text"
	if bodyForClaims == "" && htmlBody != "" {
		bodyForClaims = stripHTML(htmlBody)
		source = "body_html"
	}
	if bodyForClaims != "" {
		if claims := extractFromBody(bodyForClaims, source, platform); len(claims) > 0 {
			evidence.Claims = append(evidence.Claims, claims...)
		}
	}

	return evidence
}

// extractDomain extracts the domain from an email From header.
// Handles both "user@domain.com" and "Name <user@domain.com>" formats.
func extractDomain(from string) string {
	// Try angle bracket format first
	if idx := strings.LastIndex(from, "@"); idx >= 0 {
		domain := from[idx+1:]
		domain = strings.TrimRight(domain, "> \t")
		return strings.ToLower(domain)
	}
	return ""
}

// stripHTML removes HTML tags, leaving just text content.
func stripHTML(html string) string {
	// Simple tag stripping — sufficient for claim extraction
	re := regexp.MustCompile(`<[^>]*>`)
	text := re.ReplaceAllString(html, " ")
	// Collapse whitespace
	text = regexp.MustCompile(`\s+`).ReplaceAllString(text, " ")
	return strings.TrimSpace(text)
}
