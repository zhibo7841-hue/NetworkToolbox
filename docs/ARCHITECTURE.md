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
- `DEVICES` / 设备 — the Phase 1 LAN Device Center foundation. It presents the
  current network and the discovered device list through the existing LAN
  Scanner capability.

`HISTORY`, `PRIVACY`, and `ABOUT` are app-level secondary routes, not
bottom-navigation destinations. A shared Material 3 Drawer is available from
each top-level destination and opens those three destinations directly. There
is no user-facing `SETTINGS` route while the product has no confirmed
configurable settings. The Drawer does not duplicate the Tools catalog.

Tool routes retain their source-aware caller. A tool opened from Home or Tools
returns to that caller, while a route opened from Devices can return to
Devices. History, Privacy, and About retain the originating Home, Tools, or
Devices caller when opened from the transient Drawer. Back returns to that
caller and does not automatically reopen the Drawer; the Drawer is never a
navigation destination. History opened from a non-Drawer flow keeps its real
caller and follows the same ordinary Back behavior. UI Back and system Back
use the same state transition; system Back closes an open Drawer before
navigating.

A report opened from History keeps a one-level tool return target so Back
returns to History before the original top-level caller. The live diagnostic
tool and a restored saved report use explicit presentation contexts, allowing
the live header to remain `网络诊断` while a saved artifact can be titled
`网络诊断报告`. The navigation state uses named top-level and tool
destinations rather than integer indexes, and its saveable representation
preserves the selected destination, caller, and nested tool return target
across configuration changes. The Devices screen reuses the existing LAN
Scanner ViewModel and state through a current-network-only entry point; it does
not create a second discovery pipeline or duplicate device domain model. Tools
-> 局域网扫描 retains the existing current/custom range workflow. The Device
Center presentation is intentionally separate from that tool surface, but
both use the same `RunLanScan`, discovery engine, real progress, cancellation,
network-change handling, and identity enrichment. This phase does not
introduce Favorites, Wake-on-LAN, Device Detail, background scans, new
discovery protocols, IPv6 LAN scanning, new permissions, or new Room data.

## LAN Device Center Phase 1

The top-level Devices route is implemented as a small presentation boundary
over the existing LAN Scanner domain. `LanScannerViewModel` remains the single
owner of readiness, range state, scan lifecycle, enrichment generations, and
terminal states. The Device Center uses dedicated current-network entry
methods so a custom range selected in the Tools scanner cannot leak into the
top-level Devices flow. It does not auto-start a scan when opened.

The Device Center uses the existing `LanDevice`,
`LanDeviceIdentityAggregator`, and scanner ordering. A lightweight
`DeviceCenterNetworkSummary` is presentation data only; it does not replace
`NetworkContext` or create another network provider. The compact UI hides
technical range controls and shows real gateway/local roles, observed identity
data, and confirmed discovery evidence without inferring online status or
inventing MAC/vendor data. Network availability, cellular, VPN, cancellation,
and network changes remain mapped from the existing scanner state.
