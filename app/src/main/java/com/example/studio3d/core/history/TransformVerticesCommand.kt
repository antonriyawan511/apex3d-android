package com.example.studio3d.core.history

import com.example.studio3d.core.math.*
import com.example.studio3d.core.engine.*
import com.example.studio3d.core.scene.*
import com.example.studio3d.core.history.*
import com.example.studio3d.core.preferences.*
import com.example.studio3d.core.project.*
import com.example.studio3d.feature.editor.viewport.*
import com.example.studio3d.feature.editor.tools.select.*


import com.example.studio3d.core.math.Vector3
import com.example.studio3d.core.scene.SceneGraph

class TransformVerticesCommand(
    val nodeId: String,
    val sceneGraph: SceneGraph,
    val oldPositions: Map<Int, Vector3>,
    val newPositions: Map<Int, Vector3>
) : Command {
    override val description: String = "Transform Vertices"

    override fun execute() {
        applyPositions(newPositions)
    }

    override fun undo() {
        applyPositions(oldPositions)
    }

    private fun applyPositions(positions: Map<Int, Vector3>) {
        val node = sceneGraph.getNode(nodeId) ?: return
        val mesh = node.mesh ?: return
        for ((idx, pos) in positions) {
            mesh.setVertex(idx, pos)
        }
        mesh.recalculateNormalsAndBounds()
    }
}
