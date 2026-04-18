package id.cachet.wallet.android.ui.verification

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.cachet.wallet.android.ui.components.CachetMark
import id.cachet.wallet.android.ui.components.SealButton
import id.cachet.wallet.android.ui.model.RequestPredicate
import id.cachet.wallet.android.ui.model.VerificationRequest
import id.cachet.wallet.android.ui.theme.*

@Composable
fun IncomingRequestScreen(
    request: VerificationRequest,
    onShare: () -> Unit,
    onDecline: () -> Unit,
    onClose: () -> Unit,
    requiresLiveness: Boolean = false
) {
    Surface(
        modifier = Modifier.fillMaxSize().testTag("incoming_request_screen"),
        color = SurfaceBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // -- Close button --
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextTertiary)
                }
            }

            // -- Cachet shield with "?" badge --
            Spacer(modifier = Modifier.height(4.dp))
            Box(contentAlignment = Alignment.BottomEnd) {
                CachetMark(type = request.cachetType, size = 72.dp)
                Surface(
                    modifier = Modifier
                        .size(28.dp)
                        .offset(x = 4.dp, y = 4.dp),
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 2.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "?",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandPrimary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (!request.isVerifierVerified) {
                Text(
                    text = "Unverified requester",
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandWarm,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            // -- Title + subtitle with verifier name in accent --
            Text(
                text = "Verification Request",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            val nameColor = if (request.isVerifierVerified) BrandAccent else TextPrimary
            if (request.verifierName != null) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = nameColor, fontWeight = FontWeight.SemiBold)) {
                            append(request.verifierName)
                        }
                        append(" wants to know:")
                    },
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = "Someone wants to know:",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            // -- Question pill --
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = BrandPrimary
            ) {
                Text(
                    text = request.question,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            // -- "They will learn" header --
            val rawCount = request.predicates.count {
                it.disclosureType == id.cachet.wallet.android.ui.model.DisclosureType.RAW_VALUE
            }
            val predicateCount = request.predicates.size - rawCount
            Text(
                text = "They will learn:",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth()
            )
            val subtitle = if (rawCount > 0) {
                "$predicateCount derived facts, $rawCount shared as-is"
            } else {
                "Only these facts \u2014 nothing more"
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (rawCount > 0) BrandWarm else TextSecondary,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            // -- Predicate list card --
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    request.predicates.forEachIndexed { index, predicate ->
                        PredicateRow(predicate)
                        if (index < request.predicates.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 24.dp),
                                color = SurfaceElevated
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // -- Biometric notice (only when liveness required) --
            if (requiresLiveness) {
                Text(
                    text = "Also required:",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("biometric_notice"),
                    shape = RoundedCornerShape(12.dp),
                    color = TrustPendingBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, TrustPendingBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Face scan icon placeholder
                        Surface(
                            modifier = Modifier.size(32.dp),
                            shape = CircleShape,
                            color = Color.Transparent
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("\uD83D\uDC64", fontSize = 20.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "A face scan to confirm you are the holder",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = TrustPendingText
                            )
                            Text(
                                text = "Processed by Veriff \u00B7 facial data is not stored after verification",
                                style = MaterialTheme.typography.labelSmall,
                                color = TrustPendingText
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // -- Consent metadata (above CTA when no liveness, below CTA when liveness) --
            if (!requiresLiveness) {
                ConsentMetadata(request)
                Spacer(modifier = Modifier.height(12.dp))
            }

            // -- Action buttons --
            SealButton(
                text = if (requiresLiveness) "Verify with Face Scan" else "Verify & Share",
                onClick = onShare
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onDecline,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Text(
                    "Decline",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = BrandWarm
                )
            }
            Spacer(modifier = Modifier.height(4.dp))

            // -- Transparency log (compact, below CTA when liveness required) --
            if (requiresLiveness) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceElevated
                ) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = BrandAccent)) { append("\u2713 ") }
                            append("Transparency log: results kept for ")
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = TextPrimary)) {
                                append("${request.retentionDays} days")
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ConsentMetadata(request: VerificationRequest) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = SurfaceElevated
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Result kept by requester for",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Text(
                    "${request.retentionDays} days",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Logged in transparency log",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Text(
                    if (request.loggedInTransparencyLog) "Yes" else "No",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (request.loggedInTransparencyLog) BrandAccent else BrandWarm
                )
            }
        }
    }
}

@Composable
private fun PredicateRow(predicate: RequestPredicate) {
    val isRaw = predicate.disclosureType == id.cachet.wallet.android.ui.model.DisclosureType.RAW_VALUE
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("predicate_chip"),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier.size(24.dp),
            shape = CircleShape,
            color = if (isRaw) TrustPendingBg else TrustVerifiedBg
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = if (isRaw) "\uD83D\uDC41" else "\u2713",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isRaw) TrustPendingText else BrandAccent
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = predicate.claim,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = predicate.privacyNote,
                style = MaterialTheme.typography.labelSmall,
                color = if (isRaw) BrandWarm else TextTertiary
            )
        }
    }
}
