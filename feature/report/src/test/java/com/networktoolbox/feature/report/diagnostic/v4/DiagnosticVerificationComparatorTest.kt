package com.networktoolbox.feature.report.diagnostic.v4

import com.networktoolbox.core.common.diagnostic.DiagnosticCheck
import com.networktoolbox.core.common.diagnostic.DiagnosticCheckCode
import com.networktoolbox.core.common.diagnostic.DiagnosticCheckStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticConfidence
import com.networktoolbox.core.common.diagnostic.DiagnosticDiagnosis
import com.networktoolbox.core.common.diagnostic.DiagnosticEvidenceLevel
import com.networktoolbox.core.common.diagnostic.DiagnosticFinding
import com.networktoolbox.core.common.diagnostic.DiagnosticFindingCode
import com.networktoolbox.core.common.diagnostic.DiagnosticIntent
import com.networktoolbox.core.common.diagnostic.DiagnosticRunStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticSeverity
import com.networktoolbox.core.common.diagnostic.DiagnosticStage
import com.networktoolbox.core.common.diagnostic.DiagnosticTarget
import com.networktoolbox.core.common.diagnostic.DiagnosticTargetKind
import com.networktoolbox.core.common.diagnostic.NetworkFingerprint
import com.networktoolbox.feature.report.domain.AutomaticDiagnosticResult
import com.networktoolbox.feature.report.diagnostic.v2.orchestration.DiagnosticRunEvidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticVerificationComparatorTest {
    @Test
    fun normalToNormalIsUnchanged() {
        val result = compare(
            previous = report(),
            current = report(),
        )

        assertEquals(DiagnosticVerificationStatus.UNCHANGED, result.status)
        assertEquals("基础网络连接状态与上次基本一致。", result.summary)
        assertTrue(result.resolvedFindingCodes.isEmpty())
        assertTrue(result.stillPresentFindingCodes.isEmpty())
        assertTrue(result.newFindingCodes.isEmpty())
    }

    @Test
    fun dnsFailureNoLongerObservedIsNotReproduced() {
        val result = compare(
            previous = report(listOf(DiagnosticFindingCode.DNS_RESOLUTION_FAILURE)),
            current = report(),
        )

        assertEquals(
            DiagnosticVerificationStatus.RESOLVED_OR_NOT_REPRODUCED,
            result.status,
        )
        assertEquals(
            listOf(DiagnosticFindingCode.DNS_RESOLUTION_FAILURE),
            result.resolvedFindingCodes,
        )
    }

    @Test
    fun sameDnsFailureIsStillPresent() {
        val result = compare(
            previous = report(listOf(DiagnosticFindingCode.DNS_RESOLUTION_FAILURE)),
            current = report(listOf(DiagnosticFindingCode.DNS_RESOLUTION_FAILURE)),
        )

        assertEquals(DiagnosticVerificationStatus.STILL_PRESENT, result.status)
        assertEquals(
            listOf(DiagnosticFindingCode.DNS_RESOLUTION_FAILURE),
            result.stillPresentFindingCodes,
        )
    }

    @Test
    fun nxdomainIsComparedAsARealFindingDespiteNoticeSeverity() {
        val result = compare(
            previous = report(listOf(DiagnosticFindingCode.DNS_NXDOMAIN)),
            current = report(),
        )

        assertEquals(
            DiagnosticVerificationStatus.RESOLVED_OR_NOT_REPRODUCED,
            result.status,
        )
        assertEquals(
            listOf(DiagnosticFindingCode.DNS_NXDOMAIN),
            result.resolvedFindingCodes,
        )
    }

    @Test
    fun materialPublicUncertaintyIsComparedByStableCode() {
        val result = compare(
            previous = report(listOf(DiagnosticFindingCode.PUBLIC_CONNECTIVITY_UNCONFIRMED)),
            current = report(),
        )

        assertEquals(
            DiagnosticVerificationStatus.RESOLVED_OR_NOT_REPRODUCED,
            result.status,
        )
        assertEquals(
            listOf(DiagnosticFindingCode.PUBLIC_CONNECTIVITY_UNCONFIRMED),
            result.resolvedFindingCodes,
        )
    }

    @Test
    fun currentStageUnknownDoesNotClaimThatAnExistingFindingPersists() {
        val result = compare(
            previous = report(listOf(DiagnosticFindingCode.DNS_RESOLUTION_FAILURE)),
            current = report(
                findings = listOf(DiagnosticFindingCode.DNS_RESOLUTION_FAILURE),
                statuses = mapOf(DiagnosticStage.DNS to DiagnosticCheckStatus.UNKNOWN),
            ),
        )

        assertEquals(DiagnosticVerificationStatus.INCONCLUSIVE, result.status)
        assertTrue(result.stillPresentFindingCodes.isEmpty())
        assertEquals(
            listOf(DiagnosticFindingCode.DNS_RESOLUTION_FAILURE),
            result.inconclusiveFindingCodes,
        )
    }

    @Test
    fun resolvedAndNewProblemsAreBothPreservedInTheComparison() {
        val result = compare(
            previous = report(listOf(DiagnosticFindingCode.DNS_RESOLUTION_FAILURE)),
            current = report(listOf(DiagnosticFindingCode.NO_ACTIVE_NETWORK)),
        )

        assertEquals(DiagnosticVerificationStatus.NEW_FINDINGS, result.status)
        assertEquals(
            listOf(DiagnosticFindingCode.DNS_RESOLUTION_FAILURE),
            result.resolvedFindingCodes,
        )
        assertEquals(
            listOf(DiagnosticFindingCode.NO_ACTIVE_NETWORK),
            result.newFindingCodes,
        )
    }

    @Test
    fun newDnsFailureIsReportedAsNewFinding() {
        val result = compare(
            previous = report(),
            current = report(listOf(DiagnosticFindingCode.DNS_RESOLUTION_FAILURE)),
        )

        assertEquals(DiagnosticVerificationStatus.NEW_FINDINGS, result.status)
        assertEquals(
            listOf(DiagnosticFindingCode.DNS_RESOLUTION_FAILURE),
            result.newFindingCodes,
        )
    }

    @Test
    fun missingDnsStageDoesNotClaimResolution() {
        val result = compare(
            previous = report(listOf(DiagnosticFindingCode.DNS_RESOLUTION_FAILURE)),
            current = report(
                statuses = mapOf(DiagnosticStage.DNS to DiagnosticCheckStatus.SKIPPED),
            ),
        )

        assertEquals(DiagnosticVerificationStatus.INCONCLUSIVE, result.status)
        assertTrue(result.resolvedFindingCodes.isEmpty())
        assertEquals(
            listOf(DiagnosticFindingCode.DNS_RESOLUTION_FAILURE),
            result.inconclusiveFindingCodes,
        )
    }

    @Test
    fun gatewayNoticeIsContextNotAProblemThatPersists() {
        val result = compare(
            previous = report(listOf(DiagnosticFindingCode.GATEWAY_PROBE_NO_RESPONSE)),
            current = report(),
        )

        assertEquals(DiagnosticVerificationStatus.UNCHANGED, result.status)
        assertEquals(
            listOf(DiagnosticFindingCode.GATEWAY_PROBE_NO_RESPONSE),
            result.resolvedContextFindingCodes,
        )
    }

    @Test
    fun vpnAndFakeIpRemainContextOnly() {
        val result = compare(
            previous = report(
                listOf(
                    DiagnosticFindingCode.VPN_ACTIVE,
                    DiagnosticFindingCode.FAKE_IP_CONTEXT,
                ),
            ),
            current = report(
                listOf(
                    DiagnosticFindingCode.VPN_ACTIVE,
                    DiagnosticFindingCode.FAKE_IP_CONTEXT,
                ),
            ),
        )

        assertEquals(DiagnosticVerificationStatus.UNCHANGED, result.status)
        assertTrue(result.stillPresentFindingCodes.isEmpty())
        assertEquals(
            listOf(
                DiagnosticFindingCode.VPN_ACTIVE,
                DiagnosticFindingCode.FAKE_IP_CONTEXT,
            ),
            result.stillPresentContextFindingCodes,
        )
    }

    @Test
    fun noActiveNetworkCanResolveWhenTheNetworkStageIsVerified() {
        val result = compare(
            previous = report(listOf(DiagnosticFindingCode.NO_ACTIVE_NETWORK)),
            current = report(),
        )

        assertEquals(
            DiagnosticVerificationStatus.RESOLVED_OR_NOT_REPRODUCED,
            result.status,
        )
        assertEquals(
            listOf(DiagnosticFindingCode.NO_ACTIVE_NETWORK),
            result.resolvedFindingCodes,
        )
    }

    @Test
    fun incompleteCurrentRunHasNoVerdict() {
        val result = compare(
            previous = report(listOf(DiagnosticFindingCode.DNS_RESOLUTION_FAILURE)),
            current = report(status = DiagnosticRunStatus.CANCELLED),
        )

        assertEquals(DiagnosticVerificationStatus.INCONCLUSIVE, result.status)
        assertTrue(result.resolvedFindingCodes.isEmpty())
    }

    @Test
    fun changedFingerprintPreventsDirectComparison() {
        val result = compare(
            previous = report(fingerprint = "wifi-a"),
            current = report(fingerprint = "cellular-b"),
        )

        assertEquals(DiagnosticVerificationStatus.CONTEXT_CHANGED, result.status)
        assertEquals(false, result.sameNetworkContext)
        assertTrue(result.newFindingCodes.isEmpty())
    }

    @Test
    fun missingFingerprintIsInconclusive() {
        val result = compare(
            previous = report(fingerprint = null),
            current = report(fingerprint = null),
        )

        assertEquals(DiagnosticVerificationStatus.INCONCLUSIVE, result.status)
        assertEquals(null, result.sameNetworkContext)
    }

    @Test
    fun changedTargetPreventsTargetFindingComparison() {
        val result = compare(
            previous = report(
                findings = listOf(DiagnosticFindingCode.TARGET_TCP_TIMEOUT),
                target = DiagnosticTarget("example.com", DiagnosticTargetKind.DOMAIN),
            ),
            current = report(
                target = DiagnosticTarget("example.org", DiagnosticTargetKind.DOMAIN),
            ),
        )

        assertEquals(DiagnosticVerificationStatus.CONTEXT_CHANGED, result.status)
        assertEquals(false, result.sameTarget)
        assertTrue(result.resolvedFindingCodes.isEmpty())
    }

    @Test
    fun findingOrderIsStableAndUsesReportOrder() {
        val result = compare(
            previous = report(
                findings = listOf(
                    DiagnosticFindingCode.NO_ACTIVE_NETWORK,
                    DiagnosticFindingCode.DNS_RESOLUTION_FAILURE,
                    DiagnosticFindingCode.NO_ACTIVE_NETWORK,
                ),
            ),
            current = report(),
        )

        assertEquals(
            listOf(
                DiagnosticFindingCode.NO_ACTIVE_NETWORK,
                DiagnosticFindingCode.DNS_RESOLUTION_FAILURE,
            ),
            result.resolvedFindingCodes,
        )
    }

    private fun compare(
        previous: AutomaticDiagnosticResult,
        current: AutomaticDiagnosticResult,
    ): DiagnosticVerificationResult =
        DiagnosticVerificationComparator.compare(previous, current)

    private fun report(
        findings: List<DiagnosticFindingCode> = emptyList(),
        fingerprint: String? = "wifi-a",
        status: DiagnosticRunStatus = DiagnosticRunStatus.COMPLETED,
        target: DiagnosticTarget? = null,
        statuses: Map<DiagnosticStage, DiagnosticCheckStatus> = emptyMap(),
    ): AutomaticDiagnosticResult {
        val checks = diagnosticStages.map { stage ->
            DiagnosticCheck(
                code = stage.checkCode(),
                stage = stage,
                status = statuses[stage] ?: DiagnosticCheckStatus.PASS,
                severity = DiagnosticSeverity.HEALTHY,
                summary = "fixture",
                target = target.takeIf { stage == DiagnosticStage.TARGET },
            )
        }
        return AutomaticDiagnosticResult(
            evidence = DiagnosticRunEvidence(
                runStatus = status,
                startedAt = 1_000L,
                finishedAt = 1_100L,
                durationMs = 100L,
                fingerprint = fingerprint?.let(::NetworkFingerprint),
                networkContextSummary = null,
                observations = emptyList(),
                checks = checks,
                intent = DiagnosticIntent(target = target),
            ),
            analysis = DiagnosticAnalysisResult(
                findings = findings.map(::finding),
                diagnosis = DiagnosticDiagnosis(
                    status = com.networktoolbox.core.common.diagnostic.DiagnosticDiagnosisStatus.NORMAL,
                    title = "fixture",
                    explanation = "fixture",
                    confidence = DiagnosticConfidence.HIGH,
                ),
                recommendations = emptyList(),
            ),
        )
    }

    private fun finding(code: DiagnosticFindingCode): DiagnosticFinding = DiagnosticFinding(
        code = code,
        title = "fixture title",
        description = "fixture description",
        severity = when (code) {
            DiagnosticFindingCode.NO_ACTIVE_NETWORK -> DiagnosticSeverity.ERROR
            DiagnosticFindingCode.DNS_RESOLUTION_FAILURE,
            DiagnosticFindingCode.PUBLIC_CONNECTIVITY_UNCONFIRMED,
            DiagnosticFindingCode.LOCAL_OR_UPSTREAM_PATH_UNCONFIRMED,
            DiagnosticFindingCode.TARGET_TCP_TIMEOUT,
            DiagnosticFindingCode.TARGET_TCP_REFUSED,
            DiagnosticFindingCode.TARGET_TCP_PATH_UNCONFIRMED,
            -> DiagnosticSeverity.WARNING
            else -> DiagnosticSeverity.NOTICE
        },
        evidenceLevel = DiagnosticEvidenceLevel.SUPPORTED,
        confidence = DiagnosticConfidence.MEDIUM,
    )

    private fun DiagnosticStage.checkCode(): DiagnosticCheckCode = when (this) {
        DiagnosticStage.NETWORK_STATE -> DiagnosticCheckCode.NETWORK_STATE
        DiagnosticStage.IP_CONFIGURATION -> DiagnosticCheckCode.IP_CONFIGURATION
        DiagnosticStage.GATEWAY -> DiagnosticCheckCode.GATEWAY
        DiagnosticStage.INTERNET -> DiagnosticCheckCode.PUBLIC_CONNECTIVITY
        DiagnosticStage.DNS -> DiagnosticCheckCode.DNS_RESOLUTION
        DiagnosticStage.TARGET -> DiagnosticCheckCode.TARGET_CONNECTIVITY
        DiagnosticStage.ADVANCED_PATH -> DiagnosticCheckCode.ADVANCED_PATH
    }

    private companion object {
        val diagnosticStages = listOf(
            DiagnosticStage.NETWORK_STATE,
            DiagnosticStage.IP_CONFIGURATION,
            DiagnosticStage.GATEWAY,
            DiagnosticStage.INTERNET,
            DiagnosticStage.DNS,
            DiagnosticStage.TARGET,
        )
    }
}
