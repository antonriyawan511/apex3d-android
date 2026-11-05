package com.example.studio3d.feature.editor.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.studio3d.core.engine.GestureSensitivity
import com.example.studio3d.core.engine.ShadingMode
import com.example.studio3d.feature.editor.panel.CompactSlider
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import kotlin.math.abs

/**
 * Halaman Pengaturan Studio Penuh (Clean Monochrome Studio Design).
 * Mengatur AI Bridge & MCP Server, Sensitivitas Sentuh 1:1, dan Preferensi Viewport.
 */
@Composable
fun SettingsScreen(
    sensitivity: GestureSensitivity,
    onOrbitChanged: (Float) -> Unit,
    onPanChanged: (Float) -> Unit,
    onZoomChanged: (Float) -> Unit,
    onTwistOrbitChanged: (Float) -> Unit,
    onResetSensitivity: () -> Unit,
    isAiBridgeRunning: Boolean,
    aiBridgeIp: String?,
    aiBridgePort: Int,
    onToggleAiBridge: (Boolean) -> Unit,
    onRefreshIp: () -> Unit,
    shadingMode: ShadingMode,
    onShadingModeChanged: (ShadingMode) -> Unit,
    isTransparentView: Boolean,
    onToggleTransparentView: () -> Unit,
    isSnapping: Boolean,
    onToggleSnapping: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val clipboardManager = LocalClipboardManager.current
    var copiedTag by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_screen_full"),
        color = Color(0xFF161616)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // =========================================================================
            // TOP BAR (Sits safely below status bar, clean studio monochrome)
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .background(Color(0xFF1E1E1E))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali ke Viewport",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Pengaturan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "AI Bridge • Sensitivitas • Viewport",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF9E9E9E),
                            fontSize = 11.sp
                        )
                    }
                }

                OutlinedButton(
                    onClick = onBack,
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, StudioBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Selesai", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }

            HorizontalDivider(color = StudioBorder, thickness = 0.8.dp)

            // =========================================================================
            // SCROLLABLE SETTINGS CONTENT
            // =========================================================================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                // =====================================================================
                // SECTION 1: AI BRIDGE & MCP SERVER
                // =====================================================================
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(StudioBorder)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Header Status & Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF202020))
                                        .border(0.8.dp, StudioBorder, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Hub,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }

                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "AI Bridge & MCP Server",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = Color.White
                                        )
                                        // Status Pill (Hijau = Aktif, Abu-abu = Mati)
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0xFF1E1E1E))
                                                .border(0.6.dp, StudioBorder, RoundedCornerShape(10.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(5.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isAiBridgeRunning) Color(0xFF34C759) else Color(0xFFFF453A))
                                                )
                                                Text(
                                                    text = if (isAiBridgeRunning) "Aktif" else "Mati",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = if (isAiBridgeRunning) Color(0xFFE0E0E0) else Color(0xFF9E9E9E)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "Port $aiBridgePort • Kontrol AI Agent & Komputer",
                                        fontSize = 11.sp,
                                        color = Color(0xFF9E9E9E),
                                        modifier = Modifier.padding(top = 1.dp)
                                    )
                                }
                            }

                            Switch(
                                checked = isAiBridgeRunning,
                                onCheckedChange = onToggleAiBridge,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF4A4A4A),
                                    uncheckedThumbColor = Color(0xFF9E9E9E),
                                    uncheckedTrackColor = Color(0xFF222222)
                                )
                            )
                        }

                        if (isAiBridgeRunning) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = StudioBorder.copy(alpha = 0.5f), thickness = 0.6.dp)
                            Spacer(modifier = Modifier.height(12.dp))

                            // --- 1. KONEKSI USB ---
                            Text(
                                text = "1. Koneksi USB (ADB Cable)",
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Colok kabel USB ke PC/Laptop, lalu jalankan di terminal PC:",
                                fontSize = 11.sp,
                                color = Color(0xFF9E9E9E),
                                modifier = Modifier.padding(top = 1.dp, bottom = 6.dp)
                            )
                            CodeSnippetBox(
                                text = "adb forward tcp:$aiBridgePort tcp:$aiBridgePort",
                                isCopied = copiedTag == "adb",
                                onCopy = {
                                    clipboardManager.setText(AnnotatedString("adb forward tcp:$aiBridgePort tcp:$aiBridgePort"))
                                    copiedTag = "adb"
                                }
                            )
                            Text(
                                text = "Endpoint PC: http://127.0.0.1:$aiBridgePort",
                                fontSize = 10.sp,
                                color = Color(0xFF757575),
                                modifier = Modifier.padding(top = 3.dp, bottom = 10.dp)
                            )

                            // --- 2. KONEKSI WIRELESS ---
                            Text(
                                text = "2. Koneksi Nirkabel (Wi-Fi LAN)",
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Akses langsung melalui jaringan Wi-Fi lokal:",
                                fontSize = 11.sp,
                                color = Color(0xFF9E9E9E),
                                modifier = Modifier.padding(top = 1.dp, bottom = 6.dp)
                            )
                            val wifiUrl = if (aiBridgeIp != null) "http://$aiBridgeIp:$aiBridgePort" else "Menunggu jaringan Wi-Fi..."
                            CodeSnippetBox(
                                text = wifiUrl,
                                isCopied = copiedTag == "wifi",
                                onCopy = {
                                    if (aiBridgeIp != null) {
                                        clipboardManager.setText(AnnotatedString("http://$aiBridgeIp:$aiBridgePort"))
                                        copiedTag = "wifi"
                                    }
                                },
                                onRefresh = onRefreshIp
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // --- 3. TARIK & JALANKAN MCP SERVER DI PC ---
                            Text(
                                text = "3. Tarik & Jalankan MCP Server di PC",
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp,
                                color = Color.White
                            )
                            Text(
                                text = "HP ini menyediakan skrip adapter resmi. Jalankan di terminal PC:",
                                fontSize = 11.sp,
                                color = Color(0xFF9E9E9E),
                                modifier = Modifier.padding(top = 1.dp, bottom = 6.dp)
                            )
                            val pullCmd = if (aiBridgeIp != null) "curl -O http://$aiBridgeIp:$aiBridgePort/mcp.py && python mcp.py" else "curl -O http://127.0.0.1:$aiBridgePort/mcp.py && python mcp.py"
                            CodeSnippetBox(
                                text = pullCmd,
                                isCopied = copiedTag == "mcp",
                                onCopy = {
                                    clipboardManager.setText(AnnotatedString(pullCmd))
                                    copiedTag = "mcp"
                                }
                            )
                            Text(
                                text = "Skrip mcp.py diunduh otomatis dari HP untuk Claude Desktop / Cursor / Antigravity.",
                                fontSize = 10.sp,
                                color = Color(0xFF757575),
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                    }
                }

                // =====================================================================
                // SECTION 2: SENSITIVITAS SENTUHAN VIEWPORT (1:1)
                // =====================================================================
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StudioBorder))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Sensitivitas Sentuhan (1:1)",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                        }

                        Text(
                            text = "Rasio 1.0x adalah respons 1:1 langsung terhadap sapuan jari di layar.",
                            fontSize = 11.sp,
                            color = Color(0xFF9E9E9E),
                            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                        )

                        // 1. Orbit
                        val isOrbitOne = abs(sensitivity.orbit - 1.0f) < 0.05f
                        CompactSlider(
                            label = "Orbit Kamera (1 Jari)",
                            value = sensitivity.orbit,
                            onValueChange = onOrbitChanged,
                            valueRange = 0.2f..3.0f,
                            valueText = String.format("%.2fx", sensitivity.orbit),
                            badgeText = if (isOrbitOne) "1:1 " else null,
                            containerColor = Color(0xFF1E1E1E)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // 2. Pan
                        val isPanOne = abs(sensitivity.pan - 1.0f) < 0.05f
                        CompactSlider(
                            label = "Pan / Geser (2 Jari)",
                            value = sensitivity.pan,
                            onValueChange = onPanChanged,
                            valueRange = 0.2f..3.0f,
                            valueText = String.format("%.2fx", sensitivity.pan),
                            badgeText = if (isPanOne) "1:1 " else null,
                            containerColor = Color(0xFF1E1E1E)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // 3. Zoom
                        val isZoomOne = abs(sensitivity.zoom - 1.0f) < 0.05f
                        CompactSlider(
                            label = "Zoom / Cubit (Pinch)",
                            value = sensitivity.zoom,
                            onValueChange = onZoomChanged,
                            valueRange = 0.2f..3.0f,
                            valueText = String.format("%.2fx", sensitivity.zoom),
                            badgeText = if (isZoomOne) "1:1 " else null,
                            containerColor = Color(0xFF1E1E1E)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // 4. Twist Orbit
                        val isTwistOne = abs(sensitivity.twistOrbit - 1.0f) < 0.05f
                        CompactSlider(
                            label = "Putar 2 Jari (Turntable Orbit)",
                            value = sensitivity.twistOrbit,
                            onValueChange = onTwistOrbitChanged,
                            valueRange = 0.2f..3.0f,
                            valueText = String.format("%.2fx", sensitivity.twistOrbit),
                            badgeText = if (isTwistOne) "1:1 " else null,
                            containerColor = Color(0xFF1E1E1E)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedButton(
                            onClick = onResetSensitivity,
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, StudioBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = Color(0xFFB0B0B0)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reset Sensitivitas ke Default (1:1)", fontSize = 11.sp, color = Color(0xFFE0E0E0))
                        }
                    }
                }

                // =====================================================================
                // SECTION 3: PREFERENSI VIEWPORT & SHADING
                // =====================================================================
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StudioBorder))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ViewInAr,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Preferensi Viewport 3D",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Snapping Toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onToggleSnapping() }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Grid / Unit Snapping", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
                                Text("Kunci pergerakan translasi/rotasi ke grid", fontSize = 10.sp, color = Color(0xFF9E9E9E))
                            }
                            Switch(
                                checked = isSnapping,
                                onCheckedChange = { onToggleSnapping() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF4A4A4A),
                                    uncheckedThumbColor = Color(0xFF9E9E9E),
                                    uncheckedTrackColor = Color(0xFF222222)
                                )
                            )
                        }

                        HorizontalDivider(color = StudioBorder.copy(alpha = 0.4f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

                        // Transparent View Toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onToggleTransparentView() }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Tampilan Transparan (X-Ray)", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
                                Text("Tembus pandang geometri dalam viewport", fontSize = 10.sp, color = Color(0xFF9E9E9E))
                            }
                            Switch(
                                checked = isTransparentView,
                                onCheckedChange = { onToggleTransparentView() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF4A4A4A),
                                    uncheckedThumbColor = Color(0xFF9E9E9E),
                                    uncheckedTrackColor = Color(0xFF222222)
                                )
                            )
                        }

                        HorizontalDivider(color = StudioBorder.copy(alpha = 0.4f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

                        // Shading Mode Selector
                        Text(
                            text = "Mode Shading:",
                            fontSize = 11.sp,
                            color = Color(0xFF9E9E9E),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val modes = listOf(
                                ShadingMode.WIREFRAME to "Wire",
                                ShadingMode.CLAY to "Clay",
                                ShadingMode.MATERIAL to "Material",
                                ShadingMode.STUDIO_RENDER to "Render"
                            )
                            for ((m, label) in modes) {
                                val isSelected = shadingMode == m
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) Color(0xFFE0E0E0) else Color(0xFF202020))
                                        .border(0.8.dp, if (isSelected) Color(0xFFE0E0E0) else StudioBorder, RoundedCornerShape(6.dp))
                                        .clickable { onShadingModeChanged(m) }
                                        .padding(vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color(0xFF161616) else Color(0xFFE0E0E0)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Komponen kotak kode bersih (konsisten dengan tema gelap Studio).
 */
@Composable
private fun CodeSnippetBox(
    text: String,
    isCopied: Boolean,
    onCopy: () -> Unit,
    onRefresh: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF1B1B1B))
            .border(0.8.dp, Color(0xFF333333), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = Color(0xFFD4D4D4),
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (onRefresh != null) {
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh IP",
                        tint = Color(0xFFAAAAAA),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF282828))
                    .border(0.6.dp, Color(0xFF404040), RoundedCornerShape(4.dp))
                    .clickable { onCopy() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isCopied) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF34C759),
                            modifier = Modifier.size(11.dp)
                        )
                    }
                    Text(
                        text = if (isCopied) "Tersalin" else "Salin",
                        fontSize = 10.sp,
                        color = if (isCopied) Color(0xFF34C759) else Color(0xFFE0E0E0),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
