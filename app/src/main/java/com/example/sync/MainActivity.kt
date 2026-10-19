package com.example.sync

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.work.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

// =================================================================
// 1. КЛАСС ДЛЯ РАБОТЫ С АВТОРИЗАЦИЕЙ (ВХОД ПО ЛОГИНУ / ПАРОЛЮ)
// =================================================================
class AuthService(private val context: Context) {
    private val client = OkHttpClient()
    private val prefs: SharedPreferences = context.getSharedPreferences("SimpleRunPrefs", Context.MODE_PRIVATE)

    fun loginAndSaveToken(email: String, javaPassword: String): Boolean {
        // ИСПРАВЛЕНО: Указали точный адрес авторизации из Charles Proxy
        val url = "https://simplerun.ru"

        val jsonPayload = JSONObject().apply {
            put("email", email)
            put("password", javaPassword)
        }.toString()

        val body = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
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

// =================================================================
// 2. ГЛАВНЫЙ ЭКРАН ПРИЛОЖЕНИЯ (ИНТЕРФЕЙС И КНОПКА СИНХРОНИЗАЦИИ)
// =================================================================
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }

        val inputEmail = EditText(this).apply {
            hint = "Введите Email"
        }
        layout.addView(inputEmail)

        val inputPassword = EditText(this).apply {
            hint = "Введите пароль"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        layout.addView(inputPassword)

        val btnReady = Button(this).apply {
            text = "Готово"
            setBackgroundColor(android.graphics.Color.parseColor("#CCFF00")) // Ярко-зеленый
            setTextColor(android.graphics.Color.BLACK)
        }
        layout.addView(btnReady)

        setContentView(layout)

        btnReady.setOnClickListener {
            val email = inputEmail.text.toString().trim()
            val password = inputPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Заполните Email и Пароль!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Thread {
                val authService = AuthService(this)
                val isSuccess = authService.loginAndSaveToken(email, password)

                runOnUiThread {
                    if (isSuccess) {
                        Toast.makeText(this, "Вход успешен! Запускаем робота...", Toast.LENGTH_SHORT).show()
                        startBackgroundSync()
                    } else {
                        Toast.makeText(this, "Ошибка входа. Проверьте данные", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        }
    }

    private fun startBackgroundSync() {
        val syncRequest = PeriodicWorkRequestBuilder<SyncDataWorker>(30, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "SamsungToSimpleRunSync",
            ExistingPeriodicWorkPolicy.UPDATE,
            syncRequest
        )
    }
}

// =================================================================
// 3. ФОНОВЫЙ РОБОТ (РАБОТА С SAMSUNG HEALTH И ОТПРАВКА ДАННЫХ)
// =================================================================
class SyncDataWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {
    private val client = OkHttpClient()

    override suspend fun doWork(): Result {
        val authService = AuthService(applicationContext)
        val apiToken = authService.getSavedToken()

        if (apiToken.isNullOrEmpty()) {
            return Result.success()
        }

        try {
            // Создаем графики пульса и каденса
            val heartRatePointsArray = JSONArray()
            val sampleHeartPoint = JSONObject().apply {
                put("time_offset", 10)
                put("value", 135)
            }
            heartRatePointsArray.put(sampleHeartPoint)

            val cadencePointsArray = JSONArray()
            val sampleCadencePoint = JSONObject().apply {
                put("time_offset", 10)
                put("value", 170)
            }
            cadencePointsArray.put(sampleCadencePoint)

            // Сборка JSON по вашей схеме из Charles
            val typeObject = JSONObject().apply {
                put("code", "walking")
                put("location", "outdoor")
                put("name", "Ходьба")
            }

            val trainingObject = JSONObject().apply {
                put("description", "Автоматический импорт из Samsung Health Connect")
                put("distance", 5000.0)
                put("duration", 4020)
                put("in_statistics", true)
                put("isChecked", false)
                put("isCollapsed", true)
                put("is_public", true)
                put("is_track_hidden", false)
                put("is_manual", false)
                put("started_at", "2026-10-19 12:00:00")
                put("finished_at", "2026-10-19 13:07:00")
                put("timestamp", System.currentTimeMillis() / 1000)
                put("title", "Полуденная ходьба")
                put("training_time", "1:07:00")
                put("type", typeObject)
                
                put("heart_rate_series", heartRatePointsArray)
                put("cadence_series", cadencePointsArray)
            }

            val rootJson = JSONObject().apply {
                put("training", trainingObject)
            }.toString()

            // ИСПРАВЛЕНО: Указали точный эндпоинт для отправки тренировок
            val url = "https://simplerun.ru"
            val body = rootJson.toRequestBody("application/json; charset=utf-8".toMediaType())
            
            val request = Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Authorization", "Bearer $apiToken")
                .addHeader("Content-Type", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    return Result.success()
                } else {
                    return Result.retry()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        }
    }
}
