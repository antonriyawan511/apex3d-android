package com.example.studio3d.core.engine

import com.example.studio3d.core.math.*
import com.example.studio3d.core.engine.*
import com.example.studio3d.core.scene.*
import com.example.studio3d.core.history.*
import com.example.studio3d.core.preferences.*
import com.example.studio3d.core.project.*
import com.example.studio3d.feature.editor.viewport.*
import com.example.studio3d.feature.editor.tools.select.*


import com.example.studio3d.core.math.Matrix4
import com.example.studio3d.core.math.Ray
import com.example.studio3d.core.math.Vector3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

enum class CameraPreset(val label: String) {
    PERSPECTIVE("Perspective"),
    TOP("Top"),
    BOTTOM("Bottom"),
    FRONT("Front"),
    BACK("Back"),
    LEFT("Left"),
    RIGHT("Right")
}

class Camera {
    var target = Vector3(0f, 0.5f, 0f)
    var distance = 6.5f
    var azimuthDeg = 35f // horizontal angle around target
    var elevationDeg = 25f // vertical angle above horizon (-89 to 89)

    var isOrthographic = false
    var isOrthoSnapEnabled = false

    var orthoSize = 4f
    var fovDeg = 45f
    var nearPlane = 0.1f
    var farPlane = 100f
    var viewportWidth = 1080
    var viewportHeight = 1920

    val viewMatrix = Matrix4()
    val projectionMatrix = Matrix4()
    val viewProjectionMatrix = Matrix4()
    val invViewProjectionMatrix = Matrix4()

    fun updateMatrices() {
        // Clamp elevation slightly below 90° to maintain pure (0,1,0) UP vector without pole singularity
        elevationDeg = max(-88.5f, min(88.5f, elevationDeg))
        distance = max(0.5f, min(50f, distance))

        val azRad = (azimuthDeg * PI / 180.0).toFloat()
        val elRad = (elevationDeg * PI / 180.0).toFloat()

        val eyeX = target.x + distance * cos(elRad) * sin(azRad)
        val eyeY = target.y + distance * sin(elRad)
        val eyeZ = target.z + distance * cos(elRad) * cos(azRad)

        viewMatrix.setLookAt(
            eyeX, eyeY, eyeZ,
            target.x, target.y, target.z,
            0f, 1f, 0f
        )

        val aspect = if (viewportHeight > 0) viewportWidth.toFloat() / viewportHeight.toFloat() else 1f

        if (isOrthographic) {
            val halfH = orthoSize / 2f
            val halfW = halfH * aspect
            projectionMatrix.setOrtho(-halfW, halfW, -halfH, halfH, nearPlane, farPlane)
        } else {
            projectionMatrix.setPerspective(fovDeg, aspect, nearPlane, farPlane)
        }

        viewProjectionMatrix.copyFrom(projectionMatrix).multiply(viewMatrix)
        viewProjectionMatrix.invert(invViewProjectionMatrix)
    }

    fun orbit(deltaAzimuthDeg: Float, deltaElevationDeg: Float) {
        if (isOrthographic) {
            // Smoothly auto-switch back to Perspective on 3D orbit drag with 1-to-1 scale preservation
            distance = max(1.0f, (orthoSize / 2f) / kotlin.math.tan((fovDeg * PI / 360.0)).toFloat())
            isOrthographic = false
        }

        azimuthDeg = (azimuthDeg + deltaAzimuthDeg) % 360f
        if (azimuthDeg < 0) azimuthDeg += 360f
        elevationDeg = (elevationDeg + deltaElevationDeg).coerceIn(-88.5f, 88.5f)
        updateMatrices()
    }

    /**
     * Checks if current camera orientation is near one of the 6 principal orthogonal axes.
     * If within [toleranceDeg] (default 12°), magnetically snaps camera directly to that exact
     * orthogonal axis and activates True Orthographic projection.
     * Returns the matched CameraPreset if snapped, or null otherwise.
     */
    fun snapOnRelease(toleranceDeg: Float = 12f): CameraPreset? {
        if (!isOrthoSnapEnabled) return null

        val aR = (azimuthDeg * PI / 180.0).toFloat()
        val eR = (elevationDeg * PI / 180.0).toFloat()
        val currentDir = Vector3(cos(eR) * sin(aR), sin(eR), cos(eR) * cos(aR)).normalized()

        val targets = listOf(
            CameraPreset.FRONT to Vector3(0f, 0f, 1f),
            CameraPreset.BACK to Vector3(0f, 0f, -1f),
            CameraPreset.RIGHT to Vector3(1f, 0f, 0f),
            CameraPreset.LEFT to Vector3(-1f, 0f, 0f),
            CameraPreset.TOP to Vector3(0f, 1f, 0f),
            CameraPreset.BOTTOM to Vector3(0f, -1f, 0f)
        )

        var bestPreset: CameraPreset? = null
        var minAngle = 180f

        for ((preset, dir) in targets) {
            val dot = currentDir.dot(dir).coerceIn(-1f, 1f)
            val angle = (kotlin.math.acos(dot.toDouble()) * 180.0 / PI).toFloat()
            if (angle < minAngle) {
                minAngle = angle
                bestPreset = preset
            }
        }

        if (bestPreset != null && minAngle <= toleranceDeg) {
            when (bestPreset) {
                CameraPreset.TOP -> {
                    orthoSize = distance * 2f * kotlin.math.tan((fovDeg * PI / 360.0)).toFloat()
                    isOrthographic = true
                    val nearestAz = (kotlin.math.round(azimuthDeg / 90f) * 90f) % 360f
                    azimuthDeg = if (nearestAz < 0) nearestAz + 360f else nearestAz
                    elevationDeg = 88.5f
                    updateMatrices()
                }
                CameraPreset.BOTTOM -> {
                    orthoSize = distance * 2f * kotlin.math.tan((fovDeg * PI / 360.0)).toFloat()
                    isOrthographic = true
                    val nearestAz = (kotlin.math.round(azimuthDeg / 90f) * 90f) % 360f
                    azimuthDeg = if (nearestAz < 0) nearestAz + 360f else nearestAz
                    elevationDeg = -88.5f
                    updateMatrices()
                }
                else -> {
                    setPreset(bestPreset)
                }
            }
            return bestPreset
        }

        return null
    }

