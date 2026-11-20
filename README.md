# CyberDon - Native Android Assistant for Ali

CyberDon is a native Android AI voice assistant designed specifically to bypass Android background restrictions and provide seamless phone automation (YouTube, Free Fire Max, Camera, Split-Screen, System Settings) using Google Gemini AI and Android Accessibility Service.

## Download & Installation
1. Go to **Releases** tab on the right side of this repository.
2. Download **`CyberDon.apk`** directly to your phone.
3. Tap on the downloaded APK and select **Install** (Allow Unknown Sources if prompted).

## Setup on Phone (1-time)
1. Open the **CyberDon** app.
2. Tap **Enable Accessibility Service** -> Find **CyberDon** -> Turn it **ON**.
3. Tap **Enable Appear on Top** -> Allow permission.
4. Tap **Set as Default Assistant** -> Select **CyberDon** (replaces Google Assistant / Bixby).

## Features
- Accessibility-based app launching and basic gesture control (Android may still restrict some actions).
- Gemini Flash Lite, with the API key entered privately in the app and stored locally.
- Manual voice input on the initial version; always-on wake-word listening is not enabled yet.

## Important limitations
- The API key is a client-side secret and can be extracted from a rooted or reverse-engineered device; use a restricted key and monitor quota.
- This first build does not guarantee hands-free wake-word detection, camera vision, or reliable app closing. Android's official assistant integration and background microphone rules require additional implementation and device testing.
