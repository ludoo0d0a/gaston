package fr.geoking.gaston.parked

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * Short TTS prompt for the park-save HUN (mirrors danger-zone audio pattern).
 */
class ParkSuggestionAudio(
    context: Context,
) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = TextToSpeech(appContext, this)
    private var ready = false

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.w(TAG, "TTS init failed: $status")
            ready = false
            return
        }
        val locale = appContext.resources.configuration.locales[0] ?: Locale.FRANCE
        val result = tts?.setLanguage(locale)
        ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        if (!ready) {
            // Fallback FR then EN
            ready = tts?.setLanguage(Locale.FRANCE).let {
                it != TextToSpeech.LANG_MISSING_DATA && it != TextToSpeech.LANG_NOT_SUPPORTED
            } == true
        }
        if (!ready) {
            Log.w(TAG, "TTS language not available")
        }
    }

    fun speak(text: String) {
        if (!ready || text.isBlank()) return
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "park_suggest_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            Log.w(TAG, "TTS speak failed", e)
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        ready = false
    }

    companion object {
        private const val TAG = "ParkSuggestionAudio"
    }
}
