package com.example.studio3d.core.engine

import com.example.studio3d.core.math.*
import com.example.studio3d.core.engine.*
import com.example.studio3d.core.scene.*
import com.example.studio3d.core.history.*
import com.example.studio3d.core.preferences.*
import com.example.studio3d.core.project.*
import com.example.studio3d.feature.editor.viewport.*
import com.example.studio3d.feature.editor.tools.select.*


import com.example.studio3d.core.math.BoundingBox
import com.example.studio3d.core.math.Vector3

object MeshModifier {

    /**
     * Subdivides triangle faces of a Mesh with Quad-Aware bilinear grid division (1 to N cuts).
     * If [selectedFaceIndices] is empty, subdivides ALL faces of the mesh.
     * Prevents cracks on neighboring unselected faces via adaptive bisection.
     * Returns Pair(newMesh, newSubdividedFaceIndices).
     */
    fun subdivideFaces(
        sourceMesh: Mesh,
        selectedFaceIndices: Set<Int> = emptySet(),
        cuts: Int = 1
    ): Pair<Mesh, Set<Int>>? {
        val topology = sourceMesh.topology
        val totalFaces = topology.faceCount
        if (totalFaces == 0) return null

        val facesToSubdivide = if (selectedFaceIndices.isEmpty()) {
            (0 until totalFaces).toSet()
        } else {
            selectedFaceIndices.filter { it in 0 until totalFaces }.toSet()
        }
        if (facesToSubdivide.isEmpty()) return null

        val clampedCuts = cuts.coerceIn(1, 8)
        val segs = clampedCuts + 1

        val tempVertices = ArrayList<Float>()
        for (v in sourceMesh.vertices) tempVertices.add(v)

        val tempUvs = ArrayList<Float>()
        val hasUvs = sourceMesh.uvs.isNotEmpty()
        if (hasUvs) {
            for (u in sourceMesh.uvs) tempUvs.add(u)
        }

        // Subdivided edge point caching: Triple(minRoot, maxRoot, normalizedSeg) -> newVertexIndex
        val edgeSubdivMap = HashMap<Triple<Int, Int, Int>, Int>()
        fun getOrCreateEdgePoint(vA: Int, vB: Int, seg: Int, totalSegs: Int): Int {
            if (seg == 0) return vA
            if (seg == totalSegs) return vB
            val rA = topology.coincidentMap[vA]
            val rB = topology.coincidentMap[vB]
            val isForward = rA <= rB
            val minR = minOf(rA, rB)
            val maxR = maxOf(rA, rB)
            val normSeg = if (isForward) seg else (totalSegs - seg)
            val key = Triple(minR, maxR, normSeg)
            val existing = edgeSubdivMap[key]
            if (existing != null) return existing

            val t = seg.toFloat() / totalSegs
            val oA = vA * 3; val oB = vB * 3
            val px = sourceMesh.vertices[oA] * (1f - t) + sourceMesh.vertices[oB] * t
            val py = sourceMesh.vertices[oA + 1] * (1f - t) + sourceMesh.vertices[oB + 1] * t
            val pz = sourceMesh.vertices[oA + 2] * (1f - t) + sourceMesh.vertices[oB + 2] * t

            val newIdx = tempVertices.size / 3
            tempVertices.add(px); tempVertices.add(py); tempVertices.add(pz)
            if (hasUvs) {
                val uA = if (vA * 2 + 1 < sourceMesh.uvs.size) sourceMesh.uvs[vA * 2] else 0f
                val vA_uv = if (vA * 2 + 1 < sourceMesh.uvs.size) sourceMesh.uvs[vA * 2 + 1] else 0f
                val uB = if (vB * 2 + 1 < sourceMesh.uvs.size) sourceMesh.uvs[vB * 2] else 0f
                val vB_uv = if (vB * 2 + 1 < sourceMesh.uvs.size) sourceMesh.uvs[vB * 2 + 1] else 0f
                tempUvs.add(uA * (1f - t) + uB * t)
                tempUvs.add(vA_uv * (1f - t) + vB_uv * t)
            }
            edgeSubdivMap[key] = newIdx
            return newIdx
        }

        // Backward compatibility for single triangle bisection
        fun getOrCreateMidpoint(i0: Int, i1: Int): Int = getOrCreateEdgePoint(i0, i1, 1, 2)

        // 1. Detect Quad pairs (coplanar triangle pairs) vs Single Triangles
        data class QuadFace(val fA: Int, val fB: Int, val v0: Int, val v1: Int, val v2: Int, val v3: Int)
        val quadList = ArrayList<QuadFace>()
        val singleTriangleSet = HashSet<Int>()
        val quadFacesSet = HashSet<Int>()

        val processedFaces = HashSet<Int>()
        for (f in facesToSubdivide) {
            if (f in processedFaces) continue
            val coplanar = topology.getCoplanarConnectedFaces(f)
            if (coplanar.size == 2 && coplanar[0] in facesToSubdivide && coplanar[1] in facesToSubdivide) {
                val fA = coplanar[0]
                val fB = coplanar[1]
                val (a0, a1, a2) = topology.getFaceIndices(fA)
                val (b0, b1, b2) = topology.getFaceIndices(fB)
                val aRoots = listOf(topology.coincidentMap[a0], topology.coincidentMap[a1], topology.coincidentMap[a2])
                val bRoots = setOf(topology.coincidentMap[b0], topology.coincidentMap[b1], topology.coincidentMap[b2])
                val sharedRoots = aRoots.filter { it in bRoots }.toSet()

                if (sharedRoots.size == 2) {
                    val aIndices = listOf(a0, a1, a2)
                    val bIndices = listOf(b0, b1, b2)

                    val uA = aIndices.first { topology.coincidentMap[it] !in sharedRoots }
                    val uB = bIndices.first { topology.coincidentMap[it] !in sharedRoots }

                    val uAIdx = aIndices.indexOf(uA)
                    val d1 = aIndices[(uAIdx + 1) % 3]
                    val d2 = aIndices[(uAIdx + 2) % 3]

                    quadList.add(QuadFace(fA, fB, uA, d1, uB, d2))
                    quadFacesSet.add(fA)
                    quadFacesSet.add(fB)
                    processedFaces.add(fA)
                    processedFaces.add(fB)
                    continue
                }
            }
            singleTriangleSet.add(f)
            processedFaces.add(f)
        }

        val newIndices = ArrayList<Int>()
        val newSubdividedFaceIndices = LinkedHashSet<Int>()
        val quadGridMap = HashMap<QuadFace, Array<IntArray>>()

        // 2. Build (segs x segs) Bilinear Grid for each Quad
        for (q in quadList) {
            val grid = Array(segs + 1) { IntArray(segs + 1) }

            for (i in 0..segs) {
                for (j in 0..segs) {
                    val vIdx = when {
                        i == 0 && j == 0 -> q.v0
                        i == segs && j == 0 -> q.v1
                        i == segs && j == segs -> q.v2
                        i == 0 && j == segs -> q.v3
                        j == 0 -> getOrCreateEdgePoint(q.v0, q.v1, i, segs)
                        i == segs -> getOrCreateEdgePoint(q.v1, q.v2, j, segs)
                        j == segs -> getOrCreateEdgePoint(q.v3, q.v2, i, segs)
                        i == 0 -> getOrCreateEdgePoint(q.v0, q.v3, j, segs)
                        else -> {
                            // Interior grid vertex via Bilinear Interpolation
                            val u = i.toFloat() / segs
                            val v = j.toFloat() / segs
                            val w00 = (1f - u) * (1f - v)
                            val w10 = u * (1f - v)
                            val w11 = u * v
                            val w01 = (1f - u) * v

                            val o0 = q.v0 * 3; val o1 = q.v1 * 3; val o2 = q.v2 * 3; val o3 = q.v3 * 3
                            val px = sourceMesh.vertices[o0] * w00 + sourceMesh.vertices[o1] * w10 + sourceMesh.vertices[o2] * w11 + sourceMesh.vertices[o3] * w01
                            val py = sourceMesh.vertices[o0 + 1] * w00 + sourceMesh.vertices[o1 + 1] * w10 + sourceMesh.vertices[o2 + 1] * w11 + sourceMesh.vertices[o3 + 1] * w01
                            val pz = sourceMesh.vertices[o0 + 2] * w00 + sourceMesh.vertices[o1 + 2] * w10 + sourceMesh.vertices[o2 + 2] * w11 + sourceMesh.vertices[o3 + 2] * w01

                            val idx = tempVertices.size / 3
                            tempVertices.add(px); tempVertices.add(py); tempVertices.add(pz)

                            if (hasUvs) {
                                val u0 = if (q.v0 * 2 + 1 < sourceMesh.uvs.size) sourceMesh.uvs[q.v0 * 2] else 0f
                                val v0 = if (q.v0 * 2 + 1 < sourceMesh.uvs.size) sourceMesh.uvs[q.v0 * 2 + 1] else 0f
                                val u1 = if (q.v1 * 2 + 1 < sourceMesh.uvs.size) sourceMesh.uvs[q.v1 * 2] else 0f
                                val v1 = if (q.v1 * 2 + 1 < sourceMesh.uvs.size) sourceMesh.uvs[q.v1 * 2 + 1] else 0f
                                val u2 = if (q.v2 * 2 + 1 < sourceMesh.uvs.size) sourceMesh.uvs[q.v2 * 2] else 0f
                                val v2 = if (q.v2 * 2 + 1 < sourceMesh.uvs.size) sourceMesh.uvs[q.v2 * 2 + 1] else 0f
                                val u3 = if (q.v3 * 2 + 1 < sourceMesh.uvs.size) sourceMesh.uvs[q.v3 * 2] else 0f
                                val v3 = if (q.v3 * 2 + 1 < sourceMesh.uvs.size) sourceMesh.uvs[q.v3 * 2 + 1] else 0f

                                val uvU = u0 * w00 + u1 * w10 + u2 * w11 + u3 * w01
                                val uvV = v0 * w00 + v1 * w10 + v2 * w11 + v3 * w01
                                tempUvs.add(uvU); tempUvs.add(uvV)
                            }
                            idx
                        }
                    }
                    grid[i][j] = vIdx
                }
            }
            quadGridMap[q] = grid

            // Triangulate cells of this Quad
            val startFace = newIndices.size / 3
            for (i in 0 until segs) {
                for (j in 0 until segs) {
                    val c00 = grid[i][j]
                    val c10 = grid[i + 1][j]
                    val c11 = grid[i + 1][j + 1]
                    val c01 = grid[i][j + 1]

                    // Triangle 1
                    newIndices.add(c00); newIndices.add(c10); newIndices.add(c11)
                    // Triangle 2
                    newIndices.add(c00); newIndices.add(c11); newIndices.add(c01)
                }
            }
            val totalQuadFaces = segs * segs * 2
            for (subF in startFace until startFace + totalQuadFaces) {
                newSubdividedFaceIndices.add(subF)
            }
        }

        // 3. Triangulate remaining faces (Single Triangles or untouched neighbors)
        for (f in 0 until totalFaces) {
            if (f in quadFacesSet) continue
            val (i0, i1, i2) = topology.getFaceIndices(f)
            val isSubdiv = f in singleTriangleSet

            if (isSubdiv) {
                val m01 = getOrCreateMidpoint(i0, i1)
                val m12 = getOrCreateMidpoint(i1, i2)
                val m20 = getOrCreateMidpoint(i2, i0)

                val startFace = newIndices.size / 3
                newIndices.add(i0); newIndices.add(m01); newIndices.add(m20)
                newIndices.add(i1); newIndices.add(m12); newIndices.add(m01)
                newIndices.add(i2); newIndices.add(m20); newIndices.add(m12)
                newIndices.add(m01); newIndices.add(m12); newIndices.add(m20)

                for (subF in startFace until startFace + 4) {
                    newSubdividedFaceIndices.add(subF)
                }
            } else {
                // Adaptive bisection for unselected neighbor faces to prevent cracks
                val r0 = topology.coincidentMap[i0]
                val r1 = topology.coincidentMap[i1]
                val r2 = topology.coincidentMap[i2]
                val m01 = edgeSubdivMap[Triple(minOf(r0, r1), maxOf(r0, r1), if (r0 <= r1) 1 else (segs - 1))]
                val m12 = edgeSubdivMap[Triple(minOf(r1, r2), maxOf(r1, r2), if (r1 <= r2) 1 else (segs - 1))]
                val m20 = edgeSubdivMap[Triple(minOf(r2, r0), maxOf(r2, r0), if (r2 <= r0) 1 else (segs - 1))]

                val midCount = (if (m01 != null) 1 else 0) + (if (m12 != null) 1 else 0) + (if (m20 != null) 1 else 0)

                when (midCount) {
                    0 -> {
                        newIndices.add(i0); newIndices.add(i1); newIndices.add(i2)
                    }
                    1 -> {
                        when {
                            m01 != null -> {
                                newIndices.add(i0); newIndices.add(m01); newIndices.add(i2)
                                newIndices.add(m01); newIndices.add(i1); newIndices.add(i2)
                            }
                            m12 != null -> {
                                newIndices.add(i1); newIndices.add(m12); newIndices.add(i0)
                                newIndices.add(m12); newIndices.add(i2); newIndices.add(i0)
                            }
                            m20 != null -> {
                                newIndices.add(i2); newIndices.add(m20); newIndices.add(i1)
                                newIndices.add(m20); newIndices.add(i0); newIndices.add(i1)
                            }
                        }
                    }
                    2 -> {
                        when {
                            m01 != null && m12 != null -> {
                                newIndices.add(i0); newIndices.add(m01); newIndices.add(i2)
                                newIndices.add(m01); newIndices.add(m12); newIndices.add(i2)
                                newIndices.add(m01); newIndices.add(i1); newIndices.add(m12)
                            }
                            m12 != null && m20 != null -> {
                                newIndices.add(i1); newIndices.add(m12); newIndices.add(i0)
                                newIndices.add(m12); newIndices.add(m20); newIndices.add(i0)
                                newIndices.add(m12); newIndices.add(i2); newIndices.add(m20)
                            }
                            m20 != null && m01 != null -> {
                                newIndices.add(i2); newIndices.add(m20); newIndices.add(i1)
                                newIndices.add(m20); newIndices.add(m01); newIndices.add(i1)
                                newIndices.add(m20); newIndices.add(i0); newIndices.add(m01)
                            }
                        }
                    }
                    else -> {
                        val sm01 = m01!!; val sm12 = m12!!; val sm20 = m20!!
                        newIndices.add(i0); newIndices.add(sm01); newIndices.add(sm20)
                        newIndices.add(i1); newIndices.add(sm12); newIndices.add(sm01)
                        newIndices.add(i2); newIndices.add(sm20); newIndices.add(sm12)
                        newIndices.add(sm01); newIndices.add(sm12); newIndices.add(sm20)
                    }
                }
            }
        }

        // 4. Rebuild wireframe indices cleanly
        val wireLines = LinkedHashSet<Pair<Int, Int>>()
        val oldWire = sourceMesh.wireframeIndices
        for (w in 0 until oldWire.size step 2) {
            val v0 = oldWire[w]
            val v1 = oldWire[w + 1]
            val r0 = topology.coincidentMap[v0]
            val r1 = topology.coincidentMap[v1]
            val hasSubdiv = (1 until segs).any { s ->
                edgeSubdivMap.containsKey(Triple(minOf(r0, r1), maxOf(r0, r1), s))
            }

            if (hasSubdiv) {
                var prev = v0
                for (s in 1..segs) {
                    val curr = getOrCreateEdgePoint(v0, v1, s, segs)
                    wireLines.add(Pair(minOf(prev, curr), maxOf(prev, curr)))
                    prev = curr
                }
            } else {
                wireLines.add(Pair(minOf(v0, v1), maxOf(v0, v1)))
            }
        }

        // 4a. For Quads: Add pure horizontal and vertical grid wireframe lines (ZERO diagonals)
        for (q in quadList) {
            val grid = quadGridMap[q] ?: continue
            // Horizontal lines (along i)
            for (j in 0..segs) {
                for (i in 0 until segs) {
                    val vA = grid[i][j]
                    val vB = grid[i + 1][j]
                    wireLines.add(Pair(minOf(vA, vB), maxOf(vA, vB)))
                }
            }
            // Vertical lines (along j)
            for (i in 0..segs) {
                for (j in 0 until segs) {
                    val vA = grid[i][j]
                    val vB = grid[i][j + 1]
                    wireLines.add(Pair(minOf(vA, vB), maxOf(vA, vB)))
                }
            }
        }

        // 4b. For single triangles: Triforce inner lines
        for (f in singleTriangleSet) {
            val (i0, i1, i2) = topology.getFaceIndices(f)
            val m01 = getOrCreateMidpoint(i0, i1)
            val m12 = getOrCreateMidpoint(i1, i2)
            val m20 = getOrCreateMidpoint(i2, i0)

            wireLines.add(Pair(minOf(m01, m12), maxOf(m01, m12)))
            wireLines.add(Pair(minOf(m12, m20), maxOf(m12, m20)))
            wireLines.add(Pair(minOf(m20, m01), maxOf(m20, m01)))
        }

        val wireArray = IntArray(wireLines.size * 2)
        var wireIdx = 0
        for (line in wireLines) {
            wireArray[wireIdx++] = line.first
            wireArray[wireIdx++] = line.second
        }

        val finalVertices = FloatArray(tempVertices.size) { tempVertices[it] }
        val finalUvs = if (hasUvs) FloatArray(tempUvs.size) { tempUvs[it] } else FloatArray(0)
        val finalIndices = newIndices.toIntArray()

        val newMesh = Mesh(
            vertices = finalVertices,
            normals = FloatArray(finalVertices.size),
            uvs = finalUvs,
            indices = finalIndices,
            wireframeIndices = wireArray,
            boundingBox = BoundingBox(Vector3.ZERO, Vector3.ZERO)
        )
        newMesh.recalculateNormalsAndBounds()

        return Pair(newMesh, newSubdividedFaceIndices)
    }

