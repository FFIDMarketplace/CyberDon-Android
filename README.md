# CyberDon Android AI Assistant

CyberDon is a native Android voice and device assistant powered by Google Gemini and native Android Accessibility & Hardware APIs.

## Key Features

- **Direct Floating Voice Trigger**: A clean, draggable floating bubble (`CD 🎙`) that floats on top of any app. Tapping the bubble immediately activates speech recognition or stops speech.
- **Clean Screenshot Capture**: Automatically hides the CyberDon floating bubble prior to screen capture, waits for surface compositor refresh, and restores the bubble after capture.
- **Microphone Foreground Service**: Runs with `FOREGROUND_SERVICE_MICROPHONE` type for reliable microphone access on modern Android versions (Android 10 - 14+).
- **Comprehensive Hardware & App Control**:
  - Torch ON / OFF / Toggle via CameraManager
  - Volume control (Up, Down, Max, Mute)
  - Battery capacity readouts
  - Current time and date
  - Device vibration
  - Screen navigation (Home, Back, Recents, Notifications, Quick Settings, Lock Screen)
  - Accessibility scrolling (Swipe Up, Swipe Down)
  - Screen text reading (`readVisibleText`)
  - Direct app launching (Free Fire, WhatsApp, YouTube, Instagram, Camera, Gallery, Chrome, etc.)
  - Split screen toggling
  - In-app YouTube search
  - Local device setting shortcuts (WiFi, Bluetooth, Display, Sound)
- **Local Fallback**: If internet is unavailable or Gemini API encounters errors, essential device commands continue running locally.

## Setup Instructions

1. **Install APK**: Download `app-debug.apk` from the latest GitHub Actions build artifact.
2. **Microphone Permission**: Allow microphone access when prompted.
3. **Accessibility Service**: In phone Settings > Accessibility > Installed apps > Enable **CyberDon Assistant**.
4. **Appear on Top Permission**: In phone Settings > Apps > Special app access > Appear on top > Enable **CyberDon**.
5. **Gemini API Key**: Open the CyberDon app, tap "Set Gemini API Key", and enter your key (stored locally on your device).
6. **Start Session**: Tap **Start hands-free voice session**. The floating bubble will appear on screen.
