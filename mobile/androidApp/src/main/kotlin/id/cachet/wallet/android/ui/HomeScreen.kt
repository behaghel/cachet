package id.cachet.wallet.android.ui

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.cachet.wallet.android.ui.components.*
import id.cachet.wallet.android.ui.model.CredentialCardUi
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
        else -> { /* continue to grid */ }
    }

    val state = uiState as? WalletUiState.HasCredentials

    if (state == null || state.credentials.isEmpty()) {
        EmptyVault(onStartVerification = onStartVerification)
    } else {
        MyCachetsGrid(
            credentials = state.credentials,
            summary = state.vaultSummary,
            onStartVerification = onStartVerification
        )
    }
}

// ═══════════════════════════════════════════
// MY CACHETS GRID
// ═══════════════════════════════════════════

@Composable
private fun MyCachetsGrid(
    credentials: List<CredentialCardUi>,
    summary: VaultSummaryUi,
    onStartVerification: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 72.dp)
        ) {
            // Summary bar — full width
            item(span = { GridItemSpan(maxLineSpan) }) {
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

            // Cachet grid cards
            items(credentials, key = { it.localId }) { card ->
                CachetGridCard(card = card)
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
private fun CachetGridCard(card: CredentialCardUi) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
            // Shield mark
            if (card.cachetType != null) {
                CachetMark(type = card.cachetType, size = 56.dp)
            }

            // Display name
            Text(
                text = card.displayName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Trust status
            TrustStatusChip(status = card.trustStatus)

            // Freshness
            Text(
                text = card.freshnessLabel,
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary
            )
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
