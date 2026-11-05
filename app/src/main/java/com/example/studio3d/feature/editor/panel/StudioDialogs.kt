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


import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.studio3d.core.engine.GestureSensitivity
import com.example.studio3d.core.project.ProjectManager
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import java.io.File
import kotlin.math.abs

// =========================================================================
// 1. SETTINGS DIALOG (Pengaturan Sensitivitas Sentuh 1:1)
// =========================================================================
@Composable
fun SettingsDialog(
    sensitivity: GestureSensitivity,
    onOrbitChanged: (Float) -> Unit,
    onPanChanged: (Float) -> Unit,
    onZoomChanged: (Float) -> Unit,
    onTwistOrbitChanged: (Float) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .testTag("settings_dialog"),
            color = StudioSurface,
            shape = RoundedCornerShape(16.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(StudioBorder))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = AccentCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Sensitivitas Sentuhan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.LightGray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Text(
                    text = "Nilai 1.0x adalah rasio 1:1 real-world dengan gerakan jari.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                // 1. Orbit (1 Jari)
                val isOrbitOne = abs(sensitivity.orbit - 1.0f) < 0.05f
                CompactSlider(
                    label = "Orbit Kamera (1 Jari)",
                    value = sensitivity.orbit,
                    onValueChange = onOrbitChanged,
                    valueRange = 0.2f..3.0f,
                    valueText = String.format("%.2fx", sensitivity.orbit),
                    badgeText = if (isOrbitOne) "1:1 " else null,
                    containerColor = StudioSurfaceElevated
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 2. Pan (2 Jari)
                val isPanOne = abs(sensitivity.pan - 1.0f) < 0.05f
                CompactSlider(
                    label = "Pan / Geser (2 Jari)",
                    value = sensitivity.pan,
                    onValueChange = onPanChanged,
                    valueRange = 0.2f..3.0f,
                    valueText = String.format("%.2fx", sensitivity.pan),
                    badgeText = if (isPanOne) "1:1 " else null,
                    containerColor = StudioSurfaceElevated
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 3. Zoom (Cubit / Pinch)
                val isZoomOne = abs(sensitivity.zoom - 1.0f) < 0.05f
                CompactSlider(
                    label = "Zoom / Cubit (Pinch)",
                    value = sensitivity.zoom,
                    onValueChange = onZoomChanged,
                    valueRange = 0.2f..3.0f,
                    valueText = String.format("%.2fx", sensitivity.zoom),
                    badgeText = if (isZoomOne) "1:1 " else null,
                    containerColor = StudioSurfaceElevated
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 4. Putar 2 Jari (Turntable Orbit)
                val isTwistOne = abs(sensitivity.twistOrbit - 1.0f) < 0.05f
                CompactSlider(
                    label = "Putar 2 Jari (Turntable Orbit)",
                    value = sensitivity.twistOrbit,
                    onValueChange = onTwistOrbitChanged,
                    valueRange = 0.2f..3.0f,
                    valueText = String.format("%.2fx", sensitivity.twistOrbit),
                    badgeText = if (isTwistOne) "1:1 " else null,
                    containerColor = StudioSurfaceElevated
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Bottom Buttons (Reset ke 1:1 & Tutup)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onReset,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Reset 1:1", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentCyan,
                            contentColor = Color.Black
                        )
                    ) {
                        Text(text = "Selesai", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// =========================================================================
// 2. PROJECT DIALOG (File Manager Simpan / Buka Project)
// =========================================================================
@Composable
fun ProjectDialog(
    currentProjectName: String,
    onSaveProject: (String) -> Unit,
    onOpenProject: (File) -> Unit,
    onNewProject: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var filenameInput by remember { mutableStateOf(currentProjectName.replace(" ", "_").lowercase()) }
    val savedFiles = remember { ProjectManager.listProjects(context) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("project_dialog"),
            color = StudioSurface,
            shape = RoundedCornerShape(16.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(StudioBorder))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Project File Manager",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_project_dialog")) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Save Current Section
                Text("Save Current Studio Project", style = MaterialTheme.typography.labelSmall, color = AccentCyan)
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = filenameInput,
                        onValueChange = { filenameInput = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("project_filename_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = StudioBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Button(
                        onClick = {
                            if (filenameInput.isNotBlank()) {
                                onSaveProject(filenameInput.trim())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = Color.Black),
                        modifier = Modifier.testTag("save_project_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Save", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // New Project Action
                FilledTonalButton(
                    onClick = onNewProject,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_project_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("New Automotive Project")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Saved Projects List
                Text("Saved .studio3d Files (${savedFiles.size})", style = MaterialTheme.typography.labelSmall, color = AccentAmber)
                Spacer(modifier = Modifier.height(6.dp))

                if (savedFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No saved projects found. Save one above!", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(savedFiles) { file ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenProject(file) }
                                    .testTag("open_project_${file.name}"),
                                colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.FolderOpen, contentDescription = "Project", tint = AccentAmber, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(file.name, style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
                                            Text("${file.length()} bytes", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                        }
                                    }
                                    Text("OPEN", style = MaterialTheme.typography.labelSmall, color = AccentCyan, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// 3. RECOVER AUTOSAVE DIALOG (Pemulihan Sesi Sebelumnya)
// =========================================================================
@Composable
fun RecoverAutosaveDialog(
    onRestore: () -> Unit,
    onDiscard: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDiscard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, contentDescription = "Autosave", tint = AccentAmber)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Restore Previous Session?", color = Color.White)
            }
        },
        text = {
            Text(
                "An unsaved 3D studio session was recovered from your previous workflow. Would you like to restore it?",
                color = Color(0xFFDCE2EC)
            )
        },
        confirmButton = {
            Button(
                onClick = onRestore,
                colors = ButtonDefaults.buttonColors(containerColor = AccentAmber, contentColor = Color.Black),
                modifier = Modifier.testTag("restore_autosave_button")
            ) {
                Text("Restore Session")
            }
        },
        dismissButton = {
            TextButton(onClick = onDiscard, modifier = Modifier.testTag("discard_autosave_button")) {
                Text("Start Fresh", color = Color.LightGray)
            }
        },
        containerColor = StudioSurface,
        modifier = Modifier.testTag("recover_autosave_dialog")
    )
}
