package id.cachet.wallet.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.cachet.wallet.android.ui.components.SealButton
import id.cachet.wallet.android.ui.model.RequestPredicate
import id.cachet.wallet.android.ui.model.VerificationRequest
import id.cachet.wallet.android.ui.theme.*

@Composable
fun IncomingRequestScreen(
    request: VerificationRequest,
    onShare: () -> Unit,
    onDecline: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SurfaceBackground
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // -- Close button --
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
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextTertiary)
                    }
                }
            }

            // -- Requester identity --
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    color = if (request.isVerifierVerified) BrandAccent else BrandPrimary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (request.isVerifierVerified) "✓" else "?",
                            fontSize = 24.sp,
                            color = Color.White
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (request.verifierName != null) {
                    Text(
                        text = request.verifierName,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (request.isVerifierVerified) BrandAccent else TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
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
            }

            // -- Title --
            item {
                Text(
                    text = "Verification Request",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (request.verifierName != null) "${request.verifierName} wants to know:" else "Someone wants to know:",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // -- Question pill --
            item {
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
            }

            // -- "They will learn" header --
            item {
                Text(
                    text = "They will learn:",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Only these facts — nothing more",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // -- Predicate list card --
            item {
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
            }

            // -- Consent metadata --
            item {
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
                Spacer(modifier = Modifier.height(12.dp))
            }

            // -- Action buttons --
            item {
                SealButton(
                    text = "Verify & Share",
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
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun PredicateRow(predicate: RequestPredicate) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier.size(24.dp),
            shape = CircleShape,
            color = TrustVerifiedBg
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "✓",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandAccent
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
                color = TextTertiary
            )
        }
    }
}
