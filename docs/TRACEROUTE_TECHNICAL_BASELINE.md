# Traceroute Phase 1 Technical Baseline

**Status:** Technical baseline plus implemented IPv4 Core (no UI)
**Product line:** NetworkToolbox v0.3.0  
**Date:** 2026-08-31

This document is a feasibility and design baseline for Traceroute Phase 1. It
does not add a Traceroute screen or user-facing feature. The Task 044 section
records the isolated production Core implementation, without adding a new permission,
dependency, a version change, a tag, or a release. The product scope remains
governed by [`PRODUCT_PLAN.md`](PRODUCT_PLAN.md).

## 1. Current implementation audit

At the time this baseline was first drafted, the repository had no Traceroute
implementation. The current repository now also contains the isolated IPv4
production Core documented in §36; the pre-existing capabilities remain:

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

The Task 044-A App-UID spike below is a device-specific refinement of this
general limitation. It does not make ordinary Android UDP error-queue support
portable across OEMs, but it did establish a working IPv4 capability on the
validated Sony device. Task 044 then approved and implemented a separately
scoped IPv4 Core that retains explicit capability, parser, timeout, and
unsupported-state behavior; it does not claim portability to unvalidated OEMs.

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

### UDP Error Queue App-UID Spike (Task 044-A)

Task 044-A ran a temporary debug-only Android harness on the same Sony Xperia
1 VII / XQ-FS72 running Android 16 (API 36). The harness was launched as the
installed application, not through `adb shell`; `getuid()` and `geteuid()`
both reported application UID `10420`. It used a small NDK/JNI probe only for
that local capability check and was removed after validation. Task 044 retains
a separate production NDK/JNI adapter with the protocol and lifecycle
boundaries documented in §36; the temporary activity and its ad-hoc logging
remain removed.

The bounded probe used IPv4 `AF_INET`, `SOCK_DGRAM`, `IPPROTO_UDP`, one UDP
datagram per TTL, `IP_TTL` for TTL 1 through 8, `IP_RECVERR`, `poll(POLLERR |
POLLIN)`, and `recvmsg(MSG_ERRQUEUE)`. It did not use `SOCK_RAW`,
`CAP_NET_RAW`, Root, Toybox, `ProcessBuilder`, or a third-party packet
library. The payload was seven bytes and the destination port was in the
standard high-UDP traceroute range. Targets were `1.1.1.1`, `223.5.5.5`, and
`119.29.29.29`; no mass or repeated traffic test was performed.

Observed App-UID results:

| Operation | Result |
| --- | --- |
| `socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP)` | OK |
| `setsockopt(IP_TTL)` for TTL 1–8 | OK |
| `setsockopt(IP_RECVERR)` | OK |
| `sendto` | OK, 7 bytes for each attempted TTL |
| `poll` | `POLLERR` was delivered for error-queue responses; bounded timeouts were returned where no response arrived |
| `recvmsg(MSG_ERRQUEUE)` | OK for delivered extended errors |
| `sock_extended_err.ee_origin` | `2`, `SO_EE_ORIGIN_ICMP` |
| ICMP response | `type=11`, `code=0`, ICMP Time Exceeded |
| `SO_EE_OFFENDER` | First observed responders were the local private hops `10.0.1.1` and `10.0.0.1`; later public responders were also observed |
| latency | Approximately 3–83 ms across the bounded runs |
| timeout | Several individual TTLs timed out, without failing the socket or app |
| destination reached | Not observed within the configured maximum TTL 8 for these runs |
| normal close | Every probe closed its socket and reported `SOCKET CLOSED` |
| selected `Network.bindSocket(fd)` | OK for an application-created UDP descriptor on the active network |

The device remained SELinux `Enforcing`, and no Root or `CAP_NET_RAW` was
used. This proves that this particular non-Root application UID can receive
some IPv4 ICMP Time Exceeded errors through the Linux UDP error queue. It does
not prove that every intermediate router responds, that every destination will
produce a final port-unreachable response, or that another Android OEM will
permit the same behavior. A future implementation must treat timeout and
unsupported-socket outcomes as first-class results.

