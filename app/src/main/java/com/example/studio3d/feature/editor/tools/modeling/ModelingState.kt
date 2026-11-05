package com.example.studio3d.feature.editor.tools.modeling

/**
 * State container untuk operasi modeling interaktif (Inset, Extrude, dll.)
 */
data class InsetSettingsState(
    val isActive: Boolean = false,
    val ratio: Float = 0.20f,
    val evenThickness: Boolean = true,
    val initialFaces: Set<Int> = emptySet()
)

data class ExtrudeSettingsState(
    val isActive: Boolean = false,
    val distance: Float = 0.4f,
    val isEdgeMode: Boolean = false,
    val initialSelection: Set<Int> = emptySet()
)

data class SubdivideSettingsState(
    val isActive: Boolean = false,
    val cuts: Int = 1,
    val initialFaces: Set<Int> = emptySet()
)

enum class BevelMode { EDGE, VERTEX }

data class BevelSettingsState(
    val isActive: Boolean = false,
    val offset: Float = 0.10f,
    val segments: Int = 1,
    val profile: Float = 0.50f,
    val mode: BevelMode = BevelMode.EDGE,
    val initialSelection: Set<Int> = emptySet(),
    val currentFaces: Set<Int> = emptySet()
)
