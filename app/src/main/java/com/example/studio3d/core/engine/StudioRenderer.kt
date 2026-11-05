package com.example.studio3d.core.engine

import com.example.studio3d.core.math.*
import com.example.studio3d.core.engine.*
import com.example.studio3d.core.scene.*
import com.example.studio3d.core.history.*
import com.example.studio3d.core.preferences.*
import com.example.studio3d.core.project.*
import com.example.studio3d.feature.editor.viewport.*
import com.example.studio3d.feature.editor.tools.select.*


import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import com.example.studio3d.core.math.Matrix4
import com.example.studio3d.core.math.Vector3
import com.example.studio3d.core.scene.SceneGraph
import com.example.studio3d.core.scene.SceneNode
import com.example.studio3d.feature.editor.screen.ModelingMode
import com.example.studio3d.feature.editor.screen.SelectionMode
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class StudioRenderer(
    val context: Context,
    val sceneGraph: SceneGraph,
    val camera: Camera,
    val gizmo: Gizmo
) : GLSurfaceView.Renderer {

    var shadingMode: ShadingMode = ShadingMode.MATERIAL
    var isTransparentView: Boolean = false
    var modelingMode: ModelingMode = ModelingMode.OBJECT
    var selectionMode: SelectionMode = SelectionMode.VERTEX
    var editSelection: EditSelectionManager? = null

    private var programUnlit: ShaderProgram? = null
    private var programClay: ShaderProgram? = null
    private var programMaterial: ShaderProgram? = null
    private var programStudio: ShaderProgram? = null
    private var programBg: ShaderProgram? = null
    private var programGrid: ShaderProgram? = null
    private var programPoint: ShaderProgram? = null
    private var dynamicEbo: Int = 0

    private var bgQuadMesh: Mesh? = null
    private var groundPlaneMesh: Mesh? = null
    private var floorAxisMesh: Mesh? = null
    private var gizmoShaftMesh: Mesh? = null
    private var gizmoConeMesh: Mesh? = null
    private var gizmoBoxMesh: Mesh? = null

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Canvas Background Hex #3C3C3C
        GLES30.glClearColor(0.235f, 0.235f, 0.235f, 1.0f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glDisable(GLES30.GL_CULL_FACE) // Two-sided rendering enabled by default for 3D modeling viewport (like Blender)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        // Initialize Shader Programs
        programUnlit = ShaderProgram(Shaders.VERTEX_SHADER_3D, Shaders.FRAGMENT_SHADER_UNLIT)
        programClay = ShaderProgram(Shaders.VERTEX_SHADER_3D, Shaders.FRAGMENT_SHADER_CLAY)
        programMaterial = ShaderProgram(Shaders.VERTEX_SHADER_3D, Shaders.FRAGMENT_SHADER_MATERIAL)
        programStudio = ShaderProgram(Shaders.VERTEX_SHADER_3D, Shaders.FRAGMENT_SHADER_STUDIO)
        programBg = ShaderProgram(Shaders.VERTEX_SHADER_BG, Shaders.FRAGMENT_SHADER_BG)
        programGrid = ShaderProgram(Shaders.VERTEX_SHADER_GRID, Shaders.FRAGMENT_SHADER_GRID)
        programPoint = ShaderProgram(Shaders.VERTEX_SHADER_POINT, Shaders.FRAGMENT_SHADER_POINT)

        // Dynamic EBO for Edit Mode overlay selections
        val dynBufs = IntArray(1)
        GLES30.glGenBuffers(1, dynBufs, 0)
        dynamicEbo = dynBufs[0]

        // Generate static meshes
        bgQuadMesh = MeshFactory.createFullscreenQuad()
        bgQuadMesh?.uploadToGL()

        groundPlaneMesh = MeshFactory.createGroundPlane(size = 200f)
        groundPlaneMesh?.uploadToGL()

        floorAxisMesh = MeshFactory.createFloorAxisLines(size = 300f)
        floorAxisMesh?.uploadToGL()

        gizmoShaftMesh = MeshFactory.createGizmoShaft(radius = 0.008f, length = 0.45f, segments = 16)
        gizmoShaftMesh?.uploadToGL()

        gizmoConeMesh = MeshFactory.createGizmoCone(radius = 0.026f, length = 0.13f, segments = 16)
        gizmoConeMesh?.uploadToGL()

        gizmoBoxMesh = MeshFactory.createCube(0.032f, 0.032f, 0.032f)
        gizmoBoxMesh?.uploadToGL()

        // Reset texture IDs on fresh GL context
        TextureLoader.clear()

        // Ensure all scene nodes have their meshes uploaded
        for (node in sceneGraph.nodes.values) {
            node.material.textureId = 0
            node.ensureMesh()
            node.mesh?.uploadToGL()
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        camera.viewportWidth = width
        camera.viewportHeight = height
        camera.updateMatrices()
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        // 1. Render subtle studio background gradient
        renderBackground()

        camera.updateMatrices()

        // 2. Render Ground Studio Grid & Main Axes on floor plane (Y=0)
        renderGroundGrid()

        // 3. Render Scene Nodes (Opaque solid meshes render on top of floor grid)
        val nodes = sceneGraph.nodes.values.toList()
        for (node in nodes) {
            if (!node.isVisible || node.meshType == "group") continue
            renderNode(node)
        }

        // 4. Render Transform Gizmo
        val selectedNode = sceneGraph.getSelectedNode()
        if (selectedNode != null && gizmo.mode != TransformMode.SELECT) {
            renderGizmo(selectedNode)
        }
    }

    private fun renderBackground() {
        val mesh = bgQuadMesh ?: return
        val prog = programBg ?: return
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        prog.use()
        mesh.renderTriangles()
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }

    private fun renderGroundGrid() {
        val mesh = groundPlaneMesh ?: return
        val prog = programGrid ?: return
        prog.use()

        // Determine if camera is in Orthographic Side View (Elevation ~ 0°)
        val gridPlane = when {
            camera.isOrthographic && (kotlin.math.abs(camera.elevationDeg) < 30f) -> {
                val normAz = ((camera.azimuthDeg % 360f) + 360f) % 360f
                val distFront = kotlin.math.min(kotlin.math.abs(normAz - 0f), kotlin.math.abs(normAz - 360f))
                val distBack = kotlin.math.abs(normAz - 180f)
                val distZ = kotlin.math.min(distFront, distBack)

                val distRight = kotlin.math.abs(normAz - 90f)
                val distLeft = kotlin.math.abs(normAz - 270f)
                val distX = kotlin.math.min(distRight, distLeft)

                if (distZ <= distX) 1 else 2 // 1 = Front/Back (XY), 2 = Left/Right (ZY)
            }
            else -> 0 // 0 = Horizontal Floor (XZ)
        }

        val modelMatrix = Matrix4.identity()
        if (gridPlane == 1) {
            // Front/Back view: Orient grid on XY plane passing through scene center / camera target
            modelMatrix.translate(0f, 0f, camera.target.z)
            modelMatrix.rotate(90f, 1f, 0f, 0f)
        } else if (gridPlane == 2) {
            // Left/Right view: Orient grid on ZY plane passing through scene center / camera target
            modelMatrix.translate(camera.target.x, 0f, 0f)
            modelMatrix.rotate(90f, 0f, 0f, 1f)
        }

        setMatrixUniforms(prog, modelMatrix)
        setCameraPosUniform(prog)

        val uGridPlaneLoc = prog.getUniformLocation("uGridPlane")
        if (uGridPlaneLoc >= 0) {
            GLES30.glUniform1i(uGridPlaneLoc, gridPlane)
        }

        val uMaxDistLoc = prog.getUniformLocation("uMaxDistance")
        val maxDist = if (camera.isOrthographic) {
            kotlin.math.max(180f, camera.orthoSize * 12.0f)
        } else {
            kotlin.math.max(180f, camera.distance * 12.0f)
        }
        if (uMaxDistLoc >= 0) {
            GLES30.glUniform1f(uMaxDistLoc, maxDist)
        }

        val uSubdivLoc = prog.getUniformLocation("uSubdivColor")
        if (uSubdivLoc >= 0) {
            // Neutral dark gray 1m subdiv line (Blender style)
            GLES30.glUniform4f(uSubdivLoc, 0.38f, 0.40f, 0.44f, 0.85f)
        }

        val uMajorLoc = prog.getUniformLocation("uMajorColor")
        if (uMajorLoc >= 0) {
            // Prominent 5m major line (Blender style)
            GLES30.glUniform4f(uMajorLoc, 0.52f, 0.55f, 0.62f, 1.0f)
        }

        val uAxisXLoc = prog.getUniformLocation("uAxisXColor")
        if (uAxisXLoc >= 0) {
            // Red X-Axis line (Blender style)
            GLES30.glUniform4f(uAxisXLoc, 0.96f, 0.24f, 0.26f, 1.0f)
        }

        val uAxisZLoc = prog.getUniformLocation("uAxisZColor")
        if (uAxisZLoc >= 0) {
            // Green Floor-Axis line (Blender style)
            GLES30.glUniform4f(uAxisZLoc, 0.24f, 0.86f, 0.36f, 1.0f)
        }

        val uAxisYLoc = prog.getUniformLocation("uAxisYColor")
        if (uAxisYLoc >= 0) {
            // Vibrant Blue Upright Y-Axis (Blender style)
            GLES30.glUniform4f(uAxisYLoc, 0.24f, 0.54f, 0.96f, 1.0f)
        }

        // Render floor grid with depth test enabled and depth write disabled:
        // Solves transparency bug: solid objects rendered afterwards will cleanly occlude the grid!
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glDepthMask(false)

        mesh.renderTriangles()

        // Render Floor Axis Line primitives (ensures ground lines are ALWAYS visible even edge-on in 3D perspective)
        floorAxisMesh?.let { axisMesh ->
            val unlitProg = programUnlit
            if (unlitProg != null) {
                unlitProg.use()
                val idMatrix = Matrix4.identity()
                setMatrixUniforms(unlitProg, idMatrix)
                val colorLoc = unlitProg.getUniformLocation("uColor")
                GLES30.glLineWidth(2.5f)

                // Render X-Axis line (indices 0..2) in Red
                if (colorLoc >= 0) {
                    GLES30.glUniform4f(colorLoc, 0.96f, 0.24f, 0.26f, 1.0f)
                }
                GLES30.glBindVertexArray(axisMesh.vaoId)
                GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, axisMesh.eboWireframe)
                GLES30.glDrawElements(GLES30.GL_LINES, 2, GLES30.GL_UNSIGNED_INT, 0)

                // Render Z-Axis line (indices 2..4) in Green
                if (colorLoc >= 0) {
                    GLES30.glUniform4f(colorLoc, 0.24f, 0.86f, 0.36f, 1.0f)
                }
                GLES30.glDrawElements(GLES30.GL_LINES, 2, GLES30.GL_UNSIGNED_INT, 2 * 4)
                GLES30.glBindVertexArray(0)
            }
        }

        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
    }

    private fun renderNode(node: SceneNode) {
        node.ensureMesh()
        val mesh = node.mesh ?: return
        val worldMatrix = node.getWorldTransformMatrix(sceneGraph.nodes)
        val isSelected = (node.id == sceneGraph.selectedNodeId)
        val isObjectSelectionActive = (isSelected && modelingMode == ModelingMode.OBJECT)

        val mat = node.material
        val effectiveAlpha = if (isTransparentView) 0.45f else mat.colorA

        if (isTransparentView) {
            // In Transparent/X-Ray view, disable backface culling & depth write
            // so inner contours and back geometry are clearly seen
            GLES30.glDisable(GLES30.GL_CULL_FACE)
            GLES30.glDepthMask(false)
        }

        when (shadingMode) {
            ShadingMode.WIREFRAME -> {
                val prog = programUnlit ?: return
                prog.use()
                setMatrixUniforms(prog, worldMatrix)
                val uColorLoc = prog.getUniformLocation("uColor")

                // Technical CAD Wireframe: depth prepass to hide occluded back edges unless X-ray is active
                if (!isTransparentView) {
                    GLES30.glColorMask(false, false, false, false)
                    GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL)
                    GLES30.glPolygonOffset(1.0f, 1.0f)
                    mesh.renderTriangles()
                    GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)
                    GLES30.glColorMask(true, true, true, true)
                }

                val wireColor = if (isObjectSelectionActive) {
                    floatArrayOf(1.0f, 0.82f, 0.10f, 1.0f) // Yellow selection
                } else {
                    floatArrayOf(0.12f, 0.80f, 1.0f, 1.0f) // Crisp Electric Cyan wireframe
                }
                GLES30.glLineWidth(2.0f)
                GLES30.glUniform4f(uColorLoc, wireColor[0], wireColor[1], wireColor[2], wireColor[3])
                mesh.renderWireframe()
                GLES30.glLineWidth(1.0f)
            }
            ShadingMode.CLAY -> {
                val prog = programClay ?: return
                prog.use()
                setMatrixUniforms(prog, worldMatrix)
                setCameraPosUniform(prog)
                val uBaseColorLoc = prog.getUniformLocation("uBaseColor")
                GLES30.glUniform4f(uBaseColorLoc, 0.78f, 0.76f, 0.74f, effectiveAlpha)

                // Polygon offset pushes solid fill back into depth buffer so wireframe overlay sits crisply on top with 0 Z-fighting
                GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL)
                GLES30.glPolygonOffset(1.5f, 2.0f)
                mesh.renderTriangles()
                GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)

                if (isObjectSelectionActive || node.material.showWireframe || isTransparentView) {
                    renderWireframeOverlay(mesh, worldMatrix, isObjectSelectionActive)
                }
            }
            ShadingMode.MATERIAL -> {
                val prog = programMaterial ?: return
                prog.use()
                setMatrixUniforms(prog, worldMatrix)
                setCameraPosUniform(prog)

                GLES30.glUniform4f(prog.getUniformLocation("uBaseColor"), mat.colorR, mat.colorG, mat.colorB, effectiveAlpha)
                GLES30.glUniform1f(prog.getUniformLocation("uMetallic"), mat.metallic)
                GLES30.glUniform1f(prog.getUniformLocation("uRoughness"), mat.roughness)
                GLES30.glUniform1f(prog.getUniformLocation("uClearcoat"), mat.clearcoat)
                GLES30.glUniform1i(prog.getUniformLocation("uIsSelected"), if (isObjectSelectionActive) 1 else 0)

                val uri = mat.textureUri
                var boundTexture = false
                if (!uri.isNullOrEmpty()) {
                    if (mat.textureId == 0 || !GLES30.glIsTexture(mat.textureId)) {
                        mat.textureId = TextureLoader.getOrLoadTexture(context, uri)
                    }
                    if (mat.textureId > 0 && GLES30.glIsTexture(mat.textureId)) {
                        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mat.textureId)
                        val uTexLoc = prog.getUniformLocation("uDiffuseTexture")
                        if (uTexLoc >= 0) GLES30.glUniform1i(uTexLoc, 0)
                        val uHasTexLoc = prog.getUniformLocation("uHasTexture")
                        if (uHasTexLoc >= 0) GLES30.glUniform1i(uHasTexLoc, 1)
                        boundTexture = true
                    }
                }
                if (!boundTexture) {
                    val uHasTexLoc = prog.getUniformLocation("uHasTexture")
                    if (uHasTexLoc >= 0) GLES30.glUniform1i(uHasTexLoc, 0)
                }

                // Polygon offset pushes solid fill back into depth buffer so wireframe overlay sits crisply on top with 0 Z-fighting
                GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL)
                GLES30.glPolygonOffset(1.5f, 2.0f)
                mesh.renderTriangles()
                GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)

                if (boundTexture) {
                    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
                }

                if (isObjectSelectionActive || mat.showWireframe || isTransparentView) {
                    renderWireframeOverlay(mesh, worldMatrix, isObjectSelectionActive)
                }
            }
            ShadingMode.STUDIO_RENDER -> {
                val prog = programStudio ?: return
                prog.use()
                setMatrixUniforms(prog, worldMatrix)
                setCameraPosUniform(prog)

                GLES30.glUniform4f(prog.getUniformLocation("uBaseColor"), mat.colorR, mat.colorG, mat.colorB, effectiveAlpha)
                GLES30.glUniform1f(prog.getUniformLocation("uMetallic"), mat.metallic)
                GLES30.glUniform1f(prog.getUniformLocation("uRoughness"), mat.roughness)
                GLES30.glUniform1f(prog.getUniformLocation("uClearcoat"), mat.clearcoat)
                GLES30.glUniform1i(prog.getUniformLocation("uIsSelected"), if (isObjectSelectionActive) 1 else 0)

                val uri = mat.textureUri
                var boundTexture = false
                if (!uri.isNullOrEmpty()) {
                    if (mat.textureId == 0 || !GLES30.glIsTexture(mat.textureId)) {
                        mat.textureId = TextureLoader.getOrLoadTexture(context, uri)
                    }
                    if (mat.textureId > 0 && GLES30.glIsTexture(mat.textureId)) {
                        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mat.textureId)
                        val uTexLoc = prog.getUniformLocation("uDiffuseTexture")
                        if (uTexLoc >= 0) GLES30.glUniform1i(uTexLoc, 0)
                        val uHasTexLoc = prog.getUniformLocation("uHasTexture")
                        if (uHasTexLoc >= 0) GLES30.glUniform1i(uHasTexLoc, 1)
                        boundTexture = true
                    }
                }
                if (!boundTexture) {
                    val uHasTexLoc = prog.getUniformLocation("uHasTexture")
                    if (uHasTexLoc >= 0) GLES30.glUniform1i(uHasTexLoc, 0)
                }

                // Polygon offset pushes solid fill back into depth buffer so wireframe overlay sits crisply on top with 0 Z-fighting
                GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL)
                GLES30.glPolygonOffset(1.5f, 2.0f)
                mesh.renderTriangles()
                GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)

                if (boundTexture) {
                    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
                }

                if (isObjectSelectionActive || mat.showWireframe || isTransparentView) {
                    renderWireframeOverlay(mesh, worldMatrix, isObjectSelectionActive)
                }
            }
        }

        if (isTransparentView) {
            GLES30.glDepthMask(true)
        }

        // If this node is active in Edit Mode, render Edit Mode selection overlays
        if (isSelected && modelingMode == ModelingMode.EDIT) {
            renderEditModeOverlays(mesh, worldMatrix)
        }
    }

    private fun renderEditModeOverlays(mesh: Mesh, worldMatrix: Matrix4) {
        val editMgr = editSelection ?: return
        val topology = mesh.topology

        // 1. In Edit Mode, always render geometry wireframe so mesh topology is clear
        renderWireframeOverlay(mesh, worldMatrix, isSelected = false)

        // 2. Render Selected Faces
        val selFaces = editMgr.getFacesSnapshot()
        if (selFaces.isNotEmpty()) {
            val prog = programUnlit ?: return
            prog.use()
            setMatrixUniforms(prog, worldMatrix)
            val uColorLoc = prog.getUniformLocation("uColor")
            // Blender style vibrant orange face selection with alpha
            GLES30.glUniform4f(uColorLoc, 1.0f, 0.58f, 0.12f, 0.50f)

            GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL)
            GLES30.glPolygonOffset(-1.0f, -1.0f)
            GLES30.glDisable(GLES30.GL_CULL_FACE)

            mesh.uploadToGL()
            if (mesh.isGeometryDirty) {
                mesh.updateBuffersOnGL()
                mesh.markClean()
            }
            GLES30.glBindVertexArray(mesh.vaoId)
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, mesh.eboTriangles)

            for (f in selFaces) {
                if (f in 0 until topology.faceCount) {
                    GLES30.glDrawElements(GLES30.GL_TRIANGLES, 3, GLES30.GL_UNSIGNED_INT, f * 3 * 4)
                }
            }

            GLES30.glBindVertexArray(0)
            GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)
        }

        // 3. Render Selected Edges
        val selEdges = editMgr.getEdgesSnapshot()
        if (selEdges.isNotEmpty()) {
            val prog = programUnlit ?: return
            prog.use()
            setMatrixUniforms(prog, worldMatrix)
            val uColorLoc = prog.getUniformLocation("uColor")
            // Vibrant glowing orange-amber line
            GLES30.glUniform4f(uColorLoc, 1.0f, 0.65f, 0.08f, 1.0f)

            val topologyEdges = topology.edges
            val validEdges = selEdges.filter { it in topologyEdges.indices }
            if (validEdges.isNotEmpty()) {
                val lineIndices = IntArray(validEdges.size * 2)
                val endpointSet = LinkedHashSet<Int>()
                var ptr = 0
                for (eIdx in validEdges) {
                    val (v0, v1) = topologyEdges[eIdx]
                    lineIndices[ptr++] = v0
                    lineIndices[ptr++] = v1
                    endpointSet.add(v0)
                    endpointSet.add(v1)
                    endpointSet.addAll(topology.getCoincidentVertices(v0))
                    endpointSet.addAll(topology.getCoincidentVertices(v1))
                }

                val buf = ByteBuffer.allocateDirect(lineIndices.size * 4).run {
                    order(ByteOrder.nativeOrder())
                    asIntBuffer().apply {
                        put(lineIndices)
                        position(0)
                    }
                }

                // Disable depth test so selected edges are always drawn in front of polygon surfaces
                GLES30.glDisable(GLES30.GL_DEPTH_TEST)
                GLES30.glLineWidth(5.0f)

                mesh.uploadToGL()
                GLES30.glBindVertexArray(mesh.vaoId)
                GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, dynamicEbo)
                GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, lineIndices.size * 4, buf, GLES30.GL_DYNAMIC_DRAW)
                GLES30.glDrawElements(GLES30.GL_LINES, lineIndices.size, GLES30.GL_UNSIGNED_INT, 0)

                GLES30.glLineWidth(1.0f)

                // Render both endpoint vertices of selected edges with glowing halo caps
                val endpointArray = endpointSet.filter { it in 0 until topology.vertexCount }.toIntArray()
                if (endpointArray.isNotEmpty() && programPoint != null) {
                    val progPt = programPoint!!
                    progPt.use()
                    setMatrixUniforms(progPt, worldMatrix)
                    val uPtColor = progPt.getUniformLocation("uColor")
                    val uPtSize = progPt.getUniformLocation("uPointSize")
                    val uHaloCol = progPt.getUniformLocation("uHaloColor")
                    val uHaloW = progPt.getUniformLocation("uHaloWidth")

                    val ptBuf = ByteBuffer.allocateDirect(endpointArray.size * 4).run {
                        order(ByteOrder.nativeOrder())
                        asIntBuffer().apply {
                            put(endpointArray)
                            position(0)
                        }
                    }
                    GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, endpointArray.size * 4, ptBuf, GLES30.GL_DYNAMIC_DRAW)

                    GLES30.glUniform1f(uPtSize, 17f)
                    GLES30.glUniform4f(uPtColor, 1.0f, 0.58f, 0.08f, 1.0f)
                    if (uHaloCol >= 0) GLES30.glUniform4f(uHaloCol, 1.0f, 0.85f, 0.22f, 1.0f)
                    if (uHaloW >= 0) GLES30.glUniform1f(uHaloW, 0.20f)

                    GLES30.glDrawElements(GLES30.GL_POINTS, endpointArray.size, GLES30.GL_UNSIGNED_INT, 0)
                }

                GLES30.glBindVertexArray(0)
                GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
                GLES30.glEnable(GLES30.GL_DEPTH_TEST)
            }
        }

        // 4. Render Vertices (Points)
        if (selectionMode == SelectionMode.VERTEX || editMgr.selectedVertices.isNotEmpty()) {
            val prog = programPoint ?: return
            prog.use()
            setMatrixUniforms(prog, worldMatrix)
            val uColorLoc = prog.getUniformLocation("uColor")
            val uPointSizeLoc = prog.getUniformLocation("uPointSize")
            val uHaloColorLoc = prog.getUniformLocation("uHaloColor")
            val uHaloWidthLoc = prog.getUniformLocation("uHaloWidth")

            mesh.uploadToGL()
            if (mesh.isGeometryDirty) {
                mesh.updateBuffersOnGL()
                mesh.markClean()
            }
            GLES30.glBindVertexArray(mesh.vaoId)

            // Draw all vertices as neutral points when in VERTEX selection mode
            if (selectionMode == SelectionMode.VERTEX) {
                if (uHaloWidthLoc >= 0) GLES30.glUniform1f(uHaloWidthLoc, 0f)
                GLES30.glUniform4f(uColorLoc, 0.15f, 0.15f, 0.18f, 0.95f)
                GLES30.glUniform1f(uPointSizeLoc, 12f)
                GLES30.glDrawArrays(GLES30.GL_POINTS, 0, topology.vertexCount)
            }

            // Draw selected vertices as prominent thick halo points (26px with Golden Yellow Halo and Vibrant Orange core)
            val selVerticesSnapshot = editMgr.getVerticesSnapshot()
            if (selVerticesSnapshot.isNotEmpty()) {
                val allSelectedVertices = LinkedHashSet<Int>()
                for (v in selVerticesSnapshot) {
                    if (v in 0 until topology.vertexCount) {
                        allSelectedVertices.add(v)
                        allSelectedVertices.addAll(topology.getCoincidentVertices(v))
                    }
                }
                val selVertices = allSelectedVertices.toIntArray()
                if (selVertices.isNotEmpty()) {
                    val buf = ByteBuffer.allocateDirect(selVertices.size * 4).run {
                        order(ByteOrder.nativeOrder())
                        asIntBuffer().apply {
                            put(selVertices)
                            position(0)
                        }
                    }

                    // Disable depth test so selected vertices always pop out in front of polygon surfaces
                    GLES30.glDisable(GLES30.GL_DEPTH_TEST)

                    GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, dynamicEbo)
                    GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, selVertices.size * 4, buf, GLES30.GL_DYNAMIC_DRAW)

                    GLES30.glUniform1f(uPointSizeLoc, 26f)
                    GLES30.glUniform4f(uColorLoc, 1.0f, 0.50f, 0.02f, 1.0f) // Vibrant Orange Core
                    if (uHaloColorLoc >= 0) {
                        GLES30.glUniform4f(uHaloColorLoc, 1.0f, 0.88f, 0.15f, 1.0f) // Golden Yellow Halo
                    }
                    if (uHaloWidthLoc >= 0) {
                        GLES30.glUniform1f(uHaloWidthLoc, 0.22f)
                    }

                    GLES30.glDrawElements(GLES30.GL_POINTS, selVertices.size, GLES30.GL_UNSIGNED_INT, 0)

                    GLES30.glEnable(GLES30.GL_DEPTH_TEST)
                }
            }

            GLES30.glBindVertexArray(0)
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
        }
    }

    private fun renderWireframeOverlay(mesh: Mesh, worldMatrix: Matrix4, isSelected: Boolean) {
        val prog = programUnlit ?: return
        prog.use()
        setMatrixUniforms(prog, worldMatrix)
        val uColorLoc = prog.getUniformLocation("uColor")
        if (isSelected) {
            // Selected object outline in Object Mode: Vibrant Amber-Gold
            GLES30.glUniform4f(uColorLoc, 1.0f, 0.82f, 0.10f, 1.0f)
        } else if (modelingMode == ModelingMode.EDIT) {
            if (shadingMode == ShadingMode.WIREFRAME) {
                // In Wireframe viewport mode: vibrant electric cyan for high contrast on dark canvas background
                GLES30.glUniform4f(uColorLoc, 0.12f, 0.82f, 1.0f, 1.0f)
            } else {
                // In solid modes (Clay, Material, Studio): solid pitch-black for razor-sharp visibility on mesh
                GLES30.glUniform4f(uColorLoc, 0.04f, 0.04f, 0.06f, 1.0f)
            }
        } else {
            if (shadingMode == ShadingMode.WIREFRAME) {
                GLES30.glUniform4f(uColorLoc, 0.12f, 0.80f, 1.0f, 1.0f)
            } else {
                GLES30.glUniform4f(uColorLoc, 0.12f, 0.14f, 0.18f, 0.90f)
            }
        }
        // Unified global 2.0f wireframe line width across all modes
        GLES30.glLineWidth(2.0f)
        mesh.renderWireframe()
        GLES30.glLineWidth(1.0f)
    }

    private fun renderGizmo(selectedNode: SceneNode) {
        val shaft = gizmoShaftMesh ?: return
        val cone = gizmoConeMesh ?: return
        val box = gizmoBoxMesh ?: return
        val prog = programUnlit ?: return
        prog.use()

        // Disable depth testing temporarily so gizmo is always drawn clearly on top
        GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT)

        val worldMat = selectedNode.getWorldTransformMatrix(sceneGraph.nodes)
        val pos = if (modelingMode == ModelingMode.EDIT &&
            editSelection?.hasSelection() == true &&
            selectedNode.mesh != null
        ) {
            val localPivot = editSelection!!.calculateLocalPivot(selectedNode.mesh!!)
            worldMat.transformPoint(localPivot)
        } else {
            worldMat.transformPoint(Vector3.ZERO)
        }

        val camDist = (camera.getEyePosition() - pos).length()
        val scale = if (camera.isOrthographic) camera.orthoSize * 0.16f else camDist * 0.14f
        val shaftLen = 0.45f * scale

        val uColorLoc = prog.getUniformLocation("uColor")

        // Center Handle (Origin Anchor where all 3 axes meet)
        val centerMat = Matrix4.identity().translate(pos).scale(scale * 0.45f, scale * 0.45f, scale * 0.45f)
        setMatrixUniforms(prog, centerMat)
        GLES30.glUniform4f(uColorLoc, 1.0f, 0.90f, 0.22f, 1.0f)
        box.renderTriangles()

        // 1. X Axis (Vibrant Crimson Red) — Strictly +X from origin
        val isXActive = gizmo.activeAxis == GizmoAxis.X
        GLES30.glUniform4f(uColorLoc, 1.0f, if (isXActive) 0.88f else 0.20f, if (isXActive) 0.35f else 0.25f, 1.0f)

        // Shaft starts at pos and extends to pos.x + shaftLen
        val xShaftMat = Matrix4.identity()
            .translate(pos.x, pos.y, pos.z)
            .rotate(-90f, 0f, 0f, 1f)
            .scale(scale, scale, scale)
        setMatrixUniforms(prog, xShaftMat)
        shaft.renderTriangles()

        // Head is at pos.x + shaftLen pointing in +X
        val xHeadMat = Matrix4.identity()
            .translate(pos.x + shaftLen, pos.y, pos.z)
            .rotate(-90f, 0f, 0f, 1f)
            .scale(scale, scale, scale)
        setMatrixUniforms(prog, xHeadMat)
        if (gizmo.mode == TransformMode.SCALE) box.renderTriangles() else cone.renderTriangles()

        // 2. Y Axis (Radiant Electric Green) — Strictly +Y from origin
        val isYActive = gizmo.activeAxis == GizmoAxis.Y
        GLES30.glUniform4f(uColorLoc, if (isYActive) 0.65f else 0.18f, if (isYActive) 1.0f else 0.92f, if (isYActive) 0.65f else 0.38f, 1.0f)

        // Shaft starts at pos and extends to pos.y + shaftLen
        val yShaftMat = Matrix4.identity()
            .translate(pos.x, pos.y, pos.z)
            .scale(scale, scale, scale)
        setMatrixUniforms(prog, yShaftMat)
        shaft.renderTriangles()

        // Head is at pos.y + shaftLen pointing in +Y
        val yHeadMat = Matrix4.identity()
            .translate(pos.x, pos.y + shaftLen, pos.z)
            .scale(scale, scale, scale)
        setMatrixUniforms(prog, yHeadMat)
        if (gizmo.mode == TransformMode.SCALE) box.renderTriangles() else cone.renderTriangles()

        // 3. Z Axis (Vibrant Electric Azure Blue) — Strictly +Z from origin
        val isZActive = gizmo.activeAxis == GizmoAxis.Z
        GLES30.glUniform4f(uColorLoc, if (isZActive) 0.60f else 0.22f, if (isZActive) 0.88f else 0.62f, 1.0f, 1.0f)

        // Shaft starts at pos and extends to pos.z + shaftLen
        val zShaftMat = Matrix4.identity()
            .translate(pos.x, pos.y, pos.z)
            .rotate(90f, 1f, 0f, 0f)
            .scale(scale, scale, scale)
        setMatrixUniforms(prog, zShaftMat)
        shaft.renderTriangles()

        // Head is at pos.z + shaftLen pointing in +Z
        val zHeadMat = Matrix4.identity()
            .translate(pos.x, pos.y, pos.z + shaftLen)
            .rotate(90f, 1f, 0f, 0f)
            .scale(scale, scale, scale)
        setMatrixUniforms(prog, zHeadMat)
        if (gizmo.mode == TransformMode.SCALE) box.renderTriangles() else cone.renderTriangles()
    }

    private fun setMatrixUniforms(prog: ShaderProgram, modelMatrix: Matrix4) {
        val uModel = prog.getUniformLocation("uModelMatrix")
        val uView = prog.getUniformLocation("uViewMatrix")
        val uProj = prog.getUniformLocation("uProjectionMatrix")
        val uNorm = prog.getUniformLocation("uNormalMatrix")

        GLES30.glUniformMatrix4fv(uModel, 1, false, modelMatrix.values, 0)
        GLES30.glUniformMatrix4fv(uView, 1, false, camera.viewMatrix.values, 0)
        GLES30.glUniformMatrix4fv(uProj, 1, false, camera.projectionMatrix.values, 0)

        // Normal matrix = (modelMatrix)^-1^T
        val normMat = Matrix4()
        modelMatrix.invert(normMat)
        GLES30.glUniformMatrix4fv(uNorm, 1, true, normMat.values, 0) // transpose = true
    }

    private fun setCameraPosUniform(prog: ShaderProgram) {
        val eye = camera.getEyePosition()
        val loc = prog.getUniformLocation("uCameraPos")
        if (loc >= 0) {
            GLES30.glUniform3f(loc, eye.x, eye.y, eye.z)
        }
    }
}
