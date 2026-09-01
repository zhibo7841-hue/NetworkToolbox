# Traceroute Phase 1 Technical Baseline

**Status:** Technical baseline and implementation recommendation only  
**Product line:** NetworkToolbox v0.3.0  
**Date:** 2026-08-31

This document is a feasibility and design baseline for Traceroute Phase 1. It
does not add a Traceroute screen, production Kotlin code, a new permission, a
dependency, a version change, a tag, or a release. The product scope remains
governed by [`PRODUCT_PLAN.md`](PRODUCT_PLAN.md).

## 1. Current implementation audit

The current repository has no Traceroute implementation. The relevant existing
capabilities are:

| Area | Current implementation | What it does not provide |
| --- | --- | --- |
| Ping v2 | `AndroidPingSessionProbe` resolves with `InetAddress.getAllByName()` and calls `InetAddress.isReachable(timeoutMs)` | No caller-controlled TTL, hop parsing, or reliable ICMP method guarantee |
| TCP | `AndroidTcpPortChecker` uses `Socket.connect()` | No per-hop TCP SYN control or intermediate-router result |
| DNS v2 | `DnsResolver.rawQuery` through the Android adapter, with the system `Network` | A DNS response and records, not a route map |
| Network context | `ConnectivityManager`, `NetworkCapabilities`, and `LinkProperties` | No way to bind a child `ProcessBuilder` command to an arbitrary `Network` |
| Diagnostic | Reuses the existing Ping, DNS, and TCP abstractions | No Traceroute stage today |

The Phase 1 design must therefore introduce a separate, evidence-labelled
Traceroute core. It must not reinterpret an existing Ping or TCP result as a
traceroute hop.

## 2. Android non-Root restrictions

An ordinary Android application can open network sockets with the `INTERNET`
permission, but that permission is not a promise that the application can open
raw ICMP sockets, receive ICMP Time Exceeded messages, or set every IP header
field required by an implementation of traceroute. Raw-socket availability is
also affected by Android SELinux policy, UID capabilities, kernel behavior, and
OEM changes.

There is no public runtime permission that turns a third-party application into
a raw-socket traceroute client. NetworkToolbox will not request Root, execute
`su`, depend on `CAP_NET_RAW`, or claim that a raw ICMP implementation is
portable across Android 12–16 devices.

This makes an app-owned raw ICMP/UDP implementation an unsuitable Phase 1
default. A device-specific capability may be investigated later, but failure to
obtain raw-socket behavior must result in an explicit unavailable state rather
than a fabricated route.

