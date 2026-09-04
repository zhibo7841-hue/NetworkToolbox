package com.networktoolbox.core.common.diagnostic

data class DiagnosticVerificationContext(
    val previousReportId: String? = null,
    val previousFindingCodes: List<DiagnosticFindingCode> = emptyList(),
    val currentComparisonStatus: DiagnosticComparisonStatus =
        DiagnosticComparisonStatus.NOT_COMPARED,
) {
    init {
        previousReportId?.let { requireBoundedText(it, "previous report id", 128) }
        requireBoundedList(previousFindingCodes.size, "previous findings", 16)
    }
}
