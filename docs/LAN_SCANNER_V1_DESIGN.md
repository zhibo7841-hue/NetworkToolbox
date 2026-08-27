# NetworkToolbox LAN Scanner v1 Design

## Status and scope

This document is a design and current-state audit for a future LAN Scanner v1. It does not add Kotlin, Compose, Gradle, permissions, navigation, a database migration, or a scanner implementation.

LAN Scanner remains a confirmed future product direction. This document narrows the first implementation to a safe and explainable local IPv4 discovery workflow; it does not remove IPv6, mDNS, UPnP, vendor identification, or service discovery from the longer-term product direction.

The repository's `docs/OSS_RESEARCH.md` is still a placeholder. It contains no reviewed project, license, implementation, or Android restriction conclusion. No OSS project or dependency is adopted by this design.

## 1. Current implementation audit

### 1.1 Reusable network context

The current shared source is:

```text
NetworkRepository.observeNetworkContext()
        ↓
AndroidNetworkRepository
        ↓
ConnectivityManager + NetworkCapabilities + LinkProperties
        ↓
NetworkContext
```

`NetworkContext` currently exposes:

- `connectionType`;
- `activeNetworkAvailable` and `validated`;
- the selected IPv4 and IPv6 addresses;
- all observed IPv6 addresses;
- `ipv4PrefixLength`;
- one selected `gateway`;
- configured DNS server addresses;
- `interfaceName`;
- `vpnActive`;
- optional Wi-Fi name, signal level, and Private DNS fields.

`AndroidNetworkRepository` reads the active network's `LinkProperties`. Its default-route selection is already centralized in `DefaultGatewaySelector`: IPv4 candidates are preferred regardless of `LinkProperties.routes` order. The host-address conversion currently strips an IPv6 `%scope` suffix, so an IPv6 link-local gateway cannot currently be probed reliably by the existing Ping path. LAN Scanner v1 must not use that unscoped IPv6 value as an IPv4 LAN target.

The current repository is an observation provider, not a remote-device inventory. It does not expose ARP, neighbor-cache, MAC, host-name, or service lists.

### 1.2 Existing reachability and TCP capabilities

The current Ping v2 chain is:

```text
PingSessionEngine
        ↓
PingProbe
        ↓
AndroidPingSessionProbe
        ↓
InetAddress.getAllByName + InetAddress.isReachable
```

The Android adapter records `SYSTEM_REACHABILITY`; it does not claim that the result is confirmed ICMP. It supports protocol selection and cancellation, but it does not bind a probe to a scanner-specific `Network` or interface. A LAN adapter may reuse the session engine contract only after the implementation verifies that the selected active network is used for each probe.

The current TCP chain is:

```text
TcpPortChecker
        ↓
AndroidTcpPortChecker
        ↓
Socket.connect(InetSocketAddress(host, port), timeout)
```

It provides connection timing and distinguishes timeout, connection refusal, and other errors. It is a single-host/single-port checker, not a port scanner. LAN Scanner v1 may reuse this capability through a bounded adapter, but a TCP result must remain evidence about that port rather than a service or device identity claim.

### 1.3 History and navigation

The current history path is the generic local path:

```text
completed use case
        ↓
HistoryRecorder
        ↓
HistoryRepository / Room
        ↓
History UI
```

`HistoryRecord` and `HistoryEntity` use generic `type`, `title`, `summary`, and `detailJson` fields. There is no `LAN_SCAN` history factory today. The Tools screen also has no LAN Scanner entry. Both are expected implementation work for a later task; this design does not change either one.

The existing product rule remains: one complete diagnostic operation creates one history record. Internal probes must not create individual Ping or TCP records.

### 1.4 Current Android permissions

The current manifests declare:

- `INTERNET` in `core:network`;
- `ACCESS_NETWORK_STATE` in `app`;
- `ACCESS_WIFI_STATE` in `core:network`.

