package id.cachet.wallet.trusttrail.extraction

import id.cachet.wallet.trusttrail.model.Claim
import id.cachet.wallet.trusttrail.model.EmailEvidence
import kotlinx.datetime.Instant

/**
 * Extracts structured claims from email content.
 * Ported from tools/dkim-explorer/internal/claims/extract.go
 *
 * Pure functions, no side effects, no I/O — all on-device processing.
 */
object ClaimExtractor {

    private const val DEFAULT_CONFIDENCE_THRESHOLD = 0.7

    /**
     * Analyze email content and extract structured claims.
     *
     * Pipeline: detect platform → reject forwards → extract subject claims →
     * extract body claims.
     */
    fun extract(
        from: String,
        subject: String,
        textBody: String,
        htmlBody: String,
        date: Instant,
    ): EmailEvidence {
        val fromDomain = extractDomain(from)
        val platform = detectPlatform(fromDomain) ?: ""

        // Determine body text for forward detection and extraction
        val body = textBody.ifEmpty { stripHTML(htmlBody) }

        // Reject forwarded emails — they break the DKIM chain
        if (isForwarded(subject, body)) {
            return EmailEvidence(
                platform = platform,
                fromDomain = fromDomain,
                subject = subject,
                receivedDate = date,
                claims = emptyList(),
                rejected = true,
                rejectionReason = "forwarded_email",
            )
        }

        val claims = mutableListOf<Claim>()

        // Subject-based extraction
        claims.addAll(extractFromSubject(subject, platform))

        // Body-based extraction (prefer text, fall back to HTML)
        val bodyForClaims = textBody.ifEmpty { stripHTML(htmlBody) }
        val source = if (textBody.isNotEmpty()) "body_text" else "body_html"
        if (bodyForClaims.isNotEmpty()) {
            claims.addAll(extractFromBody(bodyForClaims, source, platform))
        }

        return EmailEvidence(
            platform = platform,
            fromDomain = fromDomain,
            subject = subject,
            receivedDate = date,
            claims = claims,
        )
    }

    /**
     * All known From domains across all platforms.
     * Used to build server-side queries (e.g., Gmail `from:` filter).
     */
    val allKnownDomains: List<String>
        get() = knownPlatforms.flatMap { it.fromDomains }

    /**
     * Build a Gmail-compatible query filter for known platform emails.
     * Combines from: domain filters with subject: keyword filters.
     * Returns a query string like: "(from:vinted.es subject:(vendu OR sold)) OR (from:care.com subject:(confirmed OR receipt))"
     */
    fun buildPlatformQuery(): String {
        return knownPlatforms.joinToString(" OR ") { platform ->
            val fromPart = platform.fromDomains.joinToString(" OR ") { "from:$it" }
            if (platform.subjectKeywords.isEmpty()) {
                "($fromPart)"
            } else {
                val subjectPart = platform.subjectKeywords.joinToString(" OR ")
                "($fromPart subject:($subjectPart))"
            }
        }
    }

    /**
     * Identify a known platform from the sending domain.
     * Returns null if the domain doesn't match any known platform.
     */
    fun detectPlatform(fromDomain: String): String? {
        val lower = fromDomain.lowercase()
        for (p in knownPlatforms) {
            for (d in p.fromDomains) {
                if (lower == d || lower.endsWith(".$d")) {
                    return p.platform
                }
            }
        }
        return null
    }

    /**
     * Extract the domain part from an email From header.
     * Handles both "user@domain.com" and "Name <user@domain.com>" formats.
     */
    fun extractDomain(from: String): String {
        val idx = from.lastIndexOf('@')
        if (idx < 0) return ""
        return from.substring(idx + 1)
            .trimEnd('>', ' ', '\t')
            .lowercase()
    }

    /**
     * Filter claims by minimum confidence threshold.
     */
    fun filterByConfidence(
        claims: List<Claim>,
        threshold: Double = DEFAULT_CONFIDENCE_THRESHOLD,
    ): List<Claim> = claims.filter { it.confidence >= threshold }

    // --- Internal helpers ---

    internal fun isForwarded(subject: String, body: String): Boolean {
        val lower = subject.trim().lowercase()
        for (prefix in forwardSubjectPrefixes) {
            if (lower.startsWith(prefix)) return true
        }
        for (marker in forwardBodyMarkers) {
            if (marker.containsMatchIn(body)) return true
        }
        return false
    }

    private fun extractFromSubject(subject: String, platform: String): List<Claim> {
        val claims = mutableListOf<Claim>()

        // Try platform-specific patterns first
        if (platform.isNotEmpty()) {
            val platformPattern = knownPlatforms.firstOrNull { it.platform == platform }
            if (platformPattern != null) {
                for (rule in platformPattern.subjectRules) {
                    val match = rule.pattern.find(subject)
                    if (match != null) {
                        claims.add(Claim(
                            type = rule.claimType,
                            confidence = rule.confidence,
                            fields = mapOf("matched" to match.value),
                            source = "subject",
                        ))
                    }
                }
            }
        }

        // Fall back to generic patterns if no platform-specific match
        if (claims.isEmpty()) {
            for (rule in genericSubjectPatterns) {
                val match = rule.pattern.find(subject)
                if (match != null) {
                    claims.add(Claim(
                        type = rule.claimType,
                        confidence = rule.confidence,
                        fields = mapOf("matched" to match.value),
                        source = "subject",
                    ))
                }
            }
        }

        return claims
    }

    private fun extractFromBody(body: String, source: String, platform: String): List<Claim> {
        val claims = mutableListOf<Claim>()

        // Try platform-specific patterns
        if (platform.isNotEmpty()) {
            val platformPattern = knownPlatforms.firstOrNull { it.platform == platform }
            if (platformPattern != null) {
                claims.addAll(applyBodyRules(body, source, platformPattern.bodyRules))
            }
        }

        // Always try generic patterns too
        claims.addAll(applyBodyRules(body, source, genericBodyPatterns))

        return claims
    }

    private fun applyBodyRules(body: String, source: String, rules: List<PatternRule>): List<Claim> {
        val claims = mutableListOf<Claim>()
        for (rule in rules) {
            val match = rule.pattern.find(body) ?: continue

            val fields = mutableMapOf<String, String>()
            for (fieldName in rule.fields) {
                val group = match.groups[fieldName]
                if (group != null && group.value.isNotEmpty()) {
                    fields[fieldName] = group.value
                }
            }

            claims.add(Claim(
                type = rule.claimType,
                confidence = rule.confidence,
                fields = fields,
                source = source,
            ))
        }
        return claims
    }

    private fun stripHTML(html: String): String {
        if (html.isEmpty()) return ""
        val tagRegex = Regex("<[^>]*>")
        val text = html.replace(tagRegex, " ")
        return text.replace(Regex("""\s+"""), " ").trim()
    }
}
