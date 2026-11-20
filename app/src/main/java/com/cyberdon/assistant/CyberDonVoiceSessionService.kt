package com.cyberdon.assistant

import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class CyberDonVoiceSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return CyberDonVoiceSession(this)
    }
}

class CyberDonVoiceSession(context: Context) : VoiceInteractionSession(context)