There is no location permission, `NEARBY_WIFI_DEVICES`, multicast permission, or storage permission. An IPv4 socket probe and the reading of `ConnectivityManager` state do not justify adding a Wi-Fi identity or location permission. This design keeps the current permission set for v1.

## 2. Android 12–16 capability and limitation audit

The following is based on the current Android API documentation and the repository implementation. “Available” means usable as an observation or a bounded socket operation; it does not mean that every device exposes a value in every network state.

| Data or mechanism | Android/API assessment | v1 decision |
| --- | --- | --- |
| Active network and transport | `NetworkCapabilities` exposes transport types such as Wi-Fi, cellular, Ethernet, and VPN. The existing repository already normalizes these. | Reuse `NetworkContext`; only Wi-Fi/Ethernet are eligible for the default LAN scan. |
| IPv4 address and prefix | `LinkProperties.getLinkAddresses()` exposes link addresses and prefix length. | Required to derive the range; no hardcoded private subnet. |
| Default route/gateway | `LinkProperties` exposes routes; the repository already filters default routes and prefers IPv4. A gateway can still be absent or ambiguous. | Use the existing selected IPv4 gateway for a deterministic marker; never assume its presence. |
| IPv6 addresses | `LinkProperties` can expose one or more IPv6 addresses. Link-local addresses are interface-scoped. | Do not enumerate IPv6 address space in v1. Preserve IPv6 for the shared context and future discovery mechanisms. |
| Configured DNS servers | `LinkProperties.getDnsServers()` exposes DNS addresses configured on the link. | Not a device-discovery source; do not turn DNS names into device identities. |
| Interface and network binding | `LinkProperties` can expose an interface name, and `Network.getSocketFactory()` can create sockets whose traffic is sent over a specific `Network`. | A future scanner adapter should bind or otherwise verify probes against the captured network. The current generic checker does not yet guarantee this. |
| Remote ARP/neighbor table | Android 10+ prevents ordinary apps from accessing `/proc/net`, which includes network-state information. The current public repository has no neighbor-table provider. | Do not make `/proc/net/arp`, shell commands, root, or hidden APIs a v1 dependency. |
| MAC address | A public `NetworkInterface` hardware address is the local interface address, not a remote host's ARP MAC. Android also documents restricted interface information and modern Wi-Fi MAC privacy behavior. | Remote MAC is optional evidence and normally `未知`; never use zero-filled or guessed MAC values. No root. |
| System reachability | Existing `InetAddress.isReachable` is a best-effort system reachability result and is deliberately labeled `SYSTEM_REACHABILITY` in this project. | Use as one evidence source, never as the only online/offline rule and never label it ICMP without proof. |
| TCP connect | Standard sockets can provide a bounded response signal. A success or a connection refusal is evidence that the TCP endpoint responded; it is not service identification. | Use a very small fallback port set only after reachability gives no positive evidence. |
| mDNS / NSD | Android `NsdManager` provides service discovery, with lifecycle and multicast considerations that vary by platform extension and foreground state. | Later LAN Scanner v1.x; not part of the first IPv4 host-discovery core. |
| UPnP / SSDP | No current project adapter or reviewed OSS implementation exists. It would add multicast, parsing, device-behavior, and lifecycle complexity. | Later phase; not a v1 dependency. |

Relevant primary references:

