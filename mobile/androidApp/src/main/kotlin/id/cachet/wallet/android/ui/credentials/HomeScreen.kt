package id.cachet.wallet.android.ui.credentials

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.sp
import id.cachet.wallet.android.ui.ErrorScreen
import id.cachet.wallet.android.ui.LoadingScreen
import id.cachet.wallet.android.ui.VerificationScreen
import id.cachet.wallet.android.ui.WalletUiState
import id.cachet.wallet.android.ui.components.*
import id.cachet.wallet.android.ui.model.CredentialCardUi
import id.cachet.wallet.android.ui.model.VaultSummaryUi
import id.cachet.wallet.android.ui.theme.*

@Composable
fun HomeScreen(
    uiState: WalletUiState,
    onStartVerification: () -> Unit,
    onRefresh: () -> Unit,
    onCardTapped: (CredentialCardUi) -> Unit = {}
) {
    // Transient states take over the whole screen
    when (uiState) {
        is WalletUiState.Loading -> { LoadingScreen(); return }
        is WalletUiState.VerificationInProgress -> { VerificationScreen(); return }
        is WalletUiState.Error -> { ErrorScreen(uiState.message, onRetry = onRefresh); return }
        else -> { /* continue to grid */ }
    }

    val state = uiState as? WalletUiState.HasCredentials

    if (state == null || state.credentials.isEmpty()) {
        EmptyVault(onStartVerification = onStartVerification)
    } else {
        MyCachetsGrid(
            credentials = state.credentials,
            summary = state.vaultSummary,
            onStartVerification = onStartVerification,
            onCardTapped = onCardTapped
        )
    }
}

// ===============================================
// MY CACHETS GRID
// ===============================================

@Composable
private fun MyCachetsGrid(
    credentials: List<CredentialCardUi>,
    summary: VaultSummaryUi,
    onStartVerification: () -> Unit,
    onCardTapped: (CredentialCardUi) -> Unit
) {
    // Sort: active first (preserve order), revoked last
    val sorted = credentials.sortedBy { it.isRevoked }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 72.dp)
        ) {
            // Summary bar — full width
            item(span = { GridItemSpan(maxLineSpan) }) {
                val summaryParts = buildList {
                    add("${summary.totalCount} cachets")
                    if (summary.verifiedCount > 0) add("${summary.verifiedCount} verified")
                    if (summary.pendingCount > 0) add("${summary.pendingCount} pending")
                    if (summary.revokedCount > 0) add("${summary.revokedCount} revoked")
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = TrustVerifiedBg,
                    border = BorderStroke(1.dp, TrustVerifiedBorder)
                ) {
                    Text(
                        text = summaryParts.joinToString("  \u00B7  "),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = TrustVerifiedText
                    )
                }
            }

            // Cachet grid cards
            items(sorted, key = { it.localId }) { card ->
                val tag = if (card.isRevoked) "cachet_card_revoked" else "cachet_card_${sorted.indexOf(card)}"
                CachetGridCard(card = card, onClick = { onCardTapped(card) }, testTag = tag)
            }

            // Empty slot — "Get a new cachet"
            item {
                EmptySlotCard(onClick = onStartVerification)
            }
        }

        // FAB — earn a new cachet
        FloatingActionButton(
            onClick = onStartVerification,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 8.dp)
                .testTag("fab_get_cachet"),
            containerColor = BrandPrimary,
            contentColor = Color.White,
            shape = CircleShape
        ) {
            Icon(Icons.Default.Add, contentDescription = "Get a cachet")
        }
    }
}

@Composable
private fun CachetGridCard(card: CredentialCardUi, onClick: () -> Unit = {}, testTag: String = "cachet_card") {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(196.dp)
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (card.isRevoked) SurfaceElevated else SurfaceCard
        ),
        border = BorderStroke(1.dp, SurfaceBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val dimmed = if (card.isRevoked) 0.4f else 1f

            // Shield mark
            if (card.cachetType != null) {
                CachetMark(type = card.cachetType, size = 56.dp, modifier = Modifier.alpha(dimmed))
            }

            // Display name
            Text(
                text = card.displayName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(dimmed)
            )

            // Trust status — stays full opacity
            TrustStatusChip(status = card.trustStatus)

            // Freshness
            Text(
                text = card.freshnessLabel,
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                modifier = Modifier.alpha(dimmed)
            )
        }
    }
}

// ===============================================
// EMPTY VAULT
// ===============================================

@Composable
private fun EmptyVault(onStartVerification: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(0.3f))

        // Dotted ghost card with empty-state message inside
        Surface(
            modifier = Modifier
                .width(263.dp)
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
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Ghost lines
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(Modifier.size(120.dp, 12.dp), shape = RoundedCornerShape(6.dp), color = SurfaceElevated) {}
                    Surface(Modifier.size(180.dp, 8.dp), shape = RoundedCornerShape(4.dp), color = SurfaceElevated) {}
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(Modifier.size(60.dp, 20.dp), shape = RoundedCornerShape(10.dp), color = SurfaceElevated) {}
                        Surface(Modifier.size(60.dp, 20.dp), shape = RoundedCornerShape(10.dp), color = SurfaceElevated) {}
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Your vault is empty",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Verify your identity to get your first\ncachet. It only takes 30 seconds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        SealButton(
            text = "Verify My Identity",
            onClick = onStartVerification
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
                StepIndicator(number = "1", label = "Take a selfie")
                StepIndicator(number = "2", label = "Scan your ID document")
                StepIndicator(number = "3", label = "Receive your first cachet")
            }
        }

        Spacer(modifier = Modifier.weight(0.3f))
    }
}
