package com.example.studio3d.feature.editor.screen

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.studio3d.core.engine.Camera
import com.example.studio3d.core.engine.CameraPreset
import com.example.studio3d.core.engine.GestureSensitivity
import com.example.studio3d.core.engine.Mesh
import com.example.studio3d.core.engine.ShadingMode
import com.example.studio3d.core.history.AddNodeCommand
import com.example.studio3d.core.history.CommandHistory
import com.example.studio3d.core.history.DeleteNodeCommand
import com.example.studio3d.core.history.GroupNodesCommand
import com.example.studio3d.core.math.Vector3
import com.example.studio3d.core.preferences.StudioPreferences
import com.example.studio3d.core.scene.Material
import com.example.studio3d.core.scene.SceneGraph
import com.example.studio3d.core.scene.SceneNode
import com.example.studio3d.core.scene.StarterScene
import com.example.studio3d.feature.editor.panel.NotificationType
import com.example.studio3d.feature.editor.panel.StudioNotification
import com.example.studio3d.feature.editor.panel.StudioNotificationManager
import com.example.studio3d.feature.editor.tools.material.MaterialController
import com.example.studio3d.feature.editor.tools.modeling.InsetSettingsState
import com.example.studio3d.feature.editor.tools.modeling.ExtrudeSettingsState
import com.example.studio3d.feature.editor.tools.modeling.SubdivideSettingsState
import com.example.studio3d.feature.editor.tools.modeling.BevelSettingsState
import com.example.studio3d.feature.editor.tools.modeling.ModelingController
import com.example.studio3d.feature.editor.tools.project.ProjectController
import com.example.studio3d.feature.editor.tools.select.EditSelectionManager
import com.example.studio3d.feature.editor.tools.select.SelectionController
import com.example.studio3d.feature.editor.tools.transform.TransformController
import com.example.studio3d.feature.editor.tools.bridge.AiBridgeServer
import com.example.studio3d.feature.editor.viewport.Gizmo
import com.example.studio3d.feature.editor.viewport.TransformMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

enum class ActivePanel {
    NONE,
    OUTLINER,
    INSPECTOR,
    ADD_MESH,
    PROJECT_MENU,
    RECOVER_AUTOSAVE,
    SETTINGS,
    TRANSFORM_TELEMETRY
}

enum class ModelingMode(val label: String) {
    OBJECT("Object Mode"),
    EDIT("Edit Mode")
}

enum class SelectionMode(val label: String) {
    VERTEX("Vertex"),
    EDGE("Edge"),
    FACE("Face")
}

/**
 * ViewModel orkestrator ringkas untuk 3D Studio.
 * Tanggung jawab didelegasikan ke controllers: Selection, Transform, Modeling, Project, dan Material.
 */
class StudioViewModel : ViewModel() {
    val sceneGraph = SceneGraph()
    val camera = Camera()
    val gizmo = Gizmo()
    val history = CommandHistory()
    val editSelection = EditSelectionManager()

    private val prefs = StudioPreferences.get()

    // Domain Controllers
    val selectionController = SelectionController(editSelection)
    val transformController = TransformController(gizmo, prefs)
    val modelingController = ModelingController()
    val projectController = ProjectController()
    val materialController = MaterialController()
    val notificationManager = StudioNotificationManager(viewModelScope)

    // Delegated States
    val modelingMode: StateFlow<ModelingMode> = selectionController.modelingMode
    val selectionMode: StateFlow<SelectionMode> = selectionController.selectionMode
    val hasEditSelection: StateFlow<Boolean> = selectionController.hasEditSelection
    val selectedFacesCount: StateFlow<Int> = selectionController.selectedFacesCount
    val selectedVerticesCount: StateFlow<Int> = selectionController.selectedVerticesCount
    val selectedEdgesCount: StateFlow<Int> = selectionController.selectedEdgesCount
    val editSelectionStatus: StateFlow<String?> = selectionController.editSelectionStatus

    val transformMode: StateFlow<TransformMode> = transformController.transformMode
    val isSnapping: StateFlow<Boolean> = transformController.isSnapping

    val insetState: StateFlow<InsetSettingsState> = modelingController.insetController.state
    val extrudeState: StateFlow<ExtrudeSettingsState> = modelingController.extrudeController.state
    val subdivideState: StateFlow<SubdivideSettingsState> = modelingController.subdivideController.state
    val bevelState: StateFlow<BevelSettingsState> = modelingController.bevelController.state

    // Wireless AI Bridge
    val aiBridgeServer = AiBridgeServer(port = 8765)
    private val _aiBridgeRunning = MutableStateFlow(prefs?.isAiBridgeEnabled ?: true)
    val aiBridgeRunning: StateFlow<Boolean> = _aiBridgeRunning.asStateFlow()
    private val _aiBridgeIp = MutableStateFlow<String?>(null)
    val aiBridgeIp: StateFlow<String?> = _aiBridgeIp.asStateFlow()
    private val _aiBridgeStatus = MutableStateFlow<String?>(null)
    val aiBridgeStatus: StateFlow<String?> = _aiBridgeStatus.asStateFlow()

    val isImporting: StateFlow<Boolean> = projectController.isImporting
    val importProgress: StateFlow<String?> = projectController.importProgress
    val errorMessage: StateFlow<String?> = projectController.errorMessage

