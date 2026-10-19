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
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

// =================================================================
// 1. КЛАСС ДЛЯ РАБОТЫ С АВТОРИЗАЦИЕЙ (ВХОД ПО ЛОГИНУ / ПАРОЛЮ)
// =================================================================
class AuthService(private val context: Context) {
    private val client = OkHttpClient()
    private val prefs: SharedPreferences = context.getSharedPreferences("SimpleRunPrefs", Context.MODE_PRIVATE)

    // Функция отправки логина и пароля на сервер по адресу из Charles Proxy
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
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseData = response.body?.string() ?: ""
                    val jsonResponse = JSONObject(responseData)
                    
                    // Достаем токен из ответа сервера
                    val token = jsonResponse.optString("token", "")

                    if (token.isNotEmpty()) {
                        // Сохраняем токен в память устройства
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

    // Метод для чтения сохраненного токена фоновым роботом
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

        // Создаем простой внешний вид программно (без XML файлов), чтобы GitHub Actions не ругался при сборке
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }

        // Поле ввода Почты
        val inputEmail = EditText(this).apply {
            hint = "Введите Email"
        }
        layout.addView(inputEmail)

        // Поле ввода Пароля
        val inputPassword = EditText(this).apply {
            hint = "Введите пароль"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        layout.addView(inputPassword)

        // Кнопка Готово
        val btnReady = Button(this).apply {
            text = "Готово"
            setBackgroundColor(android.graphics.Color.parseColor("#CCFF00")) // Ярко-зеленый цвет как на скриншоте
            setTextColor(android.graphics.Color.BLACK)
        }
        layout.addView(btnReady)

        setContentView(layout)

        // Обработка нажатия на кнопку "Готово"
        btnReady.setOnClickListener {
            val email = inputEmail.text.toString().trim()
            val password = inputPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Заполните Email и Пароль!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Запускаем проверку в фоновом потоке сети
            Thread {
                val authService = AuthService(this)
                val isSuccess = authService.loginAndSaveToken(email, password)

                runOnUiThread {
                    if (isSuccess) {
                        Toast.makeText(this, "Вход успешен! Запускаем робота...", Toast.LENGTH_SHORT).show()
                        
                        // Запускаем планировщик фоновых задач WorkManager на каждые 30 минут
                        startBackgroundSync()
                    } else {
                        Toast.makeText(this, "Ошибка входа. Проверьте данные", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        }
    }

    // Настройка периодического фонового робота
    private fun startBackgroundSync() {
        val syncRequest = PeriodicWorkRequestBuilder<SyncDataWorker>(30, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED) // Только при наличии интернета
                    .build()
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "SamsungToSimpleRunSync",
            ExistingPeriodicWorkPolicy.UPDATE, // Обновляем задачу, если она уже была запущена
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
        // Загружаем токен, который мы получили на главном экране при входе
        val authService = AuthService(applicationContext)
        val apiToken = authService.getSavedToken()

        // Если пользователь еще не залогинился, завершаем работу до следующего раза
        if (apiToken.isNullOrEmpty()) {
            return Result.success()
        }

        try {
            // -----------------------------------------------------------------
            // ЗДЕСЬ НАХОДИТСЯ ВАШ РАБОЧИЙ КОД ИЗВЛЕЧЕНИЯ ДАННЫХ SAMSUNG HEALTH
            // (Чтение READ_EXERCISE, READ_HEART_RATE за последние 2 дня)
            // -----------------------------------------------------------------
            
            // Имитация сборки данных в JSON пакет (замените на ваш готовый упакованный JSONObject)
            val trainingDataJson = JSONObject().apply {
                put("status", "sync")
                put("source", "Samsung Health Connect")
                // сюда добавляются ваши массивы пульса и каденса
            }.toString()

            // Отправка готового JSON пакета на сервер Simple Run
            val url = "https://simplerun.ru" // Базовый адрес отправки активностей
            val body = trainingDataJson.toRequestBody("application/json; charset=utf-8".toMediaType())
            
            val request = Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Authorization", "Bearer $apiToken") // Подставляем наш динамический ключ
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    return Result.success()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry() // Если упал интернет, WorkManager попробует позже
        }

        return Result.success()
    }
}
