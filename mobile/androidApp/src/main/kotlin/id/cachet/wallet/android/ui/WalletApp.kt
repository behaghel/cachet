package id.cachet.wallet.android.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.cachet.wallet.android.ui.fixtures.DemoFixtures
import id.cachet.wallet.android.ui.model.*
import id.cachet.wallet.android.ui.theme.*
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

enum class MainTab { HOME, HISTORY, RECEIPTS }

/**
 * Overlay screens that sit on top of the main tab navigation.
 * null = no overlay, show normal tabs.
 */
sealed class OverlayScreen {
    data class QrShare(val question: String, val predicates: List<String>) : OverlayScreen()
    data class IncomingRequest(val request: VerificationRequest) : OverlayScreen()
    data class BadgeResultOverlay(val result: BadgeResult) : OverlayScreen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletApp(demoMode: Boolean = false) {
    val viewModel: WalletViewModel = koinViewModel { parametersOf(demoMode) }
    val uiState by viewModel.uiState.collectAsState()

    var isOnboarded by remember { mutableStateOf(demoMode) }
    var currentTab by remember { mutableStateOf(MainTab.HOME) }
    var overlay by remember { mutableStateOf<OverlayScreen?>(null) }

    // ── Onboarding gate ──
    if (!isOnboarded) {
        OnboardingScreen(onComplete = { isOnboarded = true })
        return
    }

    // ── Overlay screens (full-screen, above tabs) ──
    overlay?.let { screen ->
        when (screen) {
            is OverlayScreen.QrShare -> QrShareScreen(
                state = QrShareState(
                    question = screen.question,
                    predicates = screen.predicates
                ),
                onBack = { overlay = null },
                onClose = { overlay = null }
            )
            is OverlayScreen.IncomingRequest -> IncomingRequestScreen(
                request = screen.request,
                onShare = {
                    overlay = OverlayScreen.BadgeResultOverlay(DemoFixtures.badgeResultPass)
                },
                onDecline = { overlay = null },
                onClose = { overlay = null }
            )
            is OverlayScreen.BadgeResultOverlay -> BadgeResultScreen(
                result = screen.result,
                onDone = { overlay = null },
                onViewReceipt = {
                    overlay = null
                    currentTab = MainTab.RECEIPTS
                }
            )
        }
        return
    }

    // ── Main app shell ──
    Scaffold(
        containerColor = SurfaceBackground,
        bottomBar = {
            CachetBottomBar(
                currentTab = currentTab,
                onTabSelected = { currentTab = it }
            )
        }
    ) { innerPadding ->
        Crossfade(
            targetState = currentTab,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            label = "main-tab"
        ) { tab ->
            when (tab) {
                MainTab.HOME -> HomeScreen(
                    uiState = uiState,
                    onStartVerification = { viewModel.startVeriffVerification() },
                    onRefresh = { viewModel.loadCredentials() }
                )
                MainTab.HISTORY -> HistoryScreen()
                MainTab.RECEIPTS -> ReceiptsScreen()
            }
        }
    }
}

@Composable
private fun CachetBottomBar(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit
) {
    NavigationBar(
        containerColor = SurfaceCard,
        tonalElevation = 0.dp
    ) {
        NavigationBarItem(
            selected = currentTab == MainTab.HOME,
            onClick = { onTabSelected(MainTab.HOME) },
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("Home", fontWeight = if (currentTab == MainTab.HOME) FontWeight.SemiBold else FontWeight.Normal) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = BrandAccent,
                selectedTextColor = BrandAccent,
                unselectedIconColor = TextTertiary,
                unselectedTextColor = TextTertiary,
                indicatorColor = SurfaceAccentTint
            )
        )
        NavigationBarItem(
            selected = currentTab == MainTab.HISTORY,
            onClick = { onTabSelected(MainTab.HISTORY) },
            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "History") },
            label = { Text("History", fontWeight = if (currentTab == MainTab.HISTORY) FontWeight.SemiBold else FontWeight.Normal) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = BrandAccent,
                selectedTextColor = BrandAccent,
                unselectedIconColor = TextTertiary,
                unselectedTextColor = TextTertiary,
                indicatorColor = SurfaceAccentTint
            )
        )
        NavigationBarItem(
            selected = currentTab == MainTab.RECEIPTS,
            onClick = { onTabSelected(MainTab.RECEIPTS) },
            icon = { Icon(Icons.Default.Receipt, contentDescription = "Receipts") },
            label = { Text("Receipts", fontWeight = if (currentTab == MainTab.RECEIPTS) FontWeight.SemiBold else FontWeight.Normal) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = BrandAccent,
                selectedTextColor = BrandAccent,
                unselectedIconColor = TextTertiary,
                unselectedTextColor = TextTertiary,
                indicatorColor = SurfaceAccentTint
            )
        )
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
