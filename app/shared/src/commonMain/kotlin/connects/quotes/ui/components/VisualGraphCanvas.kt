package connects.quotes.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import connects.quotes.model.Id
import connects.quotes.model.Passage
import connects.quotes.model.PassageType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Visual Node in the interactive Graph Canvas.
 */
data class GraphNode(
    val passage: Passage,
    val isRoot: Boolean,
    var position: Offset,
    val radius: Float,
    val color: Color
)

/**
 * Interactive Multiplatform Visual Graph Canvas.
 * Renders the local Directed Acyclic Graph topology for the inspected passage:
 * - Center root node representing the subject passage
 * - Connected neighbor nodes arranged radially
 * - Directed relationship edges (lines with directional arrows)
 * - Gesture support: pan, zoom, and interactive tap-to-inspect
 */
@Composable
fun VisualGraphCanvas(
    rootPassage: Passage,
    connectedPassages: List<Passage>,
    onNodeClick: (Id) -> Unit,
    modifier: Modifier = Modifier
) {
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var zoomScale by remember { mutableStateOf(1f) }
    var selectedNodeId by remember { mutableStateOf<Id?>(null) }

    // Color mapper for passage types
    fun nodeColor(type: PassageType): Color = when (type) {
        PassageType.QUOTE -> Color(0xFF4FC3F7)       // Light Blue
        PassageType.RECORDABLE -> Color(0xFFFFB74D)  // Amber
        PassageType.AUTHOR -> Color(0xFFBA68C8)      // Purple
        PassageType.COMMENT -> Color(0xFF81C784)     // Soft Green
        PassageType.SOURCE -> Color(0xFF4DB6AC)      // Teal
        PassageType.PASSAGE -> Color(0xFFE0E0E0)     // Crisp White/Gray
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(340.dp)
            .background(Color(0xFF0A0A0A), RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFF222222), RoundedCornerShape(6.dp))
    ) {
        if (connectedPassages.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF1E1E1E),
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFFFFFF)),
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = rootPassage.type.name.take(1),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "STANDALONE KNOWLEDGE NODE",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Color.White
                )
                Text(
                    text = "No citations or child references connected to this passage yet.",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 11.sp,
                    color = Color(0xFF888888)
                )
            }
        } else {
            // Interactive Canvas
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = constraints.maxWidth.toFloat()
                val canvasHeight = constraints.maxHeight.toFloat()
                val centerOffset = Offset(canvasWidth / 2f, canvasHeight / 2f)

                // Layout nodes: Root in center, neighbors arranged radially
                val nodes = remember(rootPassage, connectedPassages, canvasWidth, canvasHeight) {
                    val list = mutableListOf<GraphNode>()
                    // Root node
                    list.add(
                        GraphNode(
                            passage = rootPassage,
                            isRoot = true,
                            position = centerOffset,
                            radius = 28f,
                            color = Color.White
                        )
                    )

                    // Connected nodes around perimeter
                    val count = connectedPassages.size
                    val radiusDistance = (minOf(canvasWidth, canvasHeight) * 0.36f).coerceIn(90f, 160f)
                    val angleStep = (2 * PI) / count

                    connectedPassages.forEachIndexed { i, p ->
                        val angle = i * angleStep - (PI / 2) // Start from top
                        val pos = Offset(
                            x = centerOffset.x + (cos(angle) * radiusDistance).toFloat(),
                            y = centerOffset.y + (sin(angle) * radiusDistance).toFloat()
                        )
                        list.add(
                            GraphNode(
                                passage = p,
                                isRoot = false,
                                position = pos,
                                radius = 20f,
                                color = nodeColor(p.type)
                            )
                        )
                    }
                    list
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                panOffset += dragAmount
                            }
                        }
                        .pointerInput(nodes, panOffset, zoomScale) {
                            detectTapGestures { tapOffset ->
                                // Hit test nodes
                                val transformedTap = (tapOffset - panOffset) / zoomScale
                                val clickedNode = nodes.firstOrNull { node ->
                                    val dx = transformedTap.x - node.position.x
                                    val dy = transformedTap.y - node.position.y
                                    sqrt(dx * dx + dy * dy) <= (node.radius + 10f)
                                }
                                if (clickedNode != null) {
                                    selectedNodeId = clickedNode.passage.id
                                    onNodeClick(clickedNode.passage.id)
                                }
                            }
                        }
                ) {
                    // Apply zoom and pan
                    val effectiveCenter = centerOffset + panOffset

                    // 1. Draw directed edges from connected nodes to root (or vice versa)
                    val rootNode = nodes.firstOrNull { it.isRoot }
                    if (rootNode != null) {
                        nodes.filter { !it.isRoot }.forEach { neighbor ->
                            val start = (neighbor.position - centerOffset) * zoomScale + effectiveCenter
                            val end = (rootNode.position - centerOffset) * zoomScale + effectiveCenter

                            // Draw edge line
                            drawLine(
                                color = Color(0xFF333333),
                                start = start,
                                end = end,
                                strokeWidth = 2f
                            )

                            // Draw small directional arrow marker halfway
                            val mid = Offset((start.x + end.x) / 2f, (start.y + end.y) / 2f)
                            drawCircle(
                                color = neighbor.color.copy(alpha = 0.6f),
                                radius = 3.5f * zoomScale,
                                center = mid
                            )
                        }
                    }

                    // 2. Draw nodes
                    nodes.forEach { node ->
                        val pos = (node.position - centerOffset) * zoomScale + effectiveCenter
                        val r = node.radius * zoomScale

                        if (node.isRoot) {
                            // Outer halo ring for root
                            drawCircle(
                                color = Color(0xFF444444),
                                radius = r + (6f * zoomScale),
                                center = pos,
                                style = Stroke(width = 1.5f)
                            )
                            // Root body
                            drawCircle(
                                color = Color(0xFF161616),
                                radius = r,
                                center = pos
                            )
                            drawCircle(
                                color = Color.White,
                                radius = r,
                                center = pos,
                                style = Stroke(width = 2.5f)
                            )
                        } else {
                            // Neighbor node
                            drawCircle(
                                color = Color(0xFF141414),
                                radius = r,
                                center = pos
                            )
                            drawCircle(
                                color = node.color,
                                radius = r,
                                center = pos,
                                style = Stroke(width = 2f)
                            )
                        }

                        // Inner dot
                        drawCircle(
                            color = node.color,
                            radius = r * 0.35f,
                            center = pos
                        )
                    }
                }

                // Node legend overlay / controls
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedButton(
                        onClick = { zoomScale = (zoomScale * 1.25f).coerceAtMost(2.5f) },
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text("+", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                    OutlinedButton(
                        onClick = { zoomScale = (zoomScale * 0.8f).coerceAtLeast(0.6f) },
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text("-", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                    OutlinedButton(
                        onClick = {
                            panOffset = Offset.Zero
                            zoomScale = 1f
                        },
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text("RESET", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                }

                // Header badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                ) {
                    Surface(
                        color = Color(0xDD111111),
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color(0xFF4CAF50), CircleShape)
                            )
                            Text(
                                text = "TOPOLOGICAL DAG VIEW • ${connectedPassages.size} CONNECTIONS",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = Color(0xFFB5B5B5)
                            )
                        }
                    }
                }
            }
        }
    }
}
