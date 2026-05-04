package claims

import (
	"regexp"
	"strings"
)

// PlatformPattern defines extraction rules for a known platform.
type PlatformPattern struct {
	Platform    string
	FromDomains []string
	Subject     []patternRule
	Body        []patternRule
}

type patternRule struct {
	ClaimType  string
	Pattern    *regexp.Regexp
	Fields     []string // named capture groups to extract
	Confidence float64
}

// knownPlatforms holds the extraction patterns for recognized platforms.
// Data-driven: adding a new platform means adding an entry here.
var knownPlatforms = []PlatformPattern{
	{
		Platform:    "care.com",
		FromDomains: []string{"care.com", "mail.care.com"},
		Subject: []patternRule{
			{
				ClaimType:  "booking_confirmation",
				Pattern:    regexp.MustCompile(`(?i)booking\s+(confirmed|confirmation)`),
				Confidence: 0.9,
			},
			{
				ClaimType:  "payment_receipt",
				Pattern:    regexp.MustCompile(`(?i)payment\s+(receipt|received|confirmation)`),
				Confidence: 0.9,
			},
			{
				ClaimType:  "review_notification",
				Pattern:    regexp.MustCompile(`(?i)(new\s+review|review\s+(from|received))`),
				Confidence: 0.85,
			},
		},
		Body: []patternRule{
			{
				ClaimType:  "booking_detail",
				Pattern:    regexp.MustCompile(`(?i)(?:booking|appointment)\s+(?:for|on)\s+(?P<date>[A-Za-z]+\s+\d{1,2}(?:,?\s+\d{4})?)`),
				Fields:     []string{"date"},
				Confidence: 0.8,
			},
			{
				ClaimType:  "payment_amount",
				Pattern:    regexp.MustCompile(`(?i)(?:amount|total|paid)[:\s]+\$?(?P<amount>[\d,]+\.?\d{0,2})`),
				Fields:     []string{"amount"},
				Confidence: 0.8,
			},
			{
				ClaimType:  "repeat_client",
				Pattern:    regexp.MustCompile(`(?i)(?P<count>\d+)(?:st|nd|rd|th)?\s+(?:booking|visit|session)`),
				Fields:     []string{"count"},
				Confidence: 0.7,
			},
		},
	},
	{
		Platform:    "sittercity.com",
		FromDomains: []string{"sittercity.com", "mail.sittercity.com"},
		Subject: []patternRule{
			{
				ClaimType:  "booking_confirmation",
				Pattern:    regexp.MustCompile(`(?i)(booking|job)\s+(confirmed|accepted|assigned)`),
				Confidence: 0.9,
			},
			{
				ClaimType:  "review_notification",
				Pattern:    regexp.MustCompile(`(?i)(new\s+review|feedback|rating)`),
				Confidence: 0.85,
			},
		},
		Body: []patternRule{
			{
				ClaimType:  "booking_detail",
				Pattern:    regexp.MustCompile(`(?i)(?:scheduled|booked)\s+(?:for|on)\s+(?P<date>[A-Za-z]+\s+\d{1,2}(?:,?\s+\d{4})?)`),
				Fields:     []string{"date"},
				Confidence: 0.8,
			},
		},
	},
	{
		Platform:    "urbansitter.com",
		FromDomains: []string{"urbansitter.com", "mail.urbansitter.com"},
		Subject: []patternRule{
			{
				ClaimType:  "booking_confirmation",
				Pattern:    regexp.MustCompile(`(?i)(booking|job|sit)\s+(confirmed|request)`),
				Confidence: 0.9,
			},
		},
		Body: []patternRule{},
	},
	{
		Platform:    "vinted",
		FromDomains: []string{"vinted.es", "vinted.com", "vinted.fr", "vinted.de", "vinted.nl", "vinted.be", "vinted.it", "vinted.pt", "vinted.pl", "vinted.lt", "vinted.co.uk"},
		Subject: []patternRule{
			{
				ClaimType:  "sale_notification",
				Pattern:    regexp.MustCompile(`(?i)(s'est vendu|has been sold|wurde verkauft|est[áa] vendido|venduto)`),
				Confidence: 0.95,
			},
			{
				ClaimType:  "purchase_notification",
				Pattern:    regexp.MustCompile(`(?i)(a achet[ée]|has bought|hat gekauft|ha comprado|ha acquistato)`),
				Confidence: 0.95,
			},
			{
				ClaimType:  "shipping_notification",
				Pattern:    regexp.MustCompile(`(?i)(colis|parcel|paket|paquete|pacco)\s+(envoy[ée]|shipped|versendet|enviado|spedito)`),
				Confidence: 0.9,
			},
		},
		Body: []patternRule{
			{
				ClaimType:  "buyer_identity",
				Pattern:    regexp.MustCompile(`(?i)\*?(?P<buyer>\w+)\*?\s+(?:a\s+achet[ée]|has\s+bought|hat\s+gekauft)`),
				Fields:     []string{"buyer"},
				Confidence: 0.85,
			},
			{
				ClaimType:  "sale_amount",
				Pattern:    regexp.MustCompile(`(?P<amount>[\d]+[.,]\d{2})\s*[€£]`),
				Fields:     []string{"amount"},
				Confidence: 0.9,
			},
			{
				ClaimType:  "item_name",
				Pattern:    regexp.MustCompile(`(?i)(?:a\s+achet[ée]|has\s+bought|hat\s+gekauft)\s+(?P<item>.+?)\s+\d+[.,]\d{2}\s*[€£]`),
				Fields:     []string{"item"},
				Confidence: 0.8,
			},
		},
	},
	{
		Platform:    "homeexchange.com",
		FromDomains: []string{"homeexchange.com", "info.homeexchange.com", "bounces.homeexchange.com"},
		Subject: []patternRule{
			{
				ClaimType:  "exchange_confirmation",
				Pattern:    regexp.MustCompile(`(?i)(confirmed|confirmation)\s+.*\bexchange\b|exchange\b.*\b(confirmed|confirmation)`),
				Confidence: 0.9,
			},
			{
				ClaimType:  "exchange_confirmation",
				Pattern:    regexp.MustCompile(`(?i)you have confirmed your exchange`),
				Confidence: 0.95,
			},
			{
				ClaimType:  "review_notification",
				Pattern:    regexp.MustCompile(`(?i)(review|feedback|rating)\s+(from|received|left)`),
				Confidence: 0.85,
			},
		},
		Body: []patternRule{
			{
				ClaimType:  "stay_dates",
				Pattern:    regexp.MustCompile(`(?i)(?:from|dates?)[:\s]+(?P<checkin>[A-Za-z]+,?\s+[A-Za-z]+\s+\d{1,2},?\s+\d{4})\s+to\s+(?P<checkout>[A-Za-z]+,?\s+[A-Za-z]+\s+\d{1,2},?\s+\d{4})`),
				Fields:     []string{"checkin", "checkout"},
				Confidence: 0.9,
			},
			{
				ClaimType:  "guest_count",
				Pattern:    regexp.MustCompile(`(?i)(?:number of guests|guests?)[:\s]+(?P<count>\d+)`),
				Fields:     []string{"count"},
				Confidence: 0.85,
			},
			{
				ClaimType:  "guestpoints_transfer",
				Pattern:    regexp.MustCompile(`(?i)(?P<points>\d+)\s*(?:GP|GuestPoints?)\s+(?:have\s+been\s+)?transferred`),
				Fields:     []string{"points"},
				Confidence: 0.9,
			},
			{
				ClaimType:  "host_identity",
				Pattern:    regexp.MustCompile(`(?i)(?:exchange|stay)\s+(?:with|at)\s+(?P<host>[A-Z][a-z]+)(?:[''\x{2019}]s)?`),
				Fields:     []string{"host"},
				Confidence: 0.7,
			},
			{
				ClaimType:  "guarantee_coverage",
				Pattern:    regexp.MustCompile(`(?i)(?:covered|protected)\s+by\s+(?:our\s+)?(?P<guarantee>guarantees?|(?:cancellation|non-conformity)\s+(?:protection|guarantee))`),
				Fields:     []string{"guarantee"},
				Confidence: 0.8,
			},
		},
	},
}

