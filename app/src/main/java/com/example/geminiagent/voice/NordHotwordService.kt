package com.example.geminiagent.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.geminiagent.MainActivity
import java.util.Locale

class NordHotwordService : Service() {

    companion object {
        private const val TAG = "NordHotwordService"
        private const val CHANNEL_ID = "nord_voice_hotword_channel"
        private const val NOTIFICATION_ID = 2049
        private const val PREFS_NAME = "nord_hotword_prefs"
        private const val KEY_ENABLED = "hotword_enabled"

        var isServiceRunning = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, NordHotwordService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, true).apply()
        }

        fun stop(context: Context) {
            val intent = Intent(context, NordHotwordService::class.java)
            context.stopService(intent)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, false).apply()
        }

        fun isEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false)
        }
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isListeningLoopActive = false

    private val wakeWords = listOf(
        "nord", "node", "north", "norde", "nort", "nerd", "lord",
        "hey nord", "ok nord", "hello nord", "hi nord"
    )

    override fun onCreate() {
        super.onCreate()
        VoiceTtsManager.init(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isServiceRunning = true
        startForeground(NOTIFICATION_ID, buildNotification())
        startWakeWordListening()
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Nord Voice Wake Word Detection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Listens for 'Nord' wake word to activate voice agent"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Nord Assistant Active")
            .setContentText("Call 'Nord' to activate voice command")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startWakeWordListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "SpeechRecognizer not available")
            return
        }

        isListeningLoopActive = true
        mainHandler.post { initAndListen() }
    }

    private fun initAndListen() {
        if (!isListeningLoopActive) return

        try {
            destroyRecognizer()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        // Restart recognition loop on silence or timeout
                        scheduleRestart(250)
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        processRecognizedPhrases(matches)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        processRecognizedPhrases(matches)
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            }

            speechRecognizer?.startListening(recognizerIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting wake word listener", e)
            scheduleRestart(1000)
        }
    }

    private fun processRecognizedPhrases(matches: List<String>?) {
        if (matches == null) {
            scheduleRestart(250)
            return
        }

        val detected = matches.any { phrase ->
            val clean = phrase.lowercase(Locale.ROOT)
            wakeWords.any { wake -> clean.contains(wake) }
        }

        if (detected) {
            onWakeWordDetected()
        } else {
            scheduleRestart(200)
        }
    }

    private fun onWakeWordDetected() {
        Log.i(TAG, "Wake word 'Nord' detected! Responding: 'Sir!'")
        vibrate()

        // Temporarily pause listening loop so it doesn't recognize own voice
        destroyRecognizer()

        // Speak "Sir!" immediately
        VoiceTtsManager.speakAck {
            mainHandler.post {
                // Open VoiceAssistActivity to capture user's order
                val assistIntent = Intent(this@NordHotwordService, VoiceAssistActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("AUTO_LISTEN", true)
                }
                startActivity(assistIntent)
            }
        }

        // Restart listening after 5 seconds
        scheduleRestart(5000)
    }

    private fun vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                v?.vibrate(120)
            }
        } catch (_: Exception) {}
    }

    private fun scheduleRestart(delayMs: Long) {
        if (!isListeningLoopActive) return
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            initAndListen()
        }, delayMs)
    }

    private fun destroyRecognizer() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        isListeningLoopActive = false
        mainHandler.removeCallbacksAndMessages(null)
        destroyRecognizer()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
