package com.cyberdon.assistant

import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Vibrator
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cyberdon.assistant.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var geminiBrain: GeminiBrain
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private val conversationHistory = mutableListOf<Pair<String, String>>()

    private val packageMap = mapOf(
        "freefire" to "com.dts.freefiremax",
        "freefiremax" to "com.dts.freefiremax",
        "snapchat" to "com.snapchat.android",
        "youtube" to "com.google.android.youtube",
        "whatsapp" to "com.whatsapp",
        "camera" to "com.sec.android.app.camera",
        "gallery" to "com.sec.android.gallery3d",
        "settings" to "com.android.settings",
        "chrome" to "com.android.chrome",
        "instagram" to "com.instagram.android"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        geminiBrain = GeminiBrain(this)
        tts = TextToSpeech(this, this)

        checkPermissions()
        setupButtons()
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    private fun checkPermissions() {
        val isAccessibilityEnabled = CyberDonAccessibilityService.instance != null
        binding.tvAccessibilityStatus.text = if (isAccessibilityEnabled) {
            "Accessibility: ACTIVE (Full Control Enabled)"
        } else {
            "Accessibility: NOT ENABLED (Tap below to activate)"
        }

        val hasOverlay = Settings.canDrawOverlays(this)
        binding.tvOverlayStatus.text = if (hasOverlay) {
            "Appear on Top: ACTIVE"
        } else {
            "Appear on Top: NOT GRANTED"
        }
    }

    private fun setupButtons() {
        binding.btnSetApiKey.setOnClickListener {
            val input = android.widget.EditText(this).apply {
                hint = "Paste Gemini API key here"
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Gemini API key")
                .setMessage("Key is stored only on this phone. Never paste it into chat or publish it.")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save") { _, _ ->
                    getSharedPreferences("cyberdon", MODE_PRIVATE).edit()
                        .putString("gemini_api_key", input.text.toString().trim()).apply()
                    Toast.makeText(this, "Key saved on this phone", Toast.LENGTH_SHORT).show()
                }
                .show()
        }

        binding.btnEnableAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.btnEnableOverlay.setOnClickListener {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        }

        binding.btnSetDefaultAssistant.setOnClickListener {
            startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
        }

        binding.btnSpeakNow.setOnClickListener {
            startListening()
        }
    }

    private fun startListening() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        binding.tvOutput.text = "CyberDon is listening to Ali..."
                    }
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val userSaid = matches[0]
                            handleUserInput(userSaid)
                        }
                    }
                    override fun onError(error: Int) {
                        binding.tvOutput.text = "Listening error ($error). Tap to try again."
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            }
            speechRecognizer?.startListening(intent)
        } else {
            Toast.makeText(this, "Speech recognition not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleUserInput(userSaid: String) {
        binding.tvOutput.text = "Ali: "$userSaid"
Thinking..."

        lifecycleScope.launch {
            val (actions, reply) = geminiBrain.askCyberDon(userSaid, conversationHistory)
            conversationHistory.add(Pair(userSaid, reply))
            binding.tvOutput.text = "Ali: "$userSaid"

CyberDon: $reply"

            speak(reply)
            executeActions(actions)
        }
    }

    private fun executeActions(actions: List<ActionItem>) {
        val service = CyberDonAccessibilityService.instance

        for (item in actions) {
            when (item.action?.uppercase()) {
                "OPEN_APP" -> {
                    val key = item.target?.lowercase()?.replace(" ", "") ?: ""
                    val pkg = packageMap[key] ?: key
                    service?.launchApp(pkg)
                }
                "CLOSE_APP" -> service?.goHome()
                "SPLIT_SCREEN" -> service?.toggleSplitScreen()
                "YOUTUBE_SEARCH" -> {
                    val query = item.target ?: ""
                    val url = "https://www.youtube.com/results?search_query=" + Uri.encode(query)
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        setPackage("com.google.android.youtube")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                }
                "VIBRATE" -> {
                    val v = getSystemService(VIBRATOR_SERVICE) as? Vibrator
                    v?.vibrate(2000)
                }
            }
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "CyberDonSpeech")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("hi", "IN")
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
