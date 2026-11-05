package com.example.studio3d.core.engine

import com.example.studio3d.core.math.*
import com.example.studio3d.core.engine.*
import com.example.studio3d.core.scene.*
import com.example.studio3d.core.history.*
import com.example.studio3d.core.preferences.*
import com.example.studio3d.core.project.*
import com.example.studio3d.feature.editor.viewport.*
import com.example.studio3d.feature.editor.tools.select.*


import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sqrt

object MeshoptDecoder {

    private fun dezig(v: Int): Int {
        return (v ushr 1) xor -(v and 1)
    }

    fun decodeGltfBuffer(
        target: ByteArray,
        count: Int,
        size: Int,
        source: ByteArray,
        mode: String,
        filter: String?
    ) {
        when (mode) {
            "ATTRIBUTES" -> decodeVertexBuffer(target, count, size, source, filter)
            "TRIANGLES" -> decodeIndexBuffer(target, count, size, source)
            "INDICES" -> decodeIndexSequence(target, count, size, source)
            else -> throw IllegalArgumentException("Unsupported meshopt compression mode: $mode")
        }
    }

    fun decodeIndexBuffer(target: ByteArray, count: Int, byteStride: Int, source: ByteArray) {
        val header = source[0].toInt() and 0xFF
        if (header != 0xE1) {
            throw IllegalArgumentException("Invalid index buffer header: 0x${header.toString(16)}, expected 0xE1")
        }
        if (count % 3 != 0) {
            throw IllegalArgumentException("Index count must be divisible by 3")
        }
        if (byteStride != 2 && byteStride != 4) {
            throw IllegalArgumentException("Index byteStride must be 2 or 4")
        }

        val triCount = count / 3
        var codeOffs = 1
        var dataOffs = codeOffs + triCount
        val codeauxOffs = source.size - 16

        fun readLEB128(): Int {
            var n = 0
            var i = 0
            while (true) {
                val b = source[dataOffs++].toInt() and 0xFF
                n = n or ((b and 0x7F) shl i)
                if (b < 0x80) return n
                i += 7
            }
        }

        var next = 0
        var last = 0
        val edgefifo = IntArray(32)
        val vertexfifo = IntArray(16)
        var edgefifoOffset = 0
        var vertexfifoOffset = 0

        fun readfifo(fifo: IntArray, offset: Int, n: Int): Int {
            return fifo[(offset - 1 - n) and (fifo.size - 1)]
        }

        fun pushfifoEdge(n: Int) {
            edgefifo[edgefifoOffset] = n
            edgefifoOffset = (edgefifoOffset + 1) and (edgefifo.size - 1)
        }

        fun pushfifoVertex(n: Int) {
            vertexfifo[vertexfifoOffset] = n
            vertexfifoOffset = (vertexfifoOffset + 1) and (vertexfifo.size - 1)
        }

        fun decodeIndex(v: Int): Int {
            last += dezig(v)
            return last
        }

        val targetBuf = ByteBuffer.wrap(target).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until triCount) {
            val code = source[codeOffs++].toInt() and 0xFF
            val b0 = code ushr 4
            val b1 = code and 0x0F

            if (b0 < 0x0F) {
                val a = readfifo(edgefifo, edgefifoOffset, (b0 shl 1) + 0)
                val b = readfifo(edgefifo, edgefifoOffset, (b0 shl 1) + 1)
                var c = -1

                when {
                    b1 == 0x00 -> {
                        c = next++
                        pushfifoVertex(c)
                    }
                    b1 < 0x0D -> {
                        c = readfifo(vertexfifo, vertexfifoOffset, b1)
                    }
                    b1 == 0x0D -> {
                        c = --last
                        pushfifoVertex(c)
                    }
                    b1 == 0x0E -> {
                        c = ++last
                        pushfifoVertex(c)
                    }
                    b1 == 0x0F -> {
                        val v = readLEB128()
                        c = decodeIndex(v)
                        pushfifoVertex(c)
                    }
                }

                // fifo pushes happen backwards
                pushfifoEdge(b)
                pushfifoEdge(c)
                pushfifoEdge(c)
                pushfifoEdge(a)

                if (byteStride == 2) {
                    targetBuf.putShort(a.toShort())
                    targetBuf.putShort(b.toShort())
                    targetBuf.putShort(c.toShort())
                } else {
                    targetBuf.putInt(a)
                    targetBuf.putInt(b)
                    targetBuf.putInt(c)
                }
            } else {
                // b0 == 0x0F
                var a = -1
                var b = -1
                var c = -1

                if (b1 < 0x0E) {
                    val e = source[codeauxOffs + b1].toInt() and 0xFF
                    val z = e ushr 4
                    val w = e and 0x0F

                    a = next++
                    b = if (z == 0x00) next++ else readfifo(vertexfifo, vertexfifoOffset, z - 1)
                    c = if (w == 0x00) next++ else readfifo(vertexfifo, vertexfifoOffset, w - 1)

                    pushfifoVertex(a)
                    if (z == 0x00) pushfifoVertex(b)
                    if (w == 0x00) pushfifoVertex(c)
                } else {
                    val e = source[dataOffs++].toInt() and 0xFF
                    if (e == 0x00) next = 0

                    val z = e ushr 4
                    val w = e and 0x0F

                    a = if (b1 == 0x0E) next++ else decodeIndex(readLEB128())
                    b = when (z) {
                        0x00 -> next++
                        0x0F -> decodeIndex(readLEB128())
                        else -> readfifo(vertexfifo, vertexfifoOffset, z - 1)
                    }
                    c = when (w) {
                        0x00 -> next++
                        0x0F -> decodeIndex(readLEB128())
                        else -> readfifo(vertexfifo, vertexfifoOffset, w - 1)
                    }

                    pushfifoVertex(a)
                    if (z == 0x00 || z == 0x0F) pushfifoVertex(b)
                    if (w == 0x00 || w == 0x0F) pushfifoVertex(c)
                }

                pushfifoEdge(a)
                pushfifoEdge(b)
                pushfifoEdge(b)
                pushfifoEdge(c)
                pushfifoEdge(c)
                pushfifoEdge(a)

                if (byteStride == 2) {
                    targetBuf.putShort(a.toShort())
                    targetBuf.putShort(b.toShort())
                    targetBuf.putShort(c.toShort())
                } else {
                    targetBuf.putInt(a)
                    targetBuf.putInt(b)
                    targetBuf.putInt(c)
                }
            }
        }
    }

    fun decodeIndexSequence(target: ByteArray, count: Int, byteStride: Int, source: ByteArray) {
        val header = source[0].toInt() and 0xFF
        if (header != 0xD1) {
            throw IllegalArgumentException("Invalid index sequence header")
        }
        var dataOffs = 1
        fun readLEB128(): Int {
            var n = 0
            var i = 0
            while (true) {
                val b = source[dataOffs++].toInt() and 0xFF
                n = n or ((b and 0x7F) shl i)
                if (b < 0x80) return n
                i += 7
            }
        }

        val last = IntArray(2)
        val targetBuf = ByteBuffer.wrap(target).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) {
            val v = readLEB128()
            val b = v and 1
            val delta = dezig(v ushr 1)
            last[b] += delta
            val idx = last[b]
            if (byteStride == 2) targetBuf.putShort(idx.toShort()) else targetBuf.putInt(idx)
        }
    }

    fun decodeVertexBuffer(target: ByteArray, elementCount: Int, byteStride: Int, source: ByteArray, filter: String?) {
        val header = source[0].toInt() and 0xFF
        if (header != 0xA0 && header != 0xA1) {
            throw IllegalArgumentException("Invalid vertex buffer header: 0x${header.toString(16)}")
        }
        val version = header and 0x0F

        val maxBlockElements = kotlin.math.min((0x2000 / byteStride) and (0x000F.inv()), 0x100)
        val deltas = ByteArray(maxBlockElements * byteStride)

        val tailSize = if (version == 0) byteStride else byteStride + byteStride / 4
        val tailDataOffs = source.size - tailSize

        val tempData = ByteArray(byteStride)
        System.arraycopy(source, tailDataOffs, tempData, 0, byteStride)

        val channels: ByteArray? = if (version == 0) null else {
            val chan = ByteArray(tailSize - byteStride)
            System.arraycopy(source, tailDataOffs + byteStride, chan, 0, chan.size)
            chan
        }

        var srcOffs = 1 // Skip header byte

        val headerModes = arrayOf(
            intArrayOf(0, 2, 4, 8), // v0
            intArrayOf(0, 1, 2, 4), // v1, when control is 0
            intArrayOf(1, 2, 4, 8)  // v1, when control is 1
        )

        for (dstElemBase in 0 until elementCount step maxBlockElements) {
            val attrBlockElementCount = kotlin.math.min(elementCount - dstElemBase, maxBlockElements)
            val groupCount = ((attrBlockElementCount + 0x0F) and (0x0F.inv())) ushr 4
            val headerByteCount = ((groupCount + 0x03) and (0x03.inv())) ushr 2

            val controlBitsOffs = srcOffs
            srcOffs += if (version == 0) 0 else byteStride / 4

            deltas.fill(0)

            for (byte in 0 until byteStride) {
                val deltaBase = byte * attrBlockElementCount
                val controlMode = if (version == 0) 0 else {
                    ((source[controlBitsOffs + (byte ushr 2)].toInt() and 0xFF) ushr ((byte and 0x03) shl 1)) and 0x03
                }

                if (controlMode == 2) {
                    continue
                } else if (controlMode == 3) {
                    System.arraycopy(source, srcOffs, deltas, deltaBase, attrBlockElementCount)
                    srcOffs += attrBlockElementCount
                    continue
                }

                val headerBitsOffs = srcOffs
                srcOffs += headerByteCount

                for (group in 0 until groupCount) {
                    val mode = ((source[headerBitsOffs + (group ushr 2)].toInt() and 0xFF) ushr ((group and 0x03) shl 1)) and 0x03
                    val modeRow = if (version == 0) 0 else controlMode + 1
                    val modeBits = headerModes[modeRow][mode]
                    val deltaOffs = deltaBase + (group shl 4)

                    when (modeBits) {
                        0 -> { /* All 16 byte deltas are 0 */ }
                        1 -> {
                            val srcBase = srcOffs
                            srcOffs += 2
                            for (m in 0 until 16) {
                                val shift = m and 7
                                var delta = ((source[srcBase + (m ushr 3)].toInt() and 0xFF) ushr shift) and 1
                                if (delta == 1) delta = source[srcOffs++].toInt() and 0xFF
                                deltas[deltaOffs + m] = delta.toByte()
                            }
                        }
                        2 -> {
                            val srcBase = srcOffs
                            srcOffs += 4
                            for (m in 0 until 16) {
                                val shift = 6 - ((m and 3) shl 1)
                                var delta = ((source[srcBase + (m ushr 2)].toInt() and 0xFF) ushr shift) and 3
                                if (delta == 3) delta = source[srcOffs++].toInt() and 0xFF
                                deltas[deltaOffs + m] = delta.toByte()
                            }
                        }
                        4 -> {
                            val srcBase = srcOffs
                            srcOffs += 8
                            for (m in 0 until 16) {
                                val shift = 4 - ((m and 1) shl 2)
                                var delta = ((source[srcBase + (m ushr 1)].toInt() and 0xFF) ushr shift) and 0x0F
                                if (delta == 0x0F) delta = source[srcOffs++].toInt() and 0xFF
                                deltas[deltaOffs + m] = delta.toByte()
                            }
                        }
                        else -> {
                            System.arraycopy(source, srcOffs, deltas, deltaOffs, 16)
                            srcOffs += 16
                        }
                    }
                }
            }

            // Apply deltas to data
            for (elem in 0 until attrBlockElementCount) {
                val dstElem = dstElemBase + elem

                for (byteGroup in 0 until byteStride step 4) {
                    val channelMode = if (version == 0) 0 else (channels!![byteGroup ushr 2].toInt() and 0x03)

                    when (channelMode) {
                        0 -> {
                            for (byte in byteGroup until byteGroup + 4) {
                                val delta = dezig(deltas[byte * attrBlockElementCount + elem].toInt() and 0xFF)
                                val prev = tempData[byte].toInt() and 0xFF
                                val temp = (prev + delta) and 0xFF
                                tempData[byte] = temp.toByte()
                                target[dstElem * byteStride + byte] = temp.toByte()
                            }
                        }
                        1 -> {
                            for (byte in byteGroup until byteGroup + 4 step 2) {
                                val dLow = deltas[byte * attrBlockElementCount + elem].toInt() and 0xFF
                                val dHigh = deltas[(byte + 1) * attrBlockElementCount + elem].toInt() and 0xFF
                                val delta = dezig(dLow or (dHigh shl 8))

                                val tLow = tempData[byte].toInt() and 0xFF
                                val tHigh = tempData[byte + 1].toInt() and 0xFF
                                var temp = (tLow or (tHigh shl 8)) + delta
                                temp = temp and 0xFFFF

                                tempData[byte] = (temp and 0xFF).toByte()
                                tempData[byte + 1] = (temp ushr 8).toByte()

                                val dstOffs = dstElem * byteStride + byte
                                target[dstOffs] = (temp and 0xFF).toByte()
                                target[dstOffs + 1] = (temp ushr 8).toByte()
                            }
                        }
                        2 -> {
                            val byte = byteGroup
                            val d0 = deltas[byte * attrBlockElementCount + elem].toInt() and 0xFF
                            val d1 = deltas[(byte + 1) * attrBlockElementCount + elem].toInt() and 0xFF
                            val d2 = deltas[(byte + 2) * attrBlockElementCount + elem].toInt() and 0xFF
                            val d3 = deltas[(byte + 3) * attrBlockElementCount + elem].toInt() and 0xFF
                            val delta = d0 or (d1 shl 8) or (d2 shl 16) or (d3 shl 24)

                            val t0 = tempData[byte].toInt() and 0xFF
                            val t1 = tempData[byte + 1].toInt() and 0xFF
                            val t2 = tempData[byte + 2].toInt() and 0xFF
                            val t3 = tempData[byte + 3].toInt() and 0xFF
                            var temp = t0 or (t1 shl 8) or (t2 shl 16) or (t3 shl 24)

                            val rot = (channels!![byteGroup ushr 2].toInt() and 0xFF) ushr 4
                            val rotatedDelta = if (rot == 0) delta else (delta ushr rot) or (delta shl (32 - rot))
                            temp = temp xor rotatedDelta

                            tempData[byte] = (temp and 0xFF).toByte()
                            tempData[byte + 1] = ((temp ushr 8) and 0xFF).toByte()
                            tempData[byte + 2] = ((temp ushr 16) and 0xFF).toByte()
                            tempData[byte + 3] = (temp ushr 24).toByte()

                            val dstOffs = dstElem * byteStride + byte
                            target[dstOffs] = (temp and 0xFF).toByte()
                            target[dstOffs + 1] = ((temp ushr 8) and 0xFF).toByte()
                            target[dstOffs + 2] = ((temp ushr 16) and 0xFF).toByte()
                            target[dstOffs + 3] = (temp ushr 24).toByte()
                        }
                    }
                }
            }
        }

        // Apply filters if needed
        when (filter) {
            "EXPONENTIAL" -> {
                val targetBuf = ByteBuffer.wrap(target).order(ByteOrder.LITTLE_ENDIAN)
                val intCount = elementCount * (byteStride / 4)
                for (i in 0 until intCount) {
                    val offset = i * 4
                    val v = targetBuf.getInt(offset)
                    val exp = v shr 24
                    val mantissa = (v shl 8) shr 8
                    val scale = if (exp <= -127) 0f else java.lang.Float.intBitsToFloat((exp + 127) shl 23)
                    targetBuf.putFloat(offset, mantissa * scale)
                }
            }
            "OCTAHEDRAL" -> {
                val targetBuf = ByteBuffer.wrap(target).order(ByteOrder.LITTLE_ENDIAN)
                val maxInt = if (byteStride == 4) 127f else 32767f
                for (i in 0 until elementCount) {
                    val base = i * byteStride
                    var x: Float
                    var y: Float
                    var one: Float
                    if (byteStride == 4) {
                        x = target[base].toFloat()
                        y = target[base + 1].toFloat()
                        one = target[base + 2].toFloat()
                    } else {
                        x = targetBuf.getShort(base).toFloat()
                        y = targetBuf.getShort(base + 2).toFloat()
                        one = targetBuf.getShort(base + 4).toFloat()
                    }
                    if (one != 0f) {
                        x /= one
                        y /= one
                    }
                    val z = 1.0f - abs(x) - abs(y)
                    val t = max(-z, 0.0f)
                    x -= if (x >= 0f) t else -t
                    y -= if (y >= 0f) t else -t
                    val len = sqrt(x * x + y * y + z * z)
                    val h = if (len > 0f) maxInt / len else 0f
                    if (byteStride == 4) {
                        target[base] = round(x * h).toInt().toByte()
                        target[base + 1] = round(y * h).toInt().toByte()
                        target[base + 2] = round(z * h).toInt().toByte()
                    } else {
                        targetBuf.putShort(base, round(x * h).toInt().toShort())
                        targetBuf.putShort(base + 2, round(y * h).toInt().toShort())
                        targetBuf.putShort(base + 4, round(z * h).toInt().toShort())
                    }
                }
            }
            "QUATERNION" -> {
                val targetBuf = ByteBuffer.wrap(target).order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until elementCount) {
                    val base = i * 8
                    val inputW = targetBuf.getShort(base + 6).toInt()
                    val maxComponent = inputW and 0x03
                    val s = 0.7071067811865475f / (inputW or 0x03)
                    val x = targetBuf.getShort(base).toFloat() * s
                    val y = targetBuf.getShort(base + 2).toFloat() * s
                    val z = targetBuf.getShort(base + 4).toFloat() * s
                    val w = sqrt(max(0.0f, 1.0f - x * x - y * y - z * z))
                    val res = floatArrayOf(x, y, z, w)
                    targetBuf.putShort(base + ((maxComponent + 1) % 4) * 2, round(res[0] * 32767f).toInt().toShort())
                    targetBuf.putShort(base + ((maxComponent + 2) % 4) * 2, round(res[1] * 32767f).toInt().toShort())
                    targetBuf.putShort(base + ((maxComponent + 3) % 4) * 2, round(res[2] * 32767f).toInt().toShort())
                    targetBuf.putShort(base + ((maxComponent + 0) % 4) * 2, round(res[3] * 32767f).toInt().toShort())
                }
            }
        }
    }
}
