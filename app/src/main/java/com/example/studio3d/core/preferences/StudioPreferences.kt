package com.example.studio3d.core.preferences

import com.example.studio3d.core.math.*
import com.example.studio3d.core.engine.*
import com.example.studio3d.core.scene.*
import com.example.studio3d.core.history.*
import com.example.studio3d.core.preferences.*
import com.example.studio3d.core.project.*
import com.example.studio3d.feature.editor.viewport.*
import com.example.studio3d.feature.editor.tools.select.*


import android.content.Context
import android.content.SharedPreferences
import com.example.studio3d.core.engine.GestureSensitivity
import com.example.studio3d.core.engine.ShadingMode
import com.example.studio3d.feature.editor.viewport.TransformMode

/**
 * Penyimpanan memori persisten (SharedPreferences) untuk seluruh state UI dan pengaturan sensitivitas.
 */
class StudioPreferences private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("studio3d_settings", Context.MODE_PRIVATE)

    var gestureSensitivity: GestureSensitivity
        get() = GestureSensitivity(
            orbit = prefs.getFloat(KEY_GESTURE_ORBIT, 1.0f),
            pan = prefs.getFloat(KEY_GESTURE_PAN, 1.0f),
            zoom = prefs.getFloat(KEY_GESTURE_ZOOM, 1.0f),
            twistOrbit = prefs.getFloat(KEY_GESTURE_TWIST_ORBIT, 1.0f)
        )
        set(value) {
            prefs.edit()
                .putFloat(KEY_GESTURE_ORBIT, value.orbit)
                .putFloat(KEY_GESTURE_PAN, value.pan)
                .putFloat(KEY_GESTURE_ZOOM, value.zoom)
                .putFloat(KEY_GESTURE_TWIST_ORBIT, value.twistOrbit)
                .apply()
        }

    var shadingMode: ShadingMode
        get() {
            val name = prefs.getString(KEY_SHADING_MODE, ShadingMode.MATERIAL.name) ?: ShadingMode.MATERIAL.name
            return try {
                ShadingMode.valueOf(name)
            } catch (_: Exception) {
                ShadingMode.MATERIAL
            }
        }
        set(value) {
            prefs.edit().putString(KEY_SHADING_MODE, value.name).apply()
        }

    var isTransparentView: Boolean
        get() = prefs.getBoolean(KEY_TRANSPARENT_VIEW, false)
        set(value) {
            prefs.edit().putBoolean(KEY_TRANSPARENT_VIEW, value).apply()
        }

    var transformMode: TransformMode
        get() {
            val name = prefs.getString(KEY_TRANSFORM_MODE, TransformMode.TRANSLATE.name) ?: TransformMode.TRANSLATE.name
            return try {
                TransformMode.valueOf(name)
            } catch (_: Exception) {
                TransformMode.TRANSLATE
            }
        }
        set(value) {
            prefs.edit().putString(KEY_TRANSFORM_MODE, value.name).apply()
        }

    var isSnapping: Boolean
        get() = prefs.getBoolean(KEY_SNAPPING, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SNAPPING, value).apply()
        }

    var isOrthoSnapEnabled: Boolean
        get() = prefs.getBoolean(KEY_ORTHO_SNAP, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ORTHO_SNAP, value).apply()
        }

    var isAxisButtonsVisible: Boolean
        get() = prefs.getBoolean(KEY_AXIS_BUTTONS_VISIBLE, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AXIS_BUTTONS_VISIBLE, value).apply()
        }

    var isAiBridgeEnabled: Boolean
        get() = prefs.getBoolean(KEY_AI_BRIDGE_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AI_BRIDGE_ENABLED, value).apply()
        }

    companion object {
        private const val KEY_AI_BRIDGE_ENABLED = "pref_ai_bridge_enabled"
        private const val KEY_GESTURE_ORBIT = "pref_gesture_orbit"
        private const val KEY_GESTURE_PAN = "pref_gesture_pan"
        private const val KEY_GESTURE_ZOOM = "pref_gesture_zoom"
        private const val KEY_GESTURE_TWIST_ORBIT = "pref_gesture_twist_orbit"
        private const val KEY_SHADING_MODE = "pref_shading_mode"
        private const val KEY_TRANSPARENT_VIEW = "pref_transparent_view"
        private const val KEY_TRANSFORM_MODE = "pref_transform_mode"
        private const val KEY_SNAPPING = "pref_snapping"
        private const val KEY_ORTHO_SNAP = "pref_ortho_snap"
        private const val KEY_AXIS_BUTTONS_VISIBLE = "pref_axis_buttons_visible"

        @Volatile
        private var instance: StudioPreferences? = null

        fun init(context: Context): StudioPreferences {
            return instance ?: synchronized(this) {
                instance ?: StudioPreferences(context).also { instance = it }
            }
        }

        fun get(): StudioPreferences? = instance
    }
}
