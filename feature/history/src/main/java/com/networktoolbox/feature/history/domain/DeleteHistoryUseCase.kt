package com.networktoolbox.feature.history.domain

import com.networktoolbox.core.common.history.HistoryRepository
import javax.inject.Inject

class DeleteHistoryUseCase @Inject constructor(
    private val repository: HistoryRepository,
) {
    suspend operator fun invoke(id: Long) {
        repository.delete(id)
    }
}
