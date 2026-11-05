package com.example.studio3d.feature.editor.panel

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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated

// =========================================================================
// 1. COMPACT SLIDER (Standar Slider Seluruh Aplikasi)
// =========================================================================
@Composable
fun CompactSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    valueText: String? = null,
    badgeText: String? = null,
    accentColor: Color = AccentCyan,
    containerColor: Color? = null,
    trackHeight: Int = 26
) {
    val content = @Composable {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = Color.White
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (badgeText != null) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        text = valueText ?: String.format("%.2f", value),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (badgeText != null) accentColor else Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = Color.DarkGray
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight.dp)
            )
        }
    }

    if (containerColor != null) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(containerColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            content()
        }
    } else {
        Box(modifier = modifier.fillMaxWidth()) {
            content()
        }
    }
}

// =========================================================================
// 2. STUDIO BAR BUTTON (Standar Tombol Navigation Bar & Topbar)
// =========================================================================
@Composable
fun StudioBarButton(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    activeColor: Color = AccentCyan
) {
    val contentColor = when {
        !enabled -> Color.White.copy(alpha = 0.35f)
        isActive -> activeColor
        else -> Color.White.copy(alpha = 0.85f)
    }

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 24.dp),
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.sp,
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.95f),
                        offset = Offset(0f, 1.5f),
                        blurRadius = 4f
                    )
                ),
                color = contentColor
            )

            if (isActive && enabled) {
                Spacer(modifier = Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .size(width = 14.dp, height = 2.5.dp)
                        .clip(RoundedCornerShape(1f))
                        .background(activeColor)
                )
            } else {
                Spacer(modifier = Modifier.height(5.5.dp))
            }
        }
    }
}

// Alias untuk kompatibilitas jika ada yang memanggil BottomBarButton
@Composable
fun BottomBarButton(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    activeColor: Color = AccentCyan
) = StudioBarButton(
    label = label,
    icon = icon,
    isActive = isActive,
    onClick = onClick,
    testTag = testTag,
    modifier = modifier,
    enabled = enabled,
    activeColor = activeColor
)

// =========================================================================
// 3. AXIS PERSPECTIVE BUTTON (Tombol S, X, Y, Z Kamera)
// =========================================================================
@Composable
fun AxisPerspectiveButton(
    label: String,
    axisColor: Color,
    isActive: Boolean,
    directionText: String?,
    onClick: () -> Unit,
    testTag: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 22.dp),
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isActive) axisColor else Color.White.copy(alpha = 0.70f),
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = directionText ?: label,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                        fontSize = 15.sp,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.95f),
                            offset = Offset(0f, 1.5f),
                            blurRadius = 4f
                        )
                    ),
                    color = if (isActive) axisColor else Color.White.copy(alpha = 0.70f)
                )
            }

            if (isActive) {
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(width = 12.dp, height = 2.5.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(axisColor)
                )
            } else {
                Spacer(modifier = Modifier.height(4.5.dp))
            }
        }
    }
}

// =========================================================================
// 4. STUDIO POPUP MENU (Standar Popup Melayang Mendukung Tap & Drag-Hold)
// =========================================================================
data class PopupMenuItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val isActive: Boolean = false,
    val activeColor: Color = AccentCyan,
    val onSelect: () -> Unit
)

@Composable
fun StudioPopupMenu(
    isOpen: Boolean,
    title: String,
    items: List<PopupMenuItem>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    hoveredIndex: Int? = null,
    onPositionItem: (Int, Float, Float) -> Unit = { _, _, _ -> },
    testTag: String = "studio_popup_menu"
) {
    if (isOpen) {
        // Fullscreen backdrop scrim to capture touches outside
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.20f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
                .testTag("${testTag}_scrim"),
            contentAlignment = Alignment.BottomStart
        ) {
            AnimatedVisibility(
                visible = true,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
                modifier = modifier
            ) {
                Surface(
                    modifier = Modifier
                        .width(185.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* Consume click inside */ }
                        .testTag(testTag),
                    color = StudioSurface,
                    shape = RoundedCornerShape(14.dp),
                    shadowElevation = 16.dp,
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(StudioBorder))
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = title.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = AccentCyan,
                            letterSpacing = 0.8.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )

                        items.forEachIndexed { index, item ->
                            val isHovered = (hoveredIndex == index)
                            val isHighlighted = isHovered || item.isActive

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .onGloballyPositioned { coordinates ->
                                        val bounds = coordinates.boundsInWindow()
                                        onPositionItem(index, bounds.top, bounds.bottom)
                                    }
                                    .clickable {
                                        item.onSelect()
                                        onDismiss()
                                    }
                                    .testTag(item.id),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isHovered) StudioSurfaceElevated.copy(alpha = 0.9f) else StudioSurfaceElevated
                                ),
                                shape = RoundedCornerShape(8.dp),
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = SolidColor(if (isHovered) AccentCyan else StudioBorder),
                                    width = if (isHovered) 1.5.dp else 1.dp
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.label,
                                        tint = if (isHighlighted) item.activeColor else Color.LightGray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isHighlighted) item.activeColor else Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
