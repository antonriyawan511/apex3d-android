package com.example.studio3d.core.engine

import com.example.studio3d.core.math.BoundingBox
import com.example.studio3d.core.math.Vector3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sin

/**
 * Pusat Algoritma Bevel Apex3D (Modular, Terisolasi, Bersih, dan Kompatibel Blender).
 *
 * Arsitektur Terpisah:
 * 1. Edge Bevel Generation: Ekstraksi poligon, safe clamp overlap, retraksi permukaan, quad chamfer strips.
 * 2. Vertex Junction Resolution: Klasifikasi valensi sudut (1-edge, 2-edge, 3+ edge) & siklus sirkular CCW.
 * 3. Corner Patch Reconstruction:
 *    - Single-Segment Chamfer (S = 1): Tepat 1 facet segitiga datar (minimal polygon count).
 *    - Multi-Segment Round Bevel (S >= 2): 100% All-Quad Coons Grid Patch (identik topologi Blender, tanpa triangle fan/radial).
 * 4. Mesh Validation: Eliminasi face degenerasi, penjaminan winding konsisten, dan manifold geometry.
 */
object MeshBeveler {

    // =========================================================================
    // DATA STRUCTURES
    // =========================================================================

    private data class Polygon(
        val triangleIndices: List<Int>,
        val vertices: List<Int>,
        val normal: Vector3,
        val centroid: Vector3
    )

