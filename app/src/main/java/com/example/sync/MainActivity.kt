package com.example.sync

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.work.*
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

// ==========================================
// 1. ГЛАВНЫЙ ЭКРАН ПРИЛОЖЕНИЯ
// ==========================================
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

// ==========================================
// 2. ФОНОВЫЙ РОБОТ СИНХРОНИЗАЦИИ
// ==========================================
class SyncDataWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val healthConnectClient = HealthConnectClient.getOrCreate(applicationContext)

        val now = ZonedDateTime.now()
        val yesterdayStart = now.minusDays(1).with(LocalTime.MIN)
        val timeFilter = TimeRangeFilter.between(yesterdayStart.toInstant(), now.toInstant())

        try {
            val exerciseResponse = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = timeFilter
                )
            )

            if (exerciseResponse.records.isEmpty()) {
                return Result.success()
            }

            for (workout in exerciseResponse.records) {
                val workoutTimeFilter = TimeRangeFilter.between(workout.startTime, workout.endTime)

                val heartRateResponse = healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        recordType = HeartRateRecord::class,
                        timeRangeFilter = workoutTimeFilter
                    )
                )

                val cadenceResponse = healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        recordType = StepsCadenceRecord::class,
                        timeRangeFilter = workoutTimeFilter
                    )
                )

                processCollectedData(workout, heartRateResponse.records, cadenceResponse.records)
            }

            return Result.success()

        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        }
    }

    private fun processCollectedData(
        workout: ExerciseSessionRecord,
        heartRates: List<HeartRateRecord>,
        cadences: List<StepsCadenceRecord>
    ) {
        val totalPoints = heartRates.sumOf { it.samples.size }
        println("Сбор данных успешен: Тренировка ${workout.title}, Найдено точек пульса: $totalPoints")
    }
}