    fun pan(deltaScreenX: Float, deltaScreenY: Float) {
        // Extract camera screen-space right and up vectors directly from viewMatrix values
        val right = Vector3(viewMatrix.values[0], viewMatrix.values[4], viewMatrix.values[8]).normalized()
        val up = Vector3(viewMatrix.values[1], viewMatrix.values[5], viewMatrix.values[9]).normalized()

        // Exact 1-to-1 pixel-to-world factor based on FOV/distance or Ortho size
        val h = max(1, viewportHeight).toFloat()
        val factor = if (isOrthographic) {
            orthoSize / h
        } else {
            (distance * 2f * kotlin.math.tan((fovDeg * PI / 180.0) / 2.0).toFloat()) / h
        }

        target = target - right * (deltaScreenX * factor) + up * (deltaScreenY * factor)
        updateMatrices()
    }

    fun zoom(scaleFactor: Float) {
        if (isOrthographic) {
            orthoSize = max(0.5f, min(30f, orthoSize / scaleFactor))
        } else {
            distance = max(0.8f, min(35f, distance / scaleFactor))
        }
        updateMatrices()
    }

    fun setPreset(preset: CameraPreset) {
        when (preset) {
            CameraPreset.PERSPECTIVE -> {
                if (isOrthographic) {
                    distance = max(1.0f, (orthoSize / 2f) / kotlin.math.tan((fovDeg * PI / 360.0)).toFloat())
                    isOrthographic = false
                }
                azimuthDeg = 45f
                elevationDeg = 25f
            }
            CameraPreset.TOP -> {
                orthoSize = distance * 2f * kotlin.math.tan((fovDeg * PI / 360.0)).toFloat()
                isOrthographic = true
                azimuthDeg = 0f
                elevationDeg = 88.5f
            }
            CameraPreset.BOTTOM -> {
                orthoSize = distance * 2f * kotlin.math.tan((fovDeg * PI / 360.0)).toFloat()
                isOrthographic = true
                azimuthDeg = 0f
                elevationDeg = -88.5f
            }
            CameraPreset.FRONT -> {
                orthoSize = distance * 2f * kotlin.math.tan((fovDeg * PI / 360.0)).toFloat()
                isOrthographic = true
                azimuthDeg = 0f
                elevationDeg = 0f
            }
            CameraPreset.BACK -> {
                orthoSize = distance * 2f * kotlin.math.tan((fovDeg * PI / 360.0)).toFloat()
                isOrthographic = true
                azimuthDeg = 180f
                elevationDeg = 0f
            }
            CameraPreset.LEFT -> {
                orthoSize = distance * 2f * kotlin.math.tan((fovDeg * PI / 360.0)).toFloat()
                isOrthographic = true
                azimuthDeg = -90f
                elevationDeg = 0f
            }
            CameraPreset.RIGHT -> {
                orthoSize = distance * 2f * kotlin.math.tan((fovDeg * PI / 360.0)).toFloat()
                isOrthographic = true
                azimuthDeg = 90f
                elevationDeg = 0f
            }
        }
        updateMatrices()
    }

    /**
     * Projects a 3D world point to 2D screen space coordinates (pixels).
     */
    fun worldToScreen(worldPoint: Vector3): Vector3 {
        val clipSpace = viewProjectionMatrix.transformPoint(worldPoint)
        val screenX = (clipSpace.x + 1f) * 0.5f * viewportWidth
        val screenY = (1f - clipSpace.y) * 0.5f * viewportHeight
        return Vector3(screenX, screenY, clipSpace.z)
    }

    /**
     * Casts a Ray from 2D screen coordinates into 3D world space.
     */
    fun screenPointToRay(screenX: Float, screenY: Float): Ray {
        // Normalize screen coordinates to Normalized Device Coordinates [-1, 1]
        val ndcX = (2f * screenX) / viewportWidth - 1f
        val ndcY = 1f - (2f * screenY) / viewportHeight

        val nearPointNDC = Vector3(ndcX, ndcY, -1f)
        val farPointNDC = Vector3(ndcX, ndcY, 1f)

        val nearPointWorld = invViewProjectionMatrix.transformPoint(nearPointNDC)
        val farPointWorld = invViewProjectionMatrix.transformPoint(farPointNDC)

        val dir = (farPointWorld - nearPointWorld).normalized()
        return Ray(origin = nearPointWorld, direction = dir)
    }

    fun getEyePosition(): Vector3 {
        val azRad = (azimuthDeg * PI / 180.0).toFloat()
        val elRad = (elevationDeg * PI / 180.0).toFloat()

        val eyeX = target.x + distance * cos(elRad) * sin(azRad)
        val eyeY = target.y + distance * sin(elRad)
        val eyeZ = target.z + distance * cos(elRad) * cos(azRad)
        return Vector3(eyeX, eyeY, eyeZ)
    }
}
