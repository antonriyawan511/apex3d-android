package com.example.studio3d.feature.editor.tools.transform

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


import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.studio3d.core.math.Vector3
import com.example.studio3d.core.scene.SceneNode
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AxisBlue
import com.example.ui.theme.AxisGreen
import com.example.ui.theme.AxisRed
import com.example.ui.theme.StudioBorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

// =========================================================================
// ENUMS & STATE MODELS UNTUK GLOBAL NUMERIC CONTROLLER
// =========================================================================
enum class TransformCategory(val title: String, val defaultStep: Float) {
    POSITION("Position (m)", 0.1f),
    ROTATION("Rotation (°)", 5.0f),
    SCALE("Scale", 0.05f)
}

enum class AxisComponent(val label: String, val color: Color) {
    X("X", AxisRed),
    Y("Y", AxisGreen),
    Z("Z", AxisBlue)
}

data class ActiveNumericField(
    val category: TransformCategory,
    val axis: AxisComponent
)

// =========================================================================
// TRANSFORM INSPECTOR CARD (Global Numeric Controller)
// =========================================================================
@Composable
fun TransformInspectorCard(
    node: SceneNode?,
    onTransformChanged: (Vector3, Vector3, Vector3) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 2. Hanya boleh ada SATU nilai aktif secara global di seluruh kategori
    var activeField by remember { mutableStateOf<ActiveNumericField?>(null) }

    // 1. Mode Uniform Scale (default aktif)
    var isUniformScale by remember { mutableStateOf(true) }

    // State untuk input angka manual
    var editingField by remember { mutableStateOf<ActiveNumericField?>(null) }
    var manualInputText by remember { mutableStateOf("") }

    // Fungsi utilitas untuk eksekusi langkah (+ atau -)
    fun applyStep(multiplier: Float) {
        val field = activeField ?: return
        val currentNode = node ?: return

        var newPos = currentNode.position.copy()
        var newRot = currentNode.rotation.copy()
        var newScale = currentNode.scale.copy()

        when (field.category) {
            TransformCategory.POSITION -> {
                val step = field.category.defaultStep * multiplier
                newPos = when (field.axis) {
                    AxisComponent.X -> Vector3(newPos.x + step, newPos.y, newPos.z)
                    AxisComponent.Y -> Vector3(newPos.x, newPos.y + step, newPos.z)
                    AxisComponent.Z -> Vector3(newPos.x, newPos.y, newPos.z + step)
                }
            }
            TransformCategory.ROTATION -> {
                val step = field.category.defaultStep * multiplier
                newRot = when (field.axis) {
                    AxisComponent.X -> Vector3((newRot.x + step) % 360f, newRot.y, newRot.z)
                    AxisComponent.Y -> Vector3(newRot.x, (newRot.y + step) % 360f, newRot.z)
                    AxisComponent.Z -> Vector3(newRot.x, newRot.y, (newRot.z + step) % 360f)
                }
            }
            TransformCategory.SCALE -> {
                val step = field.category.defaultStep * multiplier
                if (isUniformScale) {
                    // 1. Terapkan secara proporsional ke X, Y, dan Z sekaligus
                    val sx = kotlin.math.max(0.01f, newScale.x + step)
                    val sy = kotlin.math.max(0.01f, newScale.y + step)
                    val sz = kotlin.math.max(0.01f, newScale.z + step)
                    newScale = Vector3(sx, sy, sz)
                } else {
                    newScale = when (field.axis) {
                        AxisComponent.X -> Vector3(kotlin.math.max(0.01f, newScale.x + step), newScale.y, newScale.z)
                        AxisComponent.Y -> Vector3(newScale.x, kotlin.math.max(0.01f, newScale.y + step), newScale.z)
                        AxisComponent.Z -> Vector3(newScale.x, newScale.y, kotlin.math.max(0.01f, newScale.z + step))
                    }
                }
            }
        }

        // 7. Perubahan langsung memperbarui model 3D dan UI secara realtime
        onTransformChanged(newPos, newRot, newScale)
    }

    // Fungsi utilitas untuk set nilai manual secara presisi
    fun applyManualValue(value: Float) {
        val field = editingField ?: return
        val currentNode = node ?: return

        var newPos = currentNode.position.copy()
        var newRot = currentNode.rotation.copy()
        var newScale = currentNode.scale.copy()

        when (field.category) {
            TransformCategory.POSITION -> {
                newPos = when (field.axis) {
                    AxisComponent.X -> Vector3(value, newPos.y, newPos.z)
                    AxisComponent.Y -> Vector3(newPos.x, value, newPos.z)
                    AxisComponent.Z -> Vector3(newPos.x, newPos.y, value)
                }
            }
            TransformCategory.ROTATION -> {
                newRot = when (field.axis) {
                    AxisComponent.X -> Vector3(value % 360f, newRot.y, newRot.z)
                    AxisComponent.Y -> Vector3(newRot.x, value % 360f, newRot.z)
                    AxisComponent.Z -> Vector3(newRot.x, newRot.y, value % 360f)
                }
            }
            TransformCategory.SCALE -> {
                val safeScale = kotlin.math.max(0.01f, value)
                if (isUniformScale) {
                    newScale = Vector3(safeScale, safeScale, safeScale)
                } else {
                    newScale = when (field.axis) {
                        AxisComponent.X -> Vector3(safeScale, newScale.y, newScale.z)
                        AxisComponent.Y -> Vector3(newScale.x, safeScale, newScale.z)
                        AxisComponent.Z -> Vector3(newScale.x, newScale.y, safeScale)
                    }
                }
            }
        }

        onTransformChanged(newPos, newRot, newScale)
        editingField = null
    }

    // Main Card Surface (Full Transparent / Translucent)
    Surface(
        modifier = modifier
            .widthIn(min = 280.dp, max = 340.dp)
            .fillMaxWidth()
            .testTag("transform_inspector_card"),
        color = Color(0x3310141E), // Full transparent with subtle dark tint
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 8.dp,
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(AccentAmber.copy(alpha = 0.65f)))
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .fillMaxWidth()
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Transform Inspector",
                        tint = AccentAmber,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "TRANSFORM INSPECTOR",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp,
                        color = AccentAmber,
                        letterSpacing = 0.8.sp
                    )

                    // Active field indicator badge
                    if (activeField != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(activeField!!.axis.color.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                                .border(1.dp, activeField!!.axis.color.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "${activeField!!.category.name.first()}:${activeField!!.axis.label}",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 2. Tombol Reset untuk mengembalikan Position, Rotation, Scale ke default
                    Surface(
                        modifier = Modifier
                            .height(22.dp)
                            .clickable(enabled = node != null) {
                                onTransformChanged(
                                    Vector3(0f, 0f, 0f),
                                    Vector3(0f, 0f, 0f),
                                    Vector3(1f, 1f, 1f)
                                )
                            }
                            .testTag("transform_reset_button"),
                        color = Color(0x55281820),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, Color(0xFFFF6B6B).copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset",
                                tint = Color(0xFFFF8A80),
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Reset",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFFF8A80)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("close_transform_inspector")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.LightGray,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (node == null) {
                Text(
                    text = "Pilih objek di viewport untuk mengedit transform.",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = Color.LightGray,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                // 1. Position Row (X, Y, Z buttons)
                TransformCategorySection(
                    category = TransformCategory.POSITION,
                    currentVector = node.position,
                    activeField = activeField,
                    onSelectField = { axis ->
                        activeField = ActiveNumericField(TransformCategory.POSITION, axis)
                    },
                    onOpenManualInput = { axis, currentVal ->
                        activeField = ActiveNumericField(TransformCategory.POSITION, axis)
                        editingField = ActiveNumericField(TransformCategory.POSITION, axis)
                        manualInputText = String.format(Locale.US, "%.2f", currentVal)
                    }
                )

                Spacer(modifier = Modifier.height(5.dp))

                // 2. Rotation Row (X, Y, Z buttons)
                TransformCategorySection(
                    category = TransformCategory.ROTATION,
                    currentVector = node.rotation,
                    activeField = activeField,
                    onSelectField = { axis ->
                        activeField = ActiveNumericField(TransformCategory.ROTATION, axis)
                    },
                    onOpenManualInput = { axis, currentVal ->
                        activeField = ActiveNumericField(TransformCategory.ROTATION, axis)
                        editingField = ActiveNumericField(TransformCategory.ROTATION, axis)
                        manualInputText = String.format(Locale.US, "%.1f", currentVal)
                    }
                )

                Spacer(modifier = Modifier.height(5.dp))

                // 3. Scale Row (X, Y, Z buttons)
                TransformCategorySection(
                    category = TransformCategory.SCALE,
                    currentVector = node.scale,
                    activeField = activeField,
                    onSelectField = { axis ->
                        activeField = ActiveNumericField(TransformCategory.SCALE, axis)
                    },
                    onOpenManualInput = { axis, currentVal ->
                        activeField = ActiveNumericField(TransformCategory.SCALE, axis)
                        editingField = ActiveNumericField(TransformCategory.SCALE, axis)
                        manualInputText = String.format(Locale.US, "%.2f", currentVal)
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 4. Tombol Minus (−), Uniform di tengah (20% ruang, tinggi sama 38dp), dan Plus (+)
                val isControllerEnabled = activeField != null
                val touchFeedbackColor = activeField?.axis?.color ?: AccentCyan

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Minus Button (−) : 40% ruang
                    GlobalStepButton(
                        icon = Icons.Default.Remove,
                        isEnabled = isControllerEnabled,
                        onStep = { applyStep(-1f) },
                        modifier = Modifier.weight(0.40f),
                        feedbackColor = touchFeedbackColor,
                        testTag = "global_step_minus"
                    )

                    // Tombol Uniform di tengah: 20% ruang, tinggi sama persis (38.dp)
                    Surface(
                        modifier = Modifier
                            .weight(0.20f)
                            .height(38.dp)
                            .clickable { isUniformScale = !isUniformScale }
                            .testTag("scale_uniform_toggle"),
                        color = if (isUniformScale) AccentCyan.copy(alpha = 0.25f) else Color(0xFF222834),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isUniformScale) AccentCyan else StudioBorder
                        )
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Uniform",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                fontWeight = if (isUniformScale) FontWeight.Bold else FontWeight.Medium,
                                color = if (isUniformScale) AccentCyan else Color(0xFFD0D7E2),
                                maxLines = 1
                            )
                        }
                    }

                    // Plus Button (+) : 40% ruang
                    GlobalStepButton(
                        icon = Icons.Default.Add,
                        isEnabled = isControllerEnabled,
                        onStep = { applyStep(1f) },
                        modifier = Modifier.weight(0.40f),
                        feedbackColor = touchFeedbackColor,
                        testTag = "global_step_plus"
                    )
                }
            }
        }
    }

    // Dialog Input Angka Manual melalui tombol nilai
    if (editingField != null) {
        val targetField = editingField!!
        AlertDialog(
            onDismissRequest = { editingField = null },
            title = {
                Text(
                    text = "Input Nilai ${targetField.category.title}: Sumbu ${targetField.axis.label}",
                    style = MaterialTheme.typography.titleSmall,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = targetField.axis.color
                )
            },
            text = {
                Column {
                    Text(
                        text = "Masukkan angka numerik:",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = manualInputText,
                        onValueChange = { manualInputText = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        keyboardActions = KeyboardActions(onDone = {
                            manualInputText.toFloatOrNull()?.let { applyManualValue(it) }
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = targetField.axis.color,
                            unfocusedBorderColor = StudioBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("manual_numeric_input_field")
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        manualInputText.toFloatOrNull()?.let { applyManualValue(it) }
                    }
                ) {
                    Text("Terapkan", color = AccentCyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingField = null }) {
                    Text("Batal", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E2433),
            shape = RoundedCornerShape(12.dp)
        )
    }
}

// =========================================================================
// SECTION KATEGORI (Position / Rotation / Scale) DENGAN 3 TOMBOL X, Y, Z
// =========================================================================
@Composable
fun TransformCategorySection(
    category: TransformCategory,
    currentVector: Vector3,
    activeField: ActiveNumericField?,
    onSelectField: (AxisComponent) -> Unit,
    onOpenManualInput: (AxisComponent, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = category.title,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFBAC5D6)
        )

        Spacer(modifier = Modifier.height(2.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // X Value Button
            val isXActive = activeField?.category == category && activeField.axis == AxisComponent.X
            NumericValueButton(
                axis = AxisComponent.X,
                value = currentVector.x,
                isActive = isXActive,
                onClick = {
                    if (isXActive) {
                        onOpenManualInput(AxisComponent.X, currentVector.x)
                    } else {
                        onSelectField(AxisComponent.X)
                    }
                },
                modifier = Modifier.weight(1f)
            )

            // Y Value Button
            val isYActive = activeField?.category == category && activeField.axis == AxisComponent.Y
            NumericValueButton(
                axis = AxisComponent.Y,
                value = currentVector.y,
                isActive = isYActive,
                onClick = {
                    if (isYActive) {
                        onOpenManualInput(AxisComponent.Y, currentVector.y)
                    } else {
                        onSelectField(AxisComponent.Y)
                    }
                },
                modifier = Modifier.weight(1f)
            )

            // Z Value Button
            val isZActive = activeField?.category == category && activeField.axis == AxisComponent.Z
            NumericValueButton(
                axis = AxisComponent.Z,
                value = currentVector.z,
                isActive = isZActive,
                onClick = {
                    if (isZActive) {
                        onOpenManualInput(AxisComponent.Z, currentVector.z)
                    } else {
                        onSelectField(AxisComponent.Z)
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// =========================================================================
// TOMBOL NILAI AXIS (Highlight Saat Aktif, Tap untuk Aktif / Edit Manual)
// =========================================================================
@Composable
fun NumericValueButton(
    axis: AxisComponent,
    value: Float,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isActive) {
        axis.color.copy(alpha = 0.22f)
    } else {
        Color(0x7710141E)
    }

    val borderColor = if (isActive) {
        axis.color
    } else {
        StudioBorder.copy(alpha = 0.5f)
    }

    Surface(
        modifier = modifier
            .height(32.dp)
            .clickable(onClick = onClick)
            .testTag("val_btn_${axis.label}"),
        color = backgroundColor,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(if (isActive) 1.5.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Label Axis (X / Y / Z)
            Text(
                text = axis.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = axis.color
            )

            // Numeric Value (Monospace)
            Text(
                text = String.format(Locale.US, "%.1f", value),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                fontSize = 10.sp,
                color = if (isActive) Color.White else Color(0xFFE0E6EE)
            )

            // Indikator status aktif / edit
            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(axis.color, CircleShape)
                )
            }
        }
    }
}

// =========================================================================
// GLOBAL STEP CONTROLLER BUTTON (Neutral Gray Normal, Color Feedback on Touch, Single Symbol Only)
// =========================================================================
@Composable
fun GlobalStepButton(
    icon: ImageVector,
    isEnabled: Boolean,
    onStep: () -> Unit,
    modifier: Modifier = Modifier,
    feedbackColor: Color = AccentCyan,
    testTag: String = ""
) {
    val currentOnStep by rememberUpdatedState(onStep)
    val currentIsEnabled by rememberUpdatedState(isEnabled)
    val coroutineScope = rememberCoroutineScope()
    var isPressed by remember { mutableStateOf(false) }

    // 4. Kondisi normal: abu-abu netral. Saat disentuh: feedback warna.
    val bgColor = when {
        !isEnabled -> Color(0x33141820)
        isPressed -> feedbackColor.copy(alpha = 0.35f)
        else -> Color(0xFF222834) // Abu-abu netral dalam kondisi normal
    }

    val borderColor = when {
        !isEnabled -> StudioBorder.copy(alpha = 0.25f)
        isPressed -> feedbackColor // Highlight feedback warna saat disentuh
        else -> StudioBorder // Abu-abu netral dalam kondisi normal
    }

    val iconColor = when {
        !isEnabled -> Color.Gray.copy(alpha = 0.35f)
        isPressed -> Color.White
        else -> Color(0xFFD0D7E2) // Abu-abu netral terang dalam kondisi normal
    }

    Surface(
        modifier = modifier
            .height(38.dp)
            .testTag(testTag)
            .pointerInput(currentIsEnabled) {
                if (!currentIsEnabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        // Tap untuk 1 langkah seketika
                        currentOnStep()

                        // Press-and-hold untuk perubahan berulang
                        val holdJob = coroutineScope.launch {
                            delay(320L) // Waktu tunggu awal sebelum mulai auto-repeat
                            while (isActive) {
                                currentOnStep()
                                delay(70L) // Interval pengulangan (~14 per detik)
                            }
                        }

                        tryAwaitRelease()
                        isPressed = false
                        holdJob.cancel()
                    }
                )
            },
        color = bgColor,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(if (isPressed) 1.5.dp else 1.dp, borderColor)
    ) {
        // 5. Setiap tombol HANYA menampilkan SATU simbol di tengah tanpa duplikasi
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
