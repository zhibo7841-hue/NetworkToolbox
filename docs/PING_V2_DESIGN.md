# NetworkToolbox Ping v2 Design

## 1. Current Ping Status

The current Ping implementation provides a single reachability check through the following path:

```text
PingScreen
    ↓
PingViewModel
    ↓
ExecutePingUseCase
    ↓
PingEngine
    ↓
AndroidPingEngine
    ↓
InetAddress.isReachable()
```

Current capabilities:

- Basic reachability detection.
- Latency display for a successful check.
- Local history recording through the shared `HistoryRecorder`.
- Hostname and IPv4/IPv6 address input through the platform resolver.
- Execution on `Dispatchers.IO`.

Current limitations:

- No packet statistics.
- No packet-loss calculation.
- No minimum, average, or maximum latency statistics.
- No jitter calculation.
- No network-quality evaluation.
- No continuous detection.
- No professional-level raw data display.
- The current result represents one best-effort system reachability check, not a pure ICMP measurement.

The current `PingResult` contains one target, one success state, one optional latency, the execution method, and an optional error message. `AndroidPingEngine` currently reports `SYSTEM_REACHABILITY` when the platform probe is used and does not label the result as ICMP.

## 2. Ping v2 Goal

Ping v2 will evolve the feature from answering:

> Is the target reachable?

to providing:

> What was the observed network quality during a defined set of reachability checks?

Ping v2 remains an analysis aid. Packet loss, latency, and quality level are observations for the selected target and time window; they are not proof of a single network fault or the cause of every application problem.

## 3. Data Model Design

The v2 result should represent one completed measurement session rather than one probe invocation. A future implementation may use a separate `PingSessionResult` name to avoid breaking the current V0.1 `PingResult` contract.

### Proposed request model

```kotlin
data class PingRequest(
    val target: String,
    val protocol: PingProtocol = PingProtocol.AUTO,
    val mode: PingMode = PingMode.QUICK,
    val count: Int? = null,
    val timeoutMs: Int,
    val intervalMs: Int,
)
```

`count` is required for a finite session and may be null for an explicitly user-stopped continuous session. The implementation must validate count, timeout, and interval limits before starting work.

### Proposed session result

```kotlin
data class PingSessionResult(
    val target: String,
    val address: String?,
    val protocol: PingProtocol,
    val startTime: Long,
    val endTime: Long,
    val sentPackets: Int,
    val receivedPackets: Int,
    val lostPackets: Int,
    val packetLoss: Double,
    val minLatencyMs: Long?,
    val avgLatencyMs: Double?,
    val maxLatencyMs: Long?,
    val jitterMs: Double?,
    val qualityLevel: PingQualityLevel,
    val summary: String,
    val method: PingMethod,
    val errorMessage: String?,
)
```

Field rules:

- `target` is the user input, such as a hostname or literal address.
- `address` is the resolved literal address used for the session, when known.
- `protocol` records the selected or actually used address family.
- `startTime` and `endTime` are epoch milliseconds for persistence and reporting.
- `sentPackets` counts probes that were started.
- `receivedPackets` counts probes that produced a positive result.
- `lostPackets` equals `sentPackets - receivedPackets`.
- `packetLoss` is a percentage from `0.0` through `100.0`.
- `minLatencyMs`, `avgLatencyMs`, and `maxLatencyMs` use successful samples only; they are null when no sample succeeds.
- `jitterMs` is null when fewer than two successful samples exist.
- `method` must continue to describe the real mechanism. A system reachability result must not be presented as guaranteed ICMP.
- `errorMessage` describes session-level failure or a useful limitation without claiming a definitive cause.

### Protocol and mode enums

```kotlin
enum class PingProtocol {
    AUTO,
    IPV4,
    IPV6,
}

enum class PingMode {
    QUICK,
    CONTINUOUS,
}

enum class PingQualityLevel {
    EXCELLENT,
    GOOD,
    FAIR,
    POOR,
    UNKNOWN,
}
```

The quality evaluator must be a separate, pure Kotlin domain component. Its thresholds must be centralized, documented, and covered by tests. The first implementation should treat the thresholds as heuristic guidance, not as a definitive diagnosis.

