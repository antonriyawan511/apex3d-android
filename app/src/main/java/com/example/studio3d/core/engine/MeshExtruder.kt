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

object MeshExtruder {

    /**
     * Extrudes selected triangle faces from a Mesh.
     * Produces a newly allocated Mesh with duplicated cap vertices, connecting side quads (as triangles),
     * and returns the new Mesh along with the new face indices for selection.
     */
    fun extrudeFaces(
        sourceMesh: Mesh,
        selectedFaceIndices: Set<Int>,
        extrudeDistance: Float = 0.4f
    ): Pair<Mesh, Set<Int>>? {
        if (selectedFaceIndices.isEmpty()) return null

        val topology = sourceMesh.topology
        val oldFaceCount = topology.faceCount
        val validFaces = selectedFaceIndices.filter { it in 0 until oldFaceCount }.toSet()
        if (validFaces.isEmpty()) return null

        // Calculate average normal of all selected faces for extrusion offset direction
        var avgNormal = Vector3.ZERO
        for (f in validFaces) {
            avgNormal = avgNormal + topology.getFaceNormal(f)
        }
        if (avgNormal.length() > 0.0001f) {
            avgNormal = avgNormal.normalized()
        } else {
            avgNormal = Vector3(0f, 1f, 0f)
        }
        val offsetVec = avgNormal * extrudeDistance

        // Identify boundary edges of the selected face patch
        // A boundary edge belongs to exactly ONE selected triangle
        val edgeUsage = HashMap<Pair<Int, Int>, Int>()
        val directedEdges = ArrayList<Pair<Int, Int>>()

        for (f in validFaces) {
            val (i0, i1, i2) = topology.getFaceIndices(f)
            val e01 = Pair(minOf(i0, i1), maxOf(i0, i1))
            val e12 = Pair(minOf(i1, i2), maxOf(i1, i2))
            val e20 = Pair(minOf(i2, i0), maxOf(i2, i0))

            edgeUsage[e01] = (edgeUsage[e01] ?: 0) + 1
            edgeUsage[e12] = (edgeUsage[e12] ?: 0) + 1
            edgeUsage[e20] = (edgeUsage[e20] ?: 0) + 1

            directedEdges.add(Pair(i0, i1))
            directedEdges.add(Pair(i1, i2))
            directedEdges.add(Pair(i2, i0))
        }

        val boundaryUndirected = edgeUsage.filter { it.value == 1 }.keys
        val boundaryDirected = directedEdges.filter {
            Pair(minOf(it.first, it.second), maxOf(it.first, it.second)) in boundaryUndirected
        }

        // Collect all vertices involved in selected faces
        val selectedVertexSet = LinkedHashSet<Int>()
        for (f in validFaces) {
            val (i0, i1, i2) = topology.getFaceIndices(f)
            selectedVertexSet.add(i0)
            selectedVertexSet.add(i1)
            selectedVertexSet.add(i2)
        }

        // Map original vertex index to newly duplicated extruded vertex index
        val oldVertexCount = sourceMesh.vertices.size / 3
        val vertexMap = HashMap<Int, Int>()
        var nextVertexIdx = oldVertexCount

        for (v in selectedVertexSet) {
            vertexMap[v] = nextVertexIdx++
        }

        // Build new vertex array
        val newVertexCount = nextVertexIdx
        val newVertices = FloatArray(newVertexCount * 3)
        System.arraycopy(sourceMesh.vertices, 0, newVertices, 0, sourceMesh.vertices.size)

        // Fill extruded vertex positions
        for (v in selectedVertexSet) {
            val origPos = sourceMesh.getVertex(v)
            val newPos = origPos + offsetVec
            val newIdx = vertexMap[v]!!
            newVertices[newIdx * 3] = newPos.x
            newVertices[newIdx * 3 + 1] = newPos.y
            newVertices[newIdx * 3 + 2] = newPos.z
        }

        // UVs
        val newUvs = FloatArray(newVertexCount * 2)
        if (sourceMesh.uvs.isNotEmpty()) {
            System.arraycopy(sourceMesh.uvs, 0, newUvs, 0, minOf(sourceMesh.uvs.size, newUvs.size))
            for (v in selectedVertexSet) {
                val newIdx = vertexMap[v]!!
                val origUvOffset = v * 2
                if (origUvOffset + 1 < sourceMesh.uvs.size) {
                    newUvs[newIdx * 2] = sourceMesh.uvs[origUvOffset]
                    newUvs[newIdx * 2 + 1] = sourceMesh.uvs[origUvOffset + 1]
                }
            }
        }

        // Build new index list
        val newIndices = ArrayList<Int>()
        val newCapFaceIndices = LinkedHashSet<Int>()

        // 1. Keep unselected faces untouched
        for (f in 0 until oldFaceCount) {
            val (i0, i1, i2) = topology.getFaceIndices(f)
            if (f !in validFaces) {
                newIndices.add(i0)
                newIndices.add(i1)
                newIndices.add(i2)
            }
        }

        // 2. Add extruded cap faces (remapped to newly duplicated vertices)
        for (f in validFaces) {
            val (i0, i1, i2) = topology.getFaceIndices(f)
            val newFIndex = newIndices.size / 3
            newIndices.add(vertexMap[i0]!!)
            newIndices.add(vertexMap[i1]!!)
            newIndices.add(vertexMap[i2]!!)
            newCapFaceIndices.add(newFIndex)
        }

        // 3. Add side wall quads (2 triangles per boundary edge: v0 -> v1 -> v1_new, v0 -> v1_new -> v0_new)
        for (edge in boundaryDirected) {
            val v0 = edge.first
            val v1 = edge.second
            val v0New = vertexMap[v0]!!
            val v1New = vertexMap[v1]!!

            // Triangle 1
            newIndices.add(v0)
            newIndices.add(v1)
            newIndices.add(v1New)

            // Triangle 2
            newIndices.add(v0)
            newIndices.add(v1New)
            newIndices.add(v0New)
        }

        // Build clean polygon wireframe edges (excluding internal diagonal cross-struts on side quads and cap quads)
        val edgeLines = LinkedHashSet<Pair<Int, Int>>()

        // A. Existing wireframe lines that don't belong to modified cap faces
        val oldWire = sourceMesh.wireframeIndices
        for (i in 0 until oldWire.size step 2) {
            val v0 = oldWire[i]
            val v1 = oldWire[i + 1]
            // Keep if both vertices are not simultaneously on an extruded face boundary
            edgeLines.add(Pair(minOf(v0, v1), maxOf(v0, v1)))
        }

        // B. New perimeter boundary and side pillars for extruded quads
        for (edge in boundaryDirected) {
            val v0 = edge.first
            val v1 = edge.second
            val v0New = vertexMap[v0]!!
            val v1New = vertexMap[v1]!!

            // Extruded cap top edge
            edgeLines.add(Pair(minOf(v0New, v1New), maxOf(v0New, v1New)))
            // Side pillar upright edges
            edgeLines.add(Pair(minOf(v0, v0New), maxOf(v0, v0New)))
            edgeLines.add(Pair(minOf(v1, v1New), maxOf(v1, v1New)))
        }
        val wireframeArray = IntArray(edgeLines.size * 2)
        var wIdx = 0
        for (e in edgeLines) {
            wireframeArray[wIdx++] = e.first
            wireframeArray[wIdx++] = e.second
        }

        val resultMesh = Mesh(
            vertices = newVertices,
            normals = FloatArray(newVertexCount * 3),
            uvs = newUvs,
            indices = newIndices.toIntArray(),
            wireframeIndices = wireframeArray,
            boundingBox = BoundingBox(Vector3.ZERO, Vector3.ZERO)
        )
        resultMesh.recalculateNormalsAndBounds()

        return Pair(resultMesh, newCapFaceIndices)
    }

