# CyberDon Android

A user-started Android voice assistant prototype. It uses Android speech recognition and text-to-speech, Gemini for intent/answers when configured, and AccessibilityService only for explicitly requested visible UI actions.

## What this build is intended to do
- Start one visible foreground voice session from the app; after each response it listens again, so you do not need to reopen the app or tap the mic for every turn.
- Show a persistent Android notification while the microphone session is active. Stop it with the notification's Stop action.
- Offer a movable `CD` overlay bubble when Android's Appear on Top permission is granted. Tap it to show status/listen controls.
- Keep basic commands available when Gemini is unavailable: launch a few named apps, YouTube search, battery percentage, vibration, home, visible-screen text, and vertical swipe gestures.
- Report action requests separately from actions actually verified. App launch is checked against the active accessibility window; Android may still block or delay a requested action.
- Keep the Gemini key in the app's local preferences. Never commit API keys to GitHub or send them in chat.

## Important limits
- This is **not** a wake-word engine: it does not continuously detect “CyberDon” while stopped. Android does not permit a normal app to secretly keep the microphone open; the user must start the foreground voice session and see its notification. Speech recognition may use the phone's speech provider/network; offline speech recognition depends on the installed provider and language pack.
- The session may be stopped by the user, Android, battery controls, permission changes, or manufacturer background limits. Keep CyberDon's notification visible and allow its background activity if the phone offers that setting.
- Accessibility actions require the user to enable the CyberDon accessibility service. They do not grant unrestricted control. Secure screens, protected app content, some split-screen modes, Samsung-specific controls, calls, private messages, and camera/video contents are not promised.
- Screen text is limited to visible text exposed by Android accessibility. It is not OCR or camera vision and may miss custom-drawn app content.
- Network loss does not turn the app into a general offline AI. Only the documented basic command patterns have a local fallback; factual answers require the configured AI/network.
- Build success proves compilation only. Test the APK on the target phone, permissions, each action, microphone privacy indicator, battery behavior, and speech provider before relying on it.

## Setup
1. Install the APK and open CyberDon.
2. Add your own Gemini API key in the app if you want AI answers. If an old key was posted publicly, revoke it at Google AI Studio and make a new one.
3. Enable Accessibility from CyberDon's button. Grant Appear on Top if you want the floating bubble.
4. Tap **Start hands-free voice session** and grant microphone/notification access. Keep the visible notification to keep the session active; tap **Stop** there to end it.
5. Verify every action on the phone. Android prompts and app permissions remain under the user's control.

## Build
GitHub Actions runs `gradle assembleDebug` on pushes to `main` and manual workflow dispatches. The debug APK is an Actions artifact; it is not a signed Play Store release.
