package com.example.data.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

sealed interface VoiceInputState {
    object Idle : VoiceInputState
    data class Listening(val partialText: String = "") : VoiceInputState
    data class Result(val finalText: String) : VoiceInputState
    data class Error(val message: String) : VoiceInputState
}

class VoiceInputManager(private val context: Context) {

    private val _state = MutableStateFlow<VoiceInputState>(VoiceInputState.Idle)
    val state: StateFlow<VoiceInputState> = _state.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        mainHandler.post {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                try {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
                        setRecognitionListener(createListener())
                    }
                } catch (e: Exception) {
                    Log.e("VoiceInputManager", "Failed to initialize SpeechRecognizer", e)
                }
            }
        }
    }

    fun startListening(languageCode: String = "ur-PK") {
        mainHandler.post {
            val sr = speechRecognizer ?: run {
                if (SpeechRecognizer.isRecognitionAvailable(context)) {
                    SpeechRecognizer.createSpeechRecognizer(context.applicationContext).also {
                        speechRecognizer = it
                        it.setRecognitionListener(createListener())
                    }
                } else {
                    _state.value = VoiceInputState.Error("Speech recognition is not available on this device.")
                    return@post
                }
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)

                val selectedLang = when (languageCode.lowercase(Locale.ROOT)) {
                    "auto" -> "ur-PK"
                    "urdu", "ur-pk" -> "ur-PK"
                    "en-pk" -> "en-PK"
                    "en-us", "english" -> "en-US"
                    else -> languageCode
                }

                putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLang)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, selectedLang)
            }

            _state.value = VoiceInputState.Listening("")
            try {
                sr.startListening(intent)
            } catch (e: Exception) {
                Log.e("VoiceInputManager", "Error startListening", e)
                _state.value = VoiceInputState.Error("Could not start microphone. Please try again.")
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.w("VoiceInputManager", "Error stopping listener", e)
            }
        }
    }

    fun reset() {
        _state.value = VoiceInputState.Idle
    }

    fun destroy() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                Log.w("VoiceInputManager", "Error destroying speech recognizer", e)
            }
        }
    }

    private fun createListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = VoiceInputState.Listening("")
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            val friendlyMsg = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    "Could not hear you clearly. Please try again."
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    "Network connection required for speech recognition. Please check your internet connection."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    "Microphone permission is required to use voice entry."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                    "Voice recognizer is busy. Please try again in a moment."
                else -> "Could not process speech (Error code: $error). Please try again."
            }
            _state.value = VoiceInputState.Error(friendlyMsg)
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val finalText = matches?.firstOrNull()
            if (!finalText.isNullOrBlank()) {
                _state.value = VoiceInputState.Result(finalText)
            } else {
                _state.value = VoiceInputState.Error("Could not hear you clearly. Please try again.")
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partialText = matches?.firstOrNull()
            if (!partialText.isNullOrBlank()) {
                _state.value = VoiceInputState.Listening(partialText)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
