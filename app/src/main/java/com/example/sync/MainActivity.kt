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
// 1. АВТОРИЗАЦИЯ (ВХОД ПО ЛОГИНУ И ПАРОЛЮ НА MY.SIMPLERUN.RU)
// =================================================================
class AuthService(private val context: Context) {
    private val client = OkHttpClient()
    private val prefs: SharedPreferences = context.getSharedPreferences("SimpleRunPrefs", Context.MODE_PRIVATE)

    fun loginAndSaveToken(email: String, javaPassword: String): Boolean {
        // Официальный эндпоинт авторизации бегового сообщества
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
                    // Вытаскиваем токен авторизации вашего профиля
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
// 2. ГЛАВНЫЙ ЭКРАН (ИНТЕРФЕЙС, ПРАВА ЗДОРОВЬЯ И ДВА РЕЖИМА СИНХРОНИЗАЦИИ)
// =================================================================
class MainActivity : AppCompatActivity() {

    // Права доступа к системному мостику Health Connect (базе Google/Samsung)
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
            setBackgroundColor(android.graphics.Color.parseColor("#CCFF00")) // Цвет Simple Run
            setTextColor(android.graphics.Color.BLACK)
        }
        layout.addView(btnReady)

        setContentView(layout)

        // Запрашиваем официальные права READ_EXERCISE и READ_HEART_RATE
        try {
            val healthConnectClient = HealthConnectClient.getOrCreate(this)
            val requestPermissionsContract = HealthConnectClient.permissionController.createRequestPermissionResultContract()
            val requestPermissionLauncher = registerForActivityResult(requestPermissionsContract) {}
            
            lifecycleScope.launchWhenStarted {
                val granted = healthConnectClient.permissionController.getGrantedPermissions()
                if (!granted.containsAll(PERMISSIONS)) {
                    requestPermissionLauncher.launch(PERMISSIONS)
                }
            }
        } catch (e: Exception) { e.printStackTrace() }

        btnReady.setOnClickListener {
            val email = inputEmail.text.toString().trim()
            val password = inputPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Заполните все поля!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Thread {
                val authService = AuthService(this)
                val isSuccess = authService.loginAndSaveToken(email, password)

                runOnUiThread {
                    if (isSuccess) {
                        Toast.makeText(this, "Вход успешен! Активация...", Toast.LENGTH_SHORT).show()
                        
                        // РЕЖИМ 1: Автоматический фоновый планировщик каждые 30 минут
                        startBackgroundSync()

                        // РЕЖИМ 2: Ручной мгновенный перенос тренировки прямо сейчас
                        triggerManualSync()
                    } else {
                        Toast.makeText(this, "Ошибка входа. Проверьте данные", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
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
        Toast.makeText(this, "Ручная выгрузка запущена...", Toast.LENGTH_SHORT).show()
    }
}
// =================================================================
// 3. ФОНОВЫЙ РОБОТ (ПОЛУЧЕНИЕ ИЗ HEALTH CONNECT + ВЫГРУЗКА В SIMPLA)
// =================================================================
class SyncDataWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {
    private val client = OkHttpClient()

    override suspend fun doWork(): Result {
        val authService = AuthService(applicationContext)
        val apiToken = authService.getSavedToken()

        if (apiToken.isNullOrEmpty()) return Result.success()

        try {
            val healthConnectClient = HealthConnectClient.getOrCreate(applicationContext)
            val endTime = Instant.now()
            val startTime = endTime.minus(2, ChronoUnit.DAYS)

            // Считываем сессии тренировок за последние 2 дня
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )

            // Если реальных тренировок нет — завершаем работу, пустые пакеты слать не нужно
            if (response.records.isEmpty()) return Result.success()

            // Берем самую последнюю активность
            val lastSession = response.records.last()
            val realDistance = lastSession.distance?.inMeters ?: 0.0
            val realDurationSeconds = ChronoUnit.SECONDS.between(lastSession.startTime, lastSession.endTime).toInt()
            
            // Превращаем даты в формат "ГГГГ-ММ-ДД ЧЧ:ММ:СС" для сервера
            val startTimeStr = lastSession.startTime.toString().replace("T", " ").substring(0, 19)
            val finishTimeStr = lastSession.endTime.toString().replace("T", " ").substring(0, 19)

            // Рассчитываем текстовое время (например, "1:07:00")
            val hours = realDurationSeconds / 3600
            val minutes = (realDurationSeconds % 3600) / 60
            val seconds = realDurationSeconds % 60
            val formattedTime = String.format("%d:%02d:%02d", hours, minutes, seconds)

            // Фильтруем пульс строго по времени проведения этой тренировки
            val heartRatePointsArray = JSONArray()
            val heartRateResponse = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(lastSession.startTime, lastSession.endTime)
                )
            )

            // Упаковываем пульс в JSON-массив точек
            heartRateResponse.records.forEach { record ->
                record.samples.forEach { sample ->
                    val offset = ChronoUnit.SECONDS.between(lastSession.startTime, sample.time).toInt()
                    val heartPoint = JSONObject().apply {
                        put("time_offset", offset)
                        put("value", sample.beatsPerMinute)
                    }
                    heartRatePointsArray.put(heartPoint)
                }
            }

            val cadencePointsArray = JSONArray()

            // Формируем внутренний объект типа активности по схеме из Charles
            val typeObject = JSONObject().apply {
                put("code", "walking") // "walking" для ходьбы, "running" для бега
                put("location", "outdoor")
                put("name", "Ходьба")
            }

            // Упаковываем данные в объект "training" по перехваченному шаблону ответа
            val trainingObject = JSONObject().apply {
                put("description", "Автоматический перенос из Samsung Health Connect")
                put("distance", realDistance)
                put("duration", realDurationSeconds)
                put("in_statistics", true)
                put("isChecked", false)
                put("isCollapsed", true)
                put("is_public", true)
                put("is_track_hidden", false)
                put("is_manual", false) // Автоматический трекер, а не ручной ввод
                put("started_at", startTimeStr)
                put("finished_at", finishTimeStr)
                put("timestamp", lastSession.startTime.epochSecond)
                put("title", "Полуденная ходьба")
                put("training_time", formattedTime)
                put("type", typeObject)
                
                // Подключаем собранные массивы графиков
                put("heart_rate_series", heartRatePointsArray)
                put("cadence_series", cadencePointsArray)
            }

            // Финальный корневой JSON-пакет
            val rootJson = JSONObject().apply { put("training", trainingObject) }.toString()
            
            // Системный адрес бегового сервера (API Endpoint) для загрузки активностей
            val url = "https://simplerun.ru"
            val body = rootJson.toRequestBody("application/json; charset=utf-8".toMediaType())
            
            val request = Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Authorization", "Bearer $apiToken") // Динамический токен авторизации
                .addHeader("Content-Type", "application/json")
                .build()

            client.newCall(request).execute().use { res ->
                return if (res.isSuccessful) Result.success() else Result.retry()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        }
    }
}
