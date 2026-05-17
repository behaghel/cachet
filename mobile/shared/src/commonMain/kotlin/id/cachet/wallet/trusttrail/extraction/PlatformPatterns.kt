package id.cachet.wallet.trusttrail.extraction

/**
 * Pattern rule for extracting a claim from email content.
 * Ported from tools/dkim-explorer/internal/claims/patterns.go
 */
internal data class PatternRule(
    val claimType: String,
    val pattern: Regex,
    val fields: List<String> = emptyList(),
    val confidence: Double,
)

/**
 * Extraction rules for a known platform.
 *
 * @property subjectKeywords Keywords to filter emails server-side (Gmail `subject:` query).
 *   Empty list means fetch all emails from this domain (no subject filter).
 */
internal data class PlatformPattern(
    val platform: String,
    val fromDomains: List<String>,
    val subjectKeywords: List<String> = emptyList(),
    val subjectRules: List<PatternRule>,
    val bodyRules: List<PatternRule>,
)

/**
 * Known platforms with extraction patterns.
 * Data-driven: adding a new platform means adding an entry here.
 */
internal val knownPlatforms: List<PlatformPattern> = listOf(
    PlatformPattern(
        platform = "care.com",
        fromDomains = listOf("care.com", "mail.care.com"),
        subjectKeywords = listOf("confirmed", "confirmation", "receipt", "payment", "review"),
        subjectRules = listOf(
            PatternRule(
                claimType = "booking_confirmation",
                pattern = Regex("""(?i)booking\s+(confirmed|confirmation)"""),
                confidence = 0.9,
            ),
            PatternRule(
                claimType = "payment_receipt",
                pattern = Regex("""(?i)payment\s+(receipt|received|confirmation)"""),
                confidence = 0.9,
            ),
            PatternRule(
                claimType = "review_notification",
                pattern = Regex("""(?i)(new\s+review|review\s+(from|received))"""),
                confidence = 0.85,
            ),
        ),
        bodyRules = listOf(
            PatternRule(
                claimType = "booking_detail",
                pattern = Regex("""(?i)(?:booking|appointment)\s+(?:for|on)\s+(?<date>[A-Za-z]+\s+\d{1,2}(?:,?\s+\d{4})?)"""),
                fields = listOf("date"),
                confidence = 0.8,
            ),
            PatternRule(
                claimType = "payment_amount",
                pattern = Regex("""(?i)(?:amount|total|paid)[:\s]+\$?(?<amount>[\d,]+\.?\d{0,2})"""),
                fields = listOf("amount"),
                confidence = 0.8,
            ),
        ),
    ),
    PlatformPattern(
        platform = "sittercity.com",
        fromDomains = listOf("sittercity.com", "mail.sittercity.com"),
        subjectKeywords = listOf("confirmed", "accepted", "assigned", "review", "rating"),
        subjectRules = listOf(
            PatternRule(
                claimType = "booking_confirmation",
                pattern = Regex("""(?i)(booking|job)\s+(confirmed|accepted|assigned)"""),
                confidence = 0.9,
            ),
            PatternRule(
                claimType = "review_notification",
                pattern = Regex("""(?i)(new\s+review|feedback|rating)"""),
                confidence = 0.85,
            ),
        ),
        bodyRules = listOf(
            PatternRule(
                claimType = "booking_detail",
                pattern = Regex("""(?i)(?:scheduled|booked)\s+(?:for|on)\s+(?<date>[A-Za-z]+\s+\d{1,2}(?:,?\s+\d{4})?)"""),
                fields = listOf("date"),
                confidence = 0.8,
            ),
        ),
    ),
    PlatformPattern(
        platform = "urbansitter.com",
        fromDomains = listOf("urbansitter.com", "mail.urbansitter.com"),
        subjectKeywords = listOf("confirmed", "request"),
        subjectRules = listOf(
            PatternRule(
                claimType = "booking_confirmation",
                pattern = Regex("""(?i)(booking|job|sit)\s+(confirmed|request)"""),
                confidence = 0.9,
            ),
        ),
        bodyRules = emptyList(),
    ),
    PlatformPattern(
        platform = "vinted",
        fromDomains = listOf(
            "vinted.es", "vinted.com", "vinted.fr", "vinted.de", "vinted.nl",
            "vinted.be", "vinted.it", "vinted.pt", "vinted.pl", "vinted.lt",
            "vinted.co.uk",
        ),
        subjectKeywords = listOf("vendu", "sold", "verkauft", "vendido", "venduto",
            "acheté", "bought", "gekauft", "comprado", "acquistato",
            "colis", "parcel", "paket", "paquete", "pacco"),
        subjectRules = listOf(
            PatternRule(
                claimType = "sale_notification",
                pattern = Regex("""(?i)(s'est vendu|has been sold|wurde verkauft|est[áa] vendido|venduto)"""),
                confidence = 0.95,
            ),
            PatternRule(
                claimType = "purchase_notification",
                pattern = Regex("""(?i)(a achet[ée]|has bought|hat gekauft|ha comprado|ha acquistato)"""),
                confidence = 0.95,
            ),
            PatternRule(
                claimType = "shipping_notification",
                pattern = Regex("""(?i)(colis|parcel|paket|paquete|pacco)\s+(envoy[ée]|shipped|versendet|enviado|spedito)"""),
                confidence = 0.9,
            ),
        ),
        bodyRules = listOf(
            PatternRule(
                claimType = "buyer_identity",
                pattern = Regex("""(?i)\*?(?<buyer>\w+)\*?\s+(?:a\s+achet[ée]|has\s+bought|hat\s+gekauft)"""),
                fields = listOf("buyer"),
                confidence = 0.85,
            ),
            PatternRule(
                claimType = "sale_amount",
                pattern = Regex("""(?<amount>[\d]+[.,]\d{2})\s*[€£]"""),
                fields = listOf("amount"),
                confidence = 0.9,
            ),
            PatternRule(
                claimType = "item_name",
                pattern = Regex("""(?i)(?:a\s+achet[ée]|has\s+bought|hat\s+gekauft)\s+(?<item>.+?)\s+\d+[.,]\d{2}\s*[€£]"""),
                fields = listOf("item"),
                confidence = 0.8,
            ),
        ),
    ),
    PlatformPattern(
        platform = "homeexchange.com",
        fromDomains = listOf("homeexchange.com", "info.homeexchange.com", "bounces.homeexchange.com"),
        // Only transactional: confirmed exchanges, completed stays, reviews
        subjectKeywords = listOf("confirmed", "confirmation", "review", "feedback",
            "rating", "GuestPoints", "completed"),
        subjectRules = listOf(
            PatternRule(
                claimType = "exchange_confirmation",
                pattern = Regex("""(?i)(confirmed|confirmation)\s+.*\bexchange\b|exchange\b.*\b(confirmed|confirmation)"""),
                confidence = 0.9,
            ),
            PatternRule(
                claimType = "exchange_confirmation",
                pattern = Regex("""(?i)you have confirmed your exchange"""),
                confidence = 0.95,
            ),
            PatternRule(
                claimType = "review_notification",
                pattern = Regex("""(?i)(review|feedback|rating)\s+(from|received|left)"""),
                confidence = 0.85,
            ),
        ),
        bodyRules = listOf(
            // Only claims that prove a completed transaction
            PatternRule(
                claimType = "stay_dates",
                pattern = Regex("""(?i)(?:from|dates?)[:\s]+(?<checkin>[A-Za-z]+,?\s+[A-Za-z]+\s+\d{1,2},?\s+\d{4})\s+to\s+(?<checkout>[A-Za-z]+,?\s+[A-Za-z]+\s+\d{1,2},?\s+\d{4})"""),
                fields = listOf("checkin", "checkout"),
                confidence = 0.9,
            ),
            PatternRule(
                claimType = "guest_count",
                pattern = Regex("""(?i)(?:number of guests|guests?)[:\s]+(?<count>\d+)"""),
                fields = listOf("count"),
                confidence = 0.85,
            ),
            PatternRule(
                claimType = "guestpoints_transfer",
                pattern = Regex("""(?i)(?<points>\d+)\s*(?:GP|GuestPoints?)\s+(?:have\s+been\s+)?transferred"""),
                fields = listOf("points"),
                confidence = 0.9,
            ),
        ),
    ),
)

