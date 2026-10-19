import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class AuthService(private val context: Context) {
    private val client = OkHttpClient()
    
    // Настройки встроенной памяти телефона (чтобы сохранять ключ)
    private val prefs = context.getSharedPreferences("SimpleRunPrefs", Context.MODE_PRIVATE)

    // Функция, которая принимает почту и пароль, отправляет на сервер и возвращает true (если всё ок)
    fun login(email: String, javaPassword: String): Boolean {
        // Наш добытый через Charles адрес
        val url = "https://my.simplerun.ru/api/v2/auth"

        // Собираем чемоданчик (JSON) с почтой и паролем
        val jsonPayload = JSONObject().apply {
            put("email", email)
            put("password", javaPassword)
        }.toString()

        // Упаковываем данные для отправки через интернет
        val body = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        return try {
            // Выполняем сетевой запрос
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseData = response.body?.string() ?: ""
                    val jsonResponse = JSONObject(responseData)
                    
                    // Достаем секретный токен из ответа сервера
                    // Примечание: если в Charles имя поля другое (например "access_token"), замените слово "token"
                    val token = jsonResponse.optString("token", "")

                    if (token.isNotEmpty()) {
                        // Надежно записываем токен в память телефона
                        prefs.edit().putString("api_token", token).apply()
                        true // Вход успешный!
                    } else {
                        false
                    }
                } else {
                    false // Сервер вернул ошибку (например, неправильный пароль)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            false // Ошибка интернета
        }
    }

    // Функция, которую будет вызывать ваш фоновый робот (SyncDataWorker), чтобы взять ключ из памяти
    fun getSavedToken(): String? {
        return prefs.getString("api_token", null)
    }
}