    val canUndo = history.canUndo
    val canRedo = history.canRedo
    val notification: StateFlow<StudioNotification?> = notificationManager.notification

    // Project & Viewport Local States
    private val _projectName = MutableStateFlow("Apex 3D Studio")
    val projectName: StateFlow<String> = _projectName.asStateFlow()

    private val _shadingMode = MutableStateFlow(prefs?.shadingMode ?: ShadingMode.MATERIAL)
    val shadingMode: StateFlow<ShadingMode> = _shadingMode.asStateFlow()

    private val _isTransparentView = MutableStateFlow(prefs?.isTransparentView ?: false)
    val isTransparentView: StateFlow<Boolean> = _isTransparentView.asStateFlow()

    private val _selectedNode = MutableStateFlow<SceneNode?>(null)
    val selectedNode: StateFlow<SceneNode?> = _selectedNode.asStateFlow()

    private val _sceneNodes = MutableStateFlow<List<SceneNode>>(emptyList())
    val sceneNodes: StateFlow<List<SceneNode>> = _sceneNodes.asStateFlow()

    private val _sceneRevision = MutableStateFlow(0)
    val sceneRevision: StateFlow<Int> = _sceneRevision.asStateFlow()

    private val _activePanel = MutableStateFlow(ActivePanel.NONE)
    val activePanel: StateFlow<ActivePanel> = _activePanel.asStateFlow()

    private val _gestureSensitivity = MutableStateFlow(prefs?.gestureSensitivity ?: GestureSensitivity())
    val gestureSensitivity: StateFlow<GestureSensitivity> = _gestureSensitivity.asStateFlow()

    private val _activeCameraPreset = MutableStateFlow<CameraPreset?>(CameraPreset.PERSPECTIVE)
    val activeCameraPreset: StateFlow<CameraPreset?> = _activeCameraPreset.asStateFlow()

    private val _isOrthoSnapEnabled = MutableStateFlow(prefs?.isOrthoSnapEnabled ?: true)
    val isOrthoSnapEnabled: StateFlow<Boolean> = _isOrthoSnapEnabled.asStateFlow()

    private val _isAxisButtonsVisible = MutableStateFlow(prefs?.isAxisButtonsVisible ?: false)
    val isAxisButtonsVisible: StateFlow<Boolean> = _isAxisButtonsVisible.asStateFlow()

    init {
        StarterScene.populateDefaultScene(sceneGraph)
        refreshSceneState()
        gizmo.isSnappingEnabled = isSnapping.value
        gizmo.mode = transformMode.value
        camera.isOrthoSnapEnabled = _isOrthoSnapEnabled.value

        // Start Wireless AI Bridge / MCP Server if enabled
        if (_aiBridgeRunning.value) {
            aiBridgeServer.start(this, viewModelScope)
            val ip = aiBridgeServer.localIpAddress
            _aiBridgeIp.value = ip
            _aiBridgeStatus.value = "${ip ?: "127.0.0.1"}:${aiBridgeServer.port}"
        }
    }

    // --- Scene & Hierarchy Management ---
    fun refreshSceneState() {
        val nodesList = sceneGraph.nodes.values.toList()
        _sceneNodes.value = nodesList
        val curSelected = sceneGraph.getSelectedNode()
        _selectedNode.value = curSelected
        selectionController.updateSelectionCounts(curSelected)
        _sceneRevision.value++
    }

    fun selectNode(id: String?) {
        sceneGraph.selectedNodeId = id
        if (id != null) {
            editSelection.clear()
            transformController.resetVertexTransformSession()
        }
        refreshSceneState()
    }

    fun addPrimitive(type: String) {
        val name = when (type) {
            "cube" -> "Cube"
            "plane" -> "Plane"
            "cylinder" -> "Cylinder"
            "cone" -> "Cone"
            "sphere" -> "Sphere"
            "torus" -> "Torus"
            "wedge" -> "Wedge"
            "plate", "dish" -> "Plate"
            else -> "Primitive"
        }
        val mat = Material.studioClay()
        val node = SceneNode(
            id = UUID.randomUUID().toString(),
            name = "$name ${sceneGraph.nodes.size + 1}",
            position = Vector3(0f, 0.5f, 0f),
            meshType = type,
            material = mat
        )
        node.ensureMesh()
        history.execute(AddNodeCommand(sceneGraph, node))
        sceneGraph.selectedNodeId = node.id
        refreshSceneState()
        _activePanel.value = ActivePanel.NONE
    }

    fun deleteSelected() {
        val selected = sceneGraph.getSelectedNode() ?: return
        if (modelingMode.value == ModelingMode.OBJECT) {
            history.execute(DeleteNodeCommand(sceneGraph, selected))
            refreshSceneState()
            showStatus("Node ${selected.name} dihapus", NotificationType.INFO)
        } else {
            deleteSelectedGeometry()
        }
    }

    fun duplicateSelected() {
        val selected = sceneGraph.getSelectedNode() ?: return
        val clone = selected.deepCopy()
        clone.position = clone.position + Vector3(0.5f, 0f, 0.5f)
        history.execute(AddNodeCommand(sceneGraph, clone, selected.parentId))
        refreshSceneState()
        showStatus("Duplikat dibuat", NotificationType.SUCCESS)
    }

    fun groupSelected() {
        val selected = sceneGraph.getSelectedNode() ?: return
        history.execute(GroupNodesCommand(sceneGraph, listOf(selected.id)))
        refreshSceneState()
        showStatus("Objek digrup", NotificationType.SUCCESS)
    }

