# LAN Device Identification Technical Baseline

> Status: Technical baseline for v0.3.0 LAN Device Identification Phase 1
>
> Scope: research and implementation constraints only. This document does not
> authorize a production-code, permission, dependency, Room-schema, version, or
> LAN Scanner Core change.

## 1. Purpose and frozen boundary

LAN Scanner v1 already answers a deliberately narrow question: whether a
bounded IPv4 candidate has positive discovery evidence. LAN device
identification is a separate, best-effort enrichment phase:

```text
Discovery -> confirmed online device -> identification / enrichment
```

Reverse DNS, mDNS, and SSDP failure, timeout, or absence must never remove a
device, turn it offline, change discovery evidence, alter the tested scan
range, or create additional History records. The frozen Core remains:

- system reachability plus TCP **connect-success only** evidence;
- 500 ms reachability timeout, 250 ms TCP timeout, six fallback ports, and 32
  bounded host workers;
- automatic/custom IPv4 range validation, RFC1918 and 254-host limits;
- Cellular/VPN refusal, cancellation, network-change handling, sorting, and
  one completed LAN_SCAN History record.

Current integration point: `LanDevice` already has nullable `hostName`, while
`LanDeviceEvidence` records discovery only. Identification must be represented
by an independent, source-labelled contract rather than by overloading an open
port or failed probe into a name.

## 2. Current Android and project baseline

| Item | Current repository baseline | Decision for v0.3 planning |
| --- | --- | --- |
| minSdk | 31 (Android 12) | Support remains required. |
| compileSdk / targetSdk | 36 (Android 16) | Do not upgrade in this work. |
| Java / Kotlin toolchain | JDK 17 / Kotlin 2.2.20 | No build change. |
| Gradle / AGP | 8.13 / 8.13.2 | No build change. |
| Existing local-network declarations | INTERNET, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE | No Manifest change in this task. |
| Device evidence | `NetworkContext`, `LanScanSession`, `LanDevice` and `HistoryRecorder` | Enrichment starts only after discovery has produced devices. |

`NetworkContext` gives the current type, IPv4 address/prefix, selected gateway,
DNS servers, VPN state, validation state, and interface name. It does **not**
retain an Android `Network` object. Future enrichment must capture a network
identity at scan start and obtain/bind the actual `Network` in its Android data
adapter; it must not infer a target interface from a display string alone.

## 3. Permissions and Android local-network policy

### 3.1 Current manifest audit

| Capability | Current declared permission | Android 12 | Android 13-16 default | Android 17+ when targeting 37 |
| --- | --- | --- | --- | --- |
| Network context / active-network observation | ACCESS_NETWORK_STATE; ACCESS_WIFI_STATE only for current Wi-Fi signal data | Available subject to normal platform availability | Available | ACCESS_NETWORK_STATE remains needed for relevant NSD overloads; local operations have the rules below. |
| Existing LAN IPv4 reachability/TCP scan | INTERNET | Local access is implicitly available | Local access remains open while targeting 36 | Broad scanner traffic requires runtime ACCESS_LOCAL_NETWORK. |
| Reverse DNS through the configured system resolver | INTERNET | Best effort; no name is normal | Best effort; test with Android 16 local-network opt-in | A DNS query to a local DNS server on port 53 is documented as an exception, but Task 038 must test the actual resolver path and not assume every reverse-name strategy is exempt. |
| mDNS through NsdManager | ACCESS_NETWORK_STATE already declared | Framework NSD is available | Framework NSD is available; extension level changes behavior | Broad discovery requires ACCESS_LOCAL_NETWORK; the picker only grants access to a user-selected service and cannot satisfy whole-LAN enrichment. |
| SSDP M-SEARCH / UDP replies | INTERNET | Local UDP available | Local UDP available while targeting 36 | Sending multicast and receiving unicast/multicast local UDP require ACCESS_LOCAL_NETWORK for broad operation. |
| Legacy Wi-Fi multicast receive support | Not currently declared | Manual MulticastLock requires CHANGE_WIFI_MULTICAST_STATE | Conditional; see section 6 | Same Wi-Fi behavior plus Android 17 local-network policy. |

