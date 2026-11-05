package com.example.studio3d.core.history

import com.example.studio3d.core.math.*
import com.example.studio3d.core.engine.*
import com.example.studio3d.core.scene.*
import com.example.studio3d.core.history.*
import com.example.studio3d.core.preferences.*
import com.example.studio3d.core.project.*
import com.example.studio3d.feature.editor.viewport.*
import com.example.studio3d.feature.editor.tools.select.*


import com.example.studio3d.feature.editor.tools.select.EditSelectionManager
import com.example.studio3d.core.engine.Mesh
import com.example.studio3d.core.scene.SceneGraph
import com.example.studio3d.feature.editor.screen.SelectionMode

/**
 * Command to support Undo/Redo for mesh topology changes like Face Extrude, Subdivide, Inset, or Deletion.
 * Restores both mesh geometry and exact element selection states across undo/redo history.
 */
class MeshTopologyChangeCommand(
    private val nodeId: String,
    private val sceneGraph: SceneGraph,
    private val oldMesh: Mesh,
    private val newMesh: Mesh,
    private val oldSelection: Set<Int> = emptySet(),
    private val newSelection: Set<Int> = emptySet(),
    private val editSelection: EditSelectionManager? = null,
    private val oldSelectionMode: SelectionMode? = null,
    private val newSelectionMode: SelectionMode? = null,
    private val onSetSelectionMode: ((SelectionMode) -> Unit)? = null,
    override val description: String = "Modify Mesh",
    private val onMeshChanged: (() -> Unit)? = null
) : Command {

    override fun execute() {
        val node = sceneGraph.nodes[nodeId] ?: return
        node.mesh = newMesh
        newMesh.markGeometryDirty()
        if (newSelectionMode != null) onSetSelectionMode?.invoke(newSelectionMode)
        applySelection(newSelection, newSelectionMode ?: oldSelectionMode)
        onMeshChanged?.invoke()
    }

    override fun undo() {
        val node = sceneGraph.nodes[nodeId] ?: return
        node.mesh = oldMesh
        oldMesh.markGeometryDirty()
        if (oldSelectionMode != null) onSetSelectionMode?.invoke(oldSelectionMode)
        applySelection(oldSelection, oldSelectionMode)
        onMeshChanged?.invoke()
    }

    private fun applySelection(selection: Set<Int>, mode: SelectionMode?) {
        if (editSelection == null) return
        editSelection.clear()
        when (mode) {
            SelectionMode.VERTEX -> editSelection.selectedVertices.addAll(selection)
            SelectionMode.EDGE -> editSelection.selectedEdges.addAll(selection)
            SelectionMode.FACE -> editSelection.selectedFaces.addAll(selection)
            null -> editSelection.selectedFaces.addAll(selection)
        }
    }
}
