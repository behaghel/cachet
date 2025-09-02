package id.cachet.wallet.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.cachet.wallet.domain.model.StoredCredential
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
                    else 
                        MaterialTheme.colorScheme.primary
                )
                
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = getCredentialDisplayName(credential.credential.type),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
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
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Show credential subject claims
            credential.credential.credentialSubject.forEach { (key, value) ->
                if (key != "id") { // Skip the ID field for display
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatClaimKey(key),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = value.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
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