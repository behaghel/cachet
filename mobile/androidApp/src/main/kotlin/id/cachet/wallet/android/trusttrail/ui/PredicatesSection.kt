package id.cachet.wallet.android.trusttrail.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.cachet.wallet.android.ui.model.RequestPredicate
import id.cachet.wallet.android.ui.theme.*

/**
 * "What this proves" section with predicate rows.
 * Each predicate shows a check mark, claim description, and privacy note.
 */
@Composable
fun PredicatesSection(
    predicates: List<RequestPredicate>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(color = SurfaceBorder)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "What this proves",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            modifier = Modifier.testTag("predicates_header"),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                predicates.forEachIndexed { index, predicate ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = SurfaceElevated,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    PredicateRow(predicate)
                }
            }
        }
    }
}

@Composable
private fun PredicateRow(predicate: RequestPredicate) {
    Row(modifier = Modifier.testTag("predicate_row")) {
        Text(
            text = "\u2713",
            fontSize = 14.sp,
            color = BrandAccent,
            modifier = Modifier.padding(end = 12.dp),
        )
        Column {
            Text(
                text = predicate.claim,
                fontSize = 13.sp,
                color = TextPrimary,
            )
            Text(
                text = predicate.privacyNote,
                fontSize = 11.sp,
                color = TextTertiary,
            )
        }
    }
}
