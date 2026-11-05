package com.example.studio3d.feature.editor.tools.modeling

import com.example.studio3d.core.engine.Mesh
import com.example.studio3d.core.engine.MeshExtruder
import com.example.studio3d.core.engine.MeshModifier
import com.example.studio3d.core.history.CommandHistory
import com.example.studio3d.core.history.MeshTopologyChangeCommand
import com.example.studio3d.core.scene.SceneGraph
import com.example.studio3d.core.scene.SceneNode
import com.example.studio3d.feature.editor.screen.SelectionMode
import com.example.studio3d.feature.editor.tools.select.EditSelectionManager
import com.example.studio3d.feature.editor.tools.select.SelectionController

/**
 * Controller mandiri untuk seluruh operasi manipulasi topologi dan geometri Mesh
 * (Extrude, Inset, Subdivide, Merge Vertices, Delete Geometry).
 */
class ModelingController(
    val insetController: InsetController = InsetController(),
    val extrudeController: ExtrudeController = ExtrudeController(),
    val subdivideController: SubdivideController = SubdivideController(),
    val bevelController: BevelController = BevelController()
) {

    fun extrudeSelectedFaces(
        node: SceneNode,
        sceneGraph: SceneGraph,
        history: CommandHistory,
        selectionController: SelectionController,
        distance: Float = 0.4f,
        onStatus: (String) -> Unit,
        onMeshChanged: () -> Unit
    ) {
        val currentMesh = node.mesh ?: return
        val editSelection = selectionController.editSelection
        val faces = editSelection.selectedFaces.toSet()

        if (faces.isEmpty()) {
            if (editSelection.selectedEdges.isNotEmpty()) {
                extrudeSelectedEdges(node, sceneGraph, history, selectionController, distance, onStatus, onMeshChanged)
                return
            }
            onStatus("Pilih face terlebih dahulu untuk di-extrude")
            return
        }

        val extrudeResult = MeshExtruder.extrudeFaces(currentMesh, faces, distance)
        if (extrudeResult == null) {
            onStatus("Extrude tidak dapat dilakukan pada face ini")
            return
        }
        val (newMesh, newCapFaces) = extrudeResult

        history.execute(
            MeshTopologyChangeCommand(
                nodeId = node.id,
                sceneGraph = sceneGraph,
                oldMesh = currentMesh,
                newMesh = newMesh,
                oldSelection = faces,
                newSelection = newCapFaces,
                editSelection = editSelection,
                oldSelectionMode = SelectionMode.FACE,
                newSelectionMode = SelectionMode.FACE,
                onSetSelectionMode = { selectionController.setSelectionMode(it) },
                description = "Extrude Face",
                onMeshChanged = onMeshChanged
            )
        )

        editSelection.clear()
        editSelection.selectedFaces.addAll(newCapFaces)
        onMeshChanged()
        onStatus("Extruded ${faces.size} face(s)")
    }

    fun extrudeSelectedEdges(
        node: SceneNode,
        sceneGraph: SceneGraph,
        history: CommandHistory,
        selectionController: SelectionController,
        distance: Float = 0.4f,
        onStatus: (String) -> Unit,
        onMeshChanged: () -> Unit
    ) {
        val currentMesh = node.mesh ?: return
        val editSelection = selectionController.editSelection
        val edges = editSelection.selectedEdges.toSet()

        if (edges.isEmpty()) {
            if (editSelection.selectedFaces.isNotEmpty()) {
                extrudeSelectedFaces(node, sceneGraph, history, selectionController, distance, onStatus, onMeshChanged)
                return
            }
            onStatus("Pilih edge terlebih dahulu untuk di-extrude")
            return
        }

        val extrudeResult = MeshExtruder.extrudeEdges(currentMesh, edges, distance)
        if (extrudeResult == null) {
            onStatus("Extrude edge tidak dapat dilakukan")
            return
        }
        val (newMesh, newCapEdges) = extrudeResult

        history.execute(
            MeshTopologyChangeCommand(
                nodeId = node.id,
                sceneGraph = sceneGraph,
                oldMesh = currentMesh,
                newMesh = newMesh,
                oldSelection = edges,
                newSelection = newCapEdges,
                editSelection = editSelection,
                oldSelectionMode = SelectionMode.EDGE,
                newSelectionMode = SelectionMode.EDGE,
                onSetSelectionMode = { selectionController.setSelectionMode(it) },
                description = "Extrude Edge",
                onMeshChanged = onMeshChanged
            )
        )

        editSelection.clear()
        editSelection.selectedEdges.addAll(newCapEdges)
        onMeshChanged()
        onStatus("Extruded ${edges.size} edge(s)")
    }

    fun subdivide(
        node: SceneNode,
        sceneGraph: SceneGraph,
        history: CommandHistory,
        selectionController: SelectionController,
        onStatus: (String) -> Unit,
        onMeshChanged: () -> Unit
    ) {
        val currentMesh = node.mesh ?: return
        val editSelection = selectionController.editSelection
        val topology = currentMesh.topology
        val currentMode = selectionController.selectionMode.value

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

        val subResult = MeshModifier.subdivideFaces(currentMesh, targetFaces)
        if (subResult == null) {
            onStatus("Subdivide gagal")
            return
        }
        val (newMesh, newFaces) = subResult

        history.execute(
            MeshTopologyChangeCommand(
                nodeId = node.id,
                sceneGraph = sceneGraph,
                oldMesh = currentMesh,
                newMesh = newMesh,
                oldSelection = targetFaces,
                newSelection = newFaces,
                editSelection = editSelection,
                oldSelectionMode = currentMode,
                newSelectionMode = if (targetFaces.isNotEmpty()) SelectionMode.FACE else currentMode,
                onSetSelectionMode = { selectionController.setSelectionMode(it) },
                description = "Subdivide",
                onMeshChanged = onMeshChanged
            )
        )

        editSelection.clear()
        if (targetFaces.isNotEmpty()) {
            editSelection.selectedFaces.addAll(newFaces)
            selectionController.setSelectionMode(SelectionMode.FACE)
        }
        onMeshChanged()
        val desc = if (targetFaces.isEmpty()) "Seluruh mesh disubdivide" else "${targetFaces.size} face(s) disubdivide"
        onStatus(desc)
    }

    fun deleteSelectedGeometry(
        node: SceneNode,
        sceneGraph: SceneGraph,
        history: CommandHistory,
        selectionController: SelectionController,
        onStatus: (String) -> Unit,
        onMeshChanged: () -> Unit
    ) {
        val currentMesh = node.mesh ?: return
        val editSelection = selectionController.editSelection
        if (!editSelection.hasSelection()) {
            onStatus("Tidak ada geometri yang dipilih untuk dihapus")
            return
        }

        val facesToDelete = HashSet<Int>()
        val vertsToDelete = HashSet<Int>()
        val edgesToDelete = HashSet<Pair<Int, Int>>()
        val mode = selectionController.selectionMode.value

        when (mode) {
            SelectionMode.FACE -> facesToDelete.addAll(editSelection.selectedFaces)
            SelectionMode.VERTEX -> vertsToDelete.addAll(editSelection.selectedVertices)
            SelectionMode.EDGE -> {
                val topology = currentMesh.topology
                for (edgeIdx in editSelection.selectedEdges) {
                    if (edgeIdx in 0 until topology.edges.size) {
                        edgesToDelete.add(topology.edges[edgeIdx])
                    }
                }
            }
        }

        val newMesh = MeshModifier.deleteGeometry(
            currentMesh,
            facesToDelete = facesToDelete,
            verticesToDelete = vertsToDelete,
            edgesToDelete = edgesToDelete
        )

        if (newMesh == null) {
            onStatus("Tidak dapat menghapus seluruh mesh")
            return
        }

        val oldSel = when (mode) {
            SelectionMode.FACE -> editSelection.selectedFaces.toSet()
            SelectionMode.VERTEX -> editSelection.selectedVertices.toSet()
            SelectionMode.EDGE -> editSelection.selectedEdges.toSet()
        }

        history.execute(
            MeshTopologyChangeCommand(
                nodeId = node.id,
                sceneGraph = sceneGraph,
                oldMesh = currentMesh,
                newMesh = newMesh,
                oldSelection = oldSel,
                newSelection = emptySet(),
                editSelection = editSelection,
                oldSelectionMode = mode,
                newSelectionMode = mode,
                onSetSelectionMode = { selectionController.setSelectionMode(it) },
                description = "Delete Geometry",
                onMeshChanged = onMeshChanged
            )
        )

        editSelection.clear()
        onMeshChanged()
        onStatus("Geometri berhasil dihapus")
    }

    fun mergeSelectedVertices(
        node: SceneNode,
        sceneGraph: SceneGraph,
        history: CommandHistory,
        selectionController: SelectionController,
        onStatus: (String) -> Unit,
        onMeshChanged: () -> Unit
    ) {
        val currentMesh = node.mesh ?: return
        val editSelection = selectionController.editSelection
        val topology = currentMesh.topology
        val targetVerts = HashSet<Int>()
        val mode = selectionController.selectionMode.value

        when (mode) {
            SelectionMode.VERTEX -> targetVerts.addAll(editSelection.selectedVertices)
            SelectionMode.EDGE -> {
                for (edgeIdx in editSelection.selectedEdges) {
                    if (edgeIdx in 0 until topology.edges.size) {
                        val edge = topology.edges[edgeIdx]
                        targetVerts.add(edge.first)
                        targetVerts.add(edge.second)
                    }
                }
            }
            SelectionMode.FACE -> {
                for (f in editSelection.selectedFaces) {
                    if (f in 0 until topology.faceCount) {
                        val (i0, i1, i2) = topology.getFaceIndices(f)
                        targetVerts.add(i0)
                        targetVerts.add(i1)
                        targetVerts.add(i2)
                    }
                }
            }
        }

        if (targetVerts.size < 2) {
            onStatus("Pilih minimal 2 titik untuk di-Merge")
            return
        }

        val mergeResult = MeshModifier.mergeVertices(currentMesh, targetVerts)
        if (mergeResult == null) {
            onStatus("Merge gagal")
            return
        }
        val (newMesh, mergedIdx) = mergeResult

        history.execute(
            MeshTopologyChangeCommand(
                nodeId = node.id,
                sceneGraph = sceneGraph,
                oldMesh = currentMesh,
                newMesh = newMesh,
                oldSelection = targetVerts,
                newSelection = setOf(mergedIdx),
                editSelection = editSelection,
                oldSelectionMode = mode,
                newSelectionMode = SelectionMode.VERTEX,
                onSetSelectionMode = { selectionController.setSelectionMode(it) },
                description = "Merge Vertices",
                onMeshChanged = onMeshChanged
            )
        )

        editSelection.clear()
        selectionController.setSelectionMode(SelectionMode.VERTEX)
        editSelection.selectedVertices.add(mergedIdx)
        onMeshChanged()
        onStatus("Berhasil di-Merge ke titik tengah")
    }

    fun startInteractiveBevel(
        node: SceneNode,
        selectionController: SelectionController,
        defaultOffset: Float = 0.10f,
        defaultSegments: Int = 1,
        defaultProfile: Float = 0.50f,
        onStatus: (String) -> Unit,
        onMeshChanged: () -> Unit
    ) {
        val editSelection = selectionController.editSelection
        val mode = selectionController.selectionMode.value
        val isFaceSel = mode == SelectionMode.FACE && editSelection.selectedFaces.isNotEmpty()
        val selection = when (mode) {
            SelectionMode.FACE -> editSelection.selectedFaces.toSet()
            else -> editSelection.selectedEdges.toSet()
        }

        val result = bevelController.startInteractiveBevel(
            node = node,
            mode = BevelMode.EDGE,
            isFaceSel = isFaceSel,
            selection = selection,
            defaultOffset = defaultOffset,
            defaultSegments = defaultSegments,
            defaultProfile = defaultProfile
        )

        if (result == null) {
            onStatus("Bevel tidak dapat dilakukan pada geometri ini")
            return
        }

        val (newMesh, newFaces) = result
        editSelection.clear()
        selectionController.setSelectionMode(SelectionMode.FACE)
        editSelection.selectedFaces.addAll(newFaces)
        onMeshChanged()
        onStatus("Bevel aktif: geser slider untuk mengatur")
    }

    fun bevel(
        node: SceneNode,
        sceneGraph: SceneGraph,
        history: CommandHistory,
        selectionController: SelectionController,
        offset: Float = 0.12f,
        depth: Float = 0.0f,
        segments: Int = 1,
        onStatus: (String) -> Unit,
        onMeshChanged: () -> Unit
    ) {
        val currentMesh = node.mesh ?: return
        val editSelection = selectionController.editSelection
        val mode = selectionController.selectionMode.value

        val bevelResult = if (mode == SelectionMode.EDGE && editSelection.selectedEdges.isNotEmpty()) {
            com.example.studio3d.core.engine.MeshBeveler.bevelEdges(currentMesh, editSelection.selectedEdges, offset, segments)
        } else if (editSelection.selectedFaces.isNotEmpty()) {
            com.example.studio3d.core.engine.MeshBeveler.bevelFaces(currentMesh, editSelection.selectedFaces, offset, depth, segments)
        } else if (editSelection.selectedEdges.isNotEmpty()) {
            com.example.studio3d.core.engine.MeshBeveler.bevelEdges(currentMesh, editSelection.selectedEdges, offset, segments)
        } else {
            com.example.studio3d.core.engine.MeshBeveler.bevelEdges(currentMesh, emptySet(), offset, segments)
        }

        if (bevelResult == null) {
            onStatus("Bevel tidak dapat dilakukan pada geometri ini")
            return
        }
        val (newMesh, newFaces) = bevelResult

        history.execute(
            MeshTopologyChangeCommand(
                nodeId = node.id,
                sceneGraph = sceneGraph,
                oldMesh = currentMesh,
                newMesh = newMesh,
                oldSelection = editSelection.selectedFaces.toSet(),
                newSelection = newFaces,
                editSelection = editSelection,
                oldSelectionMode = mode,
                newSelectionMode = SelectionMode.FACE,
                onSetSelectionMode = { selectionController.setSelectionMode(it) },
                description = "Bevel Geometry",
                onMeshChanged = onMeshChanged
            )
        )

        editSelection.clear()
        selectionController.setSelectionMode(SelectionMode.FACE)
        editSelection.selectedFaces.addAll(newFaces)
        onMeshChanged()
        onStatus("Bevel selesai (${newFaces.size} face baru)")
    }

    fun loopCut(
        node: SceneNode,
        sceneGraph: SceneGraph,
        history: CommandHistory,
        selectionController: SelectionController,
        factor: Float = 0.5f,
        onStatus: (String) -> Unit,
        onMeshChanged: () -> Unit
    ) {
        val currentMesh = node.mesh ?: return
        val editSelection = selectionController.editSelection
        val cutResult = com.example.studio3d.core.engine.MeshLoopCutter.loopCut(currentMesh, editSelection.selectedEdges, factor)
        if (cutResult == null) {
            onStatus("Loop Cut tidak dapat dilakukan")
            return
        }
        val (newMesh, newEdges) = cutResult

        history.execute(
            MeshTopologyChangeCommand(
                nodeId = node.id,
                sceneGraph = sceneGraph,
                oldMesh = currentMesh,
                newMesh = newMesh,
                oldSelection = editSelection.selectedEdges.toSet(),
                newSelection = newEdges,
                editSelection = editSelection,
                oldSelectionMode = selectionController.selectionMode.value,
                newSelectionMode = SelectionMode.EDGE,
                onSetSelectionMode = { selectionController.setSelectionMode(it) },
                description = "Loop Cut",
                onMeshChanged = onMeshChanged
            )
        )

        editSelection.clear()
        selectionController.setSelectionMode(SelectionMode.EDGE)
        editSelection.selectedEdges.addAll(newEdges)
        onMeshChanged()
        onStatus("Loop Cut berhasil dibuat (${newEdges.size} edge baru)")
    }

    fun bridge(
        node: SceneNode,
        sceneGraph: SceneGraph,
        history: CommandHistory,
        selectionController: SelectionController,
        onStatus: (String) -> Unit,
        onMeshChanged: () -> Unit
    ) {
        val currentMesh = node.mesh ?: return
        val editSelection = selectionController.editSelection
        if (editSelection.selectedFaces.size < 2) {
            onStatus("Pilih minimal 2 face untuk di-Bridge")
            return
        }

        val bridgeResult = com.example.studio3d.core.engine.MeshBridger.bridgeFaces(currentMesh, editSelection.selectedFaces)
        if (bridgeResult == null) {
            onStatus("Bridge gagal dibuat")
            return
        }
        val (newMesh, newFaces) = bridgeResult

        history.execute(
            MeshTopologyChangeCommand(
                nodeId = node.id,
                sceneGraph = sceneGraph,
                oldMesh = currentMesh,
                newMesh = newMesh,
                oldSelection = editSelection.selectedFaces.toSet(),
                newSelection = newFaces,
                editSelection = editSelection,
                oldSelectionMode = SelectionMode.FACE,
                newSelectionMode = SelectionMode.FACE,
                onSetSelectionMode = { selectionController.setSelectionMode(it) },
                description = "Bridge Faces",
                onMeshChanged = onMeshChanged
            )
        )

        editSelection.clear()
        selectionController.setSelectionMode(SelectionMode.FACE)
        editSelection.selectedFaces.addAll(newFaces)
        onMeshChanged()
        onStatus("Bridge berhasil dibuat (${newFaces.size} face)")
    }
}
