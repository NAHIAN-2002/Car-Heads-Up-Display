package com.example.vezelhud

import android.media.AudioManager
import android.media.ToneGenerator

/** Short warning beep played through the alarm stream. */
class AlertSound {
    private var tone: ToneGenerator? = null

    fun beep() {
        if (tone == null) {
            tone = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 100) }.getOrNull()
        }
        tone?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 350)
    }

    fun release() {
        tone?.release()
        tone = null
    }
}
