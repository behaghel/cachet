package id.cachet.wallet.android.trusttrail.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.cachet.wallet.trusttrail.extraction.ClaimExtractor
import id.cachet.wallet.trusttrail.model.Claim
import id.cachet.wallet.trusttrail.model.EmailEvidence
import kotlinx.datetime.Instant

/**
 * Demo TrustTrail screen — shows claims extracted from hardcoded fixture emails.
 * Behind TRUSTTRAIL_ENABLED feature flag.
 */
@Composable
fun TrustTrailDemoScreen() {
    val evidenceList = remember { extractDemoEvidence() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "TrustTrail",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = "Demo: claims extracted from fixture emails",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(evidenceList) { evidence ->
            EvidenceCard(evidence)
        }
    }
}

@Composable
private fun EvidenceCard(evidence: EmailEvidence) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = evidence.platform.ifEmpty { "Unknown platform" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = evidence.subject,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )

            if (evidence.rejected) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Rejected: ${evidence.rejectionReason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (evidence.claims.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                evidence.claims.forEach { claim ->
                    ClaimRow(claim)
                }
            }
        }
    }
}

@Composable
private fun ClaimRow(claim: Claim) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = claim.type.replace('_', ' '),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${(claim.confidence * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = if (claim.confidence >= 0.7)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (claim.fields.isNotEmpty() && claim.fields.keys != setOf("matched")) {
        claim.fields.filterKeys { it != "matched" }.forEach { (key, value) ->
            Text(
                text = "  $key: $value",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** Hardcoded fixture emails for demo mode. */
private fun extractDemoEvidence(): List<EmailEvidence> {
    val demoDate = Instant.parse("2026-04-25T10:00:00Z")

    val fixtures = listOf(
        DemoEmail(
            from = "L'equipe Vinted <no-reply@vinted.es>",
            subject = "Ton article s'est vendu !",
            textBody = "",
            htmlBody = """<p><strong>sophieyann2006</strong> a acheté</p>
<div>Sac polochon personnalisé pour Sixtine</div>
<div>40,00 €</div>""",
        ),
        DemoEmail(
            from = "\"HomeExchange\" <notifications@info.homeexchange.com>",
            subject = "You have confirmed your exchange at Ana's home.",
            textBody = """Your exchange at Ana's is confirmed Hubert!

Great news, you've confirmed your GuestPoints exchange with Ana! 620 GP have
been transferred to their account. Here are the details of your stay:

Dates: from Wednesday, May 27, 2026 to Sunday, May 31, 2026
Number of guests: 2

This exchange is automatically covered by our guarantees (cancellation
protection, non-conformity guarantee).""",
            htmlBody = "",
        ),
        DemoEmail(
            from = "noreply@care.com",
            subject = "Booking Confirmed for Tuesday Jan 14",
            textBody = """Dear Alice,

Your booking for January 14, 2026 has been confirmed.
This is your 5th booking with this family.
Amount: ${'$'}150.00""",
            htmlBody = "",
        ),
        DemoEmail(
            from = "someone@hotmail.com",
            subject = "Fwd: Ton article s'est vendu !",
            textBody = "---------- Forwarded message ----------\nVinted sale details here.",
            htmlBody = "",
        ),
    )

    return fixtures.map { email ->
        ClaimExtractor.extract(
            from = email.from,
            subject = email.subject,
            textBody = email.textBody,
            htmlBody = email.htmlBody,
            date = demoDate,
        )
    }
}

private data class DemoEmail(
    val from: String,
    val subject: String,
    val textBody: String,
    val htmlBody: String,
)
