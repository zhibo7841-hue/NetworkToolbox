package com.networktoolbox.feature.report.diagnostic

data class DiagnosticFinding(
    val level: FindingLevel,
    val title: String,
    val description: String,
)
