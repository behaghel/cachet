package id.cachet.wallet.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.cachet.wallet.domain.model.*
import id.cachet.wallet.domain.usecase.ConsentUseCase
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Phase 2B: Transparency Log Monitoring Interface
 * Shows transparency log status and allows users to verify receipt inclusion
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransparencyLogScreen(
    modifier: Modifier = Modifier,
    consentUseCase: ConsentUseCase = koinInject()
) {
    var auditReport by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var receipts by remember { mutableStateOf<List<ConsentReceipt>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    
    // Load consent receipts on first composition
    LaunchedEffect(Unit) {
        loadReceipts(consentUseCase) { loadedReceipts, loadError ->
            receipts = loadedReceipts
            error = loadError
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Transparency Log",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            IconButton(
                onClick = {
                    scope.launch {
                        performAudit(consentUseCase) { report, auditError ->
                            auditReport = report
                            error = auditError
                            isLoading = false
                        }
                    }
                    isLoading = true
                }
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Run Audit"
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Error display
        error?.let { errorMsg ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = errorMsg,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Audit report display
        auditReport?.let { report ->
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Audit Report",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = report,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Consent receipts with transparency log status
        Text(
            text = "Consent Receipts (${receipts.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(receipts) { receipt ->
                TransparencyLogReceiptCard(
                    receipt = receipt,
                    onVerifyClick = { 
                        scope.launch {
                            verifyReceipt(consentUseCase, receipt) { verified, verifyError ->
                                // Update the receipt verification status
                                if (verified) {
                                    // Receipt verified - could show a snackbar or update UI
                                } else {
                                    error = verifyError ?: "Verification failed"
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransparencyLogReceiptCard(
    receipt: ConsentReceipt,
    onVerifyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Receipt header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = receipt.purpose.take(50) + if (receipt.purpose.length > 50) "..." else "",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "To: ${receipt.rpDisplayName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Transparency log status indicator
                TransparencyLogStatusBadge(
                    logEntry = receipt.transparencyLogEntry
                )
            }
            
            // Transparency log details
            receipt.transparencyLogEntry?.let { logEntry ->
                Spacer(modifier = Modifier.height(12.dp))
                
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Log ID: ${logEntry.logId.take(12)}...",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (logEntry.anchoredAt != null) {
                                Text(
                                    text = "Anchored: ${logEntry.anchoredAt}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        Button(
                            onClick = onVerifyClick,
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Verify",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransparencyLogStatusBadge(
    logEntry: TransparencyLogEntry?,
    modifier: Modifier = Modifier
) {
    val (color, icon, text) = when {
        logEntry == null -> Triple(
            Color.Gray,
            Icons.Default.CloudOff,
            "Not Logged"
        )
        logEntry.isVerified -> Triple(
            Color(0xFF4CAF50),
            Icons.Default.Verified,
            "Verified"
        )
        logEntry.anchoredAt != null -> Triple(
            Color(0xFFFF9800),
            Icons.Default.Schedule,
            "Anchored"
        )
        else -> Triple(
            Color.Gray,
            Icons.Default.Schedule,
            "Pending"
        )
    }
    
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private suspend fun loadReceipts(
    consentUseCase: ConsentUseCase,
    onResult: (List<ConsentReceipt>, String?) -> Unit
) {
    consentUseCase.getConsentReceipts().fold(
        onSuccess = { receipts -> onResult(receipts, null) },
        onFailure = { error -> onResult(emptyList(), "Failed to load receipts: ${error.message}") }
    )
}

private suspend fun performAudit(
    consentUseCase: ConsentUseCase,
    onResult: (String?, String?) -> Unit
) {
    consentUseCase.performTransparencyLogAudit().fold(
        onSuccess = { report -> onResult(report, null) },
        onFailure = { error -> onResult(null, "Audit failed: ${error.message}") }
    )
}

private suspend fun verifyReceipt(
    consentUseCase: ConsentUseCase,
    receipt: ConsentReceipt,
    onResult: (Boolean, String?) -> Unit
) {
    consentUseCase.verifyTransparencyLogInclusion(receipt).fold(
        onSuccess = { verified -> onResult(verified, null) },
        onFailure = { error -> onResult(false, "Verification failed: ${error.message}") }
    )
}