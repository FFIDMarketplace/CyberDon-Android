package com.cyberdon.assistant

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class CyberDonVoiceSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return CyberDonVoiceSession(this)
    }
}

class CyberDonVoiceSession(context: Context) : VoiceInteractionSession(context) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val intent = Intent(context, CyberDonSessionService::class.java).apply {
            action = CyberDonSessionService.ACTION_LISTEN
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (_: Exception) {}

        try {
            context.startService(Intent(context, FloatingOverlayService::class.java).apply {
                action = FloatingOverlayService.ACTION_SHOW_BOTTOM_SHEET
            })
        } catch (_: Exception) {}

        hide()
    }
}
