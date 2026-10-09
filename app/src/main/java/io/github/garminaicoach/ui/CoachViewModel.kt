package io.github.garminaicoach.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.garminaicoach.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CoachUiState(
    val availability: Availability? = null,
    val granted: Set<Metric> = emptySet(),
    val snapshot: LocalSnapshot = LocalSnapshot(),
    val busy: Boolean = false,
    val accessVerified: Boolean = false,
    val message: String? = null,
)

class CoachViewModel(private val repository: HealthRepository) : ViewModel() {
    private enum class Operation { CHECK_ACCESS, REFRESH }
    private var pendingOperation: Operation? = null
    private var foreground = false
    private var accessGeneration = 0L
    private val mutable = MutableStateFlow(CoachUiState())
    val state: StateFlow<CoachUiState> = mutable.asStateFlow()
    init {
        viewModelScope.launch {
            try {
                repository.snapshots.collect { snapshot -> mutable.update { it.copy(snapshot = snapshot) } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(message = "本地数据库读取失败，请重启应用后重试") } }
        }
    }
    fun hideData() {
        foreground = false
        invalidateAccess()
    }
    fun checkAccess() {
        foreground = true
        invalidateAccess()
        enqueue(Operation.CHECK_ACCESS)
    }
    fun refresh() = enqueue(Operation.REFRESH)
    private fun invalidateAccess() {
        accessGeneration++
        mutable.update { it.copy(accessVerified = false) }
    }
    private fun enqueue(operation: Operation) {
        // A permission result can arrive before ON_RESUME. Keep it until foreground entry.
        if (pendingOperation != Operation.REFRESH) pendingOperation = operation
        drainPending()
    }
    private fun drainPending() {
        if (!foreground || mutable.value.busy) return
        val operation = pendingOperation ?: return
        pendingOperation = null
        runOperation(operation)
    }
    private fun runOperation(operation: Operation) {
        val generation = accessGeneration
        // Hide health values until permission verification completes on every foreground entry.
        mutable.update { it.copy(busy = true, accessVerified = false, message = null) }
        viewModelScope.launch {
            try {
                val availability = repository.availability()
                val granted = repository.checkAccess()
                mutable.update { it.copy(availability = availability, granted = granted,
                    accessVerified = foreground && generation == accessGeneration) }
                if (operation == Operation.REFRESH && availability == Availability.AVAILABLE && foreground) {
                    repository.refresh()
                    val after = repository.checkAccess()
                    mutable.update { it.copy(granted = after) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutable.update { it.copy(accessVerified = false, message = "无法确认 Health Connect 状态或完成读取。健康数据暂时隐藏，请重试") }
            } finally {
                mutable.update { it.copy(busy = false) }
                drainPending()
            }
        }
    }
    fun clearLocalData() {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                repository.clearLocalData()
                mutable.update { it.copy(message = "本地健康数据已清除；Health Connect 原始数据未删除") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(message = "清除本地数据失败，请重试") } }
            finally {
                mutable.update { it.copy(busy = false) }
                drainPending()
            }
        }
    }
    fun showMessage(message: String) { mutable.update { it.copy(message = message) } }
    companion object {
        fun factory(repository: HealthRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = CoachViewModel(repository) as T
        }
    }
}
