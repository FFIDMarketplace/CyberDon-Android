package com.cyberdon.assistant

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class FloatingOverlayService : Service() {
    companion object {
        const val ACTION_START = "com.cyberdon.assistant.START_OVERLAY"
        const val ACTION_STOP = "com.cyberdon.assistant.STOP_OVERLAY"
        const val ACTION_STATUS = "com.cyberdon.assistant.STATUS"
        const val ACTION_SHOW_BOTTOM_SHEET = "com.cyberdon.assistant.SHOW_BOTTOM_SHEET"
        const val ACTION_HIDE_TEMPORARY = "com.cyberdon.assistant.HIDE_TEMPORARY"
        const val ACTION_RESTORE_TEMPORARY = "com.cyberdon.assistant.RESTORE_TEMPORARY"
        const val EXTRA_STATUS = "status"
        const val EXTRA_IS_SPEAKING = "is_speaking"
        const val EXTRA_IS_LISTENING = "is_listening"
    }

    private lateinit var windowManager: WindowManager
    private var bubbleView: TextView? = null
    private var bottomSheetView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var lastStatus = "CyberDon ready hai. Boliye ya quick chips use karein."
    private var isSpeaking = false
    private var isListening = false

    private val overlayReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_HIDE_TEMPORARY -> setTemporaryVisibility(false)
                ACTION_RESTORE_TEMPORARY -> setTemporaryVisibility(true)
                ACTION_SHOW_BOTTOM_SHEET -> showBottomSheet()
                ACTION_STATUS -> {
                    lastStatus = intent.getStringExtra(EXTRA_STATUS) ?: lastStatus
                    isSpeaking = intent.getBooleanExtra(EXTRA_IS_SPEAKING, isSpeaking)
                    isListening = intent.getBooleanExtra(EXTRA_IS_LISTENING, isListening)
                    updateUiState()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val filter = IntentFilter().apply {
            addAction(ACTION_HIDE_TEMPORARY)
            addAction(ACTION_RESTORE_TEMPORARY)
            addAction(ACTION_SHOW_BOTTOM_SHEET)
            addAction(ACTION_STATUS)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(overlayReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(overlayReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_HIDE_TEMPORARY -> {
                setTemporaryVisibility(false)
                return START_STICKY
            }
            ACTION_RESTORE_TEMPORARY -> {
                setTemporaryVisibility(true)
                return START_STICKY
            }
            ACTION_SHOW_BOTTOM_SHEET -> {
                showBottomSheet()
                return START_STICKY
            }
            ACTION_STATUS -> {
                lastStatus = intent.getStringExtra(EXTRA_STATUS) ?: lastStatus
                isSpeaking = intent.getBooleanExtra(EXTRA_IS_SPEAKING, isSpeaking)
                isListening = intent.getBooleanExtra(EXTRA_IS_LISTENING, isListening)
                updateUiState()
            }
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "CyberDon ko Appear on Top permission dein.", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }

        showBubble()
        showBottomSheet()
        return START_STICKY
    }

    private fun setTemporaryVisibility(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        bottomSheetView?.visibility = visibility
        bubbleView?.visibility = visibility
    }

    private fun showBubble() {
        if (bubbleView != null) return
        bubbleView = TextView(this).apply {
            text = "CD"
            textSize = 14f
            setTextColor(0xFF00F5FF.toInt())
            gravity = Gravity.CENTER
            background = getDrawable(R.drawable.bg_floating_bubble)
            contentDescription = "CyberDon voice trigger"
            setOnClickListener {
                showBottomSheet()
                sendListenRequest()
            }
            setOnLongClickListener {
                stopSelf()
                true
            }
            setOnTouchListener(object : View.OnTouchListener {
                private var downX = 0
                private var downY = 0
                private var touchX = 0f
                private var touchY = 0f
                private var moved = false
                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    val p = bubbleParams ?: return false
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = p.x
                            downY = p.y
                            touchX = event.rawX
                            touchY = event.rawY
                            moved = false
                            return false
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = (event.rawX - touchX).toInt()
                            val dy = (event.rawY - touchY).toInt()
                            if (abs(dx) + abs(dy) > 12) moved = true
                            if (moved) {
                                p.x = downX + dx
                                p.y = downY + dy
                                try { windowManager.updateViewLayout(bubbleView, p) } catch (_: Exception) {}
                                return true
                            }
                        }
                        MotionEvent.ACTION_UP -> if (moved) return true
                    }
                    return false
                }
            })
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        bubbleParams = WindowManager.LayoutParams(
            dp(56), dp(56), type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(16)
            y = dp(200)
        }

        try {
            windowManager.addView(bubbleView, bubbleParams)
        } catch (_: Exception) {
            bubbleView = null
        }
    }

    private fun showBottomSheet() {
        if (bottomSheetView != null) {
            bottomSheetView?.visibility = View.VISIBLE
            updateUiState()
            return
        }

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_hud, null)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Crucial: FLAG_NOT_FOCUSABLE ensures window focus and audio/mic focus are NOT stolen or blocked
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            y = 0
            windowAnimations = android.R.style.Animation_InputMethod
        }

        val tvStatus = view.findViewById<TextView>(R.id.tv_hud_status)
        val tvClose = view.findViewById<TextView>(R.id.tv_hud_close)
        val btnStopSpeaking = view.findViewById<TextView>(R.id.btn_stop_speaking)
        val btnMic = view.findViewById<TextView>(R.id.btn_hud_mic)
        val btnHide = view.findViewById<TextView>(R.id.btn_hud_hide)

        tvStatus.text = lastStatus

        tvClose.setOnClickListener { hideBottomSheet() }
        btnHide.setOnClickListener { hideBottomSheet() }

        btnStopSpeaking.setOnClickListener {
            sendStopSpeaking()
        }

        btnMic.setOnClickListener {
            if (isSpeaking) {
                sendStopSpeaking()
            } else {
                sendListenRequest()
            }
        }

        // Quick action chips (one-tap execution)
        view.findViewById<TextView>(R.id.chip_torch)?.setOnClickListener { sendCommand("torch toggle") }
        view.findViewById<TextView>(R.id.chip_battery)?.setOnClickListener { sendCommand("battery") }
        view.findViewById<TextView>(R.id.chip_screenshot)?.setOnClickListener { sendCommand("screenshot") }
        view.findViewById<TextView>(R.id.chip_read_screen)?.setOnClickListener { sendCommand("read screen") }
        view.findViewById<TextView>(R.id.chip_home)?.setOnClickListener { sendCommand("home") }
        view.findViewById<TextView>(R.id.chip_recents)?.setOnClickListener { sendCommand("recents") }
        view.findViewById<TextView>(R.id.chip_scroll_down)?.setOnClickListener { sendCommand("scroll down") }
        view.findViewById<TextView>(R.id.chip_scroll_up)?.setOnClickListener { sendCommand("scroll up") }
        view.findViewById<TextView>(R.id.chip_vol_up)?.setOnClickListener { sendCommand("volume up") }
        view.findViewById<TextView>(R.id.chip_vol_down)?.setOnClickListener { sendCommand("volume down") }
        view.findViewById<TextView>(R.id.chip_mute)?.setOnClickListener { sendCommand("mute") }
        view.findViewById<TextView>(R.id.chip_notifications)?.setOnClickListener { sendCommand("notifications") }

        bottomSheetView = view
        try {
            windowManager.addView(view, params)
            updateUiState()
        } catch (_: Exception) {
            bottomSheetView = null
        }
    }

    private fun updateUiState() {
        val view = bottomSheetView ?: return
        val tvStatus = view.findViewById<TextView>(R.id.tv_hud_status)
        val tvMode = view.findViewById<TextView>(R.id.tv_hud_mode)
        val btnStopSpeaking = view.findViewById<TextView>(R.id.btn_stop_speaking)
        val btnMic = view.findViewById<TextView>(R.id.btn_hud_mic)
        val dot = view.findViewById<View>(R.id.view_status_dot)

        tvStatus?.text = lastStatus

        if (isSpeaking) {
            btnStopSpeaking?.visibility = View.VISIBLE
            btnMic?.text = "⏹ STOP SPEAKING (Awaaz Roko)"
            btnMic?.setTextColor(0xFFFFFFFF.toInt())
            btnMic?.background = getDrawable(R.drawable.bg_voice_mic_speaking)
            tvMode?.text = "[SPEAKING]"
            dot?.setBackgroundColor(0xFFFF3366.toInt())
        } else if (isListening) {
            btnStopSpeaking?.visibility = View.GONE
            btnMic?.text = "● SUN RAHA HOON… (Listening)"
            btnMic?.setTextColor(0xFF0A0E17.toInt())
            btnMic?.background = getDrawable(R.drawable.bg_voice_mic_listening)
            tvMode?.text = "[LISTENING]"
            dot?.setBackgroundColor(0xFF00F5FF.toInt())
        } else {
            btnStopSpeaking?.visibility = View.GONE
            btnMic?.text = "🎙 TAP TO SPEAK (Boliye)"
            btnMic?.setTextColor(0xFF00F5FF.toInt())
            btnMic?.background = getDrawable(R.drawable.bg_voice_mic_idle)
            tvMode?.text = "[READY]"
            dot?.setBackgroundColor(0xFF00F5FF.toInt())
        }
    }

    private fun hideBottomSheet() {
        bottomSheetView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        bottomSheetView = null
    }

    private fun sendListenRequest() {
        val intent = Intent(CyberDonSessionService.ACTION_LISTEN).setPackage(packageName)
        sendBroadcast(intent)
        try {
            startService(Intent(this, CyberDonSessionService::class.java).setAction(CyberDonSessionService.ACTION_LISTEN))
        } catch (_: Exception) {}
    }

    private fun sendStopSpeaking() {
        val intent = Intent(CyberDonSessionService.ACTION_STOP_SPEAKING).setPackage(packageName)
        sendBroadcast(intent)
        try {
            startService(Intent(this, CyberDonSessionService::class.java).setAction(CyberDonSessionService.ACTION_STOP_SPEAKING))
        } catch (_: Exception) {}
        isSpeaking = false
        updateUiState()
    }

    private fun sendCommand(cmd: String) {
        val intent = Intent(CyberDonSessionService.ACTION_TEXT_COMMAND).setPackage(packageName).apply {
            putExtra(CyberDonSessionService.EXTRA_TEXT, cmd)
        }
        sendBroadcast(intent)
        try {
            startService(Intent(this, CyberDonSessionService::class.java).apply {
                action = CyberDonSessionService.ACTION_TEXT_COMMAND
                putExtra(CyberDonSessionService.EXTRA_TEXT, cmd)
            })
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        try { unregisterReceiver(overlayReceiver) } catch (_: Exception) {}
        hideBottomSheet()
        bubbleView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        bubbleView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
