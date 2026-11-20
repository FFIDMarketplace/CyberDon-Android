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
import java.util.concurrent.TimeUnit

class GeminiBrain(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(18, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .build()

    private fun getApiKey(): String = context.getSharedPreferences("cyberdon", Context.MODE_PRIVATE)
        .getString("gemini_api_key", "")?.trim().orEmpty()

    private val endpointBase = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent?key="

    suspend fun askCyberDon(userQuery: String, history: List<Pair<String, String>>): Pair<List<ActionItem>, String> = withContext(Dispatchers.IO) {
        try {
            val contentsArray = JSONArray()

            // Attach recent conversation turns
            for (turn in history.takeLast(6)) {
                contentsArray.put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", turn.first)))
                })
                contentsArray.put(JSONObject().apply {
                    put("role", "model")
                    put("parts", JSONArray().put(JSONObject().put("text", turn.second)))
                })
            }

            // Current query
            contentsArray.put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", userQuery)))
            })

            val systemInstruction = """You are CyberDon, an ultra-smart Jarvis-level voice assistant for Ali.
Boss Name: Ali. Always address him respectfully.
Available Actions:
- OPEN_APP (target: freefire, snapchat, youtube, whatsapp, camera, gallery, settings, instagram, chrome)
- CLOSE_APP (target: null)
- SPLIT_SCREEN (target: null)
- YOUTUBE_SEARCH (target: query)
- BATTERY_STATUS (target: null)
- VIBRATE (target: seconds)

CRITICAL RULES:
1. NO VISION: You cannot see inside photos or camera feed. Honestly tell Ali: "Ali bhai, mere paas photo ya screen dekhne ka camera sensor nahi hai."
2. FORMAT: Return STRICT RAW JSON only:
{
  "actions": [{"action": "ACTION_NAME"|null, "target": string|null}],
  "speech": "What CyberDon says aloud to Ali in natural Hindi"
}
""".trimIndent()

            val payload = JSONObject().apply {
                put("contents", contentsArray)
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1)
                    put("maxOutputTokens", 300)
                })
            }

            val apiKey = getApiKey()
            if (apiKey.isBlank()) return@withContext Pair(emptyList(), "Pehle CyberDon settings mein apni Gemini API key save karein.")
            val body = payload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(endpointBase + java.net.URLEncoder.encode(apiKey, "UTF-8")).post(body).build()

            val response = client.newCall(request).execute()
            val resStr = response.body?.string() ?: ""
            val json = JSONObject(resStr)
            val raw = json.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")

            val cleanJson = raw.substringAfter("{").substringBeforeLast("}")
            val parsed = JSONObject("{$cleanJson}")

            val actions = mutableListOf<ActionItem>()
            val actionsArr = parsed.optJSONArray("actions")
            if (actionsArr != null) {
                for (i in 0 until actionsArr.length()) {
                    val item = actionsArr.getJSONObject(i)
                    actions.add(ActionItem(item.optString("action"), item.optString("target", null)))
                }
            }

            val speech = parsed.optString("speech", "Ji Ali bhai.")
            Pair(actions, speech)
        } catch (e: Exception) {
            Pair(emptyList(), "Ali bhai, network connectivity check karein.")
        }
    }
}

data class ActionItem(val action: String?, val target: String?)
