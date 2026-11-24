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
import android.os.PowerManager
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
            try {
                val intent = Intent(context, NordHotwordService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_ENABLED, true).apply()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start NordHotwordService", e)
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, NordHotwordService::class.java)
                context.stopService(intent)
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_ENABLED, false).apply()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop NordHotwordService", e)
            }
        }

        fun isEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, true)
        }
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isListeningLoopActive = false
    private var wakeLock: PowerManager.WakeLock? = null

    private val wakeWords = listOf(
        "nord", "node", "north", "norde", "nort", "nerd", "lord",
        "not", "note", "mode", "noor", "hey nord", "ok nord", "hello nord", "hi nord",
        "alexa nord", "gemini nord"
    )

    override fun onCreate() {
        super.onCreate()
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GeminiAgent::NordHotwordWakeLock")
            wakeLock?.acquire(24 * 60 * 60 * 1000L)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wakeLock", e)
        }

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
            .setContentText("Say 'Nord' anytime to give orders")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startWakeWordListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "SpeechRecognizer not available on this device")
            return
        }

        isListeningLoopActive = true
        mainHandler.post { initAndListen() }
    }

    private fun initAndListen() {
        if (!isListeningLoopActive) return

        try {
            destroyRecognizer()

            val isDeviceRecogAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(this)

            speechRecognizer = if (isDeviceRecogAvailable) {
                Log.d(TAG, "Creating On-Device SpeechRecognizer for low-latency wake word")
                SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            } else {
                SpeechRecognizer.createSpeechRecognizer(this)
            }.apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d(TAG, "Ready and listening for 'Nord'...")
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        Log.d(TAG, "SpeechRecognizer error: $error")
                        destroyRecognizer()
                        scheduleRestart(150)
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
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            }

            speechRecognizer?.startListening(recognizerIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting wake word listener", e)
            scheduleRestart(500)
        }
    }

    private fun processRecognizedPhrases(matches: List<String>?) {
        if (matches == null) {
            scheduleRestart(150)
            return
        }

        val detected = matches.any { phrase ->
            val clean = phrase.lowercase(Locale.ROOT)
            wakeWords.any { wake -> clean.contains(wake) }
        }

        if (detected) {
            onWakeWordDetected()
        } else {
            scheduleRestart(150)
        }
    }

    private fun onWakeWordDetected() {
        Log.i(TAG, "Wake word 'Nord' detected! Responding: 'Sir!'")
        vibrate()

        // 1. Wake up the screen if off / locked
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val screenWakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "GeminiAgent::ScreenWakeLock"
            )
            screenWakeLock.acquire(4000L)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to wake up screen", e)
        }

        // 2. Temporarily pause recognizer so it does not capture TTS output
        destroyRecognizer()

        // 3. Speak "Sir!" immediately via offline TTS
        VoiceTtsManager.speakAck {
            mainHandler.post {
                // 4. Launch VoiceAssistActivity over lock screen
                val assistIntent = Intent(this@NordHotwordService, VoiceAssistActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    )
                    putExtra("AUTO_LISTEN", true)
                }
                startActivity(assistIntent)
            }
        }

        // Restart wake word loop after 4 seconds
        scheduleRestart(4000)
    }

    private fun vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                v?.vibrate(150)
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
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
