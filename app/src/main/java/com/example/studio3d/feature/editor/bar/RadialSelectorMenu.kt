package com.example.studio3d.feature.editor.bar

import com.example.studio3d.core.math.*
import com.example.studio3d.core.engine.*
import com.example.studio3d.core.scene.*
import com.example.studio3d.core.history.*
import com.example.studio3d.core.preferences.*
import com.example.studio3d.core.project.*
import com.example.studio3d.feature.editor.bar.*
import com.example.studio3d.feature.editor.panel.*
import com.example.studio3d.feature.editor.viewport.*
import com.example.studio3d.feature.editor.screen.*
import com.example.studio3d.feature.editor.tools.select.*


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.GroupWork
import androidx.compose.material.icons.filled.HighlightOff
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Polyline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.studio3d.feature.editor.viewport.TransformMode
import com.example.ui.theme.TextPrimary
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Data model for an annular donut sector tool item.
 */
data class DonutSectorItem(
    val id: String,
    val index: Int,
    val label: String,
    val icon: ImageVector,
    val centerAngleDeg: Float,
    val onClick: () -> Unit
)

/**
 * Maps touch swipe vector (dx, dy) directly to the corresponding donut sector index.
 * Clock-face layout:
 * - Jam 12 (North: -90°): Index 0
 * - Jam 2 (North-East: -18°): Index 1
 * - Jam 5 (South-East: 54°): Index 2
 * - Jam 7 (South-West: 126°): Index 3
 * - Jam 10 (North-West: 198° / -162°): Index 4
 */
fun calculateRadialHoverIndex(dx: Float, dy: Float, thresholdDist: Float = 6f): Int? {
    val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
    if (dist < thresholdDist) return null
    val angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()

    return when {
        angleDeg in -126f..-54f -> 0 // Jam 12: Move / Primary Action (-90°)
        angleDeg in -54f..18f   -> 1 // Jam 2: Scale / Action 2 (-18°)
        angleDeg in 18f..90f    -> 2 // Jam 5: Snap / Action 3 (54°)
        angleDeg in 90f..162f   -> 3 // Jam 7: Select / Action 4 (126°)
        else                    -> 4 // Jam 10: Rotate / Action 5 (198° / -162°)
    }
}

/**
 * Reusable Donut Radial Menu Component.
 * - Center screen positioning
 * - Semi-transparent dark neutral body
 * - Electric Cyber Cyan (#00E5FF) hover highlight
 * - No dark scrim overlay
 * - Clean hollow center hole (No center hub)
 */
