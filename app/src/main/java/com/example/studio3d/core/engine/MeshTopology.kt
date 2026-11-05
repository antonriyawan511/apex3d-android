package com.example.studio3d.core.engine

import com.example.studio3d.core.math.*
import com.example.studio3d.core.engine.*
import com.example.studio3d.core.scene.*
import com.example.studio3d.core.history.*
import com.example.studio3d.core.preferences.*
import com.example.studio3d.core.project.*
import com.example.studio3d.feature.editor.viewport.*
import com.example.studio3d.feature.editor.tools.select.*


import com.example.studio3d.core.math.Vector3

/**
 * Topology representation of a 3D polygonal mesh for Edit Mode.
 * Extracts unique vertices, manifold edges, and triangle faces.
 */
class MeshTopology(val mesh: Mesh) {

    val vertexCount: Int = mesh.vertices.size / 3

    val faceCount: Int = mesh.indices.size / 3

    // Spatial hash grouping for coincident vertices (e.g. vertices sharing identical 3D positions across face boundaries)
    val coincidentMap: IntArray by lazy {
        val count = vertexCount
        val map = IntArray(count) { it }
        val spatialMap = HashMap<Long, MutableList<Int>>()

        for (i in 0 until count) {
            val v = getVertex(i)
            val qx = (v.x * 2000f).toInt().toLong()
            val qy = (v.y * 2000f).toInt().toLong()
            val qz = (v.z * 2000f).toInt().toLong()
            val hash = (qx * 73856093L) xor (qy * 19349663L) xor (qz * 83492791L)

            val existing = spatialMap[hash]
            if (existing != null) {
                var found = -1
                for (otherIdx in existing) {
                    val otherV = getVertex(otherIdx)
                    val dx = v.x - otherV.x
                    val dy = v.y - otherV.y
                    val dz = v.z - otherV.z
                    if (dx * dx + dy * dy + dz * dz <= 0.000025f) { // <= 5mm tolerance
                        found = map[otherIdx]
                        break
                    }
                }
                if (found != -1) {
                    map[i] = found
                } else {
                    existing.add(i)
                }
            } else {
                spatialMap[hash] = mutableListOf(i)
            }
        }
        map
    }

    val coincidentClusters: Map<Int, List<Int>> by lazy {
        val clusters = HashMap<Int, MutableList<Int>>()
        for (i in 0 until vertexCount) {
            val root = coincidentMap[i]
            clusters.getOrPut(root) { mutableListOf() }.add(i)
        }
        clusters
    }

    fun getCoincidentVertices(vertexIndex: Int): List<Int> {
        if (vertexIndex !in 0 until vertexCount) return emptyList()
        val root = coincidentMap[vertexIndex]
        return coincidentClusters[root] ?: listOf(vertexIndex)
    }

    // Unique edges: List of Pair(minVertexIndex, maxVertexIndex)
    val edges: List<Pair<Int, Int>> by lazy {
        val edgeSet = LinkedHashSet<Pair<Int, Int>>()
        val wire = mesh.wireframeIndices
        if (wire.isNotEmpty()) {
            for (i in 0 until wire.size step 2) {
                val v0 = wire[i]
                val v1 = wire[i + 1]
                edgeSet.add(Pair(minOf(v0, v1), maxOf(v0, v1)))
            }
        } else {
            for (i in 0 until mesh.indices.size step 3) {
                val v0 = mesh.indices[i]
                val v1 = mesh.indices[i + 1]
                val v2 = mesh.indices[i + 2]
                edgeSet.add(Pair(minOf(v0, v1), maxOf(v0, v1)))
                edgeSet.add(Pair(minOf(v1, v2), maxOf(v1, v2)))
                edgeSet.add(Pair(minOf(v2, v0), maxOf(v2, v0)))
            }
        }
        edgeSet.toList()
    }

    fun getVertex(index: Int): Vector3 {
        val offset = index * 3
        if (offset + 2 >= mesh.vertices.size) return Vector3.ZERO
        return Vector3(
            mesh.vertices[offset],
            mesh.vertices[offset + 1],
            mesh.vertices[offset + 2]
        )
    }

    fun getFaceIndices(faceIndex: Int): Triple<Int, Int, Int> {
        val offset = faceIndex * 3
        if (offset + 2 >= mesh.indices.size) return Triple(0, 0, 0)
        return Triple(
            mesh.indices[offset],
            mesh.indices[offset + 1],
            mesh.indices[offset + 2]
        )
    }

    fun getFaceNormal(faceIndex: Int): Vector3 {
        val (i0, i1, i2) = getFaceIndices(faceIndex)
        val v0 = getVertex(i0)
        val v1 = getVertex(i1)
        val v2 = getVertex(i2)
        val edge1 = v1 - v0
        val edge2 = v2 - v0
        return edge1.cross(edge2).normalized()
    }

    fun getFaceCenter(faceIndex: Int): Vector3 {
        val (i0, i1, i2) = getFaceIndices(faceIndex)
        val v0 = getVertex(i0)
        val v1 = getVertex(i1)
        val v2 = getVertex(i2)
        return (v0 + v1 + v2) / 3f
    }

    // Wireframe edge lookup set for distinguishing real polygon boundary edges from internal triangulation diagonals
    private val wireframeEdgeSet: Set<Pair<Int, Int>> by lazy {
        val set = HashSet<Pair<Int, Int>>()
        val wire = mesh.wireframeIndices
        if (wire.isNotEmpty()) {
            for (i in 0 until wire.size step 2) {
                val r0 = coincidentMap[wire[i]]
                val r1 = coincidentMap[wire[i + 1]]
                set.add(Pair(minOf(r0, r1), maxOf(r0, r1)))
            }
        }
        set
    }

    /**
     * Finds all triangle faces that belong to the same polygon (e.g. quad face on a cube).
     * If two triangles share an edge that is NOT a wireframe boundary, they are part of the same quad,
     * regardless of non-planar vertex deformation!
     */
    fun getCoplanarConnectedFaces(faceIndex: Int): List<Int> {
        if (faceIndex !in 0 until faceCount) return emptyList()

        val baseNormal = getFaceNormal(faceIndex)
        val visited = LinkedHashSet<Int>()
        val queue = ArrayDeque<Int>()

        visited.add(faceIndex)
        queue.add(faceIndex)

        val faceRoots = Array(faceCount) { f ->
            val (i0, i1, i2) = getFaceIndices(f)
            intArrayOf(coincidentMap[i0], coincidentMap[i1], coincidentMap[i2])
        }

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val cRoots = faceRoots[current]

            for (other in 0 until faceCount) {
                if (other in visited) continue
                val oRoots = faceRoots[other]

                var sharedCount = 0
                var s0 = -1
                var s1 = -1
                for (cr in cRoots) {
                    for (or in oRoots) {
                        if (cr == or) {
                            if (sharedCount == 0) s0 = cr
                            else if (sharedCount == 1) s1 = cr
                            sharedCount++
                            break
                        }
                    }
                }

                if (sharedCount == 2) {
                    val sharedEdge = Pair(minOf(s0, s1), maxOf(s0, s1))
                    val isInternalEdge = wireframeEdgeSet.isEmpty() || !wireframeEdgeSet.contains(sharedEdge)
                    if (isInternalEdge) {
                        val otherNormal = getFaceNormal(other)
                        if (baseNormal.dot(otherNormal) > 0.95f) {
                            visited.add(other)
                            queue.add(other)
                        }
                    }
                }
            }
        }
        return visited.toList()
    }
}
