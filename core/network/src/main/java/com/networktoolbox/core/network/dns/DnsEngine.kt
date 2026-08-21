package com.networktoolbox.core.network.dns

interface DnsEngine {
    suspend fun lookup(domain: String): DnsResult
}
