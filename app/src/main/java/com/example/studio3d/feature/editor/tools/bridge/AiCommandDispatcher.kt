package com.example.studio3d.feature.editor.tools.bridge

import com.example.studio3d.core.engine.CameraPreset
import com.example.studio3d.core.engine.ShadingMode
import com.example.studio3d.core.math.Vector3
import com.example.studio3d.feature.editor.screen.ModelingMode
import com.example.studio3d.feature.editor.screen.SelectionMode
import com.example.studio3d.feature.editor.screen.StudioViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Dispatcher untuk menerjemahkan perintah JSON dari AI Agent / PC menjadi aksi nyata
 * pada StudioViewModel dan SceneGraph secara thread-safe di Main Thread.
 */
class AiCommandDispatcher(private val viewModel: StudioViewModel) {

    suspend fun getStatus(): String = withContext(Dispatchers.Main) {
        val root = JSONObject()
        val selNode = viewModel.sceneGraph.getSelectedNode()

        root.put("status", "ok")
        root.put("modeling_mode", viewModel.modelingMode.value.name)
        root.put("selection_mode", viewModel.selectionMode.value.name)
        root.put("transform_mode", viewModel.transformMode.value.name)
        root.put("has_selection", viewModel.hasEditSelection.value)
        root.put("selected_faces_count", viewModel.selectedFacesCount.value)
        root.put("selected_edges_count", viewModel.selectedEdgesCount.value)
        root.put("selected_vertices_count", viewModel.selectedVerticesCount.value)

        val nodeJson = if (selNode != null) {
            JSONObject().apply {
                put("id", selNode.id)
                put("name", selNode.name)
                put("visible", selNode.isVisible)
                put("locked", selNode.isLocked)
                put("vertex_count", selNode.mesh?.vertices?.let { it.size / 3 } ?: 0)
                put("face_count", selNode.mesh?.indices?.let { it.size / 3 } ?: 0)
            }
        } else null
        root.put("selected_node", nodeJson)

        val historyJson = JSONObject().apply {
            put("can_undo", viewModel.canUndo.value)
            put("can_redo", viewModel.canRedo.value)
        }
        root.put("history", historyJson)

        root.toString(2)
    }

    suspend fun getScene(): String = withContext(Dispatchers.Main) {
        val root = JSONObject()
        root.put("status", "ok")

        val nodesArray = JSONArray()
        for (node in viewModel.sceneGraph.nodes.values) {
            val nJson = JSONObject()
            nJson.put("id", node.id)
            nJson.put("name", node.name)
            nJson.put("parent_id", node.parentId)
            nJson.put("is_selected", node.id == viewModel.sceneGraph.selectedNodeId)
            nJson.put("is_visible", node.isVisible)
            nJson.put("is_locked", node.isLocked)

            // Local Transform
            val posJson = JSONObject().apply {
                put("x", node.position.x)
                put("y", node.position.y)
                put("z", node.position.z)
            }
            val rotJson = JSONObject().apply {
                put("x", node.rotation.x)
                put("y", node.rotation.y)
                put("z", node.rotation.z)
            }
            val scaleJson = JSONObject().apply {
                put("x", node.scale.x)
                put("y", node.scale.y)
                put("z", node.scale.z)
            }
            nJson.put("position", posJson)
            nJson.put("rotation", rotJson)
            nJson.put("scale", scaleJson)

            // Material
            val mat = node.material
            val matJson = JSONObject().apply {
                val c = listOf(mat.colorR, mat.colorG, mat.colorB, mat.colorA)
                put("color", JSONArray(c))
                put("metallic", mat.metallic)
                put("roughness", mat.roughness)
                put("clearcoat", mat.clearcoat)
            }
            nJson.put("material", matJson)

            // Geometry specs
            val mesh = node.mesh
            if (mesh != null) {
                nJson.put("vertex_count", mesh.vertices.size / 3)
                nJson.put("face_count", mesh.indices.size / 3)
            }
            nodesArray.put(nJson)
        }
        root.put("nodes", nodesArray)
        root.toString(2)
    }

    suspend fun getSelection(): String = withContext(Dispatchers.Main) {
        val root = JSONObject()
        root.put("status", "ok")
        root.put("selected_node_id", viewModel.sceneGraph.selectedNodeId)
        root.put("modeling_mode", viewModel.modelingMode.value.name)
        root.put("selection_mode", viewModel.selectionMode.value.name)

        val editMgr = viewModel.editSelection
        root.put("selected_faces", JSONArray(editMgr.getFacesSnapshot()))
        root.put("selected_edges", JSONArray(editMgr.getEdgesSnapshot()))
        root.put("selected_vertices", JSONArray(editMgr.getVerticesSnapshot()))

        root.toString(2)
    }