IPv6 UDP error-queue probing was **NOT TESTED** in this spike. The active
Wi-Fi path did not expose a usable public IPv6 default route for a bounded
application test, while the prior system-command validation had already shown
that the device-specific IPv6 route situation required separate handling. This
does not change the IPv4 result or authorize silent IPv4 fallback for a future
IPv6 request.

**Task 044-A gate result:** app-owned UDP error queue was **GO for an IPv4
technical candidate on Sony XQ-FS72 / Android 16**. Task 044 implemented the
separate production Core described in §36. The system-command approach remains
**NO-GO** on this device. The spike itself intentionally did not test a
mid-poll cancellation race; the production Core adds a cancellation pipe and
closes the socket from the coroutine lifecycle.

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
obtained through that system-command path. The App-UID UDP error-queue spike
later produced real IPv4 intermediate ICMP responses, and Task 044 now provides
an IPv4-only production Core using that capability. It remains a
device-qualified capability, not a portable Android guarantee.

The project must not begin production Traceroute implementation from the
temporary spike alone. Task 044 supplied the separately approved production
implementation with the required pure model, response matching, cancellation,
parser behavior, and unavailable states. The app must not silently substitute
raw ICMP, TCP connect, or `ping -t`.

Do not ship app-owned raw ICMP, TCP-connect-as-traceroute, or `ping -t` as the
default. App-owned UDP error queue may be considered only through a separately
approved implementation that preserves explicit capability and timeout
semantics.

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
orchestration, coroutines already present in the project, and a small C++17
NDK/JNI platform adapter are sufficient. A DNS or packet library would not
solve Android socket-policy differences and would create additional license,
parser, security, and maintenance surface.

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
| App-owned UDP + ICMP receive | Device/policy dependent | **GO for device-qualified IPv4 Core on Sony** | App UID received real ICMP Time Exceeded via `IP_RECVERR`; production Core is bounded and remains non-portable without per-device validation |
| Normal TCP connect | Destination-only | No-Go as traceroute | Does not expose intermediate hops |
| `ping -t` orchestration | Command/output dependent | No-Go as default | Not a stable hop-result API and too easy to mislabel |
| AOSP/OEM system `traceroute` adapter | Fails on Sony XQ-FS72 | No-Go on validated device | Toybox applet exists but socket creation returns `Operation not permitted` |
| Third-party packet library | Does not remove platform limits | No-Go for now | Extra dependency without a reliable capability gain |

**Gate result:** the Sony Android 16 system-command gate failed: the applet is
present inside Toybox, but non-Root socket creation is rejected and no route
can be observed through that command. The separate Task 044-A App-UID spike
passed the bounded IPv4 UDP error-queue capability check, and Task 044-B
validated the separately scoped production Core on the same device. Do not
implement or ship a system-command adapter for this device. The UDP result
remains device-qualified and cannot be generalized to other devices without
their own capability checks.

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

Task 044 now implements the production IPv4 Core under the separate
implementation scope below. It does not authorize a UI, History, Diagnostic,
or IPv6 integration, and it must not add Root flow, `SOCK_RAW`, a native
traceroute binary, `ProcessBuilder`, or a third-party library implicitly. The
temporary spike activity itself remains removed.

## 36. Task 044 IPv4 production Core

Task 044 implements the approved non-Root IPv4 path as an isolated Core module.
The implementation is deliberately narrower than the broader v0.3 product
direction: it is a cancellable, evidence-labelled engine and not yet a user
screen or an automatic diagnostic stage.

### Architecture and boundaries

```text
TracerouteEngine
        |
DefaultTracerouteEngine       Kotlin orchestration on Dispatchers.IO
        |
UdpTracerouteNativeProbe      platform abstraction
        |
AndroidNativeUdpTracerouteProbe
        |
NativeUdpTracerouteJni        minimal C++17 NDK adapter
        |
AF_INET UDP socket + Linux extended error queue
```

The domain model is pure Kotlin in `core:network` and contains request,
per-probe, per-hop, resolution, binding, and session result types. Kotlin owns
input validation, hostname-to-IPv4 resolution through the selected Android
`Network`, network fingerprint checks, TTL/port sequencing, result mapping,
timeouts, cancellation, and session cleanup. Native code owns only one bounded
probe: socket creation, `IP_RECVERR`, `IP_TTL`, `sendto`, `poll`,
`recvmsg(MSG_ERRQUEUE)`, safe ancillary-data parsing, ICMP classification, and
file-descriptor cleanup.

