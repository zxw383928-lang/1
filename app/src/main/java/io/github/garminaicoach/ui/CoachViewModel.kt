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
    private var refreshPending = false
    private var accessCheckPending = false
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
    fun hideData() { mutable.update { it.copy(accessVerified = false) } }
    fun checkAccess() {
        hideData()
        runOperation(refresh = false)
    }
    fun refresh() = runOperation(refresh = true)
    private fun runOperation(refresh: Boolean) {
        if (mutable.value.busy) {
            if (refresh) refreshPending = true
            else accessCheckPending = true
            return
        }
        // Hide health values until permission verification completes on every foreground entry.
        mutable.update { it.copy(busy = true, accessVerified = false, message = null) }
        viewModelScope.launch {
            try {
                val availability = repository.availability()
                val granted = repository.checkAccess()
                mutable.update { it.copy(availability = availability, granted = granted, accessVerified = true) }
                if (refresh && availability == Availability.AVAILABLE) {
                    repository.refresh()
                    val after = repository.checkAccess()
                    mutable.update { it.copy(granted = after) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutable.update { it.copy(accessVerified = false, message = "无法确认 Health Connect 状态或完成读取。健康数据暂时隐藏，请重试") }
            } finally {
                mutable.update { it.copy(busy = false) }
                if (refreshPending) {
                    refreshPending = false
                    accessCheckPending = false
                    runOperation(refresh = true)
                } else if (accessCheckPending) {
                    accessCheckPending = false
                    runOperation(refresh = false)
                }
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
            finally { mutable.update { it.copy(busy = false) } }
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
