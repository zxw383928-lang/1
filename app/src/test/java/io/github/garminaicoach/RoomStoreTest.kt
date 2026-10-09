package io.github.garminaicoach

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.garminaicoach.data.local.*
import io.github.garminaicoach.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.time.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class RoomStoreTest {
    private lateinit var database: CoachDatabase
    private lateinit var store: RoomHealthStore
    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CoachDatabase::class.java).allowMainThreadQueries().build()
        store = RoomHealthStore(database)
    }
    @After fun close() { database.close() }
    @Test fun repeatedRefreshAndUpdateDoNotDuplicateRows() = runTest {
        val raw = record()
        val status = SyncStatus(Metric.STEPS, SyncPhase.SUCCESS, succeededAt = raw.end)
        val day = DailyValue(Metric.STEPS, LocalDate.parse("2026-10-09"), 100.0, setOf("test.source"), "UTC", raw.end)
        val snapshot = MetricSnapshot(listOf(raw, raw.copy(value = 200.0, modified = raw.modified.plusSeconds(1))), listOf(day))
        store.replace(Metric.STEPS, snapshot, status)
        store.replace(Metric.STEPS, snapshot, status)
        val saved = store.observe().first()
        assertEquals(1, saved.records.size)
        assertEquals(200.0, saved.records.single().value!!, 0.0)
        assertEquals(setOf("test.source"), saved.days.single().origins)
    }
    @Test fun invalidSnapshotRollsBackWithoutDeletingOldCache() = runTest {
        val raw = record()
        val status = SyncStatus(Metric.STEPS, SyncPhase.SUCCESS)
        store.replace(Metric.STEPS, MetricSnapshot(listOf(raw), emptyList()), status)
        try {
            store.replace(Metric.STEPS, MetricSnapshot(listOf(record(metric = Metric.SLEEP)), emptyList()), status)
            fail("Expected invalid snapshot")
        } catch (_: IllegalArgumentException) { assertEquals(raw, store.observe().first().records.single()) }
    }
    @Test fun revokeAndClearDeleteRawRecordsAndDays() = runTest {
        val raw = record()
        val day = DailyValue(Metric.STEPS, LocalDate.parse("2026-10-09"), null, emptySet(), "UTC", raw.end)
        store.replace(Metric.STEPS, MetricSnapshot(listOf(raw), listOf(day)), SyncStatus(Metric.STEPS, SyncPhase.SUCCESS))
        assertNull(store.observe().first().days.single().value)
        store.remove(Metric.STEPS, SyncStatus(Metric.STEPS, SyncPhase.PERMISSION_REQUIRED))
        assertTrue(store.observe().first().records.isEmpty())
        assertTrue(store.observe().first().days.isEmpty())
        store.clear()
        assertTrue(store.statuses().isEmpty())
    }
}
