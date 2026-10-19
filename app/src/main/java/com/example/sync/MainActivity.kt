package com.example.sync

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

// =================================================================
// 1. КЛАСС ДЛЯ РАБОТЫ С АВТОРИЗАЦИЕЙ (ВХОД ПО ЛОГИНУ / ПАРОЛЮ)
// =================================================================
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
        val request = Request.Builder().url(url).post(body).build()

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
// 2. ГЛАВНЫЙ ЭКРАН (ИНТЕРФЕЙС, КНОПКИ И РУЧНОЙ ЗАПУСК)
// =================================================================
class MainActivity : AppCompatActivity() {

    private val PERMISSIONS = setOf(
        ExerciseSessionRecord::class,
        HeartRateRecord::class
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }

        val inputEmail = EditText(this).apply { hint = "Введите Email" }
        layout.addView(inputEmail)

        val inputPassword = EditText(this).apply {
            hint = "Введите пароль"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        layout.addView(inputPassword)

        val btnReady = Button(this).apply {
            text = "Готово (Вход + Синхронизация)"
            setBackgroundColor(android.graphics.Color.parseColor("#CCFF00"))
            setTextColor(android.graphics.Color.BLACK)
        }
        layout.addView(btnReady)

        setContentView(layout)

        // Запрос доступов к Google Health при старте приложения
        checkAndRequestHealthPermissions()

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
                        Toast.makeText(this, "Вход успешен!", Toast.LENGTH_SHORT).show()
                        
                        // 1. АВТОМАТИЧЕСКАЯ СИНХРОНИЗАЦИЯ (Каждые 30 минут)
                        startBackgroundSync()

                        // 2. РУЧНАЯ СИНХРОНИЗАЦИЯ (Мгновенно прямо сейчас)
                        triggerManualSync()
                    } else {
                        Toast.makeText(this, "Ошибка входа. Проверьте данные", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        }
    }