Android's current guidance states that an app targeting SDK 36 or lower has
implicit local-network access through INTERNET. Android 16 exposes the future
restriction only as an opt-in compatibility test: enabling
`RESTRICT_LOCAL_NETWORK` and rebooting restricts app-process local sockets;
temporary restoration during that test uses NEARBY_WIFI_DEVICES. This is a
developer compatibility exercise, not a permission required by current
NetworkToolbox users. Framework APIs such as NsdManager run outside the app
process and are not affected by that Android 16 opt-in in the same way.

The opt-in must be part of real-device validation for Tasks 038-040:

```text
adb shell am compat enable RESTRICT_LOCAL_NETWORK com.networktoolbox
adb reboot
```

Restore the device state after the test. Do not add a temporary Android 16
permission flow to production solely for this experiment.

### 3.2 Android 17 forward-compatibility decision

Android 17 enforces Local Network Protection for apps targeting API 37 or
higher. Direct TCP/UDP local traffic, multicast/broadcast, `.local` lookup,
and NsdManager discovery are covered. Android provides a picker route for a
specific service, but a scanner must inspect the user-selected LAN rather than
one picker-selected device. Therefore the future target-37 decision is:

> Request broad `ACCESS_LOCAL_NETWORK` only when the project deliberately
> moves to target 37 and the user starts a LAN scan/enrichment operation.

This is a forward compatibility requirement, **not** an instruction to add the
permission, change targetSdk, or request it now. Task 039 must provide a
graceful unavailable/denied state on target 37; it must not silently substitute
the system service picker for whole-LAN discovery.

## 4. Identification data and source semantics

The following is a proposed domain contract. Exact package placement belongs to
the implementation task; it must not be a second discovery engine.

```text
LanDeviceIdentity
├─ ipAddress
├─ displayName?                 // aggregation result, never an IP guess
├─ hostname?
├─ services: List<LanIdentifiedService>
├─ manufacturer?
├─ model?
└─ observations: List<IdentificationObservation>

IdentificationObservation
├─ source: LOCAL_DEVICE | GATEWAY | REVERSE_DNS | MDNS | UPNP | UNKNOWN
├─ status: RESOLVED | NO_RESULT | TIMEOUT | FAILED | CANCELLED
├─ value? / structured fields
├─ observedAt
└─ scanRunId / network identity

LanIdentifiedService
├─ source: MDNS | UPNP
├─ instanceName?
├─ serviceType?
├─ hostAddresses
├─ port?
└─ txtAttributes?               // bounded and never treated as a credential store
```

`LOCAL_DEVICE` and `GATEWAY` remain deterministic role badges, not invented
host names. A provisional display priority is: validated UPnP Friendly Name,
then mDNS instance name matched to the discovered IPv4 address, then reverse
DNS hostname, then numeric IP. The precise conflict/merge presentation is
deferred to Task 041 after real-device data exists. Raw errors, unbounded TXT
values, credentials, and failed-probe text are not user-facing identity data.

## 5. Reverse DNS / hostname enrichment — conditional GO

Android's `InetAddress.getCanonicalHostName()` is documented as a best-effort
FQDN lookup using the system-configured name service. It may return the textual
address instead of a name. A missing PTR record is common on private LANs, so
the following mapping is required:

| Observation | Identification status | User meaning |
| --- | --- | --- |
| Non-IP canonical name returned | RESOLVED | Display as “主机名（反向 DNS）”. |
| Canonical name equals input IP | NO_RESULT | No name available; do not show an error. |
| Bounded caller wait elapsed | TIMEOUT | Not shown as offline or as a device error. |
| Resolver failure | FAILED | Keep device and show no name by default. |
| Scan/new scan/network change cancelled it | CANCELLED | Discard from the completed session. |

**Recommendation: CONDITIONAL GO for Task 038.** Use the platform/system
resolver first; do not add a third-party library or add PTR to the current DNS
feature as an incidental change. Run the blocking operation on `Dispatchers.IO`
with a maximum of four in-flight attempts and an approximately 1.5 second
session-visible timeout per device. Do not call it from UI or delay initial
device presentation.

