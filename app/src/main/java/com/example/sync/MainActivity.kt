package com.example.sync

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.ExerciseRouteRecord
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private val PERMISSIONS = setOf(
        ExerciseSessionRecord::class,
        HeartRateRecord::class,
        ExerciseRouteRecord::class
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
                        startBackgroundSync()
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