The native adapter uses `socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP)`. It never
uses `SOCK_RAW`, `CAP_NET_RAW`, Root, a shell, `ProcessBuilder`, or a TCP
connect result as a traceroute hop. The existing `INTERNET` and
`ACCESS_NETWORK_STATE` permissions are sufficient; no new permission or
third-party dependency was added. `Network.bindSocket(fd)` is called for the
selected active network, and bind failure is returned as a structured failure;
there is no silent default-network fallback.

### Protocol and status semantics

The first production contract is IPv4 only. IPv6 requests return an explicit
unsupported validation result and never fall back to IPv4. IPv4 literals and
hostnames are accepted; hostnames are resolved outside native code. Addresses
in `198.18.0.0/15` are retained and marked `fakeIpDetected`; they are not
blocked and do not imply a particular proxy application.

Defaults are `maxHops=30`, `probesPerHop=3`, `timeoutMs=1500`, and a high UDP
destination port beginning at `33434`. The request is bounded to 1–30 hops,
1–3 probes, 100–5000 ms, and legal destination ports; ports 53 and 123 are
rejected. Each probe uses a deterministic per-probe port offset and an
8-byte payload. No DNS/HTTP/other application protocol is used as the probe.

`ICMP_TIME_EXCEEDED` is `HOP`. `ICMP_DEST_UNREACH` with
`ICMP_PORT_UNREACH` is `DESTINATION_REACHED`. Other ICMP errors are local
errors, not destination success. A probe timeout is retained as a timeout and
does not stop later TTLs. Exhausting the hop limit without destination proof is
`PARTIAL`, not `FAILED`. `FAILED` is reserved for resolution, socket,
`IP_RECVERR`, bind, permission, malformed-response, unsupported, or persistent
local-operation failures. Unknown native codes map to `LOCAL_ERROR`.

### Cancellation and network changes

Every native socket owns a nonblocking cancellation pipe. Native `poll()` waits
on both the socket error state and the cancellation read end, so coroutine
cancellation wakes a probe rather than waiting for the full timeout. The
engine also registers cancellation cleanup, disposes that registration before
normal close, and closes the socket and pipe descriptors in `finally`.

The engine snapshots the selected `Network` fingerprint and checks it before
each probe/TTL. A changed fingerprint returns `NETWORK_CHANGED` with the
partial trace and never combines observations from the two networks. Late
callbacks are not applicable to this synchronous native probe; no process or
background monitor is retained after the session.

### Native safety and ABI

The NDK is pinned to `27.0.12077973`; CMake uses C++17, `-Wall -Wextra
-Werror`, and a 16 KB maximum page-size linker option for Android 16 readiness.
No `abiFilters` were present or added, so Gradle builds the project-supported
ABIs by default. The Debug APK contains the following library slices from the
same implementation:

| ABI | Debug library bytes | Release library bytes |
| --- | ---: | ---: |
| `arm64-v8a` | 410,968 | 351,504 |
| `armeabi-v7a` | 245,560 | 212,688 |
| `x86` | 387,432 | 318,832 |
| `x86_64` | 391,008 | 334,424 |

The final post-acceptance artifacts were `app-debug.apk` (65,110,953 bytes)
and `app-release.apk` (48,738,422 bytes). Their SHA-256 values are recorded
in the Task 044-B acceptance record below; no APK or tag is published by this
task.

### Test and integration boundary

The Core tests use fake networks and native probes, so CI does not depend on
the public Internet. They cover validation, hostname resolution and Fake-IP
marking, all native outcome mappings, hop/destination/timeout semantics,
partial traces, no-network/resolution/open/bind failures, network changes,
cancellation cleanup, and per-probe port sequencing. A temporary debug-only
device harness is permitted for the Sony gate and is removed after use. There
is no Traceroute UI, History record, Reverse DNS, ASN/GeoIP, TCP fallback,
automatic Diagnostic integration, or product version change in Task 044.

## 37. Task 044-B Sony Android 16 production Core acceptance