    fun toggleNodeVisibility(nodeId: String) {
        val node = sceneGraph.nodes[nodeId] ?: return
        node.isVisible = !node.isVisible
        refreshSceneState()
    }

    // --- Selection Delegation ---
    fun setModelingMode(mode: ModelingMode) {
        selectionController.setModelingMode(mode)
        transformController.resetVertexTransformSession()
        if (mode == ModelingMode.OBJECT) {
            editSelection.clear()
        }
        refreshSceneState()
    }

    fun toggleModelingMode() {
        selectionController.toggleModelingMode()
        transformController.resetVertexTransformSession()
        if (selectionController.modelingMode.value == ModelingMode.OBJECT) {
            editSelection.clear()
        }
        refreshSceneState()
    }

    fun setSelectionMode(mode: SelectionMode) {
        selectionController.setSelectionMode(mode)
        transformController.resetVertexTransformSession()
        notifyEditSelectionChanged()
    }

    fun toggleSelectionMode() {
        selectionController.toggleSelectionMode()
        transformController.resetVertexTransformSession()
        notifyEditSelectionChanged()
    }

    fun clearEditSelection() {
        selectionController.clear()
        notifyEditSelectionChanged()
    }

    fun notifyEditSelectionChanged() {
        selectionController.updateSelectionCounts(sceneGraph.getSelectedNode())
        transformController.resetVertexTransformSession()
        _sceneRevision.value++
    }

    fun getEditSelectionProxyNode(): SceneNode? =
        selectionController.getEditSelectionProxyNode(sceneGraph.getSelectedNode(), transformController)

    fun getVertexSelectionProxyNode(): SceneNode? = getEditSelectionProxyNode()

    // --- Transform Delegation ---
    fun setTransformMode(mode: TransformMode) {
        transformController.setTransformMode(mode)
    }

    fun toggleSnapping() {
        transformController.toggleSnapping()
    }

    fun resetVertexTransformSession() {
        transformController.resetVertexTransformSession()
    }

    fun updateSelectedTransform(newPos: Vector3, newRot: Vector3, newScale: Vector3) {
        val node = sceneGraph.getSelectedNode() ?: return
        transformController.updateTransform(
            sceneGraph = sceneGraph,
            node = node,
            history = history,
            editSelection = editSelection,
            isEditMode = modelingMode.value == ModelingMode.EDIT,
            newPos = newPos,
            newRot = newRot,
            newScale = newScale,
            onMeshDirty = { _sceneRevision.value++ },
            onSceneChanged = { refreshSceneState() }
        )
    }

    // --- Mesh Modeling Operations Delegation ---
    fun extrudeSelectedFaces(distance: Float = 0.4f) {
        if (insetState.value.isActive) cancelInset()
        if (subdivideState.value.isActive) cancelSubdivide()
        if (bevelState.value.isActive) cancelBevel()
        val selectedNode = sceneGraph.getSelectedNode() ?: return
        val faces = editSelection.selectedFaces.toSet()
        if (faces.isEmpty()) {
            if (editSelection.selectedEdges.isNotEmpty()) {
                extrudeSelectedEdges(distance)
                return
            }
            showStatus("Pilih face terlebih dahulu untuk di-extrude", NotificationType.WARNING, 2000L)
            return
        }

        val result = modelingController.extrudeController.startInteractiveExtrude(selectedNode, faces, isEdge = false)
        if (result == null) {
            showStatus("Extrude tidak dapat dilakukan pada face ini", NotificationType.WARNING, 2000L)
            return
        }
        val (newMesh, newCapFaces) = result
        selectedNode.mesh = newMesh

        editSelection.clear()
        editSelection.selectedFaces.addAll(newCapFaces)
        notifyEditSelectionChanged()
        refreshSceneState()
        showStatus("Atur jarak Extrude dengan slider di bawah", NotificationType.INFO, 2500L)
    }

    fun extrudeSelectedEdges(distance: Float = 0.4f) {
        if (insetState.value.isActive) cancelInset()
        if (subdivideState.value.isActive) cancelSubdivide()
        if (bevelState.value.isActive) cancelBevel()
        val selectedNode = sceneGraph.getSelectedNode() ?: return
        val edges = editSelection.selectedEdges.toSet()
        if (edges.isEmpty()) {
            if (editSelection.selectedFaces.isNotEmpty()) {
                extrudeSelectedFaces(distance)
                return
            }
            showStatus("Pilih edge terlebih dahulu untuk di-extrude", NotificationType.WARNING, 2000L)
            return
        }

        val result = modelingController.extrudeController.startInteractiveExtrude(selectedNode, edges, isEdge = true)
        if (result == null) {
            showStatus("Extrude edge tidak dapat dilakukan", NotificationType.WARNING, 2000L)
            return
        }
        val (newMesh, newCapEdges) = result
        selectedNode.mesh = newMesh

        editSelection.clear()
        editSelection.selectedEdges.addAll(newCapEdges)
        notifyEditSelectionChanged()
        refreshSceneState()
        showStatus("Atur jarak Extrude Edge dengan slider di bawah", NotificationType.INFO, 2500L)
    }

