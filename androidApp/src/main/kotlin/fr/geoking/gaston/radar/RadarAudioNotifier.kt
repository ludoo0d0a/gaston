package fr.geoking.gaston.radar

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.util.Log
import fr.geoking.gaston.aac.DangerZoneAlertCopy
import java.util.Locale

interface RadarAudioNotifier {
    fun playOkSpeedBeeps()
    fun playOverSpeedBeepsAndSpeak(speedLimitKmH: Int?, isDevMode: Boolean = false)
    fun speakDangerZone(speedLimitKmH: Int?, isDevMode: Boolean = false)
    fun shutdown()
}

class AndroidRadarAudioNotifier(
    private val context: Context
) : RadarAudioNotifier, TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "RadarAudioNotifier"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private var toneGenerator: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
    } catch (e: Exception) {
        Log.w(TAG, "Failed to initialize ToneGenerator", e)
        null
    }

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isTtsReady = false
    private var pendingPhrase: String? = null

    private var lastTonePlayedAtMs: Long = 0L
    private var toneDurationMs: Long = 0L

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            var langSupported = false
            val localesToTry = listOf(Locale.FRANCE, Locale.FRENCH, Locale.getDefault())
            for (locale in localesToTry) {
                val result = tts?.setLanguage(locale)
                if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    langSupported = true
                    break
                }
            }

            if (langSupported) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    try {
                        val audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                        tts?.setAudioAttributes(audioAttributes)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to set TTS AudioAttributes", e)
                    }
                }
                isTtsReady = true
                val phraseToSpeak = pendingPhrase
                if (!phraseToSpeak.isNullOrEmpty()) {
                    pendingPhrase = null
                    speakTextInternal(phraseToSpeak)
                }
            } else {
                Log.w(TAG, "No supported French/default language for TTS")
                isTtsReady = false
            }
        } else {
            Log.w(TAG, "TextToSpeech init failed with status: $status")
            isTtsReady = false
        }
    }

    override fun playOkSpeedBeeps() {
        lastTonePlayedAtMs = SystemClock.elapsedRealtime()
        toneDurationMs = 100L
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
        } catch (e: Exception) {
            Log.w(TAG, "ToneGenerator error", e)
        }
    }

    override fun playOverSpeedBeepsAndSpeak(speedLimitKmH: Int?, isDevMode: Boolean) {
        lastTonePlayedAtMs = SystemClock.elapsedRealtime()
        toneDurationMs = 300L
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 300)
        } catch (e: Exception) {
            Log.w(TAG, "ToneGenerator error", e)
        }
        speakDangerZone(speedLimitKmH, isDevMode)
    }

    override fun speakDangerZone(speedLimitKmH: Int?, isDevMode: Boolean) {
        val basePhrase = DangerZoneAlertCopy.frZoneEntry(speedLimitKmH)
        val phrase = if (isDevMode) "[Gaston] $basePhrase" else basePhrase

        val elapsed = SystemClock.elapsedRealtime() - lastTonePlayedAtMs
        val remainingToneMs = (toneDurationMs + 50L - elapsed).coerceAtLeast(0L)

        if (remainingToneMs > 0) {
            mainHandler.postDelayed({
                speakTextInternal(phrase)
            }, remainingToneMs)
        } else {
            speakTextInternal(phrase)
        }
    }

    private fun speakTextInternal(phrase: String) {
        if (!isTtsReady) {
            pendingPhrase = phrase
            if (tts == null) {
                try {
                    tts = TextToSpeech(context.applicationContext, this)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to instantiate TextToSpeech", e)
                }
            }
            return
        }

        try {
            val result = tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "danger_zone_${System.currentTimeMillis()}")
            if (result == TextToSpeech.ERROR) {
                Log.w(TAG, "TTS speak returned ERROR, re-initializing TTS engine")
                isTtsReady = false
                pendingPhrase = phrase
                tts?.shutdown()
                tts = TextToSpeech(context.applicationContext, this)
            }
        } catch (e: Exception) {
            Log.w(TAG, "TTS speak failed with exception, re-initializing TTS engine", e)
            isTtsReady = false
            pendingPhrase = phrase
            try {
                tts?.shutdown()
                tts = TextToSpeech(context.applicationContext, this)
            } catch (_: Exception) {}
        }
    }

    override fun shutdown() {
        mainHandler.removeCallbacksAndMessages(null)
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (_: Exception) {}

        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isTtsReady = false
        } catch (_: Exception) {}
    }
}