    suspend fun getCamera(): String = withContext(Dispatchers.Main) {
        val root = JSONObject()
        val cam = viewModel.camera
        root.put("status", "ok")

        val eye = cam.getEyePosition()
        val target = cam.target
        root.put("eye", JSONObject().apply { put("x", eye.x); put("y", eye.y); put("z", eye.z) })
        root.put("target", JSONObject().apply { put("x", target.x); put("y", target.y); put("z", target.z) })
        root.put("distance", cam.distance)
        root.put("azimuth_deg", cam.azimuthDeg)
        root.put("elevation_deg", cam.elevationDeg)
        root.put("is_orthographic", cam.isOrthographic)
        root.put("ortho_size", cam.orthoSize)

        root.toString(2)
    }

    suspend fun executeCommand(jsonString: String): String {
        return try {
            val json = JSONObject(jsonString)
            val action = json.optString("action")
            if (action.isEmpty()) {
                return errorResponse("Missing 'action' field in request body")
            }
            executeSingleAction(action, json)
        } catch (e: Exception) {
            errorResponse("Failed to parse or execute JSON command: ${e.message}")
        }
    }

    suspend fun executePipeline(jsonString: String): String = withContext(Dispatchers.Main) {
        return@withContext try {
            val json = JSONObject(jsonString)
            val pipeline = json.optJSONArray("pipeline")
                ?: return@withContext errorResponse("Missing 'pipeline' array in request body")

            val results = JSONArray()
            var allSuccess = true

            for (i in 0 until pipeline.length()) {
                val step = pipeline.getJSONObject(i)
                val action = step.optString("action")
                val stepResultStr = executeActionInternal(action, step)
                val stepResultJson = JSONObject(stepResultStr)
                results.put(stepResultJson)
                if (stepResultJson.optString("status") != "success") {
                    allSuccess = false
                    break
                }
            }
            viewModel.refreshSceneState()

            JSONObject().apply {
                put("status", if (allSuccess) "success" else "partial_failure")
                put("executed_steps", results.length())
                put("results", results)
            }.toString(2)
        } catch (e: Exception) {
            errorResponse("Pipeline execution error: ${e.message}")
        }
    }

    private suspend fun executeSingleAction(action: String, params: JSONObject): String = withContext(Dispatchers.Main) {
        executeActionInternal(action, params)
    }