    private fun checkAndRequestHealthPermissions() {
        try {
            val healthConnectClient = HealthConnectClient.getOrCreate(this)
            val requestPermissionsContract = HealthConnectClient.permissionController.createRequestPermissionResultContract()
            
            val requestPermissionLauncher = registerForActivityResult(requestPermissionsContract) { granted ->
                if (granted.containsAll(PERMISSIONS)) {
                    Toast.makeText(this, "Доступ к Google Health получен!", Toast.LENGTH_SHORT).show()
                }
            }

            lifecycleScope.launchWhenStarted {
                val granted = healthConnectClient.permissionController.getGrantedPermissions()
                if (!granted.containsAll(PERMISSIONS)) {
                    requestPermissionLauncher.launch(PERMISSIONS)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startBackgroundSync() {
        val syncRequest = PeriodicWorkRequestBuilder<SyncDataWorker>(30, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "SamsungToSimpleRunSync",
            ExistingPeriodicWorkPolicy.UPDATE,
            syncRequest
        )
    }

    private fun triggerManualSync() {
        val manualRequest = OneTimeWorkRequestBuilder<SyncDataWorker>().build()
        WorkManager.getInstance(this).enqueue(manualRequest)
        Toast.makeText(this, "Ручной импорт запущен...", Toast.LENGTH_SHORT).show()
    }
}
// =================================================================
// 3. ФОНОВЫЙ РОБОТ (ПОЛУЧЕНИЕ ИЗ GOOGLE HEALTH + ВЫГРУЗКА В SIMPLA)
// =================================================================
class SyncDataWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {
    private val client = OkHttpClient()

    override suspend fun doWork(): Result {
        val authService = AuthService(applicationContext)
        val apiToken = authService.getSavedToken()

        // Если пользователь еще не вошел в аккаунт, откладываем задачу
        if (apiToken.isNullOrEmpty()) return Result.success()

        try {
            // Подключаемся к базе данных Google Health Connect
            val healthConnectClient = HealthConnectClient.getOrCreate(applicationContext)
            val endTime = Instant.now()
            val startTime = endTime.minus(2, ChronoUnit.DAYS) // Ищем тренировки за последние 2 дня

            // Запрашиваем список спортивных сессий
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )

            // ЕСЛИ НАСТОЯЩИХ ТРЕНИРОВОК НЕТ — ПРЕКРАЩАЕМ РАБОТУ (Никакого хардкода!)
            if (response.records.isEmpty()) {
                return Result.success()
            }

            // Берем самую последнюю реальную тренировку из базы Google Health
            val lastSession = response.records.last()
            
            // Вытаскиваем реальные показатели из этой сессии
            val realDistance = lastSession.distance?.inMeters ?: 0.0
            val realDurationSeconds = ChronoUnit.SECONDS.between(lastSession.startTime, lastSession.endTime).toInt()
            
            // Переводим время старта и финиша в понятный серверу текстовый формат
            val startTimeStr = lastSession.startTime.toString().replace("T", " ").substring(0, 19)
            val finishTimeStr = lastSession.endTime.toString().replace("T", " ").substring(0, 19)

            // Считаем красивую текстовую строку длительности (например, "1:07:00")
            val hours = realDurationSeconds / 3600
            val minutes = (realDurationSeconds % 3600) / 60
            val seconds = realDurationSeconds % 60
            val formattedTrainingTime = String.format("%d:%02d:%02d", hours, minutes, seconds)

            // Запрашиваем реальные точки пульса строго за время проведения этой тренировки
            val heartRatePointsArray = JSONArray()
            val heartRateResponse = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(lastSession.startTime, lastSession.endTime)
                )
            )

            // Заполняем массив настоящими точками пульса
            heartRateResponse.records.forEach { record ->
                record.samples.forEach { sample ->
                    val offset = ChronoUnit.SECONDS.between(lastSession.startTime, sample.time).toInt()
                    val heartPoint = JSONObject().apply {
                        put("time_offset", offset)
                        put("value", sample.beatsPerMinute) // Реальный пульс с датчика
                    }
                    heartRatePointsArray.put(heartPoint)
                }
            }

            // Массив каденса (оставляем пустым, если датчик часов его не записал)
            val cadencePointsArray = JSONArray()

            // СБОРКА ДИНАМИЧЕСКОГО JSON-ПАКЕТА
            val typeObject = JSONObject().apply {
                put("code", "walking") // Базовый код ходьбы (можно менять на "running" при проверке типа)
                put("location", "outdoor")
                put("name", "Ходьба")
            }

            val trainingObject = JSONObject().apply {
                put("description", "Автоматический импорт из Google Health Connect")
                put("distance", realDistance)           // Реальная дистанция из Samsung/Google
                put("duration", realDurationSeconds)     // Реальное время в секундах
                put("in_statistics", true)
                put("isChecked", false)
                put("isCollapsed", true)
                put("is_public", true)
                put("is_track_hidden", false)
                put("is_manual", false)                 // Указываем false — данные получены автоматически
                put("started_at", startTimeStr)         // Настоящее время старта
                put("finished_at", finishTimeStr)       // Настоящее время финиша
                put("timestamp", lastSession.startTime.epochSecond)
                put("title", "Импортированная тренировка")
                put("training_time", formattedTrainingTime) // Настоящая длительность строкой
                put("type", typeObject)
                
                put("heart_rate_series", heartRatePointsArray) // Настоящий график пульса
                put("cadence_series", cadencePointsArray)
            }

            val rootJson = JSONObject().apply { put("training", trainingObject) }.toString()
            
            // Направление данных на сервер Simple Run
            val url = "https://simplerun.ru"
            val body = rootJson.toRequestBody("application/json; charset=utf-8".toMediaType())
            
            val request = Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Authorization", "Bearer $apiToken")
                .addHeader("Content-Type", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                return if (response.isSuccessful) Result.success() else Result.retry()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry() // В случае сбоя сети WorkManager попробует еще раз позже
        }
    }
}

