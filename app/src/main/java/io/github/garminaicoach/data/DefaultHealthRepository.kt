package io.github.garminaicoach.data

import io.github.garminaicoach.domain.*
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DefaultHealthRepository(
    private val source: HealthDataSource,
    private val store: HealthStore,
    private val clock: Clock = Clock.systemUTC(),
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : HealthRepository {
    private val mutex = Mutex()
    override val snapshots = store.observe()
    override fun availability() = source.availability()

    override suspend fun checkAccess(): Set<Metric> = mutex.withLock { inspectAccess() }
    private suspend fun inspectAccess(): Set<Metric> {
        val granted = source.grantedMetrics()
        val previous = store.statuses().associateBy { it.metric }
        Metric.entries.filter { it !in granted }.forEach { metric ->
            val phase = if (source.availability() == Availability.AVAILABLE) SyncPhase.PERMISSION_REQUIRED else SyncPhase.UNAVAILABLE
            store.remove(metric, SyncStatus(metric, phase, clock.instant(), null, if (phase == SyncPhase.PERMISSION_REQUIRED) "未授权；此类型的本地缓存已清除" else "Health Connect 不可用"))
        }
        // A process killed during a refresh must not leave a permanent syncing label.
        granted.filter { previous[it]?.phase == SyncPhase.SYNCING }.forEach { metric ->
            store.setStatus(previous.getValue(metric).copy(phase = SyncPhase.ERROR, message = "上次同步中断，请手动刷新"))
        }
        return granted
    }
    override suspend fun refresh() = mutex.withLock {
        val granted = inspectAccess()
        val window = ReadWindow.lastSevenDays(clock.instant(), zone())
        for (metric in Metric.entries.filter { it in granted }) {
            val previous = store.statuses().firstOrNull { it.metric == metric }
            val pending = SyncStatus(metric, SyncPhase.SYNCING, clock.instant(), previous?.succeededAt)
            store.setStatus(pending)
            try {
                val snapshot = source.readMetric(metric, window)
                if (metric !in source.grantedMetrics()) throw SecurityException("Permission revoked during sync")
                store.replace(metric, snapshot, pending.copy(phase = SyncPhase.SUCCESS, succeededAt = clock.instant()))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                store.remove(metric, pending.copy(phase = SyncPhase.PERMISSION_REQUIRED, succeededAt = null, message = "权限已撤销或被系统拒绝；本地缓存已清除"))
            } catch (_: Exception) {
                // Do not put exception strings or health payloads into logs / user-facing errors.
                val stillGranted = try { metric in source.grantedMetrics() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { true }
                if (!stillGranted) {
                    store.remove(metric, pending.copy(phase = SyncPhase.PERMISSION_REQUIRED, succeededAt = null, message = "权限已撤销；本地缓存已清除"))
                } else {
                    store.setStatus(pending.copy(phase = SyncPhase.ERROR, message = "读取失败，请检查 Health Connect 后重试。显示的是上次成功缓存"))
                }
            }
        }
    }
    override suspend fun clearLocalData() = mutex.withLock { store.clear() }
}
