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
        const val ACTION_HIDE_TEMPORARY = "com.cyberdon.assistant.HIDE_TEMPORARY"
        const val ACTION_RESTORE_TEMPORARY = "com.cyberdon.assistant.RESTORE_TEMPORARY"
        const val EXTRA_STATUS = "status"
        const val EXTRA_IS_SPEAKING = "is_speaking"
        const val EXTRA_IS_LISTENING = "is_listening"
    }

    private lateinit var windowManager: WindowManager
    private var bubbleView: TextView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var lastStatus = "CyberDon ready hai."
    private var isSpeaking = false
    private var isListening = false

    private val overlayReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_HIDE_TEMPORARY -> setTemporaryVisibility(false)
                ACTION_RESTORE_TEMPORARY -> setTemporaryVisibility(true)
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
            ACTION_STATUS -> {
                lastStatus = intent.getStringExtra(EXTRA_STATUS) ?: lastStatus
                isSpeaking = intent.getBooleanExtra(EXTRA_IS_SPEAKING, isSpeaking)
                isListening = intent.getBooleanExtra(EXTRA_IS_LISTENING, isListening)
                updateUiState()
                return START_STICKY
            }
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "CyberDon ko Appear on Top permission allow karein.", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }

        showBubble()
        return START_STICKY
    }

    private fun setTemporaryVisibility(visible: Boolean) {
        bubbleView?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun showBubble() {
        if (bubbleView != null) {
            updateUiState()
            return
        }

        bubbleView = TextView(this).apply {
            text = "CD 🎙"
            textSize = 14f
            setTextColor(0xFF00F5FF.toInt())
            gravity = Gravity.CENTER
            background = getDrawable(R.drawable.bg_floating_bubble_idle)
            contentDescription = "CyberDon voice trigger"
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener {
                if (isSpeaking) {
                    sendStopSpeaking()
                } else if (isListening) {
                    sendStopSpeaking()
                } else {
                    sendListenRequest()
                }
            }
            setOnLongClickListener {
                Toast.makeText(context, "CyberDon overlay band kiya.", Toast.LENGTH_SHORT).show()
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

    private fun updateUiState() {
        val bubble = bubbleView ?: return
        val p = bubbleParams ?: return

        if (isSpeaking) {
            bubble.text = "⏹ STOP (Awaaz Roko)"
            bubble.setTextColor(0xFFFFFFFF.toInt())
            bubble.background = getDrawable(R.drawable.bg_floating_bubble_speaking)
            p.width = dp(160)
            p.height = dp(48)
        } else if (isListening) {
            bubble.text = "🎙 SUN RAHA HOON…"
            bubble.setTextColor(0xFF0A0E17.toInt())
            bubble.background = getDrawable(R.drawable.bg_floating_bubble_listening)
            p.width = dp(160)
            p.height = dp(48)
        } else {
            bubble.text = "CD 🎙"
            bubble.setTextColor(0xFF00F5FF.toInt())
            bubble.background = getDrawable(R.drawable.bg_floating_bubble_idle)
            p.width = dp(56)
            p.height = dp(56)
        }

        try {
            windowManager.updateViewLayout(bubble, p)
        } catch (_: Exception) {}
    }

    private fun sendListenRequest() {
        val intent = Intent(CyberDonSessionService.ACTION_LISTEN).setPackage(packageName)
        sendBroadcast(intent)
        try {
            startService(Intent(this, CyberDonSessionService::class.java).setAction(CyberDonSessionService.ACTION_LISTEN))
        } catch (_: Exception) {}
        isListening = true
        updateUiState()
    }

    private fun sendStopSpeaking() {
        val intent = Intent(CyberDonSessionService.ACTION_STOP_SPEAKING).setPackage(packageName)
        sendBroadcast(intent)
        try {
            startService(Intent(this, CyberDonSessionService::class.java).setAction(CyberDonSessionService.ACTION_STOP_SPEAKING))
        } catch (_: Exception) {}
        isSpeaking = false
        isListening = false
        updateUiState()
    }

    override fun onDestroy() {
        try { unregisterReceiver(overlayReceiver) } catch (_: Exception) {}
        bubbleView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        bubbleView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