// Generic patterns that apply to any platform
var genericSubjectPatterns = []patternRule{
	{
		ClaimType:  "booking_confirmation",
		Pattern:    regexp.MustCompile(`(?i)(booking|reservation|exchange|stay)\s+(confirmed|confirmation)`),
		Confidence: 0.6,
	},
	{
		ClaimType:  "booking_confirmation",
		Pattern:    regexp.MustCompile(`(?i)you have confirmed`),
		Confidence: 0.6,
	},
	{
		ClaimType:  "payment_receipt",
		Pattern:    regexp.MustCompile(`(?i)(payment|receipt|invoice)\s+(receipt|received|confirmation|#\d+)`),
		Confidence: 0.6,
	},
	{
		ClaimType:  "account_activity",
		Pattern:    regexp.MustCompile(`(?i)(welcome|account\s+created|profile\s+updated)`),
		Confidence: 0.5,
	},
}

var genericBodyPatterns = []patternRule{
	{
		ClaimType:  "payment_amount",
		Pattern:    regexp.MustCompile(`(?i)(?:amount|total|paid|charged)[:\s]+[\$€£]?(?P<amount>[\d,]+\.?\d{0,2})`),
		Fields:     []string{"amount"},
		Confidence: 0.5,
	},
	{
		ClaimType:  "date_reference",
		Pattern:    regexp.MustCompile(`(?i)(?:on|for|date)[:\s]+(?P<date>\d{1,2}[/\-]\d{1,2}[/\-]\d{2,4})`),
		Fields:     []string{"date"},
		Confidence: 0.4,
	},
}

