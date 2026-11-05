package com.example.studio3d.feature.editor.tools.modeling

import com.example.studio3d.core.engine.Mesh
import com.example.studio3d.core.engine.MeshBeveler
import com.example.studio3d.core.scene.SceneNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controller untuk mengisolasi logika operasional Bevel interaktif
 * dengan dukungan Edge Bevel, Vertex Bevel, Segments, dan Profile Curve.
 */
class BevelController {
    private val _state = MutableStateFlow(BevelSettingsState())
    val state: StateFlow<BevelSettingsState> = _state.asStateFlow()

    private var baseMesh: Mesh? = null
    private var targetNode: SceneNode? = null
    private var isFaceSelection: Boolean = false
    private var initialSelection: Set<Int> = emptySet()

    /**
     * Memulai sesi Bevel interaktif.
     */
    fun startInteractiveBevel(
        node: SceneNode,
        mode: BevelMode,
        isFaceSel: Boolean,
        selection: Set<Int>,
        defaultOffset: Float = 0.10f,
        defaultSegments: Int = 1,
        defaultProfile: Float = 0.50f
    ): Pair<Mesh, Set<Int>>? {
        val currentMesh = node.mesh ?: return null

        baseMesh = currentMesh
        targetNode = node
        isFaceSelection = isFaceSel
        initialSelection = selection

        val result = computeBevel(currentMesh, mode, isFaceSel, selection, defaultOffset, defaultSegments, defaultProfile)

        if (result != null) {
            val (newMesh, newFaces) = result
            node.mesh = newMesh
            _state.value = BevelSettingsState(
                isActive = true,
                offset = defaultOffset,
                segments = defaultSegments,
                profile = defaultProfile,
                mode = mode,
                initialSelection = selection,
                currentFaces = newFaces
            )
        }
        return result
    }

    /**
     * Memperbarui parameter bevel secara real-time saat slider / chip digeser.
     */
    fun updateBevel(
        newOffset: Float = _state.value.offset,
        newSegments: Int = _state.value.segments,
        newProfile: Float = _state.value.profile,
        newMode: BevelMode = _state.value.mode
    ): Pair<Mesh, Set<Int>>? {
        val base = baseMesh ?: return null
        val node = targetNode ?: return null
        val clampedOffset = newOffset.coerceIn(0.01f, 0.45f)
        val clampedSegments = newSegments.coerceIn(1, 6)
        val clampedProfile = newProfile.coerceIn(0.15f, 0.85f)

        val result = computeBevel(base, newMode, isFaceSelection, initialSelection, clampedOffset, clampedSegments, clampedProfile)

        if (result != null) {
            val (newMesh, newFaces) = result
            node.mesh = newMesh
            _state.value = _state.value.copy(
                offset = clampedOffset,
                segments = clampedSegments,
                profile = clampedProfile,
                mode = newMode,
                currentFaces = newFaces
            )
        }
        return result
    }

    private fun computeBevel(
        mesh: Mesh,
        mode: BevelMode,
        isFaceSel: Boolean,
        selection: Set<Int>,
        offset: Float,
        segments: Int,
        profile: Float
    ): Pair<Mesh, Set<Int>>? {
        return when {
            mode == BevelMode.VERTEX && selection.isNotEmpty() -> {
                MeshBeveler.bevelVertices(mesh, selection, offset = offset, segments = segments, profile = profile)
            }
            isFaceSel && selection.isNotEmpty() -> {
                MeshBeveler.bevelFaces(mesh, selection, offset = offset, depth = 0f, segments = segments, profile = profile)
            }
            else -> {
                MeshBeveler.bevelEdges(mesh, selection, offset = offset, segments = segments, profile = profile)
            }
        }
    }

    /**
     * Mengembalikan mesh awal sebelum sesi dimulai (Cancel).
     */
    fun cancel(): Mesh? {
        val base = baseMesh ?: return null
        val node = targetNode
        if (node != null) {
            node.mesh = base
        }
        reset()
        return base
    }

    /**
     * Menyelesaikan sesi Bevel dan mengembalikan mesh awal untuk disimpan ke History (Undo/Redo).
     */
    fun confirm(): Mesh? {
        val initial = baseMesh
        reset()
        return initial
    }

    fun reset() {
        baseMesh = null
        targetNode = null
        isFaceSelection = false
        initialSelection = emptySet()
        _state.value = BevelSettingsState(isActive = false)
    }
}
