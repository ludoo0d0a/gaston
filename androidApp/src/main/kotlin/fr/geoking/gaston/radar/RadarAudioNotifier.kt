package fr.geoking.gaston.radar

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

interface RadarAudioNotifier {
    fun playOkSpeedBeeps()
    fun playOverSpeedBeepsAndSpeak(speedLimitKmH: Int?)
    fun shutdown()
}

class AndroidRadarAudioNotifier(
    private val context: Context
) : RadarAudioNotifier, TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "RadarAudioNotifier"
    }

    private var toneGenerator: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
    } catch (e: Exception) {
        Log.w(TAG, "Failed to initialize ToneGenerator", e)
        null
    }

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isTtsReady = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.FRANCE)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "French TTS language not supported or missing data")
                isTtsReady = false
            } else {
                isTtsReady = true
            }
        } else {
            Log.w(TAG, "TextToSpeech init failed with status: $status")
            isTtsReady = false
        }
    }

    override fun playOkSpeedBeeps() {
        // Play two short discreet beeps for OK speed
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
        } catch (e: Exception) {
            Log.w(TAG, "ToneGenerator error", e)
        }
    }

    override fun playOverSpeedBeepsAndSpeak(speedLimitKmH: Int?) {
        // Play rapid close beeps for overspeed
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 300)
        } catch (e: Exception) {
            Log.w(TAG, "ToneGenerator error", e)
        }

        // Speak warning via TTS
        val phrase = if (speedLimitKmH != null && speedLimitKmH > 0) {
            "Attention, radar à $speedLimitKmH km/h"
        } else {
            "Attention, radar en approche"
        }

        if (isTtsReady) {
            try {
                tts?.speak(phrase, TextToSpeech.QUEUE_ADD, null, "radar_warning_${System.currentTimeMillis()}")
            } catch (e: Exception) {
                Log.w(TAG, "TTS speak failed", e)
            }
        }
    }

    override fun shutdown() {
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (_: Exception) {}

        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (_: Exception) {}
    }
}
