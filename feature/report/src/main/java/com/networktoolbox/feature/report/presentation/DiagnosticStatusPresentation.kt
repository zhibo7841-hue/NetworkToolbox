package com.networktoolbox.feature.report.presentation

import com.networktoolbox.core.common.diagnostic.DiagnosticCheckStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticDiagnosisStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticSeverity
import com.networktoolbox.core.designsystem.StatusVisualState
import com.networktoolbox.feature.report.diagnostic.v4.DiagnosticVerificationStatus

/** UI-only mapping from report contracts to the shared semantic status visuals. */
internal data class DiagnosticStatusVisual(
    val state: StatusVisualState,
    val label: String,
)

internal object DiagnosticStatusPresentation {
    fun diagnosis(status: DiagnosticDiagnosisStatus?): DiagnosticStatusVisual = when (status) {
        DiagnosticDiagnosisStatus.NORMAL ->
            DiagnosticStatusVisual(StatusVisualState.NORMAL, "网络状态正常")

        DiagnosticDiagnosisStatus.ATTENTION ->
            DiagnosticStatusVisual(StatusVisualState.NOTICE, "发现需要关注的问题")

        DiagnosticDiagnosisStatus.LIMITED ->
            DiagnosticStatusVisual(StatusVisualState.WARNING, "部分网络能力受限")

        DiagnosticDiagnosisStatus.UNKNOWN,
        null,
        -> DiagnosticStatusVisual(StatusVisualState.UNKNOWN, "状态未确定")
    }

    fun check(
        status: DiagnosticCheckStatus,
        severity: DiagnosticSeverity,
    ): DiagnosticStatusVisual = when (status) {
        DiagnosticCheckStatus.PASS -> if (severity == DiagnosticSeverity.HEALTHY) {
            DiagnosticStatusVisual(StatusVisualState.NORMAL, "正常")
        } else {
            DiagnosticStatusVisual(StatusVisualState.NOTICE, "提示")
        }

        DiagnosticCheckStatus.FAIL -> if (severity == DiagnosticSeverity.ERROR) {
            DiagnosticStatusVisual(StatusVisualState.ERROR, "严重异常")
        } else {
            DiagnosticStatusVisual(StatusVisualState.WARNING, "异常")
        }

        DiagnosticCheckStatus.NO_RECORDS ->
            DiagnosticStatusVisual(StatusVisualState.NOTICE, "无记录")

        DiagnosticCheckStatus.NOT_APPLICABLE ->
            DiagnosticStatusVisual(StatusVisualState.NOT_EXECUTED, "不适用")

        DiagnosticCheckStatus.SKIPPED ->
            DiagnosticStatusVisual(StatusVisualState.NOT_EXECUTED, "未执行")

        DiagnosticCheckStatus.UNKNOWN ->
            DiagnosticStatusVisual(StatusVisualState.UNKNOWN, "未确定")
    }

    fun severity(severity: DiagnosticSeverity): DiagnosticStatusVisual = when (severity) {
        DiagnosticSeverity.HEALTHY ->
            DiagnosticStatusVisual(StatusVisualState.NORMAL, "正常")

        DiagnosticSeverity.NOTICE ->
            DiagnosticStatusVisual(StatusVisualState.NOTICE, "提示")

        DiagnosticSeverity.WARNING ->
            DiagnosticStatusVisual(StatusVisualState.WARNING, "异常")

        DiagnosticSeverity.ERROR ->
            DiagnosticStatusVisual(StatusVisualState.ERROR, "严重异常")
    }

    fun verification(status: DiagnosticVerificationStatus): DiagnosticStatusVisual = when (status) {
        DiagnosticVerificationStatus.RESOLVED_OR_NOT_REPRODUCED ->
            DiagnosticStatusVisual(StatusVisualState.NORMAL, "此前问题未再次出现")

        DiagnosticVerificationStatus.STILL_PRESENT ->
            DiagnosticStatusVisual(StatusVisualState.WARNING, "此前问题仍需关注")

        DiagnosticVerificationStatus.NEW_FINDINGS ->
            DiagnosticStatusVisual(StatusVisualState.WARNING, "发现新的网络问题")

        DiagnosticVerificationStatus.UNCHANGED ->
            DiagnosticStatusVisual(StatusVisualState.NORMAL, "结果基本一致")

        DiagnosticVerificationStatus.INCONCLUSIVE ->
            DiagnosticStatusVisual(StatusVisualState.UNKNOWN, "暂时无法确认")

        DiagnosticVerificationStatus.CONTEXT_CHANGED ->
            DiagnosticStatusVisual(StatusVisualState.NOTICE, "检测环境已变化")
    }
}
