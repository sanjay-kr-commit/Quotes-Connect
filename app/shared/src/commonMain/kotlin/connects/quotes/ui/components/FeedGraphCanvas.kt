package connects.quotes.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import connects.quotes.model.Id
import connects.quotes.model.Passage
import connects.quotes.model.PassageType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Experimental feed graph.
 *
 * This is a visualization of the already-loaded feed, not a second data store.
 * Increasing feed depth supplies more nodes; the canvas simply lays those nodes
 * out and draws edges from each passage's existing subscription IDs.
 */
@Composable
fun FeedGraphCanvas(
    passages: List<Passage>,
    feedDepth: Int,
    onDepthChange: (Int) -> Unit,
    onNodeClick: (Id) -> Unit,
    modifier: Modifier = Modifier
) {
    var pan by remember { mutableStateOf(Offset.Zero) }
    var zoom by remember { mutableStateOf(1f) }

    val positions = remember(passages) {
        val count = passages.size.coerceAtLeast(1)
        passages.mapIndexed { index, passage ->
            val radius = if (count == 1) 0f else 170f + (count.coerceAtMost(18) * 3f)
            val angle = (2.0 * PI * index / count) - PI / 2.0
            passage.id to Offset(
                x = (cos(angle) * radius).toFloat(),
                y = (sin(angle) * radius).toFloat()
            )
        }.toMap()
    }

    val edgeColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val nodeSurface = MaterialTheme.colorScheme.surface

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        pan += dragAmount
                    }
                }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f) + pan

            fun screen(position: Offset): Offset =
                center + position * zoom

            // Relationships are read directly from the feed's Passage objects.
            passages.forEach { passage ->
                val from = positions[passage.id] ?: return@forEach
                passage.subscribedToIds.forEach { targetId ->
                    val target = positions[targetId] ?: return@forEach
                    drawLine(
                        color = edgeColor,
                        start = screen(from),
                        end = screen(target),
                        strokeWidth = (1.5f * zoom).coerceAtLeast(0.75f)
                    )
                }
            }

            passages.forEach { passage ->
                val position = screen(positions[passage.id] ?: Offset.Zero)
                val radius = (if (passage.type == PassageType.RECORDABLE) 15f else 11f) * zoom

                drawCircle(
                    color = nodeSurface,
                    radius = radius,
                    center = position
                )
                drawCircle(
                    color = nodeColor(passage.type),
                    radius = radius,
                    center = position,
                    style = Stroke(width = (2f * zoom).coerceAtLeast(1f))
                )
                drawCircle(
                    color = nodeColor(passage.type),
                    radius = radius * 0.28f,
                    center = position
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "EXPERIMENTAL GRAPH",
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                "${passages.size} nodes · depth $feedDepth",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val next = (zoom * 0.8f).coerceAtLeast(0.45f)
                    zoom = next
                    if (next <= 0.65f && feedDepth < 3) onDepthChange(feedDepth + 1)
                }
            ) {
                Text("−")
            }
            OutlinedButton(onClick = { zoom = (zoom * 1.25f).coerceAtMost(3f) }) {
                Text("+")
            }
            OutlinedButton(onClick = {
                zoom = 1f
                pan = Offset.Zero
            }) {
                Text("RESET")
            }
        }
    }
}

private fun nodeColor(type: PassageType): Color = when (type) {
    PassageType.QUOTE -> Color(0xFFBDBDBD)
    PassageType.RECORDABLE -> Color(0xFFFFFFFF)
    PassageType.AUTHOR -> Color(0xFF8E8E8E)
    PassageType.COMMENT -> Color(0xFFAAAAAA)
    PassageType.SOURCE -> Color(0xFF707070)
    PassageType.PASSAGE -> Color(0xFFE0E0E0)
}
