package com.example.studio3d.feature.editor.screen

import com.example.studio3d.core.math.*
import com.example.studio3d.core.engine.*
import com.example.studio3d.core.scene.*
import com.example.studio3d.core.history.*
import com.example.studio3d.core.preferences.*
import com.example.studio3d.core.project.*
import com.example.studio3d.feature.editor.bar.*
import com.example.studio3d.feature.editor.panel.*
import com.example.studio3d.feature.editor.viewport.*
import com.example.studio3d.feature.editor.screen.*
import com.example.studio3d.feature.editor.tools.select.*
import com.example.studio3d.feature.editor.tools.modeling.InsetSliderPopup
import com.example.studio3d.feature.editor.tools.modeling.ExtrudeSliderPopup
import com.example.studio3d.feature.editor.tools.modeling.SubdivideSliderPopup
import com.example.studio3d.feature.editor.tools.modeling.BevelSliderPopup
import com.example.studio3d.feature.editor.tools.modeling.BevelMode
import com.example.studio3d.feature.editor.tools.transform.*


import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.HighlightOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Polyline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.studio3d.core.engine.CameraPreset
import com.example.studio3d.core.engine.ShadingMode
import com.example.studio3d.feature.editor.viewport.Studio3DView
import com.example.studio3d.feature.editor.viewport.TransformMode
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SelectionHighlight
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated

