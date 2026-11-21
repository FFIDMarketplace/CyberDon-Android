package com.cyberdon.assistant

import android.app.*
import android.content.*
import android.os.*
import android.speech.*
import android.speech.tts.TextToSpeech
import android.view.accessibility.AccessibilityNodeInfo
import android.content.Intent
import android.net.Uri
import java.util.Locale
import kotlinx.coroutines.*

/** Visible, user-started voice session. Android shows its persistent notification while the mic is active. */
class CyberDonSessionService : Service(), TextToSpeech.OnInitListener {
    companion object {
        const val ACTION_START = "com.cyberdon.assistant.SESSION_START"
        const val ACTION_STOP = "com.cyberdon.assistant.SESSION_STOP"
        const val ACTION_LISTEN = "com.cyberdon.assistant.SESSION_LISTEN"
        const val ACTION_STATUS = "com.cyberdon.assistant.SESSION_STATUS"
        const val EXTRA_STATUS = "status"
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
    private var retry = 0
    private var intentStarted = false
    private val history = mutableListOf<Pair<String, String>>()
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == MainActivity.ACTION_OVERLAY_LISTEN) startListening()
        }
    }

    override fun onCreate() {
        super.onCreate()
        brain = GeminiBrain(this)
        tts = TextToSpeech(this, this)
        createChannel()
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, IntentFilter(MainActivity.ACTION_OVERLAY_LISTEN), Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, IntentFilter(MainActivity.ACTION_OVERLAY_LISTEN))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopSession(); return START_NOT_STICKY }
            ACTION_LISTEN -> { startListening(); return START_STICKY }
            ACTION_START -> {
                intentStarted = true
                startForeground(NOTIFICATION_ID, notification())
                startOverlayIfAllowed()
                if (ttsReady) speakAndContinue("CyberDon ready hai. Ab aap bina app khole baat jaari rakh sakte hain.")
            }
        }
        return START_STICKY
    }

    private fun startOverlayIfAllowed() {
        if (android.provider.Settings.canDrawOverlays(this)) {
            try { startService(Intent(this, FloatingOverlayService::class.java).setAction(FloatingOverlayService.ACTION_START)) } catch (_: Exception) {}
        }
    }

    private fun startListening() {
        if (listening || speaking || !SpeechRecognizer.isRecognitionAvailable(this)) return
        try {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) { listening = true; status("Sun raha hoon…") }
                    override fun onBeginningOfSpeech() { retry = 0; status("Haan, boliye…") }
                    override fun onResults(results: Bundle?) {
                        listening = false
                        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        if (!text.isNullOrBlank()) process(text) else retryListen("Awaaz samajh nahi aayi.")
                    }
                    override fun onError(error: Int) {
                        listening = false
                        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) retryListen("Sun raha hoon…")
                        else retryListen("Mic unavailable (code $error). Permission aur speech service check karein.")
                    }
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
            val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            recognizer?.startListening(speechIntent)
        } catch (_: Exception) {
            listening = false
            retryListen("Mic start nahi hua. App kholkar microphone permission check karein.")
        }
    }

    private fun process(query: String) {
        status("Aapne kaha: $query\nSoch raha hoon…")
        scope.launch {
            val (actions, answer) = brain?.askCyberDon(query, history) ?: (emptyList<ActionItem>() to "Assistant abhi ready nahi hai.")
            val actionResults = execute(actions)
            val finalAnswer = if (actionResults.isEmpty()) answer else "$answer\n${actionResults.joinToString(" ")}"
            if (actions.none { it.action.equals("READ_SCREEN", ignoreCase = true) }) history.add(query to finalAnswer)
            else history.add(query to "Screen content was read locally and is not saved in conversation history.")
            while (history.size > 12) history.removeAt(0)
            status("Aap: $query\nCyberDon: $finalAnswer")
            speakAndContinue(finalAnswer)
        }
    }

    private suspend fun execute(actions: List<ActionItem>): List<String> {
        val service = CyberDonAccessibilityService.instance
        val results = mutableListOf<String>()
        for (item in actions.take(4)) {
            val action = item.action?.uppercase() ?: continue
            val target = item.target.orEmpty()
            val ok = try {
                when (action) {
                    "OPEN_APP" -> {
                        val requested = resolvePackage(target)
                        val launched = service?.launchApp(requested) == true
                        if (launched) {
                            delay(900)
                            val foreground = CyberDonAccessibilityService.instance?.foregroundPackageName()
                            val verified = foreground == requested
                            results.add(if (verified) "App khul gayi, verify kiya." else "App launch request bheji, lekin screen par khulna verify nahi hua.")
                        } else results.add("App launch nahi hui; app installed hai aur accessibility permission active hai ya nahi check karein.")
                        true
                    }
                    "CLOSE_APP", "GO_HOME" -> service?.goHome() == true
                    "SPLIT_SCREEN" -> service?.toggleSplitScreen() == true
                    "YOUTUBE_SEARCH" -> {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(target))).apply {
                            setPackage("com.google.android.youtube"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent); true
                    }
                    "BATTERY_STATUS" -> {
                        val bm = getSystemService(BATTERY_SERVICE) as android.os.BatteryManager
                        val pct = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
                        results.add("Battery lagbhag $pct% hai."); true
                    }
                    "VIBRATE" -> {
                        if (Build.VERSION.SDK_INT >= 31) {
                            val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                            vm.defaultVibrator.vibrate(android.os.VibrationEffect.createOneShot(350, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            (getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator).vibrate(350)
                        }; true
                    }
                    "SWIPE_UP" -> { val sent = service?.swipe("up") == true; results.add(if (sent) "Scroll gesture bheji; screen change verify nahi ho saka." else "Scroll gesture nahi bhej paya."); sent }
                    "SWIPE_DOWN" -> { val sent = service?.swipe("down") == true; results.add(if (sent) "Scroll gesture bheji; screen change verify nahi ho saka." else "Scroll gesture nahi bhej paya."); sent }
                    "READ_SCREEN" -> {
                        val text = service?.readVisibleText().orEmpty()
                        results.add(if (text.isBlank()) "Is screen ka readable text accessibility se nahi mila." else "Screen par: ${text.take(500)}")
                        text.isNotBlank()
                    }
                    else -> false
                }
            } catch (_: Exception) { false }
            if (action == "SPLIT_SCREEN") results.add(if (ok) "Split-screen request Android ko bheji; mode verify nahi ho saka." else "Android ne split-screen request accept nahi ki.")
            if (action == "CLOSE_APP" || action == "GO_HOME") results.add(if (ok) "Home action bheji." else "Home action nahi ho paya.")
            if (action == "YOUTUBE_SEARCH") results.add(if (ok) "YouTube search kholne ki request bheji; screen par result verify karna baaki hai." else "YouTube search nahi khul payi.")
            if (action == "VIBRATE") results.add(if (ok) "Vibration request bheji." else "Vibration request nahi bhej paya.")
        }
        return results
    }

    private fun resolvePackage(raw: String): String {
        val key = raw.lowercase(Locale.ROOT).replace(" ", "").replace("_", "")
        val map = mapOf("freefire" to "com.dts.freefiremax", "freefiremax" to "com.dts.freefiremax", "snapchat" to "com.snapchat.android", "youtube" to "com.google.android.youtube", "whatsapp" to "com.whatsapp", "camera" to "com.sec.android.app.camera", "gallery" to "com.sec.android.gallery3d", "settings" to "com.android.settings", "chrome" to "com.android.chrome", "instagram" to "com.instagram.android", "termux" to "com.termux")
        return map[key] ?: raw
    }

    private fun retryListen(message: String) {
        status(message)
        if (retry++ < 4) android.os.Handler(mainLooper).postDelayed({ startListening() }, 900L + retry * 500L)
        else { retry = 0; status("Listening ruk gaya. Floating CD bubble tap karke phir shuru karein.") }
    }

    private fun speakAndContinue(text: String) {
        speaking = true
        val engine = tts
        if (engine == null) { speaking = false; startListening(); return }
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cyberdon_reply_${System.currentTimeMillis()}")
        if (result == TextToSpeech.ERROR) { speaking = false; startListening() }
    }

    override fun onInit(statusCode: Int) {
        if (statusCode == TextToSpeech.SUCCESS) {
            ttsReady = true
            tts?.language = Locale("hi", "IN")
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { runOnMain { speaking = false; startListening() } }
                @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { runOnMain { speaking = false; startListening() } }
                override fun onError(utteranceId: String?, errorCode: Int) { runOnMain { speaking = false; startListening() } }
            })
            if (intentStarted) speakAndContinue("CyberDon ready hai.")
        }
    }

    private fun runOnMain(block: () -> Unit) { Handler(mainLooper).post(block) }
    private fun status(text: String) {
        sendBroadcast(Intent(ACTION_STATUS).setPackage(packageName).putExtra(EXTRA_STATUS, text))
        try { startService(Intent(this, FloatingOverlayService::class.java).setAction(FloatingOverlayService.ACTION_STATUS).putExtra(FloatingOverlayService.EXTRA_STATUS, text)) } catch (_: Exception) {}
    }
    private fun stopSession() {
        listening = false; speaking = false
        try { recognizer?.cancel() } catch (_: Exception) {}
        recognizer?.destroy(); recognizer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        try { stopService(Intent(this, FloatingOverlayService::class.java)) } catch (_: Exception) {}
        stopSelf()
    }
    override fun onDestroy() {
        listening = false
        ttsReady = false
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        recognizer?.destroy(); recognizer = null
        tts?.stop(); tts?.shutdown(); tts = null
        scope.cancel()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "CyberDon voice session", NotificationManager.IMPORTANCE_LOW))
    }
    private fun notification(): Notification {
        val stop = PendingIntent.getService(this, 2, Intent(this, CyberDonSessionService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        return b.setSmallIcon(R.drawable.ic_launcher).setContentTitle("CyberDon voice session active")
            .setContentText("Mic active; tap floating CD to show controls. Use Stop to end.")
            .setOngoing(true).addAction(Notification.Action.Builder(null, "Stop", stop).build()).build()
    }
}
