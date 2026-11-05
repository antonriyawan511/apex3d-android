package com.example.studio3d.feature.editor.tools.modeling

import com.example.studio3d.core.engine.Mesh
import com.example.studio3d.core.engine.MeshExtruder
import com.example.studio3d.core.scene.SceneNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controller untuk mengisolasi logika operasional Extrude Face & Edge secara interaktif.
 */
class ExtrudeController {
    private val _state = MutableStateFlow(ExtrudeSettingsState())
    val state: StateFlow<ExtrudeSettingsState> = _state.asStateFlow()

    private var baseMesh: Mesh? = null
    private var targetNode: SceneNode? = null
    private var activeElements: Set<Int> = emptySet()
    private var isEdgeMode: Boolean = false

    /**
     * Memulai sesi Extrude interaktif (Face atau Edge).
     */
    fun startInteractiveExtrude(
        node: SceneNode,
        elements: Set<Int>,
        isEdge: Boolean
    ): Pair<Mesh, Set<Int>>? {
        if (elements.isEmpty()) return null
        val currentMesh = node.mesh ?: return null

        baseMesh = currentMesh
        targetNode = node
        activeElements = elements
        isEdgeMode = isEdge
        val defaultDistance = 0.4f

        val result = if (isEdge) {
            MeshExtruder.extrudeEdges(currentMesh, elements, defaultDistance)
        } else {
            MeshExtruder.extrudeFaces(currentMesh, elements, defaultDistance)
        }

        if (result != null) {
            val (newMesh, _) = result
            node.mesh = newMesh
            _state.value = ExtrudeSettingsState(
                isActive = true,
                distance = defaultDistance,
                isEdgeMode = isEdge,
                initialSelection = elements
            )
        }
        return result
    }

    /**
     * Memperbarui jarak extrude secara real-time saat slider digeser.
     */
    fun updateDistance(newDistance: Float): Pair<Mesh, Set<Int>>? {
        val base = baseMesh ?: return null
        val node = targetNode ?: return null
        if (activeElements.isEmpty()) return null

        val result = if (isEdgeMode) {
            MeshExtruder.extrudeEdges(base, activeElements, newDistance)
        } else {
            MeshExtruder.extrudeFaces(base, activeElements, newDistance)
        }

        if (result != null) {
            val (newMesh, _) = result
            node.mesh = newMesh
            _state.value = _state.value.copy(distance = newDistance)
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
     * Menyelesaikan sesi Extrude dan mengembalikan mesh awal untuk disimpan ke History (Undo/Redo).
     */
    fun confirm(): Mesh? {
        val initial = baseMesh
        reset()
        return initial
    }

    fun reset() {
        baseMesh = null
        targetNode = null
        activeElements = emptySet()
        isEdgeMode = false
        _state.value = ExtrudeSettingsState(isActive = false)
    }
}
