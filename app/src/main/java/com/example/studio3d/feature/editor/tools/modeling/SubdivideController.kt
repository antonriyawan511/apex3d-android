package com.example.studio3d.feature.editor.tools.modeling

import com.example.studio3d.core.engine.Mesh
import com.example.studio3d.core.engine.MeshModifier
import com.example.studio3d.core.scene.SceneNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controller untuk mengisolasi logika operasional Subdivide Face interaktif (Number of Cuts).
 */
class SubdivideController {
    private val _state = MutableStateFlow(SubdivideSettingsState())
    val state: StateFlow<SubdivideSettingsState> = _state.asStateFlow()

    private var baseMesh: Mesh? = null
    private var targetNode: SceneNode? = null
    private var activeFaces: Set<Int> = emptySet()

    /**
     * Memulai sesi Subdivide interaktif.
     */
    fun startInteractiveSubdivide(node: SceneNode, facesToSubdivide: Set<Int>): Pair<Mesh, Set<Int>>? {
        val currentMesh = node.mesh ?: return null

        baseMesh = currentMesh
        targetNode = node
        activeFaces = facesToSubdivide
        val defaultCuts = 1

        val result = MeshModifier.subdivideFaces(currentMesh, facesToSubdivide, cuts = defaultCuts)
        if (result != null) {
            val (newMesh, _) = result
            node.mesh = newMesh
            _state.value = SubdivideSettingsState(
                isActive = true,
                cuts = defaultCuts,
                initialFaces = facesToSubdivide
            )
        }
        return result
    }

    /**
     * Memperbarui jumlah cuts secara real-time saat slider digeser.
     */
    fun updateCuts(newCuts: Int): Pair<Mesh, Set<Int>>? {
        val base = baseMesh ?: return null
        val node = targetNode ?: return null
        val clampedCuts = newCuts.coerceIn(1, 8)

        val result = MeshModifier.subdivideFaces(base, activeFaces, cuts = clampedCuts)
        if (result != null) {
            val (newMesh, _) = result
            node.mesh = newMesh
            _state.value = _state.value.copy(cuts = clampedCuts)
        }
        return result
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
     * Menyelesaikan sesi Subdivide dan mengembalikan mesh awal untuk disimpan ke History (Undo/Redo).
     */
    fun confirm(): Mesh? {
        val initial = baseMesh
        reset()
        return initial
    }

    fun reset() {
        baseMesh = null
        targetNode = null
        activeFaces = emptySet()
        _state.value = SubdivideSettingsState(isActive = false)
    }
}