    fun extrudeSelected(distance: Float = 0.4f) {
        if (selectionMode.value == SelectionMode.EDGE) {
            extrudeSelectedEdges(distance)
        } else {
            extrudeSelectedFaces(distance)
        }
    }

    fun updateExtrudeDistance(newDistance: Float) {
        val result = modelingController.extrudeController.updateDistance(newDistance)
        if (result != null) {
            val (_, newElements) = result
            editSelection.clear()
            if (extrudeState.value.isEdgeMode) {
                editSelection.selectedEdges.addAll(newElements)
            } else {
                editSelection.selectedFaces.addAll(newElements)
            }
            notifyEditSelectionChanged()
            refreshSceneState()
        }
    }

    fun confirmExtrude() {
        val selectedNode = sceneGraph.getSelectedNode() ?: return
        val currentMesh = selectedNode.mesh ?: return
        val isEdgeMode = extrudeState.value.isEdgeMode
        val initialElements = extrudeState.value.initialSelection
        val initialMesh = modelingController.extrudeController.confirm() ?: return
        val activeElements = if (isEdgeMode) editSelection.selectedEdges.toSet() else editSelection.selectedFaces.toSet()

        history.execute(
            com.example.studio3d.core.history.MeshTopologyChangeCommand(
                nodeId = selectedNode.id,
                sceneGraph = sceneGraph,
                oldMesh = initialMesh,
                newMesh = currentMesh,
                oldSelection = initialElements,
                newSelection = activeElements,
                editSelection = editSelection,
                oldSelectionMode = if (isEdgeMode) SelectionMode.EDGE else SelectionMode.FACE,
                newSelectionMode = if (isEdgeMode) SelectionMode.EDGE else SelectionMode.FACE,
                onSetSelectionMode = { setSelectionMode(it) },
                description = if (isEdgeMode) "Extrude Edge" else "Extrude Face",
                onMeshChanged = {
                    notifyEditSelectionChanged()
                    refreshSceneState()
                }
            )
        )
        notifyEditSelectionChanged()
        refreshSceneState()
        showStatus(if (isEdgeMode) "Extrude Edge berhasil diterapkan" else "Extrude Face berhasil diterapkan", NotificationType.SUCCESS, 1500L)
    }

    fun cancelExtrude() {
        val isEdgeMode = extrudeState.value.isEdgeMode
        val initialElements = extrudeState.value.initialSelection
        val restoredMesh = modelingController.extrudeController.cancel()
        if (restoredMesh != null) {
            editSelection.clear()
            if (isEdgeMode) {
                editSelection.selectedEdges.addAll(initialElements)
            } else {
                editSelection.selectedFaces.addAll(initialElements)
            }
            notifyEditSelectionChanged()
            refreshSceneState()
        }
        showStatus("Extrude dibatalkan", NotificationType.INFO, 1500L)
    }

    fun insetSelectedFaces(ratio: Float = 0.20f) {
        if (extrudeState.value.isActive) cancelExtrude()
        if (subdivideState.value.isActive) cancelSubdivide()
        if (bevelState.value.isActive) cancelBevel()
        val selectedNode = sceneGraph.getSelectedNode() ?: return
        val faces = editSelection.selectedFaces.toSet()
        if (faces.isEmpty()) {
            showStatus("Pilih face terlebih dahulu untuk di-inset", NotificationType.WARNING, 2000L)
            return
        }

        val insetResult = modelingController.insetController.startInteractiveInset(selectedNode, faces)
        if (insetResult == null) {
            showStatus("Inset tidak dapat dilakukan pada face ini", NotificationType.WARNING, 2000L)
            return
        }
        val (newMesh, newCapFaces) = insetResult
        selectedNode.mesh = newMesh

        editSelection.clear()
        editSelection.selectedFaces.addAll(newCapFaces)
        notifyEditSelectionChanged()
        refreshSceneState()
        showStatus("Atur jarak Inset dengan slider di bawah", NotificationType.INFO, 2500L)
    }

    fun updateInsetRatio(newRatio: Float) {
        val insetResult = modelingController.insetController.updateRatio(newRatio)
        if (insetResult != null) {
            val (_, newCapFaces) = insetResult
            editSelection.clear()
            editSelection.selectedFaces.addAll(newCapFaces)
            notifyEditSelectionChanged()
            refreshSceneState()
        }
    }

    fun toggleInsetMode() {
        val insetResult = modelingController.insetController.toggleEvenThickness()
        if (insetResult != null) {
            val (_, newCapFaces) = insetResult
            editSelection.clear()
            editSelection.selectedFaces.addAll(newCapFaces)
            notifyEditSelectionChanged()
            refreshSceneState()
            val modeLabel = if (insetState.value.evenThickness) "Mode: Even Thickness (Rata)" else "Mode: Centroid (Proporsional)"
            showStatus(modeLabel, NotificationType.INFO, 1500L)
        }
    }

