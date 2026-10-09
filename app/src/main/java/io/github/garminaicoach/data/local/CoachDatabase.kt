package io.github.garminaicoach.data.local

import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import io.github.garminaicoach.domain.*
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

@Entity(tableName = "health_records")
data class RecordEntity(
    @PrimaryKey val key: String,
    val metric: String,
    val origin: String,
    val recordId: String,
    val startMillis: Long,
    val endMillis: Long,
    val modifiedMillis: Long,
    val value: Double?,
    val payload: String,
)

@Entity(tableName = "daily_values", primaryKeys = ["metric", "date"])
data class DailyEntity(
    val metric: String,
    val date: String,
    val value: Double?,
    val originsJson: String,
    val zone: String,
    val fetchedMillis: Long,
)

@Entity(tableName = "sync_status")
data class StatusEntity(
    @PrimaryKey val metric: String,
    val phase: String,
    val attemptedMillis: Long?,
    val succeededMillis: Long?,
    val message: String?,
)

@Dao
interface HealthDao {
    @Query("SELECT * FROM health_records ORDER BY endMillis DESC")
    suspend fun records(): List<RecordEntity>
    @Query("SELECT * FROM daily_values ORDER BY date")
    suspend fun days(): List<DailyEntity>
    @Query("SELECT * FROM sync_status")
    suspend fun statuses(): List<StatusEntity>
    @Query("DELETE FROM health_records WHERE metric = :metric")
    suspend fun deleteRecords(metric: String)
    @Query("DELETE FROM daily_values WHERE metric = :metric")
    suspend fun deleteDays(metric: String)
    @Query("DELETE FROM sync_status")
    suspend fun deleteStatuses()
    @Query("DELETE FROM health_records")
    suspend fun deleteAllRecords()
    @Query("DELETE FROM daily_values")
    suspend fun deleteAllDays()
    @Upsert suspend fun upsertRecords(records: List<RecordEntity>)
    @Upsert suspend fun upsertDays(days: List<DailyEntity>)
    @Upsert suspend fun upsertStatus(status: StatusEntity)
}

@Database(entities = [RecordEntity::class, DailyEntity::class, StatusEntity::class], version = 1, exportSchema = true)
abstract class CoachDatabase : RoomDatabase() { abstract fun healthDao(): HealthDao }

class RoomHealthStore(private val database: CoachDatabase) : HealthStore {
    private val dao = database.healthDao()
    override fun observe(): Flow<LocalSnapshot> = database.invalidationTracker.createFlow(
        "health_records", "daily_values", "sync_status"
    ).map {
        // Combining independent table flows can expose different committed versions.
        // One read transaction keeps records, summaries and their status consistent.
        database.withTransaction {
            LocalSnapshot(
                dao.records().map { it.toModel() },
                dao.days().map { it.toModel() },
                dao.statuses().map { it.toModel() },
            )
        }
    }
    override suspend fun statuses() = dao.statuses().map { it.toModel() }
    override suspend fun replace(metric: Metric, snapshot: MetricSnapshot, status: SyncStatus) = database.withTransaction {
        require(snapshot.records.all { it.metric == metric } && snapshot.days.all { it.metric == metric })
        dao.deleteRecords(metric.name)
        dao.deleteDays(metric.name)
        dao.upsertRecords(deduplicate(snapshot.records).map { it.toEntity() })
        dao.upsertDays(snapshot.days.map { it.toEntity() })
        dao.upsertStatus(status.toEntity())
    }
    override suspend fun setStatus(status: SyncStatus) = dao.upsertStatus(status.toEntity())
    override suspend fun remove(metric: Metric, status: SyncStatus) = database.withTransaction {
        dao.deleteRecords(metric.name)
        dao.deleteDays(metric.name)
        dao.upsertStatus(status.toEntity())
    }
    override suspend fun clear() = database.withTransaction {
        dao.deleteAllRecords(); dao.deleteAllDays(); dao.deleteStatuses()
    }
}

private fun HealthRecord.toEntity() = RecordEntity(key, metric.name, origin, recordId, start.toEpochMilli(), end.toEpochMilli(), modified.toEpochMilli(), value, payload)
private fun RecordEntity.toModel() = HealthRecord(key, Metric.valueOf(metric), origin, recordId, Instant.ofEpochMilli(startMillis), Instant.ofEpochMilli(endMillis), Instant.ofEpochMilli(modifiedMillis), value, payload)
private fun DailyValue.toEntity() = DailyEntity(metric.name, date.toString(), value, JSONArray(origins.sorted()).toString(), zone, fetchedAt.toEpochMilli())
private fun DailyEntity.toModel(): DailyValue {
    val json = JSONArray(originsJson)
    return DailyValue(Metric.valueOf(metric), LocalDate.parse(date), value, (0 until json.length()).map { json.getString(it) }.toSet(), zone, Instant.ofEpochMilli(fetchedMillis))
}
private fun SyncStatus.toEntity() = StatusEntity(metric.name, phase.name, attemptedAt?.toEpochMilli(), succeededAt?.toEpochMilli(), message)
private fun StatusEntity.toModel() = SyncStatus(Metric.valueOf(metric), SyncPhase.valueOf(phase), attemptedMillis?.let(Instant::ofEpochMilli), succeededMillis?.let(Instant::ofEpochMilli), message)
