package com.networktoolbox.feature.report.diagnostic

data class DiagnosticReport(
    val summary: String,
    val findings: List<DiagnosticFinding>,
    val suggestions: List<String>,
)
