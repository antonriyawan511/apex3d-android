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


import android.opengl.GLES30
import com.example.studio3d.core.math.Matrix4
import com.example.studio3d.core.math.Ray
import com.example.studio3d.core.math.Vector3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin

enum class TransformMode {
    SELECT,
    TRANSLATE,
    ROTATE,
    SCALE
}

enum class GizmoAxis {
    NONE,
    X,
    Y,
    Z,
    CENTER
}

class Gizmo {
    var mode = TransformMode.TRANSLATE
    var isSnappingEnabled = true
    var gridSnapSize = 0.5f // 0.5 meter
    var angleSnapDeg = 15f // 15 degrees

    var activeAxis = GizmoAxis.NONE
    var isDragging = false

    var startObjectPos = Vector3()
    var startObjectRot = Vector3()
    var startObjectScale = Vector3()

    private var initialHitPoint = Vector3()
    private var dragPlaneNormal = Vector3()
    private var startScreenX = 0f
    private var startScreenY = 0f
    private var screenTangentX = 1f
    private var screenTangentY = 0f

    // Mesh handles for gizmo rendering
    private val shaftRadius = 0.035f
    private val shaftLength = 0.58f // Proportional shortened length (0.45f shaft + 0.13f head)
    private val headRadius = 0.07f
    private val headLength = 0.13f

    /**
     * Hit tests ray against the gizmo at object world position.
     * Returns true if an axis was picked.
     */
    fun hitTest(ray: Ray, position: Vector3, camera: Camera): GizmoAxis {
        if (mode == TransformMode.SELECT) return GizmoAxis.NONE

        val camDist = (camera.getEyePosition() - position).length()
        val scale = if (camera.isOrthographic) camera.orthoSize * 0.18f else camDist * 0.15f
        val len = shaftLength * scale
        val rShaft = 0.075f * scale // Generous touch target for mobile phone displays
        val rCenter = 0.15f * scale

        // Center handle check
        if ((ray.origin - position).length() > 0.01f) {
            val centerDist = ray.getPoint(0f).distanceTo(position)
            // Test sphere around position
            val toCenter = position - ray.origin
            val tProj = toCenter.dot(ray.direction)
            if (tProj > 0) {
                val closestPt = ray.getPoint(tProj)
                if (closestPt.distanceTo(position) < rCenter) {
                    return GizmoAxis.CENTER
                }
            }
        }

        // Test X axis (Shaft from position to position + X * len)
        val pX = position + Vector3(len, 0f, 0f)
        if (ray.intersectCylinderSegment(position, pX, rShaft) != null) {
            return GizmoAxis.X
        }

        // Test Y axis
        val pY = position + Vector3(0f, len, 0f)
        if (ray.intersectCylinderSegment(position, pY, rShaft) != null) {
            return GizmoAxis.Y
        }

        // Test Z axis
        val pZ = position + Vector3(0f, 0f, len)
        if (ray.intersectCylinderSegment(position, pZ, rShaft) != null) {
            return GizmoAxis.Z
        }

        return GizmoAxis.NONE
    }

    fun startDrag(
        axis: GizmoAxis,
        initialRay: Ray,
        position: Vector3,
        rotation: Vector3,
        scale: Vector3,
        camera: Camera,
        touchX: Float = 0f,
        touchY: Float = 0f
    ) {
        activeAxis = axis
        isDragging = true
        startObjectPos = position.copy()
        startObjectRot = rotation.copy()
        startObjectScale = scale.copy()
        startScreenX = touchX
        startScreenY = touchY

        // Choose plane normal most perpendicular to camera view or axis rotation plane
        val camDir = (camera.getEyePosition() - position).normalized()
        dragPlaneNormal = if (mode == TransformMode.ROTATE) {
            when (axis) {
                GizmoAxis.X -> Vector3.UNIT_X
                GizmoAxis.Y -> Vector3.UNIT_Y
                GizmoAxis.Z -> Vector3.UNIT_Z
                else -> camDir
            }
        } else {
            when (axis) {
                GizmoAxis.X -> if (kotlin.math.abs(camDir.y) > kotlin.math.abs(camDir.z)) Vector3.UNIT_Y else Vector3.UNIT_Z
                GizmoAxis.Y -> if (kotlin.math.abs(camDir.x) > kotlin.math.abs(camDir.z)) Vector3.UNIT_X else Vector3.UNIT_Z
                GizmoAxis.Z -> if (kotlin.math.abs(camDir.x) > kotlin.math.abs(camDir.y)) Vector3.UNIT_X else Vector3.UNIT_Y
                else -> camDir
            }
        }

        val hitDist = initialRay.intersectPlane(position, dragPlaneNormal)
        initialHitPoint = if (hitDist != null) initialRay.getPoint(hitDist) else position.copy()

        // Calculate 2D Screen Tangent Vector for Rotation Mode
        val p = initialHitPoint - position
        val t3d = when (axis) {
            GizmoAxis.X -> Vector3(0f, -p.z, p.y)
            GizmoAxis.Y -> Vector3(p.z, 0f, -p.x)
            GizmoAxis.Z -> Vector3(-p.y, p.x, 0f)
            else -> Vector3.UNIT_Y
        }.normalized()

        val pScreen = camera.worldToScreen(initialHitPoint)
        val tScreen = camera.worldToScreen(initialHitPoint + t3d * 0.2f)
        val sDirX = tScreen.x - pScreen.x
        val sDirY = tScreen.y - pScreen.y
        val len = kotlin.math.hypot(sDirX.toDouble(), sDirY.toDouble()).toFloat()

        if (len > 0.001f) {
            screenTangentX = sDirX / len
            screenTangentY = sDirY / len
        } else {
            screenTangentX = 1f
            screenTangentY = 0f
        }
    }