`getCanonicalHostName()` has no caller-controlled DNS timeout and a Kotlin
timeout does not guarantee that the underlying blocking resolver will stop.
Task 038 must therefore stop admitting new work on cancellation, tag each
result with the scan generation/network identity, and discard late completion.
It should use the captured Android network where platform APIs make that
binding reliable; otherwise it must document the system-resolver limitation.
An eventual raw PTR query based on `DnsResolver` would be a separate DNS-core
decision, not a hidden requirement of Task 038.

Reverse DNS is not mDNS: a `.local` name is an mDNS namespace and must not be
invented from a failed PTR lookup. VPN, Private DNS, and Fake-IP DNS can affect
the system resolver; every returned value still carries the `REVERSE_DNS`
source and must be treated as a best-effort observation.

## 6. mDNS / Bonjour — framework-first recommendation

**Recommendation: GO for a bounded, framework-first Task 039, subject to
real-device validation.** Use `NsdManager`/DNS-SD, not a third-party mDNS
library and not manual packet parsing in phase 1. NsdManager is asynchronous,
uses mDNS/DNS-SD, and is updated through SDK Extensions rather than Android
version alone.

NsdManager discovers instances of a specified service type; it is not a
portable “enumerate every Bonjour device” API. Do not rely on
`_services._dns-sd._udp` as the primary implementation strategy. Task 039
should start with a small, explicitly reviewed set of useful types and a
configuration seam, for example `_http._tcp`, `_ipp._tcp`, `_printer._tcp`,
and `_smb._tcp`; any AirPlay, SSH, or other type requires real-device evidence
before inclusion. This is service observation, not port or device
fingerprinting.

For Android 14+ / T Extension 22, prefer
`registerServiceInfoCallback(DiscoveryRequest, Executor, ServiceInfoCallback)`
to deprecated one-shot `resolveService`, because it combines discovery and
resolution and keeps service details fresh. API 31-33 compatibility may use
the legacy discovery-plus-resolution adapter. On platforms with
`DiscoveryRequest.Builder.setNetwork` or the Network overload, bind discovery
to the captured eligible Wi-Fi/Ethernet `Network`; earlier API paths must
cancel/discard results on network identity change rather than merge ambiguous
callbacks.

One scan starts one scan-scoped mDNS session, never one discovery session per
host. Use a shared 4 second observation window with at most three concurrent
service-type browses; stop each discovery listener and outstanding resolution
in `finally`. Merge a service into a `LanDevice` only when a resolved host
address matches a discovered device. Keep an unmatched service observation
separate rather than crediting it to an arbitrary IP.

### Wi-Fi MulticastLock

NsdManager's current documentation makes this version-sensitive:

- before T Extension 7 (Android 12 and below, plus Android 13 without that
  extension), a foreground app must manually acquire a
  `WifiManager.MulticastLock` to receive mDNS packets;
- from T Extension 7, the system manages foreground multicast reception;
- background acquisition should be avoided because multicast reception costs
  battery.

Task 039 must check the extension version, not assume an Android release
number. If API-31/older-extension support is retained, it needs a narrowly
scoped, non-reference-counted lock released in `finally` and the normal
`CHANGE_WIFI_MULTICAST_STATE` declaration. This task does not add it. The
alternative is to mark mDNS unavailable on those devices; Task 039 must choose
explicitly after the Android 12 real-device test.

**mDNS risk: MEDIUM.** Coverage depends on advertised service types, access
point multicast behavior, platform/extension lifecycle, and address-to-device
matching. No response means “no service observed in the bounded window”, not
“device has no name” or “device is offline”. Wi-Fi/Ethernet only; Cellular and
VPN retain the scanner's existing refusal policy.

## 7. SSDP / UPnP — constrained GO after mDNS validation

Android supplies no comparable high-level SSDP discovery API. **Recommendation:
CONDITIONAL GO for Task 040 only after mDNS lifecycle validation.** Phase 1
uses one bounded `DatagramSocket` M-SEARCH exchange rather than a persistent
`MulticastSocket` listener:

