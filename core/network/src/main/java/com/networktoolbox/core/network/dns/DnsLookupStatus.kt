package com.networktoolbox.core.network.dns

enum class DnsLookupStatus {
    SUCCESS,
    PARTIAL,
    NXDOMAIN,
    TIMEOUT,
    NETWORK_ERROR,
    INVALID_RESPONSE,
    NO_RECORDS,
    INVALID_QUERY,
    FAILED,
}
