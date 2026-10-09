package io.github.garminaicoach

import androidx.lifecycle.ViewModelStore
import io.github.garminaicoach.domain.*
import io.github.garminaicoach.ui.CoachViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelTest {
    private val models = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun teardown() { models.clear(); Dispatchers.resetMain() }
    private fun model(repository: StubRepository) = CoachViewModel(repository).also { models.put("test", it) }

    @Test fun unknownPermissionStatusHidesExistingCache() = runTest {
        val repo = StubRepository().apply { failAccess = true; data.value = LocalSnapshot(records = listOf(record())) }
        val model = model(repo)
        model.checkAccess()
        advanceUntilIdle()
        assertFalse(model.state.value.accessVerified)
        assertNotNull(model.state.value.message)
        assertFalse(model.state.value.busy)
    }
    @Test fun permissionResultRefreshIsQueuedDuringResumeCheck() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repo = StubRepository().apply { firstCheckGate = gate }
        val model = model(repo)
        model.checkAccess()
        runCurrent()
        assertTrue(model.state.value.busy)
        model.refresh()
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, repo.refreshCount)
        assertTrue(model.state.value.accessVerified)
        assertEquals(setOf(Metric.STEPS), model.state.value.granted)
    }
    @Test fun backgroundEntryHidesDataUntilRechecked() = runTest {
        val model = model(StubRepository())
        model.checkAccess()
        advanceUntilIdle()
        assertTrue(model.state.value.accessVerified)
        model.hideData()
        assertFalse(model.state.value.accessVerified)
    }
}

private class StubRepository : HealthRepository {
    val data = MutableStateFlow(LocalSnapshot())
    override val snapshots = data
    var failAccess = false
    var firstCheckGate: CompletableDeferred<Unit>? = null
    var checks = 0
    var refreshCount = 0
    override fun availability() = Availability.AVAILABLE
    override suspend fun checkAccess(): Set<Metric> {
        if (++checks == 1) firstCheckGate?.await()
        if (failAccess) error("test")
        return setOf(Metric.STEPS)
    }
    override suspend fun refresh() { refreshCount++ }
    override suspend fun clearLocalData() { data.value = LocalSnapshot() }
}
