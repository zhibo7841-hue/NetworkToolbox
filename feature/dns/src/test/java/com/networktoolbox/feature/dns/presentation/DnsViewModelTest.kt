package com.networktoolbox.feature.dns.presentation

import com.networktoolbox.core.common.history.HistoryRecorder
import com.networktoolbox.core.network.dns.DnsLookupResult
import com.networktoolbox.core.network.dns.DnsLookupStatus
import com.networktoolbox.core.network.dns.DnsQueryMethod
import com.networktoolbox.core.network.dns.DnsRecord
import com.networktoolbox.core.network.dns.DnsRecordType
import com.networktoolbox.feature.dns.FakeDnsQueryEngine
import com.networktoolbox.feature.dns.domain.LookupDnsV2UseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DnsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateUsesSimpleAAndAaaaDefaults() {
        val viewModel = viewModelFor(successResult())

        assertEquals("", viewModel.uiState.value.domainInput)
        assertEquals(
            setOf(DnsRecordType.A, DnsRecordType.AAAA),
            viewModel.uiState.value.selectedRecordTypes,
        )
        assertEquals(false, viewModel.uiState.value.advancedSettingsExpanded)
        assertEquals(DnsStatus.Idle, viewModel.uiState.value.status)
    }

    @Test
    fun lookupEntersLoadingBeforeCompleting() = runTest {
        val viewModel = viewModelFor(successResult())
        viewModel.onDomainChanged(" example.test ")

        viewModel.lookup()

        assertEquals(DnsStatus.Loading("example.test"), viewModel.uiState.value.status)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.status is DnsStatus.Success)
    }

    @Test
    fun successfulResultProducesSuccessState() = runTest {
        val expected = successResult()
        val viewModel = viewModelFor(expected)
        viewModel.onDomainChanged("example.test")

        viewModel.lookup()
        advanceUntilIdle()

        val state = viewModel.uiState.value.status as DnsStatus.Success
        assertEquals(expected, state.result)
    }

    @Test
    fun partialResultProducesSuccessStateWithPartialStatus() = runTest {
        val expected = result(
            status = DnsLookupStatus.PARTIAL,
            records = listOf(DnsRecord(DnsRecordType.A, "192.0.2.10")),
            errorMessage = "AAAA query returned no records.",
        )
        val viewModel = viewModelFor(expected)

        viewModel.onDomainChanged("partial.example")
        viewModel.lookup()
        advanceUntilIdle()

        assertEquals(expected, (viewModel.uiState.value.status as DnsStatus.Success).result)
    }

    @Test
    fun nxdomainProducesErrorState() = runTest {
        val expected = result(
            status = DnsLookupStatus.NXDOMAIN,
            errorMessage = "DNS response reported NXDOMAIN.",
        )
        val viewModel = viewModelFor(expected)

        viewModel.onDomainChanged("missing.example")
        viewModel.lookup()
        advanceUntilIdle()

        assertEquals(expected, (viewModel.uiState.value.status as DnsStatus.Error).result)
    }

    @Test
    fun timeoutProducesErrorState() = runTest {
        val expected = result(
            status = DnsLookupStatus.TIMEOUT,
            errorMessage = "DNS query timed out.",
        )
        val viewModel = viewModelFor(expected)

        viewModel.onDomainChanged("slow.example")
        viewModel.lookup()
        advanceUntilIdle()

        assertEquals(expected, (viewModel.uiState.value.status as DnsStatus.Error).result)
    }

    @Test
    fun noRecordsIsShownAsCompletedLookupNotFailure() = runTest {
        val expected = result(
            status = DnsLookupStatus.NO_RECORDS,
            errorMessage = "No requested DNS records found.",
        )
        val viewModel = viewModelFor(expected)

        viewModel.onDomainChanged("ipv4-only.example")
        viewModel.lookup()
        advanceUntilIdle()

        assertEquals(expected, (viewModel.uiState.value.status as DnsStatus.Success).result)
    }

    @Test
    fun advancedSettingsCanSelectRecordTypesWithoutClearingTheLastType() {
        val viewModel = viewModelFor(successResult())

        viewModel.toggleAdvancedSettings()
        viewModel.toggleRecordType(DnsRecordType.CNAME)
        viewModel.toggleRecordType(DnsRecordType.A)
        viewModel.toggleRecordType(DnsRecordType.AAAA)
        viewModel.toggleRecordType(DnsRecordType.CNAME)

        assertEquals(true, viewModel.uiState.value.advancedSettingsExpanded)
        assertEquals(setOf(DnsRecordType.CNAME), viewModel.uiState.value.selectedRecordTypes)
    }

    private fun viewModelFor(result: DnsLookupResult): DnsViewModel =
        DnsViewModel(
            LookupDnsV2UseCase(
                dnsQueryEngine = FakeDnsQueryEngine(result),
                historyRecorder = HistoryRecorder { },
            ),
        )

    private fun successResult(): DnsLookupResult = result(
        status = DnsLookupStatus.SUCCESS,
        records = listOf(
            DnsRecord(
                type = DnsRecordType.A,
                name = "example.test",
                value = "192.0.2.10",
                ttl = 300,
            ),
            DnsRecord(
                type = DnsRecordType.AAAA,
                name = "example.test",
                value = "2001:db8::10",
                ttl = 300,
            ),
        ),
    )

    private fun result(
        status: DnsLookupStatus,
        records: List<DnsRecord> = emptyList(),
        errorMessage: String? = null,
    ) = DnsLookupResult(
        queryName = "example.test",
        requestedTypes = setOf(DnsRecordType.A, DnsRecordType.AAAA),
        records = records,
        server = null,
        method = DnsQueryMethod.ANDROID_DNS_RESOLVER,
        status = status,
        durationMs = 19,
        startTime = 1,
        endTime = 20,
        errorMessage = errorMessage,
    )
}
