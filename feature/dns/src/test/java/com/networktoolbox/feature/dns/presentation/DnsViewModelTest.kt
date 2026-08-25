package com.networktoolbox.feature.dns.presentation

import com.networktoolbox.core.common.history.HistoryRecorder
import com.networktoolbox.core.network.dns.DnsMethod
import com.networktoolbox.core.network.dns.DnsRecord
import com.networktoolbox.core.network.dns.DnsRecordType
import com.networktoolbox.core.network.dns.DnsResult
import com.networktoolbox.feature.dns.FakeDnsEngine
import com.networktoolbox.feature.dns.domain.LookupDnsUseCase
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
    fun initialStateIsIdle() {
        val viewModel = viewModelFor(successResult())

        assertEquals("", viewModel.uiState.value.domainInput)
        assertEquals(DnsStatus.Idle, viewModel.uiState.value.status)
    }

    @Test
    fun lookupEntersLoadingBeforeCompleting() = runTest {
        val viewModel = viewModelFor(successResult())
        viewModel.onDomainChanged("example.test")

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

        assertEquals(DnsStatus.Success(expected), viewModel.uiState.value.status)
    }

    @Test
    fun failedResultProducesErrorState() = runTest {
        val expected = DnsResult(
            domain = "missing.example.test",
            success = false,
            records = emptyList(),
            durationMs = 18,
            method = DnsMethod.SYSTEM_RESOLVER,
            errorMessage = "Domain could not be resolved.",
        )
        val viewModel = viewModelFor(expected)
        viewModel.onDomainChanged("missing.example.test")

        viewModel.lookup()
        advanceUntilIdle()

        assertEquals(DnsStatus.Error(expected), viewModel.uiState.value.status)
    }

    private fun viewModelFor(result: DnsResult): DnsViewModel =
        DnsViewModel(
            LookupDnsUseCase(
                dnsEngine = FakeDnsEngine(result),
                historyRecorder = HistoryRecorder { },
            ),
        )

    private fun successResult(): DnsResult = DnsResult(
        domain = "example.test",
        success = true,
        records = listOf(
            DnsRecord(DnsRecordType.A, "192.0.2.10"),
            DnsRecord(DnsRecordType.AAAA, "2001:db8::10"),
        ),
        durationMs = 12,
        method = DnsMethod.SYSTEM_RESOLVER,
        errorMessage = null,
    )
}