Task 044-B performed a real-device validation of the formal production Core on
2026-09-01. The temporary harness was debug-only, used a separate validation
application id, and was removed after testing. The signed `com.networktoolbox`
installation and its local data were not uninstalled or replaced.

### Device and execution gate

| Field | Observed value |
| --- | --- |
| ADB serial | `HQ657X0B9F` |
| Manufacturer / model | Sony XQ-FS72 |
| Android / API | Android 16 / API 36 |
| Shell identity | UID 2000, SELinux `Enforcing` |
| Validation app UID | 10422 |
| Engine | `DefaultTracerouteEngine` + `AndroidNativeUdpTracerouteProbe` |
| Request | 30 hops, 3 probes/hop, 1500 ms timeout, UDP port base 33434 |

The engine completed hop processing on the validation app. Because the formal
Core has no default-network fallback after `Network.bindSocket`, these results
also confirm that binding the selected socket to the active Android `Network`
did not fail. No Root, `CAP_NET_RAW`, `SOCK_RAW`, system traceroute command,
`ProcessBuilder`, or TCP-as-traceroute fallback was used.

### Results

The following records are deliberately summarized; the full public route is
not retained in this document.

| Target | Status | Duration | Device evidence |
| --- | --- | ---: | --- |
| `1.1.1.1` | `REACHED` | 11.6 s | Reached at hop 12 with ICMP destination port-unreachable evidence; local gateways `10.0.1.1` and `10.0.0.1` were observed as intermediate hops |
| `223.5.5.5` | `PARTIAL` | 109.1 s | Later hops continued after multiple timeout hops; no timeout was promoted to a false failure |
| `119.29.29.29` | `PARTIAL` | 111.6 s | Structured partial result with the same initial network fingerprint |
| `example.com` | `NETWORK_CHANGED` | 78.3 s | Resolved through the active `Network` to `198.18.13.240`, marked `fakeIpDetected=true`, and stopped without mixing results after the network fingerprint changed |

The `1.1.1.1` run included a complete timeout hop followed by later
responding hops and final destination evidence, validating timeout recovery.
The hostname run validated active-network resolution, Fake-IP marking, and
network-change isolation; it is not evidence that a hostname route completed.

### Cancellation and repeat behavior

After the outer cancellation-boundary fix, a direct `Job.cancelAndJoin()` test
on the device returned `CANCELLED`; the native wait was interrupted and the
job joined in approximately 1.2 seconds. The result is not a completed trace.

Three immediate runs against `223.5.5.5` were intentionally recorded rather
than normalized:

| Run | Status | Duration | Note |
| --- | --- | ---: | --- |
| 1 | `FAILED` | 72 ms | Device returned structured `SENDTO errno 113` after three responding hops |
| 2 | `PARTIAL` | 112.0 s | Completed the bounded 30-hop search |
| 3 | `NETWORK_CHANGED` | 68.6 s | Active network fingerprint changed during the run |

This sequence does not pass a “three identical stable runs” criterion because
the device/network changed and one run encountered a transient send error. It
does confirm that each run is isolated and returns a structured status rather
than leaking a previous generation's hops.

### Stability, cleanup, and production bug found during acceptance

No `FATAL EXCEPTION`, `SIGSEGV`, `SIGABRT`, JNI fatal error, or validation-app
ANR was found in the final log review. After completion, an app-UID `/proc`
check reported only the three standard file descriptors; native socket and
cancellation-pipe descriptors were closed by the Core cleanup path. The formal
Core process remained alive as an ordinary cached app process and did not crash.

The first formal run exposed a real JNI ABI defect before any route result:
Kotlin's nullable `NativeSocketOpenResult.errno: Int?` has a boxed
`java.lang.Integer` constructor parameter, while the native lookup used the
primitive `I` descriptor. The production adapter now uses
`Ljava/lang/Integer;` and passes a nullable boxed errno. The earlier spike did
not expose this because it did not construct `NativeSocketOpenResult`; it used
a smaller standalone JNI result path.

**Acceptance decision:** the IPv4 production Core is device-observed and
usable on Sony XQ-FS72 / Android 16 with the limitations above. The repeated
run stability criterion remains open for a later stable-network retest. This
does not authorize Traceroute UI, History, Diagnostic integration, IPv6, or a
release.

## 38. References

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
