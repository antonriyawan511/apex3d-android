package com.example.studio3d.core.engine

import com.example.studio3d.core.math.*
import com.example.studio3d.core.engine.*
import com.example.studio3d.core.scene.*
import com.example.studio3d.core.history.*
import com.example.studio3d.core.preferences.*
import com.example.studio3d.core.project.*
import com.example.studio3d.feature.editor.viewport.*
import com.example.studio3d.feature.editor.tools.select.*


import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.opengl.GLES30
import android.opengl.GLUtils
import android.util.Log

object TextureLoader {
    private const val TAG = "TextureLoader"
    private val textureCache = mutableMapOf<String, Int>()

    /**
     * Loads a texture from a Uri string or file path, generating an OpenGL 2D texture.
     * MUST be called on the GL thread with an active EGL context.
     */
    fun getOrLoadTexture(context: Context, uriString: String): Int {
        val cachedId = textureCache[uriString]
        if (cachedId != null && cachedId > 0 && GLES30.glIsTexture(cachedId)) {
            return cachedId
        }
        textureCache.remove(uriString)

        return try {
            val bitmap = decodeBitmap(context, uriString)
            if (bitmap == null) {
                Log.e(TAG, "decodeBitmap failed for: $uriString")
                return 0
            }
            val texId = uploadBitmapToGL(bitmap)
            bitmap.recycle()
            if (texId > 0) {
                textureCache[uriString] = texId
                Log.i(TAG, "Texture loaded & cached successfully! ID=$texId for: $uriString")
            }
            texId
        } catch (t: Throwable) {
            Log.e(TAG, "Error loading texture: $uriString", t)
            0
        }
    }

    private fun decodeBitmap(context: Context, uriString: String): Bitmap? {
        val filePath = if (uriString.startsWith("file://")) uriString.removePrefix("file://") else uriString
        val file = java.io.File(filePath)
        if (file.exists() && file.isFile) {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
            val outW = boundsOpts.outWidth
            val outH = boundsOpts.outHeight
            if (outW <= 0 || outH <= 0) {
                Log.e(TAG, "decodeFile bounds failed for $filePath ($outW x $outH)")
                return null
            }
            val maxDim = maxOf(outW, outH)
            var sampleSize = 1
            while (maxDim / sampleSize > 2048) {
                sampleSize *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inSampleSize = sampleSize
            }
            val bmp = BitmapFactory.decodeFile(file.absolutePath, opts)
            if (bmp != null) {
                Log.i(TAG, "Decoded file bitmap: ${bmp.width}x${bmp.height} (sampleSize=$sampleSize) from $filePath")
            } else {
                Log.e(TAG, "BitmapFactory.decodeFile returned null for $filePath")
            }
            return bmp
        }

        // Content Uri / general stream fallback
        val uri = Uri.parse(uriString)
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes == null || bytes.isEmpty()) {
                Log.e(TAG, "No bytes read from uri: $uriString")
                return null
            }
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
            val outW = boundsOpts.outWidth
            val outH = boundsOpts.outHeight
            if (outW <= 0 || outH <= 0) {
                Log.e(TAG, "decodeByteArray bounds failed for $uriString ($outW x $outH)")
                return null
            }
            val maxDim = maxOf(outW, outH)
            var sampleSize = 1
            while (maxDim / sampleSize > 2048) {
                sampleSize *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inSampleSize = sampleSize
            }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            if (bmp != null) {
                Log.i(TAG, "Decoded stream bitmap: ${bmp.width}x${bmp.height} (sampleSize=$sampleSize)")
            } else {
                Log.e(TAG, "BitmapFactory.decodeByteArray returned null for $uriString")
            }
            bmp
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode bitmap for: $uriString", e)
            null
        }
    }

    fun uploadBitmapToGL(bitmap: Bitmap): Int {
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        val texId = textures[0]
        if (texId == 0) {
            Log.e(TAG, "glGenTextures returned 0")
            return 0
        }

        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)

        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        try {
            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        } catch (t: Throwable) {
            Log.w(TAG, "glGenerateMipmap failed: ${t.message}, fallback to LINEAR")
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)

        Log.i(TAG, "Uploaded texture successfully: ID $texId (${bitmap.width}x${bitmap.height})")
        return texId
    }

    fun releaseTexture(uriString: String) {
        val texId = textureCache.remove(uriString) ?: return
        if (texId > 0 && GLES30.glIsTexture(texId)) {
            val textures = intArrayOf(texId)
            GLES30.glDeleteTextures(1, textures, 0)
        }
    }

    fun clear() {
        for (id in textureCache.values) {
            if (id > 0 && GLES30.glIsTexture(id)) {
                GLES30.glDeleteTextures(1, intArrayOf(id), 0)
            }
        }
        textureCache.clear()
    }
}