- bind the unconnected datagram socket to the captured `Network` before use;
- send one standards-compatible M-SEARCH to `239.255.255.250:1900` with
  `MAN: "ssdp:discover"`, `ST: ssdp:all`, and `MX: 1`;
- accept unicast replies for a fixed 2 second receive window, then close;
- deduplicate first by USN, then LOCATION, then a documented fallback key;
- do not listen for persistent NOTIFY advertisements in phase 1.

M-SEARCH reply traffic is unicast, so phase 1 should not acquire a Wi-Fi
MulticastLock merely for this request/reply path. A later multicast NOTIFY
listener would require a separate MulticastLock and battery review. Android 17
still treats the outgoing multicast and any local UDP response as broad local
network operations requiring ACCESS_LOCAL_NETWORK.

SSDP headers are untrusted network input. Parse them case-insensitively with
strict line/header/packet size limits; tolerate duplicate replies but never use
a malformed response as device evidence. `LOCATION` is a hint only.

### Safe UPnP device-description retrieval

Fetch only a validated LOCATION returned in the current, bounded SSDP session:

- allow `http` only in phase 1; reject unexpected schemes, user-info, and
  malformed hosts/ports;
- do not follow redirects (`HttpURLConnection` follows redirects by default,
  so explicitly disable instance redirects);
- require a destination that is the responder IP or a verified address of the
  same discovered local device; bind the connection to the captured network;
- GET only; no cookies, credentials, authentication, POST, probing, or
  directory traversal;
- use explicit connect/read timeouts (recommended 1.5 s each), a 256 KiB
  maximum body, and close/disconnect on cancellation;
- parse with a streaming pull parser, reject DTD/DOCTYPE/entity processing and
  impose depth/text limits; do not use an unrestricted DOM parser;
- extract only openly advertised `friendlyName`, `manufacturer`, `modelName`,
  `modelNumber`, `deviceType`, and `UDN`.

UPnP XML must be treated as untrusted LAN content. A parse/fetch failure leaves
the device discovered but unidentified; it never triggers broader web access.

**SSDP/UPnP risk: HIGH.** Multicast isolation, device-specific replies,
untrusted headers/XML, NETWORK binding, and misleading LOCATION URLs require
the above limits and real-device coverage before enabling it by default.

## 8. Scheduling, cancellation, network changes, and History

The enrichment coordinator is a child of one LAN scan run:

```text
Completed discovery session
  -> show devices immediately
  -> launch bounded enrichment with scanRunId + captured network identity
  -> apply only matching, current-generation observations
```

Recommended initial budgets:

| Work | Bound | Cancellation and cleanup |
| --- | --- | --- |
| Reverse DNS | 4 concurrent; 1.5 s user-visible budget per host | Stop admitting work; discard a late non-interruptible resolver result. |
| mDNS | 1 scan session; shared 4 s window; <=3 service types at once | Stop every listener/resolution; release conditional MulticastLock. |
| SSDP | 1 M-SEARCH; MX 1; 2 s receive window | Close socket on cancel/network change. |
| UPnP descriptions | <=2 fetches concurrently; 1.5 s connect/read limits | Disconnect and discard stale response. |

Cancellation, a new scan, screen disposal, or any existing scanner network
identity change cancels the parent supervisor, closes sockets/releases locks,
stops NSD listeners, and prevents new observations from reaching UI. The old
scan's devices may remain as its partial on-screen snapshot, but no
identification result may cross into a new network or scan generation.

The first implementation stage does **not** change Room. It continues to write
one LAN_SCAN record only for a completed scan. Task 041 may extend the existing
versioned `detailJson` with a compact, bounded subset of successfully
aggregated identity sources after it defines parsing/UI compatibility. Do not
persist raw mDNS TXT content, full SSDP headers/XML, failed resolver details,
or a separate record for each enrichment operation.

## 9. Test and real-device matrix

No Android device is connected to this development host during this baseline
task, so none of the following is claimed as tested on Sony Xperia 1 VII.

### Task 038 — Reverse DNS

- Fake resolver: resolved hostname, canonical IP/no PTR, timeout, failure,
  cancellation, late completion, and source semantics.
- Existing scan regression: identified and unidentified devices keep identical
  discovery ordering/evidence and still create exactly one History record.
