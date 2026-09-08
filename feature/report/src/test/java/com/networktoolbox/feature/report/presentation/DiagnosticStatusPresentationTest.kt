package com.networktoolbox.feature.report.presentation

import com.networktoolbox.core.common.diagnostic.DiagnosticCheckStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticDiagnosisStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticSeverity
import com.networktoolbox.core.designsystem.StatusVisualState
import com.networktoolbox.feature.report.diagnostic.v4.DiagnosticVerificationStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticStatusPresentationTest {
    @Test
    fun diagnosisStatusesUseSharedUserFacingSemantics() {
        assertEquals(StatusVisualState.NORMAL, DiagnosticStatusPresentation.diagnosis(DiagnosticDiagnosisStatus.NORMAL).state)
        assertEquals(StatusVisualState.NOTICE, DiagnosticStatusPresentation.diagnosis(DiagnosticDiagnosisStatus.ATTENTION).state)
        assertEquals(StatusVisualState.WARNING, DiagnosticStatusPresentation.diagnosis(DiagnosticDiagnosisStatus.LIMITED).state)
        assertEquals(StatusVisualState.UNKNOWN, DiagnosticStatusPresentation.diagnosis(DiagnosticDiagnosisStatus.UNKNOWN).state)
        assertEquals("状态未确定", DiagnosticStatusPresentation.diagnosis(null).label)
    }

    @Test
    fun checkAndFindingStatusesDoNotExposeMachineValues() {
        assertEquals(
            "正常",
            DiagnosticStatusPresentation.check(
                DiagnosticCheckStatus.PASS,
                DiagnosticSeverity.HEALTHY,
            ).label,
        )
        assertEquals(
            StatusVisualState.ERROR,
            DiagnosticStatusPresentation.check(
                DiagnosticCheckStatus.FAIL,
                DiagnosticSeverity.ERROR,
            ).state,
        )
        assertEquals(
            "不适用",
            DiagnosticStatusPresentation.check(
                DiagnosticCheckStatus.NOT_APPLICABLE,
                DiagnosticSeverity.NOTICE,
            ).label,
        )
        assertEquals("严重异常", DiagnosticStatusPresentation.severity(DiagnosticSeverity.ERROR).label)
    }

    @Test
    fun verificationStatusesRemainCompactAndSemantic() {
        assertEquals(
            StatusVisualState.NORMAL,
            DiagnosticStatusPresentation.verification(
                DiagnosticVerificationStatus.RESOLVED_OR_NOT_REPRODUCED,
            ).state,
        )
        assertEquals(
            StatusVisualState.WARNING,
            DiagnosticStatusPresentation.verification(
                DiagnosticVerificationStatus.NEW_FINDINGS,
            ).state,
        )
        assertEquals(
            StatusVisualState.NOTICE,
            DiagnosticStatusPresentation.verification(
                DiagnosticVerificationStatus.CONTEXT_CHANGED,
            ).state,
        )
    }
}
