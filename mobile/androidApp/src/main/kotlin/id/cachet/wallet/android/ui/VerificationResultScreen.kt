package id.cachet.wallet.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import id.cachet.wallet.domain.usecase.VerificationResult
import id.cachet.wallet.network.PredicateResultDTO

@Composable
fun VerificationResultScreen(
    result: VerificationResult,
    onBack: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Badge section
        item {
            BadgeSection(result)
        }

        // Summary section
        item {
            SummarySection(result)
        }

        // Per-predicate results
        item {
            Text(
                text = "Predicate Results",
                style = MaterialTheme.typography.titleMedium
            )
        }

        items(result.predicateResults) { predResult ->
            PredicateResultCard(predResult)
        }

        // Consent receipt
        if (result.consentReceiptId != null) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Consent Receipt",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Receipt ID: ${result.consentReceiptId}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Hash submitted to transparency log",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Back to credentials")
            }
        }
    }
}

@Composable
fun BadgeSection(result: VerificationResult) {
    val badgeGranted = result.summary?.badgeGranted ?: false
    val badgeColor = if (badgeGranted) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = badgeColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (badgeGranted && result.badge.isNotEmpty()) {
                Text(
                    text = result.badge,
                    style = MaterialTheme.typography.headlineSmall
                )
            } else {
                Text(
                    text = "Badge Not Granted",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Not all required predicates could be verified",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Freshness: ${result.freshness}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun SummarySection(result: VerificationResult) {
    val summary = result.summary ?: return

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${summary.requiredSatisfied}/${summary.requiredTotal} required predicates verified",
                style = MaterialTheme.typography.titleSmall
            )
            val optSatisfied = summary.optionalSatisfied ?: 0
            val optTotal = summary.optionalTotal ?: 0
            if (optTotal > 0) {
                Text(
                    text = "$optSatisfied/$optTotal optional predicates verified",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PredicateResultCard(predResult: PredicateResultDTO) {
    val (statusColor, statusLabel) = when (predResult.status) {
        "satisfied" -> Pair(Color(0xFF4CAF50), "Satisfied")
        "failed" -> Pair(Color(0xFFF44336), "Failed")
        "not_evaluable" -> Pair(Color(0xFFFFC107), "Not Evaluable")
        "no_credential" -> Pair(Color(0xFF9E9E9E), "No Credential")
        else -> Pair(Color.Gray, predResult.status)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = statusColor,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.size(12.dp)
            ) {}

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = predResult.predicateId,
                    style = MaterialTheme.typography.bodyMedium
                )
                val reasonText = predResult.reason
                if (reasonText != null) {
                    Text(
                        text = reasonText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelMedium,
                color = statusColor
            )
        }
    }
}