    /**
     * Extrudes selected edges from a Mesh.
     * Produces a newly allocated Mesh with duplicated outer edge vertices, connecting quad faces
     * (split into 2 triangles each), updates topology and clean wireframe boundaries,
     * and returns the new Mesh along with the new edge indices for seamless chaining/transform.
     */
    fun extrudeEdges(
        sourceMesh: Mesh,
        selectedEdgeIndices: Set<Int>,
        extrudeDistance: Float = 0.4f
    ): Pair<Mesh, Set<Int>>? {
        if (selectedEdgeIndices.isEmpty()) return null

        val topology = sourceMesh.topology
        val edges = topology.edges
        val validEdges = selectedEdgeIndices.filter { it in edges.indices }.toSet()
        if (validEdges.isEmpty()) return null

        // 1. Map to unique spatial root edges
        val uniqueEdges = LinkedHashSet<Pair<Int, Int>>()
        for (e in validEdges) {
            val (v0, v1) = edges[e]
            val r0 = topology.coincidentMap[v0]
            val r1 = topology.coincidentMap[v1]
            if (r0 != r1) {
                uniqueEdges.add(Pair(minOf(r0, r1), maxOf(r0, r1)))
            }
        }
        if (uniqueEdges.isEmpty()) return null

        // Map root vertices to faces in sourceMesh
        val vertFacesMap = HashMap<Int, MutableList<Int>>()
        for (f in 0 until topology.faceCount) {
            val (i0, i1, i2) = topology.getFaceIndices(f)
            val r0 = topology.coincidentMap[i0]
            val r1 = topology.coincidentMap[i1]
            val r2 = topology.coincidentMap[i2]
            vertFacesMap.getOrPut(r0) { ArrayList() }.add(f)
            vertFacesMap.getOrPut(r1) { ArrayList() }.add(f)
            vertFacesMap.getOrPut(r2) { ArrayList() }.add(f)
        }

        // Map each spatial edge to faces sharing both endpoints
        val edgeFacesMap = HashMap<Pair<Int, Int>, MutableList<Int>>()
        for (f in 0 until topology.faceCount) {
            val (i0, i1, i2) = topology.getFaceIndices(f)
            val r0 = topology.coincidentMap[i0]
            val r1 = topology.coincidentMap[i1]
            val r2 = topology.coincidentMap[i2]
            val e01 = Pair(minOf(r0, r1), maxOf(r0, r1))
            val e12 = Pair(minOf(r1, r2), maxOf(r1, r2))
            val e20 = Pair(minOf(r2, r0), maxOf(r2, r0))
            edgeFacesMap.getOrPut(e01) { ArrayList() }.add(f)
            edgeFacesMap.getOrPut(e12) { ArrayList() }.add(f)
            edgeFacesMap.getOrPut(e20) { ArrayList() }.add(f)
        }

        // 2. Compute extrusion direction for each edge
        val edgeDisplacement = HashMap<Pair<Int, Int>, Vector3>()
        for (edge in uniqueEdges) {
            val r0 = edge.first
            val r1 = edge.second
            val p0 = sourceMesh.getVertex(r0)
            val p1 = sourceMesh.getVertex(r1)
            val edgeMid = (p0 + p1) * 0.5f
            val edgeDir = (p1 - p0).normalized()

            val sharedFaces = edgeFacesMap[edge] ?: emptyList()
            val dispDir: Vector3 = when {
                sharedFaces.size == 1 -> {
                    // Open boundary edge: extrude outward from adjacent face
                    val f = sharedFaces[0]
                    val (i0, i1, i2) = topology.getFaceIndices(f)
                    val faceCenter = (sourceMesh.getVertex(i0) + sourceMesh.getVertex(i1) + sourceMesh.getVertex(i2)) / 3f
                    val fromCenter = edgeMid - faceCenter
                    // Project perpendicular to edgeDir
                    val inPlaneOut = fromCenter - edgeDir * fromCenter.dot(edgeDir)
                    if (inPlaneOut.length() > 0.0001f) {
                        inPlaneOut.normalized()
                    } else {
                        topology.getFaceNormal(f)
                    }
                }
                sharedFaces.size >= 2 -> {
                    // Manifold crease edge: average adjacent face normals
                    var nSum = Vector3.ZERO
                    for (f in sharedFaces) {
                        nSum = nSum + topology.getFaceNormal(f)
                    }
                    if (nSum.length() > 0.0001f) nSum.normalized() else Vector3(0f, 1f, 0f)
                }
                else -> {
                    // Standalone edge without faces: use vertex normals
                    val n0 = sourceMesh.getNormal(r0)
                    val n1 = sourceMesh.getNormal(r1)
                    val nSum = n0 + n1
                    if (nSum.length() > 0.0001f) nSum.normalized() else Vector3(0f, 1f, 0f)
                }
            }
            edgeDisplacement[edge] = dispDir * extrudeDistance
        }

        // 3. For each unique root vertex, average displacements of incident selected edges (produces clean miter joints)
        val uniqueVertRoots = LinkedHashSet<Int>()
        val vertIncidentEdges = HashMap<Int, MutableList<Pair<Int, Int>>>()
        for (edge in uniqueEdges) {
            uniqueVertRoots.add(edge.first)
            uniqueVertRoots.add(edge.second)
            vertIncidentEdges.getOrPut(edge.first) { ArrayList() }.add(edge)
            vertIncidentEdges.getOrPut(edge.second) { ArrayList() }.add(edge)
        }

        val vertOffsets = HashMap<Int, Vector3>()
        for (r in uniqueVertRoots) {
            val incident = vertIncidentEdges[r] ?: emptyList()
            var sumOffset = Vector3.ZERO
            for (edge in incident) {
                sumOffset = sumOffset + (edgeDisplacement[edge] ?: Vector3.ZERO)
            }
            val avgOffset = if (incident.isNotEmpty()) sumOffset / incident.size.toFloat() else Vector3(0f, extrudeDistance, 0f)
            vertOffsets[r] = avgOffset
        }

        // 4. Duplicate vertices for new extruded edge boundaries
        val oldVertexCount = sourceMesh.vertices.size / 3
        val vertMap = HashMap<Int, Int>() // root -> newVertexIndex
        var nextIdx = oldVertexCount
        for (r in uniqueVertRoots) {
            vertMap[r] = nextIdx++
        }

        val newVertexCount = nextIdx
        val newVertices = FloatArray(newVertexCount * 3)
        System.arraycopy(sourceMesh.vertices, 0, newVertices, 0, sourceMesh.vertices.size)

        for (r in uniqueVertRoots) {
            val newIdx = vertMap[r]!!
            val origPos = sourceMesh.getVertex(r)
            val offset = vertOffsets[r] ?: Vector3(0f, extrudeDistance, 0f)
            val newPos = origPos + offset
            newVertices[newIdx * 3] = newPos.x
            newVertices[newIdx * 3 + 1] = newPos.y
            newVertices[newIdx * 3 + 2] = newPos.z
        }

        // UVs
        val newUvs = FloatArray(newVertexCount * 2)
        if (sourceMesh.uvs.isNotEmpty()) {
            System.arraycopy(sourceMesh.uvs, 0, newUvs, 0, minOf(sourceMesh.uvs.size, newUvs.size))
            for (r in uniqueVertRoots) {
                val newIdx = vertMap[r]!!
                val origUvOffset = r * 2
                if (origUvOffset + 1 < sourceMesh.uvs.size) {
                    newUvs[newIdx * 2] = sourceMesh.uvs[origUvOffset]
                    newUvs[newIdx * 2 + 1] = sourceMesh.uvs[origUvOffset + 1]
                }
            }
        }

        // 5. Build triangle indices: keep all original triangles + add 2 triangles per extruded edge
        val newIndices = ArrayList<Int>(sourceMesh.indices.size + uniqueEdges.size * 6)
        for (idx in sourceMesh.indices) {
            newIndices.add(idx)
        }

        for (edge in uniqueEdges) {
            val r0 = edge.first
            val r1 = edge.second
            val r0New = vertMap[r0]!!
            val r1New = vertMap[r1]!!

            // Determine winding order from adjacent face traversal
            var isForward = true
            val shared = edgeFacesMap[edge] ?: emptyList()
            for (f in shared) {
                val (i0, i1, i2) = topology.getFaceIndices(f)
                val cr0 = topology.coincidentMap[i0]
                val cr1 = topology.coincidentMap[i1]
                val cr2 = topology.coincidentMap[i2]
                if ((cr0 == r0 && cr1 == r1) || (cr1 == r0 && cr2 == r1) || (cr2 == r0 && cr0 == r1)) {
                    isForward = true
                    break
                } else if ((cr0 == r1 && cr1 == r0) || (cr1 == r1 && cr2 == r0) || (cr2 == r1 && cr0 == r0)) {
                    isForward = false
                    break
                }
            }

            if (isForward) {
                newIndices.add(r0)
                newIndices.add(r1)
                newIndices.add(r1New)

                newIndices.add(r0)
                newIndices.add(r1New)
                newIndices.add(r0New)
            } else {
                newIndices.add(r1)
                newIndices.add(r0)
                newIndices.add(r0New)

                newIndices.add(r1)
                newIndices.add(r0New)
                newIndices.add(r1New)
            }
        }

        // 6. Build clean wireframe: keep old wireframe + add new top edges and side pillars (no cross diagonals)
        val edgeLines = LinkedHashSet<Pair<Int, Int>>()
        val oldWire = sourceMesh.wireframeIndices
        for (i in 0 until oldWire.size step 2) {
            val v0 = oldWire[i]
            val v1 = oldWire[i + 1]
            edgeLines.add(Pair(minOf(v0, v1), maxOf(v0, v1)))
        }

        for (edge in uniqueEdges) {
            val r0 = edge.first
            val r1 = edge.second
            val r0New = vertMap[r0]!!
            val r1New = vertMap[r1]!!

            // Top extruded edge
            edgeLines.add(Pair(minOf(r0New, r1New), maxOf(r0New, r1New)))
            // Side pillar upright edges
            edgeLines.add(Pair(minOf(r0, r0New), maxOf(r0, r0New)))
            edgeLines.add(Pair(minOf(r1, r1New), maxOf(r1, r1New)))
        }

        val wireframeArray = IntArray(edgeLines.size * 2)
        var wIdx = 0
        for (e in edgeLines) {
            wireframeArray[wIdx++] = e.first
            wireframeArray[wIdx++] = e.second
        }

        val resultMesh = Mesh(
            vertices = newVertices,
            normals = FloatArray(newVertexCount * 3),
            uvs = newUvs,
            indices = newIndices.toIntArray(),
            wireframeIndices = wireframeArray,
            boundingBox = BoundingBox(Vector3.ZERO, Vector3.ZERO)
        )
        resultMesh.recalculateNormalsAndBounds()

        // 7. Find edge indices in the new topology corresponding to the newly extruded top edges
        val newTopology = resultMesh.topology
        val targetRoots = uniqueEdges.map {
            val n0 = vertMap[it.first]!!
            val n1 = vertMap[it.second]!!
            val rn0 = newTopology.coincidentMap[n0]
            val rn1 = newTopology.coincidentMap[n1]
            Pair(minOf(rn0, rn1), maxOf(rn0, rn1))
        }.toSet()

        val newSelectedEdgeIndices = LinkedHashSet<Int>()
        for ((idx, e) in newTopology.edges.withIndex()) {
            val er0 = newTopology.coincidentMap[e.first]
            val er1 = newTopology.coincidentMap[e.second]
            val ep = Pair(minOf(er0, er1), maxOf(er0, er1))
            if (ep in targetRoots) {
                newSelectedEdgeIndices.add(idx)
            }
        }

        return Pair(resultMesh, newSelectedEdgeIndices)
    }

