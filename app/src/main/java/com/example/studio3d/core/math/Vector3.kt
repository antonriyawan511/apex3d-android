package com.example.studio3d.core.math

import com.example.studio3d.core.math.*
import com.example.studio3d.core.engine.*
import com.example.studio3d.core.scene.*
import com.example.studio3d.core.history.*
import com.example.studio3d.core.preferences.*
import com.example.studio3d.core.project.*
import com.example.studio3d.feature.editor.viewport.*
import com.example.studio3d.feature.editor.tools.select.*


import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Vector3(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f
) {
    operator fun plus(other: Vector3): Vector3 = Vector3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vector3): Vector3 = Vector3(x - other.x, y - other.y, z - other.z)
    operator fun unaryMinus(): Vector3 = Vector3(-x, -y, -z)
    operator fun times(scalar: Float): Vector3 = Vector3(x * scalar, y * scalar, z * scalar)
    operator fun div(scalar: Float): Vector3 = if (scalar != 0f) Vector3(x / scalar, y / scalar, z / scalar) else Vector3()

    fun length(): Float = sqrt(x * x + y * y + z * z)
    fun lengthSquared(): Float = x * x + y * y + z * z

    fun normalized(): Vector3 {
        val len = length()
        return if (len > 0.00001f) Vector3(x / len, y / len, z / len) else Vector3(0f, 0f, 0f)
    }

    fun dot(other: Vector3): Float = x * other.x + y * other.y + z * other.z

    fun cross(other: Vector3): Vector3 = Vector3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )

    fun distanceTo(other: Vector3): Float = (this - other).length()

    fun copyFrom(other: Vector3) {
        x = other.x
        y = other.y
        z = other.z
    }

    fun toFloatArray(): FloatArray = floatArrayOf(x, y, z)

    companion object {
        val ZERO = Vector3(0f, 0f, 0f)
        val ONE = Vector3(1f, 1f, 1f)
        val UNIT_X = Vector3(1f, 0f, 0f)
        val UNIT_Y = Vector3(0f, 1f, 0f)
        val UNIT_Z = Vector3(0f, 0f, 1f)
    }
}
