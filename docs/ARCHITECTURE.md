# Architecture

## Status

This document describes the current architecture direction only. It does not create Android source code, Gradle configuration, or implementation modules.

## Technology direction

- Kotlin.
- Jetpack Compose for the UI.
- Android Native platform APIs.
- Clean Architecture as the primary separation-of-concerns approach.
- Minimum supported Android version: Android API 31.

## Clean Architecture

The implementation should keep presentation, domain, and data responsibilities separate:

- Presentation is responsible for Compose UI state and user interaction.
- Domain is responsible for use cases and the meaning of network analysis operations.
- Data is responsible for Android platform access, probe execution, and persistence of approved local data.

The exact package and module boundaries will be finalized during implementation. This planning document does not prescribe code modules before the relevant use cases are implemented.

## Modular design

The application should be organized around clear responsibilities so that network context collection, individual probes, report/history handling, permissions, and presentation can evolve independently. Module boundaries must remain aligned with the confirmed product scope and should not introduce unrelated utilities.

Modular design is an architectural direction, not a request to create empty Android modules in the planning phase.

## NetworkContext

`NetworkContext` is the shared context for a diagnostic operation. It represents the relevant observed network environment and execution conditions at the time a check runs, such as available interfaces, address information, DNS-related context, connectivity state, and permission/capability state where applicable.

It should be treated as observed context rather than a diagnosis. The final data shape, retention rules, and redaction requirements will be defined with the implementation and privacy review.

## ProbeResult

`ProbeResult` is the normalized result contract for an individual network check. It should make the probe type, target or input, outcome, measured values, timing, relevant error information, and execution context distinguishable to callers.

Results should preserve enough evidence for a report while clearly separating measurements from troubleshooting references. A `ProbeResult` must not imply that a probe has established the single cause of a network problem.

## Permission management

- Request runtime permissions only when a confirmed diagnostic requires them.
- Explain the purpose of a permission in user-facing language.
- Handle denial and unavailable capabilities gracefully.
- Keep permission state separate from probe result interpretation.
- Avoid broad permission requests that are not needed by the selected operation.

Permission behavior will follow the Android API requirements applicable to the supported version and the final implementation of each feature.

## Android version strategy

The minimum supported Android version is API 31. The application is Android Native and should use platform capabilities available on the supported range, with compatibility handling where required. Higher-version behavior must not silently redefine the V0.1 scope.

Build configuration, target SDK selection, and compatibility details belong to the Android implementation phase and are intentionally not created in this task.
