# CyberDon Android AI Assistant

CyberDon is a native Android voice and device assistant powered by Google Gemini and native Android Accessibility & Hardware APIs.

## Key Features

- **Siri / Gemini-Style Bottom Sheet**: Slides up smoothly from the bottom of the screen with a futuristic cyber aesthetic, displaying real-time transcription, audio waveforms, quick action chips, and a text input box.
- **Barge-in / Stop Speaking**: Tap the glowing Stop button or tap anywhere on the bottom sheet while CyberDon is speaking to instantly interrupt speech output (`tts.stop()`).
- **Offline & Low-Network Resilient**:
  - Full local device controls work 100% offline without internet.
  - No endless mic looping on network drop: clearly indicates offline state and allows one-tap mic trigger or keyboard typing.
- **Natural Conversation**:
  - No repeated "Ali" prefixes or name spamming.
  - Direct, concise responses without recitation of limitations unless explicitly requested.
- **Rich Hardware & System Controls**:
  - 🔦 **Torch / Flashlight**: Turn ON, OFF, or toggle.
  - 🔋 **Battery**: Real-time capacity percentage and charging status.
  - 🔊 **Audio**: Volume Up, Volume Down, Mute, Max Volume.
  - 📸 **Screenshot**: Instant screen capture via Accessibility (Android 9+).
  - 📱 **System Navigation**: Home, Back, Recent Apps, Notifications Shade, Quick Settings Toggles, Screen Lock.
  - 📜 **Screen Text Reader**: Extracts visible text nodes from the active foreground window.
  - ⚡ **Gestures**: Scroll Down (Swipe Up) and Scroll Up (Swipe Down).
  - 🚀 **Dynamic App Launcher**: Launches any installed app by label or package name (Free Fire, WhatsApp, YouTube, Instagram, Camera, Gallery, Settings, Chrome, etc.).
  - 🔍 **YouTube & Web Search**: Direct search query intents.
  - 📳 **Vibration**: Haptic feedback.
  - ⏰ **Clock & Calendar**: Live time, date, and day in Hindi/English.

## Setup Instructions

1. Install `app-debug.apk` from GitHub Actions artifacts.
2. Open CyberDon and grant the required permissions:
   - **Microphone**: For voice command recognition.
   - **Notifications**: For the persistent foreground session notification.
   - **Accessibility Service**: Enables device control, gestures, and app launching.
   - **Appear on Top (Overlay)**: Enables the Siri/Gemini-style bottom sheet and floating bubble.
3. Save your Gemini API key inside the app settings (stored only locally on your phone).
4. (Optional) Set CyberDon as your **Default Digital Assistant** in Android Settings -> Default Apps -> Digital Assistant app. Holding Home or swiping from the bottom corner will then trigger CyberDon.
