package com.networktoolbox.feature.history.ui

import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryType

/** Keeps report-action visibility independent from the report payload format. */
internal fun canShowReportAction(
    record: HistoryRecord,
    canOpenReport: (HistoryRecord) -> Boolean,
): Boolean = record.type == HistoryType.REPORT && canOpenReport(record)