    /**
     * Deletes geometry (faces, edges, or vertices) from a mesh.
     * Compacts remaining vertices and indices, removing orphaned elements.
     * Returns null if deletion results in an empty mesh or invalid operation.
     */
    fun deleteGeometry(
        sourceMesh: Mesh,
        facesToDelete: Set<Int> = emptySet(),
        verticesToDelete: Set<Int> = emptySet(),
        edgesToDelete: Set<Pair<Int, Int>> = emptySet()
    ): Mesh? {
        val topology = sourceMesh.topology
        val totalFaces = topology.faceCount
        if (totalFaces == 0) return null

        // Gather all face indices that must be removed
        val markedFaces = HashSet<Int>(facesToDelete)

        // 1. Delete faces by vertex: any face touching any deleted vertex root
        if (verticesToDelete.isNotEmpty()) {
            val allDeletedVertRoots = HashSet<Int>()
            for (v in verticesToDelete) {
                if (v in 0 until topology.vertexCount) {
                    allDeletedVertRoots.add(topology.coincidentMap[v])
                }
            }
            for (f in 0 until totalFaces) {
                val (i0, i1, i2) = topology.getFaceIndices(f)
                if (topology.coincidentMap[i0] in allDeletedVertRoots ||
                    topology.coincidentMap[i1] in allDeletedVertRoots ||
                    topology.coincidentMap[i2] in allDeletedVertRoots
                ) {
                    markedFaces.add(f)
                }
            }
        }

        // 2. Delete faces by edge: only faces containing BOTH endpoints of the edge
        if (edgesToDelete.isNotEmpty()) {
            val edgeRoots = edgesToDelete.map {
                val r0 = if (it.first in 0 until topology.vertexCount) topology.coincidentMap[it.first] else it.first
                val r1 = if (it.second in 0 until topology.vertexCount) topology.coincidentMap[it.second] else it.second
                Pair(minOf(r0, r1), maxOf(r0, r1))
            }.toSet()

            for (f in 0 until totalFaces) {
                val (i0, i1, i2) = topology.getFaceIndices(f)
                val r0 = topology.coincidentMap[i0]
                val r1 = topology.coincidentMap[i1]
                val r2 = topology.coincidentMap[i2]
                val e01 = Pair(minOf(r0, r1), maxOf(r0, r1))
                val e12 = Pair(minOf(r1, r2), maxOf(r1, r2))
                val e20 = Pair(minOf(r2, r0), maxOf(r2, r0))

                if (e01 in edgeRoots || e12 in edgeRoots || e20 in edgeRoots) {
                    markedFaces.add(f)
                }
            }
        }

        if (markedFaces.isEmpty()) return null
        if (markedFaces.size >= totalFaces) return null // Do not completely annihilate mesh into 0 faces

        // Filter surviving faces
        val survivingIndices = ArrayList<Int>()
        for (f in 0 until totalFaces) {
            if (f !in markedFaces) {
                val (i0, i1, i2) = topology.getFaceIndices(f)
                survivingIndices.add(i0)
                survivingIndices.add(i1)
                survivingIndices.add(i2)
            }
        }

        if (survivingIndices.isEmpty()) return null

        // Compact surviving vertices
        val oldVertexCount = sourceMesh.vertices.size / 3
        val remap = IntArray(oldVertexCount) { -1 }
        val newVertices = ArrayList<Float>()
        val newUvs = ArrayList<Float>()
        val hasUvs = sourceMesh.uvs.isNotEmpty()
        var newVertCount = 0

        for (oldIdx in survivingIndices) {
            if (remap[oldIdx] == -1) {
                remap[oldIdx] = newVertCount++
                val o = oldIdx * 3
                newVertices.add(sourceMesh.vertices[o])
                newVertices.add(sourceMesh.vertices[o + 1])
                newVertices.add(sourceMesh.vertices[o + 2])
                if (hasUvs) {
                    val uOff = oldIdx * 2
                    newUvs.add(if (uOff < sourceMesh.uvs.size) sourceMesh.uvs[uOff] else 0f)
                    newUvs.add(if (uOff + 1 < sourceMesh.uvs.size) sourceMesh.uvs[uOff + 1] else 0f)
                }
            }
        }

        val remappedIndices = IntArray(survivingIndices.size) { remap[survivingIndices[it]] }

        val finalVertices = FloatArray(newVertices.size) { newVertices[it] }
        val finalUvs = if (hasUvs) FloatArray(newUvs.size) { newUvs[it] } else FloatArray(0)

        // Robust, clean wireframe generation with automatic hole boundary and crease edge detection
        val wireArray = buildCleanWireframe(finalVertices, remappedIndices)

        val resultMesh = Mesh(
            vertices = finalVertices,
            normals = FloatArray(finalVertices.size),
            uvs = finalUvs,
            indices = remappedIndices,
            wireframeIndices = wireArray,
            boundingBox = BoundingBox(Vector3.ZERO, Vector3.ZERO)
        )
        resultMesh.recalculateNormalsAndBounds()
        return resultMesh
    }

