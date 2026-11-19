package io.github.amandhakar.mindscroll.service

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * Tells [onChange] when a call starts or ends, so the camera patch can make room for the
 * system's call chip. Uses the audio mode, which covers phone and VoIP calls (WhatsApp, Meet…)
 * and needs no permission: Android tells us about changes from Android 12, and older phones
 * are checked every few seconds.
 */
class CallWatcher(context: Context, private val onChange: (inCall: Boolean) -> Unit) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val executor = ContextCompat.getMainExecutor(context)
    private val handler = Handler(Looper.getMainLooper())
    private var inCall = false

    private val modeListener: Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        AudioManager.OnModeChangedListener { mode -> update(isCallMode(mode)) }
    } else {
        null
    }

    private val poll = object : Runnable {
        override fun run() {
            update(isCallMode(audio.mode))
            handler.postDelayed(this, POLL_MS)
        }
    }

    fun start() {
        update(isCallMode(audio.mode))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audio.addOnModeChangedListener(executor, modeListener as AudioManager.OnModeChangedListener)
        } else {
            handler.postDelayed(poll, POLL_MS)
        }
    }

    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { audio.removeOnModeChangedListener(modeListener as AudioManager.OnModeChangedListener) }
        } else {
            handler.removeCallbacks(poll)
        }
    }

    private fun update(newInCall: Boolean) {
        if (newInCall == inCall) return
        inCall = newInCall
        onChange(newInCall)
    }

    companion object {
        private const val POLL_MS = 3_000L

        /** Ringing, on a phone call, or on a VoIP call. */
        fun isCallMode(mode: Int): Boolean =
            mode == AudioManager.MODE_RINGTONE ||
                mode == AudioManager.MODE_IN_CALL ||
                mode == AudioManager.MODE_IN_COMMUNICATION ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && mode == AudioManager.MODE_CALL_SCREENING)
    }
}
