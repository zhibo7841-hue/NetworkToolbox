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

## App shell navigation

The current app shell has three formal top-level destinations:

- `HOME` / 首页 — current network status, recent checks, and quick actions.
- `TOOLS` / 工具 — the complete set of existing network tools.
- `DEVICES` / 设备 — the current LAN Scanner as the usable device-discovery
  entry point until a separate LAN Device Center is approved and implemented.

`SETTINGS` is a secondary route, not a bottom-navigation destination. A shared
Material 3 Drawer is available from each top-level destination and currently
contains the Settings entry. About, local data management, and privacy remain
inside the single Settings screen; the Drawer does not duplicate the Tools
catalog.

Tool routes retain their source-aware caller. A tool opened from Home or Tools
returns to that caller, while a route opened from Devices can return to
Devices. Settings follows the same rule: Back returns to the top-level
destination from which the Drawer was opened. Secondary pages expose their
Back action and do not expose the top-level Drawer action.

The navigation state uses named top-level and tool destinations rather than
integer indexes, and its saveable representation preserves the selected
destination and caller across configuration changes. The Devices screen reuses
the existing LAN Scanner ViewModel and state; it does not create a second
discovery pipeline or introduce LAN Device Center, Favorites, or Wake-on-LAN
behavior.
