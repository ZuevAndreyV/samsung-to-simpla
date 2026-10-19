package com.example.sync

import android.content.Context
import android.content.SharedPreferences
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class AuthService(private val context: Context) {
    private val client = OkHttpClient()
    private val prefs: SharedPreferences = context.getSharedPreferences("SimpleRunPrefs", Context.MODE_PRIVATE)

    fun loginAndSaveToken(email: String, javaPassword: String): Boolean {
        val url = "https://simplerun.ru"

        val jsonPayload = JSONObject().apply {
            put("email", email)
            put("password", javaPassword)
        }.toString()

        val body = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseData = response.body?.string() ?: ""
                    val jsonResponse = JSONObject(responseData)
                    val token = jsonResponse.optString("token", "")

                    if (token.isNotEmpty()) {
                        prefs.edit().putString("api_token", token).apply()
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    fun getSavedToken(): String? {
        return prefs.getString("api_token", null)
    }
}
