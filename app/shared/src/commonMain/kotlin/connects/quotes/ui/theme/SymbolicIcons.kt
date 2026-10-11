package connects.quotes.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun BillboardIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = 2.dp.toPx()
        val w = this.size.width
        val h = this.size.height

        // Outer billboard rectangle
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.15f, h * 0.15f),
            size = Size(w * 0.7f, h * 0.5f),
            cornerRadius = CornerRadius(2.dp.toPx()),
            style = Stroke(width = stroke)
        )
        // Two posts
        drawLine(
            color = tint,
            start = Offset(w * 0.35f, h * 0.65f),
            end = Offset(w * 0.35f, h * 0.88f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.65f, h * 0.65f),
            end = Offset(w * 0.65f, h * 0.88f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun FeedIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = 2.dp.toPx()
        val w = this.size.width
        val h = this.size.height

        // Three connected graph nodes
        val n1 = Offset(w * 0.25f, h * 0.3f)
        val n2 = Offset(w * 0.75f, h * 0.3f)
        val n3 = Offset(w * 0.5f, h * 0.75f)

        drawLine(tint, n1, n3, strokeWidth = stroke)
        drawLine(tint, n2, n3, strokeWidth = stroke)

        drawCircle(tint, radius = 3.dp.toPx(), center = n1)
        drawCircle(tint, radius = 3.dp.toPx(), center = n2)
        drawCircle(tint, radius = 3.5.dp.toPx(), center = n3)
    }
}

@Composable
fun NotificationIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = 2.dp.toPx()
        val w = this.size.width
        val h = this.size.height

        // Central beacon pulse dot
        drawCircle(tint, radius = 3.dp.toPx(), center = Offset(w * 0.5f, h * 0.5f))

        // Left arc
        drawArc(
            color = tint,
            startAngle = 135f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(w * 0.2f, h * 0.2f),
            size = Size(w * 0.6f, h * 0.6f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        // Right arc
        drawArc(
            color = tint,
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(w * 0.2f, h * 0.2f),
            size = Size(w * 0.6f, h * 0.6f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun AddIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = 2.2.dp.toPx()
        val w = this.size.width
        val h = this.size.height

        drawLine(tint, Offset(w * 0.5f, h * 0.2f), Offset(w * 0.5f, h * 0.8f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.2f, h * 0.5f), Offset(w * 0.8f, h * 0.5f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
fun LinkIcon(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = 1.8.dp.toPx()
        val w = this.size.width
        val h = this.size.height

        // Connecting link line
        drawLine(tint, Offset(w * 0.35f, h * 0.65f), Offset(w * 0.65f, h * 0.35f), strokeWidth = stroke)
        // Two loop endpoints
        drawCircle(tint, radius = 2.5.dp.toPx(), center = Offset(w * 0.28f, h * 0.72f), style = Stroke(stroke))
        drawCircle(tint, radius = 2.5.dp.toPx(), center = Offset(w * 0.72f, h * 0.28f), style = Stroke(stroke))
    }
}

@Composable
fun LockIcon(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = 1.8.dp.toPx()
        val w = this.size.width
        val h = this.size.height

        // Lock shackle
        drawArc(
            color = tint,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.3f, h * 0.15f),
            size = Size(w * 0.4f, h * 0.4f),
            style = Stroke(width = stroke)
        )
        // Lock body
        drawRect(
            color = tint,
            topLeft = Offset(w * 0.22f, h * 0.45f),
            size = Size(w * 0.56f, h * 0.42f),
            style = Stroke(width = stroke)
        )
    }
}

@Composable
fun TagIcon(
    modifier: Modifier = Modifier,
    size: Dp = 14.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = 1.5.dp.toPx()
        val w = this.size.width
        val h = this.size.height

        // Hashtag / diamond symbol
        drawLine(tint, Offset(w * 0.38f, h * 0.15f), Offset(w * 0.28f, h * 0.85f), strokeWidth = stroke)
        drawLine(tint, Offset(w * 0.72f, h * 0.15f), Offset(w * 0.62f, h * 0.85f), strokeWidth = stroke)
        drawLine(tint, Offset(w * 0.15f, h * 0.38f), Offset(w * 0.85f, h * 0.38f), strokeWidth = stroke)
        drawLine(tint, Offset(w * 0.15f, h * 0.65f), Offset(w * 0.85f, h * 0.65f), strokeWidth = stroke)
    }
}

@Composable
fun UserIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = 2.dp.toPx()
        val w = this.size.width
        val h = this.size.height

        // Head
        drawCircle(tint, radius = w * 0.2f, center = Offset(w * 0.5f, h * 0.32f), style = Stroke(stroke))
        // Shoulders arc
        drawArc(
            color = tint,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.18f, h * 0.58f),
            size = Size(w * 0.64f, h * 0.4f),
            style = Stroke(width = stroke)
        )
    }
}

@Composable
fun ArrowBackIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = 2.dp.toPx()
        val w = this.size.width
        val h = this.size.height

        drawLine(tint, Offset(w * 0.7f, h * 0.25f), Offset(w * 0.3f, h * 0.5f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.3f, h * 0.5f), Offset(w * 0.7f, h * 0.75f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
fun CloseIcon(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = 2.dp.toPx()
        val w = this.size.width
        val h = this.size.height

        drawLine(tint, Offset(w * 0.25f, h * 0.25f), Offset(w * 0.75f, h * 0.75f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.75f, h * 0.25f), Offset(w * 0.25f, h * 0.75f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
fun CopyIcon(
    modifier: Modifier = Modifier,
    size: Dp = 14.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = 1.3.dp.toPx()
        val w = this.size.width
        val h = this.size.height

        // Back sheet
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.28f, h * 0.12f),
            size = Size(w * 0.58f, h * 0.62f),
            cornerRadius = CornerRadius(1.5.dp.toPx()),
            style = Stroke(width = stroke)
        )
        // Front sheet
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.12f, h * 0.28f),
            size = Size(w * 0.58f, h * 0.62f),
            cornerRadius = CornerRadius(1.5.dp.toPx()),
            style = Stroke(width = stroke)
        )
    }
}

@Composable
fun ChatIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = 1.8.dp.toPx()
        val w = this.size.width
        val h = this.size.height

        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.15f, h * 0.18f),
            size = Size(w * 0.7f, h * 0.52f),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = Stroke(width = stroke)
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.3f, h * 0.7f),
            end = Offset(w * 0.22f, h * 0.85f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.22f, h * 0.85f),
            end = Offset(w * 0.45f, h * 0.7f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

