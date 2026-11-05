package com.example.studio3d.core.scene

import com.example.studio3d.core.math.*
import com.example.studio3d.core.engine.*
import com.example.studio3d.core.scene.*
import com.example.studio3d.core.history.*
import com.example.studio3d.core.preferences.*
import com.example.studio3d.core.project.*
import com.example.studio3d.feature.editor.viewport.*
import com.example.studio3d.feature.editor.tools.select.*


import com.example.studio3d.core.engine.Mesh
import com.example.studio3d.core.engine.MeshFactory
import com.example.studio3d.core.math.BoundingBox
import com.example.studio3d.core.math.Matrix4
import com.example.studio3d.core.math.Vector3
import java.util.UUID

data class SceneNode(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "Object",
    var position: Vector3 = Vector3(0f, 0f, 0f),
    var rotation: Vector3 = Vector3(0f, 0f, 0f), // in degrees
    var scale: Vector3 = Vector3(1f, 1f, 1f),
    var meshType: String = "cube",
    @Transient var mesh: Mesh? = null,
    var material: Material = Material(),
    var isVisible: Boolean = true,
    var isLocked: Boolean = false,
    var parentId: String? = null,
    val childrenIds: MutableList<String> = mutableListOf()
) {
    fun ensureMesh() {
        if (mesh != null) return
        mesh = when (meshType) {
            "cube" -> MeshFactory.createCube()
            "plane" -> MeshFactory.createPlane()
            "cylinder" -> MeshFactory.createCylinder()
            "cone" -> MeshFactory.createCone()
            "sphere" -> MeshFactory.createSphere()
            "torus" -> MeshFactory.createTorus()
            "wedge" -> MeshFactory.createWedge()
            "plate", "dish" -> MeshFactory.createPlate()
            "group" -> null
            else -> MeshFactory.createCube()
        }
    }

    fun getLocalTransformMatrix(): Matrix4 {
        return Matrix4.createTRS(position, rotation, scale)
    }

    fun getWorldTransformMatrix(allNodes: Map<String, SceneNode>): Matrix4 {
        val parent = parentId?.let { allNodes[it] }
        val local = getLocalTransformMatrix()
        return if (parent != null) {
            val parentWorld = parent.getWorldTransformMatrix(allNodes)
            Matrix4().copyFrom(parentWorld).multiply(local)
        } else {
            local
        }
    }

    fun getWorldBoundingBox(allNodes: Map<String, SceneNode>): BoundingBox {
        ensureMesh()
        val m = mesh ?: return BoundingBox(position - Vector3(0.5f, 0.5f, 0.5f), position + Vector3(0.5f, 0.5f, 0.5f))
        val worldMat = getWorldTransformMatrix(allNodes)
        return m.boundingBox.transform(worldMat)
    }

    fun deepCopy(): SceneNode {
        return SceneNode(
            id = UUID.randomUUID().toString(),
            name = "$name Copy",
            position = position.copy(),
            rotation = rotation.copy(),
            scale = scale.copy(),
            meshType = meshType,
            mesh = null,
            material = material.copy(),
            isVisible = isVisible,
            isLocked = isLocked,
            parentId = parentId,
            childrenIds = mutableListOf()
        ).also { it.ensureMesh() }
    }
}