    private fun executeActionInternal(action: String, params: JSONObject): String {
        val res = JSONObject()
        res.put("action", action)

        when (action.lowercase()) {
            // 1. Primitive Creation
            "add_primitive", "add_mesh" -> {
                val type = params.optString("type", "cube").lowercase()
                viewModel.addPrimitive(type)
                res.put("status", "success")
                res.put("message", "Created primitive of type: $type")
            }

            // 2. Selection & Mode Control
            "set_mode", "set_modeling_mode" -> {
                val modeStr = params.optString("mode", "OBJECT").uppercase()
                when (modeStr) {
                    "EDIT" -> viewModel.setModelingMode(ModelingMode.EDIT)
                    else -> viewModel.setModelingMode(ModelingMode.OBJECT)
                }
                res.put("status", "success")
                res.put("mode", viewModel.modelingMode.value.name)
            }

            "set_selection_mode" -> {
                val selStr = params.optString("mode", "FACE").uppercase()
                when (selStr) {
                    "VERTEX" -> viewModel.setSelectionMode(SelectionMode.VERTEX)
                    "EDGE" -> viewModel.setSelectionMode(SelectionMode.EDGE)
                    else -> viewModel.setSelectionMode(SelectionMode.FACE)
                }
                res.put("status", "success")
                res.put("selection_mode", viewModel.selectionMode.value.name)
            }

            "select_node" -> {
                val id = params.optString("id")
                viewModel.selectNode(id)
                res.put("status", "success")
                res.put("selected_node_id", viewModel.sceneGraph.selectedNodeId)
            }

            "select_elements" -> {
                val indices = params.optJSONArray("indices")
                val mode = viewModel.selectionMode.value
                val editMgr = viewModel.editSelection

                if (indices != null) {
                    val list = mutableListOf<Int>()
                    for (i in 0 until indices.length()) list.add(indices.getInt(i))
                    when (mode) {
                        SelectionMode.FACE -> editMgr.setFaces(list)
                        SelectionMode.EDGE -> editMgr.setEdges(list)
                        SelectionMode.VERTEX -> editMgr.setVertices(list)
                    }
                } else if (params.optBoolean("all", false)) {
                    val node = viewModel.sceneGraph.getSelectedNode()
                    val mesh = node?.mesh
                    if (mesh != null) {
                        val top = mesh.topology
                        when (mode) {
                            SelectionMode.FACE -> editMgr.setFaces((0 until top.faceCount).toList())
                            SelectionMode.EDGE -> editMgr.setEdges((0 until top.edges.size).toList())
                            SelectionMode.VERTEX -> editMgr.setVertices((0 until top.vertexCount).toList())
                        }
                    }
                }
                viewModel.notifyEditSelectionChanged()
                viewModel.refreshSceneState()
                res.put("status", "success")
                res.put("selected_faces", editMgr.getFacesSnapshot().size)
                res.put("selected_edges", editMgr.getEdgesSnapshot().size)
                res.put("selected_vertices", editMgr.getVerticesSnapshot().size)
            }

            "clear_selection", "deselect_all" -> {
                viewModel.clearEditSelection()
                res.put("status", "success")
            }

            // 3. Modeling Operations (Direct Execution)
            "extrude" -> {
                val dist = params.optDouble("distance", 0.4).toFloat()
                if (viewModel.extrudeState.value.isActive) {
                    viewModel.updateExtrudeDistance(dist)
                    viewModel.confirmExtrude()
                } else {
                    viewModel.extrudeSelected(dist)
                    // If interactive session started, immediately set distance & confirm for AI
                    if (viewModel.extrudeState.value.isActive) {
                        viewModel.updateExtrudeDistance(dist)
                        viewModel.confirmExtrude()
                    }
                }
                res.put("status", "success")
                res.put("distance", dist)
            }

            "inset" -> {
                val ratio = params.optDouble("ratio", 0.20).toFloat()
                val even = params.optBoolean("even", true)
                viewModel.insetSelectedFaces(ratio)
                if (viewModel.insetState.value.isActive) {
                    if (viewModel.insetState.value.evenThickness != even) {
                        viewModel.toggleInsetMode()
                    }
                    viewModel.updateInsetRatio(ratio)
                    viewModel.confirmInset()
                }
                res.put("status", "success")
                res.put("ratio", ratio)
                res.put("even", even)
            }

            "subdivide" -> {
                val cuts = params.optInt("cuts", 1).coerceIn(1, 8)
                viewModel.subdivideMeshOrSelectedFaces()
                if (viewModel.subdivideState.value.isActive) {
                    viewModel.updateSubdivideCuts(cuts)
                    viewModel.confirmSubdivide()
                }
                res.put("status", "success")
                res.put("cuts", cuts)
            }

            "bevel" -> {
                val offset = params.optDouble("offset", 0.12).toFloat()
                val depth = params.optDouble("depth", 0.0).toFloat()
                val segments = params.optInt("segments", 1).coerceIn(1, 8)
                viewModel.bevelDirect(offset = offset, depth = depth, segments = segments)
                res.put("status", "success")
                res.put("offset", offset)
                res.put("depth", depth)
                res.put("segments", segments)
            }

            "loop_cut" -> {
                val factor = params.optDouble("factor", 0.5).toFloat().coerceIn(0.01f, 0.99f)
                viewModel.loopCutSelected(factor = factor)
                res.put("status", "success")
                res.put("factor", factor)
            }

            "bridge" -> {
                viewModel.bridgeSelected()
                res.put("status", "success")
            }

            "delete", "delete_selected" -> {
                if (viewModel.modelingMode.value == ModelingMode.EDIT) {
                    viewModel.deleteSelectedGeometry()
                } else {
                    viewModel.deleteSelected()
                }
                res.put("status", "success")
            }

            "merge_vertices" -> {
                viewModel.mergeSelectedVertices()
                res.put("status", "success")
            }

            // 4. Object Transformations
            "transform", "set_transform" -> {
                val node = viewModel.sceneGraph.getSelectedNode()
                    ?: return errorResponse("No node selected for transform")

                if (params.has("position")) {
                    val p = params.getJSONObject("position")
                    node.position = Vector3(p.optDouble("x", 0.0).toFloat(), p.optDouble("y", 0.0).toFloat(), p.optDouble("z", 0.0).toFloat())
                }
                if (params.has("rotation")) {
                    val r = params.getJSONObject("rotation")
                    node.rotation = Vector3(r.optDouble("x", 0.0).toFloat(), r.optDouble("y", 0.0).toFloat(), r.optDouble("z", 0.0).toFloat())
                }
                if (params.has("scale")) {
                    val s = params.getJSONObject("scale")
                    node.scale = Vector3(s.optDouble("x", 1.0).toFloat(), s.optDouble("y", 1.0).toFloat(), s.optDouble("z", 1.0).toFloat())
                }
                viewModel.refreshSceneState()
                res.put("status", "success")
            }

            "translate" -> {
                val node = viewModel.sceneGraph.getSelectedNode() ?: return errorResponse("No node selected")
                val dx = params.optDouble("x", 0.0).toFloat()
                val dy = params.optDouble("y", 0.0).toFloat()
                val dz = params.optDouble("z", 0.0).toFloat()
                node.position = node.position + Vector3(dx, dy, dz)
                viewModel.refreshSceneState()
                res.put("status", "success")
            }

            // 5. Visuals, Materials & Camera
            "set_material", "set_color" -> {
                val node = viewModel.sceneGraph.getSelectedNode() ?: return errorResponse("No node selected")
                if (params.has("color")) {
                    val cArr = params.optJSONArray("color")
                    if (cArr != null && cArr.length() >= 3) {
                        node.material.colorR = cArr.getDouble(0).toFloat()
                        node.material.colorG = cArr.getDouble(1).toFloat()
                        node.material.colorB = cArr.getDouble(2).toFloat()
                        if (cArr.length() >= 4) {
                            node.material.colorA = cArr.getDouble(3).toFloat()
                        }
                    }
                }
                if (params.has("roughness")) {
                    node.material.roughness = params.getDouble("roughness").toFloat()
                }
                if (params.has("metallic")) {
                    node.material.metallic = params.getDouble("metallic").toFloat()
                }
                viewModel.refreshSceneState()
                res.put("status", "success")
            }

            "set_shading" -> {
                val mode = params.optString("shading", "SOLID").uppercase()
                when (mode) {
                    "WIRE", "WIREFRAME" -> viewModel.setShadingMode(ShadingMode.WIREFRAME)
                    "MATERIAL" -> viewModel.setShadingMode(ShadingMode.MATERIAL)
                    "RENDER", "STUDIO_RENDER" -> viewModel.setShadingMode(ShadingMode.STUDIO_RENDER)
                    else -> viewModel.setShadingMode(ShadingMode.CLAY)
                }
                res.put("status", "success")
                res.put("shading_mode", viewModel.shadingMode.value.name)
            }

            "set_camera" -> {
                if (params.has("azimuth")) viewModel.camera.azimuthDeg = params.getDouble("azimuth").toFloat()
                if (params.has("elevation")) viewModel.camera.elevationDeg = params.getDouble("elevation").toFloat()
                if (params.has("distance")) viewModel.camera.distance = params.getDouble("distance").toFloat()
                if (params.has("preset")) {
                    val presetStr = params.optString("preset", "PERSPECTIVE").uppercase()
                    val preset = try { CameraPreset.valueOf(presetStr) } catch (_: Exception) { CameraPreset.PERSPECTIVE }
                    viewModel.setCameraPreset(preset)
                }
                viewModel.refreshSceneState()
                res.put("status", "success")
            }

            "set_camera_preset" -> {
                val presetStr = params.optString("preset", "PERSPECTIVE").uppercase()
                val preset = try { CameraPreset.valueOf(presetStr) } catch (_: Exception) { CameraPreset.PERSPECTIVE }
                viewModel.setCameraPreset(preset)
                res.put("status", "success")
                res.put("camera_preset", preset.name)
            }

            // 6. History
            "undo" -> {
                viewModel.undo()
                res.put("status", "success")
                res.put("can_undo", viewModel.canUndo.value)
            }

            "redo" -> {
                viewModel.redo()
                res.put("status", "success")
                res.put("can_redo", viewModel.canRedo.value)
            }

            "clear_scene" -> {
                viewModel.sceneGraph.clear()
                viewModel.selectNode(null)
                viewModel.refreshSceneState()
                res.put("status", "success")
            }

            "new_project" -> {
                viewModel.newProject()
                res.put("status", "success")
            }

            else -> {
                return errorResponse("Unknown action: '$action'. Send GET /api/schema for available actions.")
            }
        }

        return res.toString(2)
    }

