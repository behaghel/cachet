package id.cachet.wallet.android.trusttrail.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.cachet.wallet.android.trusttrail.model.BehavioralCachetDetailUi
import id.cachet.wallet.android.ui.components.CachetMark
import id.cachet.wallet.android.ui.theme.*

/**
 * Behavioral cachet detail screen (v2).
 * Shows the tier dial hero with shield/badge/% inside,
 * cachet name, metadata, predicates, evidence, and adaptive CTA.
 */
@Composable
fun BehavioralCachetDetailScreen(
    detail: BehavioralCachetDetailUi,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("behavioral_cachet_detail_screen"),
        color = SurfaceBackground,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            // ── Back button ──
            item {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.offset(x = (-12).dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary,
                    )
                }
            }

            // ── Hero: Tier dial with shield + badge + % inside ──
            item {
                Spacer(modifier = Modifier.height(8.dp))
                TierDial(
                    strength = detail.strength,
                    dialSize = 260.dp,
                ) {
                    // Cachet shield (uses per-type colors)
                    CachetMark(
                        type = detail.cachetType,
                        size = 72.dp,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // Tier badge inside the dial
                    TierBadge(tier = detail.tier)
                    Spacer(modifier = Modifier.height(2.dp))
                    // Strength percentage inside the dial
                    Text(
                        text = "${(detail.strength * 100).toInt()}%",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 20.sp,
                        ),
                        color = TextSecondary,
                        modifier = Modifier.testTag("strength_percentage"),
                    )
                }
            }

            // ── Cachet name — most prominent text on the screen ──
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = detail.displayName,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                    ),
                    color = TextPrimary,
                    modifier = Modifier.testTag("cachet_name"),
                )
            }

            // ── Metadata row ──
            item {
                Spacer(modifier = Modifier.height(16.dp))
                MetadataRow(
                    issuedDate = detail.issuedDate,
                    issuer = detail.issuer,
                    foundationStatus = detail.foundationStatus,
                )
            }

            // ── What this proves ──
            item {
                Spacer(modifier = Modifier.height(16.dp))
                PredicatesSection(predicates = detail.predicates)
            }

            // ── Evidence breakdown ──
            if (detail.evidencePlatforms.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    EvidenceBreakdown(platforms = detail.evidencePlatforms)
                }
            }

            // ── Secondary CTA ──
            item {
                Spacer(modifier = Modifier.height(24.dp))
                TierCtaButton(tier = detail.tier)
            }
        }
    }
}
