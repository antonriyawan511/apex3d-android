package com.example.studio3d.feature.editor.tools.select

import com.example.studio3d.core.scene.SceneNode
import com.example.studio3d.feature.editor.screen.ModelingMode
import com.example.studio3d.feature.editor.screen.SelectionMode
import com.example.studio3d.feature.editor.tools.transform.TransformController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controller mandiri untuk mengelola status seleksi komponen geometri (Vertex, Edge, Face)
 * dan transisi mode modeling (Object Mode vs Edit Mode).
 */
class SelectionController(
    val editSelection: EditSelectionManager
) {
    private val _modelingMode = MutableStateFlow(ModelingMode.OBJECT)
    val modelingMode: StateFlow<ModelingMode> = _modelingMode.asStateFlow()

    private val _selectionMode = MutableStateFlow(SelectionMode.VERTEX)
    val selectionMode: StateFlow<SelectionMode> = _selectionMode.asStateFlow()

    private val _hasEditSelection = MutableStateFlow(false)
    val hasEditSelection: StateFlow<Boolean> = _hasEditSelection.asStateFlow()

    private val _selectedFacesCount = MutableStateFlow(0)
    val selectedFacesCount: StateFlow<Int> = _selectedFacesCount.asStateFlow()

    private val _selectedVerticesCount = MutableStateFlow(0)
    val selectedVerticesCount: StateFlow<Int> = _selectedVerticesCount.asStateFlow()

    private val _selectedEdgesCount = MutableStateFlow(0)
    val selectedEdgesCount: StateFlow<Int> = _selectedEdgesCount.asStateFlow()

    private val _editSelectionStatus = MutableStateFlow<String?>(null)
    val editSelectionStatus: StateFlow<String?> = _editSelectionStatus.asStateFlow()

    fun setModelingMode(mode: ModelingMode) {
        _modelingMode.value = mode
    }

    fun toggleModelingMode() {
        _modelingMode.value = if (_modelingMode.value == ModelingMode.OBJECT) {
            ModelingMode.EDIT
        } else {
            ModelingMode.OBJECT
        }
    }

    fun setSelectionMode(mode: SelectionMode) {
        _selectionMode.value = mode
    }

    fun toggleSelectionMode() {
        val next = when (_selectionMode.value) {
            SelectionMode.VERTEX -> SelectionMode.EDGE
            SelectionMode.EDGE -> SelectionMode.FACE
            SelectionMode.FACE -> SelectionMode.VERTEX
        }
        _selectionMode.value = next
    }

    fun clear() {
        editSelection.clear()
    }

    /**
     * Memperbarui hitungan elemen seleksi spasial / coplanar.
     */
    fun updateSelectionCounts(selectedNode: SceneNode?) {
        _hasEditSelection.value = editSelection.hasSelection()

        val topology = selectedNode?.mesh?.topology
        val verticesSnapshot = editSelection.getVerticesSnapshot()
        val facesSnapshot = editSelection.getFacesSnapshot()
        val edgesSnapshot = editSelection.getEdgesSnapshot()

        val uniqueVerticesCount = if (topology != null && verticesSnapshot.isNotEmpty()) {
            verticesSnapshot.map { topology.coincidentMap[it] }.toSet().size
        } else {
            verticesSnapshot.size
        }

        val uniqueFacesCount = if (topology != null && facesSnapshot.isNotEmpty()) {
            val processed = HashSet<Int>()
            var count = 0
            for (f in facesSnapshot) {
                if (processed.add(f)) {
                    val coplanar = topology.getCoplanarConnectedFaces(f)
                    processed.addAll(coplanar)
                    count++
                }
            }
            count
        } else {
            facesSnapshot.size
        }

        _selectedVerticesCount.value = uniqueVerticesCount
        _selectedEdgesCount.value = edgesSnapshot.size
        _selectedFacesCount.value = uniqueFacesCount

        _editSelectionStatus.value = if (_modelingMode.value == ModelingMode.EDIT && editSelection.hasSelection()) {
            when (_selectionMode.value) {
                SelectionMode.FACE -> {
                    val count = facesSnapshot.size
                    if (count == 1) "1 Face Selected" else "$count Faces Selected"
                }
                SelectionMode.EDGE -> {
                    val count = edgesSnapshot.size
                    if (count == 1) "1 Edge Selected" else "$count Edges Selected"
                }
                SelectionMode.VERTEX -> {
                    val count = uniqueVerticesCount
                    if (count == 1) "1 Vertex Selected" else "$count Vertices Selected"
                }
            }
        } else {
            null
        }
    }

    /**
     * Membuat proxy SceneNode untuk manipulasi angka di Inspector saat komponen geometri dipilih.
     */
    fun getEditSelectionProxyNode(
        selectedNode: SceneNode?,
        transformController: TransformController
    ): SceneNode? {
        val selected = selectedNode ?: return null
        val mesh = selected.mesh ?: return null
        if (!editSelection.hasSelection()) return null

        val (pos, rot, scale) = transformController.getEffectivePivotAndTransform(
            node = selected,
            editSelection = editSelection,
            isEditMode = _modelingMode.value == ModelingMode.EDIT
        )

        val name = when (_selectionMode.value) {
            SelectionMode.FACE -> {
                val count = editSelection.selectedFaces.size
                if (count == 1) "1 Face Selected" else "$count Faces Selected"
            }
            SelectionMode.EDGE -> {
                val count = editSelection.selectedEdges.size
                if (count == 1) "1 Edge Selected" else "$count Edges Selected"
            }
            SelectionMode.VERTEX -> {
                val count = editSelection.selectedVertices.size
                if (count == 1) "1 Vertex Selected" else "$count Vertices Selected"
            }
        }

        return SceneNode(
            id = "edit_selection_proxy",
            name = name,
            meshType = _selectionMode.value.name.lowercase(),
            position = pos,
            rotation = rot,
            scale = scale
        )
    }
}