    fun getSchema(): String {
        val root = JSONObject()
        root.put("service", "Apex 3D Wireless AI Bridge")
        root.put("version", "1.0.0")

        val endpoints = JSONObject().apply {
            put("GET /api/status", "Get general active state, modes, element counts, and history")
            put("GET /api/scene", "Get complete hierarchy of nodes, transforms, materials, and geometry specs")
            put("GET /api/camera", "Get camera position, azimuth, elevation, target, distance, and projection")
            put("GET /api/selection", "Get active node and lists of selected face, edge, and vertex indices")
            put("POST /api/command", "Execute a single action JSON { 'action': '<name>', ... }")
            put("POST /api/pipeline", "Execute a batch macro pipeline { 'pipeline': [ { 'action': ... }, ... ] }")
        }
        root.put("endpoints", endpoints)

        val actions = JSONObject().apply {
            put("add_primitive", "type: 'cube' | 'sphere' | 'cylinder' | 'plane' | 'torus'")
            put("set_mode", "mode: 'OBJECT' | 'EDIT'")
            put("set_selection_mode", "mode: 'VERTEX' | 'EDGE' | 'FACE'")
            put("select_elements", "indices: [int, ...], or all: true")
            put("clear_selection", "Deselects all components")
            put("extrude", "distance: float (meters, e.g. 0.4 or -0.3)")
            put("inset", "ratio: float (0.02 - 0.45), even: boolean (default true)")
            put("subdivide", "cuts: int (1 - 8)")
            put("transform", "position: {x,y,z}, rotation: {x,y,z}, scale: {x,y,z}")
            put("translate", "x: float, y: float, z: float")
            put("set_material", "color: [r,g,b,a] (0.0-1.0), roughness: float, metallic: float")
            put("set_shading", "shading: 'WIREFRAME' | 'CLAY' | 'MATERIAL' | 'STUDIO_RENDER'")
            put("set_camera", "azimuth: float, elevation: float, distance: float")
            put("set_camera_preset", "preset: 'TOP' | 'FRONT' | 'RIGHT' | 'BACK' | 'BOTTOM' | 'LEFT' | 'PERSPECTIVE'")
            put("delete", "Delete selected node or selected mesh components")
            put("merge_vertices", "Merge coincident vertices")
            put("undo", "Undo last command")
            put("redo", "Redo last undone command")
        }
        root.put("supported_actions", actions)

        return root.toString(2)
    }

