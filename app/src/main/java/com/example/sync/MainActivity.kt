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

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(50, 50, 50, 50)
        }

        val titleView = TextView(this).apply {
            text = "Samsung ➔ Simple Run"
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
            text = "Включить автосинхронизацию"
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
        val uniqueWorkName = "SimpleRunBackgroundSync"

        if (enable) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val syncRequest = PeriodicWorkRequest.Builder(
                SyncDataWorker::class.java, 
                30, 
                TimeUnit.MINUTES
            )
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
