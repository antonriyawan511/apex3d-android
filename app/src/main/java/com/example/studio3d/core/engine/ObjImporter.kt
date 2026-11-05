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
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.math.max
import kotlin.math.min

/**
 * Ultra-fast, zero-boxing OBJ Parser designed for large 3D models (e.g. 50MB - 100MB+ models)
 * on Android with full 32-bit GL_UNSIGNED_INT index support.
 */
object ObjImporter {

    class FloatArrayBuffer(initialCapacity: Int = 131072) {
        var data = FloatArray(initialCapacity)
        var size = 0
            private set

        fun add(v: Float) {
            if (size == data.size) {
                val newCap = if (data.size < 1048576) data.size * 2 else data.size + (data.size shr 1)
                data = data.copyOf(newCap)
            }
            data[size++] = v
        }

        fun add(x: Float, y: Float, z: Float) {
            if (size + 3 > data.size) {
                val newCap = if (data.size < 1048576) data.size * 2 else data.size + (data.size shr 1)
                data = data.copyOf(max(newCap, size + 3))
            }
            data[size++] = x
            data[size++] = y
            data[size++] = z
        }

        fun toTrimmedArray(): FloatArray {
            return if (size == data.size) data else data.copyOf(size)
        }
    }

    class IntArrayBuffer(initialCapacity: Int = 131072) {
        var data = IntArray(initialCapacity)
        var size = 0
            private set

        fun add(v: Int) {
            if (size == data.size) {
                val newCap = if (data.size < 1048576) data.size * 2 else data.size + (data.size shr 1)
                data = data.copyOf(newCap)
            }
            data[size++] = v
        }

        fun toTrimmedArray(): IntArray {
            return if (size == data.size) data else data.copyOf(size)
        }

        fun toShortArray(): ShortArray {
            val arr = ShortArray(size)
            for (i in 0 until size) {
                arr[i] = data[i].toShort()
            }
            return arr
        }
    }

