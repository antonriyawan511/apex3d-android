package com.example.studio3d.feature.editor.tools.transform

import com.example.studio3d.feature.editor.viewport.GizmoAxis
import com.example.studio3d.feature.editor.viewport.TransformMode

/**
 * State container untuk operasi transformasi (Translate, Rotate, Scale, Grid Snapping).
 */
data class TransformSettingsState(
    val mode: TransformMode = TransformMode.TRANSLATE,
    val isSnapping: Boolean = true,
    val gridSnapSize: Float = 0.5f,
    val activeAxis: GizmoAxis = GizmoAxis.NONE
)
