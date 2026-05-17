package id.cachet.wallet.android.trusttrail.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import id.cachet.wallet.android.ui.theme.BrandAccent
import id.cachet.wallet.android.ui.theme.TextPrimary
import id.cachet.wallet.android.ui.theme.TextTertiary

/**
 * Three-column metadata row: Issued / Issuer / Foundation.
 * Matches the wireframe layout with label above value.
 */
@Composable
fun MetadataRow(
    issuedDate: String,
    issuer: String,
    foundationStatus: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("metadata_row"),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        MetadataField(label = "Issued", value = issuedDate, alignment = Alignment.Start)
        MetadataField(label = "Issuer", value = issuer, alignment = Alignment.CenterHorizontally)
        MetadataField(label = "Foundation", value = foundationStatus, alignment = Alignment.End, isAccent = true)
    }
}

@Composable
private fun MetadataField(
    label: String,
    value: String,
    alignment: Alignment.Horizontal,
    isAccent: Boolean = false,
) {
    Column(horizontalAlignment = alignment) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = TextTertiary,
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (isAccent) BrandAccent else TextPrimary,
        )
    }
}
