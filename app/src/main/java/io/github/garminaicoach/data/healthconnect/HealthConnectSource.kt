package io.github.garminaicoach.data.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import io.github.garminaicoach.domain.*
import java.time.Instant
import java.time.Duration
import kotlin.reflect.KClass
import org.json.JSONArray
import org.json.JSONObject

fun Metric.readPermission(): String = when (this) {
    Metric.STEPS -> HealthPermission.getReadPermission(StepsRecord::class)
    Metric.HEART_RATE -> HealthPermission.getReadPermission(HeartRateRecord::class)
    Metric.DISTANCE -> HealthPermission.getReadPermission(DistanceRecord::class)
    Metric.SLEEP -> HealthPermission.getReadPermission(SleepSessionRecord::class)
}

class HealthConnectSource(
    context: Context,
    private val clientFactory: (Context) -> HealthConnectClient = { HealthConnectClient.getOrCreate(it) },
    private val sdkStatus: (Context) -> Int = { HealthConnectClient.getSdkStatus(it) },
) : HealthDataSource {
    private val appContext = context.applicationContext
    private val client: HealthConnectClient by lazy { clientFactory(appContext) }
    override fun availability(): Availability = when (sdkStatus(appContext)) {
        HealthConnectClient.SDK_AVAILABLE -> Availability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Availability.UPDATE_REQUIRED
        else -> Availability.UNAVAILABLE
    }
    override suspend fun grantedMetrics(): Set<Metric> {
        if (availability() != Availability.AVAILABLE) return emptySet()
        val permissions = client.permissionController.getGrantedPermissions()
        return Metric.entries.filter { it.readPermission() in permissions }.toSet()
    }
    private suspend fun requirePermission(metric: Metric) {
        if (metric !in grantedMetrics()) throw SecurityException("Read permission revoked")
    }
    override suspend fun readMetric(metric: Metric, window: ReadWindow): MetricSnapshot {
        requirePermission(metric)
        val records = if (window.end <= window.start) emptyList() else when (metric) {
            Metric.STEPS -> readAll(StepsRecord::class, metric, window).map { r ->
                normalized(metric, r.metadata, r.startTime, r.endTime, r.count.toDouble(), intervalPayload(r.startZoneOffset?.toString(), r.endZoneOffset?.toString()))
            }
            Metric.DISTANCE -> readAll(DistanceRecord::class, metric, window).map { r ->
                normalized(metric, r.metadata, r.startTime, r.endTime, r.distance.inMeters, intervalPayload(r.startZoneOffset?.toString(), r.endZoneOffset?.toString()))
            }
            Metric.HEART_RATE -> readAll(HeartRateRecord::class, metric, window).map { r ->
                val samples = JSONArray()
                r.samples.forEach { sample -> samples.put(JSONObject().put("time", sample.time.toString()).put("bpm", sample.beatsPerMinute)) }
                val payload = intervalPayload(r.startZoneOffset?.toString(), r.endZoneOffset?.toString()).put("samples", samples)
                normalized(metric, r.metadata, r.startTime, r.endTime, r.samples.takeIf { it.isNotEmpty() }?.map { it.beatsPerMinute }?.average(), payload)
            }
            Metric.SLEEP -> readAll(SleepSessionRecord::class, metric, window).map { r ->
                val stages = JSONArray()
                r.stages.forEach { stage -> stages.put(JSONObject().put("start", stage.startTime.toString()).put("end", stage.endTime.toString()).put("stage", stage.stage)) }
                val payload = intervalPayload(r.startZoneOffset?.toString(), r.endZoneOffset?.toString())
                    .put("stages", stages).put("title", r.title ?: JSONObject.NULL).put("notes", r.notes ?: JSONObject.NULL)
                normalized(metric, r.metadata, r.startTime, r.endTime, Duration.between(r.startTime, r.endTime).seconds.toDouble(), payload)
            }
        }
        val days = window.days.map { day ->
            requirePermission(metric)
            if (day.end <= day.start) {
                DailyValue(metric, day.date, null, emptySet(), window.zone.id, Instant.now())
            } else {
                val metrics = when (metric) {
                    Metric.STEPS -> setOf(StepsRecord.COUNT_TOTAL)
                    Metric.HEART_RATE -> setOf(HeartRateRecord.BPM_AVG)
                    Metric.DISTANCE -> setOf(DistanceRecord.DISTANCE_TOTAL)
                    Metric.SLEEP -> setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL)
                }
                val result = client.aggregate(AggregateRequest(metrics, TimeRangeFilter.between(day.start, day.end)))
                val value = when (metric) {
                    Metric.STEPS -> result[StepsRecord.COUNT_TOTAL]?.toDouble()
                    Metric.HEART_RATE -> result[HeartRateRecord.BPM_AVG]?.toDouble()
                    Metric.DISTANCE -> result[DistanceRecord.DISTANCE_TOTAL]?.inMeters
                    Metric.SLEEP -> result[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.toMillis()?.div(1000.0)
                }
                DailyValue(metric, day.date, value, result.dataOrigins.map { it.packageName }.toSet(), window.zone.id, Instant.now())
            }
        }
        requirePermission(metric)
        return MetricSnapshot(deduplicate(records), days)
    }
    private suspend fun <T : Record> readAll(type: KClass<T>, metric: Metric, window: ReadWindow): List<T> {
        val records = mutableListOf<T>()
        var token: String? = null
        val tokens = mutableSetOf<String>()
        do {
            requirePermission(metric)
            val response = client.readRecords(ReadRecordsRequest(type, TimeRangeFilter.between(window.start, window.end), pageSize = 1000, pageToken = token))
            records.addAll(response.records)
            token = response.pageToken
            if (token != null && !tokens.add(token)) error("Repeated Health Connect page token")
        } while (token != null)
        return records
    }
    private fun intervalPayload(startOffset: String?, endOffset: String?) = JSONObject()
        .put("startZoneOffset", startOffset ?: JSONObject.NULL)
        .put("endZoneOffset", endOffset ?: JSONObject.NULL)

    private fun normalized(metric: Metric, metadata: Metadata, start: Instant, end: Instant, value: Double?, payload: JSONObject): HealthRecord {
        payload.put("clientRecordId", metadata.clientRecordId ?: JSONObject.NULL)
            .put("clientRecordVersion", metadata.clientRecordVersion).put("recordingMethod", metadata.recordingMethod)
        return HealthRecord(recordKey(metric, metadata.dataOrigin.packageName, metadata.id), metric, metadata.dataOrigin.packageName, metadata.id, start, end, metadata.lastModifiedTime, value, payload.toString())
    }
}
