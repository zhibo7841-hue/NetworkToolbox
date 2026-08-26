package com.networktoolbox.core.network.dns

fun interface DnsServerInfoProvider {
    fun current(): DnsServerInfo?
}
