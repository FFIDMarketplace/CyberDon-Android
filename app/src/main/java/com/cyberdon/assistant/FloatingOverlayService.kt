package com.cyberdon.assistant

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class FloatingOverlayService : Service() {
    companion object {
        const val ACTION_START = "com.cyberdon.assistant.START_OVERLAY"
        const val ACTION_STOP = "com.cyberdon.assistant.STOP_OVERLAY"
        const val ACTION_LISTEN = "com.cyberdon.assistant.LISTEN"
        const val ACTION_STATUS = "com.cyberdon.assistant.STATUS"
        const val EXTRA_STATUS = "status"
    }

    private lateinit var windowManager: WindowManager
    private var bubbleView: TextView? = null
    private var hudView: android.view.View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var expanded = false
    private var lastStatus = "Ali bhai, command boliye."

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            ACTION_LISTEN -> { sendListenRequest(); return START_STICKY }
            ACTION_STATUS -> {
                lastStatus = intent.getStringExtra(EXTRA_STATUS) ?: lastStatus
                hudView?.findViewById<TextView>(R.id.tv_hud_status)?.text = lastStatus
            }
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "CyberDon ko Appear on Top permission dein.", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }
        showBubble()
        return START_STICKY
    }

    private fun showBubble() {
        if (bubbleView != null) return
        bubbleView = TextView(this).apply {
            text = "CD"
            textSize = 14f
            setTextColor(0xFF00F5FF.toInt())
            gravity = Gravity.CENTER
            background = getDrawable(R.drawable.bg_floating_bubble)
            contentDescription = "CyberDon voice button"
            setOnClickListener { toggleHud() }
            setOnLongClickListener { stopSelf(); true }
            setOnTouchListener(object : android.view.View.OnTouchListener {
                private var downX = 0
                private var downY = 0
                private var touchX = 0f
                private var touchY = 0f
                private var moved = false
                override fun onTouch(v: android.view.View, event: MotionEvent): Boolean {
                    val p = bubbleParams ?: return false
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = p.x; downY = p.y
                            touchX = event.rawX; touchY = event.rawY
                            moved = false
                            return false
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = (event.rawX - touchX).toInt()
                            val dy = (event.rawY - touchY).toInt()
                            if (abs(dx) + abs(dy) > 10) moved = true
                            if (moved) {
                                p.x = downX + dx; p.y = downY + dy
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
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        bubbleParams = WindowManager.LayoutParams(dp(56), dp(56), type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(16); y = dp(220)
        }
        try { windowManager.addView(bubbleView, bubbleParams) } catch (_: Exception) { bubbleView = null }
    }

    private fun toggleHud() { if (expanded) hideHud() else showHud() }

    private fun showHud() {
        if (hudView != null) { expanded = true; return }
        val view = LayoutInflater.from(this).inflate(R.layout.overlay_hud, null)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(dp(292), WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = dp(70) }
        view.findViewById<TextView>(R.id.tv_hud_status).text = lastStatus
        view.findViewById<TextView>(R.id.tv_hud_listen).setOnClickListener { sendListenRequest() }
        view.findViewById<TextView>(R.id.tv_hud_close).setOnClickListener { hideHud() }
        hudView = view
        try { windowManager.addView(view, params); expanded = true } catch (_: Exception) { hudView = null }
    }

    private fun hideHud() {
        hudView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        hudView = null
        expanded = false
    }

    private fun sendListenRequest() { sendBroadcast(Intent(MainActivity.ACTION_OVERLAY_LISTEN).setPackage(packageName)) }

    override fun onDestroy() {
        hideHud()
        bubbleView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        bubbleView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

}
