package fr.geoking.gaston.radar

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import fr.geoking.gaston.aac.DangerZoneAlertCopy
import java.util.Locale

interface RadarAudioNotifier {
    fun playOkSpeedBeeps()
    fun playOverSpeedBeepsAndSpeak(speedLimitKmH: Int?, isDevMode: Boolean = false)
    fun speakDangerZone(speedLimitKmH: Int?, isDevMode: Boolean = false)
    fun shutdown()
}

/**
 * Beep + TTS for danger-zone alerts (phone and Android Auto).
 *
 * Requests transient navigation audio focus before speaking so the car radio/media
 * pauses or ducks and the announcement is routed to the head unit — without focus,
 * the HUN can appear while TTS is silent or plays only on the phone.
 */
class AndroidRadarAudioNotifier(
    private val context: Context
) : RadarAudioNotifier, TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "RadarAudioNotifier"
        /** Hold focus a short while after TTS ends so the last syllables aren't cut. */
        private const val FOCUS_RELEASE_DELAY_MS = 400L
        /** Safety net if UtteranceProgressListener never fires. */
        private const val FOCUS_TIMEOUT_MS = 12_000L
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val navAudioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Another high-priority stream took over; stop speaking and release.
                try {
                    tts?.stop()
                } catch (_: Exception) {
                }
                abandonAudioFocusInternal()
            }
        }
    }

    private var focusRequest: AudioFocusRequest? = null
    private var holdingAudioFocus = false

    private var toneGenerator: ToneGenerator? = try {
        // STREAM_MUSIC follows AA media/nav routing once we hold navigation focus.
        ToneGenerator(AudioManager.STREAM_MUSIC, 80)
    } catch (e: Exception) {
        Log.w(TAG, "Failed to initialize ToneGenerator", e)
        null
    }

    private var tts: TextToSpeech? = TextToSpeech(appContext, this)
    private var isTtsReady = false
    private var pendingPhrase: String? = null

    private var lastTonePlayedAtMs: Long = 0L
    private var toneDurationMs: Long = 0L

    private val releaseFocusRunnable = Runnable { abandonAudioFocusInternal() }

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
                try {
                    tts?.setAudioAttributes(navAudioAttributes)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to set TTS AudioAttributes", e)
                }
                try {
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) = Unit

                        override fun onDone(utteranceId: String?) {
                            mainHandler.post { scheduleReleaseAudioFocus() }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            mainHandler.post { scheduleReleaseAudioFocus() }
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            mainHandler.post { scheduleReleaseAudioFocus() }
                        }
                    })
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to set UtteranceProgressListener", e)
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
        requestAlertAudioFocus()
        lastTonePlayedAtMs = SystemClock.elapsedRealtime()
        toneDurationMs = 100L
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
        } catch (e: Exception) {
            Log.w(TAG, "ToneGenerator error", e)
        }
        // Focus kept until TTS finishes (or timeout if no speech follows).
        mainHandler.removeCallbacks(releaseFocusRunnable)
        mainHandler.postDelayed(releaseFocusRunnable, FOCUS_TIMEOUT_MS)
    }

    override fun playOverSpeedBeepsAndSpeak(speedLimitKmH: Int?, isDevMode: Boolean) {
        requestAlertAudioFocus()
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

        // Ensure focus is held even when speak is called without a prior beep.
        requestAlertAudioFocus()

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
                    tts = TextToSpeech(appContext, this)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to instantiate TextToSpeech", e)
                }
            }
            return
        }

        if (!requestAlertAudioFocus()) {
            Log.w(TAG, "Audio focus not granted; speaking anyway (may be inaudible on AA with radio on)")
        }

        try {
            val utteranceId = "danger_zone_${System.currentTimeMillis()}"
            mainHandler.removeCallbacks(releaseFocusRunnable)
            mainHandler.postDelayed(releaseFocusRunnable, FOCUS_TIMEOUT_MS)
            val result = tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            if (result == TextToSpeech.ERROR) {
                Log.w(TAG, "TTS speak returned ERROR, re-initializing TTS engine")
                isTtsReady = false
                pendingPhrase = phrase
                tts?.shutdown()
                tts = TextToSpeech(appContext, this)
                scheduleReleaseAudioFocus()
            }
        } catch (e: Exception) {
            Log.w(TAG, "TTS speak failed with exception, re-initializing TTS engine", e)
            isTtsReady = false
            pendingPhrase = phrase
            try {
                tts?.shutdown()
                tts = TextToSpeech(appContext, this)
            } catch (_: Exception) {
            }
            scheduleReleaseAudioFocus()
        }
    }

    /**
     * Transient navigation focus so car radio / media pauses (or ducks) and AA routes
     * speech to the head unit. Returns true if focus was granted (or already held).
     *
     * Uses [AudioManager.AUDIOFOCUS_GAIN_TRANSIENT] (not MAY_DUCK) so FM radio and
     * media are interrupted for the alert, then resume when focus is abandoned.
     */
    private fun requestAlertAudioFocus(): Boolean {
        if (holdingAudioFocus) return true
        return try {
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(navAudioAttributes)
                    .setOnAudioFocusChangeListener(focusChangeListener, mainHandler)
                    .setAcceptsDelayedFocusGain(false)
                    .build()
                focusRequest = request
                audioManager.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    focusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
                )
            }
            holdingAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            if (!holdingAudioFocus) {
                Log.w(TAG, "requestAudioFocus returned $result")
            }
            holdingAudioFocus
        } catch (e: Exception) {
            Log.w(TAG, "requestAudioFocus failed", e)
            false
        }
    }

    private fun scheduleReleaseAudioFocus() {
        mainHandler.removeCallbacks(releaseFocusRunnable)
        mainHandler.postDelayed(releaseFocusRunnable, FOCUS_RELEASE_DELAY_MS)
    }

    private fun abandonAudioFocusInternal() {
        if (!holdingAudioFocus && focusRequest == null) return
        holdingAudioFocus = false
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(focusChangeListener)
            }
        } catch (e: Exception) {
            Log.w(TAG, "abandonAudioFocus failed", e)
        } finally {
            focusRequest = null
        }
    }

    override fun shutdown() {
        mainHandler.removeCallbacksAndMessages(null)
        abandonAudioFocusInternal()
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (_: Exception) {
        }

        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isTtsReady = false
        } catch (_: Exception) {
        }
    }
}
