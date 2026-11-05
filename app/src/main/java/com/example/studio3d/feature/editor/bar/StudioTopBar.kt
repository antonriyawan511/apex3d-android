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


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.studio3d.core.engine.ShadingMode
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.StudioSurface

/**
 * Top floating navigation bar containing Settings, Project Menu, Shading Mode selector,
 * X-Ray / Transparent View toggle, and Redo / Undo actions.
 */
@Composable
fun StudioTopBar(
    activePanel: ActivePanel?,
    shadingMode: ShadingMode,
    isTransparentView: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    isAiBridgeRunning: Boolean = false,
    onOpenSettings: () -> Unit,
    onOpenProjectMenu: () -> Unit,
    onShadingModeChanged: (ShadingMode) -> Unit,
    onToggleTransparentView: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onShowAiBridgeInfo: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var shadingMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Controls: Settings, Project Manager, Shading Mode Selector, and Transparent View Toggle (X-Ray)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Settings Button (Pojok paling kiri top bar)
            Box(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = 18.dp)
                    ) { onOpenSettings() }
                    .padding(horizontal = 6.dp, vertical = 6.dp)
                    .testTag("action_settings"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = if (activePanel == ActivePanel.SETTINGS) AccentCyan else Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Project Manager Button (kiri atas top bar)
            Box(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = 18.dp)
                    ) { onOpenProjectMenu() }
                    .padding(horizontal = 5.dp, vertical = 5.dp)
                    .testTag("action_project_manager"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Project Manager",
                        tint = if (activePanel == ActivePanel.PROJECT_MENU) AccentCyan else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Project",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp,
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.95f),
                                offset = Offset(0f, 1.5f),
                                blurRadius = 4f
                            )
                        ),
                        color = if (activePanel == ActivePanel.PROJECT_MENU) AccentCyan else Color.White
                    )
                }
            }

            // Shading Mode Selector (Material / Clay / Wire / Render)
            Box(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = 20.dp)
                    ) { shadingMenuExpanded = true }
                    .padding(horizontal = 5.dp, vertical = 5.dp)
                    .testTag("shading_mode_button"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    val (modeIcon, modeColor) = when (shadingMode) {
                        ShadingMode.WIREFRAME -> Icons.Default.GridOn to Color.White
                        ShadingMode.CLAY -> Icons.Default.Circle to Color(0xFFD0D0D0)
                        ShadingMode.MATERIAL -> Icons.Default.Layers to Color.White
                        ShadingMode.STUDIO_RENDER -> Icons.Default.WbSunny to Color(0xFFE0E0E0)
                    }
                    Icon(
                        imageVector = modeIcon,
                        contentDescription = "Shading Mode",
                        tint = modeColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = shadingMode.shortLabel,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp,
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.95f),
                                offset = Offset(0f, 1.5f),
                                blurRadius = 4f
                            )
                        ),
                        color = Color.White
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Dropdown",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = shadingMenuExpanded,
                    onDismissRequest = { shadingMenuExpanded = false },
                    modifier = Modifier.background(StudioSurface)
                ) {
                    for (mode in ShadingMode.values()) {
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (mode == shadingMode) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(AccentAmber)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        mode.label,
                                        color = if (mode == shadingMode) AccentAmber else Color.White,
                                        fontWeight = if (mode == shadingMode) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            },
                            onClick = {
                                onShadingModeChanged(mode)
                                shadingMenuExpanded = false
                            },
                            modifier = Modifier.testTag("shading_mode_${mode.name}")
                        )
                    }
                }
            }

            // Transparent View Toggle (X-Ray / Solid Mode)
            Box(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = 18.dp)
                    ) { onToggleTransparentView() }
                    .padding(horizontal = 5.dp, vertical = 5.dp)
                    .testTag("action_toggle_transparent"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = if (isTransparentView) Icons.Default.FlipToBack else Icons.Default.Layers,
                        contentDescription = "Toggle Transparent View",
                        tint = if (isTransparentView) AccentCyan else Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isTransparentView) "X-Ray" else "Solid",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isTransparentView) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.5.sp,
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.95f),
                                offset = Offset(0f, 1.5f),
                                blurRadius = 4f
                            )
                        ),
                        color = if (isTransparentView) AccentCyan else Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }

        // Right Controls: Redo and Undo (Undo paling kanan)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Redo
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clickable(
                        enabled = canRedo,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = 16.dp)
                    ) { onRedo() }
                    .testTag("action_redo"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "Redo",
                    tint = if (canRedo) Color.White else Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Undo (paling kanan)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clickable(
                        enabled = canUndo,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = 16.dp)
                    ) { onUndo() }
                    .testTag("action_undo"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo",
                    tint = if (canUndo) Color.White else Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
