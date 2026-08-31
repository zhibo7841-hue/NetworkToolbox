package com.networktoolbox.feature.lanscan.data

import com.networktoolbox.feature.lanscan.domain.ReverseDnsResolution
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidReverseDnsResolverTest {
    @Test
    fun `canonical hostname is preserved apart from a final root dot`() = runTest {
        val resolver = AndroidReverseDnsResolver(
            lookup = CanonicalHostNameLookup { "HOME-SERVER.example.local." },
        )

        assertEquals(
            ReverseDnsResolution.Resolved("HOME-SERVER.example.local"),
            resolver.resolve("10.0.1.254"),
        )
    }

    @Test
    fun `raw ip canonical hostname is no result`() = runTest {
        val resolver = AndroidReverseDnsResolver(
            lookup = CanonicalHostNameLookup { "10.0.1.254" },
        )

        assertEquals(ReverseDnsResolution.NoResult, resolver.resolve("10.0.1.254"))
    }

    @Test
    fun `blank canonical hostname is no result`() = runTest {
        val resolver = AndroidReverseDnsResolver(
            lookup = CanonicalHostNameLookup { "   " },
        )

        assertEquals(ReverseDnsResolution.NoResult, resolver.resolve("10.0.1.254"))
    }

    @Test
    fun `lookup exception is a failed result`() = runTest {
        val resolver = AndroidReverseDnsResolver(
            lookup = CanonicalHostNameLookup { error("resolver unavailable") },
        )

        assertTrue(resolver.resolve("10.0.1.254") is ReverseDnsResolution.Failed)
    }
}
