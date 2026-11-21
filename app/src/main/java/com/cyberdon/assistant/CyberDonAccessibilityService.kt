package com.cyberdon.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

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

    fun openRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    fun openNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    fun openQuickSettings(): Boolean = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)

    fun toggleSplitScreen(): Boolean = performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)

    fun lockScreen(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        } else {
            false
        }
    }

    fun takeScreenshot(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        } else {
            false
        }
    }

    fun swipe(direction: String): Boolean {
        val metrics = resources.displayMetrics
        val x = metrics.widthPixels / 2f
        val startY = when (direction.lowercase(Locale.ROOT)) {
            "up" -> metrics.heightPixels * 0.75f
            else -> metrics.heightPixels * 0.25f
        }
        val endY = when (direction.lowercase(Locale.ROOT)) {
            "up" -> metrics.heightPixels * 0.25f
            else -> metrics.heightPixels * 0.75f
        }
        val path = Path().apply { moveTo(x, startY); lineTo(x, endY) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 380))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun foregroundPackageName(): String? = rootInActiveWindow?.packageName?.toString()

    fun readVisibleText(): String {
        val root = rootInActiveWindow ?: return ""
        val collected = mutableListOf<String>()

        fun extract(node: AccessibilityNodeInfo?) {
            if (node == null || collected.size >= 60) return
            if (node.isVisibleToUser) {
                val txt = node.text?.toString()?.trim()
                val desc = node.contentDescription?.toString()?.trim()
                if (!txt.isNullOrBlank() && txt.length > 1 && txt !in collected) {
                    collected.add(txt)
                }
                if (!desc.isNullOrBlank() && desc.length > 1 && desc !in collected) {
                    collected.add(desc)
                }
            }
            for (i in 0 until node.childCount) {
                extract(node.getChild(i))
            }
        }

        extract(root)
        return collected.distinct().joinToString(" • ").take(1000)
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

    fun findPackageByName(appName: String): String? {
        val cleanQuery = appName.lowercase(Locale.ROOT).replace(" ", "").trim()
        val staticMap = mapOf(
            "freefire" to "com.dts.freefiremax",
            "freefiremax" to "com.dts.freefiremax",
            "ff" to "com.dts.freefiremax",
            "youtube" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp",
            "instagram" to "com.instagram.android",
            "insta" to "com.instagram.android",
            "snapchat" to "com.snapchat.android",
            "snap" to "com.snapchat.android",
            "chrome" to "com.android.chrome",
            "browser" to "com.android.chrome",
            "camera" to "com.sec.android.app.camera",
            "gallery" to "com.sec.android.gallery3d",
            "photos" to "com.google.android.apps.photos",
            "settings" to "com.android.settings",
            "termux" to "com.termux",
            "telegram" to "org.telegram.messenger",
            "calculator" to "com.sec.android.app.popupcalculator",
            "clock" to "com.sec.android.app.clockpackage",
            "playstore" to "com.android.vending",
            "files" to "com.sec.android.app.myfiles",
            "contacts" to "com.samsung.android.app.contacts",
            "phone" to "com.samsung.android.dialer"
        )
        if (staticMap.containsKey(cleanQuery)) {
            val candidate = staticMap[cleanQuery]!!
            if (packageManager.getLaunchIntentForPackage(candidate) != null) return candidate
        }

        try {
            val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in apps) {
                if (packageManager.getLaunchIntentForPackage(app.packageName) == null) continue
                val label = packageManager.getApplicationLabel(app).toString().lowercase(Locale.ROOT).replace(" ", "")
                if (label == cleanQuery || label.contains(cleanQuery) || app.packageName.lowercase(Locale.ROOT).contains(cleanQuery)) {
                    return app.packageName
                }
            }
        } catch (_: Exception) {}

        return staticMap[cleanQuery]
    }
}
