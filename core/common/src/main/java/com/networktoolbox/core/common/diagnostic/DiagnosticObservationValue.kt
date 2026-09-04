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
}