@Composable
fun GenericDonutRadialMenu(
    isOpen: Boolean,
    hoveredIndex: Int? = null,
    items: List<DonutSectorItem>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(animationSpec = tween(durationMillis = 60)) + scaleIn(
            initialScale = 0.90f,
            animationSpec = tween(durationMillis = 60)
        ),
        exit = fadeOut(animationSpec = tween(durationMillis = 50)) + scaleOut(
            targetScale = 0.90f,
            animationSpec = tween(durationMillis = 50)
        ),
        modifier = modifier.fillMaxSize()
    ) {
        val density = LocalDensity.current
        val totalSizeDp = 270.dp
        val innerRadiusDp = 50.dp
        val outerRadiusDp = 118.dp
        val ringThicknessDp = outerRadiusDp - innerRadiusDp // 68.dp
        val midRadiusDp = (innerRadiusDp + outerRadiusDp) / 2f // 84.dp

        val innerRadiusPx = with(density) { innerRadiusDp.toPx() }
        val outerRadiusPx = with(density) { outerRadiusDp.toPx() }
        val ringThicknessPx = with(density) { ringThicknessDp.toPx() }
        val midRadiusPx = with(density) { midRadiusDp.toPx() }

        // Fullscreen Transparent Backdrop (Tap to dismiss)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            // Main Donut Stage Box
            Box(
                modifier = Modifier
                    .size(totalSizeDp)
                    .pointerInput(items) {
                        detectTapGestures { tapOffset ->
                            val centerOffset = Offset(size.width / 2f, size.height / 2f)
                            val dx = tapOffset.x - centerOffset.x
                            val dy = tapOffset.y - centerOffset.y
                            val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()

                            if (dist in innerRadiusPx..outerRadiusPx) {
                                val tappedIdx = calculateRadialHoverIndex(dx, dy, thresholdDist = 0f)
                                tappedIdx?.let { idx ->
                                    items.getOrNull(idx)?.let { item ->
                                        item.onClick()
                                        onDismiss()
                                    }
                                }
                            } else {
                                onDismiss()
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // 1. Vector Canvas Drawing Donut Slices and Accent Strips
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val sweepAngle = 69f

                    items.forEach { item ->
                        val isHovered = (hoveredIndex == item.index)
                        val startAngle = item.centerAngleDeg - (sweepAngle / 2f)

                        // Sektor Donat Background (Semi-transparan netral vs Solid Electric Cyan saat hover)
                        val sectorColor = if (isHovered) {
                            Color(0xFF00E5FF) // Electric Cyber Cyan Solid 100%
                        } else {
                            Color(0x991E1E1E) // Semi-transparan netral ~60% alpha
                        }

                        // Draw main thick donut wedge
                        drawArc(
                            color = sectorColor,
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = Offset(c.x - midRadiusPx, c.y - midRadiusPx),
                            size = Size(midRadiusPx * 2f, midRadiusPx * 2f),
                            style = Stroke(width = ringThicknessPx, cap = StrokeCap.Butt)
                        )

                        // Subtle outer boundary arc
                        drawArc(
                            color = if (isHovered) Color(0xFFFFFFFF) else Color(0x66444444),
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = Offset(c.x - outerRadiusPx, c.y - outerRadiusPx),
                            size = Size(outerRadiusPx * 2f, outerRadiusPx * 2f),
                            style = Stroke(width = if (isHovered) 2.0f else 1.0f, cap = StrokeCap.Butt)
                        )

                        // Inner Decorative Arc Strip
                        val innerStripColor = if (isHovered) {
                            Color(0xFFFFFFFF) // Putih menyala kontras tinggi
                        } else {
                            Color(0xFF00E5FF).copy(alpha = 0.35f) // Garis aksen lembut netral
                        }

                        drawArc(
                            color = innerStripColor,
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = Offset(c.x - innerRadiusPx, c.y - innerRadiusPx),
                            size = Size(innerRadiusPx * 2f, innerRadiusPx * 2f),
                            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Butt)
                        )
                    }
                }

                // 2. Icon & Label Overlays positioned on the Donut Slices
                items.forEach { item ->
                    val isHovered = (hoveredIndex == item.index)
                    val rad = item.centerAngleDeg * PI / 180.0
                    val offsetX = (cos(rad) * midRadiusPx).roundToInt()
                    val offsetY = (sin(rad) * midRadiusPx).roundToInt()

                    val scaleAnim by animateFloatAsState(
                        targetValue = if (isHovered) 1.15f else 1.0f,
                        animationSpec = tween(durationMillis = 50),
                        label = "donut_icon_scale_${item.index}"
                    )

                    val contentTint = if (isHovered) {
                        Color(0xFF0F0F0F) // Hitam pekat tajam di atas Electric Cyan
                    } else {
                        TextPrimary // Putih netral seragam untuk semua tool
                    }

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(offsetX, offsetY) }
                            .scale(scaleAnim),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = contentTint,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.label,
                                color = contentTint,
                                fontSize = 9.sp,
                                fontWeight = if (isHovered) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 1. Transform Selector Radial Menu (Move, Scale, Snap, Select, Rotate)
 */
@Composable
fun RadialSelectorMenu(
    isOpen: Boolean,
    hoveredIndex: Int? = null,
    transformMode: TransformMode,
    isSnapping: Boolean,
    onSelectMode: (TransformMode) -> Unit,
    onToggleSnapping: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        DonutSectorItem(
            id = "tool_move",
            index = 0,
            label = "Move",
            icon = Icons.Default.OpenWith,
            centerAngleDeg = -90f, // Jam 12
            onClick = { onSelectMode(TransformMode.TRANSLATE) }
        ),
        DonutSectorItem(
            id = "tool_scale",
            index = 1,
            label = "Scale",
            icon = Icons.Default.ZoomOutMap,
            centerAngleDeg = -18f, // Jam 2
            onClick = { onSelectMode(TransformMode.SCALE) }
        ),
        DonutSectorItem(
            id = "tool_snap",
            index = 2,
            label = if (isSnapping) "Snap ON" else "Snap OFF",
            icon = Icons.Default.GridOn,
            centerAngleDeg = 54f, // Jam 5 (Kanan-Bawah)
            onClick = { onToggleSnapping() }
        ),
        DonutSectorItem(
            id = "tool_select",
            index = 3,
            label = "Select",
            icon = Icons.Default.NearMe,
            centerAngleDeg = 126f, // Jam 7 (Kiri-Bawah)
            onClick = { onSelectMode(TransformMode.SELECT) }
        ),
        DonutSectorItem(
            id = "tool_rotate",
            index = 4,
            label = "Rotate",
            icon = Icons.Default.Refresh,
            centerAngleDeg = 198f, // Jam 10
            onClick = { onSelectMode(TransformMode.ROTATE) }
        )
    )

    GenericDonutRadialMenu(
        isOpen = isOpen,
        hoveredIndex = hoveredIndex,
        items = items,
        onDismiss = onDismiss,
        modifier = modifier
    )
}

/**
 * 2. Face Ops Radial Menu (Extrude, Inset, Bevel, Bridge, Poke)
 */
@Composable
fun FaceOpsRadialMenu(
    isOpen: Boolean,
    hoveredIndex: Int? = null,
    onExtrude: () -> Unit,
    onInset: () -> Unit = {},
    onBevel: () -> Unit = {},
    onBridge: () -> Unit = {},
    onSelectDummy: (String) -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        DonutSectorItem(
            id = "face_extrude",
            index = 0,
            label = "Extrude",
            icon = Icons.Default.Polyline,
            centerAngleDeg = -90f, // Jam 12
            onClick = onExtrude
        ),
        DonutSectorItem(
            id = "face_inset",
            index = 1,
            label = "Inset",
            icon = Icons.Default.CropSquare,
            centerAngleDeg = -18f, // Jam 2
            onClick = onInset
        ),
        DonutSectorItem(
            id = "face_bevel",
            index = 2,
            label = "Bevel",
            icon = Icons.AutoMirrored.Filled.ShowChart,
            centerAngleDeg = 54f, // Jam 5 (Kanan-Bawah)
            onClick = onBevel
        ),
        DonutSectorItem(
            id = "face_bridge",
            index = 3,
            label = "Bridge",
            icon = Icons.Default.Layers,
            centerAngleDeg = 126f, // Jam 7 (Kiri-Bawah)
            onClick = onBridge
        ),
        DonutSectorItem(
            id = "face_poke",
            index = 4,
            label = "Poke",
            icon = Icons.Default.Circle,
            centerAngleDeg = 198f, // Jam 10
            onClick = { onSelectDummy("Poke Face") }
        )
    )

    GenericDonutRadialMenu(
        isOpen = isOpen,
        hoveredIndex = hoveredIndex,
        items = items,
        onDismiss = onDismiss,
        modifier = modifier
    )
}

/**
 * 3. Topology Ops Radial Menu (Subdivide, Loop Cut, Separate, Dissolve, Merge)
 */
@Composable
fun TopologyOpsRadialMenu(
    isOpen: Boolean,
    hoveredIndex: Int? = null,
    onSubdivide: () -> Unit = {},
    onLoopCut: () -> Unit = {},
    onSeparate: () -> Unit = {},
    onDissolve: () -> Unit = {},
    onMerge: () -> Unit = {},
    onSelectDummy: (String) -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        DonutSectorItem(
            id = "topo_subdiv",
            index = 0,
            label = "Subdivide",
            icon = Icons.Default.GridOn,
            centerAngleDeg = -90f, // Jam 12
            onClick = onSubdivide
        ),
        DonutSectorItem(
            id = "topo_loopcut",
            index = 1,
            label = "Loop Cut",
            icon = Icons.Default.Tune,
            centerAngleDeg = -18f, // Jam 2
            onClick = onLoopCut
        ),
        DonutSectorItem(
            id = "topo_separate",
            index = 2,
            label = "Separate",
            icon = Icons.Default.FlipToBack,
            centerAngleDeg = 54f, // Jam 5 (Kanan-Bawah)
            onClick = { onSelectDummy("Separate Mesh") }
        ),
        DonutSectorItem(
            id = "topo_dissolve",
            index = 3,
            label = "Dissolve",
            icon = Icons.Default.HighlightOff,
            centerAngleDeg = 126f, // Jam 7 (Kiri-Bawah)
            onClick = onDissolve
        ),
        DonutSectorItem(
            id = "topo_merge",
            index = 4,
            label = "Merge",
            icon = Icons.Default.GroupWork,
            centerAngleDeg = 198f, // Jam 10
            onClick = onMerge
        )
    )

    GenericDonutRadialMenu(
        isOpen = isOpen,
        hoveredIndex = hoveredIndex,
        items = items,
        onDismiss = onDismiss,
        modifier = modifier
    )
}
