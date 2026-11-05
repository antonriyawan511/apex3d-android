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


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.studio3d.core.math.Vector3
import com.example.studio3d.core.scene.Material
import com.example.studio3d.core.scene.SceneNode
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AxisBlue
import com.example.ui.theme.AxisGreen
import com.example.ui.theme.AxisRed
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun InspectorSheet(
    node: SceneNode?,
    onTransformChanged: (Vector3, Vector3, Vector3) -> Unit,
    onMaterialChanged: (Material) -> Unit,
    onSelectTexture: () -> Unit = {},
    onRemoveTexture: () -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(290.dp)
            .testTag("inspector_sheet"),
        color = StudioSurface.copy(alpha = 0.70f),
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 12.dp,
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StudioBorder))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header - Always visible regardless of object selection
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Inspector",
                        tint = AccentAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = node?.name ?: "Inspector",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1
                        )
                        if (node != null) {
                            Text(
                                text = "TYPE: ${node.meshType.uppercase(Locale.ROOT)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = AccentCyan
                            )
                        }
                    }
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("close_inspector_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Inspector",
                        tint = Color.LightGray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(StudioBorder)
            )

            // Content Area
            if (node == null) {
                // Empty State when no object is selected
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = Color.Gray.copy(alpha = 0.5f),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Pilih objek untuk melihat properti",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Ketuk objek di viewport 3D atau Outliner.",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            } else {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .verticalScroll(scrollState)
                ) {
                    // Section: Surface & Car Paint (Material Inspector)
                    Text(
                        text = "SURFACE & CAR PAINT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = AccentCyan,
                        letterSpacing = 0.8.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Color Presets
                    val colorPresets = listOf(
                        Pair("Crimson", Triple(0.92f, 0.08f, 0.12f)),
                        Pair("Cyber Cyan", Triple(0.0f, 0.85f, 0.95f)),
                        Pair("Chrome", Triple(0.92f, 0.94f, 0.98f)),
                        Pair("Stealth", Triple(0.12f, 0.12f, 0.14f)),
                        Pair("Gold", Triple(1.0f, 0.82f, 0.15f)),
                        Pair("British Racing", Triple(0.05f, 0.45f, 0.22f))
                    )

                    Text("Paint Palette", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = Color.LightGray)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        for ((_, rgb) in colorPresets) {
                            val color = Color(rgb.first, rgb.second, rgb.third)
                            val isSelected = node.material.colorR == rgb.first &&
                                    node.material.colorG == rgb.second &&
                                    node.material.colorB == rgb.third
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) Color.White else StudioBorder,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        val newMat = node.material.copy().apply {
                                            colorR = rgb.first
                                            colorG = rgb.second
                                            colorB = rgb.third
                                        }
                                        onMaterialChanged(newMat)
                                    }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Metallic
                    CompactSlider(
                        label = "Metallic Flake",
                        value = node.material.metallic,
                        onValueChange = {
                            val newMat = node.material.copy().apply { metallic = it }
                            onMaterialChanged(newMat)
                        },
                        valueText = "${(node.material.metallic * 100).roundToInt()}%",
                        accentColor = AccentCyan,
                        trackHeight = 28
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Roughness
                    CompactSlider(
                        label = "Roughness",
                        value = node.material.roughness,
                        onValueChange = {
                            val newMat = node.material.copy().apply { roughness = it }
                            onMaterialChanged(newMat)
                        },
                        valueText = "${(node.material.roughness * 100).roundToInt()}%",
                        accentColor = AccentAmber,
                        trackHeight = 28
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Clearcoat Gloss
                    CompactSlider(
                        label = "Clearcoat Gloss",
                        value = node.material.clearcoat,
                        onValueChange = {
                            val newMat = node.material.copy().apply { clearcoat = it }
                            onMaterialChanged(newMat)
                        },
                        valueText = "${(node.material.clearcoat * 100).roundToInt()}%",
                        accentColor = Color.White,
                        trackHeight = 28
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Wireframe Overlay Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Show Wireframe Edges", style = MaterialTheme.typography.labelSmall, fontSize = 11.sp, color = Color.White)
                        Switch(
                            checked = node.material.showWireframe,
                            onCheckedChange = {
                                val newMat = node.material.copy().apply { showWireframe = it }
                                onMaterialChanged(newMat)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = AccentCyan, checkedTrackColor = AccentCyan.copy(alpha = 0.5f))
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Section 3: Texture Mapping
                    Text(
                        text = "TEXTURE MAPPING",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = AccentCyan,
                        letterSpacing = 0.8.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    val textureUri = node.material.textureUri
                    val textureName = node.material.textureName ?: "Image Texture"

                    if (textureUri != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
                            shape = RoundedCornerShape(8.dp),
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AccentCyan.copy(alpha = 0.5f)))
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = "Texture",
                                        tint = AccentCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = textureName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 11.sp,
                                        color = Color.White,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Surface(
                                        onClick = onSelectTexture,
                                        color = Color(0xFF1E2838),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.weight(1f).height(30.dp)
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Text(
                                                "Ganti",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 11.sp,
                                                color = AccentCyan
                                            )
                                        }
                                    }
                                    Surface(
                                        onClick = onRemoveTexture,
                                        color = Color(0xFF381E24),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.weight(1f).height(30.dp)
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Hapus",
                                                tint = Color(0xFFFF5252),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                "Hapus",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 11.sp,
                                                color = Color(0xFFFF5252)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Surface(
                            onClick = onSelectTexture,
                            color = StudioSurfaceElevated,
                            shape = RoundedCornerShape(8.dp),
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StudioBorder)),
                            modifier = Modifier.fillMaxWidth().height(36.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = "Pilih Gambar",
                                    tint = AccentCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Pasang Tekstur (PNG/JPG)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
