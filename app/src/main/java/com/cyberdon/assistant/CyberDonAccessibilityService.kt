package com.cyberdon.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class CyberDonAccessibilityService : AccessibilityService() {

    companion object {
        var instance: CyberDonAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    fun goHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    fun goBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    fun toggleSplitScreen(): Boolean = performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)

    fun swipe(direction: String): Boolean {
        val metrics = resources.displayMetrics
        val x = metrics.widthPixels / 2f
        val startY = when (direction) { "up" -> metrics.heightPixels * 0.78f; else -> metrics.heightPixels * 0.25f }
        val endY = when (direction) { "up" -> metrics.heightPixels * 0.25f; else -> metrics.heightPixels * 0.78f }
        val path = Path().apply { moveTo(x, startY); lineTo(x, endY) }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 420)).build()
        return dispatchGesture(gesture, null, null)
    }

    fun foregroundPackageName(): String? = rootInActiveWindow?.packageName?.toString()

    fun readVisibleText(): String {
        val root = rootInActiveWindow ?: return ""
        val found = mutableListOf<String>()
        fun walk(node: android.view.accessibility.AccessibilityNodeInfo?) {
            if (node == null || found.size >= 80) return
            node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { found.add(it) }
            node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() && it !in found }?.let { found.add(it) }
            for (i in 0 until node.childCount) walk(node.getChild(i))
        }
        walk(root)
        return found.distinct().joinToString(". ").take(1200)
    }

    fun launchApp(packageName: String): Boolean {
        return try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun tap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        dispatchGesture(gesture, null, null)
    }
}
