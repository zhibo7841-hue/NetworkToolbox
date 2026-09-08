package com.networktoolbox.feature.dashboard.domain

import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryRepository
import com.networktoolbox.core.common.history.HistoryType
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Observes only the latest persisted automatic-diagnostic record for Home.
 *
 * The repository remains the single source of truth. The timestamp/id
 * ordering is repeated here deliberately so the Home definition of "latest"
 * is based on stored data rather than render time or list order.
 */
class ObserveRecentDiagnosisUseCase @Inject constructor(
    private val repository: HistoryRepository,
) {
    operator fun invoke(): Flow<HistoryRecord?> = repository.observeHistory().map { records ->
        records.asSequence()
            .filter { it.type == HistoryType.REPORT }
            .maxWithOrNull(compareBy<HistoryRecord> { it.timestamp }.thenBy { it.id })
    }
}