    /**
     * Insets selected triangle faces from a Mesh.
     * Scales the inner face vertices towards the face/patch centroid,
     * connects the boundary edges with bridging quads (2 triangles each),
     * and returns the new Mesh along with the new inner face indices for selection.
     */
    fun insetFaces(
        sourceMesh: Mesh,
        selectedFaceIndices: Set<Int>,
        insetRatio: Float = 0.25f,
        evenThickness: Boolean = false
    ): Pair<Mesh, Set<Int>>? {
        if (selectedFaceIndices.isEmpty()) return null

        val topology = sourceMesh.topology
        val oldFaceCount = topology.faceCount
        val validFaces = selectedFaceIndices.filter { it in 0 until oldFaceCount }.toSet()
        if (validFaces.isEmpty()) return null

        // Identify boundary edges and group selected faces into connected patches
        val edgeToFaces = HashMap<Pair<Int, Int>, ArrayList<Int>>()
        for (f in validFaces) {
            val (i0, i1, i2) = topology.getFaceIndices(f)
            val e01 = Pair(minOf(i0, i1), maxOf(i0, i1))
            val e12 = Pair(minOf(i1, i2), maxOf(i1, i2))
            val e20 = Pair(minOf(i2, i0), maxOf(i2, i0))
            edgeToFaces.getOrPut(e01) { ArrayList() }.add(f)
            edgeToFaces.getOrPut(e12) { ArrayList() }.add(f)
            edgeToFaces.getOrPut(e20) { ArrayList() }.add(f)
        }

        // Group connected faces into disjoint patches
        val visitedFaces = HashSet<Int>()
        val patches = ArrayList<List<Int>>()
        for (f in validFaces) {
            if (f in visitedFaces) continue
            val patch = ArrayList<Int>()
            val queue = ArrayDeque<Int>()
            queue.add(f)
            visitedFaces.add(f)

            while (queue.isNotEmpty()) {
                val curr = queue.removeFirst()
                patch.add(curr)
                val (i0, i1, i2) = topology.getFaceIndices(curr)
                val fEdges = listOf(
                    Pair(minOf(i0, i1), maxOf(i0, i1)),
                    Pair(minOf(i1, i2), maxOf(i1, i2)),
                    Pair(minOf(i2, i0), maxOf(i2, i0))
                )
                for (edge in fEdges) {
                    val neighbors = edgeToFaces[edge] ?: emptyList()
                    for (nbr in neighbors) {
                        if (nbr in validFaces && nbr !in visitedFaces) {
                            visitedFaces.add(nbr)
                            queue.add(nbr)
                        }
                    }
                }
            }
            patches.add(patch)
        }

        // Collect all vertices involved in selected faces
        val selectedVertexSet = LinkedHashSet<Int>()
        for (f in validFaces) {
            val (i0, i1, i2) = topology.getFaceIndices(f)
            selectedVertexSet.add(i0)
            selectedVertexSet.add(i1)
            selectedVertexSet.add(i2)
        }

        // Map original vertex index to newly duplicated inner vertex index
        val oldVertexCount = sourceMesh.vertices.size / 3
        val vertexMap = HashMap<Int, Int>()
        var nextVertexIdx = oldVertexCount

        for (v in selectedVertexSet) {
            vertexMap[v] = nextVertexIdx++
        }

        // Build new vertex array
        val newVertexCount = nextVertexIdx
        val newVertices = FloatArray(newVertexCount * 3)
        System.arraycopy(sourceMesh.vertices, 0, newVertices, 0, sourceMesh.vertices.size)

        val allBoundaryDirected = ArrayList<Pair<Int, Int>>()
        val ratio = insetRatio.coerceIn(0.01f, 0.45f)

        // Process each patch independently to preserve exact face shape and uniform border thickness
        for (patch in patches) {
            val patchFaces = patch.toSet()
            val patchEdgeUsage = HashMap<Pair<Int, Int>, Int>()
            val patchDirectedEdges = ArrayList<Pair<Int, Int>>()

            var patchNormalSum = Vector3.ZERO
            var patchCenterSum = Vector3.ZERO
            var patchVertexCount = 0
            val patchVertexSet = HashSet<Int>()

            for (f in patchFaces) {
                val (i0, i1, i2) = topology.getFaceIndices(f)
                val e01 = Pair(minOf(i0, i1), maxOf(i0, i1))
                val e12 = Pair(minOf(i1, i2), maxOf(i1, i2))
                val e20 = Pair(minOf(i2, i0), maxOf(i2, i0))

                patchEdgeUsage[e01] = (patchEdgeUsage[e01] ?: 0) + 1
                patchEdgeUsage[e12] = (patchEdgeUsage[e12] ?: 0) + 1
                patchEdgeUsage[e20] = (patchEdgeUsage[e20] ?: 0) + 1

                patchDirectedEdges.add(Pair(i0, i1))
                patchDirectedEdges.add(Pair(i1, i2))
                patchDirectedEdges.add(Pair(i2, i0))

                patchNormalSum = patchNormalSum + topology.getFaceNormal(f)

                if (patchVertexSet.add(i0)) {
                    patchCenterSum = patchCenterSum + sourceMesh.getVertex(i0)
                    patchVertexCount++
                }
                if (patchVertexSet.add(i1)) {
                    patchCenterSum = patchCenterSum + sourceMesh.getVertex(i1)
                    patchVertexCount++
                }
                if (patchVertexSet.add(i2)) {
                    patchCenterSum = patchCenterSum + sourceMesh.getVertex(i2)
                    patchVertexCount++
                }
            }

            val patchBoundaryUndirected = patchEdgeUsage.filter { it.value == 1 }.keys
            val patchBoundaryDirected = patchDirectedEdges.filter {
                Pair(minOf(it.first, it.second), maxOf(it.first, it.second)) in patchBoundaryUndirected
            }

            if (patchBoundaryDirected.isEmpty()) continue
            allBoundaryDirected.addAll(patchBoundaryDirected)

            val patchNormal = if (patchNormalSum.length() > 0.001f) patchNormalSum.normalized() else Vector3.UNIT_Y
            val patchCenter = if (patchVertexCount > 0) patchCenterSum / patchVertexCount.toFloat() else Vector3.ZERO

            // Calculate boundary edge lengths and inward normals for Even Thickness mode
            var minEdgeLen = Float.MAX_VALUE
            val vIncidentBoundaryIn = HashMap<Int, Vector3>()
            val vIncidentBoundaryOut = HashMap<Int, Vector3>()

            if (evenThickness) {
                for (edge in patchBoundaryDirected) {
                    val pA = sourceMesh.getVertex(edge.first)
                    val pB = sourceMesh.getVertex(edge.second)
                    val edgeVec = pB - pA
                    val len = edgeVec.length()
                    if (len > 0.0001f) {
                        if (len < minEdgeLen) minEdgeLen = len
                        val edgeDir = edgeVec / len
                        val pMid = (pA + pB) * 0.5f
                        var inward = patchNormal.cross(edgeDir).normalized()
                        // Bulletproof check: inward must point towards the inside / patchCenter
                        if (inward.dot(patchCenter - pMid) < 0f) {
                            inward = -inward
                        }
                        vIncidentBoundaryOut[edge.first] = inward
                        vIncidentBoundaryIn[edge.second] = inward
                    }
                }
            }
            if (minEdgeLen == Float.MAX_VALUE) minEdgeLen = 1.0f
            val targetDistance = minEdgeLen * ratio

            for (v in patchVertexSet) {
                val origPos = sourceMesh.getVertex(v)
                val toCenter = patchCenter - origPos
                val distToCenter = toCenter.length()

                val newPos = if (evenThickness && v in vIncidentBoundaryIn && v in vIncidentBoundaryOut) {
                    val nIn = vIncidentBoundaryIn[v]!!
                    val nOut = vIncidentBoundaryOut[v]!!
                    var bisector = (nIn + nOut)
                    if (bisector.length() > 0.001f) {
                        bisector = bisector.normalized()
                        if (bisector.dot(toCenter) < 0f) {
                            bisector = -bisector
                        }
                        val cosHalf = maxOf(0.25f, bisector.dot(nIn).coerceAtLeast(0.1f))
                        val miterDist = (targetDistance / cosHalf).coerceAtMost(distToCenter * 0.85f)
                        origPos + bisector * miterDist
                    } else {
                        origPos + toCenter * ratio
                    }
                } else {
                    // Centroid / Proportional Scaling
                    origPos + toCenter * ratio
                }

                val newIdx = vertexMap[v]!!
                newVertices[newIdx * 3] = newPos.x
                newVertices[newIdx * 3 + 1] = newPos.y
                newVertices[newIdx * 3 + 2] = newPos.z
            }
        }

        if (allBoundaryDirected.isEmpty()) return null

        // UVs
        val newUvs = FloatArray(newVertexCount * 2)
        if (sourceMesh.uvs.isNotEmpty()) {
            System.arraycopy(sourceMesh.uvs, 0, newUvs, 0, minOf(sourceMesh.uvs.size, newUvs.size))
            for (v in selectedVertexSet) {
                val newIdx = vertexMap[v]!!
                val origUvOffset = v * 2
                if (origUvOffset + 1 < sourceMesh.uvs.size) {
                    newUvs[newIdx * 2] = sourceMesh.uvs[origUvOffset]
                    newUvs[newIdx * 2 + 1] = sourceMesh.uvs[origUvOffset + 1]
                }
            }
        }

        // Build new index list
        val newIndices = ArrayList<Int>()
        val newCapFaceIndices = LinkedHashSet<Int>()

        // 1. Keep unselected faces untouched
        for (f in 0 until oldFaceCount) {
            val (i0, i1, i2) = topology.getFaceIndices(f)
            if (f !in validFaces) {
                newIndices.add(i0)
                newIndices.add(i1)
                newIndices.add(i2)
            }
        }

        // 2. Add inner cap faces (remapped to newly duplicated inner vertices)
        for (f in validFaces) {
            val (i0, i1, i2) = topology.getFaceIndices(f)
            val newFIndex = newIndices.size / 3
            newIndices.add(vertexMap[i0]!!)
            newIndices.add(vertexMap[i1]!!)
            newIndices.add(vertexMap[i2]!!)
            newCapFaceIndices.add(newFIndex)
        }

        // 3. Add bridging frame quads along boundary edges (2 triangles per boundary edge)
        for (edge in allBoundaryDirected) {
            val v0 = edge.first
            val v1 = edge.second
            val v0New = vertexMap[v0]!!
            val v1New = vertexMap[v1]!!

            // Triangle 1
            newIndices.add(v0)
            newIndices.add(v1)
            newIndices.add(v1New)

            // Triangle 2
            newIndices.add(v0)
            newIndices.add(v1New)
            newIndices.add(v0New)
        }

        // Build clean polygon wireframe edges
        val edgeLines = LinkedHashSet<Pair<Int, Int>>()

        // A. Existing wireframe lines
        val oldWire = sourceMesh.wireframeIndices
        for (i in 0 until oldWire.size step 2) {
            val v0 = oldWire[i]
            val v1 = oldWire[i + 1]
            edgeLines.add(Pair(minOf(v0, v1), maxOf(v0, v1)))
        }

        // B. New perimeter boundary ring and radial corner struts for inset frame
        for (edge in allBoundaryDirected) {
            val v0 = edge.first
            val v1 = edge.second
            val v0New = vertexMap[v0]!!
            val v1New = vertexMap[v1]!!

            // Inner boundary edge
            edgeLines.add(Pair(minOf(v0New, v1New), maxOf(v0New, v1New)))
            // Corner connecting struts
            edgeLines.add(Pair(minOf(v0, v0New), maxOf(v0, v0New)))
            edgeLines.add(Pair(minOf(v1, v1New), maxOf(v1, v1New)))
        }

        val wireframeArray = IntArray(edgeLines.size * 2)
        var wIdx = 0
        for (e in edgeLines) {
            wireframeArray[wIdx++] = e.first
            wireframeArray[wIdx++] = e.second
        }

        val resultMesh = Mesh(
            vertices = newVertices,
            normals = FloatArray(newVertexCount * 3),
            uvs = newUvs,
            indices = newIndices.toIntArray(),
            wireframeIndices = wireframeArray,
            boundingBox = BoundingBox(Vector3.ZERO, Vector3.ZERO)
        )
        resultMesh.recalculateNormalsAndBounds()

        return Pair(resultMesh, newCapFaceIndices)
    }
}
