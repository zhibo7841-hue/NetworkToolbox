package com.networktoolbox.core.common.diagnostic

sealed interface DiagnosticObservationValue {
    data class BooleanValue(val value: Boolean) : DiagnosticObservationValue

    data class TextValue(val value: String) : DiagnosticObservationValue {
        init {
            requireBoundedText(value, "observation value", 512)
        }
    }

    data class AddressValue(
        val value: String,
        val family: DiagnosticAddressFamily,
    ) : DiagnosticObservationValue {
        init {
            requireBoundedText(value, "address", 128)
        }
    }

    data class LatencyValue(val milliseconds: Long) : DiagnosticObservationValue {
        init {
            require(milliseconds >= 0L) { "Latency must not be negative." }
        }
    }

    data class TcpOutcomeValue(val outcome: DiagnosticTcpOutcome) : DiagnosticObservationValue

    data class DnsOutcomeValue(val outcome: DiagnosticDnsOutcome) : DiagnosticObservationValue

    data class DnsRecordValue(
        val recordType: String,
        val name: String,
        val value: String,
        val ttlSeconds: Long? = null,
        val priority: Int? = null,
    ) : DiagnosticObservationValue {
        init {
            requireBoundedText(recordType, "DNS record type", 16)
            requireBoundedText(name, "DNS record name", 256)
            requireBoundedText(value, "DNS record value", 512)
            require(ttlSeconds == null || ttlSeconds >= 0L) {
                "DNS record TTL must not be negative."
            }
            require(priority == null || priority >= 0) {
                "DNS record priority must not be negative."
            }
        }
    }
}
