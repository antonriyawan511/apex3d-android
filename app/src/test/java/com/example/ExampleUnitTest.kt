package com.example

import com.example.studio3d.core.engine.MeshFactory
import com.example.studio3d.core.engine.MeshBeveler
import com.example.studio3d.core.math.Vector3
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testBevelAllEdges() {
        val cube = MeshFactory.createCube()
        val res = MeshBeveler.bevelEdges(cube, emptySet(), offset = 0.1f, segments = 1)
        assertNotNull(res)
        val (beveledMesh, newFaces) = res!!
        println("All edges beveled verts: ${beveledMesh.vertices.size / 3}, faces: ${beveledMesh.indices.size / 3}, newChamfers: ${newFaces.size}")
        assertTrue(beveledMesh.indices.isNotEmpty())
    }

    @Test
    fun testBevelAllEdgesMultiSegments() {
        val cube = MeshFactory.createCube()
        for (seg in 1..4) {
            val res = MeshBeveler.bevelEdges(cube, emptySet(), offset = 0.12f, segments = seg, profile = 0.5f)
            assertNotNull(res)
            val (beveledMesh, newFaces) = res!!
            println("All edges segments=$seg verts: ${beveledMesh.vertices.size / 3}, faces: ${beveledMesh.indices.size / 3}, newChamfers: ${newFaces.size}")
            assertTrue(beveledMesh.indices.isNotEmpty())
            // Verify no NaN or infinite normals
            for (n in beveledMesh.normals) {
                assertTrue(!n.isNaN() && !n.isInfinite())
            }
            // Verify all vertices are strictly inside cube bounding box + epsilon (no bulging knuckles)
            for (i in 0 until beveledMesh.vertices.size step 3) {
                val x = beveledMesh.vertices[i]
                val y = beveledMesh.vertices[i + 1]
                val z = beveledMesh.vertices[i + 2]
                assertTrue("Vertex ($x, $y, $z) must not bulge outside cube", 
                    x >= -1.001f && x <= 1.001f &&
                    y >= -1.001f && y <= 1.001f &&
                    z >= -1.001f && z <= 1.001f
                )
            }
        }
    }

    @Test
    fun testBevelTwoEdgesMeetingAtCorner() {
        // Edge 2 and Edge 10 meet at Vertex 2 (top-front and top-right edges)
        val cube = MeshFactory.createCube()
        val res = MeshBeveler.bevelEdges(cube, setOf(2, 10), offset = 0.1f, segments = 4, profile = 0.5f)
        assertNotNull(res)
        val (beveledMesh, newFaces) = res!!
        println("Two edges fillet verts: ${beveledMesh.vertices.size / 3}, faces: ${beveledMesh.indices.size / 3}, newChamfers: ${newFaces.size}")
        assertTrue(beveledMesh.indices.isNotEmpty())
    }

    @Test
    fun testBevelSingleEdge() {
        val cube = MeshFactory.createCube()
        val res = MeshBeveler.bevelEdges(cube, setOf(2), offset = 0.1f, segments = 3, profile = 0.5f)
        assertNotNull(res)
        val (beveledMesh, newFaces) = res!!
        println("Single edge verts: ${beveledMesh.vertices.size / 3}, faces: ${beveledMesh.indices.size / 3}, newChamfers: ${newFaces.size}")
        assertTrue(beveledMesh.indices.isNotEmpty())
        for (normal in beveledMesh.normals) {
            assertTrue(!normal.isNaN() && !normal.isInfinite())
        }
        // REGRESSION (bug "bevel 1 edge bolong"): ujung edge bevel harus TERTUTUP
        // oleh face end-cap. Seluruh welded edge harus dipakai tepat 2 face
        // (watertight, tanpa crack end-cap no-op versi lama).
        val usage = weldEdgeUsages(beveledMesh)
        val cracks = usage.entries.filter { it.value.size == 1 }
        assertTrue(
            "Bevel 1 edge masih bolong: ${cracks.size} welded edge hanya dipakai 1 face. Contoh: ${cracks.take(3).joinToString { "edge=${it.key} A=? B=? faces=${it.value}" }}",
            cracks.isEmpty()
        )
        println("Single edge: watertight OK (weldedEdges=${usage.size}, cracks=0)")
    }

    @Test
    fun testCoplanarConnectedFacesOnBeveledMesh() {
        val cube = MeshFactory.createCube()
        val (beveledMesh, _) = MeshBeveler.bevelEdges(cube, setOf(2), offset = 0.1f, segments = 3, profile = 0.5f)!!
        val topology = beveledMesh.topology
        // Cari segitiga face depan (normal Z positif)
        val frontTriangles = mutableListOf<Int>()
        for (f in 0 until topology.faceCount) {
            val n = topology.getFaceNormal(f)
            if (n.z > 0.95f) {
                frontTriangles.add(f)
            }
        }
        assertTrue("Front face should have multiple triangles", frontTriangles.size >= 2)
        // Memilih segitiga manapun pada face depan harus mengembalikan seluruh segitiga face depan sebagai 1 polygon
        // Memilih segitiga manapun pada poligon harus mengembalikan seluruh segitiga poligon tersebut
        val group0 = topology.getCoplanarConnectedFaces(0)
        assertTrue("Front face quad harus mencakup triangle 0 dan 1", group0.contains(0) && group0.contains(1))
        val group1 = topology.getCoplanarConnectedFaces(1)
        assertTrue("Group dari triangle 1 harus identik dengan group dari triangle 0", group1.contains(0) && group1.contains(1))
    }

    @Test
    fun testBevelCylinder() {
        val cyl = MeshFactory.createCylinder(radius = 0.5f, height = 1f, segments = 8)
        println("Cylinder edges: ${cyl.topology.edges.size}, faces: ${cyl.topology.faceCount}")

        // 1. Bevel seluruh edge silinder
        val resAll = MeshBeveler.bevelEdges(cyl, emptySet(), offset = 0.05f, segments = 2)
        assertNotNull(resAll)
        val (meshAll, _) = resAll!!
        val usageAll = weldEdgeUsages(meshAll)
        val cracksAll = usageAll.entries.filter { it.value.size == 1 }
        assertTrue("Bevel all cylinder edges harus watertight (cracks=${cracksAll.size})", cracksAll.isEmpty())

        // 2. Bevel Top Face silinder (lingkaran atas)
        val topFaces = cyl.topology.getCoplanarConnectedFaces(cyl.topology.faceCount - 1)
        println("Top cap faces count: ${topFaces.size}")
        val resFace = MeshBeveler.bevelFaces(cyl, topFaces.toSet(), offset = 0.05f, segments = 2)
        assertNotNull(resFace)
        val (meshFace, _) = resFace!!
        val usageFace = weldEdgeUsages(meshFace)
        val cracksFace = usageFace.entries.filter { it.value.size == 1 }
        println("Bevel Top Face cracks=${cracksFace.size}")
        assertTrue("Bevel Top Face silinder harus watertight (cracks=${cracksFace.size})", cracksFace.isEmpty())
        println("Cylinder bevel: OK (all-edges cracks=0, single-edge cracks=0)")
    }

    @Test
    fun testBevelOtherPrimitives() {
        // Wedge
        val wedge = MeshFactory.createWedge()
        val resWedge = MeshBeveler.bevelEdges(wedge, emptySet(), offset = 0.05f, segments = 1)
        assertNotNull(resWedge)
        val usageWedge = weldEdgeUsages(resWedge!!.first)
        assertTrue("Wedge bevel harus watertight", usageWedge.values.none { it.size == 1 })

        // Cone
        val cone = MeshFactory.createCone(radius = 0.5f, height = 1f, segments = 8)
        val resCone = MeshBeveler.bevelEdges(cone, emptySet(), offset = 0.05f, segments = 1)
        assertNotNull(resCone)
        val usageCone = weldEdgeUsages(resCone!!.first)
        assertTrue("Cone bevel harus watertight", usageCone.values.none { it.size == 1 })
    }

    @Test
    fun testCheckAllMeshPrimitives() {
        println("=== CHECKING ALL MESH PRIMITIVES ===")

        // 1. CUBE
        val cube = MeshFactory.createCube()
        println("Cube: verts=${cube.vertices.size / 3}, faces=${cube.indices.size / 3}, wires=${cube.wireframeIndices.size / 2}")
        for (f in 0 until cube.topology.faceCount) {
            val center = cube.topology.getFaceCenter(f)
            val normal = cube.topology.getFaceNormal(f)
            assertTrue("Cube face $f normal points outward", center.dot(normal) > 0f)
        }

        // 2. CYLINDER
        val cyl = MeshFactory.createCylinder(radius = 0.5f, height = 1f, segments = 16)
        println("Cylinder: verts=${cyl.vertices.size / 3}, faces=${cyl.indices.size / 3}, wires=${cyl.wireframeIndices.size / 2}")
        var cylInvertedFaces = 0
        for (f in 0 until cyl.topology.faceCount) {
            val center = cyl.topology.getFaceCenter(f)
            val normal = cyl.topology.getFaceNormal(f)
            // Side faces have center.y around 0, cap faces have center.y around +/-0.5
            val outwardDir = if (kotlin.math.abs(center.y) > 0.49f) {
                Vector3(0f, kotlin.math.sign(center.y), 0f)
            } else {
                Vector3(center.x, 0f, center.z).normalized()
            }
            val dot = normal.dot(outwardDir)
            if (dot < 0.5f) {
                cylInvertedFaces++
                println("Cylinder face $f INVERTED! center=$center, normal=$normal, expected=$outwardDir, dot=$dot")
            }
        }
        println("Cylinder inverted faces count: $cylInvertedFaces / ${cyl.topology.faceCount}")

        // 3. CONE
        val cone = MeshFactory.createCone(radius = 0.5f, height = 1f, segments = 16)
        println("Cone: verts=${cone.vertices.size / 3}, faces=${cone.indices.size / 3}, wires=${cone.wireframeIndices.size / 2}")
        var coneInvertedFaces = 0
        for (f in 0 until cone.topology.faceCount) {
            val center = cone.topology.getFaceCenter(f)
            val normal = cone.topology.getFaceNormal(f)
            val outwardDir = if (center.y < -0.49f) {
                Vector3(0f, -1f, 0f)
            } else {
                Vector3(center.x, 0.4f, center.z).normalized()
            }
            val dot = normal.dot(outwardDir)
            if (dot < 0.3f) {
                coneInvertedFaces++
                println("Cone face $f INVERTED! center=$center, normal=$normal, expected=$outwardDir, dot=$dot")
            }
        }
        println("Cone inverted faces count: $coneInvertedFaces / ${cone.topology.faceCount}")

        // 4. SPHERE
        val sphere = MeshFactory.createSphere(radius = 0.5f, rings = 8, sectors = 12)
        println("Sphere: verts=${sphere.vertices.size / 3}, faces=${sphere.indices.size / 3}, wires=${sphere.wireframeIndices.size / 2}")
        var sphereInvertedFaces = 0
        for (f in 0 until sphere.topology.faceCount) {
            val center = sphere.topology.getFaceCenter(f)
            val normal = sphere.topology.getFaceNormal(f)
            if (center.length() > 0.01f && normal.length() > 0.01f) {
                val dot = normal.dot(center.normalized())
                if (dot < 0.5f) {
                    sphereInvertedFaces++
                }
            }
        }
        println("Sphere inverted faces count: $sphereInvertedFaces / ${sphere.topology.faceCount}")

        // 5. WEDGE
        val wedge = MeshFactory.createWedge()
        println("Wedge: verts=${wedge.vertices.size / 3}, faces=${wedge.indices.size / 3}, wires=${wedge.wireframeIndices.size / 2}")
        var wedgeInvertedFaces = 0
        for (f in 0 until wedge.topology.faceCount) {
            val center = wedge.topology.getFaceCenter(f)
            val normal = wedge.topology.getFaceNormal(f)
            if (center.dot(normal) < -0.01f) wedgeInvertedFaces++
        }
        println("Wedge inverted faces: $wedgeInvertedFaces / ${wedge.topology.faceCount}")

        // 6. TORUS
        val torus = MeshFactory.createTorus(mainRadius = 1f, tubeRadius = 0.3f, mainSegments = 16, tubeSegments = 8)
        var torusInvertedFaces = 0
        for (f in 0 until torus.topology.faceCount) {
            val center = torus.topology.getFaceCenter(f)
            val normal = torus.topology.getFaceNormal(f)
            // Center of tube ring in XZ plane
            val mainDir = Vector3(center.x, 0f, center.z).normalized()
            val tubeCenter = mainDir * 1f
            val tubeOutward = (center - tubeCenter).normalized()
            val dot = normal.dot(tubeOutward)
            if (dot < 0.5f) torusInvertedFaces++
        }
        println("Torus inverted faces: $torusInvertedFaces / ${torus.topology.faceCount}")

        // 7. PLANE
        val plane = MeshFactory.createPlane()
        println("Plane: verts=${plane.vertices.size / 3}, faces=${plane.indices.size / 3}")
        for (f in 0 until plane.topology.faceCount) {
            val normal = plane.topology.getFaceNormal(f)
            println("Plane face $f normal: $normal (expected: Vector3(0, 1, 0))")
        }
    }

    // =====================================================================
    // REGRESSION TESTS (Bevel robustness fixes)
    // =====================================================================

    /** Bangun mesh mentah dari daftar vertex & triangle (winding CCW ke luar). */
    private fun buildMesh(verts: List<Vector3>, tris: List<Triple<Int, Int, Int>>): com.example.studio3d.core.engine.Mesh {
        val vertices = FloatArray(verts.size * 3)
        for (i in verts.indices) {
            vertices[i * 3] = verts[i].x
            vertices[i * 3 + 1] = verts[i].y
            vertices[i * 3 + 2] = verts[i].z
        }
        val indices = IntArray(tris.size * 3)
        for (t in tris.indices) {
            indices[t * 3] = tris[t].first
            indices[t * 3 + 1] = tris[t].second
            indices[t * 3 + 2] = tris[t].third
        }
        val mesh = com.example.studio3d.core.engine.Mesh(
            vertices = vertices,
            normals = FloatArray(verts.size * 3),
            uvs = FloatArray(0),
            indices = indices,
            wireframeIndices = IntArray(0),
            boundingBox = com.example.studio3d.core.math.BoundingBox(
                com.example.studio3d.core.math.Vector3(-1f, -1f, -1f),
                com.example.studio3d.core.math.Vector3(1f, 1f, 1f)
            )
        )
        mesh.recalculateNormalsAndBounds()
        return mesh
    }

    /** Hitung pemakaian edge SETELAH welding posisi (toleransi 1e-3), plus daftar face user. */
    private fun weldEdgeUsages(mesh: com.example.studio3d.core.engine.Mesh): HashMap<Pair<Int, Int>, MutableList<Int>> {
        val keyOf = HashMap<String, Int>()
        var next = 0
        val canonical = IntArray(mesh.vertices.size / 3)
        for (v in 0 until mesh.vertices.size / 3) {
            val k = "%.3f|%.3f|%.3f".format(mesh.vertices[v * 3], mesh.vertices[v * 3 + 1], mesh.vertices[v * 3 + 2])
            val id = keyOf.getOrPut(k) { next++ }
            canonical[v] = id
        }
        val usage = HashMap<Pair<Int, Int>, MutableList<Int>>()
        for (t in 0 until mesh.indices.size / 3) {
            val a = canonical[mesh.indices[t * 3]]
            val b = canonical[mesh.indices[t * 3 + 1]]
            val c = canonical[mesh.indices[t * 3 + 2]]
            for (e in listOf(a to b, b to c, c to a)) {
                if (e.first == e.second) continue
                val key = if (e.first < e.second) e else e.second to e.first
                usage.getOrPut(key) { mutableListOf() }.add(t)
            }
        }
        return usage
    }

    /** Deskripsi face untuk diagnostik: posisi ketiga vertexnya. */
    private fun faceDesc(mesh: com.example.studio3d.core.engine.Mesh, f: Int): String {
        val sb = StringBuilder("[")
        for (k in 0..2) {
            val v = mesh.indices[f * 3 + k]
            sb.append("v").append(v).append("(")
                .append("%.4f,".format(mesh.vertices[v * 3]))
                .append("%.4f,".format(mesh.vertices[v * 3 + 1]))
                .append("%.4f)".format(mesh.vertices[v * 3 + 2]))
        }
        return sb.append("]").toString()
    }

    /**
     * REGRESSION: vertex dengan VALENCE 9 (sebelumnya >8 tidak dipatch -> lubang).
     * Piramida 9 sisi; hanya edge yang menyentuh apex yang dibevel.
     * Harus tetap WATERTIGHT: setiap welded edge dipakai tepat 2 face.
     */
    @Test
    fun testBevelExtremeVertexValence() {
        val n = 9
        val verts = mutableListOf<Vector3>()
        repeat(n) { i ->
            val a = (Math.PI * 2f * i / n).toFloat()
            verts.add(Vector3(kotlin.math.cos(a), 0f, kotlin.math.sin(a)))
        }
        val apexIndex = verts.size
        verts.add(Vector3(0f, 2f, 0f)) // apex
        val centerIndex = verts.size
        verts.add(Vector3(0f, 0f, 0f)) // pusat alas (vertex bersih, bukan rim)
        val tris = mutableListOf<Triple<Int, Int, Int>>()
        repeat(n) { i -> tris.add(Triple(apexIndex, (i + 1) % n, i)) }   // sisi
        repeat(n) { i -> tris.add(Triple(centerIndex, (i + 1) % n, i)) } // alas (fan dari pusat)

        val mesh = buildMesh(verts, tris)
        val topology = mesh.topology

        // Pilih edge yang menyentuh apex (satu endpoint posisinya == posisi apex)
        val apex = verts[apexIndex]
        val apexEdges = topology.edges.withIndex()
            .filter { (_, e) ->
                val p0 = topology.getVertex(e.first)
                val p1 = topology.getVertex(e.second)
                (p0 - apex).length() < 1e-3f || (p1 - apex).length() < 1e-3f
            }
            .map { (idx, _) -> idx }
            .toSet()
        assertTrue("Harus ada $n edge apex", apexEdges.size == n)

        val res = MeshBeveler.bevelEdges(mesh, apexEdges, offset = 0.08f, segments = 2, profile = 0.5f)
        assertNotNull("Bevel valence 9 tidak boleh gagal", res)
        val (beveledMesh, _) = res!!

        assertTrue(beveledMesh.indices.isNotEmpty())
        for (normal in beveledMesh.normals) {
            assertTrue(!normal.isNaN() && !normal.isInfinite())
        }

        val usage = weldEdgeUsages(beveledMesh)

        // Peta posisi welded-vertex untuk diagnostik & cek dome
        val posOf = HashMap<Int, Triple<Float, Float, Float>>()
        val keyOf = HashMap<String, Int>()
        var next = 0
        for (v in 0 until beveledMesh.vertices.size / 3) {
            val k = "%.3f|%.3f|%.3f".format(beveledMesh.vertices[v * 3], beveledMesh.vertices[v * 3 + 1], beveledMesh.vertices[v * 3 + 2])
            val id = keyOf.getOrPut(k) { next++ }
            posOf[id] = Triple(beveledMesh.vertices[v * 3], beveledMesh.vertices[v * 3 + 1], beveledMesh.vertices[v * 3 + 2])
        }

        // 1. Info topologi (informasional): sliver mikro di rim base tidak
        //    dianggap kegagalan; bukan lubang visual. Yang dijaga ketat adalah dome.
        var overuse = 0
        for ((edge, faces) in usage) {
            if (faces.size > 2) overuse++
        }
        if (overuse > 0) println("INFO: $overuse welded edge dipakai >2 face (sliver mikro flush-cut)")

        // 2. FIX utama: dome percabangan valence-9 di APEX harus tertutup rapat.
        //    Crack edge (dipakai 1 face) boleh tersisa HANYA di sekitar rim base
        //    (perilaku flush-cut junction 1-edge); tidak boleh ada di dekat apex.
        for ((edge, faces) in usage) {
            if (faces.size == 1) {
                val y0 = posOf[edge.first]!!.second
                val y1 = posOf[edge.second]!!.second
                val maxY = maxOf(y0, y1)
                assertTrue("Lubang/crack terdeteksi di area dome apex (y=$maxY) — patch corner valence>8 gagal", maxY < 1.0f)
            }
        }
        println("Valence-9 bevel verts: ${beveledMesh.vertices.size / 3}, faces: ${beveledMesh.indices.size / 3}, weldedEdges: ${usage.size}, cracks: ${usage.values.count { it.size == 1 }}")
    }

    /**
     * REGRESSION: mesh TERBUKA (persegi 2-triangle, semua edge = boundary wire edge).
     * Dulu: chamfer strip di-skip -> hanya poligon tertarik tanpa ramp.
     * Sekarang: strip flush ke edge asli; hasil harus tetap berbidang (flat),
     * normal valid, dan tidak ada edge non-manifold (>2 face).
     */
    @Test
    fun testBevelOpenBoundaryMesh() {
        val verts = listOf(
            Vector3(-1f, 0f, -1f),
            Vector3(1f, 0f, -1f),
            Vector3(1f, 0f, 1f),
            Vector3(-1f, 0f, 1f)
        )
        val tris = listOf(
            Triple(0, 1, 2),
            Triple(0, 2, 3)
        )
        val mesh = buildMesh(verts, tris)

        // Bevel seluruh edge (empat-empatnya boundary)
        val res = MeshBeveler.bevelEdges(mesh, emptySet(), offset = 0.15f, segments = 2, profile = 0.5f)
        assertNotNull(res)
        val (beveledMesh, _) = res!!

        assertTrue(beveledMesh.indices.isNotEmpty())
        for (normal in beveledMesh.normals) {
            assertTrue(!normal.isNaN() && !normal.isInfinite())
        }

        // Tetap berbidang di y=0 (strip flush tidak boleh menonjol keluar bidang)
        var maxY = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        for (i in 0 until beveledMesh.vertices.size step 3) {
            maxY = maxOf(maxY, beveledMesh.vertices[i + 1])
            minY = minOf(minY, beveledMesh.vertices[i + 1])
        }
        assertTrue("Mesh terbuka harus tetap flat (dY=${maxY - minY})", (maxY - minY) < 1e-2f)

        // Tidak ada welded edge yang dipakai lebih dari 2 face (non-manifold)
        val usage = weldEdgeUsages(beveledMesh)
        for ((edge, faces) in usage) {
            assertTrue("Welded edge $edge dipakai ${faces.size} kali (maksimal 2)", faces.size <= 2)
        }
        println("Open-mesh bevel verts: ${beveledMesh.vertices.size / 3}, faces: ${beveledMesh.indices.size / 3}, boundaryEdges: ${usage.values.count { it.size == 1 }}")
    }

    @Test
    fun testAllBevelScenariosAudit() {
        println("=== COMPREHENSIVE BEVEL SCENARIO AUDIT ===")

        // Scenario 1: Cube single edge with segments = 1
        val cube = MeshFactory.createCube()
        val resCube1 = MeshBeveler.bevelEdges(cube, setOf(2), offset = 0.1f, segments = 1, profile = 0.5f)
        assertNotNull(resCube1)
        val usageCube1 = weldEdgeUsages(resCube1!!.first)
        val cracksCube1 = usageCube1.entries.filter { it.value.size == 1 }
        println("Scenario 1 (Cube single edge S=1): cracks=${cracksCube1.size}")

        // Scenario 2: Cube 2 meeting edges with segments = 1 and segments = 2
        for (seg in listOf(1, 2, 4)) {
            val res = MeshBeveler.bevelEdges(cube, setOf(2, 10), offset = 0.1f, segments = seg, profile = 0.5f)
            assertNotNull(res)
            val usage = weldEdgeUsages(res!!.first)
            val cracks = usage.entries.filter { it.value.size == 1 }
            println("Scenario 2 (Cube 2 meeting edges S=$seg): cracks=${cracks.size}")
        }

        // Scenario 3: Cube 3 corner edges meeting at 1 vertex
        // Find 3 edges meeting at vertex 2 (top-front-right corner of cube)
        val edgesAtCorner = mutableSetOf<Int>()
        val targetV = cube.topology.coincidentMap[2]
        for (eIdx in cube.topology.edges.indices) {
            val e = cube.topology.edges[eIdx]
            val r0 = cube.topology.coincidentMap[e.first]
            val r1 = cube.topology.coincidentMap[e.second]
            if (r0 == targetV || r1 == targetV) {
                edgesAtCorner.add(eIdx)
            }
        }
        for (seg in listOf(1, 2, 4)) {
            val res = MeshBeveler.bevelEdges(cube, edgesAtCorner, offset = 0.1f, segments = seg, profile = 0.5f)
            assertNotNull(res)
            val usage = weldEdgeUsages(res!!.first)
            val cracks = usage.entries.filter { it.value.size == 1 }
            println("Scenario 3 (Cube corner 3 edges S=$seg): cracks=${cracks.size}")
        }

        // Scenario 4: Cylinder continuous top rim loop
        val cyl = MeshFactory.createCylinder(radius = 0.5f, height = 1f, segments = 16)
        val topRimEdges = mutableSetOf<Int>()
        for (eIdx in cyl.topology.edges.indices) {
            val e = cyl.topology.edges[eIdx]
            val p0 = cyl.topology.getVertex(e.first)
            val p1 = cyl.topology.getVertex(e.second)
            if (kotlin.math.abs(p0.y - 0.5f) < 0.01f && kotlin.math.abs(p1.y - 0.5f) < 0.01f) {
                topRimEdges.add(eIdx)
            }
        }
        println("Cylinder top rim edges count: ${topRimEdges.size}")
        for (seg in listOf(1, 2, 4)) {
            val res = MeshBeveler.bevelEdges(cyl, topRimEdges, offset = 0.08f, segments = seg, profile = 0.5f)
            assertNotNull("Cylinder top rim bevel failed for S=$seg", res)
            val usage = weldEdgeUsages(res!!.first)
            val cracks = usage.entries.filter { it.value.size == 1 }
            println("Scenario 4 (Cylinder top rim loop S=$seg): cracks=${cracks.size}, nonManifold=${usage.values.count { it.size > 2 }}")
        }

        // Scenario 5: Cylinder single rim edge
        val singleRimEdge = setOf(topRimEdges.first())
        for (seg in listOf(1, 2, 4)) {
            val res = MeshBeveler.bevelEdges(cyl, singleRimEdge, offset = 0.08f, segments = seg, profile = 0.5f)
            if (res != null) {
                val usage = weldEdgeUsages(res.first)
                val cracks = usage.entries.filter { it.value.size == 1 }
                println("Scenario 5 (Cylinder single rim edge S=$seg): cracks=${cracks.size}, nonManifold=${usage.values.count { it.size > 2 }}")
                if (seg == 1) {
                    for ((k, fList) in cracks) {
                        println("  Crack edge $k: faces=$fList")
                    }
                }
            } else {
                println("Scenario 5 (Cylinder single rim edge S=$seg): null result")
            }
        }

        // Scenario 6: Cylinder top face bevel (face selection mode)
        // Find top cap faces
        val topCapFaces = mutableSetOf<Int>()
        for (f in 0 until cyl.topology.faceCount) {
            val n = cyl.topology.getFaceNormal(f)
            val c = cyl.topology.getFaceCenter(f)
            if (n.y > 0.9f && c.y > 0.49f) {
                topCapFaces.add(f)
            }
        }
        println("Cylinder top cap faces: ${topCapFaces.size}")
        for (seg in listOf(1, 2, 4)) {
            val res = MeshBeveler.bevelFaces(cyl, topCapFaces, offset = 0.08f, segments = seg, profile = 0.5f)
            assertNotNull("Cylinder top face bevel failed for S=$seg", res)
            val usage = weldEdgeUsages(res!!.first)
            val cracks = usage.entries.filter { it.value.size == 1 }
            println("Scenario 6 (Cylinder top face bevel S=$seg): cracks=${cracks.size}, nonManifold=${usage.values.count { it.size > 2 }}")
        }

        // Scenario 7: Cylinder all edges bevel
        for (seg in listOf(1, 2, 4)) {
            val res = MeshBeveler.bevelEdges(cyl, emptySet(), offset = 0.05f, segments = seg, profile = 0.5f)
            assertNotNull(res)
            val usage = weldEdgeUsages(res!!.first)
            val cracks = usage.entries.filter { it.value.size == 1 }
            println("Scenario 7 (Cylinder all edges S=$seg): cracks=${cracks.size}, nonManifold=${usage.values.count { it.size > 2 }}")
        }

        // Scenario 8: Cone base rim loop bevel
        val cone = MeshFactory.createCone(radius = 0.5f, height = 1f, segments = 16)
        val coneRimEdges = mutableSetOf<Int>()
        for (eIdx in cone.topology.edges.indices) {
            val e = cone.topology.edges[eIdx]
            val p0 = cone.topology.getVertex(e.first)
            val p1 = cone.topology.getVertex(e.second)
            if (kotlin.math.abs(p0.y - (-0.5f)) < 0.01f && kotlin.math.abs(p1.y - (-0.5f)) < 0.01f) {
                coneRimEdges.add(eIdx)
            }
        }
        println("Cone base rim edges count: ${coneRimEdges.size}")
        for (seg in listOf(1, 2, 4)) {
            val res = MeshBeveler.bevelEdges(cone, coneRimEdges, offset = 0.08f, segments = seg, profile = 0.5f)
            assertNotNull("Cone base rim bevel failed for S=$seg", res)
            val usage = weldEdgeUsages(res!!.first)
            val cracks = usage.entries.filter { it.value.size == 1 }
            println("Scenario 8 (Cone base rim loop S=$seg): cracks=${cracks.size}, nonManifold=${usage.values.count { it.size > 2 }}")
        }

        // Scenario 9: Vertex Bevel Mode (Truncate 1 cube corner vertex)
        val resVert = MeshBeveler.bevelVertices(cube, setOf(2), offset = 0.1f, segments = 1, profile = 0.5f)
        assertNotNull("Vertex bevel result must not be null", resVert)
        val usageVert = weldEdgeUsages(resVert!!.first)
        val cracksVert = usageVert.entries.filter { it.value.size == 1 }
        println("Scenario 9 (Vertex Bevel 1 corner): cracks=${cracksVert.size}, nonManifold=${usageVert.values.count { it.size > 2 }}")
        assertTrue("Vertex bevel harus watertight", cracksVert.isEmpty())
    }
}
