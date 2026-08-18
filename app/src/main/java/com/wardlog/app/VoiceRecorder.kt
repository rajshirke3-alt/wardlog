package com.wardlog.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Wraps Android's SpeechRecognizer so recording only stops when the user
 * explicitly taps Stop, instead of the system default of auto-stopping after
 * a short pause. The on-device recognizer still segments speech internally
 * (it always has — that's how it detects a "result"), but instead of
 * surfacing that as an end of recording, this class starts the next segment
 * and appends its text to the running transcript, so from the user's point
 * of view recording just keeps going until they stop it.
 *
 * Tuned for accuracy:
 *  - Prefers the network-based recognizer (EXTRA_PREFER_OFFLINE = false),
 *    which is generally noticeably more accurate than the on-device model,
 *    especially for less common medical vocabulary.
 *  - Extends how long the recognizer waits through a pause before deciding
 *    a segment is "done", so normal mid-sentence pauses in ward dictation
 *    don't trigger constant restarts — every restart is a small window
 *    where words can be missed at the boundary.
 *  - Gets a fresh SpeechRecognizer instance for each segment restart rather
 *    than reusing one. Several OEM speech services throw ERROR_CLIENT /
 *    ERROR_RECOGNIZER_BUSY if startListening() is called again on an
 *    instance that hasn't finished tearing down the previous session —
 *    recreating avoids that, along with a short delay before restarting.
 *  - Distinguishes real failures (no mic access, no network, no recognizer
 *    installed) from expected pauses (no match / timeout), and actually
 *    tells the user when something is really wrong instead of retrying
 *    silently forever.
 */
class VoiceRecorder(
    private val context: Context,
    private val onLiveTextUpdate: (String) -> Unit,
    private val onFinalText: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var stopRequested = false
    private val transcriptSoFar = StringBuilder()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var consecutiveRealErrors = 0

    val isListening: Boolean get() = listening

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError(
                "No speech recognition service found on this device. " +
                    "Make sure the Google app is installed and up to date, then try again."
            )
            return
        }
        listening = true
        stopRequested = false
        consecutiveRealErrors = 0
        transcriptSoFar.clear()
        beginNewSegment()
    }

    /**
     * Stops listening and delivers the full accumulated transcript. Doesn't
     * tear the recognizer down immediately — asks it to finish gracefully
     * and waits for its last result first, so the word(s) spoken right
     * before release aren't cut off mid-processing. Falls back to whatever
     * was captured so far if the recognizer never calls back in time.
     */
    fun stop() {
        if (!listening || stopRequested) return
        listening = false
        stopRequested = true
        mainHandler.removeCallbacksAndMessages(null) // cancel any pending scheduled restart

        val rec = recognizer
        if (rec == null) {
            finishStop()
            return
        }
        try { rec.stopListening() } catch (_: Exception) {}
        // Safety net in case the recognizer never calls back.
        mainHandler.postDelayed({ finishStop() }, 1500)
    }

    fun release() {
        listening = false
        stopRequested = false
        mainHandler.removeCallbacksAndMessages(null)
        destroyRecognizer()
    }

    private fun finishStop() {
        if (!stopRequested) return // already finished (avoid double-delivery)
        stopRequested = false
        mainHandler.removeCallbacksAndMessages(null)
        destroyRecognizer()
        onFinalText(transcriptSoFar.toString().trim())
    }

    private fun destroyRecognizer() {
        recognizer?.let {
            try { it.stopListening() } catch (_: Exception) {}
            try { it.destroy() } catch (_: Exception) {}
        }
        recognizer = null
    }

    private fun beginNewSegment() {
        destroyRecognizer()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(createListener())
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Request several ranked hypotheses, not just the recognizer's
            // single top guess — MedicalTermCorrector.pickBestHypothesis
            // re-ranks them against ward vocabulary, since the acoustic
            // model alone often favors an "ordinary English" guess over an
            // unusual-but-correct medical term.
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            // Ask for the network-based recognizer when available — it's
            // generally more accurate than the offline model. Falls back
            // gracefully on devices/moments without connectivity.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            // Give the speaker much more room to pause mid-sentence before
            // the recognizer decides they're done, so normal dictation
            // pauses don't trigger a restart (and a possible word-boundary
            // gap) nearly as often.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000L)
            // Nudges the recognizer towards ward-specific medical terms and
            // doctor names on devices/OS versions that support it (Android
            // 13+). Harmless no-op elsewhere — MedicalTermCorrector still
            // catches mishearings afterward regardless.
            putStringArrayListExtra(
                RecognizerIntent.EXTRA_BIASING_STRINGS,
                ArrayList(MedicalTermCorrector.termBiasStrings + MedicalTermCorrector.doctorBiasStrings)
            )
        }

        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            scheduleRestartOrFail("Voice input hit a snag — tap the mic to try again")
        }
    }

    /** Restarts a short moment after a segment ends, giving the OS speech service time to release. */
    private fun scheduleRestart() {
        if (!listening) return
        mainHandler.postDelayed({ if (listening) beginNewSegment() }, 350)
    }

    /** Like scheduleRestart, but gives up and surfaces an error after repeated real failures in a row. */
    private fun scheduleRestartOrFail(message: String) {
        if (!listening) return
        consecutiveRealErrors++
        if (consecutiveRealErrors >= 4) {
            listening = false
            mainHandler.removeCallbacksAndMessages(null)
            destroyRecognizer()
            onError(message)
            return
        }
        scheduleRestart()
    }

    private fun createListener() = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            consecutiveRealErrors = 0
            val candidates = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val text = MedicalTermCorrector.pickBestHypothesis(candidates)
            if (text.isNotBlank()) {
                if (transcriptSoFar.isNotEmpty()) transcriptSoFar.append(" ")
                transcriptSoFar.append(text)
                onLiveTextUpdate(transcriptSoFar.toString())
            }
            if (stopRequested) {
                // The user already released the button — this was the final
                // segment's result, so wrap up now instead of restarting.
                finishStop()
            } else {
                // A "result" just means the recognizer's current segment
                // ended — start the next one shortly so recording, from the
                // user's point of view, just keeps going while held.
                scheduleRestart()
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) {
                val preview = if (transcriptSoFar.isNotEmpty()) "$transcriptSoFar $text" else text
                onLiveTextUpdate(preview)
            }
        }

        override fun onError(error: Int) {
            if (stopRequested) {
                // Wrapping up after release — any error here just means no
                // more useful audio is coming, so finalize with whatever was
                // captured rather than treating it as a real failure.
                finishStop()
                return
            }
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    // Expected during normal pauses/silence — keep listening.
                    consecutiveRealErrors = 0
                    scheduleRestart()
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_CLIENT -> {
                    // Usually transient (OS speech service still settling
                    // from the previous segment) — retry with backoff, but
                    // don't loop forever if it keeps happening.
                    scheduleRestartOrFail("Voice input hit a snag — tap the mic to try again")
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    listening = false
                    mainHandler.removeCallbacksAndMessages(null)
                    destroyRecognizer()
                    onError("Microphone permission is required for voice input")
                }
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                    scheduleRestartOrFail(
                        "Voice input needs an internet connection for best accuracy — check your connection and try again"
                    )
                }
                SpeechRecognizer.ERROR_AUDIO -> {
                    scheduleRestartOrFail("Couldn't access the microphone — check nothing else is using it and try again")
                }
                else -> {
                    scheduleRestartOrFail("Voice input hit a snag — tap the mic to try again")
                }
            }
        }

        override fun onEndOfSpeech() { /* handled via onResults/onError restart */ }
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
