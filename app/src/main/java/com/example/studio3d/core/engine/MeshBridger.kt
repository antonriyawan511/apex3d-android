package com.example.studio3d.core.engine

import com.example.studio3d.core.math.BoundingBox
import com.example.studio3d.core.math.Vector3

/**
 * Bridge Tool for Apex 3D Studio.
 * Connects two faces or edge loops by generating bridge quads between corresponding vertices.
 */
object MeshBridger {

    fun bridgeFaces(
        sourceMesh: Mesh,
        selectedFaceIndices: Set<Int>
    ): Pair<Mesh, Set<Int>>? {
        val topology = sourceMesh.topology
        val totalFaces = topology.faceCount
        if (selectedFaceIndices.size < 2) return null

        val faceList = selectedFaceIndices.filter { it in 0 until totalFaces }
        if (faceList.size < 2) return null

        val fA = faceList[0]
        val fB = faceList[1]

        val (a0, a1, a2) = topology.getFaceIndices(fA)
        val (b0, b1, b2) = topology.getFaceIndices(fB)

        val loopA = listOf(a0, a1, a2)
        val loopB = listOf(b0, b1, b2)

        val vA = loopA.map { topology.getVertex(it) }
        val vB = loopB.map { topology.getVertex(it) }

        // Find best cyclic offset k to minimize sum of squared distances
        var bestK = 0
        var minSqDist = Float.MAX_VALUE
        for (k in 0 until 3) {
            var sumDist = 0f
            for (i in 0 until 3) {
                sumDist += (vA[i] - vB[(i + k) % 3]).lengthSquared()
            }
            if (sumDist < minSqDist) {
                minSqDist = sumDist
                bestK = k
            }
        }

        val alignedB = listOf(loopB[bestK], loopB[(bestK + 1) % 3], loopB[(bestK + 2) % 3])

        val newIndices = ArrayList<Int>()
        val newBridgeFaces = HashSet<Int>()

        // 1. Copy all faces except the bridged faces fA and fB (hollow bridge)
        for (f in 0 until totalFaces) {
            if (f != fA && f != fB) {
                val (i0, i1, i2) = topology.getFaceIndices(f)
                newIndices.add(i0); newIndices.add(i1); newIndices.add(i2)
            }
        }

        // 2. Generate bridge quads connecting loopA and alignedB
        for (i in 0 until 3) {
            val nextI = (i + 1) % 3
            val vA0 = loopA[i]
            val vA1 = loopA[nextI]
            val vB0 = alignedB[i]
            val vB1 = alignedB[nextI]

            // Quad (vA0, vA1, vB1, vB0) as two triangles
            val f1 = newIndices.size / 3
            newIndices.add(vA0); newIndices.add(vA1); newIndices.add(vB1)
            newBridgeFaces.add(f1)

            val f2 = newIndices.size / 3
            newIndices.add(vA0); newIndices.add(vB1); newIndices.add(vB0)
            newBridgeFaces.add(f2)
        }

        val finalVertices = sourceMesh.vertices.clone()
        val finalIndices = newIndices.toIntArray()
        val finalNormals = FloatArray(finalVertices.size)

        for (i in finalIndices.indices step 3) {
            val idx0 = finalIndices[i]; val idx1 = finalIndices[i + 1]; val idx2 = finalIndices[i + 2]
            val pt0 = Vector3(finalVertices[idx0 * 3], finalVertices[idx0 * 3 + 1], finalVertices[idx0 * 3 + 2])
            val pt1 = Vector3(finalVertices[idx1 * 3], finalVertices[idx1 * 3 + 1], finalVertices[idx1 * 3 + 2])
            val pt2 = Vector3(finalVertices[idx2 * 3], finalVertices[idx2 * 3 + 1], finalVertices[idx2 * 3 + 2])
            val fn = (pt1 - pt0).cross(pt2 - pt0).normalized()

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

        val newMesh = Mesh(
            vertices = finalVertices,
            normals = finalNormals,
            uvs = sourceMesh.uvs.clone(),
            indices = finalIndices,
            wireframeIndices = finalWireframe,
            boundingBox = sourceMesh.boundingBox
        )

        return Pair(newMesh, newBridgeFaces)
    }
}
