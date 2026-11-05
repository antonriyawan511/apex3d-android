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

data class Ray(
    val origin: Vector3,
    val direction: Vector3 // Should be normalized
) {
    fun getPoint(distance: Float): Vector3 = origin + direction * distance

    /**
     * Intersects ray with Axis-Aligned Bounding Box (AABB).
     * Returns distance `t` along ray, or null if no hit.
     */
    fun intersectAABB(box: BoundingBox): Float? {
        var tMin = (box.min.x - origin.x) / (if (direction.x != 0f) direction.x else 0.00001f)
        var tMax = (box.max.x - origin.x) / (if (direction.x != 0f) direction.x else 0.00001f)
        if (tMin > tMax) { val temp = tMin; tMin = tMax; tMax = temp }

        var tyMin = (box.min.y - origin.y) / (if (direction.y != 0f) direction.y else 0.00001f)
        var tyMax = (box.max.y - origin.y) / (if (direction.y != 0f) direction.y else 0.00001f)
        if (tyMin > tyMax) { val temp = tyMin; tyMin = tyMax; tyMax = temp }

        if (tMin > tyMax || tyMin > tMax) return null
        if (tyMin > tMin) tMin = tyMin
        if (tyMax < tMax) tMax = tyMax

        var tzMin = (box.min.z - origin.z) / (if (direction.z != 0f) direction.z else 0.00001f)
        var tzMax = (box.max.z - origin.z) / (if (direction.z != 0f) direction.z else 0.00001f)
        if (tzMin > tzMax) { val temp = tzMin; tzMin = tzMax; tzMax = temp }

        if (tMin > tzMax || tzMin > tMax) return null
        if (tzMin > tMin) tMin = tzMin
        if (tzMax < tMax) tMax = tzMax

        return if (tMax >= 0f) max(0f, tMin) else null
    }

    /**
     * Intersects ray with a finite cylinder (used for gizmo axis shafts).
     */
    fun intersectCylinderSegment(p1: Vector3, p2: Vector3, radius: Float): Float? {
        val axis = (p2 - p1)
        val axisLen = axis.length()
        if (axisLen < 0.0001f) return null
        val d = axis / axisLen

        val m = origin - p1
        val md = m.dot(d)
        val nd = direction.dot(d)

        val a = 1f - nd * nd
        val b = direction.dot(m) - nd * md
        val c = m.dot(m) - md * md - radius * radius

        if (a.let { it > -0.00001f && it < 0.00001f }) return null

        val discr = b * b - a * c
        if (discr < 0f) return null

        val sqrtDiscr = kotlin.math.sqrt(discr)
        var t = (-b - sqrtDiscr) / a
        if (t < 0f) t = (-b + sqrtDiscr) / a
        if (t < 0f) return null

        val hitP = getPoint(t)
        val proj = (hitP - p1).dot(d)
        return if (proj in 0f..axisLen) t else null
    }

    /**
     * Intersects ray with an infinite plane defined by point and normal.
     */
    fun intersectPlane(planePoint: Vector3, planeNormal: Vector3): Float? {
        val denom = planeNormal.dot(direction)
        if (denom > -0.0001f && denom < 0.0001f) return null
        val t = planeNormal.dot(planePoint - origin) / denom
        return if (t >= 0f) t else null
    }

    /**
     * Intersects ray with a triangle (v0, v1, v2) using the Möller–Trumbore algorithm.
     * Returns distance `t` along ray, or null if no intersection.
     */
    fun intersectTriangle(v0: Vector3, v1: Vector3, v2: Vector3, backfaceCull: Boolean = true): Float? {
        val edge1 = v1 - v0
        val edge2 = v2 - v0
        val h = direction.cross(edge2)
        val a = edge1.dot(h)

        if (backfaceCull) {
            if (a < 0.00001f) return null
        } else {
            if (a > -0.00001f && a < 0.00001f) return null
        }

        val f = 1f / a
        val s = origin - v0
        val u = f * s.dot(h)
        if (u < 0f || u > 1f) return null

        val q = s.cross(edge1)
        val v = f * direction.dot(q)
        if (v < 0f || u + v > 1f) return null

        val t = f * edge2.dot(q)
        return if (t > 0.00001f) t else null
    }
}