    fun getMcpToolsList(): JSONArray {
        val tools = JSONArray()

        fun tool(name: String, desc: String, props: JSONObject, required: List<String> = emptyList()) {
            val t = JSONObject()
            t.put("name", name)
            t.put("description", desc)
            val schema = JSONObject()
            schema.put("type", "object")
            schema.put("properties", props)
            if (required.isNotEmpty()) {
                schema.put("required", JSONArray(required))
            }
            t.put("inputSchema", schema)
            tools.put(t)
        }

        tool("apex3d_get_scene", "Retrieve all objects in the active 3D scene (hierarchy, mesh specs, transforms, materials, selection).", JSONObject())
        tool("apex3d_get_status", "Check 3D editor status, active tool, selection mode, modeling mode, and undo/redo availability.", JSONObject())
        tool("apex3d_add_primitive", "Add a 3D primitive mesh to the scene.", JSONObject().apply {
            put("type", JSONObject().apply {
                put("type", "string")
                put("enum", JSONArray(listOf("cube", "sphere", "cylinder", "plane", "torus", "plate")))
                put("description", "Primitive geometry type")
            })
        }, listOf("type"))
        tool("apex3d_transform", "Transform position, rotation, or scale of selected 3D object.", JSONObject().apply {
            put("position", JSONObject().apply {
                put("type", "object")
                put("description", "Vector3 position {x, y, z}")
            })
            put("rotation", JSONObject().apply {
                put("type", "object")
                put("description", "Euler rotation in degrees {x, y, z}")
            })
            put("scale", JSONObject().apply {
                put("type", "object")
                put("description", "Vector3 scale {x, y, z}")
            })
        })
        tool("apex3d_set_material", "Configure PBR material properties (color, metallic, roughness).", JSONObject().apply {
            put("color", JSONObject().apply {
                put("type", "array")
                put("description", "RGBA array [r, g, b, a] from 0.0 to 1.0")
            })
            put("metallic", JSONObject().apply {
                put("type", "number")
                put("description", "Metallic factor (0.0 to 1.0)")
            })
            put("roughness", JSONObject().apply {
                put("type", "number")
                put("description", "Roughness factor (0.0 to 1.0)")
            })
        })
        tool("apex3d_extrude", "Extrude selected mesh faces along normal vector.", JSONObject().apply {
            put("distance", JSONObject().apply {
                put("type", "number")
                put("description", "Extrusion distance in meters (e.g. 0.4)")
            })
        })
        tool("apex3d_subdivide", "Subdivide selected mesh faces using Quad-Aware bilinear grid.", JSONObject().apply {
            put("cuts", JSONObject().apply {
                put("type", "integer")
                put("description", "Number of subdivision cuts (1 to 8)")
            })
        })
        tool("apex3d_inset", "Inset selected mesh faces.", JSONObject().apply {
            put("ratio", JSONObject().apply {
                put("type", "number")
                put("description", "Inset ratio (0.02 to 0.45)")
            })
            put("even", JSONObject().apply {
                put("type", "boolean")
                put("description", "Preserve even border thickness (default true)")
            })
        })
        tool("apex3d_bevel", "Bevel/chamfer selected mesh faces or edges with fillet segments and depth offset.", JSONObject().apply {
            put("offset", JSONObject().apply {
                put("type", "number")
                put("description", "Chamfer distance/width (default: 0.12)")
            })
            put("depth", JSONObject().apply {
                put("type", "number")
                put("description", "Normal extrusion depth (default: -0.04)")
            })
            put("segments", JSONObject().apply {
                put("type", "integer")
                put("description", "Fillet segments (default: 1)")
            })
        })
        tool("apex3d_loop_cut", "Insert an edge loop across connected quad faces bisecting at factor t.", JSONObject().apply {
            put("factor", JSONObject().apply {
                put("type", "number")
                put("description", "Normalized position along the loop (0.0 to 1.0, default: 0.5)")
            })
        })
        tool("apex3d_bridge", "Bridge two selected disconnected face loops or edge boundaries with connecting quad faces.", JSONObject())
        tool("apex3d_set_camera", "Control 3D viewport camera.", JSONObject().apply {
            put("azimuth", JSONObject().apply { put("type", "number"); put("description", "Azimuth orbit angle (deg)") })
            put("elevation", JSONObject().apply { put("type", "number"); put("description", "Elevation orbit angle (deg)") })
            put("distance", JSONObject().apply { put("type", "number"); put("description", "Distance from target") })
            put("preset", JSONObject().apply { put("type", "string"); put("description", "Camera preset name (e.g. FRONT, TOP, PERSPECTIVE)") })
        })
        tool("apex3d_set_shading", "Set viewport shading mode.", JSONObject().apply {
            put("shading", JSONObject().apply {
                put("type", "string")
                put("enum", JSONArray(listOf("WIREFRAME", "SOLID", "MATERIAL", "STUDIO_RENDER")))
            })
        }, listOf("shading"))
        tool("apex3d_execute_pipeline", "Execute a batch macro pipeline atomically in one call.", JSONObject().apply {
            put("pipeline", JSONObject().apply {
                put("type", "array")
                put("description", "Array of action steps { action, ... }")
            })
        }, listOf("pipeline"))

        return tools
    }

