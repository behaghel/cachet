package id.cachet.wallet.android.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import id.cachet.wallet.android.ui.components.CachetSegmentedControl
import id.cachet.wallet.android.ui.fixtures.DemoFixtures
import id.cachet.wallet.android.ui.mapper.CachPackMapper
import id.cachet.wallet.android.ui.model.*
import id.cachet.wallet.android.ui.theme.*
import kotlinx.serialization.json.*
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** Build a JSON payload for the QR code from a CachPackUi. */
private fun packToQrPayload(pack: CachPackUi): String {
    val obj = buildJsonObject {
        put("type", "cachet_verification_request")
        put("version", 1)
        put("question", pack.question)
        putJsonArray("predicates") {
            pack.description.split(", ").forEach { add(it) }
        }
    }
    return obj.toString()
}

/**
 * Overlay screens that sit on top of the main tab navigation.
 * null = no overlay, show normal tabs.
 */
sealed class OverlayScreen {
    data class QrShare(
        val question: String,
        val predicates: List<String>,
        val pack: CachPackUi
    ) : OverlayScreen()
    data class IncomingRequest(val request: VerificationRequest) : OverlayScreen()
    data class CachetResultOverlay(val result: CachetResult) : OverlayScreen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletApp(demoMode: Boolean = false) {
    val viewModel: WalletViewModel = koinViewModel { parametersOf(demoMode) }
    val uiState by viewModel.uiState.collectAsState()
    val activityState by viewModel.activityState.collectAsState()

    val scope = rememberCoroutineScope()
    var isOnboarded by remember { mutableStateOf(demoMode) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var overlay by remember { mutableStateOf<OverlayScreen?>(null) }

    // ── Onboarding gate ──
    if (!isOnboarded) {
        OnboardingScreen(onComplete = { isOnboarded = true })
        return
    }

    // ── Overlay screens (full-screen, above tabs) ──
    overlay?.let { screen ->
        when (screen) {
            is OverlayScreen.QrShare -> {
                // Auto-transition: simulate a scan after 4 seconds
                LaunchedEffect(screen) {
                    kotlinx.coroutines.delay(4000)
                    overlay = OverlayScreen.IncomingRequest(
                        CachPackMapper.toVerificationRequest(screen.pack)
                    )
                }
                QrShareScreen(
                    state = QrShareState(
                        question = screen.question,
                        predicates = screen.predicates,
                        qrPayload = packToQrPayload(screen.pack)
                    ),
                    onBack = { overlay = null },
                    onClose = { overlay = null },
                    onScanSimulated = {
                        overlay = OverlayScreen.IncomingRequest(
                            CachPackMapper.toVerificationRequest(screen.pack)
                        )
                    }
                )
            }
            is OverlayScreen.IncomingRequest -> IncomingRequestScreen(
                request = screen.request,
                onShare = {
                    scope.launch {
                        val result = viewModel.shareCredential(screen.request)
                        overlay = OverlayScreen.CachetResultOverlay(result)
                    }
                },
                onDecline = { overlay = null },
                onClose = { overlay = null }
            )
            is OverlayScreen.CachetResultOverlay -> CachetResultScreen(
                result = screen.result,
                onDone = { overlay = null },
                onViewReceipt = {
                    overlay = null
                    selectedTab = 1 // Activity tab
                }
            )
        }
        return
    }

    // ── Main app shell (no bottom nav) ──
    Scaffold(containerColor = SurfaceBackground) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // ── Header ──
            Text(
                text = "Cachet",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.displaySmall
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Top segmented control ──
            CachetSegmentedControl(
                tabs = listOf("My Cachets", "Activity"),
                selectedIndex = selectedTab,
                onTabSelected = { selectedTab = it }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── Tab content ──
            Crossfade(targetState = selectedTab, label = "main-tab") { tab ->
                when (tab) {
                    0 -> HomeScreen(
                        uiState = uiState,
                        onStartVerification = { viewModel.startVeriffVerification() },
                        onRefresh = { viewModel.loadCredentials() },
                        onPackSelected = { pack ->
                            overlay = OverlayScreen.QrShare(
                                question = pack.question,
                                predicates = pack.description.split(", "),
                                pack = pack
                            )
                        }
                    )
                    1 -> ActivityScreen(
                        historyGroups = activityState.historyGroups,
                        receipts = activityState.receipts,
                        auditResult = activityState.auditResult,
                        onRunAudit = { viewModel.runAudit() }
                    )
                }
            }
        }
    }
}

// ── Transient screens (loading, error, verification) ──

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(color = BrandAccent)
            Text("Loading wallet...", color = TextSecondary)
        }
    }
}

@Composable
fun VerificationScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(color = BrandAccent)
            Text(
                "Identity verification in progress...",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                "This may take a few moments",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun ErrorScreen(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Something went wrong",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onRetry) {
            Text("Try Again")
        }
    }
}