    /**
     * Merges/welds selected vertices into their centroid (collapse to center).
     * Eliminates degenerate triangles and compacts the mesh.
     * Returns Pair(newMesh, mergedVertexIndex).
     */
    fun mergeVertices(
        sourceMesh: Mesh,
        targetVertices: Set<Int>
    ): Pair<Mesh, Int>? {
        if (targetVertices.size < 2) return null

        val topology = sourceMesh.topology
        val oldVertexCount = sourceMesh.vertices.size / 3

        // Expand to all coincident vertices so multi-material/split-normal clusters weld together
        val fullVertexSet = HashSet<Int>()
        for (v in targetVertices) {
            if (v in 0 until oldVertexCount) {
                fullVertexSet.addAll(topology.getCoincidentVertices(v))
            }
        }
        if (fullVertexSet.size < 2) return null

        // 1. Calculate centroid of selected vertices across unique spatial roots (prevent bias)
        val uniqueRoots = fullVertexSet.map { topology.coincidentMap[it] }.distinct()
        if (uniqueRoots.size < 2) return null

        var centroidSum = Vector3.ZERO
        for (r in uniqueRoots) {
            centroidSum = centroidSum + sourceMesh.getVertex(r)
        }
        val centroid = centroidSum / uniqueRoots.size.toFloat()

        // 2. Select primary target vertex
        val primaryTarget = fullVertexSet.first()

        // 3. Remap all indices: any selected vertex maps to primaryTarget
        val totalTriangles = sourceMesh.indices.size / 3
        val survivingIndices = ArrayList<Int>()

        for (t in 0 until totalTriangles) {
            var i0 = sourceMesh.indices[t * 3]
            var i1 = sourceMesh.indices[t * 3 + 1]
            var i2 = sourceMesh.indices[t * 3 + 2]

            if (i0 in fullVertexSet) i0 = primaryTarget
            if (i1 in fullVertexSet) i1 = primaryTarget
            if (i2 in fullVertexSet) i2 = primaryTarget

            // Degenerate triangle check: if any 2 vertices are equal, drop it
            if (i0 != i1 && i1 != i2 && i2 != i0) {
                survivingIndices.add(i0)
                survivingIndices.add(i1)
                survivingIndices.add(i2)
            }
        }

        if (survivingIndices.isEmpty()) return null

        // 4. Compact vertices
        val remap = IntArray(oldVertexCount) { -1 }
        val newVertices = ArrayList<Float>()
        val newUvs = ArrayList<Float>()
        val hasUvs = sourceMesh.uvs.isNotEmpty()
        var newVertCount = 0

        for (oldIdx in survivingIndices) {
            if (remap[oldIdx] == -1) {
                remap[oldIdx] = newVertCount++
                if (oldIdx == primaryTarget) {
                    newVertices.add(centroid.x)
                    newVertices.add(centroid.y)
                    newVertices.add(centroid.z)
                } else {
                    val o = oldIdx * 3
                    newVertices.add(sourceMesh.vertices[o])
                    newVertices.add(sourceMesh.vertices[o + 1])
                    newVertices.add(sourceMesh.vertices[o + 2])
                }
                if (hasUvs) {
                    val uOff = oldIdx * 2
                    newUvs.add(if (uOff < sourceMesh.uvs.size) sourceMesh.uvs[uOff] else 0f)
                    newUvs.add(if (uOff + 1 < sourceMesh.uvs.size) sourceMesh.uvs[uOff + 1] else 0f)
                }
            }
        }

        val remappedIndices = IntArray(survivingIndices.size) { remap[survivingIndices[it]] }
        val finalMergedIndex = remap[primaryTarget]

        val finalVertices = FloatArray(newVertices.size) { newVertices[it] }
        val finalUvs = if (hasUvs) FloatArray(newUvs.size) { newUvs[it] } else FloatArray(0)

        // Robust, clean wireframe generation
        val wireArray = buildCleanWireframe(finalVertices, remappedIndices)

        val resultMesh = Mesh(
            vertices = finalVertices,
            normals = FloatArray(finalVertices.size),
            uvs = finalUvs,
            indices = remappedIndices,
            wireframeIndices = wireArray,
            boundingBox = BoundingBox(Vector3.ZERO, Vector3.ZERO)
        )
        resultMesh.recalculateNormalsAndBounds()

        return Pair(resultMesh, finalMergedIndex)
    }

