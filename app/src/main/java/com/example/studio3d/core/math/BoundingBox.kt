package com.example.studio3d.core.math

import com.example.studio3d.core.math.*
import com.example.studio3d.core.engine.*
import com.example.studio3d.core.scene.*
import com.example.studio3d.core.history.*
import com.example.studio3d.core.preferences.*
import com.example.studio3d.core.project.*
import com.example.studio3d.feature.editor.viewport.*
import com.example.studio3d.feature.editor.tools.select.*


import kotlin.math.max
import kotlin.math.min

data class BoundingBox(
    val min: Vector3 = Vector3(-0.5f, -0.5f, -0.5f),
    val max: Vector3 = Vector3(0.5f, 0.5f, 0.5f)
) {
    val center: Vector3
        get() = (min + max) * 0.5f

    val size: Vector3
        get() = max - min

    fun contains(point: Vector3): Boolean {
        return point.x in min.x..max.x &&
               point.y in min.y..max.y &&
               point.z in min.z..max.z
    }

    /**
     * Transforms AABB by a 4x4 matrix and returns a new enclosing AABB.
     */
    fun transform(matrix: Matrix4): BoundingBox {
        val corners = arrayOf(
            Vector3(min.x, min.y, min.z),
            Vector3(max.x, min.y, min.z),
            Vector3(min.x, max.y, min.z),
            Vector3(max.x, max.y, min.z),
            Vector3(min.x, min.y, max.z),
            Vector3(max.x, min.y, max.z),
            Vector3(min.x, max.y, max.z),
            Vector3(max.x, max.y, max.z)
        )

        val newMin = Vector3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
        val newMax = Vector3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)

        for (corner in corners) {
            val tc = matrix.transformPoint(corner)
            newMin.x = min(newMin.x, tc.x)
            newMin.y = min(newMin.y, tc.y)
            newMin.z = min(newMin.z, tc.z)

            newMax.x = max(newMax.x, tc.x)
            newMax.y = max(newMax.y, tc.y)
            newMax.z = max(newMax.z, tc.z)
        }

        return BoundingBox(newMin, newMax)
    }
}
