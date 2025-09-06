package id.cachet.wallet.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.cachet.wallet.domain.model.StoredCredential
import id.cachet.wallet.domain.model.extractQuality
import id.cachet.wallet.domain.model.getQualityBadge
import id.cachet.wallet.domain.model.meetsQualityThreshold
import id.cachet.wallet.domain.model.QualityIndicator
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialsScreen(
    credentials: List<StoredCredential>,
    onStartVerification: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "My Credentials",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
                
                FloatingActionButton(
                    onClick = onStartVerification,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Credential")
                }
            }
        }
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(credentials) { credential ->
                CredentialCard(credential = credential)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialCard(
    credential: StoredCredential
) {
    var isExpanded by remember { mutableStateOf(false) }
    val quality = remember { credential.credential.extractQuality() }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.VerifiedUser,
                    contentDescription = "Verified Credential",
                    tint = if (credential.isRevoked) 
                        MaterialTheme.colorScheme.error
                    else if (credential.credential.meetsQualityThreshold())
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.secondary
                )
                
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = getCredentialDisplayName(credential.credential.type),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    // Quality badge
                    Text(
                        text = credential.credential.getQualityBadge(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Text(
                        text = "Issued by: ${getIssuerDisplayName(credential.credential.issuer)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = "Issued: ${formatDate(credential.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    if (credential.isRevoked) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = "REVOKED",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    } else if (quality != null) {
                        // Trust score indicator
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    quality.trustScore >= 0.9 -> MaterialTheme.colorScheme.primaryContainer
                                    quality.trustScore >= 0.7 -> MaterialTheme.colorScheme.secondaryContainer
                                    else -> MaterialTheme.colorScheme.tertiaryContainer
                                }
                            )
                        ) {
                            Text(
                                text = "${(quality.trustScore * 100).toInt()}% Trust",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    
                    // Expand/Collapse button
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Show less" else "Show more"
                        )
                    }
                }
            }
            
            // Expandable quality indicators and details
            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))
                
                // Quality indicators
                if (quality != null) {
                    Text(
                        text = "Quality Indicators",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        items(quality.getQualityIndicators()) { indicator ->
                            QualityIndicatorCard(indicator = indicator)
                        }
                    }
                    
                    // Quality summary
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = quality.getQualitySummary(),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                // Show credential subject claims
                Text(
                    text = "Credential Details",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                credential.credential.credentialSubject.forEach { (key, value) ->
                    if (key != "id" && key != "verificationMetrics" && key != "evidence") { 
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatClaimKey(key),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = formatClaimValue(value.toString()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualityIndicatorCard(
    indicator: QualityIndicator
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when {
                indicator.score >= 0.8 -> MaterialTheme.colorScheme.primaryContainer
                indicator.score >= 0.6 -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.tertiaryContainer
            }
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = indicator.icon,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = indicator.title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = indicator.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun getCredentialDisplayName(types: List<String>): String {
    return when {
        types.contains("IdentityCredential") -> "Identity Credential"
        types.contains("ProofOfAge") -> "Age Verification"
        else -> types.lastOrNull() ?: "Credential"
    }
}

private fun getIssuerDisplayName(issuer: String): String {
    return when {
        issuer.contains("cachet.id") -> "Cachet"
        else -> issuer.substringAfter("did:web:").substringBefore("#")
    }
}

private fun formatDate(instant: kotlinx.datetime.Instant): String {
    val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${localDateTime.date}"
}

private fun formatClaimKey(key: String): String {
    return key.replace("_", " ")
        .split(" ")
        .joinToString(" ") { it.capitalize() }
}

private fun formatClaimValue(value: String): String {
    return when {
        value.startsWith("{") && value.endsWith("}") -> {
            // Handle JSON objects - extract key info
            if (value.contains("age")) "Age verified"
            else if (value.contains("nationality")) "Document verified"  
            else "Verified"
        }
        value.equals("true", ignoreCase = true) -> "✓ Verified"
        value.equals("false", ignoreCase = true) -> "✗ Not verified"
        value.length > 50 -> value.take(47) + "..."
        else -> value
    }
}