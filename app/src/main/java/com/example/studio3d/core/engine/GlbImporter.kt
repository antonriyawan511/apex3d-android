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
import org.json.JSONObject
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

data class GlbResult(
    val mesh: Mesh,
    val textureBytes: ByteArray? = null,
    val textureMime: String? = null,
    val textureName: String? = null
)

object GlbImporter {

    private const val GLTF_MAGIC = 0x46546C67 // "glTF"
    private const val CHUNK_JSON = 0x4E4F534A // "JSON"
    private const val CHUNK_BIN = 0x004E4942  // "BIN\0"

    fun parseGlb(inputStream: InputStream): GlbResult {
        val bytes = inputStream.readBytes()
        if (bytes.size < 20) {
            throw IllegalArgumentException("File too small to be a valid .GLB model")
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // 1. Read Header (12 bytes)
        val magic = buffer.int
        if (magic != GLTF_MAGIC) {
            throw IllegalArgumentException("Invalid GLB header: magic mismatch")
        }

        val version = buffer.int
        if (version < 2) {
            throw IllegalArgumentException("Unsupported glTF version: $version. Only glTF 2.0 supported")
        }

        val totalLength = buffer.int

        // 2. Read Chunk 0 (JSON)
        if (buffer.remaining() < 8) {
            throw IllegalArgumentException("Invalid GLB chunk structure")
        }

        val jsonChunkLength = buffer.int
        val jsonChunkType = buffer.int

        if (jsonChunkType != CHUNK_JSON) {
            throw IllegalArgumentException("Expected JSON chunk 0 in GLB file")
        }

        val jsonBytes = ByteArray(jsonChunkLength)
        buffer.get(jsonBytes)
        val jsonString = String(jsonBytes, Charsets.UTF_8)
        val json = JSONObject(jsonString)

        // 3. Read Chunk 1 (BIN)
        var binBytes = ByteArray(0)
        if (buffer.remaining() >= 8) {
            val binChunkLength = buffer.int
            val binChunkType = buffer.int
            if (binChunkType == CHUNK_BIN && buffer.remaining() >= binChunkLength) {
                binBytes = ByteArray(binChunkLength)
                buffer.get(binBytes)
            }
        }

        val jsonAccessors = json.optJSONArray("accessors")
        val jsonBufferViews = json.optJSONArray("bufferViews")
        val jsonMeshes = json.optJSONArray("meshes")

        if (jsonMeshes == null || jsonMeshes.length() == 0) {
            throw IllegalArgumentException("No meshes found in GLB file")
        }

        // Cache decoded/sliced buffer views
        val bufferViewBuffers = if (jsonBufferViews != null) arrayOfNulls<ByteBuffer>(jsonBufferViews.length()) else emptyArray()

        fun getBufferViewBuffer(viewIdx: Int): ByteBuffer {
            bufferViewBuffers[viewIdx]?.let { return it.duplicate().order(ByteOrder.LITTLE_ENDIAN) }
            val bv = jsonBufferViews!!.getJSONObject(viewIdx)

            val extObj = bv.optJSONObject("extensions")?.optJSONObject("EXT_meshopt_compression")
            val byteBuffer = if (extObj != null) {
                val count = extObj.getInt("count")
                val byteStride = extObj.getInt("byteStride")
                val compByteOffset = extObj.optInt("byteOffset", 0)
                val compByteLength = extObj.getInt("byteLength")
                val mode = extObj.getString("mode")
                val filter = if (extObj.has("filter")) extObj.getString("filter") else null

                val compSrc = ByteArray(compByteLength)
                System.arraycopy(binBytes, compByteOffset, compSrc, 0, compByteLength)

                val decompTarget = ByteArray(count * byteStride)
                MeshoptDecoder.decodeGltfBuffer(decompTarget, count, byteStride, compSrc, mode, filter)
                ByteBuffer.wrap(decompTarget).order(ByteOrder.LITTLE_ENDIAN)
            } else {
                val byteOffset = bv.optInt("byteOffset", 0)
                val byteLength = bv.getInt("byteLength")
                ByteBuffer.wrap(binBytes, byteOffset, byteLength).order(ByteOrder.LITTLE_ENDIAN)
            }

            bufferViewBuffers[viewIdx] = byteBuffer
            return byteBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        }

        val outVertices = ObjImporter.FloatArrayBuffer(65536)
        val outNormals = ObjImporter.FloatArrayBuffer(65536)
        val outUVs = ObjImporter.FloatArrayBuffer(65536)
        val outIndices = ObjImporter.IntArrayBuffer(65536)
        val outWireframe = ObjImporter.IntArrayBuffer(65536)

        for (mIdx in 0 until jsonMeshes.length()) {
            val meshObj = jsonMeshes.getJSONObject(mIdx)
            val primitives = meshObj.optJSONArray("primitives") ?: continue

            for (pIdx in 0 until primitives.length()) {
                val primitive = primitives.getJSONObject(pIdx)
                val attributes = primitive.optJSONObject("attributes") ?: continue

                val posAccIdx = attributes.optInt("POSITION", -1)
                if (posAccIdx == -1) continue

                val normAccIdx = attributes.optInt("NORMAL", -1)
                val uvAccIdx = attributes.optInt("TEXCOORD_0", -1)
                val indicesAccIdx = primitive.optInt("indices", -1)

                val baseVertOffset = outVertices.size / 3

                // 1. POSITION
                val posAcc = jsonAccessors!!.getJSONObject(posAccIdx)
                val posViewIdx = posAcc.getInt("bufferView")
                val posCount = posAcc.getInt("count")
                val posCompType = posAcc.optInt("componentType", 5126)
                val posNormalized = posAcc.optBoolean("normalized", false)
                val posAccOffset = posAcc.optInt("byteOffset", 0)

                val posView = jsonBufferViews!!.getJSONObject(posViewIdx)
                val posByteStride = posView.optInt("byteStride", if (posCompType == 5126) 12 else 6)

                val posBuf = getBufferViewBuffer(posViewIdx)
                for (i in 0 until posCount) {
                    val offset = posAccOffset + i * posByteStride
                    posBuf.position(offset)
                    val x = readComponentAsFloat(posBuf, posCompType, posNormalized)
                    val y = readComponentAsFloat(posBuf, posCompType, posNormalized)
                    val z = readComponentAsFloat(posBuf, posCompType, posNormalized)
                    outVertices.add(x)
                    outVertices.add(y)
                    outVertices.add(z)
                }

                // 2. NORMAL
                if (normAccIdx != -1) {
                    val normAcc = jsonAccessors.getJSONObject(normAccIdx)
                    val normViewIdx = normAcc.getInt("bufferView")
                    val normCount = normAcc.getInt("count")
                    val normCompType = normAcc.optInt("componentType", 5126)
                    val normNormalized = normAcc.optBoolean("normalized", normCompType != 5126)
                    val normAccOffset = normAcc.optInt("byteOffset", 0)

                    val normView = jsonBufferViews.getJSONObject(normViewIdx)
                    val normByteStride = normView.optInt("byteStride", when (normCompType) {
                        5126 -> 12
                        5120, 5121 -> 4
                        5122, 5123 -> 8
                        else -> 12
                    })

                    val normBuf = getBufferViewBuffer(normViewIdx)
                    for (i in 0 until normCount) {
                        val offset = normAccOffset + i * normByteStride
                        normBuf.position(offset)
                        val nx = readComponentAsFloat(normBuf, normCompType, normNormalized)
                        val ny = readComponentAsFloat(normBuf, normCompType, normNormalized)
                        val nz = readComponentAsFloat(normBuf, normCompType, normNormalized)
                        val len = kotlin.math.sqrt(nx * nx + ny * ny + nz * nz)
                        if (len > 0.0001f) {
                            outNormals.add(nx / len)
                            outNormals.add(ny / len)
                            outNormals.add(nz / len)
                        } else {
                            outNormals.add(0f)
                            outNormals.add(1f)
                            outNormals.add(0f)
                        }
                    }
                } else {
                    for (i in 0 until posCount) {
                        outNormals.add(0f)
                        outNormals.add(1f)
                        outNormals.add(0f)
                    }
                }

                // 3. TEXCOORD_0
                if (uvAccIdx != -1) {
                    val uvAcc = jsonAccessors.getJSONObject(uvAccIdx)
                    val uvViewIdx = uvAcc.getInt("bufferView")
                    val uvCount = uvAcc.getInt("count")
                    val uvCompType = uvAcc.optInt("componentType", 5126)
                    val uvNormalized = uvAcc.optBoolean("normalized", uvCompType != 5126)
                    val uvAccOffset = uvAcc.optInt("byteOffset", 0)

                    val uvView = jsonBufferViews.getJSONObject(uvViewIdx)
                    val uvByteStride = uvView.optInt("byteStride", if (uvCompType == 5126) 8 else 4)

                    val uvBuf = getBufferViewBuffer(uvViewIdx)
                    for (i in 0 until uvCount) {
                        val offset = uvAccOffset + i * uvByteStride
                        uvBuf.position(offset)
                        val u = readComponentAsFloat(uvBuf, uvCompType, uvNormalized)
                        val v = readComponentAsFloat(uvBuf, uvCompType, uvNormalized)
                        outUVs.add(u)
                        outUVs.add(v)
                    }
                } else {
                    for (i in 0 until posCount) {
                        outUVs.add(0f)
                        outUVs.add(0f)
                    }
                }

                // 4. INDICES
                if (indicesAccIdx != -1) {
                    val indAcc = jsonAccessors.getJSONObject(indicesAccIdx)
                    val indViewIdx = indAcc.getInt("bufferView")
                    val indCount = indAcc.getInt("count")
                    val indCompType = indAcc.getInt("componentType")
                    val indAccOffset = indAcc.optInt("byteOffset", 0)

                    val indBuf = getBufferViewBuffer(indViewIdx)
                    indBuf.position(indAccOffset)

                    val startIdx = outIndices.size
                    for (i in 0 until indCount) {
                        val rawIndex = when (indCompType) {
                            5121 -> indBuf.get().toInt() and 0xFF
                            5123 -> indBuf.short.toInt() and 0xFFFF
                            5125 -> indBuf.int
                            else -> indBuf.short.toInt() and 0xFFFF
                        }
                        outIndices.add(baseVertOffset + rawIndex)
                    }

                    if (outIndices.size < 600000) {
                        val primIndexCount = outIndices.size - startIdx
                        for (i in 0 until primIndexCount step 3) {
                            if (i + 2 < primIndexCount) {
                                val v0 = outIndices.data[startIdx + i]
                                val v1 = outIndices.data[startIdx + i + 1]
                                val v2 = outIndices.data[startIdx + i + 2]

                                outWireframe.add(v0)
                                outWireframe.add(v1)
                                outWireframe.add(v1)
                                outWireframe.add(v2)
                                outWireframe.add(v2)
                                outWireframe.add(v0)
                            }
                        }
                    }
                } else {
                    val startIdx = outIndices.size
                    for (i in 0 until posCount) {
                        outIndices.add(baseVertOffset + i)
                    }
                    if (outIndices.size < 600000) {
                        for (i in 0 until posCount step 3) {
                            if (i + 2 < posCount) {
                                val v0 = baseVertOffset + i
                                val v1 = baseVertOffset + i + 1
                                val v2 = baseVertOffset + i + 2

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
        }

        // Auto-center and rest bottom on Y = 0
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE

        val rawVertices = outVertices.toTrimmedArray()
        val numVerts = rawVertices.size / 3
        for (i in 0 until numVerts) {
            val vx = rawVertices[i * 3]
            val vy = rawVertices[i * 3 + 1]
            val vz = rawVertices[i * 3 + 2]
            minX = min(minX, vx)
            minY = min(minY, vy)
            minZ = min(minZ, vz)
            maxX = max(maxX, vx)
            maxY = max(maxY, vy)
            maxZ = max(maxZ, vz)
        }

        val centerX = (minX + maxX) / 2f
        val centerZ = (minZ + maxZ) / 2f
        val bottomY = minY

        for (i in 0 until numVerts) {
            rawVertices[i * 3] -= centerX
            rawVertices[i * 3 + 1] -= bottomY
            rawVertices[i * 3 + 2] -= centerZ
        }

        val bMin = Vector3(minX - centerX, 0f, minZ - centerZ)
        val bMax = Vector3(maxX - centerX, maxY - bottomY, maxZ - centerZ)

        val finalMesh = Mesh(
            vertices = rawVertices,
            normals = outNormals.toTrimmedArray(),
            uvs = outUVs.toTrimmedArray(),
            indices = outIndices.toTrimmedArray(),
            wireframeIndices = outWireframe.toTrimmedArray(),
            boundingBox = BoundingBox(bMin, bMax)
        )

        // 5. Check Embedded Textures
        var embeddedTextureBytes: ByteArray? = null
        var embeddedMime: String? = null
        var embeddedName: String? = null

        val jsonImages = json.optJSONArray("images")
        if (jsonImages != null && jsonImages.length() > 0) {
            val imgObj = jsonImages.getJSONObject(0)
            embeddedMime = imgObj.optString("mimeType", "image/jpeg")
            embeddedName = imgObj.optString("name", "embedded_texture")
            val imgViewIdx = imgObj.optInt("bufferView", -1)
            if (imgViewIdx != -1) {
                val bv = jsonBufferViews!!.getJSONObject(imgViewIdx)
                val byteOffset = bv.optInt("byteOffset", 0)
                val byteLength = bv.getInt("byteLength")
                embeddedTextureBytes = ByteArray(byteLength)
                System.arraycopy(binBytes, byteOffset, embeddedTextureBytes, 0, byteLength)
            }
        }

        return GlbResult(
            mesh = finalMesh,
            textureBytes = embeddedTextureBytes,
            textureMime = embeddedMime,
            textureName = embeddedName
        )
    }

    private fun readComponentAsFloat(buf: ByteBuffer, compType: Int, normalized: Boolean): Float {
        return when (compType) {
            5126 -> buf.float
            5120 -> { // BYTE (-128..127)
                val b = buf.get().toFloat()
                if (normalized) max(b / 127.0f, -1.0f) else b
            }
            5121 -> { // UNSIGNED_BYTE (0..255)
                val ub = (buf.get().toInt() and 0xFF).toFloat()
                if (normalized) ub / 255.0f else ub
            }
            5122 -> { // SHORT (-32768..32767)
                val s = buf.short.toFloat()
                if (normalized) max(s / 32767.0f, -1.0f) else s
            }
            5123 -> { // UNSIGNED_SHORT (0..65535)
                val us = (buf.short.toInt() and 0xFFFF).toFloat()
                if (normalized) us / 65535.0f else us
            }
            else -> buf.float
        }
    }
}