- [Android `LinkProperties` API](https://developer.android.com/reference/android/net/LinkProperties)
- [Android `NetworkCapabilities` API](https://developer.android.com/reference/android/net/NetworkCapabilities)
- [Android `Network` API](https://developer.android.com/reference/android/net/Network)
- [Android `NetworkInterface` API](https://developer.android.com/reference/java/net/NetworkInterface)
- [Android privacy changes, including `/proc/net` and MAC restrictions](https://developer.android.com/about/versions/10/privacy/changes)
- [Android Wi-Fi permission guidance](https://developer.android.com/develop/connectivity/wifi/wifi-permissions)
- [Android `NsdManager` API](https://developer.android.com/reference/android/net/nsd/NsdManager)
- [Android local-network permission guidance](https://developer.android.com/privacy-and-security/local-network-permission)

### Android 16 and later permission boundary

The current app targets the Android 16-era SDK in this repository. Android's current local-network guidance states that apps targeting SDK 36 or lower use the existing open local-network behavior through `INTERNET`; mandatory local-network protection is described for apps targeting Android 17/API 37 or higher. Therefore, v1 does not add `ACCESS_LOCAL_NETWORK` to the current Android 12–16 baseline. The Android 17 target and permission policy must be reviewed before a future target-SDK change.

`NEARBY_WIFI_DEVICES` is relevant to selected Wi-Fi management/discovery APIs, not a blanket requirement for enumerating IPv4 addresses with sockets. Requesting it merely to obtain SSID, BSSID, or remote MAC data would violate the current minimum-permission principle. If a future mDNS/NSD design needs a platform-specific permission or multicast behavior, it must be reviewed separately.

## 3. LAN Scanner v1 product boundary

### v1 objective

LAN Scanner v1 should answer a limited question:

> Which hosts on the current local IPv4 link produced reliable network evidence during this scan?

It should not claim to build a complete device inventory. A discovered row should contain the evidence that was actually observed and should allow unknown values.

### v1 included

- Current-link IPv4 range calculation from `NetworkContext`.
- Safe range-size validation and a bounded default for large prefixes.
- IPv4 host enumeration for Wi-Fi or Ethernet only.
- Progressive, cancellable host discovery.
- System reachability as one evidence source.
- Minimal TCP fallback on ports 443 and 80, only after no positive reachability evidence.
- Deterministic local-device and IPv4-gateway markers.
- Numeric IPv4 ordering.
- A single local history summary after a completed scan.

### v1 not included

- IPv6 `/64` brute-force enumeration.
- Root, shell, `/proc/net/arp`, or hidden Android APIs.
- Complete ARP/NDP inventory.
- Port ranges, broad port scanning, banners, or service-version detection.
- mDNS/Bonjour or UPnP/SSDP discovery.
- Reverse-DNS naming as a required step.
- Online MAC/OUI lookups.
- Automatic vendor identification without an approved local database.
- Cellular-network scanning.
- Blind scanning of a VPN-assigned range.

This is a staged implementation boundary, not a removal of IPv6 or later discovery capabilities from the product roadmap.

## 4. Scan-range calculation and safety policy

### 4.1 Source of truth

The scanner must take a snapshot of the same `NetworkContext` used by Home and Diagnostic. It must use:

- `ipv4Address`;
- `ipv4PrefixLength`;
- `connectionType`;
- `interfaceName`, when available;
- `vpnActive`;
- the selected IPv4 gateway, when available.

No `192.168.1.0/24`, `10.0.0.0/24`, or other hardcoded default is allowed.

The future pure-domain range calculator should produce a value equivalent to:

```text
LanScanRange
├─ networkAddress
├─ broadcastAddress
├─ startAddress
├─ endAddress
└─ hostCount
```

For conventional IPv4 LAN prefixes `/0` through `/30`, `networkAddress` and `broadcastAddress` are excluded from host candidates. `/31` point-to-point semantics and `/32` host routes should not be treated as ordinary LANs; v1 automatic scanning returns “不支持自动扫描此网络范围” unless a later explicit policy covers them.

### 4.2 Default maximum

The v1 default maximum is **one `/24` block, at most 254 host addresses**. This means:

- `/24` or a smaller subnet: scan the computed host range directly;
- `/25`, `/26`, and smaller: scan their computed host range directly;
- `/23` or larger: do not scan the full subnet automatically;
- for a larger prefix, offer or internally derive the `/24` block containing the current IPv4 address as the safe default window, and explain that it is only a bounded view of the attached network;
- do not silently scan millions of addresses.

The first implementation should not add an unrestricted arbitrary-range editor merely to bypass this guard. A future user-selected range requires an explicit product and safety review.

As an additional safety check, automatic scanning should be limited to IPv4 ranges that are clearly local/private or link-local. A non-private routed range should not be enumerated automatically because a prefix alone does not prove that it is a harmless LAN.

### 4.3 Network changes

The scanner captures the initial context and checks the current context during the run. If the interface, IPv4 address, prefix, or active network identity changes, it must:

1. cancel or stop scheduling new host probes;
2. retain already observed results as a partial session;
3. report `NETWORK_CHANGED` / “网络发生变化”;
4. avoid presenting a mixed-network result as a strong inventory;
5. not save a completed-history record unless the product later defines partial-scan history.

## 5. Host-discovery evidence and decision policy

### 5.1 v1 evidence order

The initial implementation should use this bounded sequence for each candidate IP:

```text
Candidate IPv4
      ↓
System reachability probe
      ↓ only if no positive evidence
TCP fallback: 443, then 80
      ↓
Evidence aggregator
      ↓
ONLINE evidence or no discovery result
```

The existing NetworkContext is also used for deterministic facts:

- the local IPv4 address identifies the local device;
- the selected IPv4 default gateway identifies the gateway, when present.

Those two markers do not depend on MAC, host name, or a probe response.

### 5.2 What counts as evidence

| Evidence | Interpretation |
| --- | --- |
| System reachability succeeds | Positive reachability evidence; record method as system reachability. |
| TCP connect succeeds on 443 or 80 | Positive TCP endpoint evidence; record the port. Do not infer the service's identity. |
| TCP returns connection refused | The target produced a TCP refusal response, which is positive evidence that this TCP path responded; the port is not open. |
| Timeout | No positive evidence within the bound; do not call the device offline with certainty. |
| Other socket/system error | No positive evidence; retain a technical reason only in advanced details. |
| Remote MAC/neighbor observed by a future provider | Additional evidence, but not required for v1. |

An address with no positive evidence is omitted from the discovered-device list rather than shown as definitively offline. This avoids presenting a firewall or ICMP policy as proof that a host is absent.

### 5.3 Do not overstate Ping

The scanner must display “系统可达性” or the actual adapter method. It must not transform a system reachability result into “ICMP successful”. If system reachability is unavailable, TCP fallback may still discover a host; the result should say that it was discovered by TCP evidence.

## 6. TCP fallback design

TCP fallback is recommended for v1, but it must remain a discovery fallback rather than a port scanner.

### Recommendation

- Ports: **443 and 80 only**.
- Order: try 443 first, then 80 only if needed.
- Trigger: only after the reachability probe gives no positive result.
- Stop: stop after the first positive TCP response.
- Result: store the responding port and `TCP_CONNECT` evidence, not a service name.
- Do not probe 22, 53, 139, 445, or arbitrary user port lists by default. Those ports are either less universal, more policy-sensitive, or too easy to reinterpret as service detection.

Two fallback ports are a compromise: one port can be closed while the host is active, while a large port set would create a port scanner and increase device/AP logs and power use. A future port policy must be measured on the target HomeLab and tested against devices that refuse ICMP.

## 7. Concurrency, timeout, cancellation, and progressive results

### Default policy

- Default concurrency: **16 in-flight host probes**.
- Hard implementation ceiling for the first version: **32**, even if a future UI exposes a setting.
- Do not create one unbounded coroutine per address.
- Use structured concurrency and a bounded semaphore/worker pool.
- Use a short, explicit reachability timeout and an even bounded TCP fallback budget; the exact values should be tuned by instrumented and real-device tests rather than hidden in UI code.
- Run socket work on an I/O dispatcher inside the platform adapter.

Why 16: it is fast enough to make a `/24` progressive scan useful while limiting phone wakeups, Wi-Fi AP pressure, bursty connection logs, and low-end device load. 32 can be a later measured option; 64 is not the v1 default because scan speed alone is not the product objective.

### Session state

The future core should expose a small state contract:

```text
IDLE
SCANNING(scannedHosts, totalHosts, discoveredDevices)
COMPLETED(result)
CANCELLED(partialResult)
ERROR(message)
```

Each completed host should be able to publish a progressive event. `scannedHosts` must represent actual finished candidates, not a timer. Cancellation must stop queued work and propagate to active probes; no background scan may continue after the use case returns.

## 8. MAC, device name, and vendor strategy

### MAC address

Android 10+ privacy and `/proc/net` restrictions make a remote MAC unreliable for an ordinary, non-root application. `NetworkInterface.getHardwareAddress()` is about an interface accessible to the app, not a supported remote-ARP inventory API; it can also return `null` when unavailable.

v1 behavior:

- `macAddress` is nullable;
- “未知” is a valid result;
- never use `00:00:00:00:00:00` as a placeholder;
- never require root or shell access;
- never infer a MAC from an IP, host name, or port.

If a later Android/platform adapter can provide a neighbor observation without privileged access, it may be added as optional evidence with an explicit source field. It must not become a required discovery path.

### Device name

v1 does not need to block the scan on name resolution. The recommended first-version behavior is:

- local device: deterministic label “本机”;
- selected IPv4 gateway: deterministic label “网关”;
- other devices: show the numeric IP and leave `hostName` unknown;
- do not name a host “Windows PC”, “Router”, or similar from an open port alone.

Future name-source priority, each recorded with its actual source, is:

1. mDNS service name;
2. UPnP friendly name;
3. explicitly bounded reverse DNS, if product value outweighs latency and privacy cost;
4. numeric IP fallback.

### Vendor/OUI

Vendor identification is not a v1 runtime capability. A future offline OUI database would require a separate review of source provenance, Apache-compatible distribution terms, package size, update process, and stale-data behavior. Online MAC lookups are explicitly excluded by the local-first principle. No OUI database is downloaded or selected in this task.

## 9. IPv4/IPv6, mDNS, and UPnP phase boundary

### IPv4-first answer

Yes. LAN Scanner v1 is IPv4-first because the current NetworkContext supplies a usable IPv4 address/prefix and IPv4 host enumeration has a bounded, understandable candidate set.

IPv6 is not removed from the product. IPv6 LAN discovery should use Neighbor Discovery, mDNS, or another discovery mechanism that observes actual addresses. It must not enumerate an IPv6 `/64` by brute force; the address space is not a practical scan range.

### Later phases

- **v1 core:** IPv4 host discovery with system reachability and bounded TCP fallback.
- **v1.x:** mDNS/NSD service discovery and reliable service-name metadata, with a separate multicast and lifecycle design.
- **Later:** UPnP/SSDP metadata, IPv6 neighbor/service discovery, optional device details, and carefully reviewed vendor data.

This ordering is consistent with the current `V0.2_PLAN.md`, which records LAN Scanner as research before direct implementation. It does not alter the product roadmap.

## 10. Proposed core data model

The first implementation should keep the model small and evidence-oriented:

```text
LanScanRange
├─ networkAddress
├─ broadcastAddress
├─ startAddress
├─ endAddress
└─ hostCount

LanScanSession
├─ networkSnapshot
├─ range
├─ status
├─ startedAt
├─ finishedAt
├─ scannedHosts
├─ totalHosts
├─ discoveredDevices
└─ networkChanged

LanDevice
├─ ipAddress
├─ macAddress?
├─ hostName?
├─ isLocalDevice
├─ isGateway
├─ responseLatencyMs?
├─ discoveryEvidence
└─ lastSeen

DiscoveryEvidence
├─ method              // SYSTEM_REACHABILITY, TCP_CONNECT, future NEIGHBOR/MDNS/UPNP
├─ tcpPort?
├─ latencyMs?
└─ detail?
```

The model must not make MAC, name, vendor, or service metadata mandatory. `discoveryEvidence` is more useful than a single ambiguous `method` because a device may respond to both system reachability and TCP.

### Ordering

The default display order is:

1. gateway, if it is in the discovered/result set;
2. local device;
3. all other devices by numeric IPv4 value ascending.

The final sort key must convert the four octets to a 32-bit unsigned value (or an equivalent `Long`), not compare strings. Thus `10.0.1.2` precedes `10.0.1.11`, and `10.0.1.11` precedes `10.0.1.100`.

## 11. History design

### One scan, one record

A completed LAN Scan creates one history record through the existing `HistoryRecorder`. Internal reachability and TCP probes use no independent history path.

The future implementation will need a logical `LAN_SCAN` type and a factory/parser addition, but the current generic Room columns are sufficient. This is a data-contract extension, not a Room schema migration. This design task does not make that code change.

### Recommended stored content

The history card should contain a human-readable summary such as:

```text
局域网扫描                 今天 10:35
10.0.1.0/24
发现 12 台设备
```

The versioned `detailJson` may store a compact, bounded snapshot:

```json
{
  "schemaVersion": 1,
  "range": "10.0.1.0/24",
  "scannedHosts": 254,
  "discoveredCount": 12,
  "durationMs": 4800,
  "devices": [
    {"ipAddress": "10.0.1.1", "isGateway": true, "isLocalDevice": false},
    {"ipAddress": "10.0.1.20", "isGateway": false, "isLocalDevice": false}
  ]
}
```

Store only discovered device essentials and measured evidence needed for later display. Do not store raw failed-probe exception text for every address. The v1 range guard keeps the device list bounded to a single `/24`; if a future range policy grows, the history size limit must be revisited.

Only a completed, stable scan is saved in v1. A cancelled or network-changed partial scan remains on screen for the current session but is not presented as a complete historical inventory.

## 12. Cellular and VPN policy

### Cellular

Cellular is **not supported for automatic LAN scanning**. A cellular `NetworkContext` may expose an address and even a next-hop-looking route, but that does not prove access to a user LAN. The scanner should stop before enumeration and show:

> 当前网络为移动网络，暂不支持局域网扫描。

It must not scan carrier/private ranges such as `10.x.x.x` merely because Android reported them.

### VPN

VPN is conservative by default:

- if `vpnActive == true`, do not automatically scan the VPN-assigned range;
- show that the active network may be a VPN or tunnel environment;
- do not infer the VPN application, proxy product, or split-tunnel policy;
- do not silently fall back to an unverified underlying interface;
- allow a future implementation only after it can prove which `Network` and interface the scan targets and whether local LAN access is intended.

This avoids scanning a remote corporate/VPN network or reporting a VPN-assigned virtual range as the physical home LAN.

## 13. Future UI structure (design only)

The future entry belongs in the existing Tools destination, not as a new top-level navigation item. No navigation change is made in this task.

Suggested first screen:

```text
局域网扫描
扫描只在当前本地网络中进行，结果保存在设备本地，不会上传。

当前网络
Wi-Fi · 10.0.1.0/24

可扫描地址
254

[ 开始扫描 ]
```

During scanning:

```text
正在扫描
83 / 254
已发现 7 台设备

网关   10.0.1.1
设备   10.0.1.20
本机   10.0.1.206

[ 停止扫描 ]
```

Completed state should show the discovered count and duration. A future device-detail page may show IP, optional name/MAC/vendor, evidence, and services, but v1 should not place all future metadata into the first screen.

The UI must map evidence to understandable language:

- “系统可达性” rather than “ICMP” unless the adapter proves ICMP;
- “TCP 443 有响应” rather than “HTTPS 服务”;
- “MAC 未知” rather than a placeholder address;
- “未发现” rather than “设备离线” when probes provide no positive evidence.

## 14. Test matrix

### Pure domain tests

- `/24` produces 254 host candidates and excludes network/broadcast addresses.
- `/25` and `/26` produce the correct smaller range.
- a route list or candidate order does not affect IPv4 range arithmetic.
- a `/8` or `/16` is rejected or safely bounded to one `/24` window; it never expands to the full address space.
- `/31` and `/32` follow the explicit unsupported/special-range policy.
- IPv4 results use numeric ordering.
- local and gateway markers are derived from context, not MAC or name.
- a non-private automatic range is rejected or requires a separately approved policy.

### Fake-engine discovery tests

- system reachability finds a device;
- system reachability fails but TCP 443 finds a device;
- system reachability fails, TCP 443 is refused, and the host is recorded as TCP-responsive but the port is not open;
- system reachability and both fallback ports time out, so no definitive offline claim is produced;
- a discovered device can have unknown MAC and name;
- cancellation stops new work and retains partial results;
- bounded concurrency is respected;
- one completed scan produces one history record;
- internal Ping/TCP probes do not produce separate history records;
- an interface/address/prefix change during scanning produces a network-changed state and prevents complete-history persistence.

### Android/instrumented and real-device tests

The primary device remains Sony Xperia 1 VII / Android 16. The first real-network test matrix should include:

- HomeLab Wi-Fi `/24`;
- `/25` and `/26` test fixtures or pure-domain equivalents;
- Wi-Fi devices that do not answer system reachability but expose TCP 443 or 80;
- a large-prefix safety guard;
- no active network;
- cellular, confirming no scan starts;
- VPN, confirming conservative refusal or explicit unsupported state;
- cancellation during a real `/24` run;
- Wi-Fi-to-cellular or network reconnection during a run;
- numeric ordering and local/gateway labels.

Real-device tests must record the actual network type, range, duration, and observed evidence. They must not use a public Internet target as a substitute for a local discovery test.

## 15. Explicit design conclusions

1. **Is LAN Scanner v1 IPv4-first?** Yes. IPv4 host discovery is the first stable bounded capability. IPv6 remains a later discovery-protocol problem, not a removed scope item.
2. **What is the default maximum scan range?** One `/24` block, at most 254 host addresses. Larger prefixes are warned and bounded to the `/24` containing the current address rather than scanned in full.
3. **What evidence is used first?** Existing system reachability, then minimal TCP fallback, plus deterministic local/gateway facts from `NetworkContext`.
4. **Is TCP fallback used?** Yes, only after no positive reachability evidence and only as bounded host-discovery evidence.
5. **Which TCP ports?** 443 and 80. No port ranges and no broad service probe set.
6. **What is the default concurrency?** 16 in-flight host probes, with an implementation ceiling of 32 for the first version.
7. **How reliable is MAC on Android 16?** Remote MAC is not a reliable ordinary-app v1 field. It remains nullable/unknown; no root, `/proc/net`, or fabricated value.
8. **How far does device naming go in v1?** Only deterministic “本机” and “网关” labels; other names remain unknown and are shown by IP. mDNS/UPnP names are later.
9. **Are mDNS and UPnP in this implementation?** No. mDNS/NSD is a v1.x discovery enrichment phase; UPnP/SSDP is later. This task only designs the boundary.
10. **Is a LAN Scan saved to History?** Yes, one completed scan creates one compact local summary record through `HistoryRecorder`. No Room schema change is proposed.
11. **How are Cellular and VPN handled?** Cellular is not scanned. VPN is conservatively not auto-scanned until the target network/interface can be proven.
12. **Are new Android permissions required?** No for the v1 IPv4 socket/context design. Keep `INTERNET`, `ACCESS_NETWORK_STATE`, and existing `ACCESS_WIFI_STATE`; do not add Location or `NEARBY_WIFI_DEVICES` for MAC/SSID convenience.

## 16. Open implementation decisions for a later task

These are implementation details to confirm before code begins, not silently decided by this design task:

- the exact timeout budget for system reachability and each TCP fallback;
- how the scanner adapter binds sockets to the captured Android `Network`;
- whether the current `NetworkContext.gateway` is sufficient for the first result model or whether a separate IPv4 gateway field is needed;
- the concrete JSON serializer/parser used for the existing generic `detailJson` contract;
- the final wording and UI treatment for a bounded `/24` view of a larger subnet;
- the future `LAN_SCAN` history type and History UI label.

None of these open decisions authorizes a code change, permission request, schema migration, OSS dependency, or product-route change in this task.
