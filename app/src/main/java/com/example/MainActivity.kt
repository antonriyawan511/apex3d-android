package com.example

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.studio3d.core.preferences.StudioPreferences
import com.example.studio3d.feature.editor.screen.StudioScreen
import com.example.ui.theme.Apex3DTheme
import com.example.ui.theme.StudioBackground

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StudioPreferences.init(this)
        enableEdgeToEdge()
        enableHighRefreshRate()
        setContent {
            Apex3DTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = StudioBackground
                ) {
                    StudioScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        enableHighRefreshRate()
    }

    /**
     * Unlocks maximum native display refresh rate (90Hz, 120Hz, 144Hz) supported by the hardware,
     * overriding Android's default 60Hz power-saving window mode limit.
     */
    private fun enableHighRefreshRate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                val activeDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    display ?: windowManager.defaultDisplay
                } else {
                    windowManager.defaultDisplay
                }
                val modes = activeDisplay?.supportedModes ?: emptyArray()
                val maxMode = modes.maxByOrNull { it.refreshRate }
                if (maxMode != null) {
                    val params = window.attributes
                    params.preferredDisplayModeId = maxMode.modeId
                    window.attributes = params
                }
            }
        } catch (_: Exception) {
            // Graceful fallback to default system refresh rate
        }
    }
}
