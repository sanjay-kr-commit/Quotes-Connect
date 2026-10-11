package connects.quotes.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TagChip(
    tag: String,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val textColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Text(
        text = "#$tag",
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        color = textColor,
        modifier = Modifier
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(4.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}
