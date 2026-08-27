package com.networktoolbox.feature.lanscan.domain

import com.networktoolbox.feature.lanscan.domain.model.LanScanRange
import com.networktoolbox.feature.lanscan.domain.model.LanScanRejectionReason

sealed interface LanScanRangeResult {
    data class Ready(val range: LanScanRange) : LanScanRangeResult

    data class Rejected(
        val reason: LanScanRejectionReason,
        val message: String,
    ) : LanScanRangeResult
}