Recommended jitter definition for the first implementation:

```text
mean(abs(latency[i] - latency[i - 1]))
```

Only adjacent successful latency samples participate. Lost probes do not create artificial latency values.

## 4. Detection Modes

### Quick detection

Quick detection is the default for general users. It performs one or a small finite number of checks and shows:

- Reachability.
- Average latency when available.
- Packet loss when more than one probe is used.
- A plain-language network-quality summary.

The finite count, timeout, and interval should have safe product defaults and validation limits. The exact defaults are implementation decisions and must not be scattered across UI code.

### Continuous detection

Continuous detection is intended for network troubleshooting and HomeLab use. It supports:

- A user-defined finite count.
- An explicitly unlimited run.
- A Stop action.
- Incremental in-memory statistics while the run is active.
- A final result when the run completes or is stopped.

The coroutine `Job` that owns the session must be cancellable. Stopping must close or cancel the active probe, stop scheduling new probes, and produce a clear final state. An in-progress session must not be written as a completed history record until it finishes or the user explicitly stops it.

The engine must not create one history record per packet. It should save one completed session result through the shared `HistoryRecorder`.

## 5. IPv4 and IPv6

Ping v2 supports both IPv4 and IPv6.

The UI should show the actual protocol used:

```text
Protocol: IPv4
```

or:

```text
Protocol: IPv6
```

Resolution and selection rules:

- `AUTO` may resolve both families and choose according to the active network and implementation policy.
- `IPV4` filters candidates to `Inet4Address`.
- `IPV6` filters candidates to `Inet6Address`.
- The result stores the literal address selected for the session.
- IPv6 link-local addresses require an appropriate interface or scope; a link-local literal without usable scope must be reported as unavailable rather than silently retried as IPv4.
- If an active Android `Network` is available, network-specific resolution and socket/interface selection should be preferred over process-wide network binding.
- The implementation must distinguish “no IPv6 address available” from “IPv6 target did not respond.”

No location permission should be introduced solely for Ping v2. Existing network permissions remain subject to the actual Android APIs used.

## 6. UI Design Principles

Ping v2 follows the project’s two-layer experience.

### Default user view

Show the smallest useful conclusion:

```text
Network quality: Excellent
Average latency: 15 ms
Packet loss: 0%
```

The explanation must use qualified language such as “observed quality during this check” and must not claim that the router, ISP, or cable is definitely faulty.

### Advanced view

Allow expansion to show:

```text
Protocol: IPv4
Packets: 100 sent / 100 received
Loss: 0%
Min: 12 ms
Avg: 15 ms
Max: 21 ms
Jitter: 3 ms
Method: System Reachability
Address: 192.0.2.10
```

Loading, stopping, completed, failed, and unavailable states must remain distinct. The UI must not display “ICMP” unless the engine has actually established that the measurement method was ICMP.

## 7. History Integration

Ping v2 must use the existing shared history path:

```text
PingSessionResult
        ↓
HistoryRecordFactory
        ↓
HistoryRecorder
        ↓
HistoryRepository
        ↓
Room
```

The stored `HistoryRecord` should keep a human-readable title and summary while `detailJson` contains the session fields needed for later display and reporting, including:

- Target and resolved address.
- Protocol and method.
- Start and end time.
- Sent, received, and lost packet counts.
- Packet loss.
- Latency statistics.
- Jitter.
- Quality level.

Do not create any of the following:

- `PingHistoryRepository`.
- A Ping-specific database.
- A second persistence path outside `HistoryRecorder`.

## 8. Diagnostic Report Integration

The normalized Ping v2 session result is a future input to Diagnostic Report v2.

The report may use:

- Reachability observation.
- Average, minimum, and maximum latency.
- Packet loss.
- Jitter.
- Network-quality level.
- Protocol and execution method.

The report should explain what these measurements may indicate and provide troubleshooting suggestions. It must preserve uncertainty and must not convert a Ping result into a definitive statement such as “the router is broken” or “the ISP is at fault.”

