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
import com.example.studio3d.core.scene.Material
import com.example.studio3d.core.scene.SceneGraph
import com.example.studio3d.core.scene.SceneNode

interface Command {
    val description: String
    fun execute()
    fun undo()
}

class TransformNodeCommand(
    private val sceneGraph: SceneGraph,
    private val nodeId: String,
    private val oldPos: Vector3,
    private val oldRot: Vector3,
    private val oldScale: Vector3,
    private val newPos: Vector3,
    private val newRot: Vector3,
    private val newScale: Vector3
) : Command {
    override val description: String = "Transform Object"

    override fun execute() {
        val node = sceneGraph.getNode(nodeId) ?: return
        node.position = newPos.copy()
        node.rotation = newRot.copy()
        node.scale = newScale.copy()
    }

    override fun undo() {
        val node = sceneGraph.getNode(nodeId) ?: return
        node.position = oldPos.copy()
        node.rotation = oldRot.copy()
        node.scale = oldScale.copy()
    }
}

class AddNodeCommand(
    private val sceneGraph: SceneGraph,
    private val node: SceneNode,
    private val parentId: String? = null
) : Command {
    override val description: String = "Add ${node.name}"

    override fun execute() {
        sceneGraph.addNode(node, parentId)
        sceneGraph.selectedNodeId = node.id
    }

    override fun undo() {
        sceneGraph.removeNode(node.id)
    }
}

class DeleteNodeCommand(
    private val sceneGraph: SceneGraph,
    private val node: SceneNode
) : Command {
    private val parentId = node.parentId
    override val description: String = "Delete ${node.name}"

    override fun execute() {
        sceneGraph.removeNode(node.id)
    }

    override fun undo() {
        sceneGraph.addNode(node, parentId)
        sceneGraph.selectedNodeId = node.id
    }
}

class DeleteSelectionElementCommand(
    private val sceneGraph: SceneGraph,
    private val nodeId: String,
    private val selectionModeLabel: String
) : Command {
    override val description: String = "Delete $selectionModeLabel"

    override fun execute() {
        sceneGraph.getNode(nodeId)?.let { _ ->
            // Sub-element selection deletion state
        }
    }

    override fun undo() {
        sceneGraph.getNode(nodeId)?.let { _ ->
            // Restore sub-element state
        }
    }
}

class GroupNodesCommand(
    private val sceneGraph: SceneGraph,
    private val nodeIds: List<String>,
    private val groupName: String = "Group"
) : Command {
    private var createdGroup: SceneNode? = null
    private val previousParents = mutableMapOf<String, String?>()
    override val description: String = "Group ${nodeIds.size} Objects"

    override fun execute() {
        for (id in nodeIds) {
            previousParents[id] = sceneGraph.getNode(id)?.parentId
        }
        createdGroup = sceneGraph.groupSelected(nodeIds, groupName)
    }

    override fun undo() {
        val group = createdGroup ?: return
        // Reparent children back
        for (childId in ArrayList(group.childrenIds)) {
            val oldParent = previousParents[childId]
            sceneGraph.reparent(childId, oldParent)
        }
        sceneGraph.removeNode(group.id)
    }
}

class ChangeMaterialCommand(
    private val sceneGraph: SceneGraph,
    private val nodeId: String,
    private val oldMaterial: Material,
    private val newMaterial: Material
) : Command {
    override val description: String = "Change Material"

    override fun execute() {
        sceneGraph.getNode(nodeId)?.material = newMaterial.copy()
    }

    override fun undo() {
        sceneGraph.getNode(nodeId)?.material = oldMaterial.copy()
    }
}
