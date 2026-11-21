# CyberDon Android AI Assistant

CyberDon is a native Android voice and device assistant powered by Google Gemini and native Android Accessibility & Hardware APIs.

## Key Features

- **Siri / Gemini-Style Voice Bottom Sheet**: Slides up smoothly from the bottom of the screen with a futuristic cyber aesthetic. Pure voice-first design with `FLAG_NOT_FOCUSABLE` window configuration to ensure conflict-free microphone access and zero keyboard intrusion.
- **Clean Screenshot Capture**: Automatically hides the CyberDon bottom sheet and floating bubble prior to screen capture, waits for surface compositor refresh, and restores the overlay after capture.
- **Barge-in / Stop Speaking**: Tap the glowing Stop button or say "stop / chup / ruk jao" while CyberDon is speaking to instantly interrupt speech output (`tts.stop()`).
- **Offline & Low-Network Resilient**:
  - Full local device controls (torch, volume, battery, system navigation, screenshot, gestures) execute locally via Accessibility and system managers.
  - Clear offline notifications without endless mic loops on network disconnect.
- **Natural Conversation**:
  - Direct, concise responses without repetitive name calling.
- **Hardware & System Controls**:
  - 🔦 **Torch / Flashlight**: Turn ON, OFF, or toggle.
  - 🔋 **Battery**: Real-time capacity percentage.
  - 🔊 **Audio**: Volume Up, Volume Down, Mute, Max Volume.
  - 📸 **Screenshot**: Clean screen capture via Accessibility (Android 9+).
  - 📱 **System Navigation**: Home, Back, Recent Apps, Notifications Shade, Quick Settings Toggles, Screen Lock.
  - 📜 **Screen Text Reader**: Extracts visible text nodes from the active foreground window.
  - ⚡ **Gestures**: Scroll Down and Scroll Up.
  - 🚀 **Dynamic App Launcher**: Launches installed apps by label or package name (Free Fire, WhatsApp, YouTube, Instagram, Camera, Gallery, Settings, Chrome, etc.).
  - 🔍 **YouTube & Web Search**: Direct query intents.
  - 📳 **Vibration**: Haptic feedback.
  - ⏰ **Clock & Calendar**: Live time, date, and day in Hindi/English.

## Setup Instructions

1. Install `app-debug.apk` from GitHub Actions artifacts.
2. Open CyberDon and grant the required permissions:
   - **Microphone**: For voice command recognition.
   - **Notifications**: For the persistent foreground session notification.
   - **Accessibility Service**: Enables device control, gestures, and app launching.
   - **Appear on Top (Overlay)**: Enables the Siri/Gemini-style bottom sheet and floating bubble.
3. Save your Gemini API key inside the app settings (stored only locally in Encrypted/App SharedPreferences on your phone).
4. (Optional) Set CyberDon as your **Default Digital Assistant** in Android Settings -> Default Apps -> Digital Assistant app.
