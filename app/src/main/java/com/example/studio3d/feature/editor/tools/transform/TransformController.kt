package com.example.studio3d.feature.editor.tools.transform

import com.example.studio3d.core.engine.Mesh
import com.example.studio3d.core.history.CommandHistory
import com.example.studio3d.core.history.TransformNodeCommand
import com.example.studio3d.core.history.TransformVerticesCommand
import com.example.studio3d.core.math.Vector3
import com.example.studio3d.core.preferences.StudioPreferences
import com.example.studio3d.core.scene.SceneGraph
import com.example.studio3d.core.scene.SceneNode
import com.example.studio3d.feature.editor.tools.select.EditSelectionManager
import com.example.studio3d.feature.editor.viewport.Gizmo
import com.example.studio3d.feature.editor.viewport.TransformMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controller mandiri untuk mengelola interaksi Transformasi (Translate, Rotate, Scale, Snapping,
 * dan pergerakan komponen Vertex/Edge/Face).
 */
class TransformController(
    val gizmo: Gizmo,
    private val prefs: StudioPreferences?
) {
    private val _transformMode = MutableStateFlow(prefs?.transformMode ?: TransformMode.TRANSLATE)
    val transformMode: StateFlow<TransformMode> = _transformMode.asStateFlow()

    private val _isSnapping = MutableStateFlow(prefs?.isSnapping ?: true)
    val isSnapping: StateFlow<Boolean> = _isSnapping.asStateFlow()

    // Sesi interaksi transform komponen vertex/edge/face
    private var vertexTransformBasePivot: Vector3? = null
    private var vertexTransformInitialPositions: Map<Int, Vector3>? = null
    private var vertexTransformCurrentPos: Vector3 = Vector3.ZERO
    private var vertexTransformCurrentRot: Vector3 = Vector3.ZERO
    private var vertexTransformCurrentScale: Vector3 = Vector3(1f, 1f, 1f)

    init {
        gizmo.mode = _transformMode.value
        gizmo.isSnappingEnabled = _isSnapping.value
    }

    fun setTransformMode(mode: TransformMode) {
        _transformMode.value = mode
        gizmo.mode = mode
        prefs?.transformMode = mode
    }

    fun toggleSnapping(): Boolean {
        val newVal = !_isSnapping.value
        _isSnapping.value = newVal
        gizmo.isSnappingEnabled = newVal
        prefs?.isSnapping = newVal
        return newVal
    }

    fun setSnapping(enabled: Boolean) {
        _isSnapping.value = enabled
        gizmo.isSnappingEnabled = enabled
        prefs?.isSnapping = enabled
    }

    fun resetVertexTransformSession() {
        vertexTransformBasePivot = null
        vertexTransformInitialPositions = null
        vertexTransformCurrentPos = Vector3.ZERO
        vertexTransformCurrentRot = Vector3.ZERO
        vertexTransformCurrentScale = Vector3(1f, 1f, 1f)
    }

    /**
     * Mengambil pivot efektif dan nilai transform untuk ditampilkan di Gizmo dan Transform Card.
     */
    fun getEffectivePivotAndTransform(
        node: SceneNode,
        editSelection: EditSelectionManager,
        isEditMode: Boolean
    ): Triple<Vector3, Vector3, Vector3> {
        val mesh = node.mesh
        if (!isEditMode || mesh == null || !editSelection.hasSelection()) {
            return Triple(node.position.copy(), node.rotation.copy(), node.scale.copy())
        }

        val effectiveVertices = editSelection.getEffectiveSelectedVertices(mesh)
        if (effectiveVertices.isEmpty()) {
            return Triple(node.position.copy(), node.rotation.copy(), node.scale.copy())
        }

        val localPivot = editSelection.calculateLocalPivot(mesh)
        if (vertexTransformBasePivot == null) {
            vertexTransformBasePivot = localPivot
            vertexTransformCurrentPos = localPivot.copy()
            vertexTransformCurrentRot = Vector3.ZERO
            vertexTransformCurrentScale = Vector3(1f, 1f, 1f)
            vertexTransformInitialPositions = effectiveVertices.associateWith { mesh.getVertex(it) }
        }

        return Triple(
            vertexTransformCurrentPos.copy(),
            vertexTransformCurrentRot.copy(),
            vertexTransformCurrentScale.copy()
        )
    }

    /**
     * Menerapkan transformasi baik pada komponen terpilih di Edit Mode, maupun Object Node.
     */
    fun updateTransform(
        sceneGraph: SceneGraph,
        node: SceneNode,
        history: CommandHistory,
        editSelection: EditSelectionManager,
        isEditMode: Boolean,
        newPos: Vector3,
        newRot: Vector3,
        newScale: Vector3,
        onMeshDirty: () -> Unit,
        onSceneChanged: () -> Unit
    ) {
        // 1. Edit Mode Component Transform
        if (isEditMode && editSelection.hasSelection() && node.mesh != null) {
            val mesh = node.mesh!!
            if (vertexTransformBasePivot == null || vertexTransformInitialPositions == null) {
                vertexTransformBasePivot = editSelection.calculateLocalPivot(mesh)
                vertexTransformCurrentPos = vertexTransformBasePivot!!.copy()
                vertexTransformCurrentRot = Vector3.ZERO
                vertexTransformCurrentScale = Vector3(1f, 1f, 1f)
                val effectiveVertices = editSelection.getEffectiveSelectedVertices(mesh)
                vertexTransformInitialPositions = effectiveVertices.associateWith { mesh.getVertex(it) }
            }

            val basePivot = vertexTransformBasePivot!!
            val initialPositions = vertexTransformInitialPositions!!

            val deltaPos = newPos - basePivot
            val deltaRot = newRot
            val scaleFactor = newScale

            vertexTransformCurrentPos = newPos.copy()
            vertexTransformCurrentRot = newRot.copy()
            vertexTransformCurrentScale = newScale.copy()

            editSelection.applyTransform(
                mesh = mesh,
                initialPositions = initialPositions,
                localPivot = basePivot,
                deltaTranslation = deltaPos,
                deltaRotationEuler = deltaRot,
                scaleFactor = scaleFactor
            )

            mesh.recalculateNormalsAndBounds()
            onMeshDirty()
            return
        }

        // 2. Object Node Transform
        history.execute(
            TransformNodeCommand(
                sceneGraph = sceneGraph,
                nodeId = node.id,
                oldPos = node.position.copy(),
                oldRot = node.rotation.copy(),
                oldScale = node.scale.copy(),
                newPos = newPos,
                newRot = newRot,
                newScale = newScale
            )
        )
        onSceneChanged()
    }

    /**
     * Menyimpan hasil gestur/drag pergerakan komponen ke Command History saat sentuhan dilepas.
     */
    fun commitVertexTransformGesture(
        sceneGraph: SceneGraph,
        node: SceneNode,
        history: CommandHistory,
        onSceneChanged: () -> Unit
    ) {
        val mesh = node.mesh ?: return
        val initials = vertexTransformInitialPositions ?: return
        if (initials.isEmpty()) return

        val finals = initials.keys.associateWith { mesh.getVertex(it) }
        val hasMoved = initials.any { (idx, initialPos) ->
            val finalPos = finals[idx] ?: return@any false
            (initialPos - finalPos).lengthSquared() > 0.000001f
        }

        if (hasMoved) {
            history.execute(
                TransformVerticesCommand(
                    nodeId = node.id,
                    sceneGraph = sceneGraph,
                    oldPositions = initials,
                    newPositions = finals
                )
            )
            onSceneChanged()
        }
        resetVertexTransformSession()
    }
}
