package id.cachet.wallet.android.trusttrail.ui

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.cachet.wallet.android.trusttrail.auth.GmailSignIn
import id.cachet.wallet.android.trusttrail.provider.GmailEmailProvider
import id.cachet.wallet.trusttrail.model.DiscoveredPlatform
import id.cachet.wallet.trusttrail.model.EmailEvidence
import id.cachet.wallet.trusttrail.usecase.InboxScannerUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Self-contained TrustTrail tab that manages the full Gmail flow:
 * Sign-in → headers scan → platform discovery → consent → extraction.
 */
@Composable
fun TrustTrailTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // State
    var isSignedIn by remember { mutableStateOf(GmailSignIn.getLastSignedInAccount(context) != null) }
    var isScanning by remember { mutableStateOf(false) }
    var isExtracting by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var discoveredPlatforms by remember { mutableStateOf<List<DiscoveredPlatform>>(emptyList()) }
    var extractedEvidence by remember { mutableStateOf<List<EmailEvidence>>(emptyList()) }
    var showExtraction by remember { mutableStateOf(false) }

    // Keep screen on while scanning or extracting — prevents Android from killing the network
    val isBusy = isScanning || isExtracting
    val activity = context as? Activity
    DisposableEffect(isBusy) {
        if (isBusy) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Google Sign-In launcher
    val signInClient = remember { GmailSignIn.buildClient(context) }
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val account = GmailSignIn.handleSignInResult(result.data)
            if (account != null) {
                isSignedIn = true
                scanError = null
                // Auto-start scanning after sign-in
                scope.launch {
                    scanInbox(context, discoveredPlatformsCallback = { platforms ->
                        discoveredPlatforms = platforms
                        isScanning = false
                    }, onError = { error ->
                        scanError = error
                        isScanning = false
                    }, onStart = { isScanning = true })
                }
            } else {
                scanError = "Sign-in failed — please try again"
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        // Header
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
                Icon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("TrustTrail", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }

        if (!isSignedIn) {
            // --- Not connected ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Nothing leaves your device", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Emails are scanned on your phone. Only claims you approve are shared.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { signInLauncher.launch(GmailSignIn.getSignInIntent(signInClient)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Email, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connect Gmail")
                        }
                    }
                }
            }
        } else if (isScanning || isExtracting) {
            // --- In progress ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        if (isExtracting) {
                            Text("Extracting claims...", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Reading email content and extracting evidence on-device",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text("Scanning inbox headers...", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Only From, Subject, Date — no email bodies read yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        } else if (showExtraction && extractedEvidence.isNotEmpty()) {
            // --- Extraction results ---
            item {
                Text("Extracted Evidence", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
            }
            items(extractedEvidence) { evidence ->
                EvidenceCard(evidence)
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { showExtraction = false }) { Text("Back to platforms") }
            }
        } else if (discoveredPlatforms.isNotEmpty()) {
            // --- Discovered platforms ---
            item {
                Text("Discovered Platforms", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Found in your inbox (last 6 months)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            items(discoveredPlatforms) { platform ->
                PlatformCard(platform, onProcess = {
                    scope.launch {
                        isExtracting = true
                        extractFromPlatform(context, platform) { evidence ->
                            extractedEvidence = evidence
                            showExtraction = true
                            isExtracting = false
                        }
                    }
                })
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = {
                    scope.launch {
                        GmailSignIn.signOut(signInClient)
                        isSignedIn = false
                        discoveredPlatforms = emptyList()
                        extractedEvidence = emptyList()
                    }
                }) { Text("Disconnect Gmail") }
            }
        } else {
            // --- Connected but no platforms found ---
            item {
                Text("No known platforms found in your inbox.", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    scope.launch {
                        scanInbox(context,
                            discoveredPlatformsCallback = { discoveredPlatforms = it; isScanning = false },
                            onError = { scanError = it; isScanning = false },
                            onStart = { isScanning = true })
                    }
                }) { Text("Scan again") }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = {
                    scope.launch {
                        GmailSignIn.signOut(signInClient)
                        isSignedIn = false
                    }
                }) { Text("Disconnect") }
            }
        }

        // Error display
        if (scanError != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(scanError!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlatformCard(platform: DiscoveredPlatform, onProcess: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(platform.platform, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${platform.emailCount} email${if (platform.emailCount != 1) "s" else ""} found",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onProcess, contentPadding = PaddingValues(horizontal = 16.dp)) {
                Text("Process")
            }
        }
    }
}

@Composable
private fun EvidenceCard(evidence: EmailEvidence) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                evidence.platform.ifEmpty { "Unknown" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(evidence.subject, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)

            if (evidence.rejected) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Rejected: ${evidence.rejectionReason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            } else if (evidence.claims.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                evidence.claims.forEach { claim ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(claim.type.replace('_', ' '), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text("${(claim.confidence * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium,
                            color = if (claim.confidence >= 0.7) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    claim.fields.filterKeys { it != "matched" }.forEach { (key, value) ->
                        Text("  $key: $value", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            } else {
                Text("No claims extracted", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// --- Gmail scanning logic ---

private suspend fun httpGet(url: String, headers: Map<String, String>): String =
    withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            if (connection.responseCode != 200) {
                val error = connection.errorStream?.bufferedReader()?.readText() ?: "HTTP ${connection.responseCode}"
                throw Exception("Gmail API error ${connection.responseCode}: $error")
            }
            connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }

private suspend fun scanInbox(
    context: android.content.Context,
    discoveredPlatformsCallback: (List<DiscoveredPlatform>) -> Unit,
    onError: (String) -> Unit,
    onStart: () -> Unit,
) {
    onStart()
    try {
        val account = GmailSignIn.getLastSignedInAccount(context) ?: run {
            onError("Not signed in"); return
        }
        val token = GmailSignIn.getAccessToken(context, account) ?: run {
            onError("Failed to get access token"); return
        }

        val provider = GmailEmailProvider(
            tokenProvider = { token },
            httpGet = ::httpGet,
        )

        val useCase = InboxScannerUseCase(provider)
        val platforms = useCase.discoverPlatforms()
        discoveredPlatformsCallback(platforms)
    } catch (e: Exception) {
        onError("Scan failed: ${e.message}")
    }
}

private suspend fun extractFromPlatform(
    context: android.content.Context,
    platform: DiscoveredPlatform,
    callback: (List<EmailEvidence>) -> Unit,
) {
    try {
        val account = GmailSignIn.getLastSignedInAccount(context) ?: return
        val token = GmailSignIn.getAccessToken(context, account) ?: return

        val provider = GmailEmailProvider(
            tokenProvider = { token },
            httpGet = ::httpGet,
        )

        val useCase = InboxScannerUseCase(provider)
        val evidence = useCase.extractClaims(listOf(platform))
        callback(evidence)
    } catch (e: Exception) {
        callback(emptyList())
    }
}
