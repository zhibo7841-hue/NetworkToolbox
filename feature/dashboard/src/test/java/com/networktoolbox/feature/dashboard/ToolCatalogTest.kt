package com.networktoolbox.feature.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCatalogTest {
    @Test
    fun toolsAreGroupedByCurrentProductCategories() {
        val callbacks = callbacks()
        val sections = dashboardToolSections(callbacks)

        assertEquals(listOf("连通性检测", "网络工具", "诊断与记录"), sections.map { it.title })
        assertEquals(
            listOf(
                listOf(DashboardToolId.PING, DashboardToolId.DNS, DashboardToolId.TCP, DashboardToolId.TRACEROUTE),
                listOf(DashboardToolId.SUBNET, DashboardToolId.LAN_SCAN),
                listOf(DashboardToolId.REPORT, DashboardToolId.HISTORY),
            ),
            sections.map { section -> section.tools.map(DashboardToolDefinition::id) },
        )
        assertEquals(8, sections.flatMap { it.tools }.distinctBy(DashboardToolDefinition::id).size)
    }

    @Test
    fun homeQuickTools_areLimitedToFourExistingTools() {
        val quickIds = quickToolDefinitions(callbacks()).map(DashboardToolDefinition::id)

        assertEquals(
            listOf(
                DashboardToolId.PING,
                DashboardToolId.DNS,
                DashboardToolId.TRACEROUTE,
                DashboardToolId.LAN_SCAN,
            ),
            quickIds,
        )
        assertEquals(4, quickIds.size)
        assertFalse(dashboardToolDefinitions(callbacks()).any { it.title.contains("Wake") })
    }

    @Test
    fun everyToolCard_invokesItsNavigationCallback() {
        val calls = mutableListOf<DashboardToolId>()
        val definitions = dashboardToolDefinitions(callbacks { id -> calls += id })

        definitions.forEach { it.onClick() }

        assertEquals(definitions.map(DashboardToolDefinition::id), calls)
        assertTrue(calls.containsAll(DashboardToolId.entries.toList()))
    }

    private fun callbacks(onClick: (DashboardToolId) -> Unit = {}): DashboardNavigationCallbacks =
        DashboardNavigationCallbacks(
            onOpenPing = { onClick(DashboardToolId.PING) },
            onOpenDns = { onClick(DashboardToolId.DNS) },
            onOpenTcp = { onClick(DashboardToolId.TCP) },
            onOpenTraceroute = { onClick(DashboardToolId.TRACEROUTE) },
            onOpenSubnet = { onClick(DashboardToolId.SUBNET) },
            onOpenLanScan = { onClick(DashboardToolId.LAN_SCAN) },
            onOpenReport = { onClick(DashboardToolId.REPORT) },
            onOpenHistory = { onClick(DashboardToolId.HISTORY) },
        )
}