    fun confirmInset() {
        val selectedNode = sceneGraph.getSelectedNode() ?: return
        val currentMesh = selectedNode.mesh ?: return
        val initialFaces = insetState.value.initialFaces
        val initialMesh = modelingController.insetController.confirm() ?: return
        val activeCapFaces = editSelection.selectedFaces.toSet()

        history.execute(
            com.example.studio3d.core.history.MeshTopologyChangeCommand(
                nodeId = selectedNode.id,
                sceneGraph = sceneGraph,
                oldMesh = initialMesh,
                newMesh = currentMesh,
                oldSelection = initialFaces,
                newSelection = activeCapFaces,
                editSelection = editSelection,
                oldSelectionMode = SelectionMode.FACE,
                newSelectionMode = SelectionMode.FACE,
                onSetSelectionMode = { setSelectionMode(it) },
                description = "Inset Face",
                onMeshChanged = {
                    notifyEditSelectionChanged()
                    refreshSceneState()
                }
            )
        )
        notifyEditSelectionChanged()
        refreshSceneState()
        showStatus("Inset Face berhasil diterapkan", NotificationType.SUCCESS, 1500L)
    }

    fun cancelInset() {
        val initialFaces = insetState.value.initialFaces
        val restoredMesh = modelingController.insetController.cancel()
        if (restoredMesh != null) {
            editSelection.clear()
            editSelection.selectedFaces.addAll(initialFaces)
            notifyEditSelectionChanged()
            refreshSceneState()
        }
        showStatus("Inset dibatalkan", NotificationType.INFO, 1500L)
    }

    fun subdivideMeshOrSelectedFaces() {
        if (insetState.value.isActive) cancelInset()
        if (extrudeState.value.isActive) cancelExtrude()

        val selectedNode = sceneGraph.getSelectedNode() ?: return
        val currentMesh = selectedNode.mesh ?: return
        val topology = currentMesh.topology
        val currentMode = selectionMode.value

        val targetFaces = HashSet<Int>(editSelection.selectedFaces)
        if (targetFaces.isEmpty()) {
            if (currentMode == SelectionMode.EDGE && editSelection.selectedEdges.isNotEmpty()) {
                val edgeRoots = editSelection.selectedEdges.mapNotNull {
                    if (it in 0 until topology.edges.size) topology.edges[it] else null
                }.map {
                    val r0 = topology.coincidentMap[it.first]
                    val r1 = topology.coincidentMap[it.second]
                    Pair(minOf(r0, r1), maxOf(r0, r1))
                }.toSet()

                for (f in 0 until topology.faceCount) {
                    val (i0, i1, i2) = topology.getFaceIndices(f)
                    val r0 = topology.coincidentMap[i0]
                    val r1 = topology.coincidentMap[i1]
                    val r2 = topology.coincidentMap[i2]
                    val e01 = Pair(minOf(r0, r1), maxOf(r0, r1))
                    val e12 = Pair(minOf(r1, r2), maxOf(r1, r2))
                    val e20 = Pair(minOf(r2, r0), maxOf(r2, r0))
                    if (e01 in edgeRoots || e12 in edgeRoots || e20 in edgeRoots) {
                        targetFaces.add(f)
                    }
                }
            } else if (currentMode == SelectionMode.VERTEX && editSelection.selectedVertices.isNotEmpty()) {
                val vertRoots = editSelection.selectedVertices.map { topology.coincidentMap[it] }.toSet()
                for (f in 0 until topology.faceCount) {
                    val (i0, i1, i2) = topology.getFaceIndices(f)
                    if (topology.coincidentMap[i0] in vertRoots ||
                        topology.coincidentMap[i1] in vertRoots ||
                        topology.coincidentMap[i2] in vertRoots
                    ) {
                        targetFaces.add(f)
                    }
                }
            }
        }

        val result = modelingController.subdivideController.startInteractiveSubdivide(selectedNode, targetFaces)
        if (result == null) {
            showStatus("Subdivide tidak dapat dilakukan", NotificationType.WARNING, 2000L)
            return
        }
        val (newMesh, newFaces) = result
        selectedNode.mesh = newMesh

        editSelection.clear()
        if (targetFaces.isNotEmpty()) {
            editSelection.selectedFaces.addAll(newFaces)
            setSelectionMode(SelectionMode.FACE)
        }
        notifyEditSelectionChanged()
        refreshSceneState()
        showStatus("Atur jumlah Cuts dengan slider di bawah", NotificationType.INFO, 2500L)
    }

    fun updateSubdivideCuts(cuts: Int) {
        val result = modelingController.subdivideController.updateCuts(cuts)
        if (result != null) {
            val (_, newFaces) = result
            if (subdivideState.value.initialFaces.isNotEmpty()) {
                editSelection.clear()
                editSelection.selectedFaces.addAll(newFaces)
            }
            notifyEditSelectionChanged()
            refreshSceneState()
        }
    }

    fun confirmSubdivide() {
        val selectedNode = sceneGraph.getSelectedNode() ?: return
        val currentMesh = selectedNode.mesh ?: return
        val initialFaces = subdivideState.value.initialFaces
        val initialMesh = modelingController.subdivideController.confirm() ?: return
        val activeFaces = editSelection.selectedFaces.toSet()

        history.execute(
            com.example.studio3d.core.history.MeshTopologyChangeCommand(
                nodeId = selectedNode.id,
                sceneGraph = sceneGraph,
                oldMesh = initialMesh,
                newMesh = currentMesh,
                oldSelection = initialFaces,
                newSelection = activeFaces,
                editSelection = editSelection,
                oldSelectionMode = selectionMode.value,
                newSelectionMode = if (initialFaces.isNotEmpty()) SelectionMode.FACE else selectionMode.value,
                onSetSelectionMode = { setSelectionMode(it) },
                description = "Subdivide",
                onMeshChanged = {
                    notifyEditSelectionChanged()
                    refreshSceneState()
                }
            )
        )
        notifyEditSelectionChanged()
        refreshSceneState()
        showStatus("Subdivide berhasil diterapkan", NotificationType.SUCCESS, 1500L)
    }