/** Generic subject patterns — lower confidence, any platform. */
internal val genericSubjectPatterns: List<PatternRule> = listOf(
    PatternRule(
        claimType = "booking_confirmation",
        pattern = Regex("""(?i)(booking|reservation|exchange|stay)\s+(confirmed|confirmation)"""),
        confidence = 0.6,
    ),
    PatternRule(
        claimType = "booking_confirmation",
        pattern = Regex("""(?i)you have confirmed"""),
        confidence = 0.6,
    ),
    PatternRule(
        claimType = "payment_receipt",
        pattern = Regex("""(?i)(payment|receipt|invoice)\s+(receipt|received|confirmation|#\d+)"""),
        confidence = 0.6,
    ),
    PatternRule(
        claimType = "account_activity",
        pattern = Regex("""(?i)(welcome|account\s+created|profile\s+updated)"""),
        confidence = 0.5,
    ),
)

/** Generic body patterns — lower confidence, any platform. */
internal val genericBodyPatterns: List<PatternRule> = listOf(
    PatternRule(
        claimType = "payment_amount",
        pattern = Regex("""(?i)(?:amount|total|paid|charged)[:\s]+[$€£]?(?<amount>[\d,]+\.?\d{0,2})"""),
        fields = listOf("amount"),
        confidence = 0.5,
    ),
    PatternRule(
        claimType = "date_reference",
        pattern = Regex("""(?i)(?:on|for|date)[:\s]+(?<date>\d{1,2}[/\-]\d{1,2}[/\-]\d{2,4})"""),
        fields = listOf("date"),
        confidence = 0.4,
    ),
)

/** Subject prefixes indicating a forwarded message (8 languages). */
internal val forwardSubjectPrefixes: List<String> = listOf(
    "fwd:", // English (Gmail, Outlook, Apple Mail)
    "fw:",  // Outlook
    "tr:",  // French (transféré)
    "wg:",  // German (weitergeleitet)
    "rv:",  // Spanish (reenviado)
    "vs:",  // Italian / Dutch (verstuurd)
    "vl:",  // Finnish (välitetty)
    "enc:", // Portuguese (encaminhado)
)

/** Body markers indicating forwarded content. */
internal val forwardBodyMarkers: List<Regex> = listOf(
    Regex("""(?i)-{3,}\s*forwarded\s+message\s*-{3,}"""),
    Regex("""(?i)d[ée]but\s+du\s+message\s+transf[ée]r[ée]"""),
    Regex("""(?i)-{3,}\s*original\s+message\s*-{3,}"""),
)
