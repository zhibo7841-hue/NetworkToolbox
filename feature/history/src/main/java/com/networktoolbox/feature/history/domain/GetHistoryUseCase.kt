package com.networktoolbox.feature.history.domain

import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryRepository
import javax.inject.Inject

class GetHistoryUseCase @Inject constructor(
    private val repository: HistoryRepository,
) {
    suspend operator fun invoke(): List<HistoryRecord> = repository.getHistory()
}
