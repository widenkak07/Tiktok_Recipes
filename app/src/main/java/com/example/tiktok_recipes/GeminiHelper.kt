package com.example.tiktok_recipes

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object GeminiHelper {

    private val API_KEY = BuildConfig.GEMINI_API_KEY
    private const val MODEL = "gemini-2.5-flash"
    private val URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$API_KEY"

    private val client = OkHttpClient()

    // Zwraca surowy tekst odpowiedzi Gemini (oczekujemy czystego JSON-a w środku)
    fun askGemini(prompt: String): String {
        val requestBodyJson = JSONObject().apply {
            put("contents", org.json.JSONArray().put(
                JSONObject().apply {
                    put("parts", org.json.JSONArray().put(
                        JSONObject().put("text", prompt)
                    ))
                }
            ))
        }

        val body = requestBodyJson.toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(URL)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw Exception("Błąd Gemini API: ${response.code} - $responseBody")
            }
            val json = JSONObject(responseBody)
            return json.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        }
    }
}