    fun cancelSubdivide() {
        val initialFaces = subdivideState.value.initialFaces
        val restoredMesh = modelingController.subdivideController.cancel()
        if (restoredMesh != null) {
            editSelection.clear()
            editSelection.selectedFaces.addAll(initialFaces)
            notifyEditSelectionChanged()
            refreshSceneState()
        }
        showStatus("Subdivide dibatalkan", NotificationType.INFO, 1500L)
    }

    fun deleteSelectedGeometry() {
        val node = sceneGraph.getSelectedNode() ?: return
        modelingController.deleteSelectedGeometry(
            node = node,
            sceneGraph = sceneGraph,
            history = history,
            selectionController = selectionController,
            onStatus = { showStatus(it, NotificationType.INFO) },
            onMeshChanged = {
                notifyEditSelectionChanged()
                refreshSceneState()
            }
        )
    }

    fun mergeSelectedVertices() {
        val node = sceneGraph.getSelectedNode() ?: return
        modelingController.mergeSelectedVertices(
            node = node,
            sceneGraph = sceneGraph,
            history = history,
            selectionController = selectionController,
            onStatus = { showStatus(it, NotificationType.SUCCESS) },
            onMeshChanged = {
                notifyEditSelectionChanged()
                refreshSceneState()
            }
        )
    }

    fun startInteractiveBevel(defaultOffset: Float = 0.10f, defaultSegments: Int = 1) {
        if (subdivideState.value.isActive) cancelSubdivide()
        if (bevelState.value.isActive) cancelBevel()
        val node = sceneGraph.getSelectedNode() ?: return
        modelingController.startInteractiveBevel(
            node = node,
            selectionController = selectionController,
            defaultOffset = defaultOffset,
            defaultSegments = defaultSegments,
            onStatus = { showStatus(it, NotificationType.INFO) },
            onMeshChanged = {
                notifyEditSelectionChanged()
                refreshSceneState()
            }
        )
    }

    fun updateBevel(
        offset: Float = bevelState.value.offset,
        segments: Int = bevelState.value.segments,
        profile: Float = bevelState.value.profile,
        mode: com.example.studio3d.feature.editor.tools.modeling.BevelMode = bevelState.value.mode
    ) {
        val result = modelingController.bevelController.updateBevel(offset, segments, profile, mode)
        if (result != null) {
            val (_, newFaces) = result
            editSelection.clear()
            selectionController.setSelectionMode(SelectionMode.FACE)
            editSelection.selectedFaces.addAll(newFaces)
            notifyEditSelectionChanged()
            refreshSceneState()
        }
    }

    fun confirmBevel() {
        val selectedNode = sceneGraph.getSelectedNode() ?: return
        val currentMesh = selectedNode.mesh ?: return
        val initialSel = bevelState.value.initialSelection
        val initialMesh = modelingController.bevelController.confirm() ?: return
        val activeFaces = editSelection.selectedFaces.toSet()

        val oldSelMode = SelectionMode.EDGE

        history.execute(
            com.example.studio3d.core.history.MeshTopologyChangeCommand(
                nodeId = selectedNode.id,
                sceneGraph = sceneGraph,
                oldMesh = initialMesh,
                newMesh = currentMesh,
                oldSelection = initialSel,
                newSelection = activeFaces,
                editSelection = editSelection,
                oldSelectionMode = oldSelMode,
                newSelectionMode = SelectionMode.FACE,
                onSetSelectionMode = { setSelectionMode(it) },
                description = "Bevel Geometry",
                onMeshChanged = {
                    notifyEditSelectionChanged()
                    refreshSceneState()
                }
            )
        )

        showStatus("Bevel selesai (${activeFaces.size} face baru)", NotificationType.SUCCESS)
        notifyEditSelectionChanged()
        refreshSceneState()
    }

    fun cancelBevel() {
        val initialSel = bevelState.value.initialSelection
        val restoredMesh = modelingController.bevelController.cancel()
        if (restoredMesh != null) {
            editSelection.clear()
            setSelectionMode(SelectionMode.EDGE)
            editSelection.selectedEdges.addAll(initialSel)
            notifyEditSelectionChanged()
            refreshSceneState()
        }
    }

    fun bevelSelected(offset: Float = 0.10f, depth: Float = 0.0f, segments: Int = 1) {
        startInteractiveBevel(defaultOffset = offset, defaultSegments = segments)
    }

    fun bevelDirect(offset: Float = 0.12f, depth: Float = 0.0f, segments: Int = 1) {
        val node = sceneGraph.getSelectedNode() ?: return
        modelingController.bevel(
            node = node,
            sceneGraph = sceneGraph,
            history = history,
            selectionController = selectionController,
            offset = offset,
            depth = depth,
            segments = segments,
            onStatus = { showStatus(it, NotificationType.SUCCESS) },
            onMeshChanged = {
                notifyEditSelectionChanged()
                refreshSceneState()
            }
        )
    }

    fun loopCutSelected(factor: Float = 0.5f) {
        val node = sceneGraph.getSelectedNode() ?: return
        modelingController.loopCut(
            node = node,
            sceneGraph = sceneGraph,
            history = history,
            selectionController = selectionController,
            factor = factor,
            onStatus = { showStatus(it, NotificationType.SUCCESS) },
            onMeshChanged = {
                notifyEditSelectionChanged()
                refreshSceneState()
            }
        )
    }

