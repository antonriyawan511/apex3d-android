package com.example.studio3d.feature.editor.tools.material

import android.content.Context
import android.net.Uri
import com.example.studio3d.core.history.ChangeMaterialCommand
import com.example.studio3d.core.history.CommandHistory
import com.example.studio3d.core.scene.Material
import com.example.studio3d.core.scene.SceneGraph
import com.example.studio3d.core.scene.SceneNode

/**
 * Controller mandiri untuk mengelola material dan tekstur pada objek SceneNode.
 */
class MaterialController {

    fun updateSelectedMaterial(
        node: SceneNode,
        sceneGraph: SceneGraph,
        history: CommandHistory,
        newMat: Material,
        onSceneChanged: () -> Unit
    ) {
        history.execute(
            ChangeMaterialCommand(
                sceneGraph = sceneGraph,
                nodeId = node.id,
                oldMaterial = node.material.copy(),
                newMaterial = newMat
            )
        )
        onSceneChanged()
    }

    fun applyTextureToSelected(
        context: Context,
        uri: Uri,
        node: SceneNode,
        sceneGraph: SceneGraph,
        history: CommandHistory,
        onStatus: (String) -> Unit,
        onSceneChanged: () -> Unit
    ) {
        var fileName = uri.lastPathSegment ?: "Texture"
        try {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) {
                        fileName = cursor.getString(idx)
                    }
                }
            }
        } catch (_: Exception) {}

        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {}

        val newMat = node.material.copy().apply {
            colorR = 1.0f
            colorG = 1.0f
            colorB = 1.0f
            textureUri = uri.toString()
            textureName = fileName
            textureId = 0
        }
        updateSelectedMaterial(node, sceneGraph, history, newMat, onSceneChanged)
        onStatus("Tekstur dipasang: $fileName")
    }

    fun removeTextureFromSelected(
        node: SceneNode,
        sceneGraph: SceneGraph,
        history: CommandHistory,
        onStatus: (String) -> Unit,
        onSceneChanged: () -> Unit
    ) {
        val newMat = node.material.copy().apply {
            textureUri = null
            textureName = null
            textureId = 0
        }
        updateSelectedMaterial(node, sceneGraph, history, newMat, onSceneChanged)
        onStatus("Tekstur dilepas")
    }
}
