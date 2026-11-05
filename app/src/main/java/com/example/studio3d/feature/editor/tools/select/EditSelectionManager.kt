package com.example.studio3d.feature.editor.tools.select

import com.example.studio3d.core.math.*
import com.example.studio3d.core.engine.*
import com.example.studio3d.core.scene.*
import com.example.studio3d.core.history.*
import com.example.studio3d.core.preferences.*
import com.example.studio3d.core.project.*
import com.example.studio3d.feature.editor.bar.*
import com.example.studio3d.feature.editor.panel.*
import com.example.studio3d.feature.editor.viewport.*
import com.example.studio3d.feature.editor.screen.*
import com.example.studio3d.feature.editor.tools.select.*


import com.example.studio3d.core.math.Matrix4
import com.example.studio3d.core.math.Ray
import com.example.studio3d.core.math.Vector3
import com.example.studio3d.core.scene.SceneNode
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Manages mesh component selection (Vertices, Edges, Faces) in Edit Mode,
 * and handles screen-touch picking with depth and geometric visibility.
 */
class EditSelectionManager {

    val selectedVertices = LinkedHashSet<Int>()
    val selectedEdges = LinkedHashSet<Int>()
    val selectedFaces = LinkedHashSet<Int>()

    fun getFacesSnapshot(): List<Int> = synchronized(this) { selectedFaces.toList() }
    fun getEdgesSnapshot(): List<Int> = synchronized(this) { selectedEdges.toList() }
    fun getVerticesSnapshot(): List<Int> = synchronized(this) { selectedVertices.toList() }

    fun setFaces(faces: Collection<Int>) = synchronized(this) {
        selectedFaces.clear()
        selectedFaces.addAll(faces)
    }

    fun addFaces(faces: Collection<Int>) = synchronized(this) {
        selectedFaces.addAll(faces)
    }

    fun setEdges(edges: Collection<Int>) = synchronized(this) {
        selectedEdges.clear()
        selectedEdges.addAll(edges)
    }

    fun addEdges(edges: Collection<Int>) = synchronized(this) {
        selectedEdges.addAll(edges)
    }

    fun setVertices(vertices: Collection<Int>) = synchronized(this) {
        selectedVertices.clear()
        selectedVertices.addAll(vertices)
    }

    fun addVertices(vertices: Collection<Int>) = synchronized(this) {
        selectedVertices.addAll(vertices)
    }

    fun hasSelection(mode: com.example.studio3d.feature.editor.screen.SelectionMode? = null): Boolean = synchronized(this) {
        when (mode) {
            com.example.studio3d.feature.editor.screen.SelectionMode.VERTEX -> selectedVertices.isNotEmpty()
            com.example.studio3d.feature.editor.screen.SelectionMode.EDGE -> selectedEdges.isNotEmpty()
            com.example.studio3d.feature.editor.screen.SelectionMode.FACE -> selectedFaces.isNotEmpty()
            null -> selectedVertices.isNotEmpty() || selectedEdges.isNotEmpty() || selectedFaces.isNotEmpty()
        }
    }

    fun clear() = synchronized(this) {
        selectedVertices.clear()
        selectedEdges.clear()
        selectedFaces.clear()
    }

    fun toggleVertex(index: Int, topology: MeshTopology? = null) = synchronized(this) {
        if (topology != null) {
            val cluster = topology.getCoincidentVertices(index)
            val isAlreadySelected = cluster.any { selectedVertices.contains(it) }
            if (isAlreadySelected) {
                selectedVertices.removeAll(cluster.toSet())
            } else {
                selectedVertices.addAll(cluster)
            }
        } else {
            if (!selectedVertices.add(index)) {
                selectedVertices.remove(index)
            }
        }
    }

