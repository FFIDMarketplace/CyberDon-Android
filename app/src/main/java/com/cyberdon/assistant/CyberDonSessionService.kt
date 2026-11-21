package com.cyberdon.assistant

import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.speech.*
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.*

class CyberDonSessionService : Service(), TextToSpeech.OnInitListener {
    companion object {
        const val ACTION_START = "com.cyberdon.assistant.SESSION_START"
        const val ACTION_STOP = "com.cyberdon.assistant.SESSION_STOP"
        const val ACTION_LISTEN = "com.cyberdon.assistant.SESSION_LISTEN"
        const val ACTION_STOP_SPEAKING = "com.cyberdon.assistant.SESSION_STOP_SPEAKING"
        const val ACTION_TEXT_COMMAND = "com.cyberdon.assistant.SESSION_TEXT_COMMAND"
        const val ACTION_STATUS = "com.cyberdon.assistant.SESSION_STATUS"
        const val EXTRA_STATUS = "status"
        const val EXTRA_TEXT = "text"
        const val EXTRA_IS_SPEAKING = "is_speaking"
        const val EXTRA_IS_LISTENING = "is_listening"
        private const val CHANNEL = "cyberdon_voice"
        private const val NOTIFICATION_ID = 7302
    }

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var brain: GeminiBrain? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listening = false
    private var speaking = false
    private var ttsReady = false
    private var isTorchOn = false
    private var intentStarted = false
    private val history = mutableListOf<Pair<String, String>>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                MainActivity.ACTION_OVERLAY_LISTEN, ACTION_LISTEN -> {
                    stopSpeaking()
                    startListening()
                }
                ACTION_STOP_SPEAKING -> stopSpeaking()
                ACTION_TEXT_COMMAND -> {
                    val cmd = intent.getStringExtra(EXTRA_TEXT).orEmpty()
                    if (cmd.isNotBlank()) {
                        stopSpeaking()
                        process(cmd)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        brain = GeminiBrain(this)
        tts = TextToSpeech(this, this)
        createChannel()
        val filter = IntentFilter().apply {
            addAction(MainActivity.ACTION_OVERLAY_LISTEN)
            addAction(ACTION_LISTEN)
            addAction(ACTION_STOP_SPEAKING)
            addAction(ACTION_TEXT_COMMAND)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!intentStarted) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                } else {
                    startForeground(NOTIFICATION_ID, notification())
                }
                intentStarted = true
            } catch (_: Exception) {
                try {
                    startForeground(NOTIFICATION_ID, notification())
                    intentStarted = true
                } catch (_: Exception) {}
            }
        }
        when (intent?.action) {
            ACTION_STOP -> { stopSession(); return START_NOT_STICKY }
            ACTION_STOP_SPEAKING -> { stopSpeaking(); return START_STICKY }
            ACTION_LISTEN -> {
                stopSpeaking()
                startListening()
                return START_STICKY
            }
            ACTION_TEXT_COMMAND -> {
                val cmd = intent.getStringExtra(EXTRA_TEXT).orEmpty()
                if (cmd.isNotBlank()) {
                    stopSpeaking()
                    process(cmd)
                }
                return START_STICKY
            }
            ACTION_START -> {
                startOverlayIfAllowed()
                if (ttsReady) speakAndContinue("CyberDon ready hai. Bubble tap karke boliye.")
            }
        }
        return START_STICKY
    }

    private fun startOverlayIfAllowed() {
        if (Settings.canDrawOverlays(this)) {
            try {
                startService(Intent(this, FloatingOverlayService::class.java).setAction(FloatingOverlayService.ACTION_START))
            } catch (_: Exception) {}
        }
    }

    private fun startListening() {
        if (listening) return
        stopSpeaking()

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status("Microphone permission allow karein (Settings > Apps > CyberDon).", isSpeaking = false, isListening = false)
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status("Speech recognition available nahi hai. Google Speech Services check karein.", isSpeaking = false, isListening = false)
            return
        }

        try {
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            }
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    listening = true
                    status("Sun raha hoon… Boliye.", isSpeaking = false, isListening = true)
                }
                override fun onBeginningOfSpeech() {
                    status("Awaaz detect hui…", isSpeaking = false, isListening = true)
                }
                override fun onResults(results: Bundle?) {
                    listening = false
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!text.isNullOrBlank()) {
                        process(text)
                    } else {
                        status("Awaaz samajh nahi aayi. Bubble par tap karein.", isSpeaking = false, isListening = false)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!text.isNullOrBlank()) {
                        status("Suna: $text...", isSpeaking = false, isListening = true)
                    }
                }
                override fun onError(error: Int) {
                    listening = false
                    val errorMsg = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio error (3). Bubble par tap karein."
                        SpeechRecognizer.ERROR_CLIENT -> "Client error (5). Bubble par tap karein."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic permission error (9)."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network offline/timeout. Bubble tap karein."
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Kuch sunai nahi diya. Bubble par tap karke boliye."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Mic busy tha. Dobara tap karein."
                        else -> "Mic idle ($error). Bubble par tap karein."
                    }
                    status(errorMsg, isSpeaking = false, isListening = false)
                }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    listening = false
                    status("Soch raha hoon…", isSpeaking = false, isListening = false)
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            recognizer?.cancel()
            val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            }
            recognizer?.startListening(speechIntent)
            listening = true
            status("Mic active hai… Boliye.", isSpeaking = false, isListening = true)
        } catch (ex: Exception) {
            listening = false
            status("Mic start nahi hua: ${ex.message}", isSpeaking = false, isListening = false)
        }
    }

    private fun stopSpeaking() {
        if (speaking) {
            try { tts?.stop() } catch (_: Exception) {}
            speaking = false
            status("Awaaz rok di gayi hai.", isSpeaking = false, isListening = false)
        }
    }

    private fun process(query: String) {
        val qClean = query.trim()
        if (listOf("stop", "chup", "ruk jao", "bas karo", "shant", "quiet", "band karo").any { qClean.equals(it, ignoreCase = true) }) {
            stopSpeaking()
            return
        }

        status("Aap: $query\nSoch raha hoon…", isSpeaking = false, isListening = false)
        scope.launch {
            val (actions, answer) = brain?.askCyberDon(query, history) ?: (emptyList<ActionItem>() to "Command samajh nahi aayi.")
            val actionResults = execute(actions)
            val finalAnswer = if (actionResults.isEmpty()) answer else "$answer\n${actionResults.joinToString(" ")}"

            if (actions.none { it.action.equals("READ_SCREEN", ignoreCase = true) }) {
                history.add(query to finalAnswer)
                while (history.size > 8) history.removeAt(0)
            }

            status("CyberDon: $finalAnswer", isSpeaking = true, isListening = false)
            speakAndContinue(finalAnswer)
        }
    }

    private suspend fun execute(actions: List<ActionItem>): List<String> {
        val service = CyberDonAccessibilityService.instance
        val results = mutableListOf<String>()

        for (item in actions.take(4)) {
            val action = item.action?.uppercase(Locale.ROOT) ?: continue
            val target = item.target.orEmpty()
            try {
                when (action) {
                    "TORCH_ON" -> {
                        setTorch(true)
                        results.add("Torch ON.")
                    }
                    "TORCH_OFF" -> {
                        setTorch(false)
                        results.add("Torch OFF.")
                    }
                    "TORCH_TOGGLE" -> {
                        setTorch(!isTorchOn)
                        results.add(if (isTorchOn) "Torch ON." else "Torch OFF.")
                    }
                    "OPEN_APP" -> {
                        val pkg = service?.findPackageByName(target) ?: target
                        val launched = service?.launchApp(pkg) == true
                        if (launched) {
                            delay(900)
                            val fg = service?.foregroundPackageName()
                            val ok = fg == pkg || fg?.contains(target, ignoreCase = true) == true
                            results.add(if (ok) "$target khul gaya." else "$target open request bheji.")
                        } else {
                            results.add("$target app nahi mila ya accessibility off hai.")
                        }
                    }
                    "GO_HOME", "CLOSE_APP" -> {
                        val ok = service?.goHome() == true
                        if (ok) results.add("Home screen.")
                    }
                    "GO_BACK" -> {
                        val ok = service?.goBack() == true
                        if (ok) results.add("Back action.")
                    }
                    "RECENTS" -> {
                        val ok = service?.openRecents() == true
                        if (ok) results.add("Recent apps.")
                    }
                    "NOTIFICATIONS" -> {
                        val ok = service?.openNotifications() == true
                        if (ok) results.add("Notifications.")
                    }
                    "QUICK_SETTINGS" -> {
                        val ok = service?.openQuickSettings() == true
                        if (ok) results.add("Quick settings.")
                    }
                    "LOCK_SCREEN" -> {
                        val ok = service?.lockScreen() == true
                        if (ok) results.add("Screen locked.")
                    }
                    "TAKE_SCREENSHOT" -> {
                        val hideIntent = Intent(FloatingOverlayService.ACTION_HIDE_TEMPORARY).setPackage(packageName)
                        sendBroadcast(hideIntent)
                        try {
                            startService(Intent(this, FloatingOverlayService::class.java).setAction(FloatingOverlayService.ACTION_HIDE_TEMPORARY))
                        } catch (_: Exception) {}
                        delay(450)
                        val ok = service?.takeScreenshot() == true
                        delay(450)
                        val restoreIntent = Intent(FloatingOverlayService.ACTION_RESTORE_TEMPORARY).setPackage(packageName)
                        sendBroadcast(restoreIntent)
                        try {
                            startService(Intent(this, FloatingOverlayService::class.java).setAction(FloatingOverlayService.ACTION_RESTORE_TEMPORARY))
                        } catch (_: Exception) {}
                        results.add(if (ok) "Clean screenshot le liya." else "Screenshot nahi le paya (Android 9+ required).")
                    }
                    "SPLIT_SCREEN" -> {
                        val ok = service?.toggleSplitScreen() == true
                        results.add(if (ok) "Split screen toggle." else "Split screen allow nahi hua.")
                    }
                    "VOLUME_UP" -> {
                        val am = getSystemService(AUDIO_SERVICE) as AudioManager
                        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                        results.add("Volume UP.")
                    }
                    "VOLUME_DOWN" -> {
                        val am = getSystemService(AUDIO_SERVICE) as AudioManager
                        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                        results.add("Volume DOWN.")
                    }
                    "MUTE" -> {
                        val am = getSystemService(AUDIO_SERVICE) as AudioManager
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
                        results.add("Muted.")
                    }
                    "MAX_VOLUME" -> {
                        val am = getSystemService(AUDIO_SERVICE) as AudioManager
                        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, max, AudioManager.FLAG_SHOW_UI)
                        results.add("Full volume.")
                    }
                    "BATTERY_STATUS" -> {
                        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
                        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                        results.add("Battery: $pct%.")
                    }
                    "TIME_DATE" -> {
                        val timeStr = SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(Date())
                        val dateStr = SimpleDateFormat("EEEE, dd MMM yyyy", Locale.ENGLISH).format(Date())
                        results.add("Time: $timeStr, Date: $dateStr.")
                    }
                    "VIBRATE" -> {
                        if (Build.VERSION.SDK_INT >= 31) {
                            val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                            vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            (getSystemService(VIBRATOR_SERVICE) as Vibrator).vibrate(300)
                        }
                        results.add("Vibrated.")
                    }
                    "SWIPE_UP" -> {
                        service?.swipe("up")
                        results.add("Scrolled down.")
                    }
                    "SWIPE_DOWN" -> {
                        service?.swipe("down")
                        results.add("Scrolled up.")
                    }
                    "READ_SCREEN" -> {
                        val text = service?.readVisibleText().orEmpty()
                        results.add(if (text.isBlank()) "Screen readable nahi hai." else "Screen: ${text.take(400)}")
                    }
                    "YOUTUBE_SEARCH" -> {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(target))).apply {
                            setPackage("com.google.android.youtube")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                        results.add("YouTube search: $target.")
                    }
                    "WEB_SEARCH" -> {
                        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                            putExtra(SearchManager.QUERY, target)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                        results.add("Web search: $target.")
                    }
                    "SETTINGS_PANEL" -> {
                        val intent = when (target.lowercase(Locale.ROOT)) {
                            "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
                            "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                            "display" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
                            "sound" -> Intent(Settings.ACTION_SOUND_SETTINGS)
                            else -> Intent(Settings.ACTION_SETTINGS)
                        }.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        startActivity(intent)
                        results.add("$target settings open.")
                    }
                }
            } catch (ex: Exception) {
                results.add("Action error: ${ex.message}")
            }
        }
        return results
    }

    private fun setTorch(on: Boolean) {
        try {
            val cm = getSystemService(CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull { camId ->
                cm.getCameraCharacteristics(camId).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: cm.cameraIdList.firstOrNull() ?: "0"
            cm.setTorchMode(id, on)
            isTorchOn = on
        } catch (_: Exception) {}
    }

    private fun speakAndContinue(text: String) {
        speaking = true
        status(text, isSpeaking = true, isListening = false)
        val engine = tts
        if (engine == null) {
            speaking = false
            status(text, isSpeaking = false, isListening = false)
            return
        }
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cyberdon_${System.currentTimeMillis()}")
        if (result == TextToSpeech.ERROR) {
            speaking = false
            status(text, isSpeaking = false, isListening = false)
        }
    }

    override fun onInit(statusCode: Int) {
        if (statusCode == TextToSpeech.SUCCESS) {
            ttsReady = true
            tts?.language = Locale("hi", "IN")
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    speaking = true
                }
                override fun onDone(utteranceId: String?) {
                    runOnMain {
                        speaking = false
                        status("Suniye ya command boliye.", isSpeaking = false, isListening = false)
                    }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    runOnMain {
                        speaking = false
                    }
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    runOnMain {
                        speaking = false
                    }
                }
            })
            if (intentStarted) speakAndContinue("CyberDon ready hai.")
        }
    }

    private fun runOnMain(block: () -> Unit) { Handler(mainLooper).post(block) }

    private fun status(text: String, isSpeaking: Boolean = speaking, isListening: Boolean = listening) {
        val intent = Intent(ACTION_STATUS).setPackage(packageName).apply {
            putExtra(EXTRA_STATUS, text)
            putExtra(EXTRA_IS_SPEAKING, isSpeaking)
            putExtra(EXTRA_IS_LISTENING, isListening)
        }
        sendBroadcast(intent)
        try {
            startService(Intent(this, FloatingOverlayService::class.java).apply {
                action = FloatingOverlayService.ACTION_STATUS
                putExtra(FloatingOverlayService.EXTRA_STATUS, text)
                putExtra(FloatingOverlayService.EXTRA_IS_SPEAKING, isSpeaking)
                putExtra(FloatingOverlayService.EXTRA_IS_LISTENING, isListening)
            })
        } catch (_: Exception) {}
    }

    private fun stopSession() {
        listening = false
        speaking = false
        stopSpeaking()
        try { recognizer?.cancel() } catch (_: Exception) {}
        recognizer?.destroy()
        recognizer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        try { stopService(Intent(this, FloatingOverlayService::class.java)) } catch (_: Exception) {}
        stopSelf()
    }

    override fun onDestroy() {
        listening = false
        speaking = false
        ttsReady = false
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, "CyberDon voice session", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(): Notification {
        val stop = PendingIntent.getService(
            this, 2,
            Intent(this, CyberDonSessionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        return b.setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("CyberDon Active")
            .setContentText("Voice & Device Control online. Tap Stop to end.")
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
    }
}