    fun bridgeSelected() {
        val node = sceneGraph.getSelectedNode() ?: return
        modelingController.bridge(
            node = node,
            sceneGraph = sceneGraph,
            history = history,
            selectionController = selectionController,
            onStatus = { showStatus(it, NotificationType.SUCCESS) },
            onMeshChanged = {
                notifyEditSelectionChanged()
                refreshSceneState()
            }
        )
    }

    // --- Project & Autosave Delegation ---
    fun startAutosave(context: Context) {
        projectController.startAutosave(
            context = context,
            scope = viewModelScope,
            getProjectName = { _projectName.value },
            sceneGraph = sceneGraph,
            camera = camera,
            getShadingMode = { _shadingMode.value },
            onAutosaveFound = { _activePanel.value = ActivePanel.RECOVER_AUTOSAVE }
        )
    }

    fun saveProject(context: Context, filename: String) {
        projectController.saveProject(
            context = context,
            filename = filename,
            projectName = _projectName.value,
            sceneGraph = sceneGraph,
            camera = camera,
            shadingMode = _shadingMode.value,
            onSuccess = {
                notificationManager.showSuccess(it, 2500L)
                _activePanel.value = ActivePanel.NONE
            },
            onError = { notificationManager.showError(it, 3500L) }
        )
    }

    fun openProject(context: Context, file: File) {
        projectController.openProject(
            context = context,
            file = file,
            sceneGraph = sceneGraph,
            camera = camera,
            onSuccess = { name, mode ->
                _projectName.value = name
                _shadingMode.value = mode
                history.clear()
                refreshSceneState()
                notificationManager.showSuccess("Proyek dimuat: ${file.name}", 2500L)
                _activePanel.value = ActivePanel.NONE
            },
            onError = { notificationManager.showError(it, 3500L) }
        )
    }

    fun newProject() {
        projectController.newProject(sceneGraph, camera) {
            _projectName.value = "New Project"
            history.clear()
            refreshSceneState()
            notificationManager.showInfo("Proyek baru dibuat", 2000L)
            _activePanel.value = ActivePanel.NONE
        }
    }

    fun restoreAutosave(context: Context) {
        projectController.restoreAutosave(
            context = context,
            sceneGraph = sceneGraph,
            camera = camera,
            onSuccess = { name, mode ->
                _projectName.value = name
                _shadingMode.value = mode
                history.clear()
                refreshSceneState()
                notificationManager.showSuccess("Sesi otomatis berhasil dipulihkan", 2500L)
                _activePanel.value = ActivePanel.NONE
            },
            onError = { notificationManager.showError(it, 3000L) }
        )
    }

    fun import3DModelFile(context: Context, uri: Uri) {
        projectController.import3DModelFile(
            context = context,
            scope = viewModelScope,
            uri = uri,
            sceneGraph = sceneGraph,
            onNodeCreated = { node ->
                history.execute(AddNodeCommand(sceneGraph, node))
                sceneGraph.selectedNodeId = node.id
                refreshSceneState()
                _activePanel.value = ActivePanel.NONE
            },
            onSuccess = { notificationManager.showSuccess(it, 3000L) },
            onError = { notificationManager.showError(it, 4000L) }
        )
    }

    fun importObjFile(context: Context, uri: Uri) = import3DModelFile(context, uri)

    fun dismissError() {
        projectController.dismissError()
    }

    // --- Material & Texture Delegation ---
    fun updateSelectedMaterial(newMat: Material) {
        val node = sceneGraph.getSelectedNode() ?: return
        materialController.updateSelectedMaterial(node, sceneGraph, history, newMat) {
            refreshSceneState()
        }
    }

    fun applyTextureToSelected(context: Context, uri: Uri) {
        val node = sceneGraph.getSelectedNode() ?: return
        materialController.applyTextureToSelected(context, uri, node, sceneGraph, history, {
            notificationManager.showSuccess(it, 2500L)
        }) {
            refreshSceneState()
        }
    }

    fun removeTextureFromSelected() {
        val node = sceneGraph.getSelectedNode() ?: return
        materialController.removeTextureFromSelected(node, sceneGraph, history, {
            notificationManager.showInfo(it, 2000L)
        }) {
            refreshSceneState()
        }
    }

    // --- Viewport, Camera & Touch Settings ---
    fun setShadingMode(mode: ShadingMode) {
        _shadingMode.value = mode
        prefs?.shadingMode = mode
    }

    fun toggleTransparentView() {
        val newVal = !_isTransparentView.value
        _isTransparentView.value = newVal
        prefs?.isTransparentView = newVal
    }

    fun toggleOrthoSnap() {
        val newVal = !_isOrthoSnapEnabled.value
        _isOrthoSnapEnabled.value = newVal
        camera.isOrthoSnapEnabled = newVal
        prefs?.isOrthoSnapEnabled = newVal
    }

    fun toggleAxisButtonsVisibility() {
        val newVal = !_isAxisButtonsVisible.value
        _isAxisButtonsVisible.value = newVal
        prefs?.isAxisButtonsVisible = newVal
    }

    fun onCameraManuallyMoved() {
        _activeCameraPreset.value = null
    }