    /**
     * Builds clean, topological feature wireframe lines for any mesh.
     * Includes mesh boundary edges (open boundaries / holes) and crease edges (angle > ~2.5°).
     * Suppresses internal coplanar quad diagonals and duplicate lines.
     */
    fun buildCleanWireframe(
        vertices: FloatArray,
        indices: IntArray
    ): IntArray {
        val triCount = indices.size / 3
        if (triCount == 0) return IntArray(0)

        val vertexCount = vertices.size / 3

        // 1. Group vertices by spatial position (spatial hashing)
        val spatialMap = HashMap<Long, MutableList<Int>>()
        val rootMap = IntArray(vertexCount) { it }

        for (i in 0 until vertexCount) {
            val vx = vertices[i * 3]
            val vy = vertices[i * 3 + 1]
            val vz = vertices[i * 3 + 2]
            val qx = (vx * 2000f).toInt().toLong()
            val qy = (vy * 2000f).toInt().toLong()
            val qz = (vz * 2000f).toInt().toLong()
            val hash = (qx * 73856093L) xor (qy * 19349663L) xor (qz * 83492791L)

            val existing = spatialMap[hash]
            if (existing != null) {
                var found = -1
                for (other in existing) {
                    val ox = vertices[other * 3]
                    val oy = vertices[other * 3 + 1]
                    val oz = vertices[other * 3 + 2]
                    val dx = vx - ox
                    val dy = vy - oy
                    val dz = vz - oz
                    if (dx * dx + dy * dy + dz * dz <= 0.000025f) {
                        found = rootMap[other]
                        break
                    }
                }
                if (found != -1) {
                    rootMap[i] = found
                } else {
                    existing.add(i)
                }
            } else {
                spatialMap[hash] = mutableListOf(i)
            }
        }

        // 2. Compute face normals for all triangles
        val faceNormals = Array(triCount) { Vector3.ZERO }
        for (t in 0 until triCount) {
            val i0 = indices[t * 3]
            val i1 = indices[t * 3 + 1]
            val i2 = indices[t * 3 + 2]
            val v0 = Vector3(vertices[i0 * 3], vertices[i0 * 3 + 1], vertices[i0 * 3 + 2])
            val v1 = Vector3(vertices[i1 * 3], vertices[i1 * 3 + 1], vertices[i1 * 3 + 2])
            val v2 = Vector3(vertices[i2 * 3], vertices[i2 * 3 + 1], vertices[i2 * 3 + 2])
            faceNormals[t] = (v1 - v0).cross(v2 - v0).normalized()
        }

        // 3. Map spatial edges to triangle owners
        class EdgeData(val v0: Int, val v1: Int) {
            val triangles = ArrayList<Int>(2)
        }
        val edgeMap = LinkedHashMap<Pair<Int, Int>, EdgeData>()

        for (t in 0 until triCount) {
            val i0 = indices[t * 3]
            val i1 = indices[t * 3 + 1]
            val i2 = indices[t * 3 + 2]
            val triEdges = arrayOf(Pair(i0, i1), Pair(i1, i2), Pair(i2, i0))

            for ((va, vb) in triEdges) {
                val r0 = rootMap[va]
                val r1 = rootMap[vb]
                if (r0 == r1) continue
                val key = Pair(minOf(r0, r1), maxOf(r0, r1))
                val entry = edgeMap.getOrPut(key) { EdgeData(va, vb) }
                entry.triangles.add(t)
            }
        }

        // 4. Determine wireframe edges: boundary edges and non-coplanar crease edges
        val wireLines = ArrayList<Int>()
        for ((_, data) in edgeMap) {
            val tris = data.triangles
            val isBoundary = (tris.size == 1)
            val isCrease = if (tris.size == 2) {
                val n0 = faceNormals[tris[0]]
                val n1 = faceNormals[tris[1]]
                n0.dot(n1) < 0.999f
            } else {
                tris.size > 2
            }

            if (isBoundary || isCrease) {
                wireLines.add(data.v0)
                wireLines.add(data.v1)
            }
        }

        return wireLines.toIntArray()
    }
}
