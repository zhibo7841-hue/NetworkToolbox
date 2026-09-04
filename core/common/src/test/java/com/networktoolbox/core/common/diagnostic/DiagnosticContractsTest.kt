package com.networktoolbox.core.common.diagnostic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticContractsTest {
    @Test
    fun severityAndConfidenceAreIndependent() {
        val finding = finding(
            severity = DiagnosticSeverity.ERROR,
            confidence = DiagnosticConfidence.LOW,
        )

        assertEquals(DiagnosticSeverity.ERROR, finding.severity)
        assertEquals(DiagnosticConfidence.LOW, finding.confidence)
    }

    @Test
    fun evidenceLevelHasIndependentInterpretationDimension() {
        assertEquals(
            listOf("CONFIRMED", "SUPPORTED", "INCONCLUSIVE", "CONTRADICTED"),
            DiagnosticEvidenceLevel.entries.map { it.name },
        )
    }

    @Test
    fun runStatusAndDiagnosisStatusAreSeparate() {
        assertEquals(DiagnosticRunStatus.NETWORK_CHANGED, DiagnosticRunStatus.NETWORK_CHANGED)
        assertEquals(DiagnosticDiagnosisStatus.UNKNOWN, DiagnosticDiagnosisStatus.UNKNOWN)
        assertNotEquals(
            DiagnosticRunStatus.NETWORK_CHANGED.name,
            DiagnosticDiagnosisStatus.UNKNOWN.name,
        )
    }

    @Test
    fun stableFindingCodeDoesNotDependOnLocalizedTitle() {
        val first = finding(title = "Network problem")
        val second = finding(title = "网络状态需要关注")

        assertEquals(DiagnosticFindingCode.NO_ACTIVE_NETWORK, first.code)
        assertEquals(first.code, second.code)
        assertEquals("NO_ACTIVE_NETWORK", first.code.name)
    }

    @Test
    fun stableCheckAndRecommendationCodesAreDefined() {
        assertEquals("PUBLIC_CONNECTIVITY", DiagnosticCheckCode.PUBLIC_CONNECTIVITY.name)
        assertEquals("RETRY_DIAGNOSTIC", DiagnosticRecommendationCode.RETRY_DIAGNOSTIC.name)
    }

    @Test
    fun observationCanCarryTypedValueAndNetworkFingerprint() {
        val observation = DiagnosticObservation(
            id = "obs-public-1",
            code = DiagnosticObservationCode.PUBLIC_TCP_OUTCOME,
            stage = DiagnosticStage.INTERNET,
            source = DiagnosticObservationSource.TCP_CHECKER,
            value = DiagnosticObservationValue.TcpOutcomeValue(
                DiagnosticTcpOutcome.CONNECT_SUCCESS,
            ),
            observedAt = 1_000L,
            networkFingerprint = NetworkFingerprint("wifi:test"),
        )

        assertEquals("obs-public-1", observation.id)
        assertEquals(DiagnosticTcpOutcome.CONNECT_SUCCESS,
            (observation.value as DiagnosticObservationValue.TcpOutcomeValue).outcome)
        assertEquals(NetworkFingerprint("wifi:test"), observation.networkFingerprint)
    }

    @Test
    fun findingReferencesObservationAndCheckEvidence() {
        val finding = finding(
            evidenceObservationIds = listOf("obs-gateway", "obs-public"),
            evidenceCheckCodes = listOf(
                DiagnosticCheckCode.GATEWAY,
                DiagnosticCheckCode.PUBLIC_CONNECTIVITY,
            ),
        )

        assertEquals(listOf("obs-gateway", "obs-public"), finding.evidenceObservationIds)
        assertTrue(DiagnosticCheckCode.GATEWAY in finding.evidenceCheckCodes)
    }

    @Test
    fun diagnosisCanPointToPrimaryFinding() {
        val diagnosis = DiagnosticDiagnosis(
            status = DiagnosticDiagnosisStatus.ATTENTION,
            title = "需要关注",
            explanation = "公网路径尚未确认。",
            primaryFindingCode = DiagnosticFindingCode.PUBLIC_CONNECTIVITY_UNCONFIRMED,
            confidence = DiagnosticConfidence.MEDIUM,
        )

        assertEquals(
            DiagnosticFindingCode.PUBLIC_CONNECTIVITY_UNCONFIRMED,
            diagnosis.primaryFindingCode,
        )
    }

    @Test
    fun reportUsesSchemaVersionOneAndPreservesAppVersion() {
        val report = report()

        assertEquals(1, report.schemaVersion)
        assertEquals("0.4.0-dev", report.appVersion)
    }

    @Test
    fun tracerouteIsStoredAsBoundedSummaryNotFullHopList() {
        val summary = DiagnosticTracerouteSummary(
            target = DiagnosticTarget("example.com", DiagnosticTargetKind.DOMAIN),
            resolvedAddress = "203.0.113.10",
            status = DiagnosticTracerouteStatus.PARTIAL,
            hopCount = 8,
            respondedHopCount = 6,
            reachedTarget = false,
            durationMs = 1_500L,
        )
        val report = report(tracerouteSummary = summary)

        assertEquals(8, report.tracerouteSummary?.hopCount)
        assertEquals(6, report.tracerouteSummary?.respondedHopCount)
        assertFalse(report.tracerouteSummary?.reachedTarget == true)
    }

    @Test
    fun vpnAndFakeIpAreRepresentableAsNoticeFindings() {
        val findings = listOf(
            finding(
                code = DiagnosticFindingCode.VPN_ACTIVE,
                severity = DiagnosticSeverity.NOTICE,
            ),
            finding(
                code = DiagnosticFindingCode.FAKE_IP_CONTEXT,
                severity = DiagnosticSeverity.NOTICE,
            ),
        )

        assertTrue(findings.all { it.severity == DiagnosticSeverity.NOTICE })
        assertEquals(
            setOf(DiagnosticFindingCode.VPN_ACTIVE, DiagnosticFindingCode.FAKE_IP_CONTEXT),
            findings.map { it.code }.toSet(),
        )
    }

    @Test
    fun gatewayNoResponseCanBeNotice() {
        val finding = finding(
            code = DiagnosticFindingCode.GATEWAY_PROBE_NO_RESPONSE,
            severity = DiagnosticSeverity.NOTICE,
            evidenceLevel = DiagnosticEvidenceLevel.CONTRADICTED,
        )

        assertEquals(DiagnosticSeverity.NOTICE, finding.severity)
        assertEquals(DiagnosticEvidenceLevel.CONTRADICTED, finding.evidenceLevel)
    }

    @Test
    fun refusedAndTimeoutAreDifferentTcpOutcomes() {
        assertNotEquals(
            DiagnosticTcpOutcome.CONNECTION_REFUSED,
            DiagnosticTcpOutcome.TIMEOUT,
        )
    }

    @Test
    fun cancelledRunCannotBeConstructedAsReport() {
        var rejected = false
        try {
            report(runStatus = DiagnosticRunStatus.CANCELLED)
        } catch (_: IllegalArgumentException) {
            rejected = true
        }

        assertTrue(rejected)
    }

    @Test
    fun networkChangedRunCanHaveUnknownDiagnosisIndependently() {
        val report = report(
            runStatus = DiagnosticRunStatus.NETWORK_CHANGED,
            diagnosis = DiagnosticDiagnosis(
                status = DiagnosticDiagnosisStatus.UNKNOWN,
                title = "无法确认",
                explanation = "检测期间网络发生变化。",
                confidence = DiagnosticConfidence.LOW,
            ),
        )

        assertEquals(DiagnosticRunStatus.NETWORK_CHANGED, report.runStatus)
        assertEquals(DiagnosticDiagnosisStatus.UNKNOWN, report.diagnosis?.status)
    }

    @Test
    fun fingerprintEqualityIsDeterministic() {
        assertEquals(NetworkFingerprint("network-a"), NetworkFingerprint("network-a"))
        assertNotEquals(NetworkFingerprint("network-a"), NetworkFingerprint("network-b"))
    }

    @Test
    fun intentAllowsGeneralDefaultAndOptionalTarget() {
        val general = DiagnosticIntent()
        val target = DiagnosticTarget("example.com", DiagnosticTargetKind.DOMAIN)
        val targeted = DiagnosticIntent(
            problemType = DiagnosticProblemType.TARGET_NOT_ACCESSIBLE,
            target = target,
        )

        assertEquals(DiagnosticProblemType.GENERAL, general.problemType)
        assertEquals(target, targeted.target)
    }

    @Test
    fun networkSummaryExcludesUnapprovedIdentityFields() {
        val summary = DiagnosticNetworkSummary(
            connectionType = DiagnosticConnectionType.WIFI,
            localAddressSummary = listOf("10.0.1.206/24"),
            gateway = "10.0.1.1",
            configuredDnsServers = listOf("10.0.1.1"),
            validated = true,
        )

        assertEquals("10.0.1.1", summary.gateway)
        assertTrue(summary.localAddressSummary.isNotEmpty())
    }

    @Test
    fun recommendationUsesStableCodeAndSimplePriority() {
        val recommendation = DiagnosticRecommendation(
            code = DiagnosticRecommendationCode.RETRY_DIAGNOSTIC,
            priority = DiagnosticRecommendationPriority.PRIMARY,
            title = "重新检测",
            action = "重新执行诊断。",
            reason = "此前证据可能已经变化。",
        )

        assertEquals(DiagnosticRecommendationCode.RETRY_DIAGNOSTIC, recommendation.code)
        assertEquals(DiagnosticRecommendationPriority.PRIMARY, recommendation.priority)
    }

    @Test
    fun verificationContextReservesComparisonWithoutImplementingIt() {
        val context = DiagnosticVerificationContext(
            previousReportId = "history-1",
            previousFindingCodes = listOf(DiagnosticFindingCode.DNS_RESOLUTION_FAILURE),
            currentComparisonStatus = DiagnosticComparisonStatus.EVIDENCE_CHANGED,
        )

        assertEquals("history-1", context.previousReportId)
        assertEquals(
            DiagnosticComparisonStatus.EVIDENCE_CHANGED,
            context.currentComparisonStatus,
        )
    }

    @Test
    fun observationStatesDoNotContainContradicted() {
        assertEquals(
            setOf("CONFIRMED", "UNAVAILABLE", "UNKNOWN"),
            DiagnosticObservationState.entries.map { it.name }.toSet(),
        )
        assertFalse(DiagnosticObservationState.entries.any { it.name == "CONTRADICTED" })
    }

    @Test
    fun reportViewLevelIsOnlyAProjectionChoice() {
        assertEquals(
            listOf(DiagnosticReportViewLevel.CONCISE, DiagnosticReportViewLevel.TECHNICAL),
            DiagnosticReportViewLevel.entries,
        )
    }

    @Test
    fun fixtureEqualityIsStable() {
        val first = report()
        val second = report()

        assertEquals(first, second)
    }

    private fun finding(
        code: DiagnosticFindingCode = DiagnosticFindingCode.NO_ACTIVE_NETWORK,
        title: String = "Network finding",
        severity: DiagnosticSeverity = DiagnosticSeverity.WARNING,
        evidenceLevel: DiagnosticEvidenceLevel = DiagnosticEvidenceLevel.SUPPORTED,
        confidence: DiagnosticConfidence = DiagnosticConfidence.MEDIUM,
        evidenceObservationIds: List<String> = emptyList(),
        evidenceCheckCodes: List<DiagnosticCheckCode> = emptyList(),
    ) = DiagnosticFinding(
        code = code,
        title = title,
        description = "Observed evidence requires a cautious explanation.",
        severity = severity,
        evidenceLevel = evidenceLevel,
        confidence = confidence,
        evidenceObservationIds = evidenceObservationIds,
        evidenceCheckCodes = evidenceCheckCodes,
    )

    private fun report(
        runStatus: DiagnosticRunStatus = DiagnosticRunStatus.COMPLETED,
        diagnosis: DiagnosticDiagnosis? = null,
        tracerouteSummary: DiagnosticTracerouteSummary? = null,
    ) = DiagnosticReportV1(
        appVersion = "0.4.0-dev",
        timestamp = 1_000L,
        durationMs = 250L,
        diagnosis = diagnosis,
        tracerouteSummary = tracerouteSummary,
        runStatus = runStatus,
    )
}
