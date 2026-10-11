package connects.quotes.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import connects.quotes.model.ChannelSecurityMode
import connects.quotes.ui.theme.QCRadiusInput

/**
 * Visual badge/chip displaying transport channel security status.
 * - Shows "SSL / TLS SECURED" when connected via native HTTPS.
 * - Shows "ENCRYPTED CHANNEL" when connected over plain HTTP with active Asymmetric/Hybrid Channel Encryption.
 * - Shows "DISCONNECTED" when the server is offline.
 */
@Composable
fun ChannelSecurityChip(
    mode: ChannelSecurityMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (glyph, label, borderColor, textColor) = when (mode) {
        ChannelSecurityMode.TLS_SSL -> Quadruple(
            "🔒",
            "SSL / TLS SECURED",
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primary
        )
        ChannelSecurityMode.ASYM_ENCRYPTED -> Quadruple(
            "🔐",
            "ENCRYPTED CHANNEL",
            MaterialTheme.colorScheme.outline,
            MaterialTheme.colorScheme.onSurface
        )
        ChannelSecurityMode.INSECURE -> Quadruple(
            "⚠",
            "INSECURE HTTP",
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.error
        )
        ChannelSecurityMode.OFFLINE -> Quadruple(
            "○",
            "DISCONNECTED",
            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Row(
        modifier = modifier
            .border(1.dp, borderColor, RoundedCornerShape(QCRadiusInput))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = glyph,
            fontSize = 10.sp
        )
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            maxLines = 1
        )
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