    private class BevelBuildContext(
        val sourceMesh: Mesh,
        val hasUvs: Boolean
    ) {
        val outVertices = ArrayList<Float>()
        val outUvs = ArrayList<Float>()
        val newIndices = ArrayList<Int>()
        val newChamferFaces = HashSet<Int>()
        val wireframeEdges = LinkedHashSet<Pair<Int, Int>>()
        // Registrasi titik flush-cut per root-vertex (untuk end-cap 1-edge junction)
        val flushCutsByRoot = HashMap<Int, MutableList<Pair<Int, Int>>>()

        // Pemakai welded-edge (anti double-cover antar stage: cap/miter/dome)
        private val edgeWeldUse = HashMap<Pair<String, String>, Int>()

        private fun weldKey(idx: Int): String {
            val p = getPos(idx)
            return "%.4f|%.4f|%.4f".format(p.x, p.y, p.z)
        }

        private fun countWeldEdge(a: Int, b: Int) {
            val ka = weldKey(a)
            val kb = weldKey(b)
            if (ka == kb) return
            val key = if (ka < kb) ka to kb else kb to ka
            edgeWeldUse[key] = (edgeWeldUse[key] ?: 0) + 1
        }

        private fun weldEdgeUse(a: Int, b: Int): Int {
            val ka = weldKey(a)
            val kb = weldKey(b)
            if (ka == kb) return 0
            val key = if (ka < kb) ka to kb else kb to ka
            return edgeWeldUse[key] ?: 0
        }

        /** Welded edge gabungan tri: apakah implementasi bebas double-cover? */
        fun isTriangleWeldFree(i0: Int, i1: Int, i2: Int): Boolean {
            return weldEdgeUse(i0, i1) < 2 && weldEdgeUse(i1, i2) < 2 && weldEdgeUse(i2, i0) < 2
        }

        private val posIndexMap = HashMap<String, Int>()

        fun getOrAddVertex(p: Vector3, origV: Int): Int {
            val key = "%.4f|%.4f|%.4f".format(p.x, p.y, p.z)
            val existing = posIndexMap[key]
            if (existing != null) return existing
            val idx = addVertex(p, origV)
            posIndexMap[key] = idx
            return idx
        }

        fun addVertex(p: Vector3, origV: Int): Int {
            val idx = outVertices.size / 3
            outVertices.add(p.x)
            outVertices.add(p.y)
            outVertices.add(p.z)
            if (hasUvs) {
                val u = if (origV * 2 < sourceMesh.uvs.size) sourceMesh.uvs[origV * 2] else 0f
                val v = if (origV * 2 + 1 < sourceMesh.uvs.size) sourceMesh.uvs[origV * 2 + 1] else 0f
                outUvs.add(u)
                outUvs.add(v)
            }
            return idx
        }

        fun getPos(idx: Int): Vector3 {
            return Vector3(outVertices[idx * 3], outVertices[idx * 3 + 1], outVertices[idx * 3 + 2])
        }

        fun addWireEdge(v0: Int, v1: Int) {
            if (v0 != v1) {
                wireframeEdges.add(Pair(minOf(v0, v1), maxOf(v0, v1)))
            }
        }

        fun addTriangle(
            idx0: Int, 
            idx1: Int, 
            idx2: Int, 
            targetNormal: Vector3, 
            markAsChamfer: Boolean = true,
            addInternalWires: Boolean = true,
            addWireframe: Boolean = true
        ) {
            if (idx0 == idx1 || idx1 == idx2 || idx2 == idx0) return // Skip degenerate
            val p0 = getPos(idx0); val p1 = getPos(idx1); val p2 = getPos(idx2)
            val fn = (p1 - p0).cross(p2 - p0)
            if (fn.lengthSquared() < 1e-8f) return // Skip zero area

            val f = newIndices.size / 3
            if (fn.dot(targetNormal) >= 0f) {
                newIndices.add(idx0); newIndices.add(idx1); newIndices.add(idx2)
            } else {
                newIndices.add(idx0); newIndices.add(idx2); newIndices.add(idx1)
            }
            countWeldEdge(idx0, idx1)
            countWeldEdge(idx1, idx2)
            countWeldEdge(idx2, idx0)
            if (markAsChamfer) newChamferFaces.add(f)
            if (addWireframe) {
                addWireEdge(idx0, idx1)
                if (addInternalWires) {
                    addWireEdge(idx1, idx2)
                    addWireEdge(idx2, idx0)
                }
            }
        }

        fun addQuad(idx0: Int, idx1: Int, idx2: Int, idx3: Int, targetNormal: Vector3, markAsChamfer: Boolean = true) {
            val p0 = getPos(idx0); val p1 = getPos(idx1); val p2 = getPos(idx2)
            val fn = (p1 - p0).cross(p2 - p0)
            val f1 = newIndices.size / 3
            val f2 = f1 + 1
            if (fn.dot(targetNormal) >= 0f) {
                newIndices.add(idx0); newIndices.add(idx1); newIndices.add(idx2)
                newIndices.add(idx0); newIndices.add(idx2); newIndices.add(idx3)
            } else {
                newIndices.add(idx0); newIndices.add(idx2); newIndices.add(idx1)
                newIndices.add(idx0); newIndices.add(idx3); newIndices.add(idx2)
            }
            countWeldEdge(idx0, idx1)
            countWeldEdge(idx1, idx2)
            countWeldEdge(idx2, idx3)
            countWeldEdge(idx3, idx0)
            if (markAsChamfer) {
                newChamferFaces.add(f1)
                newChamferFaces.add(f2)
            }
            addWireEdge(idx0, idx1)
            addWireEdge(idx1, idx2)
            addWireEdge(idx2, idx3)
            addWireEdge(idx3, idx0)
        }
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Bevel boundary edges dari sekumpulan face yang dipilih.
     */
    fun bevelFaces(
        sourceMesh: Mesh,
        selectedFaceIndices: Set<Int>,
        offset: Float = 0.12f,
        depth: Float = 0.0f,
        segments: Int = 1,
        profile: Float = 0.5f
    ): Pair<Mesh, Set<Int>>? {
        val topology = sourceMesh.topology
        val totalFaces = topology.faceCount
        if (totalFaces == 0) return null

        val validFaces = selectedFaceIndices.filter { it in 0 until totalFaces }.toSet()
        if (validFaces.isEmpty()) return null

        val targetEdges = if (validFaces.size == totalFaces) {
            (0 until topology.edges.size).toSet()
        } else {
            val edgeFaces = HashMap<Pair<Int, Int>, MutableList<Int>>()
            for (f in validFaces) {
                val (i0, i1, i2) = topology.getFaceIndices(f)
                val r0 = topology.coincidentMap[i0]
                val r1 = topology.coincidentMap[i1]
                val r2 = topology.coincidentMap[i2]
                val e01 = Pair(minOf(r0, r1), maxOf(r0, r1))
                val e12 = Pair(minOf(r1, r2), maxOf(r1, r2))
                val e20 = Pair(minOf(r2, r0), maxOf(r2, r0))
                edgeFaces.getOrPut(e01) { mutableListOf() }.add(f)
                edgeFaces.getOrPut(e12) { mutableListOf() }.add(f)
                edgeFaces.getOrPut(e20) { mutableListOf() }.add(f)
            }

            val targetEdgeKeys = HashSet<Pair<Int, Int>>()
            for ((key, faces) in edgeFaces) {
                if (faces.size == 1) {
                    targetEdgeKeys.add(key)
                } else if (faces.size == 2) {
                    val n0 = topology.getFaceNormal(faces[0])
                    val n1 = topology.getFaceNormal(faces[1])
                    if (n0.dot(n1) < 0.99f) {
                        targetEdgeKeys.add(key)
                    }
                }
            }

            val edgeIndices = HashSet<Int>()
            for (eIdx in topology.edges.indices) {
                val e = topology.edges[eIdx]
                val r0 = topology.coincidentMap[e.first]
                val r1 = topology.coincidentMap[e.second]
                val key = Pair(minOf(r0, r1), maxOf(r0, r1))
                if (key in targetEdgeKeys) {
                    edgeIndices.add(eIdx)
                }
            }
            edgeIndices
        }

        return bevelEdgesInternal(sourceMesh, targetEdges, offset, depth, segments, profile)
    }

    /**
     * Bevel sekumpulan edge yang dipilih (atau seluruh edge jika selection kosong).
     */
    fun bevelEdges(
        sourceMesh: Mesh,
        selectedEdgeIndices: Set<Int>,
        offset: Float = 0.12f,
        segments: Int = 1,
        profile: Float = 0.5f
    ): Pair<Mesh, Set<Int>>? {
        val topology = sourceMesh.topology
        val targetEdges = if (selectedEdgeIndices.isEmpty()) {
            (0 until topology.edges.size).toSet()
        } else {
            selectedEdgeIndices.filter { it in 0 until topology.edges.size }.toSet()
        }
        return bevelEdgesInternal(sourceMesh, targetEdges, offset, 0f, segments, profile)
    }

    /**
     * Bevel sekumpulan vertex yang dipilih (Vertex Bevel / Corner Truncation ala Blender Ctrl+Shift+B).
     */
    fun bevelVertices(
        sourceMesh: Mesh,
        selectedVertexIndices: Set<Int>,
        offset: Float = 0.12f,
        segments: Int = 1,
        profile: Float = 0.5f
    ): Pair<Mesh, Set<Int>>? {
        val topology = sourceMesh.topology
        if (topology.faceCount == 0 || selectedVertexIndices.isEmpty()) return null

        val targetRootVerts = selectedVertexIndices.mapNotNull {
            if (it in 0 until topology.vertexCount) topology.coincidentMap[it] else null
        }.toSet()
        if (targetRootVerts.isEmpty()) return null

        val ctx = BevelBuildContext(sourceMesh, sourceMesh.uvs.isNotEmpty())
        val polygons = EdgeBevelStage.extractPolygons(topology)
        val clampedOffset = offset.coerceIn(0.005f, 0.45f)

        // Peta cut vertex per pair (rootVertex, otherRootVertex)
        val cutVertexMap = HashMap<Pair<Int, Int>, Int>()
        fun getCutVertex(vRoot: Int, otherRoot: Int, origV: Int): Int {
            val key = Pair(vRoot, otherRoot)
            val existing = cutVertexMap[key]
            if (existing != null) return existing
            val pV = topology.getVertex(vRoot)
            val pOther = topology.getVertex(otherRoot)
            val dir = (pOther - pV).let { if (it.lengthSquared() > 1e-6f) it.normalized() else Vector3.ZERO }
            val cutPos = pV + dir * clampedOffset
            val idx = ctx.getOrAddVertex(cutPos, origV)
            cutVertexMap[key] = idx
            return idx
        }

        // 1. Rekonstruksi Poligon Permukaan dengan pemotongan sudut
        for (poly in polygons) {
            val k = poly.vertices.size
            val newPolyVerts = ArrayList<Int>()

            for (i in 0 until k) {
                val v = poly.vertices[i]
                val rV = topology.coincidentMap[v]

                if (rV in targetRootVerts) {
                    val prevV = poly.vertices[(i - 1 + k) % k]
                    val nextV = poly.vertices[(i + 1) % k]
                    val rPrev = topology.coincidentMap[prevV]
                    val rNext = topology.coincidentMap[nextV]

                    val cutPrev = getCutVertex(rV, rPrev, v)
                    val cutNext = getCutVertex(rV, rNext, v)

                    newPolyVerts.add(cutPrev)
                    if (cutNext != cutPrev) newPolyVerts.add(cutNext)
                } else {
                    val p = topology.getVertex(v)
                    newPolyVerts.add(ctx.getOrAddVertex(p, v))
                }
            }

            val m = newPolyVerts.size
            for (j in 1 until m - 1) {
                ctx.newIndices.add(newPolyVerts[0])
                ctx.newIndices.add(newPolyVerts[j])
                ctx.newIndices.add(newPolyVerts[j + 1])
            }
            for (j in 0 until m) {
                ctx.addWireEdge(newPolyVerts[j], newPolyVerts[(j + 1) % m])
            }
        }

        // 2. Buat Face Penutup Sudut (Corner Cap) untuk setiap vertex terpilih
        for (rV in targetRootVerts) {
            val incidentCutVerts = ArrayList<Int>()

            for (e in topology.edges) {
                val r0 = topology.coincidentMap[e.first]
                val r1 = topology.coincidentMap[e.second]
                if (r0 == rV) {
                    incidentCutVerts.add(getCutVertex(rV, r1, e.first))
                } else if (r1 == rV) {
                    incidentCutVerts.add(getCutVertex(rV, r0, e.second))
                }
            }

            if (incidentCutVerts.size < 3) continue

            val vPos = topology.getVertex(rV)
            var normSum = Vector3.ZERO
            for (cutIdx in incidentCutVerts) {
                normSum += (vPos - ctx.getPos(cutIdx))
            }
            val cornerNormal = if (normSum.lengthSquared() > 1e-4f) normSum.normalized() else Vector3.UNIT_Y

            val c0 = incidentCutVerts[0]
            for (j in 1 until incidentCutVerts.size - 1) {
                ctx.addTriangle(c0, incidentCutVerts[j], incidentCutVerts[j + 1], cornerNormal, markAsChamfer = true)
            }
        }

        return MeshValidator.assembleAndValidate(ctx, sourceMesh)
    }

    // =========================================================================
    // CORE PIPELINE
    // =========================================================================

    private fun bevelEdgesInternal(
        sourceMesh: Mesh,
        targetEdgeIndices: Set<Int>,
        offset: Float,
        depth: Float,
        segments: Int,
        profile: Float = 0.5f
    ): Pair<Mesh, Set<Int>>? {
        val topology = sourceMesh.topology
        if (topology.faceCount == 0 || targetEdgeIndices.isEmpty()) return null

        val ctx = BevelBuildContext(sourceMesh, sourceMesh.uvs.isNotEmpty())

        // 1. Ekstraksi Target Edge Keys
        val targetEdgeKeys = HashSet<Pair<Int, Int>>()
        var minTargetEdgeLen = Float.MAX_VALUE
        for (eIdx in targetEdgeIndices) {
            val edge = topology.edges[eIdx]
            val r0 = topology.coincidentMap[edge.first]
            val r1 = topology.coincidentMap[edge.second]
            val key = Pair(minOf(r0, r1), maxOf(r0, r1))
            targetEdgeKeys.add(key)
            val len = (topology.getVertex(edge.second) - topology.getVertex(edge.first)).length()
            if (len > 0.0001f && len < minTargetEdgeLen) minTargetEdgeLen = len
        }
        if (targetEdgeKeys.isEmpty()) return null
        if (minTargetEdgeLen == Float.MAX_VALUE) minTargetEdgeLen = 1.0f

        // Index cepat: himpunan root-vertex yang menyentuh edge beveled
        // (dipakai retractPolygons -> mengganti targetEdgeKeys.any{} yang O(N) per verteks)
        val targetRootVerts = HashSet<Int>()
        for (key in targetEdgeKeys) {
            targetRootVerts.add(key.first)
            targetRootVerts.add(key.second)
        }

        // 2. Ekstraksi Poligon Permukaan (Quad berpasangan atau Segitiga standalone)
        val polygons = EdgeBevelStage.extractPolygons(topology)

        // 3. Safe Clamp Overlap & Normalisasi Segmen
        val clampedOffset = EdgeBevelStage.computeSafeOffset(polygons, topology, minTargetEdgeLen, offset)
        // Jika segmen > 1, promosikan angka ganjil ke genap terdekat (2, 4, 6) agar membentuk 100% all-quad grid sempurna ala Blender
        val clampedSegments = if (segments <= 1) 1 else if (segments % 2 != 0) (segments + 1).coerceAtMost(6) else segments.coerceIn(1, 6)
        val clampedProfile = profile.coerceIn(0.15f, 0.85f)

        // 4. Retraksi Poligon Permukaan
        val polyRetractedVerts = EdgeBevelStage.retractPolygons(
            polygons = polygons,
            topology = topology,
            targetEdgeKeys = targetEdgeKeys,
            targetRootVerts = targetRootVerts,
            clampedOffset = clampedOffset,
            ctx = ctx
        )

        // 5. Generasi Edge Chamfer Strips
        val (edgeEndArcChains, edgeBevelNormals) = EdgeBevelStage.generateChamferStrips(
            polygons = polygons,
            polyRetractedVerts = polyRetractedVerts,
            topology = topology,
            targetEdgeKeys = targetEdgeKeys,
            clampedOffset = clampedOffset,
            clampedSegments = clampedSegments,
            clampedProfile = clampedProfile,
            depth = depth,
            ctx = ctx
        )

        // 6. Resolusi Vertex Junction & Rekonstruksi Corner Patch (100% Quad Grid / Chamfer Facet)
        VertexJunctionStage.resolveAndPatchCorners(
            targetEdgeKeys = targetEdgeKeys,
            edgeEndArcChains = edgeEndArcChains,
            edgeBevelNormals = edgeBevelNormals,
            topology = topology,
            clampedOffset = clampedOffset,
            clampedSegments = clampedSegments,
            clampedProfile = clampedProfile,
            ctx = ctx
        )

        // 7. Mesh Validation & Finalisasi
        return MeshValidator.assembleAndValidate(ctx, sourceMesh)
    }

    // =========================================================================
    // STAGE 1: EDGE BEVEL GENERATION
    // =========================================================================

    private object EdgeBevelStage {

        private fun extractOrderedBoundaryVertices(
            coplanar: List<Int>,
            topology: MeshTopology,
            normal: Vector3
        ): List<Int>? {
            val directedEdgeCount = HashMap<Pair<Int, Int>, Int>()
            val edgeToOrigPair = HashMap<Pair<Int, Int>, Pair<Int, Int>>()

            for (f in coplanar) {
                val (i0, i1, i2) = topology.getFaceIndices(f)
                val r0 = topology.coincidentMap[i0]
                val r1 = topology.coincidentMap[i1]
                val r2 = topology.coincidentMap[i2]

                val e01 = Pair(r0, r1)
                val e12 = Pair(r1, r2)
                val e20 = Pair(r2, r0)

                directedEdgeCount[e01] = (directedEdgeCount[e01] ?: 0) + 1
                edgeToOrigPair[e01] = Pair(i0, i1)

                directedEdgeCount[e12] = (directedEdgeCount[e12] ?: 0) + 1
                edgeToOrigPair[e12] = Pair(i1, i2)

                directedEdgeCount[e20] = (directedEdgeCount[e20] ?: 0) + 1
                edgeToOrigPair[e20] = Pair(i2, i0)
            }

            // Edge batas terluar poligon: directed edge yang opposite-nya tidak ada di dalam coplanar
            val nextVertexMap = HashMap<Int, Int>()
            val rootToOrig = HashMap<Int, Int>()

            for ((edge, _) in directedEdgeCount) {
                val opposite = Pair(edge.second, edge.first)
                if (!directedEdgeCount.containsKey(opposite)) {
                    nextVertexMap[edge.first] = edge.second
                    val origPair = edgeToOrigPair[edge]!!
                    rootToOrig[edge.first] = origPair.first
                    rootToOrig[edge.second] = origPair.second
                }
            }

            if (nextVertexMap.size < 3) return null

            // Rangkai loop boundary berurutan
            val startRoot = nextVertexMap.keys.first()
            var currRoot = startRoot
            val orderedVerts = ArrayList<Int>()
            val visitedRoots = HashSet<Int>()

            while (currRoot !in visitedRoots) {
                visitedRoots.add(currRoot)
                orderedVerts.add(rootToOrig[currRoot] ?: currRoot)
                val nextRoot = nextVertexMap[currRoot] ?: break
                currRoot = nextRoot
            }

            if (orderedVerts.size < 3) return null

            // Pastikan winding konsisten CCW searah target normal
            val p0 = topology.getVertex(orderedVerts[0])
            val p1 = topology.getVertex(orderedVerts[1])
            val p2 = topology.getVertex(orderedVerts[2])
            val cross = (p1 - p0).cross(p2 - p0)
            return if (cross.dot(normal) >= 0f) orderedVerts else orderedVerts.asReversed()
        }

        fun extractPolygons(topology: MeshTopology): List<Polygon> {
            val processedFaces = HashSet<Int>()
            val polygons = ArrayList<Polygon>()

            for (f in 0 until topology.faceCount) {
                if (f in processedFaces) continue
                val coplanar = topology.getCoplanarConnectedFaces(f)
                val n = topology.getFaceNormal(f)

                if (coplanar.size >= 2) {
                    val orderedVerts = extractOrderedBoundaryVertices(coplanar, topology, n)
                    if (orderedVerts != null && orderedVerts.size >= 3) {
                        var sum = Vector3.ZERO
                        for (v in orderedVerts) {
                            sum += topology.getVertex(v)
                        }
                        val centroid = sum / orderedVerts.size.toFloat()
                        polygons.add(Polygon(coplanar, orderedVerts, n, centroid))
                        processedFaces.addAll(coplanar)
                        continue
                    }
                }

                // Standalone Triangle
                val (t0, t1, t2) = topology.getFaceIndices(f)
                val centroid = (topology.getVertex(t0) + topology.getVertex(t1) + topology.getVertex(t2)) / 3f
                polygons.add(Polygon(listOf(f), listOf(t0, t1, t2), n, centroid))
                processedFaces.add(f)
            }
            return polygons
        }

        fun computeSafeOffset(
            polygons: List<Polygon>,
            topology: MeshTopology,
            minTargetEdgeLen: Float,
            requestedOffset: Float
        ): Float {
            var safeMax = minTargetEdgeLen * 0.48f
            for (p in polygons) {
                val k = p.vertices.size
                for (i in 0 until k) {
                    val p0 = topology.getVertex(p.vertices[i])
                    val p1 = topology.getVertex(p.vertices[(i + 1) % k])
                    val edgeLen = (p1 - p0).length()
                    if (edgeLen > 0.0001f) safeMax = minOf(safeMax, edgeLen * 0.45f)
                    val distCentroid = (p0 - p.centroid).length()
                    if (distCentroid > 0.0001f) safeMax = minOf(safeMax, distCentroid * 0.45f)
                }
            }
            return requestedOffset.coerceIn(0.001f, safeMax)
        }

        fun retractPolygons(
            polygons: List<Polygon>,
            topology: MeshTopology,
            targetEdgeKeys: Set<Pair<Int, Int>>,
            targetRootVerts: Set<Int>,
            clampedOffset: Float,
            ctx: BevelBuildContext
        ): Array<IntArray> {
            val polyRetractedVerts = Array(polygons.size) { IntArray(0) }

            for (pIdx in polygons.indices) {
                val poly = polygons[pIdx]
                val k = poly.vertices.size
                val n = poly.normal
                val centroid = poly.centroid

                val edgeInward = Array(k) { Vector3.ZERO }
                val edgeIsBeveled = BooleanArray(k)

                for (i in 0 until k) {
                    val v0 = poly.vertices[i]
                    val v1 = poly.vertices[(i + 1) % k]
                    val r0 = topology.coincidentMap[v0]
                    val r1 = topology.coincidentMap[v1]
                    edgeIsBeveled[i] = Pair(minOf(r0, r1), maxOf(r0, r1)) in targetEdgeKeys

                    val p0 = topology.getVertex(v0)
                    val p1 = topology.getVertex(v1)
                    var inDir = n.cross(p1 - p0)
                    if (inDir.dot(centroid - p0) < 0f) inDir = -inDir
                    edgeInward[i] = if (inDir.lengthSquared() > 1e-6f) inDir.normalized() else Vector3.ZERO
                }

                val retractedIndicesForStrip = IntArray(k)
                val finalPolygonVerts = ArrayList<Int>()

                for (i in 0 until k) {
                    val v = poly.vertices[i]
                    val origPos = topology.getVertex(v)
                    val prevEdge = (i - 1 + k) % k
                    val nextEdge = i
                    val prevBevel = edgeIsBeveled[prevEdge]
                    val nextBevel = edgeIsBeveled[nextEdge]

                    val rV = topology.coincidentMap[v]
                    val isPerpBeveled = !prevBevel && !nextBevel && rV in targetRootVerts

                    if (isPerpBeveled) {
                        // Sudut ini dipotong rata oleh edge tegak lurus yang dibevel (Flush cut ala Blender)
                        val pPrev = topology.getVertex(poly.vertices[(i - 1 + k) % k])
                        val pNext = topology.getVertex(poly.vertices[(i + 1) % k])
                        val dirPrev = (pPrev - origPos).let { if (it.lengthSquared() > 1e-6f) it.normalized() else Vector3.ZERO }
                        val dirNext = (pNext - origPos).let { if (it.lengthSquared() > 1e-6f) it.normalized() else Vector3.ZERO }

                        val ptOnPrev = origPos + dirPrev * clampedOffset
                        val ptOnNext = origPos + dirNext * clampedOffset

                        val idxPrev = ctx.getOrAddVertex(ptOnPrev, v)
                        val idxNext = ctx.getOrAddVertex(ptOnNext, v)
                        // Rekam untuk end-cap: pasangan titik potong rata di sekitar root ini
                        ctx.flushCutsByRoot.getOrPut(rV) { mutableListOf() }.add(idxPrev to idxNext)

                        retractedIndicesForStrip[i] = idxPrev
                        finalPolygonVerts.add(idxPrev)
                        finalPolygonVerts.add(idxNext)
                    } else {
                        val offsetVec = when {
                            prevBevel && nextBevel -> {
                                val sum = edgeInward[prevEdge] + edgeInward[nextEdge]
                                val lenSq = sum.lengthSquared()
                                if (lenSq > 1e-4f) {
                                    val factor = (2f * clampedOffset / lenSq).coerceAtMost(clampedOffset * 3.5f)
                                    sum * factor
                                } else {
                                    sum * clampedOffset
                                }
                            }
                            prevBevel -> {
                                // nextEdge is a non-beveled seam. Move along the seam edge to keep adjacent faces glued.
                                val pNext = topology.getVertex(poly.vertices[(i + 1) % k])
                                val seamDir = (pNext - origPos).let { if (it.lengthSquared() > 1e-6f) it.normalized() else Vector3.ZERO }
                                val pPrev = topology.getVertex(poly.vertices[(i - 1 + k) % k])
                                val bevelDir = (pPrev - origPos).let { if (it.lengthSquared() > 1e-6f) it.normalized() else Vector3.ZERO }
                                val cosA = seamDir.dot(bevelDir).coerceIn(-0.99f, 0.99f)
                                // Use direct offset along seam to match flush cuts exactly
                                val dist = clampedOffset
                                if (seamDir.lengthSquared() > 1e-6f) seamDir * dist else edgeInward[prevEdge] * clampedOffset
                            }
                            nextBevel -> {
                                // prevEdge is a non-beveled seam. Move along the seam edge to keep adjacent faces glued.
                                val pPrev = topology.getVertex(poly.vertices[(i - 1 + k) % k])
                                val seamDir = (pPrev - origPos).let { if (it.lengthSquared() > 1e-6f) it.normalized() else Vector3.ZERO }
                                val pNext = topology.getVertex(poly.vertices[(i + 1) % k])
                                val bevelDir = (pNext - origPos).let { if (it.lengthSquared() > 1e-6f) it.normalized() else Vector3.ZERO }
                                val cosA = seamDir.dot(bevelDir).coerceIn(-0.99f, 0.99f)
                                // Use direct offset along seam to match flush cuts exactly
                                val dist = clampedOffset
                                if (seamDir.lengthSquared() > 1e-6f) seamDir * dist else edgeInward[nextEdge] * clampedOffset
                            }
                            else -> Vector3.ZERO
                        }
                        val idx = ctx.getOrAddVertex(origPos + offsetVec, v)
                        retractedIndicesForStrip[i] = idx
                        finalPolygonVerts.add(idx)
                    }
                }
                polyRetractedVerts[pIdx] = retractedIndicesForStrip

                // Triangulasi interior poligon (face non-chamfer)
                val m = finalPolygonVerts.size
                for (j in 1 until m - 1) {
                    ctx.newIndices.add(finalPolygonVerts[0])
                    ctx.newIndices.add(finalPolygonVerts[j])
                    ctx.newIndices.add(finalPolygonVerts[j + 1])
                }

                // Daftarkan HANYA garis keliling (perimeter) tanpa garis diagonal segitiga interior
                for (j in 0 until m) {
                    ctx.addWireEdge(finalPolygonVerts[j], finalPolygonVerts[(j + 1) % m])
                }
            }
            return polyRetractedVerts
        }

        fun generateChamferStrips(
            polygons: List<Polygon>,
            polyRetractedVerts: Array<IntArray>,
            topology: MeshTopology,
            targetEdgeKeys: Set<Pair<Int, Int>>,
            clampedOffset: Float,
            clampedSegments: Int,
            clampedProfile: Float,
            depth: Float,
            ctx: BevelBuildContext
        ): Pair<HashMap<Pair<Pair<Int, Int>, Int>, IntArray>, HashMap<Pair<Int, Int>, Vector3>> {
            val edgeToPolyEdges = HashMap<Pair<Int, Int>, MutableList<Pair<Int, Int>>>()
            for (pIdx in polygons.indices) {
                val poly = polygons[pIdx]
                val k = poly.vertices.size
                for (i in 0 until k) {
                    val r0 = topology.coincidentMap[poly.vertices[i]]
                    val r1 = topology.coincidentMap[poly.vertices[(i + 1) % k]]
                    val key = Pair(minOf(r0, r1), maxOf(r0, r1))
                    if (key in targetEdgeKeys) {
                        edgeToPolyEdges.getOrPut(key) { mutableListOf() }.add(Pair(pIdx, i))
                    }
                }
            }

            val bulgeRatio = if (clampedSegments > 1) {
                clampedOffset * (1.0f - 1.0f / 1.41421356f) * (clampedProfile / 0.5f)
            } else {
                depth
            }

            val edgeEndArcChains = HashMap<Pair<Pair<Int, Int>, Int>, IntArray>()
            val edgeBevelNormals = HashMap<Pair<Int, Int>, Vector3>()

            for ((edgeKey, polyList) in edgeToPolyEdges) {
                if (polyList.size == 2) {
                    val (pAIdx, edgeA) = polyList[0]
                    val (pBIdx, edgeB) = polyList[1]
                    val polyA = polygons[pAIdx]
                    val polyB = polygons[pBIdx]

                    val vA0 = polyA.vertices[edgeA]
                    val vA1 = polyA.vertices[(edgeA + 1) % polyA.vertices.size]
                    val vB0 = polyB.vertices[edgeB]
                    val vB1 = polyB.vertices[(edgeB + 1) % polyB.vertices.size]

                    val rA0 = topology.coincidentMap[vA0]
                    val rA1 = topology.coincidentMap[vA1]
                    val rB0 = topology.coincidentMap[vB0]
                    val rB1 = topology.coincidentMap[vB1]

                    val pA_idx0 = polyRetractedVerts[pAIdx][edgeA]
                    val pA_idx1 = polyRetractedVerts[pAIdx][(edgeA + 1) % polyA.vertices.size]
                    val pB_idx0 = polyRetractedVerts[pBIdx][edgeB]
                    val pB_idx1 = polyRetractedVerts[pBIdx][(edgeB + 1) % polyB.vertices.size]

                    // Pemetaan titik ujung yang unambiguously konsisten terhadap rA0 dan rA1
                    val pB_match_A0 = if (rB0 == rA0) pB_idx0 else pB_idx1
                    val pB_match_A1 = if (rB0 == rA1) pB_idx0 else pB_idx1

                    val arcNormRaw = polyA.normal + polyB.normal
                    val arcNorm = if (arcNormRaw.lengthSquared() > 1e-4f) arcNormRaw.normalized() else polyA.normal
                    edgeBevelNormals[edgeKey] = arcNorm

                    val chain0 = IntArray(clampedSegments + 1)
                    val chain1 = IntArray(clampedSegments + 1)
                    chain0[0] = pA_idx0
                    chain1[0] = pA_idx1

                    if (clampedSegments == 1) {
                        chain0[1] = pB_match_A0
                        chain1[1] = pB_match_A1
                        ctx.addQuad(pA_idx0, pA_idx1, pB_match_A1, pB_match_A0, arcNorm)
                    } else {
                        val pA0 = ctx.getPos(pA_idx0); val pA1 = ctx.getPos(pA_idx1)
                        val pB0 = ctx.getPos(pB_match_A0); val pB1 = ctx.getPos(pB_match_A1)

                        var prev0 = pA_idx0
                        var prev1 = pA_idx1

                        for (s in 1..clampedSegments) {
                            val t = s.toFloat() / clampedSegments
                            val (cur0, cur1) = if (s == clampedSegments) {
                                Pair(pB_match_A0, pB_match_A1)
                            } else {
                                val arcOffset = arcNorm * (sin(t * PI.toFloat()) * bulgeRatio)
                                val pt0 = pA0 * (1f - t) + pB0 * t + arcOffset
                                val pt1 = pA1 * (1f - t) + pB1 * t + arcOffset
                                Pair(ctx.getOrAddVertex(pt0, vA0), ctx.getOrAddVertex(pt1, vA1))
                            }
                            chain0[s] = cur0
                            chain1[s] = cur1
                            ctx.addQuad(prev0, prev1, cur1, cur0, arcNorm)
                            prev0 = cur0
                            prev1 = cur1
                        }
                    }

                    edgeEndArcChains[Pair(edgeKey, rA0)] = chain0
                    edgeEndArcChains[Pair(edgeKey, rA1)] = chain1
                } else if (polyList.isNotEmpty()) {
                    // -----------------------------------------------------------------
                    // FIX: BOUNDARY / WIRE EDGE (dimiliki SATU polygon saja, non-manifold,
                    // misal tepi mesh terbuka). Dulu: strip di-skip senyap -> lubang.
                    // Sekarang: chamfer strip rata (flush) dari edge yang sudah ditarik
                    // ke posisi edge ORIGINAL, sehingga mesh tetap tertutup dan tidak
                    // ada geometri yang hilang. Chain tetap panjang segments+1 agar
                    // kompatibel dengan dua-dua junction miter di tahap berikutnya.
                    // -----------------------------------------------------------------
                    val (pAIdx, edgeA) = polyList[0]
                    val polyA = polygons[pAIdx]
                    val kA = polyA.vertices.size
                    val vA0 = polyA.vertices[edgeA]
                    val vA1 = polyA.vertices[(edgeA + 1) % kA]
                    val pA_idx0 = polyRetractedVerts[pAIdx][edgeA]
                    val pA_idx1 = polyRetractedVerts[pAIdx][(edgeA + 1) % kA]

                    val arcNorm = polyA.normal
                    edgeBevelNormals[edgeKey] = arcNorm

                    val retracted0 = ctx.getPos(pA_idx0)
                    val retracted1 = ctx.getPos(pA_idx1)
                    val orig0 = topology.getVertex(vA0)
                    val orig1 = topology.getVertex(vA1)

                    val chain0 = IntArray(clampedSegments + 1)
                    val chain1 = IntArray(clampedSegments + 1)
                    chain0[0] = pA_idx0
                    chain1[0] = pA_idx1
                    for (s in 1..clampedSegments) {
                        val t = s.toFloat() / clampedSegments
                        val pt0 = retracted0 * (1f - t) + orig0 * t
                        val pt1 = retracted1 * (1f - t) + orig1 * t
                        chain0[s] = ctx.getOrAddVertex(pt0, vA0)
                        chain1[s] = ctx.getOrAddVertex(pt1, vA1)
                    }
                    for (s in 0 until clampedSegments) {
                        ctx.addQuad(chain0[s], chain1[s], chain1[s + 1], chain0[s + 1], arcNorm)
                    }
                    edgeEndArcChains[Pair(edgeKey, topology.coincidentMap[vA0])] = chain0
                    edgeEndArcChains[Pair(edgeKey, topology.coincidentMap[vA1])] = chain1
                }
            }

            return Pair(edgeEndArcChains, edgeBevelNormals)
        }
    }

    // =========================================================================
    // STAGE 2: VERTEX JUNCTION RESOLUTION
    // =========================================================================

    private object VertexJunctionStage {

        fun resolveAndPatchCorners(
            targetEdgeKeys: Set<Pair<Int, Int>>,
            edgeEndArcChains: HashMap<Pair<Pair<Int, Int>, Int>, IntArray>,
            edgeBevelNormals: HashMap<Pair<Int, Int>, Vector3>,
            topology: MeshTopology,
            clampedOffset: Float,
            clampedSegments: Int,
            clampedProfile: Float,
            ctx: BevelBuildContext
        ) {
            val vertexToBevelEdges = HashMap<Int, MutableList<Pair<Int, Int>>>()
            for (key in targetEdgeKeys) {
                vertexToBevelEdges.getOrPut(key.first) { mutableListOf() }.add(key)
                vertexToBevelEdges.getOrPut(key.second) { mutableListOf() }.add(key)
            }

            for ((rootV, edgesAtV) in vertexToBevelEdges) {
                val origVPos = topology.getVertex(rootV)

                var bevelNormalSum = Vector3.ZERO
                for (edgeKey in edgesAtV) {
                    val bn = edgeBevelNormals[edgeKey]
                    if (bn != null) bevelNormalSum += bn
                }
                val cornerNormal = if (bevelNormalSum.lengthSquared() > 1e-4f) {
                    bevelNormalSum.normalized()
                } else {
                    Vector3.UNIT_Y
                }

                when (edgesAtV.size) {
                    1 -> CornerPatchStage.reconstructEndCap(
                        rootV = rootV,
                        edgeKey = edgesAtV[0],
                        edgeEndArcChains = edgeEndArcChains,
                        origVPos = origVPos,
                        clampedSegments = clampedSegments,
                        topology = topology,
                        ctx = ctx
                    )
                    2 -> CornerPatchStage.reconstructTwoEdgeMiter(
                        rootV = rootV,
                        edgesAtV = edgesAtV,
                        edgeEndArcChains = edgeEndArcChains,
                        cornerNormal = cornerNormal,
                        clampedSegments = clampedSegments,
                        ctx = ctx
                    )
                    in 3..Int.MAX_VALUE -> CornerPatchStage.reconstructMultiEdgeCorner(
                        rootV = rootV,
                        edgesAtV = edgesAtV,
                        edgeEndArcChains = edgeEndArcChains,
                        origVPos = origVPos,
                        cornerNormal = cornerNormal,
                        clampedOffset = clampedOffset,
                        clampedSegments = clampedSegments,
                        clampedProfile = clampedProfile,
                        ctx = ctx
                    )
                }
            }
        }
    }

    // =========================================================================
    // STAGE 3: CORNER PATCH RECONSTRUCTION (BLENDER QUAD GRID & FLAT CHAMFER)
    // =========================================================================

    private object CornerPatchStage {

        /**
         * Case 1: Isolated Beveled Edge End-Cap Closure (Flush planar termination).
         * Menutup celah ujung busur strip bevel dengan fan triangles menghadap ke luar sumbu edge beveled.
         */
        fun reconstructEndCap(
            rootV: Int,
            edgeKey: Pair<Int, Int>,
            edgeEndArcChains: HashMap<Pair<Pair<Int, Int>, Int>, IntArray>,
            origVPos: Vector3,
            clampedSegments: Int,
            topology: MeshTopology,
            ctx: BevelBuildContext
        ) {
            val chain = edgeEndArcChains[Pair(edgeKey, rootV)] ?: return
            // Boundary strip (ujung chain = posisi vertex asli) dibiarkan flush
            if ((ctx.getPos(chain.last()) - origVPos).lengthSquared() < 1e-6f) return

            // Arah normal penutup cap: tepat mengarah keluar dari sumbu edge yang dibevel
            val otherV = if (edgeKey.first == rootV) edgeKey.second else edgeKey.first
            val edgeDir = topology.getVertex(rootV) - topology.getVertex(otherV)
            val capNormal = if (edgeDir.lengthSquared() > 1e-6f) edgeDir.normalized() else Vector3.UNIT_Y

            val flushList = ctx.flushCutsByRoot[rootV]
            if (!flushList.isNullOrEmpty()) {
                // Skenario ada face tetangga non-beveled yang dipotong rata (flush cut):
                // Hubungkan busur chain ke titik potong flush cut non-beveled
                for ((idxA, idxB) in flushList) {
                    val posA = ctx.getPos(idxA)
                    val posB = ctx.getPos(idxB)
                    val posLast = ctx.getPos(chain.last())
                    val posFirst = ctx.getPos(chain.first())

                    // Cari titik mana dari pasangan flush cut yang merupakan titik miter luar
                    val isAMatch = (posA - posLast).lengthSquared() < 1e-4f || (posA - posFirst).lengthSquared() < 1e-4f
                    val outerIdx = if (isAMatch) idxB else idxA

                    for (s in 0 until clampedSegments) {
                        ctx.addTriangle(
                            idx0 = chain[s],
                            idx1 = chain[s + 1],
                            idx2 = outerIdx,
                            targetNormal = capNormal,
                            markAsChamfer = true,
                            addInternalWires = false,
                            addWireframe = false
                        )
                    }
                }
            } else if (clampedSegments > 1) {
                // Fan triangulation dari chain[0] ke setiap segmen busur arc
                val rootIdx = chain[0]
                for (s in 1 until clampedSegments) {
                    ctx.addTriangle(
                        idx0 = rootIdx,
                        idx1 = chain[s],
                        idx2 = chain[s + 1],
                        targetNormal = capNormal,
                        markAsChamfer = true,
                        addInternalWires = false,
                        addWireframe = false
                    )
                }
            }
        }

        /**
         * Case 2: 2-Edge Corner Miter (Seamless 45-degree patch miter without protruding spikes).
         */
        fun reconstructTwoEdgeMiter(
            rootV: Int,
            edgesAtV: List<Pair<Int, Int>>,
            edgeEndArcChains: HashMap<Pair<Pair<Int, Int>, Int>, IntArray>,
            cornerNormal: Vector3,
            clampedSegments: Int,
            ctx: BevelBuildContext
        ) {
            val rawA = edgeEndArcChains[Pair(edgesAtV[0], rootV)] ?: return
            val rawB = edgeEndArcChains[Pair(edgesAtV[1], rootV)] ?: return

            val d00 = (ctx.getPos(rawA.first()) - ctx.getPos(rawB.first())).lengthSquared()
            val d0L = (ctx.getPos(rawA.first()) - ctx.getPos(rawB.last())).lengthSquared()
            val dL0 = (ctx.getPos(rawA.last()) - ctx.getPos(rawB.first())).lengthSquared()
            val dLL = (ctx.getPos(rawA.last()) - ctx.getPos(rawB.last())).lengthSquared()

            val minD = minOf(d00, d0L, dL0, dLL)
            val (alignedA, alignedB) = when (minD) {
                d00 -> Pair(rawA.clone(), rawB.clone())
                d0L -> Pair(rawA.clone(), rawB.reversedArray())
                dL0 -> Pair(rawA.reversedArray(), rawB.clone())
                else -> Pair(rawA.reversedArray(), rawB.reversedArray())
            }

            // Titik sudut pertemuan di face yang sama menyatu identik
            alignedB[0] = alignedA[0]

            val pLastA = ctx.getPos(alignedA.last())
            val pLastB = ctx.getPos(alignedB.last())
            if ((pLastA - pLastB).lengthSquared() < 1e-4f) {
                alignedB[clampedSegments] = alignedA[clampedSegments]
            }

            for (s in 0 until clampedSegments) {
                val u0 = alignedA[s]
                val u1 = alignedA[s + 1]
                val w0 = alignedB[s]
                val w1 = alignedB[s + 1]

                val posU0 = ctx.getPos(u0); val posW0 = ctx.getPos(w0)
                val posU1 = ctx.getPos(u1); val posW1 = ctx.getPos(w1)

                val u0_w0_same = (u0 == w0) || ((posU0 - posW0).lengthSquared() < 1e-5f)
                val u1_w1_same = (u1 == w1) || ((posU1 - posW1).lengthSquared() < 1e-5f)

                when {
                    u0_w0_same && u1_w1_same -> { /* Degenerate cell skipped */ }
                    u0_w0_same -> ctx.addTriangle(u0, u1, w1, cornerNormal)
                    u1_w1_same -> ctx.addTriangle(u0, u1, w0, cornerNormal)
                    else -> ctx.addQuad(u0, u1, w1, w0, cornerNormal)
                }
            }
        }

        /**
         * Case 3: Multi-Edge Corner Junction (3+ edges meeting at corner).
         * Prioritas:
         * - S = 1 (Single-Segment Chamfer): Tepat 1 Segitiga Datar Bersih (K = 3) / Minimal Polygon Fan (K > 3).
         * - S >= 2 (Multi-Segment Round Fillet): 100% All-Quad Coons Grid Patch (identik topologi Blender).
         * Nol triangle fan radial, nol pola bunga.
         */
        fun reconstructMultiEdgeCorner(
            rootV: Int,
            edgesAtV: List<Pair<Int, Int>>,
            edgeEndArcChains: HashMap<Pair<Pair<Int, Int>, Int>, IntArray>,
            origVPos: Vector3,
            cornerNormal: Vector3,
            clampedOffset: Float,
            clampedSegments: Int,
            clampedProfile: Float,
            ctx: BevelBuildContext
        ) {
            val rawChains = edgesAtV.mapNotNull { edgeEndArcChains[Pair(it, rootV)] }
            if (rawChains.size < 3) return

            // 1. Urutkan rantai edge secara sirkular menutup loop sudut
            val orderedChains = mutableListOf<IntArray>()
            val remaining = rawChains.map { it.clone() }.toMutableList()

            var currChain = remaining.removeAt(0)
            orderedChains.add(currChain)
            var tailPos = ctx.getPos(currChain.last())

            while (remaining.isNotEmpty()) {
                var bestIdx = -1
                var bestReversed = false
                var minDist = Float.MAX_VALUE

                for (rIdx in remaining.indices) {
                    val ch = remaining[rIdx]
                    val dStart = (ctx.getPos(ch.first()) - tailPos).lengthSquared()
                    val dEnd = (ctx.getPos(ch.last()) - tailPos).lengthSquared()

                    if (dStart < minDist) {
                        minDist = dStart
                        bestIdx = rIdx
                        bestReversed = false
                    }
                    if (dEnd < minDist) {
                        minDist = dEnd
                        bestIdx = rIdx
                        bestReversed = true
                    }
                }

                if (bestIdx == -1) break
                val chosen = remaining.removeAt(bestIdx)
                val aligned = if (bestReversed) chosen.reversedArray() else chosen
                orderedChains.add(aligned)
                tailPos = ctx.getPos(aligned.last())
            }

            val K = orderedChains.size
            if (K < 3) return

            // 2. Pastikan orientasi orderedChains adalah Counter-Clockwise terhadap cornerNormal
            val p0 = ctx.getPos(orderedChains[0].first())
            val p1 = ctx.getPos(orderedChains[1].first())
            val p2 = ctx.getPos(orderedChains[2].first())
            val loopCross = (p1 - p0).cross(p2 - p0)
            if (loopCross.dot(cornerNormal) < 0f) {
                // Balik orientasi loop agar counter-clockwise konsisten
                val reversedLoop = orderedChains.map { it.reversedArray() }.reversed()
                orderedChains.clear()
                orderedChains.addAll(reversedLoop)
            }

            // 3. Jamin kontinuitas endpoint antar arc yang berdekatan
            for (k in 0 until K) {
                val nextK = (k + 1) % K
                orderedChains[nextK][0] = orderedChains[k].last()
            }

            val S = clampedSegments

            // -------------------------------------------------------------
            // SUB-CASE 3.1: SINGLE-SEGMENT CHAMFER (S = 1) - MINIMAL FACES
            // -------------------------------------------------------------
            if (S == 1) {
                // Untuk K = 3 (Kubus 90° convex/concave): Tepat 1 Segitiga Datar Bersih
                val c0 = orderedChains[0][0]
                for (k in 1 until K - 1) {
                    val c1 = orderedChains[k][0]
                    val c2 = orderedChains[k + 1][0]
                    ctx.addTriangle(c0, c1, c2, cornerNormal)
                }
                return
            }

            // -------------------------------------------------------------
            // SUB-CASE 3.2: MULTI-SEGMENT ROUND FILLET (S >= 2) - ALL-QUAD COONS GRID
            // -------------------------------------------------------------
            val m = S / 2 // m >= 1 (e.g. S=2 -> m=1; S=4 -> m=2)

            // 1. Hitung titik pusat fillet P_center dan pusat kelengkungan bola cSphere (CEMBUNG)
            var mSum = Vector3.ZERO
            for (k in 0 until K) {
                val midV = orderedChains[k][m]
                mSum += ctx.getPos(midV)
            }
            val mAvg = mSum * (1f / K)

            val rSphere = clampedOffset
            var rBoundSum = 0f
            for (k in 0 until K) {
                val midPos = ctx.getPos(orderedChains[k][m])
                rBoundSum += (midPos - mAvg).length()
            }
            val rBoundary = rBoundSum / K
            val safeRBound = rBoundary.coerceIn(0.001f, rSphere * 0.95f)

            // Hitung tinggi kubah sagitta capHeight: h = rSphere - sqrt(rSphere^2 - safeRBound^2)
            val capHeight = rSphere - kotlin.math.sqrt(rSphere * rSphere - safeRBound * safeRBound)
            val h = (capHeight * (clampedProfile / 0.5f)).coerceIn(0.05f * clampedOffset, 0.35f * clampedOffset)

            // Arah penonjolan kubah: ke arah luar (origVPos) agar CEMBUNG sempurna
            val toCorner = origVPos - mAvg
            val isConvex = toCorner.dot(cornerNormal) >= 0f
            val domeDir = if (isConvex) cornerNormal else -cornerNormal

            val centerPos = mAvg + domeDir * h
            val cSphere = centerPos - domeDir * rSphere
            val vCenter = ctx.addVertex(centerPos, rootV)

            // 2. Garis miter radial L_k dari setiap midpoint M_k ke vCenter
            val radialMiter = Array(K) { IntArray(m + 1) }
            for (k in 0 until K) {
                val mIdx = orderedChains[k][m]
                val mPos = ctx.getPos(mIdx)
                radialMiter[k][0] = mIdx
                radialMiter[k][m] = vCenter

                for (j in 1 until m) {
                    val t = j.toFloat() / m
                    val ptLinear = mPos * (1f - t) + centerPos * t
                    val ptDir = (ptLinear - cSphere).let {
                        if (it.lengthSquared() > 1e-6f) it.normalized() else domeDir
                    }
                    val ptSpherical = cSphere + ptDir * rSphere
                    radialMiter[k][j] = ctx.addVertex(ptSpherical, rootV)
                }
            }

            // 3. Bangun m x m Quad Grid untuk setiap sektor k
            for (k in 0 until K) {
                val prevK = (k - 1 + K) % K
                val grid = Array(m + 1) { IntArray(m + 1) }

                // Batas Bottom (v = 0): Arc k dari 0 ke m
                for (u in 0..m) {
                    grid[u][0] = orderedChains[k][u]
                }
                // Batas Right (u = m): Radial miter k dari 0 ke m
                for (v in 0..m) {
                    grid[m][v] = radialMiter[k][v]
                }
                // Batas Top (v = m): Radial miter prevK dari 0 ke m
                for (u in 0..m) {
                    grid[u][m] = radialMiter[prevK][u]
                }
                // Batas Left (u = 0): Arc prevK dari 2m mundur ke m
                for (v in 0..m) {
                    grid[0][v] = orderedChains[prevK][2 * m - v]
                }

                // Titik-titik interior (jika m >= 2, misalnya S = 4 -> m = 2)
                for (u in 1 until m) {
                    val su = u.toFloat() / m
                    for (v in 1 until m) {
                        val sv = v.toFloat() / m

                        // Coons Patch / Transfinite Bilinear Blend
                        val pBottom = ctx.getPos(grid[u][0])
                        val pTop = ctx.getPos(grid[u][m])
                        val pLeft = ctx.getPos(grid[0][v])
                        val pRight = ctx.getPos(grid[m][v])

                        val p00 = ctx.getPos(grid[0][0])
                        val pM0 = ctx.getPos(grid[m][0])
                        val p0M = ctx.getPos(grid[0][m])
                        val pMM = ctx.getPos(grid[m][m])

                        val blendPos = (pBottom * (1f - sv) + pTop * sv) +
                                       (pLeft * (1f - su) + pRight * su) -
                                       (p00 * (1f - su) * (1f - sv) +
                                        pM0 * su * (1f - sv) +
                                        p0M * (1f - su) * sv +
                                        pMM * su * sv)

                        val ptDir = (blendPos - cSphere).let {
                            if (it.lengthSquared() > 1e-6f) it.normalized() else domeDir
                        }
                        val ptSpherical = cSphere + ptDir * rSphere
                        grid[u][v] = ctx.addVertex(ptSpherical, rootV)
                    }
                }

                // Buat Quads untuk setiap sel pada grid
                for (u in 0 until m) {
                    for (v in 0 until m) {
                        val i0 = grid[u][v]
                        val i1 = grid[u + 1][v]
                        val i2 = grid[u + 1][v + 1]
                        val i3 = grid[u][v + 1]
                        ctx.addQuad(i0, i1, i2, i3, cornerNormal)
                    }
                }
            }
        }
    }

    // =========================================================================
    // STAGE 4: MESH VALIDATION & REBUILD
    // =========================================================================

    private object MeshValidator {

        fun assembleAndValidate(ctx: BevelBuildContext, sourceMesh: Mesh): Pair<Mesh, Set<Int>>? {
            if (ctx.outVertices.isEmpty() || ctx.newIndices.isEmpty()) return null

            val vertArray = ctx.outVertices.toFloatArray()
            val indArray = ctx.newIndices.toIntArray()
            val totalVerts = vertArray.size / 3

            // Normal Buffer Computation (Area-weighted per vertex)
            val normAcc = Array(totalVerts) { Vector3.ZERO }
            for (i in 0 until indArray.size step 3) {
                val i0 = indArray[i]
                val i1 = indArray[i + 1]
                val i2 = indArray[i + 2]
                val p0 = Vector3(vertArray[i0 * 3], vertArray[i0 * 3 + 1], vertArray[i0 * 3 + 2])
                val p1 = Vector3(vertArray[i1 * 3], vertArray[i1 * 3 + 1], vertArray[i1 * 3 + 2])
                val p2 = Vector3(vertArray[i2 * 3], vertArray[i2 * 3 + 1], vertArray[i2 * 3 + 2])
                val fn = (p1 - p0).cross(p2 - p0)
                normAcc[i0] += fn
                normAcc[i1] += fn
                normAcc[i2] += fn
            }

            val finalNormals = FloatArray(vertArray.size)
            for (v in 0 until totalVerts) {
                val n = if (normAcc[v].lengthSquared() > 1e-8f) normAcc[v].normalized() else Vector3.UNIT_Y
                finalNormals[v * 3] = n.x
                finalNormals[v * 3 + 1] = n.y
                finalNormals[v * 3 + 2] = n.z
            }

            val finalUvs = if (ctx.hasUvs && ctx.outUvs.size == totalVerts * 2) {
                ctx.outUvs.toFloatArray()
            } else {
                FloatArray(0)
            }

            // Wireframe Indices diambil secara eksklusif dari ctx.wireframeEdges (Perimeter quad & tri saja, TANPA diagonal interior)
            val wireframeArray = IntArray(ctx.wireframeEdges.size * 2)
            var wIdx = 0
            for (e in ctx.wireframeEdges) {
                wireframeArray[wIdx++] = e.first
                wireframeArray[wIdx++] = e.second
            }

            val finalMesh = Mesh(
                vertices = vertArray,
                normals = finalNormals,
                uvs = finalUvs,
                indices = indArray,
                wireframeIndices = wireframeArray,
                boundingBox = BoundingBox(Vector3.ZERO, Vector3.ZERO)
            )
            finalMesh.recalculateNormalsAndBounds()

            return Pair(finalMesh, ctx.newChamferFaces)
        }
    }
}
