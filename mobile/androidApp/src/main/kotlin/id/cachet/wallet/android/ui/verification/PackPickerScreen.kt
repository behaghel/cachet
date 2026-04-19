package id.cachet.wallet.android.ui.verification

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.cachet.wallet.android.ui.components.CachetMark
import id.cachet.wallet.android.ui.model.CachPackUi
import id.cachet.wallet.android.ui.theme.*

enum class PackPickerMode { HOLDER, VERIFIER }

@Composable
fun PackPickerScreen(
    mode: PackPickerMode,
    packs: List<CachPackUi>,
    onPackSelected: (CachPackUi) -> Unit,
    onClose: () -> Unit
) {
    val isHolder = mode == PackPickerMode.HOLDER
    val bgColor = if (isHolder) SurfaceBackground else BrandPrimary
    val cardColor = if (isHolder) Color.White else Color(0xFF334155)
    val cardBorder = if (isHolder) BorderStroke(1.dp, SurfaceBorder) else null
    val titleColor = if (isHolder) TextPrimary else Color.White
    val subtitleColor = if (isHolder) TextSecondary else Color(0xFF94A3B8)
    val metaColor = if (isHolder) TextTertiary else Color(0xFF64748B)
    val closeColor = if (isHolder) TextTertiary else Color(0xFF94A3B8)

    Surface(
        modifier = Modifier.fillMaxSize().testTag("pack_picker_screen"),
        color = bgColor
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Close button
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = closeColor)
                    }
                }
            }

            // Title
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isHolder) "Get a new cachet" else "What do you want\nto verify?",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isHolder) "Choose a verification to complete."
                           else "Pick a question. They'll prove the answer.",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                    color = subtitleColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Pack cards
            items(packs, key = { it.question }) { pack ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPackSelected(pack) }
                        .testTag("pack_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    border = cardBorder
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CachetMark(type = pack.cachetType, size = 56.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pack.question,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = titleColor
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = pack.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = subtitleColor
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${pack.proofCount} ${if (pack.proofCount == 1) "proof" else "proofs"} required",
                                style = MaterialTheme.typography.labelSmall,
                                color = metaColor
                            )
                        }
                        Surface(
                            modifier = Modifier.size(32.dp),
                            shape = CircleShape,
                            color = BrandAccent
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Hint
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isHolder) "You'll complete a quick identity check with Veriff"
                           else "They'll scan your QR or open your link to respond",
                    style = MaterialTheme.typography.bodySmall,
                    color = metaColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── Bottom safe area ──
            item {
                Spacer(modifier = Modifier
                    .navigationBarsPadding()
                    .height(8.dp))
            }
        }
    }
}
