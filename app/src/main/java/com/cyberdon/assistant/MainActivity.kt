package com.cyberdon.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.cyberdon.assistant.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    companion object {
        const val ACTION_OVERLAY_LISTEN = "com.cyberdon.assistant.OVERLAY_LISTEN"
    }

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
            "Accessibility: ACTIVE (All device gestures & app controls enabled)"
        } else {
            "Accessibility: NOT ENABLED (Tap below to activate)"
        }

        val hasOverlay = Settings.canDrawOverlays(this)
        binding.tvOverlayStatus.text = if (hasOverlay) {
            "Appear on Top: ACTIVE (Floating CyberDon bubble ready)"
        } else {
            "Appear on Top: NOT GRANTED (Required for Floating Bubble)"
        }
    }

    private fun setupButtons() {
        binding.btnSetApiKey.setOnClickListener {
            val input = android.widget.EditText(this).apply {
                hint = "Paste Gemini API key here"
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            AlertDialog.Builder(this)
                .setTitle("Gemini API key")
                .setMessage("Key is stored locally and securely on this device.")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save") { _, _ ->
                    val k = input.text.toString().trim()
                    getSharedPreferences("cyberdon", MODE_PRIVATE).edit()
                        .putString("gemini_api_key", k).apply()
                    Toast.makeText(this, "API Key saved successfully", Toast.LENGTH_SHORT).show()
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

        binding.btnSpeakNow.setOnClickListener { startVoiceSession() }
    }

    private fun startVoiceSession() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 141)
            return
        }

        try {
            val intent = Intent(this, CyberDonSessionService::class.java).apply {
                action = CyberDonSessionService.ACTION_START
            }
            ContextCompat.startForegroundService(this, intent)

            if (Settings.canDrawOverlays(this)) {
                startService(Intent(this, FloatingOverlayService::class.java).apply {
                    action = FloatingOverlayService.ACTION_START
                })
            }

            binding.tvOutput.text = "CyberDon active! Screen par floating bubble dikhega. Bubble par tap karke direct boliye."
        } catch (e: Exception) {
            binding.tvOutput.text = "Start error: ${e.localizedMessage ?: "Permissions check karein"}"
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 141 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startVoiceSession()
        } else if (requestCode == 141) {
            binding.tvOutput.text = "Microphone permission required hai."
        }
    }
}
