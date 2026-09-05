package com.networktoolbox.feature.report.diagnostic.v2

import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.feature.report.domain.AutomaticDiagnosticResult

/**
 * Resolves the report formats that the app can restore without running a new
 * diagnostic. Keeping this decision outside the History composable prevents
 * payload-version checks from drifting between the action and navigation
 * paths.
 */
sealed interface ResolvedDiagnosticHistory {
    data class Automatic(val result: AutomaticDiagnosticResult) : ResolvedDiagnosticHistory

    data class Legacy(val report: DiagnosticReportV2) : ResolvedDiagnosticHistory
}

object DiagnosticHistoryReportResolver {
    /**
     * Reads the newest supported snapshot first, then falls back to the
     * schema-2 report reader. Malformed and unsupported records return null.
     */
    fun resolve(record: HistoryRecord): ResolvedDiagnosticHistory? =
        AutomaticDiagnosticHistorySnapshotDeserializer.fromHistoryRecord(record)
            ?.let { result -> ResolvedDiagnosticHistory.Automatic(result) }
            ?: DiagnosticReportV2HistoryDeserializer.fromHistoryRecord(record)
                ?.let { report -> ResolvedDiagnosticHistory.Legacy(report) }

    fun canOpen(record: HistoryRecord): Boolean = resolve(record) != null
}
