package com.example.studio3d.core.engine

import com.example.studio3d.core.math.*
import com.example.studio3d.core.engine.*
import com.example.studio3d.core.scene.*
import com.example.studio3d.core.history.*
import com.example.studio3d.core.preferences.*
import com.example.studio3d.core.project.*
import com.example.studio3d.feature.editor.viewport.*
import com.example.studio3d.feature.editor.tools.select.*


/**
 * Konfigurasi sensitivitas sentuhan gesture jari pada 3D Viewport.
 * Skala 1.0f adalah rasio 1:1 dengan gerakan sentuhan fisik di dunia nyata.
 */
data class GestureSensitivity(
    val orbit: Float = 1.0f,      // Multiplier orbit kamera 1 jari (1.0x = standar rotasi 1:1)
    val pan: Float = 1.0f,        // Multiplier geser kamera 2 jari (1.0x = 1:1 pixel ke ruang 3D)
    val zoom: Float = 1.0f,       // Multiplier perbesaran cubit 2 jari (1.0x = 1:1 rasio jarak jari)
    val twistOrbit: Float = 1.0f  // Multiplier putar 2 jari turntable (1.0x = 1:1 derajat rotasi sudut)
)
