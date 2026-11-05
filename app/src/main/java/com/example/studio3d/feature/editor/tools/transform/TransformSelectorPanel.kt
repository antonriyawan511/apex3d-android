package com.example.studio3d.feature.editor.tools.transform

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


import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.studio3d.feature.editor.viewport.TransformMode
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCyan

typealias SelectorItemData = PopupMenuItem

@Composable
fun TransformSelectorPanel(
    isOpen: Boolean,
    hoveredIndex: Int? = null,
    transformMode: TransformMode,
    isSnapping: Boolean,
    onSetTransformMode: (TransformMode) -> Unit,
    onToggleSnapping: () -> Unit,
    onDismiss: () -> Unit,
    onPositionItem: (Int, Float, Float) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val items = listOf(
        PopupMenuItem("tool_snap", if (isSnapping) "Snap ON" else "Snap OFF", Icons.Default.GridOn, isActive = isSnapping, activeColor = AccentAmber) {
            onToggleSnapping()
        },
        PopupMenuItem("tool_scale", "Scale", Icons.Default.ZoomOutMap, isActive = transformMode == TransformMode.SCALE) {
            onSetTransformMode(TransformMode.SCALE)
        },
        PopupMenuItem("tool_rotate", "Rotate", Icons.Default.Refresh, isActive = transformMode == TransformMode.ROTATE) {
            onSetTransformMode(TransformMode.ROTATE)
        },
        PopupMenuItem("tool_move", "Move", Icons.Default.OpenWith, isActive = transformMode == TransformMode.TRANSLATE) {
            onSetTransformMode(TransformMode.TRANSLATE)
        },
        PopupMenuItem("tool_select", "Select", Icons.Default.NearMe, isActive = transformMode == TransformMode.SELECT) {
            onSetTransformMode(TransformMode.SELECT)
        }
    )

    StudioPopupMenu(
        isOpen = isOpen,
        title = "Transform Tools",
        items = items,
        hoveredIndex = hoveredIndex,
        onDismiss = onDismiss,
        onPositionItem = onPositionItem,
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = 64.dp, bottom = 92.dp),
        testTag = "transform_selector_container"
    )
}

@Composable
fun AddMeshSelectorPanel(
    isOpen: Boolean,
    hoveredIndex: Int? = null,
    onAddPrimitive: (String) -> Unit,
    onDismiss: () -> Unit,
    onPositionItem: (Int, Float, Float) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val items = listOf(
        PopupMenuItem("import_obj", "Import .OBJ/.GLB", Icons.Default.FolderOpen, activeColor = AccentCyan) {
            onAddPrimitive("import_obj")
        },
        PopupMenuItem("add_cube", "Cube", Icons.Default.ViewInAr) {
            onAddPrimitive("cube")
        },
        PopupMenuItem("add_plane", "Plane", Icons.Default.CropSquare) {
            onAddPrimitive("plane")
        },
        PopupMenuItem("add_cylinder", "Cylinder", Icons.Default.ViewInAr) {
            onAddPrimitive("cylinder")
        },
        PopupMenuItem("add_cone", "Cone", Icons.Default.ViewInAr) {
            onAddPrimitive("cone")
        },
        PopupMenuItem("add_sphere", "Sphere", Icons.Default.FiberManualRecord) {
            onAddPrimitive("sphere")
        },
        PopupMenuItem("add_torus", "Torus", Icons.Default.Circle) {
            onAddPrimitive("torus")
        },
        PopupMenuItem("add_plate", "Dining Plate", Icons.Default.Circle) {
            onAddPrimitive("plate")
        },
        PopupMenuItem("add_wedge", "Wedge", Icons.Default.ViewInAr) {
            onAddPrimitive("wedge")
        }
    )

    StudioPopupMenu(
        isOpen = isOpen,
        title = "Add Mesh Primitives",
        items = items,
        hoveredIndex = hoveredIndex,
        onDismiss = onDismiss,
        onPositionItem = onPositionItem,
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = 180.dp, bottom = 52.dp),
        testTag = "add_mesh_selector_container"
    )
}

@Composable
fun PanelsSelectorPanel(
    isOpen: Boolean,
    hoveredIndex: Int? = null,
    onOpenOutliner: () -> Unit,
    onOpenInspector: () -> Unit,
    onOpenProjects: () -> Unit,
    onDismiss: () -> Unit,
    onPositionItem: (Int, Float, Float) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val items = listOf(
        PopupMenuItem("cat_project", "Project Manager", Icons.Default.Folder, activeColor = AccentCyan) {
            onOpenProjects()
        },
        PopupMenuItem("cat_inspector", "Inspector", Icons.Default.Tune, activeColor = AccentAmber) {
            onOpenInspector()
        },
        PopupMenuItem("cat_outliner", "Outliner", Icons.Default.Widgets, activeColor = AccentCyan) {
            onOpenOutliner()
        }
    )

    StudioPopupMenu(
        isOpen = isOpen,
        title = "Workspace Panels",
        items = items,
        hoveredIndex = hoveredIndex,
        onDismiss = onDismiss,
        onPositionItem = onPositionItem,
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = 12.dp, bottom = 52.dp),
        testTag = "panels_selector_container"
    )
}

@Composable
fun VerticalSelectorPanel(
    isOpen: Boolean,
    title: String,
    items: List<PopupMenuItem>,
    hoveredIndex: Int? = null,
    onDismiss: () -> Unit,
    onPositionItem: (Int, Float, Float) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
    testTag: String = "vertical_selector_container"
) = StudioPopupMenu(
    isOpen = isOpen,
    title = title,
    items = items,
    onDismiss = onDismiss,
    modifier = modifier,
    hoveredIndex = hoveredIndex,
    onPositionItem = onPositionItem,
    testTag = testTag
)
