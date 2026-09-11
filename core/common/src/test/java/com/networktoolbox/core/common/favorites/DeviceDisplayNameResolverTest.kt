package com.networktoolbox.core.common.favorites

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDisplayNameResolverTest {
    @Test
    fun `custom name has priority over detected identity`() {
        assertEquals(
            "客厅路由器",
            DeviceDisplayNameResolver.resolve("  客厅路由器  ", "router.local"),
        )
    }

    @Test
    fun `custom name works without detected identity`() {
        assertEquals("NAS", DeviceDisplayNameResolver.resolve("NAS", null))
    }

    @Test
    fun `detected identity is used when custom name is absent`() {
        assertEquals("printer.local", DeviceDisplayNameResolver.resolve(null, " printer.local "))
    }

    @Test
    fun `unknown fallback is used when both names are absent`() {
        assertEquals("未知设备", DeviceDisplayNameResolver.resolve(null, null))
        assertEquals("Unknown", DeviceDisplayNameResolver.resolve(null, null, "Unknown"))
    }

    @Test
    fun `blank names are rejected and do not override detected identity`() {
        assertFalse(DeviceDisplayNameResolver.validateCustomName("   ").isSuccess)
        assertEquals("router.local", DeviceDisplayNameResolver.resolve("   ", "router.local"))
    }

    @Test
    fun `control characters are rejected`() {
        assertFalse(DeviceDisplayNameResolver.validateCustomName("router\nroom").isSuccess)
        assertFalse(DeviceDisplayNameResolver.validateCustomName("router\u0000").isSuccess)
    }

    @Test
    fun `unicode names are supported`() {
        val name = "客厅📡设备"
        assertEquals(name, DeviceDisplayNameResolver.normalizeCustomName(name))
    }

    @Test
    fun `length limit counts unicode code points`() {
        val accepted = "😀".repeat(DeviceDisplayNameResolver.MAX_CUSTOM_NAME_CODE_POINTS)
        val rejected = accepted + "😀"

        assertTrue(DeviceDisplayNameResolver.validateCustomName(accepted).isSuccess)
        assertFalse(DeviceDisplayNameResolver.validateCustomName(rejected).isSuccess)
    }

    @Test
    fun `clearing custom name restores automatic resolution`() {
        assertEquals("detected.local", DeviceDisplayNameResolver.resolve(null, "detected.local"))
    }
}
