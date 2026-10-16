package com.example.sync

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.work.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Создаем интерфейс программно без XML-файлов, чтобы робот не запутался
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(50, 50, 50, 50)
        }

        val titleView = TextView(this).apply {
            text = "Samsung ➔ Simpla"
            textSize = 24f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 100)
        }

        val statusView = TextView(this).apply {
            text = "Статус: Приложение готово"
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 50)
        }

        val syncButton = Button(this).apply {
            text = "Включить автосинхронизацию (15 мин)"
            setOnClickListener {
                scheduleFlexibleSync(true)
                statusView.text = "Статус: Фоновый режим АКТИВИРОВАН"
            }
        }

        layout.addView(titleView)
        layout.addView(statusView)
        layout.addView(syncButton)
        setContentView(layout)
    }

    private fun scheduleFlexibleSync(enable: Boolean) {
        val workManager = WorkManager.getInstance(this)
        val uniqueWorkName = "SimplaBackgroundSync"

        if (enable) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<SyncDataWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                uniqueWorkName,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
        } else {
            workManager.cancelUniqueWork(uniqueWorkName)
        }
    }
}
