package com.example.studio3d.feature.editor.panel

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


import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GroupWork
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.studio3d.core.scene.SceneNode
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.SelectionHighlight
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated

@Composable
fun OutlinerSheet(
    nodes: List<SceneNode>,
    selectedNodeId: String?,
    onSelectNode: (String) -> Unit,
    onToggleVisibility: (String) -> Unit,
    onDeleteSelected: () -> Unit,
    onDuplicateSelected: () -> Unit,
    onGroupSelected: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(310.dp)
            .heightIn(max = 420.dp)
            .testTag("outliner_sheet"),
        color = StudioSurface.copy(alpha = 0.70f),
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 10.dp,
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(StudioBorder))
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .wrapContentHeight()
        ) {
            // 1. Tree list of nodes (di atas, mengembang ke atas sesuai jumlah item)
            val rootNodes = nodes.filter { it.parentId == null }

            LazyColumn(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(rootNodes) { root ->
                    NodeTreeItem(
                        node = root,
                        allNodes = nodes,
                        selectedNodeId = selectedNodeId,
                        depth = 0,
                        onSelectNode = onSelectNode,
                        onToggleVisibility = onToggleVisibility
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Action Toolbar (Duplicate, Group, Delete)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilledTonalButton(
                    onClick = onDuplicateSelected,
                    enabled = selectedNodeId != null,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("outliner_duplicate_button"),
                    contentPadding = ButtonDefaults.ContentPadding
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", modifier = Modifier.size(16.dp))
                }
                FilledTonalButton(
                    onClick = onGroupSelected,
                    enabled = selectedNodeId != null,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("outliner_group_button"),
                    contentPadding = ButtonDefaults.ContentPadding
                ) {
                    Icon(Icons.Default.GroupWork, contentDescription = "Group", modifier = Modifier.size(16.dp))
                }
                FilledTonalButton(
                    onClick = onDeleteSelected,
                    enabled = selectedNodeId != null,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF4A1A1A),
                        contentColor = Color(0xFFFF6B6B)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("outliner_delete_button"),
                    contentPadding = ButtonDefaults.ContentPadding
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Title & Exit/Close Button di baris paling bawah agar sangat mudah dijangkau jempol
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Widgets,
                        contentDescription = "Outliner Icon",
                        tint = AccentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Scene Hierarchy",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("close_outliner_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun NodeTreeItem(
    node: SceneNode,
    allNodes: List<SceneNode>,
    selectedNodeId: String?,
    depth: Int,
    onSelectNode: (String) -> Unit,
    onToggleVisibility: (String) -> Unit
) {
    val isSelected = (node.id == selectedNodeId)
    val isGroup = (node.meshType == "group")
    val childNodes = allNodes.filter { it.parentId == node.id }

    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (depth * 14).dp)
                .clickable { onSelectNode(node.id) }
                .testTag("outliner_node_${node.id}"),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isSelected -> SelectionHighlight.copy(alpha = 0.30f)
                    isGroup -> StudioSurfaceElevated
                    else -> Color(0xFF181C28)
                }
            ),
            shape = RoundedCornerShape(6.dp),
            border = if (isSelected) CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SelectionHighlight)) else null
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isGroup) Icons.Default.Folder else Icons.Default.Widgets,
                        contentDescription = "Node Type",
                        tint = if (isGroup) AccentCyan else Color(0xFF90A4AE),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = node.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) Color.White else Color(0xFFDCE2EC),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1
                    )
                }

                // Eye visibility toggle
                IconButton(
                    onClick = { onToggleVisibility(node.id) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (node.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Toggle Visibility",
                        tint = if (node.isVisible) Color(0xFF64B5F6) else Color.DarkGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Render children indented
        for (child in childNodes) {
            NodeTreeItem(
                node = child,
                allNodes = allNodes,
                selectedNodeId = selectedNodeId,
                depth = depth + 1,
                onSelectNode = onSelectNode,
                onToggleVisibility = onToggleVisibility
            )
        }
    }
}