References: [Android permissions](https://developer.android.com/reference/android/Manifest.permission.html),
[AOSP SELinux concepts](https://source.android.com/docs/security/features/selinux/concepts?hl=en),
and [Android 16 QPR2 untrusted-app network policy](https://android.googlesource.com/platform/system/sepolicy/+/android16-qpr2-release/private/app_neverallows.te).

## 3. ICMP traceroute feasibility

Classic ICMP traceroute sends probes with increasing IPv4 TTL or IPv6 hop
limit and receives ICMP Time Exceeded responses from intermediate routers. A
complete implementation needs controlled packet headers, response matching,
and access to the relevant ICMP responses.

On non-Root Android this is not a stable application contract. The fact that a
particular device or platform component can perform diagnostics does not grant
the same capability to an untrusted application. NetworkToolbox's current
`InetAddress.isReachable()` result is intentionally labelled system
reachability; it must not be relabelled ICMP traceroute.

**Decision:** app-owned raw ICMP is not the Phase 1 primary implementation.

## 4. UDP traceroute feasibility

UDP traceroute sends packets to a high destination port with increasing TTL.
Intermediate routers may return ICMP Time Exceeded and the destination may
return an ICMP port-unreachable response. The method still needs reliable
ICMP response reception, response-to-probe matching, and a way to set TTL or
hop limit.

An ordinary `DatagramSocket` can send user-space UDP data, but that alone does
not provide a portable way for an Android application to receive and interpret
all intermediate ICMP errors. NAT, VPNs, firewalls, and carrier networks can
also suppress or rewrite the expected responses.

**Decision:** app-owned UDP traceroute is not a reliable Phase 1 fallback.

## 5. TCP traceroute feasibility

TCP traceroute uses controlled TCP probes, commonly SYNs to a destination port
such as 443, and infers intermediate responses from TTL expiry or the final
TCP handshake. A normal Java/Android `Socket.connect()` only answers whether a
connection was established or failed; it does not expose the intermediate
routers that discarded a packet.

The current TCP checker therefore cannot be promoted to TCP traceroute. A
rootless raw-SYN implementation would have the same capability and policy
problems as raw ICMP, while a normal connect would be a destination check, not
a route trace.

**Decision:** TCP 443 may be a future probe mode only if a platform-backed,
device-verified implementation is found. It is not the Phase 1 default.

## 6. Android system commands

The most practical non-Root candidate is an allow-listed Android system
command invoked as a child process. AOSP Toybox documents `traceroute` and
`traceroute6` options including IPv4/IPv6 selection, maximum TTL, probe count,
wait time, and interface selection. AOSP Android 13 device configuration also
contains the Toybox traceroute entry. This is evidence about AOSP branches, not
proof that a Sony Android 16 production image exposes the same command or
output.

The current AOSP Toybox source shows that traceroute itself uses privileged or
platform-sensitive ICMP receive behavior. A command being present does not
remove the need to test its execution as the application-facing UID.

| Candidate | Baseline decision |
| --- | --- |
| `/system/bin/traceroute` | Primary candidate for IPv4, only after device capability and output validation |
| `/system/bin/traceroute6` | Conditional IPv6 candidate, only after device validation |
| `/system/bin/tracepath` | Research candidate only; availability and output are not assumed |
| `/system/bin/ping` | Existing system utility candidate, not a traceroute contract |
| `toolbox traceroute` | Not assumed; current AOSP toolbox source does not establish it as the provider |

References: [AOSP Toybox generated help](https://android.googlesource.com/platform/external/toybox/+/refs/heads/main/android/linux/generated/help.h),
[Toybox traceroute source](https://android.googlesource.com/platform/external/toybox/+/40d21c59f76a9388b0ebb5c4c706732c2d034a67/toys/pending/traceroute.c),
[AOSP Android 13 Toybox configuration](https://android.googlesource.com/platform/external/toybox/+/refs/tags/android-13.0.0_r28/android/device/generated/newtoys.h),
and [AOSP toolbox](https://android.googlesource.com/platform/system/core/+/master/toolbox/).

## 7. `ping` with TTL

AOSP Toybox `ping` accepts `-t` and sets IPv4 `IP_TTL` or IPv6
`IPV6_UNICAST_HOPS`. That shows a technically possible building block on an
AOSP image. It does not define a stable Android application API or guarantee
that the resulting output exposes and reliably labels intermediate ICMP Time
Exceeded messages.

Running one `ping` process per TTL would also require process orchestration,
output parsing, response correlation, and a large number of probes. It is not
equivalent to a native traceroute implementation and must not be used as a
silent fallback when traceroute is absent.

**Decision:** do not use `ping -t` as the Phase 1 primary or an unlabelled
fallback. It may be included in a future capability experiment only.

Reference: [AOSP Toybox ping source](https://android.googlesource.com/platform/external/toybox/+/40d21c59f76a9388b0ebb5c4c706732c2d034a67/toys/net/ping.c).

## 8. Sony Xperia 1 VII / Android 16 Real Device Validation

Task 043-R completed the required device gate on 2026-09-01. The actual
`adb devices` output was:

```text
List of devices attached
HQ657X0B9F	device
```

The device identity was:

| Field | Result |
| --- | --- |
| Manufacturer | Sony |
| Model | XQ-FS72 |
| Android version | 16 |
| API level | 36 |

The ADB shell used for validation was not Root:

```text
uid=2000(shell) gid=2000(shell) context=u:r:shell:s0
getenforce: Enforcing
```

### Command availability

There is no standalone executable or symlink at the expected paths:

| Check | Result |
| --- | --- |
| `command -v traceroute` | No result, exit 1 |
| `command -v traceroute6` | No result, exit 1 |
| `command -v tracepath` | No result, exit 1 |
| `/system/bin/traceroute` | Missing, exit 1 |
| `/system/bin/traceroute6` | Missing, exit 1 |
| `/system/bin/tracepath` | Missing, exit 1 |
| `/system/bin/toybox` | Present and executable |

The real Toybox command list contains both `traceroute` and `traceroute6`.
`toybox traceroute --help` executed successfully and reported Toybox
`0.8.12-android`. The observed options were:

- `-4` / `-6` for address-family selection;
- `-m` for maximum TTL, range 1–255;
- `-q` for probes per TTL, range 1–255, default 3;
- `-w` for wait seconds, range 0–86400, default 3;
- `-n` for numeric output;
- `-i` for interface;
- `-p` for the base UDP port;
- IPv4-only `-U` UDP datagrams and `-I` ICMP Echo;
- IPv4-only `-f`, `-F`, `-g`, and `-z` options.

The direct commands `traceroute`, `traceroute6`, and `tracepath` were not
available through the shell PATH. The usable applet entry point was
`/system/bin/toybox traceroute` or `/system/bin/toybox traceroute6`.

### IPv4 command tests

The following bounded commands were run as the non-Root ADB shell user, with
`-m 3 -q 1 -w 1 -n`:

| Target/variant | Exit code | stdout | stderr |
| --- | ---: | --- | --- |
| `toybox traceroute -4 1.1.1.1` | 1 | Empty | `traceroute: socket 3 1: Operation not permitted` |
| `toybox traceroute -4 223.5.5.5` | 1 | Empty | `traceroute: socket 3 1: Operation not permitted` |
| `toybox traceroute -4 119.29.29.29` | 1 | Empty | `traceroute: socket 3 1: Operation not permitted` |
| `toybox traceroute -4 -U 1.1.1.1` | 1 | Empty | `traceroute: socket 3 1: Operation not permitted` |
| `toybox traceroute -4 -I 1.1.1.1` | 1 | Empty | `traceroute: socket 3 1: Operation not permitted` |

No command reached the first hop, produced a timeout marker, or produced a
destination response. The failure occurs while creating the socket, before a
route can be observed. No large or repeated traffic test was performed.

### IPv6 command and route tests

`toybox traceroute6 --help` was available through the multicall binary, but
`toybox traceroute6 -6 -n -m 3 -q 1 -w 1 2606:4700:4700::1111` exited 1 with:

```text
traceroute6: socket 3 3a: Operation not permitted
```

The device exposed global IPv6 addresses on cellular interfaces, but the
ordinary `ip -6 route` table had no default route. The all-tables view showed
cellular-specific IPv6 defaults, while Wi-Fi had only link-local IPv6. A
usable end-to-end public IPv6 traceroute was therefore not established.

### Network scenarios not run

The command failed before network probing, so comparative trace results are
not available:

| Scenario | Result |
| --- | --- |
| Current Wi-Fi | NOT TESTED as a successful traceroute |
| OpenClash Wi-Fi | NOT TESTED |
| Mobile data | NOT TESTED as a successful traceroute |
| Domain `example.com` | NOT TESTED because IPv4 prerequisite failed |
| Fake-IP observation | NOT TESTED |
| ProcessBuilder from an app UID | NOT TESTED directly; no spike was justified after the shell-level socket failure |
| `destroy()` / `destroyForcibly()` | NOT TESTED because no traceroute process reached a running trace |
| Sony output fixture | No hop fixture available |

The shell-level failure is sufficient to reject the system-command approach for
this device's non-Root Phase 1 path. A separate app-UID spike would not add
useful evidence when the same Toybox applet cannot create its socket even from
the less restricted ADB shell context.

### Final device-specific decision

For Sony XQ-FS72 / Android 16:

- IPv4 Traceroute Phase 1: **NO-GO** for the system-command approach;
- IPv6 Traceroute Phase 1: **NO-GO** for the system-command approach on this
  device;
- System traceroute adapter: **NO-GO** on the validated device;
- ProcessBuilder: **NO-GO** as a Phase 1 strategy on this device.

This is a device capability result, not proof that every Android OEM behaves
identically. It does mean Task 042's system-command recommendation cannot be
promoted to a supported Sony Android 16 product path.

For a future device or separately approved OEM investigation, the following
non-Root shell checks remain useful:

```text
adb shell command -v traceroute
adb shell command -v traceroute6
adb shell ls -l /system/bin/traceroute /system/bin/traceroute6
adb shell /system/bin/traceroute -4 -m 3 -q 1 -w 1 1.1.1.1
adb shell /system/bin/traceroute6 -6 -m 3 -q 1 -w 1 2606:4700:4700::1111
```

These commands are validation probes only; on the Sony device validated here,
the standalone paths are known to be missing and the Toybox applets are known
to fail at socket creation. They are not a production feature and must not be
treated as evidence for another device until output and exit semantics are
recorded.

## 9. IPv4 policy

IPv4 remains the only protocol that was a plausible Phase 1 default before the
device gate. On a different device, a separately approved implementation could
use a verified system traceroute command with an explicit IPv4 option when the
target is an IPv4 literal or the user selects IPv4. The Sony device validated
in this task is not such a device.

IPv4 results must distinguish an observed intermediate address from a timeout.
An intermediate hop may not respond while later hops continue to respond. That
is a normal limitation of traceroute evidence, not automatically a faulty
router.

## 10. IPv6 policy

IPv6 would remain **conditional** even on a device that passes the IPv4 gate. It
requires both a usable IPv6 route and a verified IPv6-capable command/parser
path. An IPv6 link-local address is not a usable public traceroute target
without a scope or interface, and NetworkToolbox must not silently strip that
scope.

If `traceroute6` is absent, rejected, or produces an unverified format, the
result should say that IPv6 traceroute is unavailable on this device. It must
not fall back to IPv4 or report an empty route as an IPv6 failure.

## 11. VPN behavior

If the active Android network is a VPN, the traceroute represents the path
visible through the active/default VPN environment, subject to the system
command's routing behavior. It does not prove the path inside the VPN tunnel
or identify a particular VPN application.

The result should carry a `vpnActive`/environment notice when the shared
`NetworkContext` says VPN is active. This is a notice, not an error. The UI
must explain that the observed path may be the path after VPN routing.

## 12. OpenClash and Fake-IP behavior

If domain resolution yields an address in `198.18.0.0/15`, the traceroute
should preserve the resolved address and attach a notice that it is a special-
purpose result that may belong to a proxy/Fake-IP environment. It must not
assert that a specific proxy application is installed and must not classify the
address as a DNS failure solely because of the range.

Tracing that address also does not prove the route to the domain's real public
origin. The result should state what address was actually traced.

## 13. Network binding

Android's `Network.bindSocket()` can bind an application-created `Socket`,
`DatagramSocket`, or file descriptor to a selected `Network`, provided the
socket is not already connected. This does not provide a documented way to
bind a `ProcessBuilder` child command's internal sockets to an arbitrary
`Network`.

The Phase 1 system-command adapter should therefore:

1. snapshot the active network and its identity before starting;
2. execute through the normal default routing context;
3. observe network changes while the process runs;
4. cancel and classify the result as `NETWORK_CHANGED` if the observed network
   changes before completion;
5. avoid claiming that the command was bound to a selected network.

Using process-global `bindProcessToNetwork()` merely to support one operation
would create global side effects and is not recommended for the first design.

Reference: [Android `Network` API](https://developer.android.com/reference/android/net/Network).

## 14. Candidate Phase 1 architecture

Before the real-device gate, the smallest candidate architecture was a
**conditional system-command adapter** with a pure Kotlin parser and an explicit
unavailable result:

```text
TracerouteUseCase
        |
TracerouteEngine
        |
TracerouteProcessRunner  -- Android data adapter
        |
ProcessBuilder (/system/bin/traceroute or traceroute6)
        |
stdout + stderr + exit code
        |
TracerouteOutputParser   -- pure Kotlin
        |
TracerouteResult
```

The core must not import Android `Context`, `Network`, `NsdManager`, or Compose.
The data adapter would own command capability detection, process lifecycle,
network snapshot integration, and platform exception mapping. The parser would
own only deterministic text-to-model conversion.

The Sony validation below failed before any hop output was produced, so this
architecture is not approved for the validated device.

## 15. Final protocol recommendation after Sony validation

The proposed system-command method is **not approved** for Sony XQ-FS72 /
Android 16. Although the Toybox applets exist, both IPv4 and IPv6 fail at socket
creation for the non-Root ADB shell. No real hop output or parser fixture was
obtained.

The project must not begin production Traceroute implementation from this
candidate. A new technical/product decision is required before selecting a
different platform approach. The app must not silently substitute raw ICMP,
app-owned UDP, TCP connect, or `ping -t`.

Do not ship app-owned raw ICMP/UDP, TCP-connect-as-traceroute, or `ping -t` as
the default. On the validated device, Phase 1 should remain unavailable until
a separately approved platform solution exists.

## 16. Process invocation and input safety

`ProcessBuilder` takes a list of command arguments and exposes separate process
streams. The adapter must pass an allow-listed executable path and individual
arguments; it must never build a shell string or invoke `sh -c`.

Allowed user-controlled values are validated before entering the argument list:

- a domain or IP target, with no whitespace/control characters;
- protocol restricted to the internal enum;
- numeric max hops, probe count, and timeout within fixed bounds;
- no user-supplied executable path, option name, redirection, pipe, or shell
  token.

The adapter must retain stdout and stderr separately, cap captured output, and
redact or avoid logging complete user targets when diagnostic logging is
enabled.

Reference: [Android `ProcessBuilder`](https://developer.android.com/reference/java/lang/ProcessBuilder).

## 17. Target and name resolution

Supported input is an IPv4 literal, an IPv6 literal, or a domain name. Literal
addresses should bypass DNS. For a domain, Phase 1 should prefer the selected
system command's own resolver so the traced destination and the system network
environment are not silently split between two resolvers.

If the command cannot report the resolved destination, the adapter may perform
a separately labelled system-resolution step only when it can keep the result
consistent with the active network. It must not use an unlabelled
`InetAddress.getAllByName()` pre-resolution and then claim that the route was
traced to a specific DNS answer from the v2 resolver.

The result must include the input target and, when reliably observed, the
resolved destination address and resolution method. NXDOMAIN/DNS failure must
remain distinct from a route containing timeout hops.

## 18. Default parameters and limits

The initial proposal is intentionally bounded:

| Parameter | Phase 1 proposal |
| --- | --- |
| Max hops | 30, hard maximum 30 |
| Probes per hop | 3, hard maximum 3 |
| Per-hop timeout | 1,500 ms at the core contract; adapter uses the closest verified command-supported value |
| Overall process budget | Bounded by the use case; a command that exceeds it is terminated and classified as timeout/incomplete |
| Concurrent traceroutes | 1 per screen/session |
| Default protocol | Auto, selecting IPv4 for IPv4 literals and verified IPv6 only when an IPv6 route and adapter exist |
| TCP destination port | Not a Phase 1 parameter; no TCP traceroute claim |

The limits prevent a user input from creating an unbounded process or a large
number of probes. They are design defaults, not permission to add a settings
surface in this task.

## 19. Per-hop semantics

The parser and result model must support:

- one or more observed addresses for a hop;
- one latency per observed probe where available;
- a hostname only when the command provides it, without reverse-DNS inference;
- `INTERMEDIATE` when a hop responds but is not the destination;
- `REACHED` when the destination is confirmed;
- `TIMEOUT` when no probe for that hop responds;
- `MIXED` when some probes respond and others time out.

A timeout hop must not stop parsing later lines. Later responding hops are
evidence that the path continued, even when an earlier router did not answer.

## 20. Endpoint result statuses

The session-level status should include at least:

| Status | Meaning |
| --- | --- |
| `REACHED` | Destination was confirmed by command output and/or validated destination response |
| `INCOMPLETE` | The command ran but the destination was not confirmed; remaining path is unknown |
| `DNS_FAILURE` | Name resolution failed before a route could be started |
| `PROCESS_FAILURE` | Command unavailable, rejected, malformed, or exited without usable evidence |
| `TIMEOUT` | The bounded operation exceeded its budget without completion |
| `CANCELLED` | User or coroutine cancellation stopped the operation |
| `NETWORK_CHANGED` | The active network changed during the operation; the partial trace is not a single-environment conclusion |

`INCOMPLETE` and `TIMEOUT` must not be presented as “the Internet is down”.

## 21. Output parser design

`TracerouteOutputParser` must be pure Kotlin and deterministic. It should
parse the smallest stable grammar rather than depend on a locale-specific
sentence. The first implementation should recognize:

- hop number at the beginning of a line;
- `*` probe timeouts;
- IPv4 and IPv6 address tokens;
- optional hostname followed by a parenthesized address;
- numeric latency followed by `ms` or a documented Toybox variant;
- destination/reached markers and common error markers;
- stderr diagnostics separately from hop output.

The parser must return a structured parse error for truncated lines, invalid
hop numbers, malformed addresses, impossible latency values, or output with no
usable structure. It must never recurse based on user output, index beyond a
buffer, or throw a parsing exception into Compose.

Parser tests should use fixed output fixtures for successful hops, timeout
hops, mixed probes, IPv4, IPv6, DNS errors, OEM spacing variation, and
malformed output. A parse fixture is not evidence that a Sony command produces
that format; device output must be captured separately.

## 22. Process lifecycle, timeout, and stop

The Android adapter must start the process on `Dispatchers.IO`, drain stdout
and stderr without blocking either pipe, and wait for completion under a
bounded timeout. On coroutine cancellation or user stop it must:

1. mark the operation as cancelling;
2. call `Process.destroy()`;
3. wait only for a short bounded grace period;
4. call `destroyForcibly()` if still alive;
5. join stream readers and close resources;
6. return cancellation rather than a normal completed result.

`destroyForcibly()` is cleanup, not a substitute for cancellation semantics.
No process may outlive the use case, and no blocking process wait may run on
the main thread.

## 23. Network-change handling

The engine should capture a network identity before launch and subscribe to the
existing network observation path. If the identity changes while the command
is running, the engine must terminate the process and return
`NETWORK_CHANGED`. Partial hops may be retained for detailed diagnostics, but
they must not be summarized as a complete path.

The implementation must not combine the first half of a Wi-Fi trace with the
second half of a cellular/VPN trace. A rerun after the network is stable is the
safe recommendation.

## 24. Cancellation and foreground policy

Phase 1 is a foreground, one-session operation. There is no background
traceroute, monitor, scheduled run, or process retained after the screen leaves
the operation scope. A screen-level ViewModel cancellation must propagate to
the engine and the child process.

No History record should be created for a cancelled, network-changed, or
process-unavailable run unless a later product decision explicitly defines
that record as a failed attempt. The first History integration should save one
completed session only.

## 25. Output model

The domain model should remain small and evidence-oriented:

```kotlin
data class TracerouteRequest(
    val target: String,
    val protocol: TracerouteProtocol,
    val maxHops: Int,
    val probesPerHop: Int,
    val timeoutMs: Long,
)

data class TracerouteHop(
    val hopNumber: Int,
    val addresses: List<String>,
    val latenciesMs: List<Long?>,
    val hostname: String?,
    val status: TracerouteHopStatus,
)

data class TracerouteResult(
    val target: String,
    val resolvedAddress: String?,
    val protocol: TracerouteProtocol,
    val method: TracerouteMethod,
    val startedAt: Long,
    val finishedAt: Long,
    val status: TracerouteStatus,
    val hops: List<TracerouteHop>,
    val errorMessage: String?,
    val notices: List<String>,
)
```

The final Kotlin names may follow existing project conventions. The model must
not contain a boolean such as `networkIsBroken`; it should preserve observed
hop evidence, execution status, and limitations.

## 26. Protocol and method enums

Recommended domain enums:

```text
TracerouteProtocol: AUTO, IPV4, IPV6
TracerouteMethod: SYSTEM_TRACEROUTE, SYSTEM_TRACEROUTE6, UNAVAILABLE
TracerouteHopStatus: INTERMEDIATE, REACHED, TIMEOUT, MIXED, UNKNOWN
```

`SYSTEM_TRACEROUTE` means a verified system command was used. It does not
mean that the app implemented ICMP itself. If the command mode internally
uses UDP or ICMP, that detail may be added as a separately observed method
field only when the command and device test establish it.

## 27. PTR and hostname handling

Reverse-DNS hostnames shown by a system command are best-effort metadata. They
must never replace the observed IP address or be treated as a device identity.

If PTR lookup slows or changes the route output, the adapter should prefer a
numeric mode where available and omit hostnames. Phase 1 does not add a
separate PTR lookup service or a new permission.

## 28. Diagnostic interpretation boundaries

Traceroute is evidence about responses to a bounded set of probes. The future
interpretation layer may say:

- destination reached: the destination responded to the selected probe;
- intermediate timeout with later responses: that hop did not respond, but the
  path continued;
- repeated late timeouts: the remaining path could not be confirmed;
- DNS failure: the name could not be resolved before tracing.

It must not say:

- a named router is broken;
- the ISP is down;
- a firewall is definitely blocking traffic;
- the target website is down;
- one traceroute proves the complete route for all protocols.

Possible causes should use “may”, “could”, and “cannot be confirmed”.

## 29. History proposal

Task 042 does not implement History. A later integration should save one
completed Traceroute session as one `LAN_TRACEROUTE`/`TRACEROUTE` record through
the existing unified HistoryRecorder, not create a
`TracerouteHistoryRepository`.

The serialized detail should be versioned and include target, actual method,
protocol, session status, hop list, timing, and notices. Raw stdout/stderr
should not be required for ordinary History display; if retained for local
debugging, it must be bounded and treated as sensitive local network data.

Cancelled and network-changed sessions should not become a completed History
entry. Room schema changes are outside this baseline.

## 30. Permissions and privacy

The likely manifest requirements are the existing `INTERNET` and
`ACCESS_NETWORK_STATE` permissions. No location, nearby-device, multicast, or
Root permission is required by the proposed system-command approach.

Traceroute sends network probes to the user-selected target. This is a local
diagnostic action, not a data-upload feature. The app must not send route data,
LAN addresses, DNS results, or raw command output to a server. Logs must avoid
complete user network inventories and should be disabled or bounded outside
debug builds.

## 31. Dependency decision

No third-party dependency is justified for Phase 1. Kotlin standard-library
parsing, `ProcessBuilder`, coroutines already present in the project, and a
small platform adapter are sufficient. A DNS or packet library would not
solve Android raw-socket policy and would create additional license, parser,
security, and maintenance surface.

## 32. Testing plan

All core parser and orchestration tests must be deterministic and must not use
the public Internet in CI.

| Test group | Required coverage |
| --- | --- |
| Parser | IPv4 hops, IPv6 hops, multiple probes, `*` timeout, mixed hop, reached destination, hostname/address pair, malformed/truncated output |
| Process runner fake | stdout/stderr capture, non-zero exit, missing command, bounded output, DNS failure, parse failure |
| Timeout | Process exceeds budget and is terminated; result is timeout/incomplete, not a hang |
| Cancellation | User/coroutine cancellation destroys the process and does not produce a completed result |
| Network change | Change during execution stops the process and returns `NETWORK_CHANGED` |
| Input | IPv4 literal, IPv6 literal, domain, empty/invalid target, invalid limits, shell metacharacters |
| Fake-IP | `198.18.0.0/15` is preserved and produces a notice, not DNS failure |
| IPv6 | Explicit IPv6 selection is unavailable when the adapter is unavailable; no silent IPv4 fallback |
| History contract | One completed session produces one record; cancelled/unavailable runs do not create a completed record |

Sony Xperia 1 VII / Android 16 testing must be a separate device gate and must
record actual command presence, exact output, exit code, IPv4/IPv6 behavior,
VPN behavior, timeout, stop, and network-change results.

## 33. Small technical spike policy

A temporary, local-only spike may inspect command availability and capture
sanitized output on the test device. It must not be added to production source,
must not be committed, and must not expand permissions or product scope. The
spike is useful only to answer whether Sony's image exposes a usable command;
it cannot be used to bypass the core architecture or parser tests.

## 34. Recommendation and decision gate

| Candidate | Feasibility | Phase 1 decision | Reason |
| --- | --- | --- | --- |
| App-owned raw ICMP | Device/policy dependent | No-Go | No stable non-Root third-party app contract |
| App-owned UDP + ICMP receive | Device/policy dependent | No-Go | Cannot reliably receive/match intermediate ICMP responses |
| Normal TCP connect | Destination-only | No-Go as traceroute | Does not expose intermediate hops |
| `ping -t` orchestration | Command/output dependent | No-Go as default | Not a stable hop-result API and too easy to mislabel |
| AOSP/OEM system `traceroute` adapter | Fails on Sony XQ-FS72 | No-Go on validated device | Toybox applet exists but socket creation returns `Operation not permitted` |
| Third-party packet library | Does not remove platform limits | No-Go for now | Extra dependency without a reliable capability gain |

**Gate result:** the Sony Android 16 gate failed. The applet is present inside
Toybox, but non-Root socket creation is rejected and no route can be observed.
Do not implement or ship a system-command adapter for this device. Any later
cross-OEM investigation must be a separately authorized technical task; it
cannot promote the Sony result to GO.

## 35. Scope confirmation

This baseline does not authorize:

- Traceroute UI or a new navigation entry;
- MTR, route maps, GeoIP, ASN, ISP/carrier identification, or BGP analysis;
- automatic Diagnostic pipeline integration;
- custom DNS, DoH, DoT, or DNS comparison;
- background monitoring or infinite tracing;
- new runtime permissions;
- LAN Scanner changes;
- version changes, tags, releases, or APK uploads.

No implementation task is authorized by this baseline after the failed gate.
A future task must first resolve and explicitly approve an alternative
technical approach; it must not add a binary, Root flow, raw-socket path, or
third-party library implicitly.

## 36. References

- [Android `Network`](https://developer.android.com/reference/android/net/Network)
- [Android `ProcessBuilder`](https://developer.android.com/reference/java/lang/ProcessBuilder)
- [Android manifest permissions](https://developer.android.com/reference/android/Manifest.permission.html)
- [Android network operations guidance](https://developer.android.com/develop/connectivity/network-ops/managing)
- [AOSP SELinux concepts](https://source.android.com/docs/security/features/selinux/concepts?hl=en)
- [AOSP Android 16 QPR2 untrusted-app network policy](https://android.googlesource.com/platform/system/sepolicy/+/android16-qpr2-release/private/app_neverallows.te)
- [AOSP Toybox generated help](https://android.googlesource.com/platform/external/toybox/+/refs/heads/main/android/linux/generated/help.h)
- [AOSP Toybox traceroute source](https://android.googlesource.com/platform/external/toybox/+/40d21c59f76a9388b0ebb5c4c706732c2d034a67/toys/pending/traceroute.c)
- [AOSP Toybox ping source](https://android.googlesource.com/platform/external/toybox/+/40d21c59f76a9388b0ebb5c4c706732c2d034a67/toys/net/ping.c)
- [AOSP Android 13 Toybox device configuration](https://android.googlesource.com/platform/external/toybox/+/refs/tags/android-13.0.0_r28/android/device/generated/newtoys.h)
- [AOSP toolbox source tree](https://android.googlesource.com/platform/system/core/+/master/toolbox/)
