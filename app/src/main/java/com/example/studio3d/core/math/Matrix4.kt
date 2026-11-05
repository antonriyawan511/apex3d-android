package com.example.studio3d.core.math

import com.example.studio3d.core.math.*
import com.example.studio3d.core.engine.*
import com.example.studio3d.core.scene.*
import com.example.studio3d.core.history.*
import com.example.studio3d.core.preferences.*
import com.example.studio3d.core.project.*
import com.example.studio3d.feature.editor.viewport.*
import com.example.studio3d.feature.editor.tools.select.*


import android.opengl.Matrix

/**
 * 4x4 Column-Major Matrix for OpenGL ES 3.0 transformations and camera projections.
 */
class Matrix4 {
    val values = FloatArray(16)

    init {
        identity()
    }

    fun identity(): Matrix4 {
        Matrix.setIdentityM(values, 0)
        return this
    }

    fun copyFrom(other: Matrix4): Matrix4 {
        System.arraycopy(other.values, 0, values, 0, 16)
        return this
    }

    fun multiply(other: Matrix4): Matrix4 {
        val result = FloatArray(16)
        Matrix.multiplyMM(result, 0, values, 0, other.values, 0)
        System.arraycopy(result, 0, values, 0, 16)
        return this
    }

    fun translate(x: Float, y: Float, z: Float): Matrix4 {
        Matrix.translateM(values, 0, x, y, z)
        return this
    }

    fun translate(v: Vector3): Matrix4 = translate(v.x, v.y, v.z)

    fun rotate(angleDegrees: Float, x: Float, y: Float, z: Float): Matrix4 {
        Matrix.rotateM(values, 0, angleDegrees, x, y, z)
        return this
    }

    fun scale(x: Float, y: Float, z: Float): Matrix4 {
        Matrix.scaleM(values, 0, x, y, z)
        return this
    }

    fun scale(v: Vector3): Matrix4 = scale(v.x, v.y, v.z)

    fun setLookAt(
        eyeX: Float, eyeY: Float, eyeZ: Float,
        centerX: Float, centerY: Float, centerZ: Float,
        upX: Float, upY: Float, upZ: Float
    ): Matrix4 {
        Matrix.setLookAtM(values, 0, eyeX, eyeY, eyeZ, centerX, centerY, centerZ, upX, upY, upZ)
        return this
    }

    fun setPerspective(fovyDegrees: Float, aspect: Float, near: Float, far: Float): Matrix4 {
        Matrix.perspectiveM(values, 0, fovyDegrees, aspect, near, far)
        return this
    }

    fun setOrtho(left: Float, right: Float, bottom: Float, top: Float, near: Float, far: Float): Matrix4 {
        Matrix.orthoM(values, 0, left, right, bottom, top, near, far)
        return this
    }

    fun invert(out: Matrix4): Boolean {
        return Matrix.invertM(out.values, 0, values, 0)
    }

    fun invert(): Matrix4 {
        val out = Matrix4()
        Matrix.invertM(out.values, 0, values, 0)
        return out
    }

    fun transformPoint(p: Vector3): Vector3 {
        val inVec = floatArrayOf(p.x, p.y, p.z, 1f)
        val outVec = FloatArray(4)
        Matrix.multiplyMV(outVec, 0, values, 0, inVec, 0)
        val w = if (outVec[3] != 0f) outVec[3] else 1f
        return Vector3(outVec[0] / w, outVec[1] / w, outVec[2] / w)
    }

    fun transformDirection(dir: Vector3): Vector3 {
        val inVec = floatArrayOf(dir.x, dir.y, dir.z, 0f)
        val outVec = FloatArray(4)
        Matrix.multiplyMV(outVec, 0, values, 0, inVec, 0)
        return Vector3(outVec[0], outVec[1], outVec[2]).normalized()
    }

    fun transformVector(v: Vector3): Vector3 {
        val inVec = floatArrayOf(v.x, v.y, v.z, 0f)
        val outVec = FloatArray(4)
        Matrix.multiplyMV(outVec, 0, values, 0, inVec, 0)
        return Vector3(outVec[0], outVec[1], outVec[2])
    }

    companion object {
        fun identity(): Matrix4 = Matrix4()

        fun createTRS(translation: Vector3, rotationEulerDeg: Vector3, scale: Vector3): Matrix4 {
            val m = Matrix4()
            m.translate(translation.x, translation.y, translation.z)
            m.rotate(rotationEulerDeg.y, 0f, 1f, 0f)
            m.rotate(rotationEulerDeg.x, 1f, 0f, 0f)
            m.rotate(rotationEulerDeg.z, 0f, 0f, 1f)
            m.scale(scale.x, scale.y, scale.z)
            return m
        }
    }
}
