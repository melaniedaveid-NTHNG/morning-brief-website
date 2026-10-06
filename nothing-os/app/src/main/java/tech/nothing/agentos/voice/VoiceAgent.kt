package tech.nothing.agentos.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Ears and voice. Speech recognition streams partial text and mic level; TTS speaks replies.
 * Must be used from the main thread.
 */
class VoiceAgent(context: Context, private val listener: Listener) {

    interface Listener {
        fun onLevel(level: Float)
        fun onPartial(text: String)
        fun onHeard(text: String)
        fun onNothingHeard()
        fun onDoneSpeaking()
    }

    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var ttsReady = false
    private var lastPartial = ""

    private val tts = TextToSpeech(appContext) { status ->
        ttsReady = status == TextToSpeech.SUCCESS
    }.apply {
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = listener.onDoneSpeaking()
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = listener.onDoneSpeaking()
        })
    }

    val canListen: Boolean get() = SpeechRecognizer.isRecognitionAvailable(appContext)

    fun listen() {
        stopSpeaking()
        lastPartial = ""
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(appContext).also {
            it.setRecognitionListener(recognitionListener)
            recognizer = it
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        r.startListening(intent)
    }

    /** Stop capturing and deliver what was heard so far (push-to-talk release, tap while listening). */
    fun finishListening() {
        recognizer?.stopListening()
    }

    fun cancelListening() {
        recognizer?.cancel()
        listener.onLevel(0f)
    }

    fun speak(text: String) {
        if (!ttsReady) {
            listener.onDoneSpeaking()
            return
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reply")
    }

    fun stopSpeaking() {
        if (ttsReady) tts.stop()
    }

    fun release() {
        recognizer?.destroy()
        recognizer = null
        tts.shutdown()
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = listener.onLevel(0f)
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        // rmsdB is roughly -2..10 on most recognisers.
        override fun onRmsChanged(rmsdB: Float) = listener.onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults.firstResult() ?: return
            lastPartial = text
            listener.onPartial(text)
        }

        override fun onResults(results: Bundle?) {
            val text = results.firstResult() ?: lastPartial
            if (text.isBlank()) listener.onNothingHeard() else listener.onHeard(text)
        }

        override fun onError(error: Int) {
            listener.onLevel(0f)
            // A stop that races the recogniser still has a usable partial.
            if (lastPartial.isNotBlank()) listener.onHeard(lastPartial) else listener.onNothingHeard()
        }
    }

    private fun Bundle?.firstResult(): String? =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }
}
