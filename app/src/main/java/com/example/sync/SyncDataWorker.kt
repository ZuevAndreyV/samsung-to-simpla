package com.example.sync

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.ExerciseRouteRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.temporal.ChronoUnit

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

            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )

            if (response.records.isEmpty()) return Result.success()

            val lastSession = response.records.last()
            val realDistance = lastSession.distance?.inMeters ?: 0.0
            val realDurationSeconds = ChronoUnit.SECONDS.between(lastSession.startTime, lastSession.endTime).toInt()
            
            val startTimeStr = lastSession.startTime.toString().replace("T", " ").substring(0, 19)
            val finishTimeStr = lastSession.endTime.toString().replace("T", " ").substring(0, 19)

            val hours = realDurationSeconds / 3600
            val minutes = (realDurationSeconds % 3600) / 60
            val seconds = realDurationSeconds % 60
            val formattedTime = String.format("%d:%02d:%02d", hours, minutes, seconds)

            // СБОР ПУЛЬСА
            val heartRatePointsArray = JSONArray()
            val heartRateResponse = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(lastSession.startTime, lastSession.endTime)
                )
            )

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

            // СБОР GPS ТРЕКА (ExerciseRouteRecord)
            val gpsPointsArray = JSONArray()
            var hasTrack = false

            try {
                val routeResponse = healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        recordType = ExerciseRouteRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(lastSession.startTime, lastSession.endTime)
                    )
                )

                val routeRecord = routeResponse.records.firstOrNull()
                if (routeRecord != null && routeRecord.route != null) {
                    hasTrack = true
                    routeRecord.route!!.points.forEach { locationPoint ->
                        val offset = ChronoUnit.SECONDS.between(lastSession.startTime, locationPoint.time).toInt()
                        val gpsPoint = JSONObject().apply {
                            put("time_offset", offset)
                            put("latitude", locationPoint.latitude)
                            put("longitude", locationPoint.longitude)
                            put("altitude", locationPoint.altitude?.inMeters ?: 0.0)
                        }
                        gpsPointsArray.put(gpsPoint)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val cadencePointsArray = JSONArray()

            val typeObject = JSONObject().apply {
                put("code", "walking") 
                put("location", "outdoor")
                put("name", "Ходьба")
            }

            val trainingObject = JSONObject().apply {
                put("description", "Автоматический перенос из Samsung Health Connect")
                put("distance", realDistance)
                put("duration", realDurationSeconds)
                put("in_statistics", true)
                put("isChecked", false)
                put("isCollapsed", true)
                put("is_public", true)
                put("is_track_hidden", false)
                put("is_manual", false)
                put("has_track", hasTrack)
                put("started_at", startTimeStr)
                put("finished_at", finishTimeStr)
                put("timestamp", lastSession.startTime.epochSecond * 1000)
                put("timestamp_finish", lastSession.endTime.epochSecond * 1000)
                put("tracker_training_id", lastSession.metadata.id)
                put("title", "Полуденная ходьба")
                put("training_time", formattedTime)
                put("type", typeObject)
                
                put("debug_data", JSONObject().apply {
                    put("has_track", hasTrack)
                    put("is_manual_upload", false)
                    put("points_count", gpsPointsArray.length())
                })
                
                put("heart_rate_series", heartRatePointsArray)
                put("cadence_series", cadencePointsArray)
                put("route_points", gpsPointsArray)
            }

            val rootJson = JSONObject().apply { put("training", trainingObject) }.toString()
            
            val url = "https://simplerun.ru"
            val body = rootJson.toRequestBody("application/json; charset=utf-8".toMediaType())
            
            val request = Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Authorization", "Bearer $apiToken")
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