    fun updateDrag(
        currentRay: Ray,
        camera: Camera,
        touchX: Float = startScreenX,
        touchY: Float = startScreenY
    ): Triple<Vector3, Vector3, Vector3> {
        if (!isDragging || activeAxis == GizmoAxis.NONE) {
            return Triple(startObjectPos, startObjectRot, startObjectScale)
        }

        val hitDist = currentRay.intersectPlane(startObjectPos, dragPlaneNormal) ?: return Triple(startObjectPos, startObjectRot, startObjectScale)
        val currentHit = currentRay.getPoint(hitDist)
        val delta = currentHit - initialHitPoint

        var newPos = startObjectPos.copy()
        var newRot = startObjectRot.copy()
        var newScale = startObjectScale.copy()

        when (mode) {
            TransformMode.TRANSLATE -> {
                when (activeAxis) {
                    GizmoAxis.X -> {
                        var dx = delta.x
                        if (isSnappingEnabled) {
                            val snapTarget = startObjectPos.x + dx
                            newPos.x = round(snapTarget / gridSnapSize) * gridSnapSize
                        } else {
                            newPos.x = startObjectPos.x + dx
                        }
                    }
                    GizmoAxis.Y -> {
                        var dy = delta.y
                        if (isSnappingEnabled) {
                            val snapTarget = startObjectPos.y + dy
                            newPos.y = round(snapTarget / gridSnapSize) * gridSnapSize
                        } else {
                            newPos.y = startObjectPos.y + dy
                        }
                    }
                    GizmoAxis.Z -> {
                        var dz = delta.z
                        if (isSnappingEnabled) {
                            val snapTarget = startObjectPos.z + dz
                            newPos.z = round(snapTarget / gridSnapSize) * gridSnapSize
                        } else {
                            newPos.z = startObjectPos.z + dz
                        }
                    }
                    GizmoAxis.CENTER -> {
                        newPos = startObjectPos + delta
                        if (isSnappingEnabled) {
                            newPos.x = round(newPos.x / gridSnapSize) * gridSnapSize
                            newPos.y = round(newPos.y / gridSnapSize) * gridSnapSize
                            newPos.z = round(newPos.z / gridSnapSize) * gridSnapSize
                        }
                    }
                    GizmoAxis.NONE -> {}
                }
            }
            TransformMode.ROTATE -> {
                val screenDx = touchX - startScreenX
                val screenDy = touchY - startScreenY

                // Projected drag distance along the 2D screen tangent vector of the rotation circle
                val tangentDrag = screenDx * screenTangentX + screenDy * screenTangentY
                val deltaAngleDeg = tangentDrag * 0.40f // 0.40 degrees per screen pixel drag

                when (activeAxis) {
                    GizmoAxis.X -> {
                        var r = startObjectRot.x + deltaAngleDeg
                        if (isSnappingEnabled) r = round(r / angleSnapDeg) * angleSnapDeg
                        newRot.x = r
                    }
                    GizmoAxis.Y -> {
                        var r = startObjectRot.y + deltaAngleDeg
                        if (isSnappingEnabled) r = round(r / angleSnapDeg) * angleSnapDeg
                        newRot.y = r
                    }
                    GizmoAxis.Z -> {
                        var r = startObjectRot.z + deltaAngleDeg
                        if (isSnappingEnabled) r = round(r / angleSnapDeg) * angleSnapDeg
                        newRot.z = r
                    }
                    else -> {}
                }
            }
            TransformMode.SCALE -> {
                when (activeAxis) {
                    GizmoAxis.X -> {
                        val scaleDelta = delta.x * 0.8f
                        var s = kotlin.math.max(0.05f, startObjectScale.x + scaleDelta)
                        if (isSnappingEnabled) s = round(s * 10f) / 10f
                        newScale.x = s
                    }
                    GizmoAxis.Y -> {
                        val scaleDelta = delta.y * 0.8f
                        var s = kotlin.math.max(0.05f, startObjectScale.y + scaleDelta)
                        if (isSnappingEnabled) s = round(s * 10f) / 10f
                        newScale.y = s
                    }
                    GizmoAxis.Z -> {
                        val scaleDelta = delta.z * 0.8f
                        var s = kotlin.math.max(0.05f, startObjectScale.z + scaleDelta)
                        if (isSnappingEnabled) s = round(s * 10f) / 10f
                        newScale.z = s
                    }
                    GizmoAxis.CENTER -> {
                        val scaleDelta = (delta.x + delta.y + delta.z) * 0.5f
                        var s = kotlin.math.max(0.05f, 1f + scaleDelta)
                        if (isSnappingEnabled) s = round(s * 10f) / 10f
                        newScale = startObjectScale * s
                    }
                    GizmoAxis.NONE -> {}
                }
            }
            TransformMode.SELECT -> {}
        }

        return Triple(newPos, newRot, newScale)
    }

    fun endDrag() {
        isDragging = false
        activeAxis = GizmoAxis.NONE
    }
}