## 9. Android Implementation Research

### ICMP implementation

Direct ICMP from an ordinary Android application is not a portable assumption. Raw socket access, platform privileges, and vendor behavior make a direct ICMP implementation unsuitable as the default V0.2 approach. A result must never be labeled ICMP merely because it resembles a Ping.

### System reachability API

`InetAddress.isReachable()` is the closest existing platform capability and is already used by V0.1. Android documents it as best effort: the implementation may try ICMP ECHO first and fall back to TCP ECHO, and success on either protocol returns true. This makes it useful for a clearly labeled system reachability measurement, but not for a guaranteed ICMP-only metric.

The v2 engine can collect repeated samples by invoking the same platform reachability operation for each probe, while preserving `SYSTEM_REACHABILITY` as the method.

### System ping command

Invoking a device shell command such as `ping` or `ping6` is not recommended as the primary implementation because command availability, flags, output format, permissions, and vendor behavior are not stable application contracts. Parsing command output would also make the engine harder to test and maintain. It may be investigated as a device-specific diagnostic fallback only if its method and limitations are exposed explicitly.

### Socket approaches

TCP connect is not Ping and must not be used as a hidden substitute. A successful TCP connection proves only that a TCP service accepted a connection on a selected port. UDP without an application-level response does not provide a reliable reachability result either.

Socket APIs may still be useful for network selection and address-family handling. They must remain separate from the Ping measurement method and must not be reported as ICMP success.

### IPv6 and network selection

Android provides `Network.getAllByName()` for resolution on a specific network and `Network.bindSocket()` for directing socket traffic to that network. These APIs should be evaluated for network-specific resolution and interface selection when the active network is known. Process-wide binding should not be used casually because it changes the behavior of subsequent sockets and resolutions.

### Recommended approach

For the first Ping v2 implementation:

1. Keep the engine platform-native and dependency-free.
2. Introduce a session-oriented domain model and a pure Kotlin statistics evaluator.
3. Use repeated `InetAddress.isReachable()` calls as the initial measurement mechanism.
4. Record the method as `SYSTEM_REACHABILITY` for every such result.
5. Add explicit IPv4/IPv6 address filtering and active-network-aware resolution where supported.
6. Use coroutine cancellation for Stop.
7. Persist one completed session through the existing `HistoryRecorder`.
8. Keep direct ICMP and shell-command Ping out of the default implementation until a separate platform feasibility decision is approved.

This approach provides useful statistics without claiming a measurement guarantee that Android cannot provide consistently across devices.

## 10. Testing Plan

### Unit tests

Use injected fake resolver, fake reachability probe, clock, and delay/scheduler components. Do not make public-network access a unit-test prerequisite.

Cover at least:

- Successful IPv4 session.
- Unreachable target.
- Zero packet loss.
- Partial packet loss.
- Complete packet loss.
- High-latency samples.
- Minimum, average, and maximum latency calculations.
- Jitter calculation and the fewer-than-two-samples case.
- Quality-level mapping.
- IPv6 address selection.
- Invalid protocol/target combinations.
- Finite-count completion.
- Continuous-session cancellation and Stop behavior.
- History record creation for a completed session.

### Android tests

Use local or controlled test targets where possible. Verify Android resolver and address-family behavior without depending on a public DNS or Ping service. Check cancellation, lifecycle behavior, and `Dispatchers.IO` execution boundaries.

### Real-device tests

On the Sony Xperia 1 VII running Android 16, verify:

- IPv4 quick detection.
- IPv6 detection when the device/network provides IPv6.
- Finite count configuration.
- Continuous detection and Stop.
- Quality and advanced statistics display.
- One completed Ping history record.
- Diagnostic Report consumption of the Ping v2 result.

Public targets such as `8.8.8.8` may be used for manual smoke testing only. They must not be the sole automated test dependency.

## References

- [Android `InetAddress` API](https://developer.android.com/reference/java/net/InetAddress)
- [Android `Network` API](https://developer.android.com/reference/android/net/Network)
- [Android `ConnectivityManager` API](https://developer.android.com/reference/android/net/ConnectivityManager)
