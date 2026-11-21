package com.cyberdon.assistant

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

class GeminiBrain(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .callTimeout(18, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private fun getApiKey(): String = context.getSharedPreferences("cyberdon", Context.MODE_PRIVATE)
        .getString("gemini_api_key", "")?.trim().orEmpty()

    private val endpointBase = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent?key="

    suspend fun askCyberDon(userQuery: String, history: List<Pair<String, String>>): Pair<List<ActionItem>, String> = withContext(Dispatchers.IO) {
        val key = getApiKey()
        if (key.isBlank()) return@withContext localFallback(userQuery, "Gemini key phone mein save nahi hai.")
        try {
            val contents = JSONArray()
            history.takeLast(4).forEach { (user, assistant) ->
                contents.put(turn("user", user)); contents.put(turn("model", assistant))
            }
            contents.put(turn("user", userQuery))

            val system = """You are CyberDon, a fast and smart Android AI voice assistant. Speak crisp, sharp Hindi/Hinglish.
DO NOT start sentences with "Ali" or "Ali bhai" or repeat the user's name unless directly asked what their name is.
DO NOT recite limitation lists unless the user specifically asks "tum kya nahi kar sakte".
Answer questions directly, concisely (1-2 sentences), like a natural conversation.
Available device actions:
- OPEN_APP (target: app name e.g. youtube, whatsapp, freefire, camera, gallery, chrome, etc.)
- TORCH_ON, TORCH_OFF, TORCH_TOGGLE
- BATTERY_STATUS
- VOLUME_UP, VOLUME_DOWN, MUTE, MAX_VOLUME
- TAKE_SCREENSHOT
- GO_HOME, GO_BACK, RECENTS, NOTIFICATIONS, QUICK_SETTINGS, LOCK_SCREEN, SPLIT_SCREEN
- SWIPE_UP, SWIPE_DOWN
- READ_SCREEN
- YOUTUBE_SEARCH (target: search query)
- WEB_SEARCH (target: search query)
- VIBRATE
- TIME_DATE
- SETTINGS_PANEL (target: wifi, bluetooth, display, sound)
Return raw JSON only: {"actions":[{"action":"TORCH_ON"}],"speech":"Torch on kar di hai."}""".trimIndent()

            val payload = JSONObject().apply {
                put("contents", contents)
                put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                put("generationConfig", JSONObject().put("temperature", 0.2).put("maxOutputTokens", 250).put("responseMimeType", "application/json"))
            }

            val request = Request.Builder()
                .url(endpointBase + URLEncoder.encode(key, "UTF-8"))
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) return@withContext localFallback(userQuery, providerError(response.code))
                val root = JSONObject(text)
                val raw = root.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")
                    ?.optJSONArray("parts")?.optJSONObject(0)?.optString("text").orEmpty()
                if (raw.isBlank()) return@withContext localFallback(userQuery, "AI response empty tha.")
                val parsed = parseJson(raw) ?: return@withContext localFallback(userQuery, "AI JSON parse nahi hua.")
                val actions = mutableListOf<ActionItem>()
                parsed.optJSONArray("actions")?.let { array ->
                    for (i in 0 until minOf(array.length(), 4)) {
                        val item = array.optJSONObject(i) ?: continue
                        actions.add(ActionItem(item.optString("action"), item.optString("target").takeUnless { it == "null" || it.isBlank() }))
                    }
                }
                val speech = parsed.optString("speech").ifBlank { "Ho gaya." }
                Pair(actions, speech)
            }
        } catch (e: Exception) {
            localFallback(userQuery, when (e) {
                is java.net.UnknownHostException -> "Internet connect nahi hai."
                is java.net.SocketTimeoutException -> "Network response slow hai."
                else -> "AI service reach nahi ho rahi."
            })
        }
    }

    private fun turn(role: String, text: String) = JSONObject().put("role", role).put("parts", JSONArray().put(JSONObject().put("text", text)))

    private fun parseJson(text: String): JSONObject? {
        return try { JSONObject(text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()) }
        catch (_: Exception) {
            val a = text.indexOf('{'); val b = text.lastIndexOf('}')
            if (a >= 0 && b > a) try { JSONObject(text.substring(a, b + 1)) } catch (_: Exception) { null } else null
        }
    }

    private fun providerError(code: Int): String = when (code) {
        401, 403 -> "API key galat ya expired hai ($code)."
        404 -> "Model unavailable hai (404)."
        429 -> "Gemini quota limit poori hui (429)."
        in 500..599 -> "Gemini server busy hai ($code)."
        else -> "AI error ($code)."
    }

    fun localFallback(query: String, reason: String = ""): Pair<List<ActionItem>, String> {
        val q = query.lowercase(Locale.ROOT).trim()
        val actions = mutableListOf<ActionItem>()

        // Torch / Flashlight
        if (listOf("torch on", "light on", "flashlight on", "torch jalao", "light jalao", "torch chalao").any { q.contains(it) }) {
            actions.add(ActionItem("TORCH_ON", null))
            return Pair(actions, "Torch on kar di hai.")
        }
        if (listOf("torch off", "light off", "flashlight off", "torch band", "light band", "torch bujhao").any { q.contains(it) }) {
            actions.add(ActionItem("TORCH_OFF", null))
            return Pair(actions, "Torch band kar di hai.")
        }
        if (q == "torch" || q == "flashlight" || q.contains("torch toggle")) {
            actions.add(ActionItem("TORCH_TOGGLE", null))
            return Pair(actions, "Torch toggle kar di hai.")
        }

        // Battery
        if (listOf("battery", "बैटरी", "charge", "charging", "battery kitni hai").any { q.contains(it) }) {
            actions.add(ActionItem("BATTERY_STATUS", null))
            return Pair(actions, "Battery status check kar raha hoon.")
        }

        // Volume controls
        if (listOf("volume up", "awaaz badhao", "sound badhao", "volume badhao", "vol +").any { q.contains(it) }) {
            actions.add(ActionItem("VOLUME_UP", null))
            return Pair(actions, "Volume badha raha hoon.")
        }
        if (listOf("volume down", "awaaz kam karo", "sound kam", "volume kam", "vol -").any { q.contains(it) }) {
            actions.add(ActionItem("VOLUME_DOWN", null))
            return Pair(actions, "Volume kam kar raha hoon.")
        }
        if (listOf("mute", "silent", "awaaz band", "sound band").any { q.contains(it) }) {
            actions.add(ActionItem("MUTE", null))
            return Pair(actions, "Phone mute kar diya hai.")
        }
        if (listOf("full volume", "max volume", "puri awaaz").any { q.contains(it) }) {
            actions.add(ActionItem("MAX_VOLUME", null))
            return Pair(actions, "Volume full kar di hai.")
        }

        // Screenshot
        if (listOf("screenshot", "screen capture", "screen photo", "photo lo screen").any { q.contains(it) }) {
            actions.add(ActionItem("TAKE_SCREENSHOT", null))
            return Pair(actions, "Screenshot le raha hoon.")
        }

        // Navigation
        if (listOf("home", "go home", "home screen", "main screen").any { q.contains(it) }) {
            actions.add(ActionItem("GO_HOME", null))
            return Pair(actions, "Home screen par aa gaya hoon.")
        }
        if (listOf("back", "go back", "peechhe", "wapas").any { q.contains(it) }) {
            actions.add(ActionItem("GO_BACK", null))
            return Pair(actions, "Back kar raha hoon.")
        }
        if (listOf("recents", "recent apps", "overview", "background apps").any { q.contains(it) }) {
            actions.add(ActionItem("RECENTS", null))
            return Pair(actions, "Recent apps khol raha hoon.")
        }
        if (listOf("notification", "notifications", "notification shade", "messages shade").any { q.contains(it) }) {
            actions.add(ActionItem("NOTIFICATIONS", null))
            return Pair(actions, "Notifications panel open kar raha hoon.")
        }
        if (listOf("quick settings", "toggles panel", "control center").any { q.contains(it) }) {
            actions.add(ActionItem("QUICK_SETTINGS", null))
            return Pair(actions, "Quick settings khol raha hoon.")
        }
        if (listOf("lock screen", "phone lock", "screen lock").any { q.contains(it) }) {
            actions.add(ActionItem("LOCK_SCREEN", null))
            return Pair(actions, "Screen lock kar raha hoon.")
        }
        if (listOf("split screen", "dual screen").any { q.contains(it) }) {
            actions.add(ActionItem("SPLIT_SCREEN", null))
            return Pair(actions, "Split screen toggle kar raha hoon.")
        }

        // Scrolling
        if (listOf("scroll down", "neeche scroll", "neeche karo", "swipe up").any { q.contains(it) }) {
            actions.add(ActionItem("SWIPE_UP", null))
            return Pair(actions, "Neeche scroll kar raha hoon.")
        }
        if (listOf("scroll up", "upar scroll", "upar karo", "swipe down").any { q.contains(it) }) {
            actions.add(ActionItem("SWIPE_DOWN", null))
            return Pair(actions, "Upar scroll kar raha hoon.")
        }

        // Screen Read
        if (listOf("screen read", "read screen", "screen padho", "kya likha hai", "screen par kya hai").any { q.contains(it) }) {
            actions.add(ActionItem("READ_SCREEN", null))
            return Pair(actions, "Screen padh raha hoon.")
        }

        // YouTube Search
        if (q.contains("youtube") && listOf("search", "dhundo", "dhoondo", "play", "chalao").any { q.contains(it) }) {
            val queryPart = query.substringAfter("search", "").substringAfter("dhundo", "")
                .substringAfter("dhoondo", "").substringAfter("chalao", "").trim()
            if (queryPart.isNotBlank()) {
                actions.add(ActionItem("YOUTUBE_SEARCH", queryPart))
                return Pair(actions, "YouTube par $queryPart search kar raha hoon.")
            }
        }

        // App launch
        val appKeywords = listOf("open", "kholo", "chalao", "launch")
        val hasOpen = appKeywords.any { q.contains(it) }
        val rawApp = if (hasOpen) {
            var temp = q
            appKeywords.forEach { temp = temp.replace(it, "") }
            temp.replace("app", "").trim()
        } else null

        if (!rawApp.isNullOrBlank() && rawApp.length > 1) {
            actions.add(ActionItem("OPEN_APP", rawApp))
            return Pair(actions, "$rawApp open kar raha hoon.")
        }

        // Time / Date
        if (listOf("time", "samay", "date", "tareekh", "din", "aaj kya din").any { q.contains(it) }) {
            actions.add(ActionItem("TIME_DATE", null))
            return Pair(actions, "Samay aur date check kar raha hoon.")
        }

        // Vibration
        if (listOf("vibrate", "phone hilao", "vibration").any { q.contains(it) }) {
            actions.add(ActionItem("VIBRATE", null))
            return Pair(actions, "Phone vibrate kar raha hoon.")
        }

        // Settings panels
        if (q.contains("wifi")) {
            actions.add(ActionItem("SETTINGS_PANEL", "wifi"))
            return Pair(actions, "Wi-Fi settings open kar raha hoon.")
        }
        if (q.contains("bluetooth")) {
            actions.add(ActionItem("SETTINGS_PANEL", "bluetooth"))
            return Pair(actions, "Bluetooth settings open kar raha hoon.")
        }

        // General greeting / follow-up
        if (listOf("batao", "hello", "hi", "sun", "suno", "cyberdon").any { q == it }) {
            return Pair(emptyList(), "Haan, boliye kya command chalani hai?")
        }

        val fallbackMsg = if (reason.isNotBlank()) "$reason Offline device controls chal rahe hain." else "Offline basic commands available hain."
        return Pair(emptyList(), fallbackMsg)
    }
}

data class ActionItem(val action: String?, val target: String?)
