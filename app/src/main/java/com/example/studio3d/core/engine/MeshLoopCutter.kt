package com.example.studio3d.core.engine

import com.example.studio3d.core.math.BoundingBox
import com.example.studio3d.core.math.Vector3

/**
 * Loop Cut & Slide Tool for Apex 3D Studio.
 * Slices through quad loops and polygonal faces, inserting an edge loop
 * at a given normalized distance factor (default: 0.5 = midpoint).
 */
object MeshLoopCutter {

    fun loopCut(
        sourceMesh: Mesh,
        selectedEdgeIndices: Set<Int> = emptySet(),
        factor: Float = 0.5f
    ): Pair<Mesh, Set<Int>>? {
        val topology = sourceMesh.topology
        val totalFaces = topology.faceCount
        if (totalFaces == 0) return null

        val clampedFactor = factor.coerceIn(0.05f, 0.95f)

        val tempVertices = ArrayList<Float>()
        for (v in sourceMesh.vertices) tempVertices.add(v)

        val tempUvs = ArrayList<Float>()
        val hasUvs = sourceMesh.uvs.isNotEmpty()
        if (hasUvs) {
            for (u in sourceMesh.uvs) tempUvs.add(u)
        }

        // Cache for cut points along original edges: Pair(minRoot, maxRoot) -> newVertexIdx
        val cutPointMap = HashMap<Pair<Int, Int>, Int>()

        fun getOrCreateCutPoint(vA: Int, vB: Int, t: Float): Int {
            val rA = topology.coincidentMap[vA]
            val rB = topology.coincidentMap[vB]
            val key = Pair(minOf(rA, rB), maxOf(rA, rB))
            val existing = cutPointMap[key]
            if (existing != null) return existing

            val actualT = if (rA <= rB) t else (1f - t)
            val oA = vA * 3; val oB = vB * 3
            val px = sourceMesh.vertices[oA] * (1f - actualT) + sourceMesh.vertices[oB] * actualT
            val py = sourceMesh.vertices[oA + 1] * (1f - actualT) + sourceMesh.vertices[oB + 1] * actualT
            val pz = sourceMesh.vertices[oA + 2] * (1f - actualT) + sourceMesh.vertices[oB + 2] * actualT

            val newIdx = tempVertices.size / 3
            tempVertices.add(px); tempVertices.add(py); tempVertices.add(pz)

            if (hasUvs) {
                val uA = if (vA * 2 < sourceMesh.uvs.size) sourceMesh.uvs[vA * 2] else 0f
                val vA_uv = if (vA * 2 + 1 < sourceMesh.uvs.size) sourceMesh.uvs[vA * 2 + 1] else 0f
                val uB = if (vB * 2 < sourceMesh.uvs.size) sourceMesh.uvs[vB * 2] else 0f
                val vB_uv = if (vB * 2 + 1 < sourceMesh.uvs.size) sourceMesh.uvs[vB * 2 + 1] else 0f
                tempUvs.add(uA * (1f - actualT) + uB * actualT)
                tempUvs.add(vA_uv * (1f - actualT) + vB_uv * actualT)
            }

            cutPointMap[key] = newIdx
            return newIdx
        }

        // Detect Quad pairs (coplanar triangle pairs)
        data class QuadFace(val fA: Int, val fB: Int, val v0: Int, val v1: Int, val v2: Int, val v3: Int)
        val quadList = ArrayList<QuadFace>()
        val processedFaces = HashSet<Int>()

        for (f in 0 until totalFaces) {
            if (f in processedFaces) continue
            val coplanar = topology.getCoplanarConnectedFaces(f)
            if (coplanar.size == 2) {
                val fA = coplanar[0]
                val fB = coplanar[1]
                val (a0, a1, a2) = topology.getFaceIndices(fA)
                val (b0, b1, b2) = topology.getFaceIndices(fB)
                val aRoots = listOf(topology.coincidentMap[a0], topology.coincidentMap[a1], topology.coincidentMap[a2])
                val bRoots = listOf(topology.coincidentMap[b0], topology.coincidentMap[b1], topology.coincidentMap[b2])
                val sharedRoots = aRoots.intersect(bRoots.toSet())

                if (sharedRoots.size == 2) {
                    val unsharedA = listOf(a0, a1, a2).first { topology.coincidentMap[it] !in sharedRoots }
                    val unsharedB = listOf(b0, b1, b2).first { topology.coincidentMap[it] !in sharedRoots }
                    val sharedV = listOf(a0, a1, a2).filter { topology.coincidentMap[it] in sharedRoots }
                    val vShared0 = sharedV[0]
                    val vShared1 = sharedV[1]

                    quadList.add(QuadFace(fA, fB, unsharedA, vShared0, unsharedB, vShared1))
                    processedFaces.add(fA)
                    processedFaces.add(fB)
                }
            }
        }

        val newIndices = ArrayList<Int>()
        val newWireEdges = ArrayList<Pair<Int, Int>>()

        // Cut each quad into two quads across the middle
        for (q in quadList) {
            // Cut edges (v0, v1) and (v3, v2)
            val mA = getOrCreateCutPoint(q.v0, q.v1, clampedFactor)
            val mB = getOrCreateCutPoint(q.v3, q.v2, clampedFactor)

            // New Quad 1: (v0, mA, mB, v3) -> 2 triangles
            newIndices.add(q.v0); newIndices.add(mA); newIndices.add(mB)
            newIndices.add(q.v0); newIndices.add(mB); newIndices.add(q.v3)

            // New Quad 2: (mA, v1, v2, mB) -> 2 triangles
            newIndices.add(mA); newIndices.add(q.v1); newIndices.add(q.v2)
            newIndices.add(mA); newIndices.add(q.v2); newIndices.add(mB)

            newWireEdges.add(Pair(minOf(mA, mB), maxOf(mA, mB)))
        }

        // Copy remaining single triangles (bisecting if they share cut points)
        for (f in 0 until totalFaces) {
            if (f in processedFaces) continue
            val (i0, i1, i2) = topology.getFaceIndices(f)
            val r0 = topology.coincidentMap[i0]
            val r1 = topology.coincidentMap[i1]
            val r2 = topology.coincidentMap[i2]

            val cut01 = cutPointMap[Pair(minOf(r0, r1), maxOf(r0, r1))]
            val cut12 = cutPointMap[Pair(minOf(r1, r2), maxOf(r2, r1))]
            val cut20 = cutPointMap[Pair(minOf(r2, r0), maxOf(r0, r2))]

            when {
                cut01 != null && cut12 == null && cut20 == null -> {
                    newIndices.add(i0); newIndices.add(cut01); newIndices.add(i2)
                    newIndices.add(cut01); newIndices.add(i1); newIndices.add(i2)
                }
                cut12 != null && cut01 == null && cut20 == null -> {
                    newIndices.add(i0); newIndices.add(i1); newIndices.add(cut12)
                    newIndices.add(i0); newIndices.add(cut12); newIndices.add(i2)
                }
                cut20 != null && cut01 == null && cut12 == null -> {
                    newIndices.add(i0); newIndices.add(i1); newIndices.add(cut20)
                    newIndices.add(cut20); newIndices.add(i1); newIndices.add(i2)
                }
                else -> {
                    newIndices.add(i0); newIndices.add(i1); newIndices.add(i2)
                }
            }
        }

        val finalVertices = tempVertices.toFloatArray()
        val finalIndices = newIndices.toIntArray()
        val finalNormals = FloatArray(finalVertices.size)

        // Compute normals
        for (i in finalIndices.indices step 3) {
            val idx0 = finalIndices[i]; val idx1 = finalIndices[i + 1]; val idx2 = finalIndices[i + 2]
            val v0 = Vector3(finalVertices[idx0 * 3], finalVertices[idx0 * 3 + 1], finalVertices[idx0 * 3 + 2])
            val v1 = Vector3(finalVertices[idx1 * 3], finalVertices[idx1 * 3 + 1], finalVertices[idx1 * 3 + 2])
            val v2 = Vector3(finalVertices[idx2 * 3], finalVertices[idx2 * 3 + 1], finalVertices[idx2 * 3 + 2])
            val fn = (v1 - v0).cross(v2 - v0).normalized()

            for (idx in listOf(idx0, idx1, idx2)) {
                finalNormals[idx * 3] += fn.x
                finalNormals[idx * 3 + 1] += fn.y
                finalNormals[idx * 3 + 2] += fn.z
            }
        }

        for (i in 0 until finalVertices.size / 3) {
            val nx = finalNormals[i * 3]; val ny = finalNormals[i * 3 + 1]; val nz = finalNormals[i * 3 + 2]
            val lenSq = nx * nx + ny * ny + nz * nz
            if (lenSq > 0.00001f) {
                val invLen = 1f / kotlin.math.sqrt(lenSq)
                finalNormals[i * 3] *= invLen
                finalNormals[i * 3 + 1] *= invLen
                finalNormals[i * 3 + 2] *= invLen
            } else {
                finalNormals[i * 3 + 1] = 1f
            }
        }

        val finalWireframe = MeshModifier.buildCleanWireframe(finalVertices, finalIndices)

        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        for (i in 0 until finalVertices.size / 3) {
            val vx = finalVertices[i * 3]; val vy = finalVertices[i * 3 + 1]; val vz = finalVertices[i * 3 + 2]
            if (vx < minX) minX = vx; if (vx > maxX) maxX = vx
            if (vy < minY) minY = vy; if (vy > maxY) maxY = vy
            if (vz < minZ) minZ = vz; if (vz > maxZ) maxZ = vz
        }

        val newMesh = Mesh(
            vertices = finalVertices,
            normals = finalNormals,
            uvs = if (hasUvs) tempUvs.toFloatArray() else FloatArray(0),
            indices = finalIndices,
            wireframeIndices = finalWireframe,
            boundingBox = BoundingBox(Vector3(minX, minY, minZ), Vector3(maxX, maxY, maxZ))
        )

        val newTopology = newMesh.topology
        val newEdgeIndices = HashSet<Int>()
        for (e in newWireEdges) {
            val idx = newTopology.edges.indexOfFirst {
                val r0 = newTopology.coincidentMap[it.first]
                val r1 = newTopology.coincidentMap[it.second]
                val k0 = newTopology.coincidentMap[e.first]
                val k1 = newTopology.coincidentMap[e.second]
                (r0 == k0 && r1 == k1) || (r0 == k1 && r1 == k0)
            }
            if (idx != -1) newEdgeIndices.add(idx)
        }

        return Pair(newMesh, newEdgeIndices)
    }
}
