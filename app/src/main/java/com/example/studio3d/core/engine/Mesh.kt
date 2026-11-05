package com.example.studio3d.core.engine

import com.example.studio3d.core.math.*
import com.example.studio3d.core.engine.*
import com.example.studio3d.core.scene.*
import com.example.studio3d.core.history.*
import com.example.studio3d.core.preferences.*
import com.example.studio3d.core.project.*
import com.example.studio3d.feature.editor.viewport.*
import com.example.studio3d.feature.editor.tools.select.*


import android.opengl.GLES30
import com.example.studio3d.core.math.BoundingBox
import com.example.studio3d.core.math.Vector3
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer

class Mesh(
    val vertices: FloatArray,
    val normals: FloatArray,
    val uvs: FloatArray,
    val indices: IntArray,
    val wireframeIndices: IntArray,
    var boundingBox: BoundingBox
) {
    // Secondary constructor for backwards compatibility with primitive shapes (ShortArray)
    constructor(
        vertices: FloatArray,
        normals: FloatArray,
        uvs: FloatArray,
        indices: ShortArray,
        wireframeIndices: ShortArray,
        boundingBox: BoundingBox
    ) : this(
        vertices,
        normals,
        uvs,
        IntArray(indices.size) { indices[it].toInt() and 0xFFFF },
        IntArray(wireframeIndices.size) { wireframeIndices[it].toInt() and 0xFFFF },
        boundingBox
    )
    var vaoId = 0
    private var vboVertices = 0
    private var vboNormals = 0
    private var vboUvs = 0
    var eboTriangles = 0
        internal set
    var eboWireframe = 0
        internal set
    var isUploaded = false
        private set
    var isGeometryDirty = false
        private set

    fun markClean() {
        isGeometryDirty = false
    }

    fun markGeometryDirty() {
        isGeometryDirty = true
    }

    fun getVertex(index: Int): Vector3 {
        val offset = index * 3
        if (offset + 2 >= vertices.size) return Vector3.ZERO
        return Vector3(vertices[offset], vertices[offset + 1], vertices[offset + 2])
    }

    fun getNormal(index: Int): Vector3 {
        val offset = index * 3
        if (offset + 2 >= normals.size) return Vector3.ZERO
        return Vector3(normals[offset], normals[offset + 1], normals[offset + 2])
    }

    fun setVertex(index: Int, pos: Vector3) {
        val offset = index * 3
        if (offset + 2 < vertices.size) {
            vertices[offset] = pos.x
            vertices[offset + 1] = pos.y
            vertices[offset + 2] = pos.z
            isGeometryDirty = true
        }
    }

    fun recalculateNormalsAndBounds() {
        val vCount = vertices.size / 3
        if (vCount == 0) return

        // 1. Reset normals to zero
        for (i in normals.indices) {
            normals[i] = 0f
        }

        // 2. Compute bounding box
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE

        for (v in 0 until vCount) {
            val vx = vertices[v * 3]
            val vy = vertices[v * 3 + 1]
            val vz = vertices[v * 3 + 2]
            if (vx < minX) minX = vx
            if (vx > maxX) maxX = vx
            if (vy < minY) minY = vy
            if (vy > maxY) maxY = vy
            if (vz < minZ) minZ = vz
            if (vz > maxZ) maxZ = vz
        }
        boundingBox = BoundingBox(Vector3(minX, minY, minZ), Vector3(maxX, maxY, maxZ))

        // 3. Accumulate face area-weighted normals for every triangle
        val triCount = indices.size / 3
        for (t in 0 until triCount) {
            val i0 = indices[t * 3]
            val i1 = indices[t * 3 + 1]
            val i2 = indices[t * 3 + 2]

            val o0 = i0 * 3
            val o1 = i1 * 3
            val o2 = i2 * 3

            val x0 = vertices[o0]
            val y0 = vertices[o0 + 1]
            val z0 = vertices[o0 + 2]

            val x1 = vertices[o1]
            val y1 = vertices[o1 + 1]
            val z1 = vertices[o1 + 2]

            val x2 = vertices[o2]
            val y2 = vertices[o2 + 1]
            val z2 = vertices[o2 + 2]

            val e1x = x1 - x0
            val e1y = y1 - y0
            val e1z = z1 - z0

            val e2x = x2 - x0
            val e2y = y2 - y0
            val e2z = z2 - z0

            // Cross product: (e1 x e2)
            val nx = e1y * e2z - e1z * e2y
            val ny = e1z * e2x - e1x * e2z
            val nz = e1x * e2y - e1y * e2x

            normals[o0] += nx
            normals[o0 + 1] += ny
            normals[o0 + 2] += nz

            normals[o1] += nx
            normals[o1 + 1] += ny
            normals[o1 + 2] += nz

            normals[o2] += nx
            normals[o2 + 1] += ny
            normals[o2 + 2] += nz
        }

        // 4. Normalize all vertex normals
        for (v in 0 until vCount) {
            val o = v * 3
            val nx = normals[o]
            val ny = normals[o + 1]
            val nz = normals[o + 2]
            val len = kotlin.math.sqrt(nx * nx + ny * ny + nz * nz)
            if (len > 0.00001f) {
                normals[o] = nx / len
                normals[o + 1] = ny / len
                normals[o + 2] = nz / len
            } else {
                normals[o] = 0f
                normals[o + 1] = 1f
                normals[o + 2] = 0f
            }
        }

        isGeometryDirty = true
    }

    fun updateBuffersOnGL() {
        if (!isUploaded) {
            uploadToGL()
            return
        }
        val vBuffer = ByteBuffer.allocateDirect(vertices.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply {
                put(vertices)
                position(0)
            }
        }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboVertices)
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, vertices.size * 4, vBuffer)

        val nBuffer = ByteBuffer.allocateDirect(normals.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply {
                put(normals)
                position(0)
            }
        }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboNormals)
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, normals.size * 4, nBuffer)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    @Transient
    private var _topology: MeshTopology? = null
    val topology: MeshTopology
        get() = _topology ?: MeshTopology(this).also { _topology = it }

    fun invalidateTopology() {
        _topology = null
    }

    fun uploadToGL() {
        if (isUploaded) return

        val vaos = IntArray(1)
        GLES30.glGenVertexArrays(1, vaos, 0)
        vaoId = vaos[0]
        GLES30.glBindVertexArray(vaoId)

        val buffers = IntArray(5)
        GLES30.glGenBuffers(5, buffers, 0)
        vboVertices = buffers[0]
        vboNormals = buffers[1]
        vboUvs = buffers[2]
        eboTriangles = buffers[3]
        eboWireframe = buffers[4]

        // Vertices (attribute 0)
        val vBuffer = ByteBuffer.allocateDirect(vertices.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply {
                put(vertices)
                position(0)
            }
        }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboVertices)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * 4, vBuffer, GLES30.GL_STATIC_DRAW)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glEnableVertexAttribArray(0)

        // Normals (attribute 1)
        val nBuffer = ByteBuffer.allocateDirect(normals.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply {
                put(normals)
                position(0)
            }
        }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboNormals)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, normals.size * 4, nBuffer, GLES30.GL_STATIC_DRAW)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glEnableVertexAttribArray(1)

        // UVs (attribute 2)
        val uvBuffer = ByteBuffer.allocateDirect(uvs.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply {
                put(uvs)
                position(0)
            }
        }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboUvs)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, uvs.size * 4, uvBuffer, GLES30.GL_STATIC_DRAW)
        GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glEnableVertexAttribArray(2)

        // Element Buffer Object (Triangles) - Supports up to 2+ billion indices with GL_UNSIGNED_INT
        val iBuffer = ByteBuffer.allocateDirect(indices.size * 4).run {
            order(ByteOrder.nativeOrder())
            asIntBuffer().apply {
                put(indices)
                position(0)
            }
        }
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, eboTriangles)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.size * 4, iBuffer, GLES30.GL_STATIC_DRAW)

        GLES30.glBindVertexArray(0)

        // Element Buffer Object (Wireframe lines)
        val wBuffer = ByteBuffer.allocateDirect(wireframeIndices.size * 4).run {
            order(ByteOrder.nativeOrder())
            asIntBuffer().apply {
                put(wireframeIndices)
                position(0)
            }
        }
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, eboWireframe)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, wireframeIndices.size * 4, wBuffer, GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)

        isUploaded = true
    }

    fun renderTriangles() {
        if (!isUploaded) uploadToGL()
        if (isGeometryDirty) {
            updateBuffersOnGL()
            isGeometryDirty = false
        }
        GLES30.glBindVertexArray(vaoId)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, eboTriangles)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indices.size, GLES30.GL_UNSIGNED_INT, 0)
        GLES30.glBindVertexArray(0)
    }

    fun renderWireframe() {
        if (!isUploaded) uploadToGL()
        if (isGeometryDirty) {
            updateBuffersOnGL()
            isGeometryDirty = false
        }
        GLES30.glBindVertexArray(vaoId)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, eboWireframe)
        GLES30.glDrawElements(GLES30.GL_LINES, wireframeIndices.size, GLES30.GL_UNSIGNED_INT, 0)
        GLES30.glBindVertexArray(0)
    }

    fun release() {
        if (!isUploaded) return
        val vaos = intArrayOf(vaoId)
        GLES30.glDeleteVertexArrays(1, vaos, 0)
        val buffers = intArrayOf(vboVertices, vboNormals, vboUvs, eboTriangles, eboWireframe)
        GLES30.glDeleteBuffers(5, buffers, 0)
        isUploaded = false
        vaoId = 0
    }
}
