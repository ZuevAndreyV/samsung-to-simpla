import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.LocalTime
import java.time.ZonedDateTime

class SyncDataWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val healthConnectClient = HealthConnectClient.getOrCreate(applicationContext)

        // 1. Высчитываем временной фильтр (за сегодня и вчера)
        val now = ZonedDateTime.now()
        val yesterdayStart = now.minusDays(1).with(LocalTime.MIN)
        val timeFilter = TimeRangeFilter.between(yesterdayStart.toInstant(), now.toInstant())

        try {
            // 2. Читаем список сессий тренировок из Samsung Health
            val exerciseResponse = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = timeFilter
                )
            )

            if (exerciseResponse.records.isEmpty()) {
                // Если новых тренировок за 2 дня нет, засыпаем до следующего будильника
                return Result.success()
            }

            // 3. Перебираем каждую найденную тренировку для глубокого сбора метрик
            for (workout in exerciseResponse.records) {
                
                // Фильтр строго на время проведения конкретно этой тренировки
                val workoutTimeFilter = TimeRangeFilter.between(workout.startTime, workout.endTime)

                // Собираем график пульса за время тренировки
                val heartRateResponse = healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        recordType = HeartRateRecord::class,
                        timeRangeFilter = workoutTimeFilter
                    )
                )

                // Собираем данные о частоте шагов (каденсе) за время тренировки
                val cadenceResponse = healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        recordType = StepsCadenceRecord::class,
                        timeRangeFilter = workoutTimeFilter
                    )
                )

                // 4. Логируем или готовим данные для Simpla
                processCollectedData(workout, heartRateResponse.records, cadenceResponse.records)
            }

            return Result.success()

        } catch (e: Exception) {
            e.printStackTrace()
            // Если произошла сетевая ошибка или сбой системы, Android попробует запустить задачу позже
            return Result.retry()
        }
    }

    // Временная функция-заглушка. Сюда мы вставим отправку на my.simplerun.ru
    private fun processCollectedData(
        workout: ExerciseSessionRecord,
        heartRates: List<HeartRateRecord>,
        cadences: List<StepsCadenceRecord>
    ) {
        val totalPoints = heartRates.sumOf { it.samples.size }
        println("Сбор данных успешен: Тренировка ${workout.title}, Найдено точек пульса: $totalPoints")
        
        // ТОЧКА ИНТЕГРАЦИИ: Тут мы будем превращать данные в JSON и слать в Simpla
    }
}
