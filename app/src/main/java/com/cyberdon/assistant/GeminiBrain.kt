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
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private fun getApiKey(): String = context.getSharedPreferences("cyberdon", Context.MODE_PRIVATE)
        .getString("gemini_api_key", "")?.trim().orEmpty()

    private val endpointBase = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent?key="

    suspend fun askCyberDon(userQuery: String, history: List<Pair<String, String>>): Pair<List<ActionItem>, String> = withContext(Dispatchers.IO) {
        val key = getApiKey()
        if (key.isBlank()) return@withContext localFallback(userQuery, "Gemini key phone settings mein save nahi hai.")
        try {
            val contents = JSONArray()
            history.takeLast(6).forEach { (user, assistant) ->
                contents.put(turn("user", user)); contents.put(turn("model", assistant))
            }
            contents.put(turn("user", userQuery))
            val system = """You are CyberDon, Ali's Hindi/Hinglish Android voice assistant. Interpret Hindi, Hinglish, English and common speech-recognition errors. Do not claim an action succeeded until the app verifies it. Never claim to see camera/video/photos or read private messages. Screen reading is permitted only from visible accessibility text. Only use the listed actions, max 4 actions, in the order needed. For non-device questions, answer directly and return actions []. If unsure, ask a short clarification.
Available actions: OPEN_APP target package/app name; CLOSE_APP target null; GO_HOME target null; SPLIT_SCREEN target null; YOUTUBE_SEARCH target query; BATTERY_STATUS target null; VIBRATE target null; SWIPE_UP target null; SWIPE_DOWN target null; READ_SCREEN target null.
Return raw JSON only: {"actions":[{"action":"OPEN_APP","target":"youtube"}],"speech":"Hindi answer"}.""".trimIndent()
            val payload = JSONObject().apply {
                put("contents", contents)
                put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                put("generationConfig", JSONObject().put("temperature", 0.2).put("maxOutputTokens", 300).put("responseMimeType", "application/json"))
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
                if (raw.isBlank()) return@withContext localFallback(userQuery, "AI ne khaali jawab diya; basic offline command try kar sakte hain.")
                val parsed = parseJson(raw) ?: return@withContext localFallback(userQuery, "AI ka response samajh nahi aaya; basic offline command try karein.")
                val actions = mutableListOf<ActionItem>()
                parsed.optJSONArray("actions")?.let { array ->
                    for (i in 0 until minOf(array.length(), 4)) {
                        val item = array.optJSONObject(i) ?: continue
                        actions.add(ActionItem(item.optString("action"), item.optString("target").takeUnless { it == "null" || it.isBlank() }))
                    }
                }
                Pair(actions, parsed.optString("speech").ifBlank { "Ji Ali bhai." })
            }
        } catch (e: Exception) {
            localFallback(userQuery, when (e) {
                is java.net.UnknownHostException -> "Internet unavailable hai. Basic phone commands offline try kar sakta hoon."
                is java.net.SocketTimeoutException -> "AI response abhi slow hai. Basic phone command offline try kar sakta hoon."
                else -> "AI service abhi nahi mili. Basic phone commands offline try kar sakta hoon."
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
        401, 403 -> "Gemini API key rejected ($code). Settings mein key check karein. Basic offline commands available hain."
        404 -> "Gemini model ya endpoint nahi mila (404). Basic offline commands available hain."
        429 -> "Gemini ki request limit poori hui (429). Thodi der baad try karein; basic commands chalenge."
        in 500..599 -> "Gemini service abhi busy hai ($code). Basic commands offline try karein."
        else -> "Gemini request failed ($code). Basic commands offline try karein."
    }

    private fun localFallback(query: String, reason: String): Pair<List<ActionItem>, String> {
        val q = query.lowercase(Locale.ROOT)
        val actions = mutableListOf<ActionItem>()
        val apps = listOf("youtube", "whatsapp", "instagram", "snapchat", "chrome", "camera", "gallery", "settings", "termux", "free fire", "freefire")
        val app = apps.firstOrNull { q.contains(it) }
        val hasOpen = listOf("open", "kholo", "khol", "chalao", "launch").any { q.contains(it) }
        if (app != null && hasOpen) actions.add(ActionItem("OPEN_APP", app.replace(" ", "")))
        if (q.contains("youtube") && listOf("search", "dhundo", "dhoondo", "play").any { q.contains(it) }) {
            val queryPart = query.substringAfter("search", "").substringAfter("dhundo", "").substringAfter("dhoondo", "").trim()
            if (queryPart.isNotBlank()) actions.add(ActionItem("YOUTUBE_SEARCH", queryPart))
        }
        if (listOf("battery", "बैटरी", "charge", "चार्ज").any { q.contains(it) }) actions.add(ActionItem("BATTERY_STATUS", null))
        if (listOf("vibrate", "vibration", "kampan", "vibrate karo", "वाइब्रेट").any { q.contains(it) }) actions.add(ActionItem("VIBRATE", null))
        if (listOf("screen par kya", "screen read", "screen pe kya", "padh ke", "read screen").any { q.contains(it) }) actions.add(ActionItem("READ_SCREEN", null))
        if (listOf("scroll down", "neeche scroll", "neeche karo", "swipe up").any { q.contains(it) }) actions.add(ActionItem("SWIPE_UP", null))
        if (listOf("scroll up", "upar scroll", "upar karo", "swipe down").any { q.contains(it) }) actions.add(ActionItem("SWIPE_DOWN", null))
        if (q.contains("home") || q.contains("home screen")) actions.add(ActionItem("GO_HOME", null))
        val speech = if (actions.isNotEmpty()) "Internet nahi chal raha, main basic command offline try kar raha hoon." else "$reason Main offline jawab yaad se nahi banaunga, kyunki galat baat keh sakta hoon. Network theek hone par sawal dobara boliye."
        return Pair(actions, speech)
    }
}

data class ActionItem(val action: String?, val target: String?)
