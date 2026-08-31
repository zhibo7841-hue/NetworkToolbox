package com.networktoolbox.feature.lanscan.data

import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.feature.lanscan.domain.MdnsDiscoveryRequest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidMdnsDiscoveryExecutorTest {
    @Test
    fun `session stop leaves callback executor available for late framework work`() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            createSession(executor, generation = 1L).stop()

            val callbackRan = CountDownLatch(1)
            executor.execute(callbackRan::countDown)

            assertTrue(callbackRan.await(1L, TimeUnit.SECONDS))
            assertFalse(executor.isShutdown)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `shared callback executor remains available across closed sessions`() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            createSession(executor, generation = 1L).stop()
            createSession(executor, generation = 2L).stop()

            val callbackRan = CountDownLatch(1)
            executor.execute(callbackRan::countDown)

            assertTrue(callbackRan.await(1L, TimeUnit.SECONDS))
            assertFalse(executor.isShutdown)
        } finally {
            executor.shutdownNow()
        }
    }

    private fun createSession(
        executor: ExecutorService,
        generation: Long,
    ) = AndroidMdnsDiscovery.Session(
        nsdManager = null,
        connectivityManager = null,
        wifiManager = null,
        callbackExecutor = executor,
        request = MdnsDiscoveryRequest(
            generation = generation,
            networkIdentity = "test-network",
            connectionType = ConnectionType.WIFI,
            serviceTypes = listOf("_http._tcp"),
        ),
        onEvent = {},
    )
}
