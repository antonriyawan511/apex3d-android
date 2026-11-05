package com.example.studio3d.core.project

import com.example.studio3d.core.math.*
import com.example.studio3d.core.engine.*
import com.example.studio3d.core.scene.*
import com.example.studio3d.core.history.*
import com.example.studio3d.core.preferences.*
import com.example.studio3d.core.project.*
import com.example.studio3d.feature.editor.viewport.*
import com.example.studio3d.feature.editor.tools.select.*


import android.content.Context
import com.example.studio3d.core.engine.Camera
import com.example.studio3d.core.engine.ShadingMode
import com.example.studio3d.core.math.Vector3
import com.example.studio3d.core.scene.Material
import com.example.studio3d.core.scene.SceneGraph
import com.example.studio3d.core.scene.SceneNode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object ProjectManager {
    private const val AUTOSAVE_FILE = "autosave.studio3d"
    private const val PROJECTS_DIR = "projects"

    fun getProjectsDir(context: Context): File {
        val dir = File(context.filesDir, PROJECTS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun serializeScene(
        projectName: String,
        graph: SceneGraph,
        camera: Camera,
        shadingMode: ShadingMode
    ): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("projectName", projectName)
        root.put("timestamp", System.currentTimeMillis())
        root.put("shadingMode", shadingMode.name)

        // Camera
        val camObj = JSONObject()
        camObj.put("distance", camera.distance.toDouble())
        camObj.put("azimuth", camera.azimuthDeg.toDouble())
        camObj.put("elevation", camera.elevationDeg.toDouble())
        camObj.put("isOrtho", camera.isOrthographic)
        camObj.put("targetX", camera.target.x.toDouble())
        camObj.put("targetY", camera.target.y.toDouble())
        camObj.put("targetZ", camera.target.z.toDouble())
        root.put("camera", camObj)

        // Nodes
        val nodesArr = JSONArray()
        for (node in graph.nodes.values) {
            val nObj = JSONObject()
            nObj.put("id", node.id)
            nObj.put("name", node.name)
            nObj.put("meshType", node.meshType)
            nObj.put("isVisible", node.isVisible)
            nObj.put("isLocked", node.isLocked)
            nObj.put("parentId", node.parentId ?: JSONObject.NULL)

            // Children
            val cArr = JSONArray()
            for (cId in node.childrenIds) cArr.put(cId)
            nObj.put("childrenIds", cArr)

            // Transform
            nObj.put("posX", node.position.x.toDouble())
            nObj.put("posY", node.position.y.toDouble())
            nObj.put("posZ", node.position.z.toDouble())

            nObj.put("rotX", node.rotation.x.toDouble())
            nObj.put("rotY", node.rotation.y.toDouble())
            nObj.put("rotZ", node.rotation.z.toDouble())

            nObj.put("scaleX", node.scale.x.toDouble())
            nObj.put("scaleY", node.scale.y.toDouble())
            nObj.put("scaleZ", node.scale.z.toDouble())

            // Material
            val matObj = JSONObject()
            matObj.put("r", node.material.colorR.toDouble())
            matObj.put("g", node.material.colorG.toDouble())
            matObj.put("b", node.material.colorB.toDouble())
            matObj.put("a", node.material.colorA.toDouble())
            matObj.put("metallic", node.material.metallic.toDouble())
            matObj.put("roughness", node.material.roughness.toDouble())
            matObj.put("clearcoat", node.material.clearcoat.toDouble())
            matObj.put("wireframe", node.material.showWireframe)
            if (node.material.textureUri != null) {
                matObj.put("textureUri", node.material.textureUri)
                matObj.put("textureName", node.material.textureName)
            }
            nObj.put("material", matObj)

            nodesArr.put(nObj)
        }
        root.put("nodes", nodesArr)

        return root.toString(2)
    }

    fun deserializeScene(
        jsonString: String,
        graph: SceneGraph,
        camera: Camera
    ): Pair<String, ShadingMode> {
        val root = JSONObject(jsonString)
        val projectName = root.optString("projectName", "Untitled Project")
        val shadingName = root.optString("shadingMode", ShadingMode.MATERIAL.name)
        val shadingMode = try {
            ShadingMode.valueOf(shadingName)
        } catch (_: Exception) {
            ShadingMode.MATERIAL
        }

        // Restore Camera
        if (root.has("camera")) {
            val camObj = root.getJSONObject("camera")
            camera.distance = camObj.optDouble("distance", 6.5).toFloat()
            camera.azimuthDeg = camObj.optDouble("azimuth", 35.0).toFloat()
            camera.elevationDeg = camObj.optDouble("elevation", 25.0).toFloat()
            camera.isOrthographic = camObj.optBoolean("isOrtho", false)
            camera.target = Vector3(
                camObj.optDouble("targetX", 0.0).toFloat(),
                camObj.optDouble("targetY", 0.5).toFloat(),
                camObj.optDouble("targetZ", 0.0).toFloat()
            )
            camera.updateMatrices()
        }

        // Restore Nodes
        graph.clear()
        val nodesArr = root.getJSONArray("nodes")
        for (i in 0 until nodesArr.length()) {
            val nObj = nodesArr.getJSONObject(i)
            val id = nObj.getString("id")
            val name = nObj.optString("name", "Object")
            val meshType = nObj.optString("meshType", "cube")
            val isVis = nObj.optBoolean("isVisible", true)
            val isLock = nObj.optBoolean("isLocked", false)
            val parentId = if (nObj.isNull("parentId")) null else nObj.getString("parentId")

            val pos = Vector3(
                nObj.optDouble("posX", 0.0).toFloat(),
                nObj.optDouble("posY", 0.0).toFloat(),
                nObj.optDouble("posZ", 0.0).toFloat()
            )
            val rot = Vector3(
                nObj.optDouble("rotX", 0.0).toFloat(),
                nObj.optDouble("rotY", 0.0).toFloat(),
                nObj.optDouble("rotZ", 0.0).toFloat()
            )
            val scale = Vector3(
                nObj.optDouble("scaleX", 1.0).toFloat(),
                nObj.optDouble("scaleY", 1.0).toFloat(),
                nObj.optDouble("scaleZ", 1.0).toFloat()
            )

            var mat = Material()
            if (nObj.has("material")) {
                val mObj = nObj.getJSONObject("material")
                mat = Material(
                    colorR = mObj.optDouble("r", 0.8).toFloat(),
                    colorG = mObj.optDouble("g", 0.2).toFloat(),
                    colorB = mObj.optDouble("b", 0.2).toFloat(),
                    colorA = mObj.optDouble("a", 1.0).toFloat(),
                    metallic = mObj.optDouble("metallic", 0.5).toFloat(),
                    roughness = mObj.optDouble("roughness", 0.3).toFloat(),
                    clearcoat = mObj.optDouble("clearcoat", 0.8).toFloat(),
                    showWireframe = mObj.optBoolean("wireframe", false),
                    textureUri = if (mObj.has("textureUri")) mObj.getString("textureUri") else null,
                    textureName = if (mObj.has("textureName")) mObj.getString("textureName") else null
                )
            }

            val node = SceneNode(
                id = id,
                name = name,
                position = pos,
                rotation = rot,
                scale = scale,
                meshType = meshType,
                material = mat,
                isVisible = isVis,
                isLocked = isLock,
                parentId = parentId
            )
            node.ensureMesh()
            graph.nodes[id] = node
        }

        // Restore children lists
        for (i in 0 until nodesArr.length()) {
            val nObj = nodesArr.getJSONObject(i)
            val id = nObj.getString("id")
            val node = graph.nodes[id] ?: continue
            val cArr = nObj.optJSONArray("childrenIds") ?: continue
            node.childrenIds.clear()
            for (j in 0 until cArr.length()) {
                node.childrenIds.add(cArr.getString(j))
            }
        }

        if (graph.nodes.isNotEmpty()) {
            graph.selectedNodeId = graph.nodes.keys.first()
        }

        return Pair(projectName, shadingMode)
    }

    fun saveProject(
        context: Context,
        fileName: String,
        projectName: String,
        graph: SceneGraph,
        camera: Camera,
        shadingMode: ShadingMode
    ): File {
        val safeName = if (fileName.endsWith(".studio3d")) fileName else "$fileName.studio3d"
        val file = File(getProjectsDir(context), safeName)
        val json = serializeScene(projectName, graph, camera, shadingMode)
        file.writeText(json)
        return file
    }

    fun openProject(
        context: Context,
        file: File,
        graph: SceneGraph,
        camera: Camera
    ): Pair<String, ShadingMode> {
        val json = file.readText()
        return deserializeScene(json, graph, camera)
    }

    fun saveAutosave(
        context: Context,
        projectName: String,
        graph: SceneGraph,
        camera: Camera,
        shadingMode: ShadingMode
    ) {
        val file = File(context.filesDir, AUTOSAVE_FILE)
        val json = serializeScene(projectName, graph, camera, shadingMode)
        file.writeText(json)
    }

    fun hasAutosave(context: Context): Boolean {
        val file = File(context.filesDir, AUTOSAVE_FILE)
        return file.exists() && file.length() > 0
    }

    fun restoreAutosave(
        context: Context,
        graph: SceneGraph,
        camera: Camera
    ): Pair<String, ShadingMode>? {
        val file = File(context.filesDir, AUTOSAVE_FILE)
        if (!file.exists()) return null
        return try {
            val json = file.readText()
            deserializeScene(json, graph, camera)
        } catch (_: Exception) {
            null
        }
    }

    fun listProjects(context: Context): List<File> {
        val dir = getProjectsDir(context)
        return dir.listFiles { _, name -> name.endsWith(".studio3d") }?.toList() ?: emptyList()
    }
}
