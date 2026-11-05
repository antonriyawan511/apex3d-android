package com.example.studio3d.feature.editor.tools.project

import android.content.Context
import android.net.Uri
import com.example.studio3d.core.engine.Camera
import com.example.studio3d.core.engine.CameraPreset
import com.example.studio3d.core.engine.GlbImporter
import com.example.studio3d.core.engine.GlbResult
import com.example.studio3d.core.engine.ObjImporter
import com.example.studio3d.core.engine.ShadingMode
import com.example.studio3d.core.project.ProjectManager
import com.example.studio3d.core.scene.Material
import com.example.studio3d.core.scene.SceneGraph
import com.example.studio3d.core.scene.SceneNode
import com.example.studio3d.core.scene.StarterScene
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Controller mandiri untuk operasi file proyek, autosave, serta import format 3D (OBJ/GLB/GLTF).
 */
class ProjectController {
    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _importProgress = MutableStateFlow<String?>(null)
    val importProgress: StateFlow<String?> = _importProgress.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun dismissError() {
        _errorMessage.value = null
    }

    fun startAutosave(
        context: Context,
        scope: CoroutineScope,
        getProjectName: () -> String,
        sceneGraph: SceneGraph,
        camera: Camera,
        getShadingMode: () -> ShadingMode,
        onAutosaveFound: () -> Unit
    ) {
        scope.launch {
            if (ProjectManager.hasAutosave(context)) {
                onAutosaveFound()
            }
            while (true) {
                delay(30_000)
                try {
                    ProjectManager.saveAutosave(
                        context = context,
                        projectName = getProjectName(),
                        graph = sceneGraph,
                        camera = camera,
                        shadingMode = getShadingMode()
                    )
                } catch (_: Exception) {}
            }
        }
    }

    fun saveProject(
        context: Context,
        filename: String,
        projectName: String,
        sceneGraph: SceneGraph,
        camera: Camera,
        shadingMode: ShadingMode,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val file = ProjectManager.saveProject(
                context = context,
                fileName = filename,
                projectName = projectName,
                graph = sceneGraph,
                camera = camera,
                shadingMode = shadingMode
            )
            onSuccess("Proyek disimpan: ${file.name}")
        } catch (e: Exception) {
            onError("Gagal menyimpan: ${e.message}")
        }
    }

    fun openProject(
        context: Context,
        file: File,
        sceneGraph: SceneGraph,
        camera: Camera,
        onSuccess: (String, ShadingMode) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val (name, mode) = ProjectManager.openProject(context, file, sceneGraph, camera)
            onSuccess(name, mode)
        } catch (e: Exception) {
            onError("Gagal memuat: ${e.message}")
        }
    }

    fun newProject(
        sceneGraph: SceneGraph,
        camera: Camera,
        onReset: () -> Unit
    ) {
        sceneGraph.clear()
        StarterScene.populateDefaultScene(sceneGraph)
        camera.setPreset(CameraPreset.PERSPECTIVE)
        onReset()
    }

    fun restoreAutosave(
        context: Context,
        sceneGraph: SceneGraph,
        camera: Camera,
        onSuccess: (String, ShadingMode) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val res = ProjectManager.restoreAutosave(context, sceneGraph, camera)
            if (res != null) {
                onSuccess(res.first, res.second)
            } else {
                onError("Gagal memulihkan autosave")
            }
        } catch (e: Exception) {
            onError("Gagal memulihkan autosave: ${e.message}")
        }
    }

    fun import3DModelFile(
        context: Context,
        scope: CoroutineScope,
        uri: Uri,
        sceneGraph: SceneGraph,
        onNodeCreated: (SceneNode) -> Unit,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            _isImporting.value = true
            _errorMessage.value = null
            try {
                val fileName = getFileNameFromUri(context, uri) ?: "imported_model.obj"
                _importProgress.value = "Membaca $fileName..."
                val isGlb = fileName.endsWith(".glb", ignoreCase = true) || fileName.endsWith(".gltf", ignoreCase = true)

                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    _importProgress.value = "Memproses geometri & menyusun buffer..."
                    val parsedResult = if (isGlb) {
                        GlbImporter.parseGlb(inputStream)
                    } else {
                        GlbResult(mesh = ObjImporter.parseObj(inputStream))
                    }
                    val parsedMesh = parsedResult.mesh

                    var texUri: String? = null
                    var texName: String? = null
                    if (parsedResult.textureBytes != null) {
                        try {
                            val ext = if (parsedResult.textureMime?.contains("png", true) == true) ".png" else ".jpg"
                            val texDir = File(context.filesDir, "textures").apply { mkdirs() }
                            val texFile = File(texDir, "tex_${System.currentTimeMillis()}$ext")
                            texFile.writeBytes(parsedResult.textureBytes)
                            texUri = texFile.absolutePath
                            texName = "Embedded Texture"
                        } catch (_: Exception) {}
                    }

                    inputStream.close()

                    withContext(Dispatchers.Main) {
                        val baseName = fileName.substringBeforeLast(".")
                        val nodeMat = Material.studioClay().apply {
                            if (texUri != null) {
                                colorR = 1.0f
                                colorG = 1.0f
                                colorB = 1.0f
                                textureUri = texUri
                                textureName = texName
                            }
                        }

                        val node = SceneNode(
                            id = UUID.randomUUID().toString(),
                            name = baseName,
                            meshType = "custom",
                            mesh = parsedMesh,
                            material = nodeMat
                        )
                        node.ensureMesh()
                        onNodeCreated(node)
                        _isImporting.value = false
                        _importProgress.value = null
                        onSuccess("Model $fileName berhasil diimpor")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _errorMessage.value = "Tidak dapat membuka file yang dipilih"
                        _isImporting.value = false
                        _importProgress.value = null
                        onError("Gagal membuka file")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val msg = "Gagal mengimpor file: ${e.message ?: "Format file tidak didukung"}"
                    _errorMessage.value = msg
                    _isImporting.value = false
                    _importProgress.value = null
                    onError(msg)
                }
            }
        }
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            result = cursor.getString(nameIndex)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }
}
