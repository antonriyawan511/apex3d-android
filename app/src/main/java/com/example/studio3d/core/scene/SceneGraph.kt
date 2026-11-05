package com.example.studio3d.core.scene

import com.example.studio3d.core.math.*
import com.example.studio3d.core.engine.*
import com.example.studio3d.core.scene.*
import com.example.studio3d.core.history.*
import com.example.studio3d.core.preferences.*
import com.example.studio3d.core.project.*
import com.example.studio3d.feature.editor.viewport.*
import com.example.studio3d.feature.editor.tools.select.*


import com.example.studio3d.core.math.BoundingBox
import com.example.studio3d.core.math.Matrix4
import com.example.studio3d.core.math.Ray
import com.example.studio3d.core.math.Vector3
import java.util.UUID

class SceneGraph {
    val nodes = LinkedHashMap<String, SceneNode>()
    var selectedNodeId: String? = null

    fun addNode(node: SceneNode, parentId: String? = null) {
        node.parentId = parentId
        node.ensureMesh()
        nodes[node.id] = node

        if (parentId != null) {
            val parent = nodes[parentId]
            if (parent != null && !parent.childrenIds.contains(node.id)) {
                parent.childrenIds.add(node.id)
            }
        }
    }

    fun removeNode(id: String): SceneNode? {
        val node = nodes[id] ?: return null
        // Also remove children
        val childrenCopy = ArrayList(node.childrenIds)
        for (childId in childrenCopy) {
            removeNode(childId)
        }

        // Remove from parent
        node.parentId?.let { pId ->
            nodes[pId]?.childrenIds?.remove(id)
        }

        if (selectedNodeId == id) {
            selectedNodeId = null
        }

        return nodes.remove(id)
    }

    fun duplicateNode(id: String): SceneNode? {
        val original = nodes[id] ?: return null
        val clone = original.deepCopy()
        clone.position = clone.position + Vector3(0.5f, 0f, 0.5f)
        addNode(clone, original.parentId)
        selectedNodeId = clone.id
        return clone
    }

    fun groupSelected(ids: List<String>, groupName: String = "Group"): SceneNode {
        val group = SceneNode(
            id = UUID.randomUUID().toString(),
            name = groupName,
            meshType = "group"
        )
        // Compute center of grouped objects
        var sumX = 0f
        var sumY = 0f
        var sumZ = 0f
        var count = 0
        for (id in ids) {
            val n = nodes[id] ?: continue
            sumX += n.position.x
            sumY += n.position.y
            sumZ += n.position.z
            count++
        }
        if (count > 0) {
            group.position = Vector3(sumX / count, sumY / count, sumZ / count)
        }

        addNode(group)

        for (id in ids) {
            val child = nodes[id] ?: continue
            // Adjust child local position relative to group
            child.position = child.position - group.position
            reparent(id, group.id)
        }

        selectedNodeId = group.id
        return group
    }

    fun reparent(nodeId: String, newParentId: String?) {
        val node = nodes[nodeId] ?: return
        if (nodeId == newParentId) return // Cannot parent to self

        // Remove from old parent
        node.parentId?.let { oldPId ->
            nodes[oldPId]?.childrenIds?.remove(nodeId)
        }

        node.parentId = newParentId
        if (newParentId != null) {
            nodes[newParentId]?.let { newParent ->
                if (!newParent.childrenIds.contains(nodeId)) {
                    newParent.childrenIds.add(nodeId)
                }
            }
        }
    }

    fun getNode(id: String?): SceneNode? = if (id != null) nodes[id] else null

    fun getSelectedNode(): SceneNode? = selectedNodeId?.let { nodes[it] }

    fun getRootNodes(): List<SceneNode> = nodes.values.filter { it.parentId == null }

    /**
     * Casts a ray through the scene and returns the closest hit node using
     * precise mesh triangle intersection in local space with padded AABB culling.
     */
    fun raycast(ray: Ray): SceneNode? {
        var closestDist = Float.MAX_VALUE
        var hitNode: SceneNode? = null

        for (node in nodes.values) {
            if (!node.isVisible || node.meshType == "group") continue
            node.ensureMesh()
            val mesh = node.mesh ?: continue
            val worldMatrix = node.getWorldTransformMatrix(nodes)
            val invWorld = Matrix4().copyFrom(worldMatrix).invert()
            val localOrigin = invWorld.transformPoint(ray.origin)
            val localDir = invWorld.transformDirection(ray.direction).normalized()
            val localRay = Ray(localOrigin, localDir)

            // 1. Fast local AABB cull (padded to handle zero-thickness geometry such as planes)
            val rawBox = mesh.boundingBox
            val pad = 0.08f
            val paddedAabb = BoundingBox(
                Vector3(rawBox.min.x - pad, rawBox.min.y - pad, rawBox.min.z - pad),
                Vector3(rawBox.max.x + pad, rawBox.max.y + pad, rawBox.max.z + pad)
            )
            val aabbDist = localRay.intersectAABB(paddedAabb) ?: continue

            // 2. Precise triangle intersection across all faces
            val topology = mesh.topology
            var nodeHitDist: Float? = null

            if (topology.faceCount > 0) {
                var closestTriDist = Float.MAX_VALUE
                for (f in 0 until topology.faceCount) {
                    val (i0, i1, i2) = topology.getFaceIndices(f)
                    val v0 = topology.getVertex(i0)
                    val v1 = topology.getVertex(i1)
                    val v2 = topology.getVertex(i2)
                    val t = localRay.intersectTriangle(v0, v1, v2, backfaceCull = false)
                    if (t != null && t < closestTriDist) {
                        closestTriDist = t
                    }
                }
                if (closestTriDist < Float.MAX_VALUE) {
                    val hitLocal = localRay.getPoint(closestTriDist)
                    val hitWorld = worldMatrix.transformPoint(hitLocal)
                    nodeHitDist = (hitWorld - ray.origin).length()
                }
            } else {
                // Fallback for custom or procedural meshes without indexed topology faces
                val hitLocal = localRay.getPoint(aabbDist)
                val hitWorld = worldMatrix.transformPoint(hitLocal)
                nodeHitDist = (hitWorld - ray.origin).length()
            }

            if (nodeHitDist != null && nodeHitDist < closestDist) {
                closestDist = nodeHitDist
                hitNode = node
            }
        }

        return hitNode
    }

    fun clear() {
        nodes.clear()
        selectedNodeId = null
    }
}
