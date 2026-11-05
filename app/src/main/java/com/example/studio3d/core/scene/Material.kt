package com.example.studio3d.core.scene

import com.example.studio3d.core.math.*
import com.example.studio3d.core.engine.*
import com.example.studio3d.core.scene.*
import com.example.studio3d.core.history.*
import com.example.studio3d.core.preferences.*
import com.example.studio3d.core.project.*
import com.example.studio3d.feature.editor.viewport.*
import com.example.studio3d.feature.editor.tools.select.*


data class Material(
    var colorR: Float = 0.85f,
    var colorG: Float = 0.15f,
    var colorB: Float = 0.15f,
    var colorA: Float = 1.0f,
    var metallic: Float = 0.8f,
    var roughness: Float = 0.25f,
    var clearcoat: Float = 0.9f,
    var showWireframe: Boolean = false,
    var textureUri: String? = null,
    var textureName: String? = null,
    @Transient var textureId: Int = 0
) {
    fun copy(): Material = Material(
        colorR = colorR,
        colorG = colorG,
        colorB = colorB,
        colorA = colorA,
        metallic = metallic,
        roughness = roughness,
        clearcoat = clearcoat,
        showWireframe = showWireframe,
        textureUri = textureUri,
        textureName = textureName,
        textureId = textureId
    )

    companion object {
        fun automotiveRed(): Material = Material(0.92f, 0.08f, 0.12f, 1f, 0.9f, 0.2f, 1.0f)
        fun automotiveCyberCyan(): Material = Material(0.0f, 0.85f, 0.95f, 1f, 0.85f, 0.25f, 0.9f)
        fun automotiveChrome(): Material = Material(0.92f, 0.94f, 0.98f, 1f, 0.98f, 0.1f, 1.0f)
        fun matteBlackRubber(): Material = Material(0.12f, 0.12f, 0.14f, 1f, 0.05f, 0.85f, 0.0f)
        fun tintedGlass(): Material = Material(0.15f, 0.25f, 0.35f, 0.85f, 0.3f, 0.1f, 1.0f)
        fun studioClay(): Material = Material(0.78f, 0.76f, 0.74f, 1f, 0.0f, 0.75f, 0.0f)
    }
}