    /**
     * Resolves all unique vertex indices associated with currently selected elements (vertices, edges, faces).
     * When mode is provided, prioritizes elements belonging strictly to that selection mode.
     */
    fun getEffectiveSelectedVertices(mesh: Mesh, mode: com.example.studio3d.feature.editor.screen.SelectionMode? = null): Set<Int> {
        val result = LinkedHashSet<Int>()
        val topology = mesh.topology

        val facesSnapshot = getFacesSnapshot()
        val edgesSnapshot = getEdgesSnapshot()
        val verticesSnapshot = getVerticesSnapshot()

        when (mode) {
            com.example.studio3d.feature.editor.screen.SelectionMode.FACE -> {
                if (facesSnapshot.isNotEmpty()) {
                    for (f in facesSnapshot) {
                        if (f in 0 until topology.faceCount) {
                            val (i0, i1, i2) = topology.getFaceIndices(f)
                            result.add(i0)
                            result.addAll(topology.getCoincidentVertices(i0))
                            result.add(i1)
                            result.addAll(topology.getCoincidentVertices(i1))
                            result.add(i2)
                            result.addAll(topology.getCoincidentVertices(i2))
                        }
                    }
                    return result
                }
            }
            com.example.studio3d.feature.editor.screen.SelectionMode.EDGE -> {
                if (edgesSnapshot.isNotEmpty()) {
                    val edges = topology.edges
                    for (e in edgesSnapshot) {
                        if (e in edges.indices) {
                            val (v0, v1) = edges[e]
                            result.add(v0)
                            result.addAll(topology.getCoincidentVertices(v0))
                            result.add(v1)
                            result.addAll(topology.getCoincidentVertices(v1))
                        }
                    }
                    return result
                }
            }
            com.example.studio3d.feature.editor.screen.SelectionMode.VERTEX -> {
                if (verticesSnapshot.isNotEmpty()) {
                    for (v in verticesSnapshot) {
                        result.add(v)
                        result.addAll(topology.getCoincidentVertices(v))
                    }
                    return result
                }
            }
            null -> {
                if (facesSnapshot.isNotEmpty() && edgesSnapshot.isEmpty() && verticesSnapshot.isEmpty()) {
                    return getEffectiveSelectedVertices(mesh, com.example.studio3d.feature.editor.screen.SelectionMode.FACE)
                }
                if (edgesSnapshot.isNotEmpty() && facesSnapshot.isEmpty() && verticesSnapshot.isEmpty()) {
                    return getEffectiveSelectedVertices(mesh, com.example.studio3d.feature.editor.screen.SelectionMode.EDGE)
                }
                if (verticesSnapshot.isNotEmpty() && edgesSnapshot.isEmpty() && facesSnapshot.isEmpty()) {
                    return getEffectiveSelectedVertices(mesh, com.example.studio3d.feature.editor.screen.SelectionMode.VERTEX)
                }
            }
        }

        // Fallback: Union of all active selections
        for (v in verticesSnapshot) {
            result.add(v)
            result.addAll(topology.getCoincidentVertices(v))
        }

        val edges = topology.edges
        for (e in edgesSnapshot) {
            if (e in edges.indices) {
                val (v0, v1) = edges[e]
                result.add(v0)
                result.addAll(topology.getCoincidentVertices(v0))
                result.add(v1)
                result.addAll(topology.getCoincidentVertices(v1))
            }
        }

        for (f in facesSnapshot) {
            if (f in 0 until topology.faceCount) {
                val (i0, i1, i2) = topology.getFaceIndices(f)
                result.add(i0)
                result.addAll(topology.getCoincidentVertices(i0))
                result.add(i1)
                result.addAll(topology.getCoincidentVertices(i1))
                result.add(i2)
                result.addAll(topology.getCoincidentVertices(i2))
            }
        }

        return result
    }

    /**
     * Calculates the local 3D median center (pivot) of the selected components.
     * When mode is provided, calculates pivot strictly from that mode's selection.
     */
    fun calculateLocalPivot(mesh: Mesh, mode: com.example.studio3d.feature.editor.screen.SelectionMode? = null): Vector3 {
        val effectiveVertices = getEffectiveSelectedVertices(mesh, mode)
        if (effectiveVertices.isEmpty()) return Vector3.ZERO
        var sumX = 0.0
        var sumY = 0.0
        var sumZ = 0.0
        val topology = mesh.topology
        val uniqueSpatialRoots = HashSet<Int>()

        for (vIdx in effectiveVertices) {
            if (vIdx in 0 until topology.vertexCount) {
                val root = topology.coincidentMap[vIdx]
                if (uniqueSpatialRoots.add(root)) {
                    val v = mesh.getVertex(vIdx)
                    sumX += v.x
                    sumY += v.y
                    sumZ += v.z
                }
            }
        }

        val count = uniqueSpatialRoots.size
        if (count == 0) return Vector3.ZERO
        return Vector3((sumX / count).toFloat(), (sumY / count).toFloat(), (sumZ / count).toFloat())
    }

    fun calculateFacePivot(mesh: Mesh): Vector3 = calculateLocalPivot(mesh, com.example.studio3d.feature.editor.screen.SelectionMode.FACE)

