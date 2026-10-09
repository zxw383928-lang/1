package io.github.garminaicoach.domain

import java.time.Instant
import kotlinx.coroutines.flow.Flow

interface HealthDataSource {
    fun availability(): Availability
    suspend fun grantedMetrics(): Set<Metric>
    suspend fun readMetric(metric: Metric, window: ReadWindow): MetricSnapshot
}

interface HealthStore {
    fun observe(): Flow<LocalSnapshot>
    suspend fun statuses(): List<SyncStatus>
    /** Replace one type atomically only after every raw page and aggregate was read. */
    suspend fun replace(metric: Metric, snapshot: MetricSnapshot, status: SyncStatus)
    suspend fun setStatus(status: SyncStatus)
    suspend fun remove(metric: Metric, status: SyncStatus)
    suspend fun clear()
}

interface HealthRepository {
    val snapshots: Flow<LocalSnapshot>
    fun availability(): Availability
    suspend fun checkAccess(): Set<Metric>
    suspend fun refresh()
    suspend fun clearLocalData()
}

/** Phase 2: user-selected file only; implement parsing and fingerprint dedupe separately. */
interface FitImportRepository {
    suspend fun preview(contentUri: String): FitImportPreview
    suspend fun importApproved(previewId: String): FitImportResult
}
data class FitImportPreview(val id: String, val sha256: String, val recordCount: Int, val warnings: List<String>)
data class FitImportResult(val inserted: Int, val duplicateCount: Int)

/** Phase 2 contract only. V0.1 has no implementation, API key or network permission. */
interface CoachAiRepository {
    suspend fun prepareSummary(request: AiSummaryRequest): AiSummaryDraft
    suspend fun analyzeApproved(draft: AiSummaryDraft, consent: UploadConsent): CoachExplanation
}
data class AiSummaryRequest(val rangeStart: Instant, val rangeEnd: Instant, val metrics: Set<Metric>, val zoneId: String)
data class AiSummaryDraft(val id: String, val schemaVersion: Int, val json: String, val sha256: String, val providerId: String)
data class UploadConsent(val draftId: String, val payloadSha256: String, val providerId: String, val grantedAt: Instant, val expiresAt: Instant)
data class CoachExplanation(val observations: List<String>, val limitations: List<String>, val model: String)
