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


import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.studio3d.core.engine.CameraPreset
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.SelectionHighlight

/**
 * Vertical floating bar containing camera axis shortcuts (X, Y, Z), magnetic snap (S),
 * transform telemetry card toggle (T), and collapse/expand controls.
 */
@Composable
fun StudioAxisBar(
    isAxisButtonsVisible: Boolean,
    isTelemetryActive: Boolean,
    isOrthoSnapEnabled: Boolean,
    activeCameraPreset: CameraPreset?,
    isCameraOrthographic: Boolean,
    onToggleVisibility: () -> Unit,
    onToggleTelemetry: () -> Unit,
    onToggleOrthoSnap: () -> Unit,
    onToggleAxisCamera: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(start = 6.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Axis Collapse / Expand Header Toggle Button (Jetpack Compose Material Icons)
        AxisPerspectiveButton(
            label = if (isAxisButtonsVisible) "Collapse" else "Expand",
            icon = if (isAxisButtonsVisible) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
            axisColor = AccentCyan,
            isActive = isAxisButtonsVisible,
            directionText = null,
            onClick = { onToggleVisibility() },
            testTag = "camera_axis_collapse_toggle"
        )

        androidx.compose.animation.AnimatedVisibility(
            visible = isAxisButtonsVisible,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(1.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // T: Transform Telemetry Card Toggle Button
                AxisPerspectiveButton(
                    label = "T",
                    axisColor = AccentAmber,
                    isActive = isTelemetryActive,
                    directionText = null,
                    onClick = { onToggleTelemetry() },
                    testTag = "camera_axis_telemetry"
                )

                // S: Orthographic Snap Toggle
                AxisPerspectiveButton(
                    label = "S",
                    axisColor = SelectionHighlight,
                    isActive = isOrthoSnapEnabled,
                    directionText = null,
                    onClick = { onToggleOrthoSnap() },
                    testTag = "camera_axis_snap"
                )

                // X Perspective Button
                val isXActive = isCameraOrthographic && (activeCameraPreset == CameraPreset.RIGHT || activeCameraPreset == CameraPreset.LEFT)
                val xDirection = if (activeCameraPreset == CameraPreset.RIGHT) "+X" else if (activeCameraPreset == CameraPreset.LEFT) "-X" else null
                AxisPerspectiveButton(
                    label = "X",
                    axisColor = Color(0xFFFF5252),
                    isActive = isXActive,
                    directionText = xDirection,
                    onClick = { onToggleAxisCamera('X') },
                    testTag = "camera_axis_x"
                )

                // Y Perspective Button
                val isYActive = isCameraOrthographic && (activeCameraPreset == CameraPreset.FRONT || activeCameraPreset == CameraPreset.BACK)
                val yDirection = if (activeCameraPreset == CameraPreset.FRONT) "+Y" else if (activeCameraPreset == CameraPreset.BACK) "-Y" else null
                AxisPerspectiveButton(
                    label = "Y",
                    axisColor = Color(0xFF69F0AE),
                    isActive = isYActive,
                    directionText = yDirection,
                    onClick = { onToggleAxisCamera('Y') },
                    testTag = "camera_axis_y"
                )

                // Z Perspective Button
                val isZActive = isCameraOrthographic && (activeCameraPreset == CameraPreset.TOP || activeCameraPreset == CameraPreset.BOTTOM)
                val zDirection = if (activeCameraPreset == CameraPreset.TOP) "+Z" else if (activeCameraPreset == CameraPreset.BOTTOM) "-Z" else null
                AxisPerspectiveButton(
                    label = "Z",
                    axisColor = Color(0xFF40C4FF),
                    isActive = isZActive,
                    directionText = zDirection,
                    onClick = { onToggleAxisCamera('Z') },
                    testTag = "camera_axis_z"
                )
            }
        }
    }
}
