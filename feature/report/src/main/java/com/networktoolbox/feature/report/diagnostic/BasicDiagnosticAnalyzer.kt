package com.networktoolbox.feature.report.diagnostic

import com.networktoolbox.core.network.dns.DnsResult
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.network.ping.PingResult
import com.networktoolbox.core.network.tcp.TcpProbeResult

class BasicDiagnosticAnalyzer : DiagnosticAnalyzer {
    override fun analyze(
        context: NetworkContext?,
        ping: PingResult?,
        dns: DnsResult?,
        tcp: TcpProbeResult?,
    ): DiagnosticReport {
        val findings = mutableListOf<DiagnosticFinding>()
        val suggestions = mutableListOf<String>()
        val basicConnectivityPassed = context != null && ping?.success == true && dns?.success == true

        if (basicConnectivityPassed) {
            findings += DiagnosticFinding(
                level = FindingLevel.INFO,
                title = "Network connectivity looks normal",
                description = "当前网络、解析和目标连通性均通过基础检测。",
            )
        } else if (ping?.success == true) {
            findings += DiagnosticFinding(
                level = FindingLevel.INFO,
                title = "目标可达性检测通过",
                description = "Ping 检测通过，但其他检测结果仍需单独关注。",
            )
        }

        if (ping?.success == false) {
            findings += DiagnosticFinding(
                level = FindingLevel.WARNING,
                title = "目标不可达",
                description = "Ping 检测未成功，目标可达性无法确认。",
            )
            suggestions += "检查目标地址、网络连接或防火墙规则。"
        }

        if (dns?.success == false) {
            findings += DiagnosticFinding(
                level = FindingLevel.WARNING,
                title = "DNS解析失败",
                description = "DNS 查询未成功，域名解析结果不可用。",
            )
            suggestions += "检查DNS配置或DNS服务是否可用。"
        }

        when {
            tcp?.success == false && tcp.errorMessage == CONNECTION_REFUSED -> {
                findings += DiagnosticFinding(
                    level = FindingLevel.WARNING,
                    title = "目标端口拒绝连接",
                    description = "目标主机可能可达，但对应服务未监听。",
                )
                suggestions += "检查目标端口号和对应服务状态。"
            }

            tcp?.success == false && tcp.errorMessage == TIMEOUT -> {
                findings += DiagnosticFinding(
                    level = FindingLevel.WARNING,
                    title = "连接超时",
                    description = "目标没有及时响应TCP连接。",
                )
                suggestions += "检查目标地址、端口或防火墙规则。"
            }

            tcp?.success == false -> {
                findings += DiagnosticFinding(
                    level = FindingLevel.WARNING,
                    title = "TCP端口检测失败",
                    description = "TCP连接未建立，具体原因无法由本次检测确定。",
                )
                suggestions += "检查目标地址、端口和相关防火墙规则。"
            }
        }

        val summary = when {
            basicConnectivityPassed && findings.none { it.level != FindingLevel.INFO } -> "基础网络连接正常"
            findings.isNotEmpty() -> "检测完成，发现需要关注的网络现象"
            else -> "暂无足够检测结果"
        }

        return DiagnosticReport(
            summary = summary,
            findings = findings,
            suggestions = suggestions.distinct(),
        )
    }

    private companion object {
        const val CONNECTION_REFUSED = "Connection refused"
        const val TIMEOUT = "Timeout"
    }
}
