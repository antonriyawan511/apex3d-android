package com.example.studio3d.feature.editor.bar

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
import com.example.studio3d.feature.editor.tools.transform.*


import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.HighlightOff
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Polyline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.studio3d.feature.editor.viewport.TransformMode
import com.example.studio3d.core.scene.SceneNode
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.ErrorRed

/**
 * 2-Row x 7-Column Bottom Action Bar component for 3D modeling operations,
 * component selection, transform modes, and side sheet activations.
 */
@Composable
fun StudioBottomBar(
    modelingMode: ModelingMode,
    selectionMode: SelectionMode,
    transformMode: TransformMode,
    selectedNode: SceneNode?,
    hasEditSelection: Boolean,
    selectedVerticesCount: Int,
    selectedEdgesCount: Int,
    selectedFacesCount: Int,
    activePanel: ActivePanel?,
    isTransformPanelOpen: Boolean,
    isAddMeshPanelOpen: Boolean,
    isFaceOpsOpen: Boolean = false,
    onOpenFaceOps: () -> Unit = {},
    onCloseFaceOps: () -> Unit = {},
    hoveredFaceOpsIndex: Int? = null,
    onHoverFaceOpsIndex: (Int?) -> Unit = {},
    onFaceDummySelected: (String) -> Unit = {},
    isTopologyOpsOpen: Boolean = false,
    onOpenTopologyOps: () -> Unit = {},
    onCloseTopologyOps: () -> Unit = {},
    hoveredTopologyOpsIndex: Int? = null,
    onHoverTopologyOpsIndex: (Int?) -> Unit = {},
    onTopologyDummySelected: (String) -> Unit = {},
    onOpenTransformPanel: () -> Unit = {},
    onCloseTransformPanel: () -> Unit = {},
    onToggleTransformPanel: () -> Unit,
    onToggleAddMeshPanel: () -> Unit,
    onTransformModeChanged: (TransformMode) -> Unit,
    onToggleSnapping: () -> Unit,
    hoveredTransformIndex: Int?,
    onHoverTransformIndex: (Int?) -> Unit,
    buttonWindowTop: Float,
    onButtonWindowTopChanged: (Float) -> Unit,
    onUpdateHoverFromY: (Float) -> Unit,
    onSetSelectionMode: (SelectionMode) -> Unit,
    onToggleModelingMode: () -> Unit,
    onSetActivePanel: (ActivePanel) -> Unit,
    onExtrudeFaces: () -> Unit,
    onExtrudeEdges: () -> Unit = {},
    onInsetFaces: () -> Unit = {},
    onBevel: () -> Unit = {},
    onBridge: () -> Unit = {},
    onLoopCut: () -> Unit = {},
    onSubdivide: () -> Unit = {},
    onMergeVertices: () -> Unit = {},
    onDeleteSelected: () -> Unit,
    onClearEditSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 4.dp, vertical = 0.dp)
            .testTag("bottom_action_bar"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // ROW 1: Extrude, Inner, Transform, Vertex, Edge, Face, Mode Toggle
        val currentSelectionMode by rememberUpdatedState(selectionMode)
        val currentOnExtrudeFaces by rememberUpdatedState(onExtrudeFaces)
        val currentOnExtrudeEdges by rememberUpdatedState(onExtrudeEdges)
        val currentOnInsetFaces by rememberUpdatedState(onInsetFaces)
        val currentOnBevel by rememberUpdatedState(onBevel)
        val currentOnBridge by rememberUpdatedState(onBridge)
        val currentOnLoopCut by rememberUpdatedState(onLoopCut)
        val currentOnFaceDummySelected by rememberUpdatedState(onFaceDummySelected)
        val currentHoveredFaceOpsIndex by rememberUpdatedState(hoveredFaceOpsIndex)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // 1. Component Ops Radial Selector (Extrude, Inset, Bevel, Bridge, Poke)
            val isFaceEditMode = modelingMode == ModelingMode.EDIT && selectionMode == SelectionMode.FACE
            val isEdgeEditMode = modelingMode == ModelingMode.EDIT && selectionMode == SelectionMode.EDGE
            val isExtrudeEnabled = (isFaceEditMode && selectedFacesCount > 0) || (isEdgeEditMode && selectedEdgesCount > 0)
            val opsButtonLabel = when {
                selectionMode == SelectionMode.EDGE -> "Edge Ops"
                selectionMode == SelectionMode.VERTEX -> "Vertex Ops"
                else -> "Face Ops"
            }

            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .pointerInput(selectionMode) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val downX = down.position.x
                            val downY = down.position.y
                            var hasDragged = false
                            var localHoverIndex: Int? = null

                            onOpenFaceOps()

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) {
                                    val finalSelected = localHoverIndex ?: currentHoveredFaceOpsIndex
                                    if (hasDragged && finalSelected != null) {
                                        when (finalSelected) {
                                            0 -> {
                                                if (currentSelectionMode == SelectionMode.EDGE) {
                                                    currentOnExtrudeEdges()
                                                } else {
                                                    currentOnExtrudeFaces()
                                                }
                                            }
                                            1 -> {
                                                if (currentSelectionMode == SelectionMode.EDGE) {
                                                    currentOnFaceDummySelected("Inset hanya untuk Face")
                                                } else {
                                                    currentOnInsetFaces()
                                                }
                                            }
                                            2 -> currentOnBevel()
                                            3 -> currentOnBridge()
                                            4 -> currentOnFaceDummySelected("Poke Face")
                                        }
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onCloseFaceOps()
                                    } else {
                                        onHoverFaceOpsIndex(null)
                                    }
                                    break
                                } else {
                                    val dx = change.position.x - downX
                                    val dy = change.position.y - downY
                                    val dist = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                                    if (dist > 6f) {
                                        hasDragged = true
                                        val hoverIdx = calculateRadialHoverIndex(dx, dy, thresholdDist = 6f)
                                        if (hoverIdx != null && hoverIdx != localHoverIndex) {
                                            localHoverIndex = hoverIdx
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onHoverFaceOpsIndex(hoverIdx)
                                        }
                                    }
                                }
                            }
                        }
                    }
            ) {
                BottomBarButton(
                    label = opsButtonLabel,
                    icon = Icons.Default.Polyline,
                    isActive = isFaceOpsOpen || isExtrudeEnabled,
                    enabled = true,
                    activeColor = AccentAmber,
                    onClick = { /* Handled by pointerInput */ },
                    testTag = "bottom_face_ops"
                )
            }

            // 2. Topology Radial Selector (Subdivide, Loop Cut, Separate, Dissolve, Merge)
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val downX = down.position.x
                            val downY = down.position.y
                            var hasDragged = false
                            var localHoverIndex: Int? = null

                            onOpenTopologyOps()

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) {
                                    val finalSelected = localHoverIndex ?: hoveredTopologyOpsIndex
                                    if (hasDragged && finalSelected != null) {
                                        when (finalSelected) {
                                            0 -> onSubdivide()
                                            1 -> currentOnLoopCut()
                                            2 -> onTopologyDummySelected("Separate Mesh")
                                            3 -> onDeleteSelected()
                                            4 -> onMergeVertices()
                                        }
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onCloseTopologyOps()
                                    } else {
                                        onHoverTopologyOpsIndex(null)
                                    }
                                    break
                                } else {
                                    val dx = change.position.x - downX
                                    val dy = change.position.y - downY
                                    val dist = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                                    if (dist > 6f) {
                                        hasDragged = true
                                        val hoverIdx = calculateRadialHoverIndex(dx, dy, thresholdDist = 6f)
                                        if (hoverIdx != null && hoverIdx != localHoverIndex) {
                                            localHoverIndex = hoverIdx
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onHoverTopologyOpsIndex(hoverIdx)
                                        }
                                    }
                                }
                            }
                        }
                    }
            ) {
                BottomBarButton(
                    label = "Topology",
                    icon = Icons.Default.Build,
                    isActive = isTopologyOpsOpen,
                    enabled = true,
                    activeColor = AccentCyan,
                    onClick = { /* Handled by pointerInput */ },
                    testTag = "bottom_topology_ops"
                )
            }

            // 3. Transform Selector Button (Hybrid: Tap to toggle popup, or Hold & Slide to flick select)
            val activeTransformLabel = when (transformMode) {
                TransformMode.SELECT -> "Select"
                TransformMode.TRANSLATE -> "Move"
                TransformMode.ROTATE -> "Rotate"
                TransformMode.SCALE -> "Scale"
            }
            val activeTransformIcon = when (transformMode) {
                TransformMode.SELECT -> Icons.Default.NearMe
                TransformMode.TRANSLATE -> Icons.Default.OpenWith
                TransformMode.ROTATE -> Icons.Default.Refresh
                TransformMode.SCALE -> Icons.Default.ZoomOutMap
            }

            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .onGloballyPositioned { coordinates ->
                        onButtonWindowTopChanged(coordinates.boundsInWindow().top)
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val downX = down.position.x
                            val downY = down.position.y
                            var hasDragged = false
                            var localHoverIndex: Int? = null

                            // Langsung buka radial donut pada sentuhan pertama di tombol!
                            onOpenTransformPanel()

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) {
                                    // Pointer released
                                    val finalSelected = localHoverIndex ?: hoveredTransformIndex
                                    if (hasDragged && finalSelected != null) {
                                        when (finalSelected) {
                                            0 -> onTransformModeChanged(TransformMode.TRANSLATE) // Jam 12: Move
                                            1 -> onTransformModeChanged(TransformMode.SCALE)     // Jam 2: Scale
                                            2 -> onToggleSnapping()                              // Jam 5: Snap
                                            3 -> onTransformModeChanged(TransformMode.SELECT)    // Jam 7: Select
                                            4 -> onTransformModeChanged(TransformMode.ROTATE)    // Jam 10: Rotate
                                        }
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        // Tutup radial panel seketika sehabis flick swipe (pasti tertutup)
                                        onCloseTransformPanel()
                                    } else {
                                        // Klik biasa tanpa geser: menu donat tetap terbuka di tengah layar
                                        onHoverTransformIndex(null)
                                    }
                                    break
                                } else {
                                    val dx = change.position.x - downX
                                    val dy = change.position.y - downY
                                    val dist = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                                    // Slide to flick: baca pergerakan instan sehabis dari tombol
                                    if (dist > 6f) {
                                        hasDragged = true
                                        val hoverIdx = calculateRadialHoverIndex(dx, dy, thresholdDist = 6f)
                                        if (hoverIdx != null && hoverIdx != localHoverIndex) {
                                            localHoverIndex = hoverIdx
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) // Getaran mikro taktil saat pindah sektor!
                                            onHoverTransformIndex(hoverIdx)
                                        }
                                    }
                                }
                            }
                        }
                    }
            ) {
                StudioBarButton(
                    label = activeTransformLabel,
                    icon = activeTransformIcon,
                    isActive = isTransformPanelOpen,
                    onClick = { /* Handled by hybrid pointerInput */ },
                    testTag = "bottom_transform"
                )
            }

            // 4. Vertex
            val vertexLabel = if (modelingMode == ModelingMode.EDIT && selectedVerticesCount > 0) {
                "Vertex ($selectedVerticesCount)"
            } else {
                "Vertex"
            }
            BottomBarButton(
                label = vertexLabel,
                icon = Icons.Default.Circle,
                isActive = modelingMode == ModelingMode.EDIT && selectionMode == SelectionMode.VERTEX,
                onClick = { onSetSelectionMode(SelectionMode.VERTEX) },
                testTag = "bottom_vertex"
            )

            // 5. Edge
            val edgeLabel = if (modelingMode == ModelingMode.EDIT && selectedEdgesCount > 0) {
                "Edge ($selectedEdgesCount)"
            } else {
                "Edge"
            }
            BottomBarButton(
                label = edgeLabel,
                icon = Icons.AutoMirrored.Filled.ShowChart,
                isActive = modelingMode == ModelingMode.EDIT && selectionMode == SelectionMode.EDGE,
                onClick = { onSetSelectionMode(SelectionMode.EDGE) },
                testTag = "bottom_edge"
            )

            // 6. Face
            val faceLabel = if (modelingMode == ModelingMode.EDIT && selectedFacesCount > 0) {
                "Face ($selectedFacesCount)"
            } else {
                "Face"
            }
            BottomBarButton(
                label = faceLabel,
                icon = Icons.Default.CropSquare,
                isActive = modelingMode == ModelingMode.EDIT && selectionMode == SelectionMode.FACE,
                onClick = { onSetSelectionMode(SelectionMode.FACE) },
                testTag = "bottom_face"
            )

            // 7. Mode Toggle
            val isEditMode = modelingMode == ModelingMode.EDIT
            BottomBarButton(
                label = if (isEditMode) "Edit" else "Object",
                icon = if (isEditMode) Icons.Default.Edit else Icons.Default.ViewInAr,
                isActive = isEditMode,
                activeColor = AccentAmber,
                onClick = { onToggleModelingMode() },
                testTag = "bottom_mode_toggle"
            )
        }

        // ROW 2: Outliner, Inspector, Add Mesh, Subdiv, Tools, Delete, Deselect
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // 1. Outliner Button
            BottomBarButton(
                label = "Outliner",
                icon = Icons.Default.Widgets,
                isActive = activePanel == ActivePanel.OUTLINER,
                activeColor = AccentCyan,
                onClick = { onSetActivePanel(ActivePanel.OUTLINER) },
                testTag = "bottom_outliner"
            )

            // 2. Inspector Button
            BottomBarButton(
                label = "Inspector",
                icon = Icons.Default.Tune,
                isActive = activePanel == ActivePanel.INSPECTOR,
                activeColor = AccentAmber,
                onClick = { onSetActivePanel(ActivePanel.INSPECTOR) },
                testTag = "bottom_inspector"
            )

            // 3. Add Mesh Button (Tap to open/close popup)
            BottomBarButton(
                label = "Add",
                icon = Icons.Default.Add,
                isActive = isAddMeshPanelOpen,
                onClick = { onToggleAddMeshPanel() },
                testTag = "bottom_add_mesh"
            )

            // 4. Delete Button (Deletes selected object in Object Mode or active element in Edit Mode)
            val isDeleteEnabled = if (modelingMode == ModelingMode.EDIT) hasEditSelection else (selectedNode != null)
            BottomBarButton(
                label = "Delete",
                icon = Icons.Default.Delete,
                isActive = false,
                enabled = isDeleteEnabled,
                onClick = { onDeleteSelected() },
                activeColor = ErrorRed,
                testTag = "bottom_delete"
            )

            // 5. Deselect Button (Clears all active mesh component selections in Edit Mode)
            val isDeselectEnabled = (modelingMode == ModelingMode.EDIT && hasEditSelection)
            BottomBarButton(
                label = "Deselect",
                icon = Icons.Default.HighlightOff,
                isActive = false,
                enabled = isDeselectEnabled,
                onClick = { onClearEditSelection() },
                activeColor = AccentAmber,
                testTag = "bottom_deselect"
            )
        }
    }
}
