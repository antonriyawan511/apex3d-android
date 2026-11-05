package com.example.studio3d.feature.editor.panel

import com.example.studio3d.core.math.*
import com.example.studio3d.core.engine.*
import com.example.studio3d.core.scene.*
import com.example.studio3d.core.history.*
import com.example.studio3d.core.preferences.*
import com.example.studio3d.core.project.*
import com.example.studio3d.feature.editor.bar.*
import com.example.studio3d.feature.editor.panel.*
import com.example.studio3d.feature.editor.viewport.*
import com.example.studio3d.feature.editor.screen.*
import com.example.studio3d.feature.editor.tools.select.*


import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Tipe status untuk notifikasi studio
 */
enum class NotificationType {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}

/**
 * Model data notifikasi dengan durasi tampil dan auto-close
 */
data class StudioNotification(
    val id: Long = System.currentTimeMillis(),
    val message: String,
    val type: NotificationType = NotificationType.INFO,
    val durationMs: Long = 2500L
)

/**
 * Manager khusus untuk mengontrol kemunculan dan penutupan popup notifikasi di viewport.
 * Memastikan timer auto-close teratur, tidak race condition, dan dapat ditutup manual kapan saja.
 */
class StudioNotificationManager(private val scope: CoroutineScope) {

    private val _notification = MutableStateFlow<StudioNotification?>(null)
    val notification: StateFlow<StudioNotification?> = _notification.asStateFlow()

    private var dismissJob: Job? = null

    /**
     * Tampilkan notifikasi dengan pesan, jenis, dan durasi tertentu (dalam milidetik).
     * Jika ada notifikasi aktif sebelumnya, timer lama dibatalkan secara aman.
     */
    fun show(
        message: String,
        type: NotificationType = NotificationType.INFO,
        durationMs: Long = 2500L
    ) {
        dismissJob?.cancel()
        val notif = StudioNotification(
            id = System.nanoTime(),
            message = message,
            type = type,
            durationMs = durationMs
        )
        _notification.value = notif

        if (durationMs > 0) {
            dismissJob = scope.launch {
                delay(durationMs)
                if (_notification.value?.id == notif.id) {
                    _notification.value = null
                }
            }
        }
    }

    fun showSuccess(message: String, durationMs: Long = 2500L) {
        show(message, NotificationType.SUCCESS, durationMs)
    }

    fun showError(message: String, durationMs: Long = 3500L) {
        show(message, NotificationType.ERROR, durationMs)
    }

    fun showInfo(message: String, durationMs: Long = 2000L) {
        show(message, NotificationType.INFO, durationMs)
    }

    fun showWarning(message: String, durationMs: Long = 3000L) {
        show(message, NotificationType.WARNING, durationMs)
    }

    /**
     * Tutup notifikasi seketika (manual dismiss / tombol close)
     */
    fun dismiss() {
        dismissJob?.cancel()
        dismissJob = null
        _notification.value = null
    }
}