- Xperia / Android 16: ordinary Wi-Fi, OpenClash/Fake-IP environment, VPN
  refusal, and Android 16 `RESTRICT_LOCAL_NETWORK` compatibility opt-in.

### Task 039 — mDNS

- Fake NsdManager adapter: started/found/lost/resolve failure, duplicate
  services, address matching, stop lifecycle, network change, and stale
  callback rejection.
- Xperia / Android 16: ordinary HomeLab Wi-Fi; an mDNS-capable device such as
  printer, media endpoint, macOS/iOS, or HomeLab service; Android 12/13
  extension behavior; AP multicast isolation; cancellation; Wi-Fi-to-cellular
  transition; OpenClash and VPN conservative paths.

### Task 040 — SSDP / UPnP

- Fixed UDP response fixtures: case-insensitive headers, duplicates, malformed
  lines, missing/unsafe LOCATION, packet-size cap, deduplication, cancellation.
- Fixed XML fixtures: allowed fields, over-size input, DTD/DOCTYPE rejection,
  deep nesting, malformed XML, no redirects, responder-address validation.
- Xperia / Android 16: router/HomeLab/TV or other known UPnP endpoint; ordinary
  Wi-Fi versus AP multicast isolation; cancellation and network switch.

### Android 17 forward test plan (only after a deliberate target-37 branch)

- ACCESS_LOCAL_NETWORK denied/granted/revoked for scanner, reverse DNS, SSDP,
  and mDNS broad discovery;
- system picker behavior for one NSD service, explicitly confirming it does
  not replace broad LAN scanning;
- no permission request before a user starts the selected local operation;
- network change, backgrounding, cancellation, and extension-version paths;
- regressions to Cellular/VPN refusal and one-record History behavior.

## 10. Task 038 implementation specification

Task 038 should add only a `ReverseDnsEnrichmentEngine` abstraction plus an
Android system-resolver adapter and deterministic fake tests. It consumes
already completed `LanDevice` items, emits progressive source-labelled
observations, uses the bounded scheduling/cancellation contract above, and
does not change `LanDiscoveryEngine`, TCP semantics, range logic, or Room.

It must not add a new `PingHistoryRepository`, `DnsHistoryRepository`, service
scanner, user permission request, UI page, manual DNS server, or a cloud lookup.
The UI aggregation and any History-detail addition remain Task 041 work.

## 11. Final technical decisions and references

1. Reverse DNS is **conditional GO**: useful best-effort enrichment, but no
   PTR is normal and the system API cannot provide a strict resolver timeout.
2. mDNS is **framework-first GO** with `NsdManager`, SDK Extension checks,
   bounded known service types, and real-device validation.
3. SSDP/UPnP is **conditional GO after mDNS validation**, with one bounded
   request/reply exchange and a hardened local-only XML fetch.
4. No third-party library is recommended: Java/Android platform APIs cover the
   baseline. `docs/OSS_RESEARCH.md` has no completed compatible dependency
   research and authorizes no adoption.
5. Android 17 Local Network Protection is a future implementation gate, not a
   new v0.3 product decision and not a current Manifest change.

Primary Android references (reviewed 2026-08-31):

- [Local network permission](https://developer.android.com/privacy-and-security/local-network-permission)
- [Android 17 behavior changes](https://developer.android.com/about/versions/17/behavior-changes-17)
- [NsdManager API reference](https://developer.android.com/reference/android/net/nsd/NsdManager)
- [DiscoveryRequest API reference](https://developer.android.com/reference/android/net/nsd/DiscoveryRequest)
- [Network API reference](https://developer.android.com/reference/android/net/Network)
- [WifiManager.MulticastLock API reference](https://developer.android.com/reference/android/net/wifi/WifiManager.MulticastLock)
- [InetAddress API reference](https://developer.android.com/reference/java/net/InetAddress)
- [HttpURLConnection API reference](https://developer.android.com/reference/java/net/HttpURLConnection)

No new product decision is required at this stage. The only future decision
gate is the target-37 broad-local-network permission UX; it becomes actionable
only when the project deliberately upgrades targetSdk.