    fun parseObj(inputStream: InputStream): Mesh {
        // Raw buffers for vertex attributes (1D primitive float arrays)
        val rawPositions = FloatArrayBuffer(262144) // 3 floats per vertex (x, y, z)
        val rawNormals = FloatArrayBuffer(262144)   // 3 floats per normal (nx, ny, nz)
        val rawUVs = FloatArrayBuffer(131072)       // 2 floats per UV (u, v)

        // Output mesh buffers
        val outVertices = FloatArrayBuffer(524288)
        val outNormals = FloatArrayBuffer(524288)
        val outUVs = FloatArrayBuffer(262144)
        val outIndices = IntArrayBuffer(524288)
        val outWireframe = IntArrayBuffer(262144)

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE

        // Temporary arrays for polygon face decoding
        val facePos = IntArray(32)
        val faceUv = IntArray(32)
        val faceNorm = IntArray(32)

        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8), 65536)

        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: break
                val len = l.length
                if (len < 2) continue

                val c0 = l[0]
                val c1 = l[1]

                if (c0 == 'v') {
                    if (c1 == ' ' || c1 == '\t') { // "v x y z"
                        var idx = 2
                        while (idx < len && (l[idx] == ' ' || l[idx] == '\t')) idx++
                        val xEnd = findNextWhitespace(l, idx, len)
                        val x = fastParseFloat(l, idx, xEnd)

                        idx = skipWhitespace(l, xEnd, len)
                        val yEnd = findNextWhitespace(l, idx, len)
                        val y = fastParseFloat(l, idx, yEnd)

                        idx = skipWhitespace(l, yEnd, len)
                        val zEnd = findNextWhitespace(l, idx, len)
                        val z = fastParseFloat(l, idx, zEnd)

                        rawPositions.add(x, y, z)

                        minX = min(minX, x)
                        minY = min(minY, y)
                        minZ = min(minZ, z)
                        maxX = max(maxX, x)
                        maxY = max(maxY, y)
                        maxZ = max(maxZ, z)
                    } else if (c1 == 'n') { // "vn nx ny nz"
                        var idx = 2
                        idx = skipWhitespace(l, idx, len)
                        val xEnd = findNextWhitespace(l, idx, len)
                        val nx = fastParseFloat(l, idx, xEnd)

                        idx = skipWhitespace(l, xEnd, len)
                        val yEnd = findNextWhitespace(l, idx, len)
                        val ny = fastParseFloat(l, idx, yEnd)

                        idx = skipWhitespace(l, yEnd, len)
                        val zEnd = findNextWhitespace(l, idx, len)
                        val nz = fastParseFloat(l, idx, zEnd)

                        rawNormals.add(nx, ny, nz)
                    } else if (c1 == 't') { // "vt u v"
                        var idx = 2
                        idx = skipWhitespace(l, idx, len)
                        val uEnd = findNextWhitespace(l, idx, len)
                        val u = fastParseFloat(l, idx, uEnd)

                        idx = skipWhitespace(l, uEnd, len)
                        val vEnd = findNextWhitespace(l, idx, len)
                        val v = fastParseFloat(l, idx, vEnd)

                        rawUVs.add(u)
                        rawUVs.add(v)
                    }
                } else if (c0 == 'f' && (c1 == ' ' || c1 == '\t')) { // "f v/vt/vn ..."
                    var idx = skipWhitespace(l, 2, len)
                    var vertCount = 0

                    val totalPositions = rawPositions.size / 3
                    val totalNormals = rawNormals.size / 3
                    val totalUVs = rawUVs.size / 2

                    while (idx < len && vertCount < 32) {
                        val tokenEnd = findNextWhitespace(l, idx, len)

                        // Parse "pos/uv/norm" inside token
                        var slash1 = -1
                        var slash2 = -1
                        for (i in idx until tokenEnd) {
                            if (l[i] == '/') {
                                if (slash1 == -1) slash1 = i else if (slash2 == -1) slash2 = i
                            }
                        }

                        // Position index
                        val pEnd = if (slash1 != -1) slash1 else tokenEnd
                        val rawP = fastParseInt(l, idx, pEnd)
                        facePos[vertCount] = if (rawP < 0) totalPositions + rawP else rawP - 1

                        // UV index
                        if (slash1 != -1) {
                            val uvStart = slash1 + 1
                            val uvEnd = if (slash2 != -1) slash2 else tokenEnd
                            if (uvEnd > uvStart) {
                                val rawUv = fastParseInt(l, uvStart, uvEnd)
                                faceUv[vertCount] = if (rawUv < 0) totalUVs + rawUv else rawUv - 1
                            } else {
                                faceUv[vertCount] = -1
                            }
                        } else {
                            faceUv[vertCount] = -1
                        }

                        // Normal index
                        if (slash2 != -1) {
                            val normStart = slash2 + 1
                            if (tokenEnd > normStart) {
                                val rawNorm = fastParseInt(l, normStart, tokenEnd)
                                faceNorm[vertCount] = if (rawNorm < 0) totalNormals + rawNorm else rawNorm - 1
                            } else {
                                faceNorm[vertCount] = -1
                            }
                        } else {
                            faceNorm[vertCount] = -1
                        }

                        vertCount++
                        idx = skipWhitespace(l, tokenEnd, len)
                    }

                    // Triangulate face using fan triangulation
                    if (vertCount >= 3) {
                        for (i in 1 until vertCount - 1) {
                            val v0 = appendVertex(0, facePos, faceUv, faceNorm, rawPositions, rawNormals, rawUVs, outVertices, outNormals, outUVs)
                            val v1 = appendVertex(i, facePos, faceUv, faceNorm, rawPositions, rawNormals, rawUVs, outVertices, outNormals, outUVs)
                            val v2 = appendVertex(i + 1, facePos, faceUv, faceNorm, rawPositions, rawNormals, rawUVs, outVertices, outNormals, outUVs)

                            outIndices.add(v0)
                            outIndices.add(v1)
                            outIndices.add(v2)

                            // Generate wireframe edges (limit to meshes with < 200k triangles for mobile GPU performance)
                            if (outIndices.size < 600000) {
                                outWireframe.add(v0)
                                outWireframe.add(v1)
                                outWireframe.add(v1)
                                outWireframe.add(v2)
                                outWireframe.add(v2)
                                outWireframe.add(v0)
                            }
                        }
                    }
                }
            }
        } finally {
            reader.close()
        }

        // Center model on ground floor (X=0, Z=0, bottom sitting on Y=0)
        // and normalize scale if the model was exported in huge units (e.g. millimeters / micrometers)
        val validMinX = if (minX != Float.MAX_VALUE) minX else -0.5f
        val validMinY = if (minY != Float.MAX_VALUE) minY else 0f
        val validMinZ = if (minZ != Float.MAX_VALUE) minZ else -0.5f
        val validMaxX = if (maxX != -Float.MAX_VALUE) maxX else 0.5f
        val validMaxY = if (maxY != -Float.MAX_VALUE) maxY else 1f
        val validMaxZ = if (maxZ != -Float.MAX_VALUE) maxZ else 0.5f

        val sizeX = validMaxX - validMinX
        val sizeY = validMaxY - validMinY
        val sizeZ = validMaxZ - validMinZ
        val maxDim = max(sizeX, max(sizeY, sizeZ))

        val centerX = (validMinX + validMaxX) / 2f
        val centerZ = (validMinZ + validMaxZ) / 2f
        val bottomY = validMinY

        // Auto-fit scale if model dimensions are extreme (> 50m or < 0.1m)
        val autoScale = if (maxDim > 50f || maxDim < 0.1f) 3.0f / max(0.001f, maxDim) else 1.0f

        val vData = outVertices.data
        val vCount = outVertices.size
        var i = 0
        while (i < vCount) {
            vData[i] = (vData[i] - centerX) * autoScale
            vData[i + 1] = (vData[i + 1] - bottomY) * autoScale
            vData[i + 2] = (vData[i + 2] - centerZ) * autoScale
            i += 3
        }

        val bMin = Vector3((validMinX - centerX) * autoScale, 0f, (validMinZ - centerZ) * autoScale)
        val bMax = Vector3((validMaxX - centerX) * autoScale, sizeY * autoScale, (validMaxZ - centerZ) * autoScale)

        return Mesh(
            vertices = outVertices.toTrimmedArray(),
            normals = outNormals.toTrimmedArray(),
            uvs = outUVs.toTrimmedArray(),
            indices = outIndices.toTrimmedArray(),
            wireframeIndices = outWireframe.toTrimmedArray(),
            boundingBox = BoundingBox(bMin, bMax)
        )
    }

    private fun appendVertex(
        slot: Int,
        facePos: IntArray,
        faceUv: IntArray,
        faceNorm: IntArray,
        rawPositions: FloatArrayBuffer,
        rawNormals: FloatArrayBuffer,
        rawUVs: FloatArrayBuffer,
        outVertices: FloatArrayBuffer,
        outNormals: FloatArrayBuffer,
        outUVs: FloatArrayBuffer
    ): Int {
        val pIdx = facePos[slot]
        val uvIdx = faceUv[slot]
        val nIdx = faceNorm[slot]

        val newIdx = outVertices.size / 3

        // Position (x, y, z)
        val pData = rawPositions.data
        if (pIdx >= 0 && (pIdx * 3 + 2) < rawPositions.size) {
            val base = pIdx * 3
            outVertices.add(pData[base], pData[base + 1], pData[base + 2])
        } else {
            outVertices.add(0f, 0f, 0f)
        }

        // Normal (nx, ny, nz)
        val nData = rawNormals.data
        if (nIdx >= 0 && (nIdx * 3 + 2) < rawNormals.size) {
            val base = nIdx * 3
            outNormals.add(nData[base], nData[base + 1], nData[base + 2])
        } else {
            outNormals.add(0f, 1f, 0f)
        }

        // UV (u, v)
        val uvData = rawUVs.data
        if (uvIdx >= 0 && (uvIdx * 2 + 1) < rawUVs.size) {
            val base = uvIdx * 2
            outUVs.add(uvData[base])
            outUVs.add(uvData[base + 1])
        } else {
            outUVs.add(0f)
            outUVs.add(0f)
        }

        return newIdx
    }

    private fun skipWhitespace(s: String, start: Int, end: Int): Int {
        var i = start
        while (i < end && (s[i] == ' ' || s[i] == '\t')) i++
        return i
    }

    private fun findNextWhitespace(s: String, start: Int, end: Int): Int {
        var i = start
        while (i < end && s[i] != ' ' && s[i] != '\t') i++
        return i
    }

    private fun fastParseInt(s: String, start: Int, end: Int): Int {
        if (start >= end) return 0
        var i = start
        var sign = 1
        if (s[i] == '-') {
            sign = -1
            i++
        } else if (s[i] == '+') {
            i++
        }
        var result = 0
        while (i < end) {
            val c = s[i]
            if (c in '0'..'9') {
                result = result * 10 + (c - '0')
            } else {
                break
            }
            i++
        }
        return result * sign
    }

    private fun fastParseFloat(s: String, start: Int, end: Int): Float {
        if (start >= end) return 0f
        return try {
            s.substring(start, end).toFloat()
        } catch (_: Exception) {
            0f
        }
    }
}