    /**
     * Transforms all selected vertices relative to their pivot with Translation, Rotation, and Scale.
     */
    fun applyTransform(
        mesh: Mesh,
        initialPositions: Map<Int, Vector3>,
        localPivot: Vector3,
        deltaTranslation: Vector3,
        deltaRotationEuler: Vector3,
        scaleFactor: Vector3
    ) {
        val hasRotation = deltaRotationEuler.x != 0f || deltaRotationEuler.y != 0f || deltaRotationEuler.z != 0f
        val rotMat = if (hasRotation) {
            Matrix4.identity()
                .rotate(deltaRotationEuler.y, 0f, 1f, 0f)
                .rotate(deltaRotationEuler.x, 1f, 0f, 0f)
                .rotate(deltaRotationEuler.z, 0f, 0f, 1f)
        } else null

        for ((idx, origPos) in initialPositions) {
            val p0 = origPos - localPivot
            val p1 = Vector3(p0.x * scaleFactor.x, p0.y * scaleFactor.y, p0.z * scaleFactor.z)
            val p2 = if (rotMat != null) rotMat.transformVector(p1) else p1
            val newPos = localPivot + p2 + deltaTranslation
            mesh.setVertex(idx, newPos)
        }
    }

    fun toggleEdge(index: Int) = synchronized(this) {
        if (!selectedEdges.add(index)) {
            selectedEdges.remove(index)
        }
    }

    fun toggleFace(index: Int, topology: MeshTopology? = null) = synchronized(this) {
        if (topology != null) {
            val coplanarQuad = topology.getCoplanarConnectedFaces(index)
            val isAlreadySelected = coplanarQuad.any { selectedFaces.contains(it) }
            if (isAlreadySelected) {
                selectedFaces.removeAll(coplanarQuad.toSet())
            } else {
                selectedFaces.addAll(coplanarQuad)
            }
        } else {
            if (!selectedFaces.add(index)) {
                selectedFaces.remove(index)
            }
        }
    }

    /**
     * Picks the closest visible Face under the screen touch coordinate using raycast.
     */
    fun pickFace(
        screenX: Float,
        screenY: Float,
        node: SceneNode,
        camera: Camera,
        allNodes: Map<String, SceneNode>,
        isTransparent: Boolean
    ): Int? {
        val mesh = node.mesh ?: return null
        val topology = mesh.topology
        val ray = camera.screenPointToRay(screenX, screenY)

        val worldMatrix = node.getWorldTransformMatrix(allNodes)
        val invWorld = Matrix4().copyFrom(worldMatrix).invert()
        val localOrigin = invWorld.transformPoint(ray.origin)
        val localDir = invWorld.transformDirection(ray.direction).normalized()
        val localRay = Ray(localOrigin, localDir)

        var closestDist = Float.MAX_VALUE
        var closestFace: Int? = null

        for (f in 0 until topology.faceCount) {
            val (i0, i1, i2) = topology.getFaceIndices(f)
            val v0 = topology.getVertex(i0)
            val v1 = topology.getVertex(i1)
            val v2 = topology.getVertex(i2)

            val t = localRay.intersectTriangle(v0, v1, v2, backfaceCull = false)
            if (t != null && t < closestDist) {
                closestDist = t
                closestFace = f
            }
        }

        return closestFace
    }

