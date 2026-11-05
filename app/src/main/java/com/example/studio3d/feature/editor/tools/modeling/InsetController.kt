package com.example.studio3d.feature.editor.tools.modeling

import com.example.studio3d.core.engine.Mesh
import com.example.studio3d.core.engine.MeshExtruder
import com.example.studio3d.core.scene.SceneNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controller untuk mengisolasi logika operasional Inset Face interaktif.
 */
class InsetController {
    private val _state = MutableStateFlow(InsetSettingsState())
    val state: StateFlow<InsetSettingsState> = _state.asStateFlow()

    private var baseMesh: Mesh? = null
    private var targetNode: SceneNode? = null
    private var activeFaces: Set<Int> = emptySet()

    /**
     * Memulai sesi Inset interaktif.
     */
    fun startInteractiveInset(node: SceneNode, facesToInset: Set<Int>): Pair<Mesh, Set<Int>>? {
        if (facesToInset.isEmpty()) return null
        val currentMesh = node.mesh ?: return null

        baseMesh = currentMesh
        targetNode = node
        activeFaces = facesToInset
        val defaultRatio = 0.20f
        val isEven = _state.value.evenThickness

        val result = MeshExtruder.insetFaces(currentMesh, facesToInset, insetRatio = defaultRatio, evenThickness = isEven)
        if (result != null) {
            val (newMesh, _) = result
            node.mesh = newMesh
            _state.value = InsetSettingsState(
                isActive = true,
                ratio = defaultRatio,
                evenThickness = isEven,
                initialFaces = facesToInset
            )
        }
        return result
    }

    /**
     * Memperbarui rasio inset secara real-time saat slider digeser.
     */
    fun updateRatio(newRatio: Float): Pair<Mesh, Set<Int>>? {
        val base = baseMesh ?: return null
        val node = targetNode ?: return null
        if (activeFaces.isEmpty()) return null

        val result = MeshExtruder.insetFaces(base, activeFaces, insetRatio = newRatio, evenThickness = _state.value.evenThickness)
        if (result != null) {
            val (newMesh, _) = result
            node.mesh = newMesh
            _state.value = _state.value.copy(ratio = newRatio)
        }
        return result
    }

    /**
     * Mengganti mode antara Even Thickness (ketebalan rata di semua sisi) dan Centroid (proporsional ke pusat).
     */
    fun toggleEvenThickness(): Pair<Mesh, Set<Int>>? {
        val base = baseMesh ?: return null
        val node = targetNode ?: return null
        if (activeFaces.isEmpty()) return null

        val newEven = !_state.value.evenThickness
        val result = MeshExtruder.insetFaces(base, activeFaces, insetRatio = _state.value.ratio, evenThickness = newEven)
        if (result != null) {
            val (newMesh, _) = result
            node.mesh = newMesh
            _state.value = _state.value.copy(evenThickness = newEven)
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
     * Menyelesaikan sesi Inset dan mengembalikan mesh awal untuk disimpan ke History (Undo/Redo).
     */
    fun confirm(): Mesh? {
        val initial = baseMesh
        reset()
        return initial
    }

    private fun reset() {
        baseMesh = null
        targetNode = null
        activeFaces = emptySet()
        _state.value = _state.value.copy(isActive = false)
    }
}
