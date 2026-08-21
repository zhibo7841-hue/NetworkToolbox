package com.networktoolbox.feature.history.domain

import com.networktoolbox.core.common.history.HistoryRepository
import javax.inject.Inject

class ClearHistoryUseCase @Inject constructor(
    private val repository: HistoryRepository,
) {
    suspend operator fun invoke() {
        repository.clear()
    }
}
