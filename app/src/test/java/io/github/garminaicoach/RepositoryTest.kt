package io.github.garminaicoach

import io.github.garminaicoach.data.DefaultHealthRepository
import io.github.garminaicoach.domain.*
import java.time.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RepositoryTest {
    private val now = Instant.parse("2026-10-09T10:00:00Z")
    private fun repository(source: FakeSource, store: MemoryStore) = DefaultHealthRepository(source, store, Clock.fixed(now, ZoneOffset.UTC)) { ZoneOffset.UTC }

    @Test fun partialGrantReadsOnlyGrantedType() = runTest {
        val source = FakeSource(setOf(Metric.STEPS))
        val store = MemoryStore()
        repository(source, store).refresh()
        assertEquals(listOf(Metric.STEPS), source.reads)
        assertEquals(SyncPhase.SUCCESS, store.statuses().first { it.metric == Metric.STEPS }.phase)
        assertEquals(SyncPhase.PERMISSION_REQUIRED, store.statuses().first { it.metric == Metric.SLEEP }.phase)
    }
    @Test fun revocationPurgesOnlyThatTypeAndItsAggregates() = runTest {
        val source = FakeSource(setOf(Metric.STEPS, Metric.SLEEP))
        val store = MemoryStore()
        val repo = repository(source, store)
        repo.refresh()
        source.granted = setOf(Metric.STEPS)
        repo.checkAccess()
        assertTrue(store.flow.value.records.all { it.metric == Metric.STEPS })
        assertTrue(store.flow.value.days.all { it.metric == Metric.STEPS })
        assertNull(store.statuses().first { it.metric == Metric.SLEEP }.succeededAt)
    }
    @Test fun readFailureKeepsCacheAndDoesNotStopOtherTypes() = runTest {
        val source = FakeSource(setOf(Metric.STEPS, Metric.SLEEP))
        val store = MemoryStore()
        val repo = repository(source, store)
        repo.refresh()
        val saved = store.flow.value.records.first { it.metric == Metric.STEPS }
        source.fail = Metric.STEPS
        repo.refresh()
        assertEquals(saved, store.flow.value.records.first { it.metric == Metric.STEPS })
        assertEquals(SyncPhase.ERROR, store.statuses().first { it.metric == Metric.STEPS }.phase)
        assertEquals(now, store.statuses().first { it.metric == Metric.STEPS }.succeededAt)
        assertEquals(SyncPhase.SUCCESS, store.statuses().first { it.metric == Metric.SLEEP }.phase)
    }
    @Test fun revokeDuringReadNeverCommitsResult() = runTest {
        val source = FakeSource(setOf(Metric.STEPS))
        val store = MemoryStore()
        source.revokeDuringRead = true
        repository(source, store).refresh()
        assertTrue(store.flow.value.records.isEmpty())
        assertTrue(store.flow.value.days.isEmpty())
        assertEquals(SyncPhase.PERMISSION_REQUIRED, store.statuses().first { it.metric == Metric.STEPS }.phase)
    }
    @Test fun successfulEmptyReadReplacesDeletedRecordsWithMissingValues() = runTest {
        val source = FakeSource(setOf(Metric.STEPS))
        val store = MemoryStore()
        val repo = repository(source, store)
        repo.refresh()
        source.empty = true
        repo.refresh()
        assertTrue(store.flow.value.records.isEmpty())
        assertEquals(7, store.flow.value.days.size)
        assertTrue(store.flow.value.days.all { it.value == null })
    }
    @Test fun cancellationPropagates() = runTest {
        val source = FakeSource(setOf(Metric.STEPS))
        val store = MemoryStore()
        source.cancel = true
        try { repository(source, store).refresh(); fail("Cancellation swallowed") }
        catch (_: CancellationException) { assertTrue(store.flow.value.records.isEmpty()) }
    }
    @Test fun unavailableNeverReads() = runTest {
        val source = FakeSource(emptySet()).apply { available = Availability.UNAVAILABLE }
        val store = MemoryStore()
        repository(source, store).refresh()
        assertTrue(source.reads.isEmpty())
        assertTrue(store.statuses().all { it.phase == SyncPhase.UNAVAILABLE })
    }
    @Test fun interruptedSyncCanBeRetried() = runTest {
        val source = FakeSource(setOf(Metric.STEPS))
        val store = MemoryStore()
        store.setStatus(SyncStatus(Metric.STEPS, SyncPhase.SYNCING))
        repository(source, store).checkAccess()
        assertEquals(SyncPhase.ERROR, store.statuses().first { it.metric == Metric.STEPS }.phase)
    }
}

internal class FakeSource(var granted: Set<Metric>) : HealthDataSource {
    var available = Availability.AVAILABLE
    var fail: Metric? = null
    var empty = false
    var cancel = false
    var revokeDuringRead = false
    val reads = mutableListOf<Metric>()
    override fun availability() = available
    override suspend fun grantedMetrics() = granted
    override suspend fun readMetric(metric: Metric, window: ReadWindow): MetricSnapshot {
        reads += metric
        if (cancel) throw CancellationException("test")
        if (fail == metric) error("test")
        if (revokeDuringRead) granted = emptySet()
        return MetricSnapshot(if (empty) emptyList() else listOf(record(metric = metric)), window.days.map {
            DailyValue(metric, it.date, if (empty) null else 100.0, setOf("test.source"), window.zone.id, window.end)
        })
    }
}

internal class MemoryStore : HealthStore {
    val flow = MutableStateFlow(LocalSnapshot())
    override fun observe() = flow
    override suspend fun statuses() = flow.value.statuses
    override suspend fun replace(metric: Metric, snapshot: MetricSnapshot, status: SyncStatus) {
        flow.value = flow.value.copy(records = flow.value.records.filter { it.metric != metric } + deduplicate(snapshot.records), days = flow.value.days.filter { it.metric != metric } + snapshot.days)
        setStatus(status)
    }
    override suspend fun setStatus(status: SyncStatus) { flow.value = flow.value.copy(statuses = flow.value.statuses.filter { it.metric != status.metric } + status) }
    override suspend fun remove(metric: Metric, status: SyncStatus) { replace(metric, MetricSnapshot(emptyList(), emptyList()), status) }
    override suspend fun clear() { flow.value = LocalSnapshot() }
}