// Forward detection patterns.
// Subject prefixes used by mail clients worldwide to indicate a forwarded message.
var forwardSubjectPrefixes = []string{
	"fwd:", // English (Gmail, Outlook, Apple Mail)
	"fw:",  // Outlook
	"tr:",  // French (transféré)
	"wg:",  // German (weitergeleitet)
	"rv:",  // Spanish (reenviado)
	"vs:",  // Italian (verstuurd) / Dutch
	"vl:",  // Finnish (välitetty)
	"enc:", // Portuguese (encaminhado)
}

// Body markers indicating the content below is a forwarded message.
var forwardBodyMarkers = []*regexp.Regexp{
	regexp.MustCompile(`(?i)-{3,}\s*forwarded\s+message\s*-{3,}`),
	regexp.MustCompile(`(?i)d[ée]but\s+du\s+message\s+transf[ée]r[ée]`),
	regexp.MustCompile(`(?i)-{3,}\s*original\s+message\s*-{3,}`),
}

// isForwarded detects whether an email is a forward rather than a direct platform email.
func isForwarded(subject, body string) bool {
	lower := strings.ToLower(strings.TrimSpace(subject))
	for _, prefix := range forwardSubjectPrefixes {
		if strings.HasPrefix(lower, prefix) {
			return true
		}
	}

	for _, re := range forwardBodyMarkers {
		if re.MatchString(body) {
			return true
		}
	}

	return false
}

// detectPlatform identifies a known platform from the sending domain.
func detectPlatform(fromDomain string) string {
	fromDomain = strings.ToLower(fromDomain)
	for _, p := range knownPlatforms {
		for _, d := range p.FromDomains {
			if fromDomain == d || strings.HasSuffix(fromDomain, "."+d) {
				return p.Platform
			}
		}
	}
	return "" // unknown platform
}

// extractFromSubject applies subject-line patterns and returns claims.
func extractFromSubject(subject, platform string) []Claim {
	var claims []Claim

	// Try platform-specific patterns first
	if platform != "" {
		for _, p := range knownPlatforms {
			if p.Platform == platform {
				for _, rule := range p.Subject {
					if rule.Pattern.MatchString(subject) {
						claims = append(claims, Claim{
							Type:       rule.ClaimType,
							Confidence: rule.Confidence,
							Fields:     map[string]string{"matched": rule.Pattern.FindString(subject)},
							Source:     "subject",
						})
					}
				}
				break
			}
		}
	}

	// Fall back to generic patterns if no platform-specific match
	if len(claims) == 0 {
		for _, rule := range genericSubjectPatterns {
			if rule.Pattern.MatchString(subject) {
				claims = append(claims, Claim{
					Type:       rule.ClaimType,
					Confidence: rule.Confidence,
					Fields:     map[string]string{"matched": rule.Pattern.FindString(subject)},
					Source:     "subject",
				})
			}
		}
	}

	return claims
}

// extractFromBody applies body content patterns and returns claims.
func extractFromBody(body, source, platform string) []Claim {
	var claims []Claim

	// Try platform-specific patterns
	if platform != "" {
		for _, p := range knownPlatforms {
			if p.Platform == platform {
				claims = append(claims, applyBodyRules(body, source, p.Body)...)
				break
			}
		}
	}

	// Always try generic patterns too
	claims = append(claims, applyBodyRules(body, source, genericBodyPatterns)...)

	return claims
}

// applyBodyRules applies a set of pattern rules to body text.
func applyBodyRules(body, source string, rules []patternRule) []Claim {
	var claims []Claim
	for _, rule := range rules {
		match := rule.Pattern.FindStringSubmatch(body)
		if match == nil {
			continue
		}

		fields := make(map[string]string)
		for i, name := range rule.Pattern.SubexpNames() {
			if name != "" && i < len(match) && match[i] != "" {
				fields[name] = match[i]
			}
		}

		claims = append(claims, Claim{
			Type:       rule.ClaimType,
			Confidence: rule.Confidence,
			Fields:     fields,
			Source:     source,
		})
	}
	return claims
}
