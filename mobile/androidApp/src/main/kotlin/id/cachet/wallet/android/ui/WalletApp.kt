package id.cachet.wallet.android.ui

import android.content.Intent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import id.cachet.wallet.android.ui.components.BrandShieldMark
import id.cachet.wallet.android.ui.components.CachetSegmentedControl
import id.cachet.wallet.android.ui.fixtures.DemoFixtures
import id.cachet.wallet.android.ui.fixtures.HappyPathScenario
import id.cachet.wallet.android.ui.fixtures.ScenarioRegistry
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
    data class PackPicker(val mode: PackPickerMode) : OverlayScreen()
    data class QrShare(
        val question: String,
        val predicates: List<String>,
        val pack: CachPackUi
    ) : OverlayScreen()
    data class IncomingRequest(val request: VerificationRequest) : OverlayScreen()
    data class CachetResultOverlay(val result: CachetResult) : OverlayScreen()
    data class CachetDetail(val detail: CachetDetailUi) : OverlayScreen()
    data object QrScanner : OverlayScreen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletApp(demoMode: Boolean = false, demoEmpty: Boolean = false, demoScenario: String = "") {
    // Resolve and set the active demo scenario before ViewModel creation.
    if (demoMode || demoEmpty) {
        val scenario = when {
            demoEmpty -> ScenarioRegistry.get("empty")
            demoScenario.isNotBlank() -> ScenarioRegistry.get(demoScenario)
            else -> HappyPathScenario
        }
        DemoFixtures.activeScenario = scenario
    }
    val viewModel: WalletViewModel = koinViewModel { parametersOf(demoMode, demoEmpty) }
    val uiState by viewModel.uiState.collectAsState()
    val activityState by viewModel.activityState.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isOnboarded by remember { mutableStateOf(demoMode || demoEmpty) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var overlay by remember { mutableStateOf<OverlayScreen?>(null) }
    // Track the QR payload separately so it can be updated asynchronously
    var qrPayload by remember { mutableStateOf("") }

    // -- Onboarding gate --
    if (!isOnboarded) {
        OnboardingScreen(onComplete = { isOnboarded = true })
        return
    }

    // -- Overlay screens (full-screen, above tabs) --
    overlay?.let { screen ->
        when (screen) {
            is OverlayScreen.PackPicker -> PackPickerScreen(
                mode = screen.mode,
                packs = DemoFixtures.cachPacks,
                onPackSelected = { pack ->
                    when (screen.mode) {
                        PackPickerMode.HOLDER -> {
                            overlay = OverlayScreen.IncomingRequest(
                                CachPackMapper.toVerificationRequest(pack)
                            )
                        }
                        PackPickerMode.VERIFIER -> {
                            overlay = OverlayScreen.QrShare(
                                question = pack.question,
                                predicates = pack.description.split(", "),
                                pack = pack
                            )
                        }
                    }
                },
                onClose = { overlay = null }
            )
            is OverlayScreen.QrShare -> {
                // Create relay session -> real QR -> poll for holder response
                LaunchedEffect(screen) {
                    val relayQr = viewModel.createVerifierSession(
                        packId = screen.pack.id,
                        question = screen.question,
                        predicates = screen.predicates
                    )

                    if (relayQr == null) {
                        // Relay unavailable -- fall back to demo auto-transition
                        if (demoMode) {
                            qrPayload = packToQrPayload(screen.pack)
                            kotlinx.coroutines.delay(4000)
                            overlay = OverlayScreen.IncomingRequest(
                                CachPackMapper.toVerificationRequest(screen.pack)
                            )
                            return@LaunchedEffect
                        }
                        overlay = OverlayScreen.CachetResultOverlay(CachetResult(
                            cachetName = "Error",
                            allPassed = false, passedCount = 0, totalCount = 0,
                            predicates = emptyList(),
                            isError = true,
                            errorMessage = "Could not connect to the verification service. Check that backend services are running."
                        ))
                        return@LaunchedEffect
                    }

                    qrPayload = relayQr

                    // Simulate holder scanning after a short delay
                    kotlinx.coroutines.delay(3000)

                    val request = viewModel.fetchRequestFromRelay(relayQr)
                    if (request != null) {
                        overlay = OverlayScreen.IncomingRequest(request)
                    } else {
                        overlay = OverlayScreen.CachetResultOverlay(CachetResult(
                            cachetName = "Error",
                            allPassed = false, passedCount = 0, totalCount = 0,
                            predicates = emptyList(),
                            isError = true,
                            errorMessage = "Failed to fetch verification request from relay."
                        ))
                    }
                }
                QrShareScreen(
                    state = QrShareState(
                        question = screen.question,
                        predicates = screen.predicates,
                        qrPayload = qrPayload.ifBlank { packToQrPayload(screen.pack) },
                        sessionTtlSeconds = 300
                    ),
                    onBack = { overlay = null; qrPayload = "" },
                    onClose = { overlay = null; qrPayload = "" },
                    onShareLink = {
                        if (qrPayload.isNotBlank()) {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                putExtra(Intent.EXTRA_TEXT, qrPayload)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share verification link"))
                        }
                    },
                    onRetry = {
                        // Re-create the overlay to restart the session
                        qrPayload = ""
                        overlay = OverlayScreen.QrShare(
                            question = screen.question,
                            predicates = screen.predicates,
                            pack = screen.pack
                        )
                    }
                )
            }
            is OverlayScreen.IncomingRequest -> IncomingRequestScreen(
                request = screen.request,
                onShare = {
                    scope.launch {
                        if (qrPayload.startsWith("cachet://")) {
                            viewModel.holderRespondViaRelay(qrPayload)
                            val result = viewModel.awaitVerifierResult()
                            overlay = OverlayScreen.CachetResultOverlay(result)
                            qrPayload = ""
                        } else {
                            val result = viewModel.shareCredential(screen.request)
                            overlay = OverlayScreen.CachetResultOverlay(result)
                        }
                    }
                },
                onDecline = { overlay = null; qrPayload = "" },
                onClose = { overlay = null; qrPayload = "" }
            )
            is OverlayScreen.CachetResultOverlay -> CachetResultScreen(
                result = screen.result,
                onDone = { overlay = null },
                onViewReceipt = {
                    overlay = null
                    selectedTab = 1 // Activity tab
                }
            )
            is OverlayScreen.QrScanner -> QrScannerScreen(
                demoMode = demoMode,
                onCodeScanned = { code ->
                    if (code.startsWith("cachet://")) {
                        // Real relay flow: fetch request from relay
                        scope.launch {
                            qrPayload = code
                            val request = viewModel.fetchRequestFromRelay(code)
                            if (request != null) {
                                overlay = OverlayScreen.IncomingRequest(request)
                            }
                        }
                    } else {
                        // Demo fallback
                        overlay = OverlayScreen.IncomingRequest(
                            CachPackMapper.toVerificationRequest(DemoFixtures.cachPacks.first())
                        )
                    }
                },
                onClose = { overlay = null }
            )
            is OverlayScreen.CachetDetail -> CachetDetailScreen(
                detail = screen.detail,
                onBack = { overlay = null },
                onShare = {
                    val d = screen.detail
                    val syntheticPack = CachPackUi(
                        id = defaultPackIdForType(d.cachetType),
                        question = d.displayName,
                        description = d.predicates.joinToString(", ") { it.claim },
                        proofCount = d.predicates.size,
                        cachetType = d.cachetType
                    )
                    overlay = OverlayScreen.QrShare(
                        question = d.displayName,
                        predicates = d.predicates.map { it.claim },
                        pack = syntheticPack
                    )
                },
                onRevoke = { overlay = null },
                onSeeAllActivity = {
                    overlay = null
                    selectedTab = 1 // Activity tab
                }
            )
        }
        return
    }

    // -- Main app shell (no bottom nav) --
    Scaffold(containerColor = SurfaceBackground) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // -- Header: brand shield + wordmark --
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BrandShieldMark(size = 32.dp, fillWidth = false)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Cachet",
                    style = MaterialTheme.typography.displaySmall
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // -- Top segmented control --
            CachetSegmentedControl(
                tabs = listOf("My Cachets", "Activity"),
                selectedIndex = selectedTab,
                onTabSelected = { selectedTab = it }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // -- Tab content --
            Crossfade(targetState = selectedTab, label = "main-tab") { tab ->
                when (tab) {
                    0 -> HomeScreen(
                        uiState = uiState,
                        onStartVerification = {
                            if (uiState is WalletUiState.Empty) {
                                // Empty vault: go straight to Veriff IDV — no pack picker
                                viewModel.startVeriffVerification()
                            } else {
                                overlay = OverlayScreen.PackPicker(PackPickerMode.HOLDER)
                            }
                        },
                        onRefresh = { viewModel.loadCredentials() },
                        onCardTapped = { card ->
                            val demoDetail = DemoFixtures.detailFor(card.localId)
                            if (demoDetail != null) {
                                overlay = OverlayScreen.CachetDetail(demoDetail)
                            } else {
                                // Build detail from real credential data
                                scope.launch {
                                    val detail = viewModel.getDetailForCredential(card.localId)
                                    if (detail != null) {
                                        overlay = OverlayScreen.CachetDetail(detail)
                                    }
                                }
                            }
                        }
                    )
                    1 -> ActivityScreen(
                        historyGroups = activityState.historyGroups,
                        receipts = activityState.receipts,
                        auditResult = activityState.auditResult,
                        onRunAudit = { viewModel.runAudit() },
                        onStartVerification = { overlay = OverlayScreen.PackPicker(PackPickerMode.VERIFIER) },
                        onScanQr = { overlay = OverlayScreen.QrScanner }
                    )
                }
            }
        }
    }
}

private fun defaultPackIdForType(type: id.cachet.wallet.android.ui.components.CachetType): String = when (type) {
    id.cachet.wallet.android.ui.components.CachetType.CHILDCARE -> PackIds.CHILDCARE_ES
    id.cachet.wallet.android.ui.components.CachetType.SELLER -> PackIds.SAFE_SELLER
    id.cachet.wallet.android.ui.components.CachetType.AGE -> PackIds.CHILDCARE_BASE
    id.cachet.wallet.android.ui.components.CachetType.IDENTITY -> PackIds.IDENTITY_BASIC
}

// -- Transient screens (loading, error, verification) --

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
