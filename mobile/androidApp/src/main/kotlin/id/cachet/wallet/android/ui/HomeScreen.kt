package id.cachet.wallet.android.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.cachet.wallet.android.ui.components.*
import id.cachet.wallet.android.ui.fixtures.DemoFixtures
import id.cachet.wallet.android.ui.model.CredentialCardUi
import id.cachet.wallet.android.ui.model.CachPackUi
import id.cachet.wallet.android.ui.model.VaultSummaryUi
import id.cachet.wallet.android.ui.theme.*

@Composable
fun HomeScreen(
    uiState: WalletUiState,
    onStartVerification: () -> Unit,
    onRefresh: () -> Unit
) {
    // Transient states take over the whole screen
    when (uiState) {
        is WalletUiState.Loading -> { LoadingScreen(); return }
        is WalletUiState.VerificationInProgress -> { VerificationScreen(); return }
        is WalletUiState.Error -> { ErrorScreen(uiState.message, onRetry = onRefresh); return }
        else -> { /* continue to tabs */ }
    }

    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
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

        // ── Segmented control ──
        CachetSegmentedControl(
            tabs = listOf("My Trust", "Cache it"),
            selectedIndex = selectedTab,
            onTabSelected = { selectedTab = it }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── Tab content ──
        Crossfade(targetState = selectedTab, label = "home-tab") { tab ->
            when (tab) {
                0 -> MyTrustTab(
                    uiState = uiState,
                    onStartVerification = onStartVerification
                )
                1 -> CacheTab(packs = DemoFixtures.cachPacks)
            }
        }
    }
}

// ═══════════════════════════════════════════
// MY TRUST TAB
// ═══════════════════════════════════════════

@Composable
private fun MyTrustTab(
    uiState: WalletUiState,
    onStartVerification: () -> Unit
) {
    val state = uiState as? WalletUiState.HasCredentials

    if (state == null || state.credentials.isEmpty()) {
        EmptyVault(onStartVerification = onStartVerification)
    } else {
        CredentialVault(
            credentials = state.credentials,
            summary = state.vaultSummary,
            onStartVerification = onStartVerification
        )
    }
}

@Composable
private fun CredentialVault(
    credentials: List<CredentialCardUi>,
    summary: VaultSummaryUi,
    onStartVerification: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 72.dp)
        ) {
            // Summary bar
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = TrustVerifiedBg,
                    border = BorderStroke(1.dp, TrustVerifiedBorder)
                ) {
                    Text(
                        text = "${summary.totalCount} credentials  ·  ${summary.verifiedCount} verified  ·  ${summary.pendingCount} pending",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = TrustVerifiedText
                    )
                }
            }

            // Credential cards
            items(credentials, key = { it.localId }) { card ->
                VaultCredentialCard(card = card)
            }
        }

        // FAB
        FloatingActionButton(
            onClick = onStartVerification,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 8.dp),
            containerColor = BrandAccent,
            contentColor = TextOnBrand,
            shape = CircleShape
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add credential")
        }
    }
}

@Composable
private fun VaultCredentialCard(card: CredentialCardUi) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, SurfaceBorder)
    ) {
        Column {
            // ── Slate header bar ──
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                color = if (card.isRevoked) TextSecondary else BrandPrimary
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (card.cachetType != null) {
                        CachetMark(type = card.cachetType, size = 28.dp)
                    }
                    Text(
                        text = card.displayName,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp),
                        color = TextOnBrand,
                        modifier = Modifier.weight(1f)
                    )
                    TrustStatusChip(status = card.trustStatus)
                    Text(
                        text = card.freshnessLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }
            }

            // ── Body ──
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = card.issuerLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "What this proves",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(card.predicates) { label ->
                        PredicateChip(label = label, verified = !card.isRevoked)
                    }
                }

                if (card.sharesSummary.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = card.sharesSummary,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════
// EMPTY VAULT
// ═══════════════════════════════════════════

@Composable
private fun EmptyVault(onStartVerification: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(0.3f))

        // Dotted ghost card
        Surface(
            modifier = Modifier
                .width(263.dp)
                .height(160.dp)
                .border(
                    width = 2.dp,
                    color = SurfaceBorder,
                    shape = RoundedCornerShape(20.dp)
                ),
            shape = RoundedCornerShape(20.dp),
            color = SurfaceBackground
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(Modifier.size(120.dp, 12.dp), shape = RoundedCornerShape(6.dp), color = SurfaceElevated) {}
                Surface(Modifier.size(180.dp, 8.dp), shape = RoundedCornerShape(4.dp), color = SurfaceElevated) {}
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(Modifier.size(60.dp, 20.dp), shape = RoundedCornerShape(10.dp), color = SurfaceElevated) {}
                    Surface(Modifier.size(60.dp, 20.dp), shape = RoundedCornerShape(10.dp), color = SurfaceElevated) {}
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Your vault is empty",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Verify your identity to get your first\ncredential. It takes about 2 minutes.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, SurfaceBorder)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StepRow(number = "1", label = "Take a selfie")
                StepRow(number = "2", label = "Scan your ID document")
                StepRow(number = "3", label = "Receive your credential")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        SealButton(
            text = "Verify My Identity",
            onClick = onStartVerification
        )

        Spacer(modifier = Modifier.weight(0.3f))
    }
}

@Composable
private fun StepRow(number: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(24.dp),
            shape = CircleShape,
            color = TrustVerifiedBg
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TrustVerifiedText
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary
        )
    }
}

// ═══════════════════════════════════════════
// CACHE TAB
// ═══════════════════════════════════════════

@Composable
private fun CacheTab(packs: List<CachPackUi>) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text(
                text = "What do you need to know?",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Pick a question — we'll handle the proof",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Cach'Pack question cards
        items(packs) { pack ->
            CachPackCard(pack = pack)
        }

        // Custom / browse
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
                border = BorderStroke(1.dp, SurfaceBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = SurfaceBorder
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("+", fontSize = 20.sp, color = TextTertiary)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Something else", style = MaterialTheme.typography.titleLarge.copy(fontSize = 15.sp), color = TextSecondary)
                        Text("Browse all available cach'packs", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                    }
                }
            }
        }

        // Or scan QR
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "— or —",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { /* TODO: QR scanner */ },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(2.dp, BrandPrimary)
            ) {
                Icon(
                    Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = BrandPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Scan a QR code",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = BrandPrimary
                )
            }
        }
    }
}

@Composable
private fun CachPackCard(pack: CachPackUi) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, SurfaceBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CachetMark(type = pack.cachetType, size = 44.dp)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pack.question,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 15.sp),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = pack.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = TrustVerifiedBg
                ) {
                    Text(
                        text = "${pack.proofCount} proof${if (pack.proofCount != 1) "s" else ""}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = TrustVerifiedText
                    )
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = BrandAccent,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
