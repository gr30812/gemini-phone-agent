package com.example.geminiagent.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

object VoiceTtsManager : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val callbacks = mutableMapOf<String, () -> Unit>()

    fun init(context: Context) {
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext, this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { engine ->
                val result = engine.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine.language = Locale.getDefault()
                }
                engine.setPitch(1.05f)
                engine.setSpeechRate(1.05f)
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {
                        utteranceId?.let { id ->
                            callbacks.remove(id)?.invoke()
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        utteranceId?.let { id ->
                            callbacks.remove(id)
                        }
                    }
                })
                isInitialized = true
            }
        }
    }

    fun speak(text: String, flush: Boolean = true, onDone: (() -> Unit)? = null) {
        val cleanText = sanitizeTextForSpeech(text)
        if (cleanText.isBlank()) {
            onDone?.invoke()
            return
        }

        val utteranceId = UUID.randomUUID().toString()
        if (onDone != null) {
            callbacks[utteranceId] = onDone
        }

        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }

        tts?.speak(cleanText, queueMode, params, utteranceId)
    }

    /**
     * Speaks the prompt response acknowledging the wake word "Nord".
     * Responds with "Sir!" as requested.
     */
    fun speakAck(onDone: (() -> Unit)? = null) {
        speak("Sir!", flush = true, onDone = onDone)
    }

    fun stop() {
        try {
            callbacks.clear()
            tts?.stop()
        } catch (_: Exception) {}
    }

    fun isSpeaking(): Boolean {
        return tts?.isSpeaking == true
    }

    fun shutdown() {
        try {
            stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        } catch (_: Exception) {}
    }

    private fun sanitizeTextForSpeech(input: String): String {
        return input
            .replace(Regex("```[\\s\\S]*?```"), "Code block omitted.")
            .replace(Regex("`[^`]+`"), "")
            .replace(Regex("[#*_~>\\[\\]()|]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