    fun onCameraPresetSnapped(preset: CameraPreset) {
        _activeCameraPreset.value = preset
    }

    fun toggleAxisCamera(axis: Char) {
        val current = _activeCameraPreset.value
        val (targetPreset, targetOrtho) = when (axis.uppercaseChar()) {
            'X' -> if (current == CameraPreset.RIGHT) Pair(CameraPreset.LEFT, true) else Pair(CameraPreset.RIGHT, true)
            'Y' -> if (current == CameraPreset.TOP) Pair(CameraPreset.BOTTOM, true) else Pair(CameraPreset.TOP, true)
            'Z' -> if (current == CameraPreset.FRONT) Pair(CameraPreset.BACK, true) else Pair(CameraPreset.FRONT, true)
            else -> Pair(CameraPreset.PERSPECTIVE, false)
        }
        setCameraPreset(targetPreset, targetOrtho)
    }

    fun setCameraPreset(preset: CameraPreset, forceOrtho: Boolean? = null) {
        _activeCameraPreset.value = preset
        camera.setPreset(preset)
        if (forceOrtho != null) {
            camera.isOrthographic = forceOrtho
            if (forceOrtho) {
                camera.orthoSize = camera.distance * 2f * kotlin.math.tan((camera.fovDeg * Math.PI / 360.0)).toFloat()
            }
        }
        camera.updateMatrices()
    }

    fun toggleOrthoPerspective() {
        if (camera.isOrthographic) {
            camera.isOrthographic = false
            _activeCameraPreset.value = CameraPreset.PERSPECTIVE
        } else {
            camera.orthoSize = camera.distance * 2f * kotlin.math.tan((camera.fovDeg * Math.PI / 360.0)).toFloat()
            camera.isOrthographic = true
        }
        camera.updateMatrices()
    }

    fun updateOrbitSensitivity(value: Float) {
        val updated = _gestureSensitivity.value.copy(orbit = value)
        _gestureSensitivity.value = updated
        prefs?.gestureSensitivity = updated
    }

    fun updatePanSensitivity(value: Float) {
        val updated = _gestureSensitivity.value.copy(pan = value)
        _gestureSensitivity.value = updated
        prefs?.gestureSensitivity = updated
    }

    fun updateZoomSensitivity(value: Float) {
        val updated = _gestureSensitivity.value.copy(zoom = value)
        _gestureSensitivity.value = updated
        prefs?.gestureSensitivity = updated
    }

    fun updateTwistOrbitSensitivity(value: Float) {
        val updated = _gestureSensitivity.value.copy(twistOrbit = value)
        _gestureSensitivity.value = updated
        prefs?.gestureSensitivity = updated
    }

    fun resetGestureSensitivity() {
        val defaultVal = GestureSensitivity()
        _gestureSensitivity.value = defaultVal
        prefs?.gestureSensitivity = defaultVal
        showStatus("Sensitivitas sentuhan direset ke 1:1", NotificationType.INFO, 2000L)
    }

    // --- Panel, History & Status ---
    fun setActivePanel(panel: ActivePanel) {
        _activePanel.value = if (_activePanel.value == panel) ActivePanel.NONE else panel
    }

    fun dismissPanel() {
        _activePanel.value = ActivePanel.NONE
    }

    fun undo() {
        history.undo()
        refreshSceneState()
    }

    fun redo() {
        history.redo()
        refreshSceneState()
    }

    fun showStatus(msg: String, type: NotificationType = NotificationType.INFO, durationMs: Long = 2500L) {
        when (type) {
            NotificationType.INFO -> notificationManager.showInfo(msg, durationMs)
            NotificationType.SUCCESS -> notificationManager.showSuccess(msg, durationMs)
            NotificationType.WARNING -> notificationManager.showWarning(msg, durationMs)
            NotificationType.ERROR -> notificationManager.showError(msg, durationMs)
        }
    }

    fun toggleAiBridge(enable: Boolean) {
        prefs?.isAiBridgeEnabled = enable
        _aiBridgeRunning.value = enable
        if (enable) {
            if (!aiBridgeServer.isRunning) {
                aiBridgeServer.start(this, viewModelScope)
            }
            val ip = aiBridgeServer.localIpAddress ?: aiBridgeServer.getActiveLocalIpAddress()
            _aiBridgeIp.value = ip
            _aiBridgeStatus.value = "${ip ?: "127.0.0.1"}:${aiBridgeServer.port}"
            showStatus("AI Bridge & MCP Server aktif (Port ${aiBridgeServer.port})", NotificationType.SUCCESS)
        } else {
            aiBridgeServer.stop()
            _aiBridgeStatus.value = null
            showStatus("AI Bridge & MCP Server dinonaktifkan", NotificationType.INFO)
        }
    }

    fun refreshAiBridgeIp() {
        val ip = aiBridgeServer.getActiveLocalIpAddress()
        aiBridgeServer.localIpAddress = ip
        _aiBridgeIp.value = ip
        if (_aiBridgeRunning.value) {
            _aiBridgeStatus.value = "${ip ?: "127.0.0.1"}:${aiBridgeServer.port}"
        }
        showStatus("Alamat IP diperbarui: ${ip ?: "Koneksi kabel ADB aktif"}", NotificationType.INFO)
    }

    override fun onCleared() {
        super.onCleared()
        aiBridgeServer.stop()
    }
}
