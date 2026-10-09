package io.github.garminaicoach.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class Metric(val label: String, val unit: String) {
    STEPS("步数", "步"), HEART_RATE("心率", "bpm"), DISTANCE("距离", "km"), SLEEP("睡眠", "h")
}

enum class Availability { AVAILABLE, UPDATE_REQUIRED, UNAVAILABLE }
enum class SyncPhase { IDLE, SYNCING, SUCCESS, ERROR, PERMISSION_REQUIRED, UNAVAILABLE }

data class HealthRecord(
    val key: String,
    val metric: Metric,
    val origin: String,
    val recordId: String,
    val start: Instant,
    val end: Instant,
    val modified: Instant,
    val value: Double?,
    val payload: String,
)

/** Values use steps / bpm / meters / seconds. Null means missing, never an invented zero. */
data class DailyValue(
    val metric: Metric,
    val date: LocalDate,
    val value: Double?,
    val origins: Set<String>,
    val zone: String,
    val fetchedAt: Instant,
)

data class MetricSnapshot(val records: List<HealthRecord>, val days: List<DailyValue>)
data class SyncStatus(
    val metric: Metric,
    val phase: SyncPhase = SyncPhase.IDLE,
    val attemptedAt: Instant? = null,
    val succeededAt: Instant? = null,
    val message: String? = null,
)
data class LocalSnapshot(
    val records: List<HealthRecord> = emptyList(),
    val days: List<DailyValue> = emptyList(),
    val statuses: List<SyncStatus> = emptyList(),
)

data class DayWindow(val date: LocalDate, val start: Instant, val end: Instant)
data class ReadWindow(val days: List<DayWindow>, val zone: ZoneId) {
    val start: Instant get() = days.first().start
    val end: Instant get() = days.last().end
    companion object {
        fun lastSevenDays(now: Instant, zone: ZoneId): ReadWindow {
            val today = now.atZone(zone).toLocalDate()
            val days = (6L downTo 0L).map { offset ->
                val date = today.minusDays(offset)
                val start = date.atStartOfDay(zone).toInstant()
                val end = minOf(date.plusDays(1).atStartOfDay(zone).toInstant(), now)
                DayWindow(date, start, end)
            }
            return ReadWindow(days, zone)
        }
    }
}

fun recordKey(metric: Metric, origin: String, recordId: String): String {
    require(recordId.isNotBlank()) { "Health Connect record has no ID" }
    return "health-connect:${metric.name}:$origin:$recordId"
}

/** Health Connect stable IDs dedupe rereads; different origins remain available for provenance. */
fun deduplicate(records: List<HealthRecord>): List<HealthRecord> = records
    .groupBy { it.key }.values.map { versions -> versions.maxBy { it.modified } }