    /**
     * Picks the closest visible Vertex under the screen touch within a pixel threshold.
     */
    fun pickVertex(
        screenX: Float,
        screenY: Float,
        node: SceneNode,
        camera: Camera,
        allNodes: Map<String, SceneNode>,
        isTransparent: Boolean,
        thresholdPx: Float = 36f
    ): Int? {
        val mesh = node.mesh ?: return null
        val topology = mesh.topology
        val worldMatrix = node.getWorldTransformMatrix(allNodes)
        val ray = camera.screenPointToRay(screenX, screenY)

        // Find closest surface hit distance for depth occlusion
        var minSurfaceDist = Float.MAX_VALUE
        val invWorld = Matrix4().copyFrom(worldMatrix).invert()
        val localOrigin = invWorld.transformPoint(ray.origin)
        val localDir = invWorld.transformDirection(ray.direction).normalized()
        val localRay = Ray(localOrigin, localDir)

        if (!isTransparent) {
            for (f in 0 until topology.faceCount) {
                val (i0, i1, i2) = topology.getFaceIndices(f)
                val t = localRay.intersectTriangle(
                    topology.getVertex(i0),
                    topology.getVertex(i1),
                    topology.getVertex(i2),
                    backfaceCull = false
                )
                if (t != null && t < minSurfaceDist) {
                    minSurfaceDist = t
                }
            }
        }

        var closestVertex: Int? = null
        var minPixelDist = thresholdPx

        for (v in 0 until topology.vertexCount) {
            val localPos = topology.getVertex(v)
            val worldPos = worldMatrix.transformPoint(localPos)
            val screenPos = camera.worldToScreen(worldPos)

            // Must be in front of the camera frustum
            if (screenPos.z <= 0f) continue

            // Distance in screen pixels
            val pixelDist = hypot(screenPos.x - screenX, screenPos.y - screenY)
            if (pixelDist <= minPixelDist) {
                if (!isTransparent && minSurfaceDist < Float.MAX_VALUE) {
                    // Check if vertex is occluded by front surfaces (depth check)
                    val vertexLocalDepth = (localPos - localRay.origin).dot(localRay.direction)
                    if (vertexLocalDepth > minSurfaceDist + 0.15f) {
                        continue // Occluded by front geometry
                    }
                }
                minPixelDist = pixelDist
                closestVertex = v
            }
        }

        return closestVertex
    }

    /**
     * Picks the closest visible Edge under the screen touch within a pixel threshold.
     */
    fun pickEdge(
        screenX: Float,
        screenY: Float,
        node: SceneNode,
        camera: Camera,
        allNodes: Map<String, SceneNode>,
        isTransparent: Boolean,
        thresholdPx: Float = 30f
    ): Int? {
        val mesh = node.mesh ?: return null
        val topology = mesh.topology
        val worldMatrix = node.getWorldTransformMatrix(allNodes)
        val ray = camera.screenPointToRay(screenX, screenY)

        val invWorld = Matrix4().copyFrom(worldMatrix).invert()
        val localOrigin = invWorld.transformPoint(ray.origin)
        val localDir = invWorld.transformDirection(ray.direction).normalized()
        val localRay = Ray(localOrigin, localDir)

        var minSurfaceDist = Float.MAX_VALUE
        if (!isTransparent) {
            for (f in 0 until topology.faceCount) {
                val (i0, i1, i2) = topology.getFaceIndices(f)
                val t = localRay.intersectTriangle(
                    topology.getVertex(i0),
                    topology.getVertex(i1),
                    topology.getVertex(i2),
                    backfaceCull = false
                )
                if (t != null && t < minSurfaceDist) {
                    minSurfaceDist = t
                }
            }
        }

        var closestEdge: Int? = null
        var minPixelDist = thresholdPx

        val edges = topology.edges
        for (e in edges.indices) {
            val (v0Idx, v1Idx) = edges[e]
            val v0Local = topology.getVertex(v0Idx)
            val v1Local = topology.getVertex(v1Idx)
            val w0 = worldMatrix.transformPoint(v0Local)
            val w1 = worldMatrix.transformPoint(v1Local)

            val s0 = camera.worldToScreen(w0)
            val s1 = camera.worldToScreen(w1)

            if (s0.z <= 0f && s1.z <= 0f) continue

            val pixelDist = distPointToSegment(screenX, screenY, s0.x, s0.y, s1.x, s1.y)
            if (pixelDist <= minPixelDist) {
                if (!isTransparent && minSurfaceDist < Float.MAX_VALUE) {
                    val midLocal = (v0Local + v1Local) * 0.5f
                    val edgeLocalDepth = (midLocal - localRay.origin).dot(localRay.direction)
                    if (edgeLocalDepth > minSurfaceDist + 0.15f) {
                        continue // Occluded by front geometry
                    }
                }
                minPixelDist = pixelDist
                closestEdge = e
            }
        }

        return closestEdge
    }

    private fun distPointToSegment(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val lenSq = dx * dx + dy * dy
        if (lenSq < 0.0001f) {
            val diffX = px - x1
            val diffY = py - y1
            return sqrt(diffX * diffX + diffY * diffY)
        }
        var t = ((px - x1) * dx + (py - y1) * dy) / lenSq
        t = t.coerceIn(0f, 1f)
        val projX = x1 + t * dx
        val projY = y1 + t * dy
        val diffX = px - projX
        val diffY = py - projY
        return sqrt(diffX * diffX + diffY * diffY)
    }
}
