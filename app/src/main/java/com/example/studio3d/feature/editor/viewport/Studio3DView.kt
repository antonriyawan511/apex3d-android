package com.example.studio3d.feature.editor.viewport

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


import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.example.studio3d.core.history.CommandHistory
import com.example.studio3d.core.history.TransformNodeCommand
import com.example.studio3d.core.history.TransformVerticesCommand
import com.example.studio3d.core.math.Matrix4
import com.example.studio3d.core.math.Vector3
import com.example.studio3d.core.scene.SceneGraph
import com.example.studio3d.feature.editor.screen.ModelingMode
import com.example.studio3d.feature.editor.screen.SelectionMode
import kotlin.math.abs
import kotlin.math.atan2

class Studio3DView(
    context: Context,
    val sceneGraph: SceneGraph,
    val camera: Camera,
    val gizmo: Gizmo,
    val history: CommandHistory,
    val onSelectionChanged: (String?) -> Unit = {},
    val onTransformUpdated: () -> Unit = {},
    val onCameraMoved: () -> Unit = {},
    val onCameraPresetChanged: (CameraPreset?) -> Unit = {}
) : GLSurfaceView(context) {

    val renderer: StudioRenderer
    var gestureSensitivity = GestureSensitivity()
    var modelingMode: ModelingMode = ModelingMode.OBJECT
    var selectionMode: SelectionMode = SelectionMode.VERTEX
    var editSelection: EditSelectionManager? = null
    var onEditSelectionChanged: (() -> Unit)? = null

    private enum class TouchGestureMode {
        NONE,
        GIZMO,
        SINGLE_ORBIT,
        MULTI_TOUCH
    }

    private var touchGestureMode = TouchGestureMode.NONE
    private var previousX = 0f
    private var previousY = 0f
    private var previousPanX = 0f
    private var previousPanY = 0f
    private var multiTouchStartMidX = 0f
    private var multiTouchStartMidY = 0f
    private var isMultiPanActive = false
    private var previousAngleDeg = 0f
    private var multiTouchStartAngleDeg = 0f
    private var isMultiTwistActive = false
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var isGizmoInteracting = false
    private var hasMovedSignificantly = false

    // Record initial transform state before dragging for Command undo/redo
    private var dragStartPos = Vector3()
    private var dragStartRot = Vector3()
    private var dragStartScale = Vector3()

    // Vertex transform dragging session cache
    private var isVertexTransformDrag = false
    private var vertexDragInitialPositions: Map<Int, Vector3> = emptyMap()
    private var vertexDragLocalPivot = Vector3.ZERO

    private val scaleDetector: ScaleGestureDetector

    init {
        setEGLContextClientVersion(3)
        renderer = StudioRenderer(context, sceneGraph, camera, gizmo)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY

        scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (!isGizmoInteracting && touchGestureMode == TouchGestureMode.MULTI_TOUCH) {
                    val rawDelta = detector.scaleFactor - 1.0f
                    val adjustedScale = 1.0f + rawDelta * gestureSensitivity.zoom
                    camera.zoom(adjustedScale)
                    requestRender()
                }
                return true
            }
        }).apply {
            // Disable Android's default QuickScale (double-tap & drag with 1 finger)
            // which causes rapid successive 1-finger swipes to accidentally zoom in/out instead of orbiting!
            isQuickScaleEnabled = false
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                isStylusScaleEnabled = false
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchGestureMode = TouchGestureMode.NONE
                previousX = x
                previousY = y
                touchDownX = x
                touchDownY = y
                hasMovedSignificantly = false
                isGizmoInteracting = false
                previousPanX = 0f
                previousPanY = 0f
                multiTouchStartMidX = 0f
                multiTouchStartMidY = 0f
                isMultiPanActive = false
                previousAngleDeg = 0f
                multiTouchStartAngleDeg = 0f
                isMultiTwistActive = false

                val selectedNode = sceneGraph.getSelectedNode()
                val ray = camera.screenPointToRay(x, y)

                val isComponentEditMode = (modelingMode == ModelingMode.EDIT &&
                    (editSelection?.hasSelection(selectionMode) == true || editSelection?.hasSelection() == true) &&
                    selectedNode?.mesh != null)

                val gizmoPivot = if (isComponentEditMode && selectedNode != null) {
                    val activeMode = if (editSelection!!.hasSelection(selectionMode)) selectionMode else null
                    val localPivot = editSelection!!.calculateLocalPivot(selectedNode.mesh!!, activeMode)
                    selectedNode.getWorldTransformMatrix(sceneGraph.nodes).transformPoint(localPivot)
                } else {
                    selectedNode?.position ?: Vector3.ZERO
                }

                // 1. First priority: Check if user tapped a Gizmo handle
                if (selectedNode != null && gizmo.mode != TransformMode.SELECT) {
                    val hitAxis = gizmo.hitTest(ray, gizmoPivot, camera)
                    if (hitAxis != GizmoAxis.NONE) {
                        touchGestureMode = TouchGestureMode.GIZMO
                        isGizmoInteracting = true
                        isVertexTransformDrag = isComponentEditMode
                        dragStartPos = gizmoPivot.copy()
                        dragStartRot = if (isComponentEditMode) Vector3.ZERO else selectedNode.rotation.copy()
                        dragStartScale = if (isComponentEditMode) Vector3(1f, 1f, 1f) else selectedNode.scale.copy()
                        if (isComponentEditMode) {
                            val mesh = selectedNode.mesh!!
                            val activeMode = if (editSelection!!.hasSelection(selectionMode)) selectionMode else null
                            vertexDragLocalPivot = editSelection!!.calculateLocalPivot(mesh, activeMode)
                            val effectiveVertices = editSelection!!.getEffectiveSelectedVertices(mesh, activeMode)
                            vertexDragInitialPositions = effectiveVertices.associateWith { mesh.getVertex(it) }
                        }
                        gizmo.startDrag(hitAxis, ray, gizmoPivot, dragStartRot, dragStartScale, camera, x, y)
                        requestRender()
                        return true
                    }
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (touchGestureMode != TouchGestureMode.GIZMO) {
                    touchGestureMode = TouchGestureMode.MULTI_TOUCH
                    if (event.pointerCount >= 2) {
                        val midX = (event.getX(0) + event.getX(1)) / 2f
                        val midY = (event.getY(0) + event.getY(1)) / 2f
                        previousPanX = midX
                        previousPanY = midY
                        multiTouchStartMidX = midX
                        multiTouchStartMidY = midY
                        isMultiPanActive = false

                        val pDx = event.getX(1) - event.getX(0)
                        val pDy = event.getY(1) - event.getY(0)
                        val angle = Math.toDegrees(atan2(pDy.toDouble(), pDx.toDouble())).toFloat()
                        previousAngleDeg = angle
                        multiTouchStartAngleDeg = angle
                        isMultiTwistActive = false
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val moveDist = kotlin.math.hypot((x - touchDownX).toDouble(), (y - touchDownY).toDouble()).toFloat()
                if (moveDist > 12f) {
                    hasMovedSignificantly = true
                }

                when (touchGestureMode) {
                    TouchGestureMode.GIZMO -> {
                        if (isGizmoInteracting) {
                            val selectedNode = sceneGraph.getSelectedNode()
                            if (selectedNode != null) {
                                val currentRay = camera.screenPointToRay(x, y)
                                val (newPos, newRot, newScale) = gizmo.updateDrag(currentRay, camera, x, y)

                                if (isVertexTransformDrag && selectedNode.mesh != null) {
                                    val mesh = selectedNode.mesh!!
                                    val worldMatrix = selectedNode.getWorldTransformMatrix(sceneGraph.nodes)
                                    val invWorld = Matrix4().copyFrom(worldMatrix).invert()

                                    // Transform delta translation into local mesh coordinate space
                                    val worldDelta = newPos - dragStartPos
                                    val localDelta = invWorld.transformVector(worldDelta)

                                    val deltaRot = newRot - dragStartRot
                                    val scaleRatio = Vector3(
                                        if (dragStartScale.x != 0f) newScale.x / dragStartScale.x else 1f,
                                        if (dragStartScale.y != 0f) newScale.y / dragStartScale.y else 1f,
                                        if (dragStartScale.z != 0f) newScale.z / dragStartScale.z else 1f
                                    )

                                    editSelection?.applyTransform(
                                        mesh = mesh,
                                        initialPositions = vertexDragInitialPositions,
                                        localPivot = vertexDragLocalPivot,
                                        deltaTranslation = localDelta,
                                        deltaRotationEuler = deltaRot,
                                        scaleFactor = scaleRatio
                                    )
                                    mesh.recalculateNormalsAndBounds()
                                    onTransformUpdated()
                                    requestRender()
                                } else {
                                    selectedNode.position = newPos
                                    selectedNode.rotation = newRot
                                    selectedNode.scale = newScale
                                    onTransformUpdated()
                                    requestRender()
                                }
                            }
                        }
                    }
                    TouchGestureMode.MULTI_TOUCH -> {
                        if (event.pointerCount >= 2) {
                            val midX = (event.getX(0) + event.getX(1)) / 2f
                            val midY = (event.getY(0) + event.getY(1)) / 2f

                            // 1. Two-finger Pan (Continuous per-frame once activated)
                            if (!isMultiPanActive) {
                                val panDist = kotlin.math.hypot((midX - multiTouchStartMidX).toDouble(), (midY - multiTouchStartMidY).toDouble()).toFloat()
                                if (panDist > 12f) {
                                    isMultiPanActive = true
                                    previousPanX = midX
                                    previousPanY = midY
                                }
                            } else {
                                val panDx = (midX - previousPanX) * gestureSensitivity.pan
                                val panDy = (midY - previousPanY) * gestureSensitivity.pan
                                camera.pan(panDx, panDy)
                                previousPanX = midX
                                previousPanY = midY
                                onCameraMoved()
                            }

                            // 2. Two-finger Twist (Continuous per-frame once activated)
                            val pDx = event.getX(1) - event.getX(0)
                            val pDy = event.getY(1) - event.getY(0)
                            val currentAngleDeg = Math.toDegrees(atan2(pDy.toDouble(), pDx.toDouble())).toFloat()

                            if (!isMultiTwistActive) {
                                var totalAngleDiff = currentAngleDeg - multiTouchStartAngleDeg
                                if (totalAngleDiff > 180f) totalAngleDiff -= 360f
                                else if (totalAngleDiff < -180f) totalAngleDiff += 360f

                                if (abs(totalAngleDiff) > 2.5f) {
                                    isMultiTwistActive = true
                                    previousAngleDeg = currentAngleDeg
                                }
                            } else {
                                var deltaAngle = currentAngleDeg - previousAngleDeg
                                if (deltaAngle > 180f) deltaAngle -= 360f
                                else if (deltaAngle < -180f) deltaAngle += 360f

                                camera.orbit(deltaAngle * gestureSensitivity.twistOrbit, 0f)
                                previousAngleDeg = currentAngleDeg
                                onCameraMoved()
                            }

                            requestRender()
                        }
                    }
                    TouchGestureMode.NONE, TouchGestureMode.SINGLE_ORBIT -> {
                        if (event.pointerCount == 1 && !scaleDetector.isInProgress) {
                            if (hasMovedSignificantly) {
                                touchGestureMode = TouchGestureMode.SINGLE_ORBIT
                                val dx = x - previousX
                                val dy = y - previousY
                                val wasOrtho = camera.isOrthographic
                                val orbitSpeed = 0.35f * gestureSensitivity.orbit
                                camera.orbit(-dx * orbitSpeed, dy * orbitSpeed)
                                if (wasOrtho && !camera.isOrthographic) {
                                    onCameraPresetChanged(null)
                                } else {
                                    onCameraMoved()
                                }
                                requestRender()
                            }
                            previousX = x
                            previousY = y
                        }
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (touchGestureMode == TouchGestureMode.MULTI_TOUCH) {
                    // One finger was lifted from multi-touch gesture.
                    // Keep MULTI_TOUCH state so remaining finger never triggers accidental single-finger orbit!
                    isMultiPanActive = false
                    isMultiTwistActive = false
                    previousPanX = 0f
                    previousPanY = 0f
                }
            }

            MotionEvent.ACTION_UP -> {
                val finishedGesture = touchGestureMode
                val wasGizmoDrag = (finishedGesture == TouchGestureMode.GIZMO && isGizmoInteracting && hasMovedSignificantly)
                touchGestureMode = TouchGestureMode.NONE
                previousPanX = 0f
                previousPanY = 0f
                isMultiPanActive = false
                isMultiTwistActive = false

                if (wasGizmoDrag) {
                    val selectedNode = sceneGraph.getSelectedNode()
                    if (selectedNode != null) {
                        if (isVertexTransformDrag && selectedNode.mesh != null) {
                            val mesh = selectedNode.mesh!!
                            val endPositions = vertexDragInitialPositions.keys.associateWith { mesh.getVertex(it) }
                            if (vertexDragInitialPositions != endPositions) {
                                history.recordExecuted(
                                    TransformVerticesCommand(
                                        nodeId = selectedNode.id,
                                        sceneGraph = sceneGraph,
                                        oldPositions = vertexDragInitialPositions,
                                        newPositions = endPositions
                                    )
                                )
                                onTransformUpdated()
                            }
                        } else {
                            // Push Command to undo/redo history if transform changed
                            if (dragStartPos != selectedNode.position ||
                                dragStartRot != selectedNode.rotation ||
                                dragStartScale != selectedNode.scale
                            ) {
                                history.recordExecuted(
                                    TransformNodeCommand(
                                        sceneGraph = sceneGraph,
                                        nodeId = selectedNode.id,
                                        oldPos = dragStartPos,
                                        oldRot = dragStartRot,
                                        oldScale = dragStartScale,
                                        newPos = selectedNode.position.copy(),
                                        newRot = selectedNode.rotation.copy(),
                                        newScale = selectedNode.scale.copy()
                                    )
                                )
                            }
                        }
                    }
                    gizmo.endDrag()
                    isGizmoInteracting = false
                    isVertexTransformDrag = false
                    requestRender()
                } else {
                    if (isGizmoInteracting) {
                        gizmo.endDrag()
                        isGizmoInteracting = false
                        isVertexTransformDrag = false
                    }
                    if (!hasMovedSignificantly && finishedGesture != TouchGestureMode.MULTI_TOUCH) {
                        if (modelingMode == ModelingMode.EDIT) {
                            val selectedNode = sceneGraph.getSelectedNode()
                            val editMgr = editSelection
                            if (selectedNode != null && editMgr != null) {
                                when (selectionMode) {
                                    SelectionMode.VERTEX -> {
                                        val v = editMgr.pickVertex(x, y, selectedNode, camera, sceneGraph.nodes, renderer.isTransparentView)
                                        if (v != null) {
                                            editMgr.toggleVertex(v, selectedNode.mesh?.topology)
                                            onEditSelectionChanged?.invoke()
                                        }
                                    }
                                    SelectionMode.EDGE -> {
                                        val e = editMgr.pickEdge(x, y, selectedNode, camera, sceneGraph.nodes, renderer.isTransparentView)
                                        if (e != null) {
                                            editMgr.toggleEdge(e)
                                            onEditSelectionChanged?.invoke()
                                        }
                                    }
                                    SelectionMode.FACE -> {
                                        val f = editMgr.pickFace(x, y, selectedNode, camera, sceneGraph.nodes, renderer.isTransparentView)
                                        if (f != null) {
                                            editMgr.toggleFace(f, selectedNode.mesh?.topology)
                                            onEditSelectionChanged?.invoke()
                                        }
                                    }
                                }
                                requestRender()
                            }
                        } else {
                            // User tapped on the screen without dragging: Object Selection Raycast!
                            val ray = camera.screenPointToRay(x, y)
                            val hit = sceneGraph.raycast(ray)
                            sceneGraph.selectedNodeId = hit?.id
                            onSelectionChanged(hit?.id)
                            requestRender()
                        }
                    } else if (hasMovedSignificantly && finishedGesture == TouchGestureMode.SINGLE_ORBIT) {
                        val snappedPreset = camera.snapOnRelease()
                        if (snappedPreset != null) {
                            onCameraPresetChanged(snappedPreset)
                            requestRender()
                        }
                    }
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                touchGestureMode = TouchGestureMode.NONE
                isGizmoInteracting = false
                isVertexTransformDrag = false
                previousPanX = 0f
                previousPanY = 0f
                isMultiPanActive = false
                isMultiTwistActive = false
            }
        }
        return true
    }
}
