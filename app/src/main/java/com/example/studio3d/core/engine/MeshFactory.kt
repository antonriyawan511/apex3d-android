package com.example.studio3d.core.engine

import com.example.studio3d.core.math.*
import com.example.studio3d.core.engine.*
import com.example.studio3d.core.scene.*
import com.example.studio3d.core.history.*
import com.example.studio3d.core.preferences.*
import com.example.studio3d.core.project.*
import com.example.studio3d.feature.editor.viewport.*
import com.example.studio3d.feature.editor.tools.select.*


import com.example.studio3d.core.math.BoundingBox
import com.example.studio3d.core.math.Vector3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object MeshFactory {

    fun createCube(width: Float = 1f, height: Float = 1f, depth: Float = 1f): Mesh {
        val hx = width / 2f
        val hy = height / 2f
        val hz = depth / 2f

        val vertices = floatArrayOf(
            // Front face (+Z)
            -hx, -hy,  hz,   hx, -hy,  hz,   hx,  hy,  hz,  -hx,  hy,  hz,
            // Back face (-Z)
             hx, -hy, -hz,  -hx, -hy, -hz,  -hx,  hy, -hz,   hx,  hy, -hz,
            // Top face (+Y)
            -hx,  hy,  hz,   hx,  hy,  hz,   hx,  hy, -hz,  -hx,  hy, -hz,
            // Bottom face (-Y)
            -hx, -hy, -hz,   hx, -hy, -hz,   hx, -hy,  hz,  -hx, -hy,  hz,
            // Right face (+X)
             hx, -hy,  hz,   hx, -hy, -hz,   hx,  hy, -hz,   hx,  hy,  hz,
            // Left face (-X)
            -hx, -hy, -hz,  -hx, -hy,  hz,  -hx,  hy,  hz,  -hx,  hy, -hz
        )

        val normals = floatArrayOf(
            // Front
            0f, 0f, 1f,  0f, 0f, 1f,  0f, 0f, 1f,  0f, 0f, 1f,
            // Back
            0f, 0f, -1f, 0f, 0f, -1f, 0f, 0f, -1f, 0f, 0f, -1f,
            // Top
            0f, 1f, 0f,  0f, 1f, 0f,  0f, 1f, 0f,  0f, 1f, 0f,
            // Bottom
            0f, -1f, 0f, 0f, -1f, 0f, 0f, -1f, 0f, 0f, -1f, 0f,
            // Right
            1f, 0f, 0f,  1f, 0f, 0f,  1f, 0f, 0f,  1f, 0f, 0f,
            // Left
            -1f, 0f, 0f, -1f, 0f, 0f, -1f, 0f, 0f, -1f, 0f, 0f
        )

        val uvs = floatArrayOf(
            // Front (+Z)
            0f, 0f,  1f, 0f,  1f, 1f,  0f, 1f,
            // Back (-Z)
            0f, 0f,  1f, 0f,  1f, 1f,  0f, 1f,
            // Top (+Y)
            0f, 0f,  1f, 0f,  1f, 1f,  0f, 1f,
            // Bottom (-Y)
            0f, 0f,  1f, 0f,  1f, 1f,  0f, 1f,
            // Right (+X)
            0f, 0f,  1f, 0f,  1f, 1f,  0f, 1f,
            // Left (-X)
            0f, 0f,  1f, 0f,  1f, 1f,  0f, 1f
        )

        val indices = shortArrayOf(
            0, 1, 2,  0, 2, 3,       // Front
            4, 5, 6,  4, 6, 7,       // Back
            8, 9, 10,  8, 10, 11,    // Top
            12, 13, 14,  12, 14, 15, // Bottom
            16, 17, 18,  16, 18, 19, // Right
            20, 21, 22,  20, 22, 23  // Left
        )

        val wireframe = shortArrayOf(
            0, 1,  1, 2,  2, 3,  3, 0,
            4, 5,  5, 6,  6, 7,  7, 4,
            0, 5,  1, 4,  2, 7,  3, 6
        )

        return Mesh(
            vertices = vertices,
            normals = normals,
            uvs = uvs,
            indices = indices,
            wireframeIndices = wireframe,
            boundingBox = BoundingBox(Vector3(-hx, -hy, -hz), Vector3(hx, hy, hz))
        )
    }

    fun createCylinder(radius: Float = 0.5f, height: Float = 1f, segments: Int = 24): Mesh {
        val halfH = height / 2f
        val vList = mutableListOf<Float>()
        val nList = mutableListOf<Float>()
        val uvList = mutableListOf<Float>()
        val iList = mutableListOf<Short>()
        val wList = mutableListOf<Short>()

        // Side vertices (smooth cylindrical normals)
        for (i in 0..segments) {
            val theta = (i.toFloat() / segments) * 2f * PI.toFloat()
            val c = cos(theta)
            val s = sin(theta)

            // Top rim
            vList.addAll(listOf(c * radius, halfH, s * radius))
            nList.addAll(listOf(c, 0f, s))
            uvList.addAll(listOf(i.toFloat() / segments, 1f))

            // Bottom rim
            vList.addAll(listOf(c * radius, -halfH, s * radius))
            nList.addAll(listOf(c, 0f, s))
            uvList.addAll(listOf(i.toFloat() / segments, 0f))
        }

        // Side indices (CCW outward winding)
        for (i in 0 until segments) {
            val top1 = (i * 2).toShort()
            val bot1 = (i * 2 + 1).toShort()
            val top2 = ((i + 1) * 2).toShort()
            val bot2 = ((i + 1) * 2 + 1).toShort()

            iList.addAll(listOf(top1, top2, bot1, top2, bot2, bot1))
            wList.addAll(listOf(top1, top2, bot1, bot2, top1, bot1))
        }

        // Top Cap (dedicated vertices with upward normal 0, 1, 0)
        val topCenterIdx = (vList.size / 3).toShort()
        vList.addAll(listOf(0f, halfH, 0f))
        nList.addAll(listOf(0f, 1f, 0f))
        uvList.addAll(listOf(0.5f, 0.5f))

        val topRimStart = (vList.size / 3).toShort()
        for (i in 0..segments) {
            val theta = (i.toFloat() / segments) * 2f * PI.toFloat()
            val c = cos(theta)
            val s = sin(theta)
            vList.addAll(listOf(c * radius, halfH, s * radius))
            nList.addAll(listOf(0f, 1f, 0f))
            uvList.addAll(listOf(0.5f + 0.5f * c, 0.5f - 0.5f * s))
        }

        for (i in 0 until segments) {
            val r1 = (topRimStart + i).toShort()
            val r2 = (topRimStart + i + 1).toShort()
            // CCW pointing UP (+Y)
            iList.addAll(listOf(topCenterIdx, r2, r1))
        }

        // Bottom Cap (dedicated vertices with downward normal 0, -1, 0)
        val botCenterIdx = (vList.size / 3).toShort()
        vList.addAll(listOf(0f, -halfH, 0f))
        nList.addAll(listOf(0f, -1f, 0f))
        uvList.addAll(listOf(0.5f, 0.5f))

        val botRimStart = (vList.size / 3).toShort()
        for (i in 0..segments) {
            val theta = (i.toFloat() / segments) * 2f * PI.toFloat()
            val c = cos(theta)
            val s = sin(theta)
            vList.addAll(listOf(c * radius, -halfH, s * radius))
            nList.addAll(listOf(0f, -1f, 0f))
            uvList.addAll(listOf(0.5f + 0.5f * c, 0.5f + 0.5f * s))
        }

        for (i in 0 until segments) {
            val r1 = (botRimStart + i).toShort()
            val r2 = (botRimStart + i + 1).toShort()
            // CCW pointing DOWN (-Y)
            iList.addAll(listOf(botCenterIdx, r1, r2))
        }

        return Mesh(
            vertices = vList.toFloatArray(),
            normals = nList.toFloatArray(),
            uvs = uvList.toFloatArray(),
            indices = iList.toShortArray(),
            wireframeIndices = wList.toShortArray(),
            boundingBox = BoundingBox(Vector3(-radius, -halfH, -radius), Vector3(radius, halfH, radius))
        )
    }

    /**
     * Dedicated 3D Move Gizmo shaft starting strictly at y = 0.0f and extending only to +length.
     * Prevents any shaft geometry from penetrating through the center origin into negative space.
     */
    fun createGizmoShaft(radius: Float = 0.009f, length: Float = 0.82f, segments: Int = 16): Mesh {
        val vList = mutableListOf<Float>()
        val nList = mutableListOf<Float>()
        val uvList = mutableListOf<Float>()
        val iList = mutableListOf<Short>()
        val wList = mutableListOf<Short>()

        // Side vertices from y = 0.0f to y = length
        for (i in 0..segments) {
            val theta = (i.toFloat() / segments) * 2f * PI.toFloat()
            val c = cos(theta)
            val s = sin(theta)

            // Top rim at y = length
            vList.addAll(listOf(c * radius, length, s * radius))
            nList.addAll(listOf(c, 0f, s))
            uvList.addAll(listOf(i.toFloat() / segments, 1f))

            // Bottom base rim at y = 0.0f (exact origin)
            vList.addAll(listOf(c * radius, 0f, s * radius))
            nList.addAll(listOf(c, 0f, s))
            uvList.addAll(listOf(i.toFloat() / segments, 0f))
        }

        // Side faces (CCW outward winding)
        for (i in 0 until segments) {
            val top1 = (i * 2).toShort()
            val bot1 = (i * 2 + 1).toShort()
            val top2 = ((i + 1) * 2).toShort()
            val bot2 = ((i + 1) * 2 + 1).toShort()

            iList.addAll(listOf(top1, top2, bot1, top2, bot2, bot1))
            wList.addAll(listOf(top1, top2, bot1, bot2))
        }

        // Base cap at y = 0.0f
        val botCenterIdx = (vList.size / 3).toShort()
        vList.addAll(listOf(0f, 0f, 0f))
        nList.addAll(listOf(0f, -1f, 0f))
        uvList.addAll(listOf(0.5f, 0.5f))

        for (i in 0 until segments) {
            val bot1 = (i * 2 + 1).toShort()
            val bot2 = ((i + 1) * 2 + 1).toShort()
            iList.addAll(listOf(botCenterIdx, bot1, bot2))
        }

        return Mesh(
            vertices = vList.toFloatArray(),
            normals = nList.toFloatArray(),
            uvs = uvList.toFloatArray(),
            indices = iList.toShortArray(),
            wireframeIndices = wList.toShortArray(),
            boundingBox = BoundingBox(Vector3(-radius, 0f, -radius), Vector3(radius, length, radius))
        )
    }

    /**
     * Dedicated conical arrowhead for 3D Move Gizmo with base at y = 0.0f and pointed tip at +length.
     */
    fun createGizmoCone(radius: Float = 0.034f, length: Float = 0.22f, segments: Int = 16): Mesh {
        val vList = mutableListOf<Float>()
        val nList = mutableListOf<Float>()
        val uvList = mutableListOf<Float>()
        val iList = mutableListOf<Short>()
        val wList = mutableListOf<Short>()

        // Tip vertex at y = length (pointing in positive direction)
        val tipIdx = (vList.size / 3).toShort()
        vList.addAll(listOf(0f, length, 0f))
        nList.addAll(listOf(0f, 1f, 0f))
        uvList.addAll(listOf(0.5f, 1f))

        // Base ring vertices at y = 0.0f
        val baseStartIdx = (vList.size / 3).toShort()
        for (i in 0..segments) {
            val theta = (i.toFloat() / segments) * 2f * PI.toFloat()
            val c = cos(theta)
            val s = sin(theta)
            vList.addAll(listOf(c * radius, 0f, s * radius))
            val normal = Vector3(c, 0.35f, s).normalized()
            nList.addAll(listOf(normal.x, normal.y, normal.z))
            uvList.addAll(listOf(i.toFloat() / segments, 0f))
        }

        // Side faces (CCW outward winding)
        for (i in 0 until segments) {
            val b1 = (baseStartIdx + i).toShort()
            val b2 = (baseStartIdx + i + 1).toShort()
            iList.addAll(listOf(tipIdx, b2, b1))
            wList.addAll(listOf(tipIdx, b1, b1, b2))
        }

        // Base cap at y = 0.0f
        val botCenterIdx = (vList.size / 3).toShort()
        vList.addAll(listOf(0f, 0f, 0f))
        nList.addAll(listOf(0f, -1f, 0f))
        uvList.addAll(listOf(0.5f, 0.5f))

        for (i in 0 until segments) {
            val b1 = (baseStartIdx + i).toShort()
            val b2 = (baseStartIdx + i + 1).toShort()
            iList.addAll(listOf(botCenterIdx, b1, b2))
        }

        return Mesh(
            vertices = vList.toFloatArray(),
            normals = nList.toFloatArray(),
            uvs = uvList.toFloatArray(),
            indices = iList.toShortArray(),
            wireframeIndices = wList.toShortArray(),
            boundingBox = BoundingBox(Vector3(-radius, 0f, -radius), Vector3(radius, length, radius))
        )
    }

    /**
     * Flat horizontal 2D plane mesh.
     */
    fun createPlane(width: Float = 2f, depth: Float = 2f): Mesh {
        val hx = width / 2f
        val hz = depth / 2f

        val vertices = floatArrayOf(
            -hx, 0f, -hz,
             hx, 0f, -hz,
             hx, 0f,  hz,
            -hx, 0f,  hz
        )

        val normals = floatArrayOf(
            0f, 1f, 0f,
            0f, 1f, 0f,
            0f, 1f, 0f,
            0f, 1f, 0f
        )

        val uvs = floatArrayOf(
            0f, 0f,
            1f, 0f,
            1f, 1f,
            0f, 1f
        )

        // CCW when viewed from above (+Y)
        val indices = shortArrayOf(
            0, 3, 2,  0, 2, 1
        )

        val wireframe = shortArrayOf(
            0, 1,  1, 2,  2, 3,  3, 0
        )

        return Mesh(
            vertices = vertices,
            normals = normals,
            uvs = uvs,
            indices = indices,
            wireframeIndices = wireframe,
            boundingBox = BoundingBox(Vector3(-hx, -0.01f, -hz), Vector3(hx, 0.01f, hz))
        )
    }

    /**
     * Centered 3D conical primitive mesh.
     */
    fun createCone(radius: Float = 0.5f, height: Float = 1f, segments: Int = 24): Mesh {
        val halfH = height / 2f
        val vList = mutableListOf<Float>()
        val nList = mutableListOf<Float>()
        val uvList = mutableListOf<Float>()
        val iList = mutableListOf<Short>()
        val wList = mutableListOf<Short>()

        // Tip vertex at y = halfH
        val tipIdx = 0.toShort()
        vList.addAll(listOf(0f, halfH, 0f))
        nList.addAll(listOf(0f, 1f, 0f))
        uvList.addAll(listOf(0.5f, 1f))

        // Base ring vertices at y = -halfH for cone mantle
        val baseStartIdx = 1.toShort()
        for (i in 0..segments) {
            val theta = (i.toFloat() / segments) * 2f * PI.toFloat()
            val c = cos(theta)
            val s = sin(theta)
            vList.addAll(listOf(c * radius, -halfH, s * radius))
            val normal = Vector3(c, 0.4f, s).normalized()
            nList.addAll(listOf(normal.x, normal.y, normal.z))
            uvList.addAll(listOf(i.toFloat() / segments, 0f))
        }

        // Side faces (CCW outward winding)
        for (i in 0 until segments) {
            val b1 = (baseStartIdx + i).toShort()
            val b2 = (baseStartIdx + i + 1).toShort()
            iList.addAll(listOf(tipIdx, b2, b1))
            wList.addAll(listOf(tipIdx, b1, b1, b2))
        }

        // Base cap center
        val botCenterIdx = (vList.size / 3).toShort()
        vList.addAll(listOf(0f, -halfH, 0f))
        nList.addAll(listOf(0f, -1f, 0f))
        uvList.addAll(listOf(0.5f, 0.5f))

        // Dedicated base cap rim vertices with downward normal
        val botRimStart = (vList.size / 3).toShort()
        for (i in 0..segments) {
            val theta = (i.toFloat() / segments) * 2f * PI.toFloat()
            val c = cos(theta)
            val s = sin(theta)
            vList.addAll(listOf(c * radius, -halfH, s * radius))
            nList.addAll(listOf(0f, -1f, 0f))
            uvList.addAll(listOf(0.5f + 0.5f * c, 0.5f + 0.5f * s))
        }

        for (i in 0 until segments) {
            val r1 = (botRimStart + i).toShort()
            val r2 = (botRimStart + i + 1).toShort()
            // CCW pointing DOWN (-Y)
            iList.addAll(listOf(botCenterIdx, r1, r2))
        }

        return Mesh(
            vertices = vList.toFloatArray(),
            normals = nList.toFloatArray(),
            uvs = uvList.toFloatArray(),
            indices = iList.toShortArray(),
            wireframeIndices = wList.toShortArray(),
            boundingBox = BoundingBox(Vector3(-radius, -halfH, -radius), Vector3(radius, halfH, radius))
        )
    }

    /**
     * Centered 3D Torus (Donut Ring) primitive mesh.
     */
    fun createTorus(
        mainRadius: Float = 0.6f,
        tubeRadius: Float = 0.2f,
        mainSegments: Int = 24,
        tubeSegments: Int = 16
    ): Mesh {
        val vList = mutableListOf<Float>()
        val nList = mutableListOf<Float>()
        val uvList = mutableListOf<Float>()
        val iList = mutableListOf<Short>()
        val wList = mutableListOf<Short>()

        for (i in 0..mainSegments) {
            val u = (i.toFloat() / mainSegments) * 2f * PI.toFloat()
            val cosU = cos(u)
            val sinU = sin(u)

            for (j in 0..tubeSegments) {
                val v = (j.toFloat() / tubeSegments) * 2f * PI.toFloat()
                val cosV = cos(v)
                val sinV = sin(v)

                val x = (mainRadius + tubeRadius * cosV) * sinU
                val y = tubeRadius * sinV
                val z = (mainRadius + tubeRadius * cosV) * cosU

                vList.addAll(listOf(x, y, z))

                val nx = cosV * sinU
                val ny = sinV
                val nz = cosV * cosU
                nList.addAll(listOf(nx, ny, nz))

                uvList.addAll(listOf(i.toFloat() / mainSegments, j.toFloat() / tubeSegments))
            }
        }

        val stride = tubeSegments + 1
        for (i in 0 until mainSegments) {
            for (j in 0 until tubeSegments) {
                val current = (i * stride + j).toShort()
                val next = ((i + 1) * stride + j).toShort()

                val c1 = current
                val c2 = (current + 1).toShort()
                val n1 = next
                val n2 = (next + 1).toShort()

                iList.addAll(listOf(c1, n1, c2))
                iList.addAll(listOf(c2, n1, n2))

                // Complete 3D wireframe mesh grid (all edges connected)
                wList.addAll(listOf(c1, c2))
                wList.addAll(listOf(c1, n1))
            }
        }

        val totalR = mainRadius + tubeRadius
        return Mesh(
            vertices = vList.toFloatArray(),
            normals = nList.toFloatArray(),
            uvs = uvList.toFloatArray(),
            indices = iList.toShortArray(),
            wireframeIndices = wList.toShortArray(),
            boundingBox = BoundingBox(Vector3(-totalR, -tubeRadius, -totalR), Vector3(totalR, tubeRadius, totalR))
        )
    }

    /**
     * Dining Plate (Piring Makan Cekung) primitive mesh.
     * Solid seamless porcelain dining plate with sunken central basin, sloping cavity wall,
     * wide raised rim flange, smooth lip, and bottom foot ring.
     */
    fun createPlate(
        radius: Float = 1.35f,
        depth: Float = 0.16f,
        radialSegments: Int = 32
    ): Mesh {
        val vList = mutableListOf<Float>()
        val nList = mutableListOf<Float>()
        val uvList = mutableListOf<Float>()
        val iList = mutableListOf<Short>()
        val wList = mutableListOf<Short>()

        // 2D Profile points (r, y)
        val profileR = floatArrayOf(
            0.00f, 0.48f * radius, 0.68f * radius, 0.78f * radius,
            0.98f * radius, 1.00f * radius, 0.96f * radius, 0.68f * radius,
            0.55f * radius, 0.52f * radius, 0.48f * radius, 0.45f * radius, 0.00f
        )
        val profileY = floatArrayOf(
            0.00f, 0.010f * depth, 0.45f * depth, 0.85f * depth,
            1.00f * depth, 0.92f * depth, 0.75f * depth, 0.28f * depth,
            0.00f, -0.20f * depth, -0.20f * depth, 0.00f, -0.10f * depth
        )
        val numPoints = profileR.size

        // Calculate 2D profile normals (perpendicular to curve)
        val pNormalsR = FloatArray(numPoints)
        val pNormalsY = FloatArray(numPoints)

        for (k in 0 until numPoints) {
            val prevK = if (k > 0) k - 1 else k
            val nextK = if (k < numPoints - 1) k + 1 else k
            val dr = profileR[nextK] - profileR[prevK]
            val dy = profileY[nextK] - profileY[prevK]
            var nr = -dy
            var ny = dr
            val len = kotlin.math.sqrt(nr * nr + ny * ny)
            if (len > 0.0001f) {
                nr /= len
                ny /= len
            } else {
                nr = 0f
                ny = 1f
            }
            pNormalsR[k] = nr
            pNormalsY[k] = ny
        }

        for (i in 0..radialSegments) {
            val u = (i.toFloat() / radialSegments) * 2f * Math.PI.toFloat()
            val cosU = kotlin.math.cos(u)
            val sinU = kotlin.math.sin(u)

            for (j in 0 until numPoints) {
                val r = profileR[j]
                val y = profileY[j]

                val x = r * sinU
                val z = r * cosU
                vList.addAll(listOf(x, y, z))

                val nr = pNormalsR[j]
                val ny = pNormalsY[j]
                val nx = nr * sinU
                val nz = nr * cosU
                nList.addAll(listOf(nx, ny, nz))

                uvList.addAll(listOf(i.toFloat() / radialSegments, j.toFloat() / (numPoints - 1)))
            }
        }

        val stride = numPoints
        for (i in 0 until radialSegments) {
            for (j in 0 until numPoints - 1) {
                val current = (i * stride + j).toShort()
                val next = ((i + 1) * stride + j).toShort()

                val c1 = current
                val c2 = (current + 1).toShort()
                val n1 = next
                val n2 = (next + 1).toShort()

                iList.addAll(listOf(c1, n1, c2))
                iList.addAll(listOf(c2, n1, n2))

                wList.addAll(listOf(c1, c2))
                wList.addAll(listOf(c1, n1))
            }
        }

        val maxR = profileR.maxOrNull() ?: radius
        return Mesh(
            vertices = vList.toFloatArray(),
            normals = nList.toFloatArray(),
            uvs = uvList.toFloatArray(),
            indices = iList.toShortArray(),
            wireframeIndices = wList.toShortArray(),
            boundingBox = BoundingBox(
                Vector3(-maxR, -0.25f * depth, -maxR),
                Vector3(maxR, 1.1f * depth, maxR)
            )
        )
    }

    fun createSphere(radius: Float = 0.5f, rings: Int = 16, sectors: Int = 24): Mesh {
        val vList = mutableListOf<Float>()
        val nList = mutableListOf<Float>()
        val uvList = mutableListOf<Float>()
        val iList = mutableListOf<Short>()
        val wList = mutableListOf<Short>()

        for (r in 0..rings) {
            val phi = (PI.toFloat() * r) / rings
            val y = radius * cos(phi)
            val ringRadius = radius * sin(phi)

            for (s in 0..sectors) {
                val theta = (2f * PI.toFloat() * s) / sectors
                val x = ringRadius * sin(theta)
                val z = ringRadius * cos(theta)

                vList.addAll(listOf(x, y, z))
                val normal = Vector3(x, y, z).normalized()
                nList.addAll(listOf(normal.x, normal.y, normal.z))
                uvList.addAll(listOf(s.toFloat() / sectors, r.toFloat() / rings))
            }
        }

        for (r in 0 until rings) {
            for (s in 0 until sectors) {
                val cur = (r * (sectors + 1) + s).toShort()
                val next = (cur + sectors + 1).toShort()

                iList.addAll(listOf(cur, next, (cur + 1).toShort()))
                iList.addAll(listOf((cur + 1).toShort(), next, (next + 1).toShort()))

                // Complete 3D wireframe sphere grid (all edges connected)
                wList.addAll(listOf(cur, (cur + 1).toShort()))
                wList.addAll(listOf(cur, next))
            }
        }

        return Mesh(
            vertices = vList.toFloatArray(),
            normals = nList.toFloatArray(),
            uvs = uvList.toFloatArray(),
            indices = iList.toShortArray(),
            wireframeIndices = wList.toShortArray(),
            boundingBox = BoundingBox(Vector3(-radius, -radius, -radius), Vector3(radius, radius, radius))
        )
    }

    fun createWedge(width: Float = 1f, height: Float = 1f, depth: Float = 1f): Mesh {
        val hx = width / 2f
        val hy = height / 2f
        val hz = depth / 2f

        val v = floatArrayOf(
            // Bottom face (-Y)
            -hx, -hy, -hz,   hx, -hy, -hz,   hx, -hy,  hz,  -hx, -hy,  hz,
            // Back face (-Z)
             hx, -hy, -hz,  -hx, -hy, -hz,  -hx,  hy, -hz,   hx,  hy, -hz,
            // Sloped face (hypotenuse)
            -hx, -hy,  hz,   hx, -hy,  hz,   hx,  hy, -hz,  -hx,  hy, -hz,
            // Left triangle (-X)
            -hx, -hy, -hz,  -hx, -hy,  hz,  -hx,  hy, -hz,
            // Right triangle (+X)
             hx, -hy, -hz,   hx,  hy, -hz,   hx, -hy,  hz
        )

        // Normals computation
        val n = floatArrayOf(
            0f, -1f, 0f,  0f, -1f, 0f,  0f, -1f, 0f,  0f, -1f, 0f,
            0f, 0f, -1f,  0f, 0f, -1f,  0f, 0f, -1f,  0f, 0f, -1f,
            0f, 0.707f, 0.707f, 0f, 0.707f, 0.707f, 0f, 0.707f, 0.707f, 0f, 0.707f, 0.707f,
            -1f, 0f, 0f, -1f, 0f, 0f, -1f, 0f, 0f,
             1f, 0f, 0f,  1f, 0f, 0f,  1f, 0f, 0f
        )

        val uv = FloatArray(v.size / 3 * 2) { 0.5f }

        val indices = shortArrayOf(
            0, 1, 2,  0, 2, 3,        // Bottom
            4, 5, 6,  4, 6, 7,        // Back
            8, 9, 10,  8, 10, 11,     // Sloped
            12, 13, 14,               // Left
            15, 16, 17                // Right
        )

        val wireframe = shortArrayOf(
            0, 1,  1, 2,  2, 3,  3, 0,
            4, 5,  5, 6,  6, 7,  7, 4,
            8, 9,  9, 10, 10, 11, 11, 8
        )

        return Mesh(
            vertices = v,
            normals = n,
            uvs = uv,
            indices = indices,
            wireframeIndices = wireframe,
            boundingBox = BoundingBox(Vector3(-hx, -hy, -hz), Vector3(hx, hy, hz))
        )
    }

    /**
     * Subdivided floor grid lines (step = 1m).
     */
    fun createGroundGridSubdiv(size: Float = 60f, step: Float = 1f, majorStep: Float = 5f): Mesh {
        val lines = mutableListOf<Float>()
        val wIndices = mutableListOf<Short>()
        var idx: Short = 0

        val half = size / 2f
        var current = -half
        while (current <= half + 0.001f) {
            val isAxis = kotlin.math.abs(current) < 0.001f
            val isMajor = kotlin.math.abs(current % majorStep) < 0.001f || kotlin.math.abs((current % majorStep) - majorStep) < 0.001f
            if (!isAxis && !isMajor) {
                // Line along Z
                lines.addAll(listOf(current, 0f, -half))
                lines.addAll(listOf(current, 0f, half))
                wIndices.addAll(listOf(idx, (idx + 1).toShort()))
                idx = (idx + 2).toShort()

                // Line along X
                lines.addAll(listOf(-half, 0f, current))
                lines.addAll(listOf(half, 0f, current))
                wIndices.addAll(listOf(idx, (idx + 1).toShort()))
                idx = (idx + 2).toShort()
            }
            current += step
        }

        return Mesh(
            vertices = lines.toFloatArray(),
            normals = FloatArray(lines.size) { if (it % 3 == 1) 1f else 0f },
            uvs = FloatArray(lines.size / 3 * 2),
            indices = shortArrayOf(),
            wireframeIndices = wIndices.toShortArray(),
            boundingBox = BoundingBox(Vector3(-half, -0.01f, -half), Vector3(half, 0.01f, half))
        )
    }

    /**
     * Clearer major floor grid lines (step = 5m).
     */
    fun createGroundGridMajor(size: Float = 60f, majorStep: Float = 5f): Mesh {
        val lines = mutableListOf<Float>()
        val wIndices = mutableListOf<Short>()
        var idx: Short = 0

        val half = size / 2f
        var current = -half
        while (current <= half + 0.001f) {
            val isAxis = kotlin.math.abs(current) < 0.001f
            val isMajor = kotlin.math.abs(current % majorStep) < 0.001f || kotlin.math.abs((current % majorStep) - majorStep) < 0.001f
            if (!isAxis && isMajor) {
                // Line along Z with tiny vertical epsilon to prevent any coplanar interference
                lines.addAll(listOf(current, 0.0002f, -half))
                lines.addAll(listOf(current, 0.0002f, half))
                wIndices.addAll(listOf(idx, (idx + 1).toShort()))
                idx = (idx + 2).toShort()

                // Line along X
                lines.addAll(listOf(-half, 0.0002f, current))
                lines.addAll(listOf(half, 0.0002f, current))
                wIndices.addAll(listOf(idx, (idx + 1).toShort()))
                idx = (idx + 2).toShort()
            }
            current += 1.0f
        }

        return Mesh(
            vertices = lines.toFloatArray(),
            normals = FloatArray(lines.size) { if (it % 3 == 1) 1f else 0f },
            uvs = FloatArray(lines.size / 3 * 2),
            indices = shortArrayOf(),
            wireframeIndices = wIndices.toShortArray(),
            boundingBox = BoundingBox(Vector3(-half, -0.01f, -half), Vector3(half, 0.01f, half))
        )
    }

    /**
     * Red X-Axis Line on floor plane.
     */
    fun createFloorAxisX(size: Float = 60f): Mesh {
        val half = size / 2f
        val lines = floatArrayOf(-half, 0.0005f, 0f, half, 0.0005f, 0f)
        return Mesh(
            vertices = lines,
            normals = FloatArray(lines.size) { if (it % 3 == 1) 1f else 0f },
            uvs = FloatArray(lines.size / 3 * 2),
            indices = shortArrayOf(),
            wireframeIndices = shortArrayOf(0, 1),
            boundingBox = BoundingBox(Vector3(-half, -0.01f, -0.01f), Vector3(half, 0.01f, 0.01f))
        )
    }

    /**
     * Green Y-Axis Line on floor plane (perpendicular to X on floor).
     */
    fun createFloorAxisY(size: Float = 60f): Mesh {
        val half = size / 2f
        val lines = floatArrayOf(0f, 0.0005f, -half, 0f, 0.0005f, half)
        return Mesh(
            vertices = lines,
            normals = FloatArray(lines.size) { if (it % 3 == 1) 1f else 0f },
            uvs = FloatArray(lines.size / 3 * 2),
            indices = shortArrayOf(),
            wireframeIndices = shortArrayOf(0, 1),
            boundingBox = BoundingBox(Vector3(-0.01f, -0.01f, -half), Vector3(0.01f, 0.01f, half))
        )
    }

    /**
     * Infinite/Extensive ground plane for procedural anti-aliased Blender grid shader.
     */
    fun createGroundPlane(size: Float = 200f): Mesh {
        val half = size / 2f
        val vertices = floatArrayOf(
            -half, 0f, -half,
             half, 0f, -half,
             half, 0f,  half,
            -half, 0f,  half
        )
        val normals = floatArrayOf(
            0f, 1f, 0f,
            0f, 1f, 0f,
            0f, 1f, 0f,
            0f, 1f, 0f
        )
        val uvs = floatArrayOf(
            0f, 0f,
            1f, 0f,
            1f, 1f,
            0f, 1f
        )
        // Counter-Clockwise (CCW) when viewed from above (+Y)
        val indices = shortArrayOf(0, 3, 2, 0, 2, 1)
        return Mesh(
            vertices = vertices,
            normals = normals,
            uvs = uvs,
            indices = indices,
            wireframeIndices = shortArrayOf(),
            boundingBox = BoundingBox(Vector3(-half, -0.01f, -half), Vector3(half, 0.01f, half))
        )
    }

    /**
     * Dedicated line primitives for X and Z ground axes (never culled or clipped even at elevation 0°).
     */
    fun createFloorAxisLines(size: Float = 300f): Mesh {
        val half = size / 2f
        val vertices = floatArrayOf(
            -half, 0f, 0f,
             half, 0f, 0f,
            0f, 0f, -half,
            0f, 0f,  half
        )
        val normals = FloatArray(12) { if (it % 3 == 1) 1f else 0f }
        val uvs = FloatArray(8)
        val wireframeIndices = shortArrayOf(0, 1, 2, 3)
        return Mesh(
            vertices = vertices,
            normals = normals,
            uvs = uvs,
            indices = shortArrayOf(),
            wireframeIndices = wireframeIndices,
            boundingBox = BoundingBox(Vector3(-half, -0.01f, -half), Vector3(half, 0.01f, half))
        )
    }

    /**
     * Screen-aligned 2D quad for subtle background gradient rendering.
     */
    fun createFullscreenQuad(): Mesh {
        val vertices = floatArrayOf(
            -1f, -1f, 0f,
             1f, -1f, 0f,
             1f,  1f, 0f,
            -1f,  1f, 0f
        )
        // Counter-Clockwise (CCW) when viewed from above (+Y)
        val indices = shortArrayOf(0, 3, 2, 0, 2, 1)
        return Mesh(
            vertices = vertices,
            normals = FloatArray(12),
            uvs = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f),
            indices = indices,
            wireframeIndices = shortArrayOf(),
            boundingBox = BoundingBox(Vector3(-1f, -1f, 0f), Vector3(1f, 1f, 0f))
        )
    }
}