@Composable
fun StudioScreen(
    viewModel: StudioViewModel = viewModel()
) {
    val context = LocalContext.current
    val shadingMode by viewModel.shadingMode.collectAsState()
    val transformMode by viewModel.transformMode.collectAsState()
    val isSnapping by viewModel.isSnapping.collectAsState()
    val isTransparentView by viewModel.isTransparentView.collectAsState()
    val selectedNode by viewModel.selectedNode.collectAsState()
    val sceneNodes by viewModel.sceneNodes.collectAsState()
    val activePanel by viewModel.activePanel.collectAsState()
    val projectName by viewModel.projectName.collectAsState()
    val activeCameraPreset by viewModel.activeCameraPreset.collectAsState()
    val isOrthoSnapEnabled by viewModel.isOrthoSnapEnabled.collectAsState()
    val isAxisButtonsVisible by viewModel.isAxisButtonsVisible.collectAsState()
    val modelingMode by viewModel.modelingMode.collectAsState()
    val selectionMode by viewModel.selectionMode.collectAsState()
    val hasEditSelection by viewModel.hasEditSelection.collectAsState()
    val selectedFacesCount by viewModel.selectedFacesCount.collectAsState()
    val selectedVerticesCount by viewModel.selectedVerticesCount.collectAsState()
    val selectedEdgesCount by viewModel.selectedEdgesCount.collectAsState()
    val editSelectionStatus by viewModel.editSelectionStatus.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val importProgress by viewModel.importProgress.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val notification by viewModel.notification.collectAsState()
    val gestureSensitivity by viewModel.gestureSensitivity.collectAsState()
    val insetState by viewModel.insetState.collectAsState()
    val extrudeState by viewModel.extrudeState.collectAsState()
    val subdivideState by viewModel.subdivideState.collectAsState()
    val bevelState by viewModel.bevelState.collectAsState()
    val aiBridgeStatus by viewModel.aiBridgeStatus.collectAsState()
    val aiBridgeRunning by viewModel.aiBridgeRunning.collectAsState()
    val aiBridgeIp by viewModel.aiBridgeIp.collectAsState()
    val sceneRevision by viewModel.sceneRevision.collectAsState()

    // Popup & Hybrid Gesture States
    var isTransformPanelOpen by remember { mutableStateOf(false) }
    var hoveredTransformIndex by remember { mutableStateOf<Int?>(null) }
    var isFaceOpsOpen by remember { mutableStateOf(false) }
    var hoveredFaceOpsIndex by remember { mutableStateOf<Int?>(null) }
    var isTopologyOpsOpen by remember { mutableStateOf(false) }
    var hoveredTopologyOpsIndex by remember { mutableStateOf<Int?>(null) }
    val transformItemBounds = remember { mutableMapOf<Int, Pair<Float, Float>>() }
    var buttonWindowTop by remember { mutableStateOf(0f) }

    fun updateHoverFromY(rawY: Float) {
        var found: Int? = null
        for ((idx, bounds) in transformItemBounds) {
            if (rawY >= bounds.first && rawY <= bounds.second) {
                found = idx
                break
            }
        }
        hoveredTransformIndex = found
    }

    var isAddMeshPanelOpen by remember { mutableStateOf(false) }

    val objPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.importObjFile(context, it) }
    }

    val texturePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.applyTextureToSelected(context, it) }
    }

    LaunchedEffect(Unit) {
        com.example.studio3d.core.preferences.StudioPreferences.init(context)
        viewModel.startAutosave(context)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF3C3C3C))
            .testTag("studio_root")
    ) {
        // 1. OpenGL ES 3.0 Viewport
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .testTag("opengl_viewport"),
            factory = { ctx ->
                Studio3DView(
                    context = ctx,
                    sceneGraph = viewModel.sceneGraph,
                    camera = viewModel.camera,
                    gizmo = viewModel.gizmo,
                    history = viewModel.history,
                    onSelectionChanged = { id ->
                        viewModel.selectNode(id)
                    },
                    onTransformUpdated = {
                        viewModel.refreshSceneState()
                    },
                    onCameraMoved = {
                        viewModel.onCameraManuallyMoved()
                    },
                    onCameraPresetChanged = { preset ->
                        if (preset != null) {
                            viewModel.onCameraPresetSnapped(preset)
                        } else {
                            viewModel.onCameraManuallyMoved()
                        }
                    }
                ).apply {
                    renderer.shadingMode = shadingMode
                    renderer.isTransparentView = isTransparentView
                    renderer.modelingMode = modelingMode
                    renderer.selectionMode = selectionMode
                    renderer.editSelection = viewModel.editSelection
                    this.gestureSensitivity = gestureSensitivity
                    this.modelingMode = modelingMode
                    this.selectionMode = selectionMode
                    this.editSelection = viewModel.editSelection
                    this.onEditSelectionChanged = {
                        viewModel.notifyEditSelectionChanged()
                    }
                }
            },
            update = { view ->
                val dummyRev = sceneRevision
                view.renderer.shadingMode = shadingMode
                view.renderer.isTransparentView = isTransparentView
                view.renderer.modelingMode = modelingMode
                view.renderer.selectionMode = selectionMode
                view.renderer.editSelection = viewModel.editSelection
                view.gestureSensitivity = gestureSensitivity
                view.modelingMode = modelingMode
                view.selectionMode = selectionMode
                view.editSelection = viewModel.editSelection
                view.onEditSelectionChanged = {
                    viewModel.notifyEditSelectionChanged()
                }
                view.requestRender()
            }
        )

        // 2. Top Floating Controls (Project Manager, Material Preview & Transparent View on LEFT, Redo & Undo on RIGHT)
        // 2. Main Vertical Layout Scaffold (Top Bar -> Middle Space -> Bottom Bar)
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // A. Top Floating Controls (StudioTopBar)
            StudioTopBar(
                activePanel = activePanel,
                shadingMode = shadingMode,
                isTransparentView = isTransparentView,
                canUndo = canUndo,
                canRedo = canRedo,
                isAiBridgeRunning = aiBridgeRunning,
                onOpenSettings = { viewModel.setActivePanel(ActivePanel.SETTINGS) },
                onOpenProjectMenu = { viewModel.setActivePanel(ActivePanel.PROJECT_MENU) },
                onShadingModeChanged = { viewModel.setShadingMode(it) },
                onToggleTransparentView = { viewModel.toggleTransparentView() },
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                onShowAiBridgeInfo = {
                    viewModel.setActivePanel(ActivePanel.SETTINGS)
                }
            )

            // B. Middle Viewport Space (Sisa ruang layar persis di antara Top Bar dan Bottom Bar)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Ketuk di luar = menutup panel aktif (Outliner, Inspector, Transform Telemetry)
                if (activePanel in listOf(ActivePanel.OUTLINER, ActivePanel.INSPECTOR, ActivePanel.TRANSFORM_TELEMETRY)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(activePanel) {
                                detectTapGestures {
                                    viewModel.dismissPanel()
                                }
                            }
                    )
                }

                // Edit Mode & Component Selection Status HUD Badge (Dihapus sesuai permintaan user)

                // Custom Compact Card: Transform Inspector (Global Numeric Controller, Muncul di atas Bottom Bar)
                androidx.compose.animation.AnimatedVisibility(
                    visible = activePanel == ActivePanel.TRANSFORM_TELEMETRY,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 48.dp, end = 8.dp, bottom = 6.dp)
                ) {
                    TransformInspectorCard(
                        node = if (modelingMode == ModelingMode.EDIT && hasEditSelection) {
                            viewModel.getEditSelectionProxyNode()
                        } else {
                            selectedNode
                        },
                        onTransformChanged = { pos, rot, scale ->
                            viewModel.updateSelectedTransform(pos, rot, scale)
                        },
                        onClose = { viewModel.dismissPanel() }
                    )
                }

                // Vertical T, S, X, Y, Z Perspective Buttons (StudioAxisBar)
                val isTelemetryActive = activePanel == ActivePanel.TRANSFORM_TELEMETRY
                StudioAxisBar(
                    isAxisButtonsVisible = isAxisButtonsVisible,
                    isTelemetryActive = isTelemetryActive,
                    isOrthoSnapEnabled = isOrthoSnapEnabled,
                    activeCameraPreset = activeCameraPreset,
                    isCameraOrthographic = viewModel.camera.isOrthographic,
                    onToggleVisibility = { viewModel.toggleAxisButtonsVisibility() },
                    onToggleTelemetry = {
                        if (isTelemetryActive) viewModel.dismissPanel()
                        else viewModel.setActivePanel(ActivePanel.TRANSFORM_TELEMETRY)
                    },
                    onToggleOrthoSnap = { viewModel.toggleOrthoSnap() },
                    onToggleAxisCamera = { viewModel.toggleAxisCamera(it) },
                    modifier = Modifier.align(Alignment.BottomStart)
                )

                // Sliding Side Panels: Outliner & Inspector (di sebelah kanan Middle Box)
                androidx.compose.animation.AnimatedVisibility(
                    visible = activePanel == ActivePanel.OUTLINER,
                    enter = slideInHorizontally(initialOffsetX = { it }),
                    exit = slideOutHorizontally(targetOffsetX = { it }),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 6.dp, end = 6.dp)
                ) {
                    OutlinerSheet(
                        modifier = Modifier.wrapContentHeight(),
                        nodes = sceneNodes,
                        selectedNodeId = selectedNode?.id,
                        onSelectNode = { viewModel.selectNode(it) },
                        onToggleVisibility = { viewModel.toggleNodeVisibility(it) },
                        onDeleteSelected = { viewModel.deleteSelected() },
                        onDuplicateSelected = { viewModel.duplicateSelected() },
                        onGroupSelected = { viewModel.groupSelected() },
                        onClose = { viewModel.dismissPanel() }
                    )
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = activePanel == ActivePanel.INSPECTOR,
                    enter = slideInHorizontally(initialOffsetX = { it }),
                    exit = slideOutHorizontally(targetOffsetX = { it }),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(vertical = 4.dp, horizontal = 6.dp)
                        .fillMaxHeight()
                ) {
                    InspectorSheet(
                        modifier = Modifier.fillMaxHeight(),
                        node = if (modelingMode == ModelingMode.EDIT && hasEditSelection) {
                            viewModel.getEditSelectionProxyNode()
                        } else {
                            selectedNode
                        },
                        onTransformChanged = { pos, rot, scale ->
                            viewModel.updateSelectedTransform(pos, rot, scale)
                        },
                        onMaterialChanged = { newMat ->
                            viewModel.updateSelectedMaterial(newMat)
                        },
                        onSelectTexture = { texturePickerLauncher.launch("image/*") },
                        onRemoveTexture = { viewModel.removeTextureFromSelected() },
                        onClose = { viewModel.dismissPanel() }
                    )
                }
            }

            // Interactive Inset Distance Slider Popup
            InsetSliderPopup(
                visible = insetState.isActive,
                ratio = insetState.ratio,
                evenThickness = insetState.evenThickness,
                onRatioChange = { viewModel.updateInsetRatio(it) },
                onToggleEvenThickness = { viewModel.toggleInsetMode() },
                onConfirm = { viewModel.confirmInset() },
                onCancel = { viewModel.cancelInset() }
            )

            // Interactive Extrude Distance Slider Popup
            ExtrudeSliderPopup(
                visible = extrudeState.isActive,
                distance = extrudeState.distance,
                isEdgeMode = extrudeState.isEdgeMode,
                onDistanceChange = { viewModel.updateExtrudeDistance(it) },
                onConfirm = { viewModel.confirmExtrude() },
                onCancel = { viewModel.cancelExtrude() }
            )

            // Interactive Subdivide Cuts Slider Popup
            SubdivideSliderPopup(
                visible = subdivideState.isActive,
                cuts = subdivideState.cuts,
                onCutsChange = { viewModel.updateSubdivideCuts(it) },
                onConfirm = { viewModel.confirmSubdivide() },
                onCancel = { viewModel.cancelSubdivide() }
            )

            // Interactive Bevel Width & Segments Slider Popup
            BevelSliderPopup(
                visible = bevelState.isActive,
                offset = bevelState.offset,
                segments = bevelState.segments,
                profile = bevelState.profile,
                mode = bevelState.mode,
                onOffsetChange = { viewModel.updateBevel(offset = it) },
                onSegmentsChange = { viewModel.updateBevel(segments = it) },
                onProfileChange = { viewModel.updateBevel(profile = it) },
                onModeChange = { viewModel.updateBevel(mode = it) },
                onConfirm = { viewModel.confirmBevel() },
                onCancel = { viewModel.cancelBevel() }
            )

            // C. Minimalist 2-Row Bottom Action Bar (StudioBottomBar)
            StudioBottomBar(
                modelingMode = modelingMode,
                selectionMode = selectionMode,
                transformMode = transformMode,
                selectedNode = selectedNode,
                hasEditSelection = hasEditSelection,
                selectedVerticesCount = selectedVerticesCount,
                selectedEdgesCount = selectedEdgesCount,
                selectedFacesCount = selectedFacesCount,
                activePanel = activePanel,
                isTransformPanelOpen = isTransformPanelOpen,
                isAddMeshPanelOpen = isAddMeshPanelOpen,
                isFaceOpsOpen = isFaceOpsOpen,
                onOpenFaceOps = {
                    isFaceOpsOpen = true
                    isTopologyOpsOpen = false
                    isTransformPanelOpen = false
                    isAddMeshPanelOpen = false
                },
                onCloseFaceOps = {
                    isFaceOpsOpen = false
                    hoveredFaceOpsIndex = null
                },
                hoveredFaceOpsIndex = hoveredFaceOpsIndex,
                onHoverFaceOpsIndex = { hoveredFaceOpsIndex = it },
                onFaceDummySelected = { toolName ->
                    viewModel.showStatus("$toolName dipilih (Dummy)", NotificationType.INFO, 2000L)
                },
                isTopologyOpsOpen = isTopologyOpsOpen,
                onOpenTopologyOps = {
                    isTopologyOpsOpen = true
                    isFaceOpsOpen = false
                    isTransformPanelOpen = false
                    isAddMeshPanelOpen = false
                },
                onCloseTopologyOps = {
                    isTopologyOpsOpen = false
                    hoveredTopologyOpsIndex = null
                },
                hoveredTopologyOpsIndex = hoveredTopologyOpsIndex,
                onHoverTopologyOpsIndex = { hoveredTopologyOpsIndex = it },
                onTopologyDummySelected = { toolName ->
                    viewModel.showStatus("$toolName dipilih (Dummy)", NotificationType.INFO, 2000L)
                },
                onOpenTransformPanel = {
                    isTransformPanelOpen = true
                    isFaceOpsOpen = false
                    isTopologyOpsOpen = false
                    isAddMeshPanelOpen = false
                },
                onCloseTransformPanel = {
                    isTransformPanelOpen = false
                    hoveredTransformIndex = null
                },
                onToggleTransformPanel = {
                    isTransformPanelOpen = !isTransformPanelOpen
                    if (isTransformPanelOpen) {
                        isFaceOpsOpen = false
                        isTopologyOpsOpen = false
                        isAddMeshPanelOpen = false
                    }
                },
                onToggleAddMeshPanel = {
                    isAddMeshPanelOpen = !isAddMeshPanelOpen
                    if (isAddMeshPanelOpen) {
                        isFaceOpsOpen = false
                        isTopologyOpsOpen = false
                        isTransformPanelOpen = false
                    }
                },
                onTransformModeChanged = { viewModel.setTransformMode(it) },
                onToggleSnapping = { viewModel.toggleSnapping() },
                hoveredTransformIndex = hoveredTransformIndex,
                onHoverTransformIndex = { hoveredTransformIndex = it },
                buttonWindowTop = buttonWindowTop,
                onButtonWindowTopChanged = { buttonWindowTop = it },
                onUpdateHoverFromY = { updateHoverFromY(it) },
                onSetSelectionMode = { viewModel.setSelectionMode(it) },
                onToggleModelingMode = { viewModel.toggleModelingMode() },
                onSetActivePanel = { viewModel.setActivePanel(it) },
                onExtrudeFaces = { viewModel.extrudeSelectedFaces() },
                onExtrudeEdges = { viewModel.extrudeSelectedEdges() },
                onInsetFaces = { viewModel.insetSelectedFaces() },
                onBevel = { viewModel.bevelSelected() },
                onBridge = { viewModel.bridgeSelected() },
                onLoopCut = { viewModel.loopCutSelected() },
                onSubdivide = { viewModel.subdivideMeshOrSelectedFaces() },
                onMergeVertices = { viewModel.mergeSelectedVertices() },
                onDeleteSelected = { viewModel.deleteSelected() },
                onClearEditSelection = { viewModel.clearEditSelection() }
            )
        } // End of Main Vertical Layout Column

        // Overlays drawn ON TOP of the Bottom Action Bar in Z-order
        // 3a. Radial Selector Menu Overlay (Transform Tools)
        RadialSelectorMenu(
            isOpen = isTransformPanelOpen,
            hoveredIndex = hoveredTransformIndex,
            transformMode = transformMode,
            isSnapping = isSnapping,
            onSelectMode = { viewModel.setTransformMode(it) },
            onToggleSnapping = { viewModel.toggleSnapping() },
            onDismiss = {
                isTransformPanelOpen = false
                hoveredTransformIndex = null
            }
        )

        // 3b. Face / Edge Ops Radial Menu Overlay (Extrude, Inset, Bevel, Bridge, Poke)
        FaceOpsRadialMenu(
            isOpen = isFaceOpsOpen,
            hoveredIndex = hoveredFaceOpsIndex,
            onExtrude = {
                if (selectionMode == SelectionMode.EDGE && viewModel.selectedEdgesCount.value > 0) {
                    viewModel.extrudeSelectedEdges()
                } else {
                    viewModel.extrudeSelectedFaces()
                }
            },
            onInset = {
                viewModel.insetSelectedFaces()
            },
            onBevel = {
                viewModel.bevelSelected()
            },
            onBridge = {
                viewModel.bridgeSelected()
            },
            onSelectDummy = { toolName ->
                val finalName = if (selectionMode == SelectionMode.EDGE) {
                    when (toolName) {
                        "Bevel Face" -> "Bevel Edge"
                        "Bridge Faces" -> "Bridge Edges"
                        else -> toolName
                    }
                } else toolName
                viewModel.showStatus("$finalName dipilih (Dummy)", NotificationType.INFO, 2000L)
            },
            onDismiss = {
                isFaceOpsOpen = false
                hoveredFaceOpsIndex = null
            }
        )

        // 3c. Topology Ops Radial Menu Overlay (Subdivide, Loop Cut, Separate, Dissolve, Merge)
        TopologyOpsRadialMenu(
            isOpen = isTopologyOpsOpen,
            hoveredIndex = hoveredTopologyOpsIndex,
            onSubdivide = { viewModel.subdivideMeshOrSelectedFaces() },
            onLoopCut = { viewModel.loopCutSelected() },
            onDissolve = { viewModel.deleteSelected() },
            onMerge = { viewModel.mergeSelectedVertices() },
            onSelectDummy = { toolName ->
                viewModel.showStatus("$toolName dipilih (Dummy)", NotificationType.INFO, 2000L)
            },
            onDismiss = {
                isTopologyOpsOpen = false
                hoveredTopologyOpsIndex = null
            }
        )

        // 3c. Add Mesh Selector Panel Overlay (Standard tap popup)
        AddMeshSelectorPanel(
            isOpen = isAddMeshPanelOpen,
            onAddPrimitive = { type ->
                if (type == "import_obj") {
                    objPickerLauncher.launch("*/*")
                } else {
                    viewModel.addPrimitive(type)
                }
            },
            onDismiss = {
                isAddMeshPanelOpen = false
            }
        )




        // 8. Project Dialog
        if (activePanel == ActivePanel.PROJECT_MENU) {
            ProjectDialog(
                currentProjectName = projectName,
                onSaveProject = { filename ->
                    viewModel.saveProject(context, filename)
                },
                onOpenProject = { file ->
                    viewModel.openProject(context, file)
                },
                onNewProject = {
                    viewModel.newProject()
                },
                onDismiss = { viewModel.dismissPanel() }
            )
        }

        // 9. Autosave Recovery Dialog
        if (activePanel == ActivePanel.RECOVER_AUTOSAVE) {
            RecoverAutosaveDialog(
                onRestore = { viewModel.restoreAutosave(context) },
                onDiscard = { viewModel.dismissPanel() }
            )
        }

        // Full-Screen Settings Page (Sensitivitas Sentuh, AI Bridge / MCP Server, dan Preferensi)
        if (activePanel == ActivePanel.SETTINGS) {
            SettingsScreen(
                sensitivity = gestureSensitivity,
                onOrbitChanged = { viewModel.updateOrbitSensitivity(it) },
                onPanChanged = { viewModel.updatePanSensitivity(it) },
                onZoomChanged = { viewModel.updateZoomSensitivity(it) },
                onTwistOrbitChanged = { viewModel.updateTwistOrbitSensitivity(it) },
                onResetSensitivity = { viewModel.resetGestureSensitivity() },
                isAiBridgeRunning = aiBridgeRunning,
                aiBridgeIp = aiBridgeIp,
                aiBridgePort = viewModel.aiBridgeServer.port,
                onToggleAiBridge = { viewModel.toggleAiBridge(it) },
                onRefreshIp = { viewModel.refreshAiBridgeIp() },
                shadingMode = shadingMode,
                onShadingModeChanged = { viewModel.setShadingMode(it) },
                isTransparentView = isTransparentView,
                onToggleTransparentView = { viewModel.toggleTransparentView() },
                isSnapping = isSnapping,
                onToggleSnapping = { viewModel.toggleSnapping() },
                onBack = { viewModel.dismissPanel() }
            )
        }

        // 10. Floating Studio Notification Banner (Disabled as requested)

        // 11. 3D Model Loading Animation Dialog Overlay
        if (isImporting) {
            Dialog(
                onDismissRequest = { /* Modal during loading */ },
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, StudioBorder),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = AccentCyan,
                            strokeWidth = 3.5.dp,
                            modifier = Modifier.size(46.dp)
                        )
                        Text(
                            text = "Memuat Model 3D...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = importProgress ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // 12. Model Load Error Alert Dialog
        if (errorMessage != null) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissError() },
                icon = {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Error",
                        tint = ErrorRed,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = {
                    Text(
                        text = "Gagal Memuat Model",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                text = {
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.dismissError() }
                    ) {
                        Text("Tutup", color = AccentCyan, fontWeight = FontWeight.SemiBold)
                    }
                },
                containerColor = StudioSurfaceElevated,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}