    suspend fun handleMcpRequest(jsonStr: String): String = withContext(Dispatchers.Main) {
        try {
            val req = JSONObject(jsonStr)
            val id = req.opt("id")
            val method = req.optString("method")

            when (method) {
                "initialize" -> {
                    val resp = JSONObject()
                    resp.put("jsonrpc", "2.0")
                    if (id != null) resp.put("id", id)
                    val result = JSONObject()
                    result.put("protocolVersion", "2024-11-05")
                    val cap = JSONObject()
                    cap.put("tools", JSONObject())
                    cap.put("resources", JSONObject())
                    result.put("capabilities", cap)
                    val info = JSONObject()
                    info.put("name", "apex3d-mcp")
                    info.put("version", "1.0.0")
                    result.put("serverInfo", info)
                    resp.put("result", result)
                    resp.toString(2)
                }
                "notifications/initialized" -> {
                    val resp = JSONObject()
                    resp.put("jsonrpc", "2.0")
                    if (id != null) resp.put("id", id)
                    resp.put("result", JSONObject())
                    resp.toString(2)
                }
                "ping" -> {
                    val resp = JSONObject()
                    resp.put("jsonrpc", "2.0")
                    if (id != null) resp.put("id", id)
                    resp.put("result", JSONObject())
                    resp.toString(2)
                }
                "tools/list" -> {
                    val resp = JSONObject()
                    resp.put("jsonrpc", "2.0")
                    if (id != null) resp.put("id", id)
                    val result = JSONObject()
                    result.put("tools", getMcpToolsList())
                    resp.put("result", result)
                    resp.toString(2)
                }
                "tools/call" -> {
                    val params = req.optJSONObject("params") ?: JSONObject()
                    val toolName = params.optString("name")
                    val args = params.optJSONObject("arguments") ?: JSONObject()

                    val actionName = when (toolName) {
                        "apex3d_get_scene" -> "get_scene"
                        "apex3d_get_status" -> "get_status"
                        "apex3d_add_primitive" -> "add_primitive"
                        "apex3d_transform" -> "transform"
                        "apex3d_set_material" -> "set_material"
                        "apex3d_extrude" -> "extrude"
                        "apex3d_subdivide" -> "subdivide"
                        "apex3d_inset" -> "inset"
                        "apex3d_set_camera" -> "set_camera"
                        "apex3d_set_shading" -> "set_shading"
                        "apex3d_execute_pipeline" -> "pipeline"
                        else -> toolName.removePrefix("apex3d_")
                    }

                    val toolResultText = when (actionName) {
                        "get_scene" -> getScene()
                        "get_status" -> getStatus()
                        "pipeline" -> executePipeline(args.toString())
                        else -> executeSingleAction(actionName, args)
                    }

                    val resp = JSONObject()
                    resp.put("jsonrpc", "2.0")
                    if (id != null) resp.put("id", id)
                    val result = JSONObject()
                    val contentArr = JSONArray()
                    val item = JSONObject()
                    item.put("type", "text")
                    item.put("text", toolResultText)
                    contentArr.put(item)
                    result.put("content", contentArr)
                    resp.put("result", result)
                    resp.toString(2)
                }
                "resources/list" -> {
                    val resp = JSONObject()
                    resp.put("jsonrpc", "2.0")
                    if (id != null) resp.put("id", id)
                    val resArr = JSONArray()
                    resArr.put(JSONObject().apply {
                        put("uri", "apex3d://scene")
                        put("name", "Active 3D Scene Graph")
                        put("mimeType", "application/json")
                    })
                    resArr.put(JSONObject().apply {
                        put("uri", "apex3d://status")
                        put("name", "Active 3D Studio Status")
                        put("mimeType", "application/json")
                    })
                    resp.put("result", JSONObject().apply { put("resources", resArr) })
                    resp.toString(2)
                }
                "resources/read" -> {
                    val params = req.optJSONObject("params") ?: JSONObject()
                    val uri = params.optString("uri")
                    val text = when (uri) {
                        "apex3d://scene" -> getScene()
                        else -> getStatus()
                    }
                    val resp = JSONObject()
                    resp.put("jsonrpc", "2.0")
                    if (id != null) resp.put("id", id)
                    val contents = JSONArray()
                    contents.put(JSONObject().apply {
                        put("uri", uri)
                        put("mimeType", "application/json")
                        put("text", text)
                    })
                    resp.put("result", JSONObject().apply { put("contents", contents) })
                    resp.toString(2)
                }
                else -> {
                    val resp = JSONObject()
                    resp.put("jsonrpc", "2.0")
                    if (id != null) resp.put("id", id)
                    val err = JSONObject()
                    err.put("code", -32601)
                    err.put("message", "Method not found: $method")
                    resp.put("error", err)
                    resp.toString(2)
                }
            }
        } catch (e: Exception) {
            val resp = JSONObject()
            resp.put("jsonrpc", "2.0")
            val err = JSONObject()
            err.put("code", -32603)
            err.put("message", "Internal error: ${e.message}")
            resp.put("error", err)
            resp.toString(2)
        }
    }

    private fun errorResponse(msg: String): String {
        return JSONObject().apply {
            put("status", "error")
            put("message", msg)
        }.toString(2)
    }
}
