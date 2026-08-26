# NetworkToolbox Diagnostic Report v2 Design

Status: Design only
Date: 2026-08-26
Scope: v0.2 Diagnostic Report enhancement

This document defines the proposed Diagnostic Report v2 flow, evidence model,
analysis rules, and migration boundary. It does not modify Kotlin code, the
current Diagnostic UI, the Room schema, the product roadmap, or any probe
implementation.

## 1. Product Goal and Boundary

Diagnostic Report v2 should evolve the current report from a collection of
probe outcomes into an evidence-based network diagnosis aid:

```text
observe
   ↓
run focused checks
   ↓
compare evidence
   ↓
describe possible problem areas
   ↓
suggest the next checks
```

The product must remain clear about uncertainty. It may identify an observed
failure boundary, such as “the gateway check did not succeed”, but it must not
claim that a router, cable, ISP, DNS provider, or application is definitely
broken from these checks alone.

Diagnostic Report v2 is not:

- an automatic repair system;
- an AI diagnosis service;
- a guarantee that every network failure can be located;
- a service availability monitor;
- a LAN scanner;
- an SSH/Telnet client.

The default experience should be understandable to general users. Professional
users should be able to expand the report to inspect check status, measured
values, target, method, and relevant raw probe data.

## 2. Current Capability Audit

This section records what the repository actually does today. It intentionally
does not treat the v0.2 plan as an implemented capability.

### 2.1 Current execution chain

The current report path is:

```text
ReportScreen
    ↓
ReportViewModel
    ↓
GenerateDiagnosticReportUseCase
    ├─ NetworkRepository.observeNetworkContext().first()
    ├─ Report PingUseCase
    │      └─ ExecutePingUseCase(persistHistory = false)
    │             └─ legacy PingEngine
    ├─ Report DnsUseCase
    │      └─ LookupDnsUseCase(persistHistory = false)
    │             └─ legacy DnsEngine
    ├─ Report TcpUseCase
    │      └─ CheckTcpPortUseCase(persistHistory = false)
    │             └─ TcpPortChecker
    ├─ BasicDiagnosticAnalyzer
    └─ HistoryRecorder.report(...)
```

Every probe is wrapped in a fallback so that one probe exception does not stop
the report. The final report is saved as one shared `HistoryRecord` of type
`REPORT`; the internal Ping, DNS, and TCP calls do not create separate history
records.

### 2.2 Current network information

`NetworkRepository` exposes a `Flow<NetworkContext>`. The Android repository
reads the active network, capabilities, link properties, addresses, default
gateway, configured DNS addresses, Wi-Fi name/signal, and VPN transport where
the platform provides them.

`NetworkContext.unknown()` is used when the active network is absent or the
platform read is unavailable. The current model does not distinguish all of
these cases:

- no active network;
- permission or platform read failure;
- a network whose details are not exposed;
- a transient network transition.

Therefore the current report cannot reliably state “there is no network” from
`ConnectionType.UNKNOWN` alone. Diagnostic v2 needs an explicit availability
or observation-confidence field before making that conclusion.

### 2.3 Current Ping capability

The report currently uses the legacy single-result path:

```text
ExecutePingUseCase
    → PingEngine
    → AndroidPingEngine
    → InetAddress.getByName()
    → InetAddress.isReachable()
```

It performs one best-effort system reachability check, returns one latency when
successful, and labels the method `SYSTEM_REACHABILITY`. It does not provide
packet loss, average/minimum/maximum latency, jitter, or a quality level to the
current report.

The Ping v2 `PingSessionEngine` and `PingSessionResult` already exist for the
user-facing Ping feature, but the current Diagnostic use case is not wired to
them.

`InetAddress.isReachable()` is not a guaranteed ICMP measurement. A Ping v2
diagnostic must preserve the real method label and must not turn a failed
system reachability check into a conclusion that the Internet is unavailable.

### 2.4 Current DNS capability

The current report uses the legacy DNS path:

```text
LookupDnsUseCase
    → DnsEngine
    → AndroidDnsEngine
    → InetAddress.getAllByName()
```

This path returns only A/AAAA address values, a duration, a boolean success
value, and a generic error. It cannot reliably expose TTL, CNAME, MX, TXT,
NXDOMAIN, or the actual responding DNS server.

DNS v2 now also has a separate `DnsQueryEngine` path using Android
`DnsResolver.rawQuery` plus a pure Kotlin response parser. It can return A,
AAAA, CNAME, MX, TXT, TTL, structured status, and configured DNS context where
available. The current Diagnostic report does not yet call that v2 path.

### 2.5 Current TCP capability

The current report calls one TCP check against the fixed target
`example.com:443`. `AndroidTcpPortChecker` performs a Socket connect and
classifies failures such as `Connection refused` and `Timeout`.

This proves only whether a TCP connection to the selected host and port was
established. It does not prove that a host is healthy generally, that HTTP/TLS
works, or that the full Internet is available.

The current report has no gateway TCP check, no public-target fallback, no
multi-target comparison, and no relationship analysis between Ping and TCP.

### 2.6 Current analyzer and report model

The current `DiagnosticAnalyzer` accepts:

- nullable `NetworkContext`;
- legacy `PingResult`;
- legacy `DnsResult`;
- `TcpProbeResult`.

`BasicDiagnosticAnalyzer` currently:

- considers basic connectivity normal when context exists, Ping succeeds, and
  legacy DNS succeeds;
- adds a generic target-unreachable finding when Ping fails;
- adds a generic DNS finding when DNS fails;
- adds TCP findings for connection refusal, timeout, or another failure;
- returns a short summary and de-duplicated suggestions.

The current normal-connectivity rule does not require TCP success and does not
compare gateway, public reachability, address families, DNS status, or domain
access. The current findings are a result collection with basic explanations,
not a stage-aware fault analysis.

The current `DiagnosticReport` contains only:

```text
summary
findings
suggestions
```

It has no timestamp, overall status, check list, stage status, raw evidence,
target, or structured recommendation model.

### 2.7 Current UI and state

`ReportViewModel` exposes four states:

- `Idle`;
- `Running` with active/completed `ReportStep` values;
- `Success` with the current report;
- `Error` for an unexpected orchestration/analyzer failure.

The current steps are network information, Ping, DNS, and TCP. The page shows
fixed targets, a simple progress list, summary, findings, and suggestions. It
does not show separate network/gateway/public/DNS/domain check statuses or
professional raw probe data.

### 2.8 Current History behavior

`HistoryRecorder` is the shared persistence boundary. The report use case
records exactly one report entry through `HistoryRecordFactory.report` after
analysis. The generic Room entity stores a human-readable summary plus
`detailJson`, so the current schema can carry a future versioned report payload
without adding a report-specific table.

The current report History payload contains findings and suggestions, but not
the complete NetworkContext or individual Ping/DNS/TCP results. The current
History UI displays the record summary and does not reconstruct a full report
detail view.

## 3. Diagnostic v2 Architecture

### 3.1 Proposed orchestration boundary

The future flow should keep platform and probe implementations outside the
Compose layer:

```text
ReportScreen
    ↓ observes
ReportViewModel
    ↓ invokes and receives real stage callbacks
GenerateDiagnosticReportV2UseCase
    ├─ NetworkObservationProvider
    ├─ GatewayProbe
    ├─ PublicConnectivityProbe
    ├─ DnsLookupV2UseCase / DnsQueryEngine adapter
    ├─ DomainAccessProbe
    └─ DiagnosticAnalyzerV2
```

The orchestration use case owns the order, cancellation, per-stage fallback,
network-change handling, and one-report persistence policy. The analyzer owns
interpretation. Neither Compose nor the analyzer should execute Android APIs,
open sockets, or directly write Room.

Small domain interfaces should be used so unit tests can inject fakes. The
future diagnostic probes may internally delegate to the existing Ping v2 and
TCP/DNS abstractions; they must not fork a second implementation of those
protocols.

### 3.2 Network snapshot and network changes

Stage 1 should capture a diagnostic network snapshot containing:

- active-network availability and observation confidence;
- connection type;
- IPv4/IPv6 addresses where available;
- gateway where available;
- configured DNS context;
- VPN state;
- a platform/network identity sufficient to detect a change during the run,
  without exposing platform objects to the domain layer.

The report must not assume that a snapshot taken before a multi-second run is
still valid after the run. Before network-dependent stages, the platform
adapter should detect whether the active network changed. If it did, the
report should record a `NETWORK_CHANGED` observation and avoid combining
measurements from different networks as if they were one path.

DNS v2 must continue to select the current active Android `Network` for each
query. The report must not cache a DNS server address and call it the responder.

### 3.3 Failure isolation

Each stage returns a structured check outcome. A timeout, unsupported capability,
or missing gateway should not crash the complete report.

Recommended behavior:

- no active network: do not run network-dependent stages; mark them skipped
  with a reason;
- no known gateway: mark gateway `NOT_APPLICABLE`, not failed;
- gateway failure: continue to public/DNS stages when an active network still
  exists, but report the conflicting evidence;
- DNS failure: do not discard network and public results;
- one public target failure: try another approved target before classifying the
  public stage;
- cancellation: stop scheduling new probes and return a cancelled/incomplete
  run state without saving a completed report unless product behavior later
  explicitly defines a cancelled report.

## 4. Layered Diagnostic Flow

### Stage 1: Local network state

Observe the current `NetworkContext` and explicit availability information.

Checks:

- active network exists;
- connection type is known where possible;
- IPv4 and IPv6 addresses;
- gateway and configured DNS context;
- VPN transport state;
- network snapshot confidence.

Interpretation:

- an explicit platform indication of no active network is a strong failure;
- a missing IPv6 address is not a failure by itself;
- a missing gateway or DNS list may be an unavailable observation rather than
  a broken network;
- `UNKNOWN` must not be treated as `UNAVAILABLE` without platform evidence.

### Stage 2: Local link and gateway

If a usable default gateway is present, run a small bounded Ping v2 session
against that gateway. The diagnostic-specific request must be separate from
the user Ping settings, for example a short finite session with a bounded
timeout. Exact count and timeout values are implementation configuration, not
UI state.

The probe must use `PingSessionEngine`, preserve its actual method, and set
history persistence off. It must not create a per-probe or per-packet history
record.

Interpretation:

- gateway success is evidence that the selected local path responded to the
  system reachability method;
- gateway failure may indicate a local link, access point, router, VLAN, or
  filtering issue, but does not prove which one;
- a gateway that is absent or not testable is `NOT_APPLICABLE` or `UNKNOWN`,
  not an automatic failure.

### Stage 3: Public connectivity

The report needs a public-path signal that does not depend on the DNS stage.
No single third-party address should be the sole definition of Internet
availability.

Candidate signals:

1. Android `NET_CAPABILITY_VALIDATED`, where exposed, as platform context. It
   is useful evidence but not an active proof and may be stale or unavailable.
2. A bounded `PingSessionEngine` check to a reviewed public IP target. This is
   useful supporting evidence, but system reachability is not guaranteed ICMP.
3. A TCP connect to port 443 on at least two independently operated, reviewed
   public endpoints. This tests a TCP path without requiring DNS, but it does
   not prove HTTP, TLS, or every Internet service works.

Recommended first design:

- use platform validation as context only;
- use a small approved multi-target set, not one hard-coded provider;
- prefer bounded TCP 443 checks to at least two targets for the primary public
  path signal;
- optionally include Ping v2 as supporting evidence and show its real method;
- if all targets are unavailable or no approved target can be used, return
  `UNKNOWN` rather than declaring the Internet broken.

The actual endpoint list, ownership, change process, and failure isolation
policy need a separate implementation/configuration review. This design does
not add public DNS providers or silently upload data. These probes are normal
network traffic; they do not send the user's report, local addresses, or
network inventory to the endpoints.

### Stage 4: DNS resolution

Diagnostic v2 should migrate to the existing DNS v2 contract:

- default A + AAAA query;
- current system DNS path only;
- current active network for each query;
- structured status;
- records and TTL only when present in a valid raw response;
- configured DNS addresses clearly labeled as configuration context;
- no claim about the actual responding server.

The diagnostic call must disable History persistence. One final Diagnostic
Report is the only History entry for an automatic run.

The analyzer should distinguish:

- A records available and AAAA `NO_RECORDS`: normal dual-stack difference,
  not a DNS failure;
- A success and AAAA timeout/error: partial DNS evidence and a possible
  address-family problem;
- explicit NXDOMAIN: the queried name was reported as nonexistent by the
  response, without asserting why;
- Fake-IP range such as `198.18.0.0/15`: a notice about a possible special
  DNS environment, not an automatic failure;
- timeout/network/invalid response: actual DNS-stage failure evidence.

During migration, a compatibility adapter may project `DnsLookupResult` into
the old `DnsResult` contract for code that still depends on the current
analyzer. Diagnostic v2 should use the richer result for its own analysis; it
should not permanently lose status, record type, or response evidence by
converting it too early.

### Stage 5: Domain access path

This stage is intended to separate “public path works” from “a domain-based
path has a problem”. It must not become an HTTP monitor or TLS inspector.

Recommended bounded check:

- run only when DNS returned a usable A or AAAA address;
- select one or more returned addresses according to the report policy;
- use the existing single-target TCP checker against port 443;
- preserve IPv4/IPv6, target address, latency, refusal, timeout, and unknown
  error distinctly;
- do not claim that a successful TCP handshake proves the service or website
  is healthy.

Possible interpretations:

- public direct targets succeed, DNS fails: DNS path is the primary observed
  boundary;
- DNS succeeds, domain TCP fails: the selected domain/service path did not
  accept the check, but the cause may be service policy, address-family
  filtering, firewalling, or target-specific behavior;
- Ping fails but domain TCP succeeds: the system reachability/Ping method is
  inconclusive or filtered; do not report the Internet as disconnected;
- all checks disagree: report “结果不一致” or an equivalent qualified
  conclusion and show the evidence.

### Stage 6: Analysis and report assembly

The analyzer consumes all completed and skipped check outcomes. It should:

1. identify the strongest observed boundary;
2. preserve contradictory evidence;
3. attach possible causes using qualified language;
4. produce a short summary for general users;
5. produce a small, prioritized recommendation list;
6. retain professional details for expansion.

The analyzer must never convert a missing optional capability into a failure or
choose a definitive hardware/provider diagnosis from one probe.

## 5. Fault Judgment Matrix

The matrix below describes the proposed v2 interpretation. “Pass” means the
specific observation passed; it does not mean every application path works.

| Scenario | Evidence | Overall interpretation | Severity | Suggested wording |
|---|---|---|---|---|
| A. No active network | Platform explicitly reports no active network | Device currently has no usable network connection | ERROR | “设备当前未连接可用网络。请检查 Wi-Fi、移动网络或飞行模式。” |
| B. IP present, gateway not reachable | Local address exists; usable gateway check fails | Local link may be abnormal; AP/router/VLAN/filtering are possibilities | WARNING | “本机有网络地址，但网关检测未成功。问题可能位于本地链路或网关路径。” |
| C. Gateway pass, public path fails | Gateway responds; all approved public checks fail or are unavailable with strong evidence | Public/upstream path may be unavailable | WARNING | “本地网关可达，但公网连通性检测未通过。可能与路由器 WAN、上游网络或目标策略有关。” |
| D. Gateway/public pass, DNS fails | Public path passes; DNS has timeout/network/invalid response or explicit failure | DNS path is the primary observed problem | ERROR | “公网连接正常，但 DNS 查询失败。问题可能与 DNS 服务或网络配置有关。” |
| E1. A pass, AAAA no records | A has valid records; AAAA completed normally with `NO_RECORDS` | IPv4 is available; no IPv6 record was published | NOTICE | “域名有 IPv4 记录，未发现 IPv6 记录；这不一定是故障。” |
| E2. A pass, AAAA timeout/error | A has records; AAAA has a real failure | Partial/address-family DNS evidence | WARNING | “IPv4 解析成功，但 IPv6 查询未正常完成。” |
| F. IPv4 fails, IPv6 passes | IPv4 checks fail; IPv6 checks pass | Possible IPv4-specific path issue; not proof that all Internet access is broken | WARNING | “检测到 IPv4 与 IPv6 结果不一致，可能存在地址族或路径差异。” |
| G. Fake-IP range | A/AAAA contains `198.18.0.0/15` | Special-use/Fake-IP environment may be present | NOTICE | “检测到特殊用途地址，可能存在 Fake-IP DNS 环境；诊断流程仍会继续。” |
| H. VPN active | Platform reports VPN transport | Results may describe the VPN tunnel's path rather than the physical uplink | NOTICE | “检测到 VPN 网络，以下结果可能反映 VPN 隧道后的网络环境。” |
| I. Ping fails, TCP succeeds | System reachability fails; selected TCP 443 succeeds | Ping method is inconclusive or filtered; TCP path is observed working | NOTICE | “Ping 未成功，但 TCP 连接可建立；不能据此判断公网已断开。” |
| J. DNS passes, domain TCP fails | Records returned; target TCP check fails | Target-specific service/path issue is possible | WARNING | “域名解析成功，但目标端口未建立连接；原因可能与服务、地址族或防火墙策略有关。” |
| K. Network changes during run | Active network identity changes between stages | Results span different network states and should be interpreted cautiously | NOTICE | “检测期间网络发生切换，部分结果可能来自不同网络。” |

The analyzer should keep stage status separate from overall severity. For
example, a DNS warning and a VPN notice do not automatically become two
independent red failures, and a missing IPv6 address does not make an IPv4
connection unhealthy.

## 6. Status and Severity Model

The result contract should separate what happened from how much attention it
deserves.

### 6.1 Check status

```text
PASS
FAIL
NO_RECORDS
NOT_APPLICABLE
SKIPPED
UNKNOWN
```

`NO_RECORDS` is important for DNS and must not be treated as a transport
failure. `SKIPPED` means the stage was intentionally not run, for example
because there was no gateway or no DNS address to test. `UNKNOWN` means the
platform or probe could not provide enough evidence.

### 6.2 Severity

```text
HEALTHY
NOTICE
WARNING
ERROR
```

- `HEALTHY`: the selected check passed with no relevant limitation;
- `NOTICE`: an observation or limitation that is not itself a fault, such as
  no AAAA record, VPN, Fake-IP possibility, or Ping/TCP method difference;
- `WARNING`: a partial, target-specific, or conflicting result that needs
  attention but does not prove a complete outage;
- `ERROR`: strong evidence that a required stage is unavailable, such as an
  explicit no-network state or a public path that cannot be reached after
  independent checks.

### 6.3 Overall status

The report can expose a compact overall status such as:

```text
HEALTHY
ATTENTION
LIMITED
UNKNOWN
```

The analyzer should use the strongest evidence and a conservative rule:

- all required checks pass → `HEALTHY`;
- only notices or optional capability differences → `HEALTHY` with notices;
- partial/conflicting or one important stage warning → `ATTENTION`;
- explicit no-network or corroborated required-path failure → `LIMITED`;
- insufficient or contradictory evidence → `UNKNOWN`.

This prevents one missing IPv6 record or one failed ICMP-like probe from
turning the whole report into a definitive failure.

## 7. Proposed Data Model

The v2 model should be structured but small enough to serialize locally and
inspect in the professional view.

```text
DiagnosticReportV2
├─ timestamp
├─ durationMs
├─ overallStatus
├─ summary
├─ networkSnapshot
├─ checks: List<DiagnosticCheck>
├─ findings: List<DiagnosticFindingV2>
└─ recommendations: List<DiagnosticRecommendation>
```

### 7.1 Report

```text
DiagnosticReportV2(
    timestamp,
    durationMs,
    overallStatus,
    summary,
    networkSnapshot,
    checks,
    findings,
    recommendations,
)
```

`timestamp` and `durationMs` describe the report run. `summary` is a concise
qualified sentence. `networkSnapshot` is an observed context, not a diagnosis.

### 7.2 Check

```text
DiagnosticCheck(
    id,
    stage,
    name,
    status,
    severity,
    summary,
    target,
    method,
    observedAt,
    details,
)
```

Recommended check IDs are:

```text
NETWORK_CONTEXT
GATEWAY_REACHABILITY
PUBLIC_CONNECTIVITY
DNS_RESOLUTION
DOMAIN_ACCESS
NETWORK_CHANGED
```

`details` should contain typed or bounded display data such as latency,
address family, packet counts, record counts, error category, or a skip reason.
It should not become an unrestricted framework or include platform objects.

### 7.3 Finding

```text
DiagnosticFindingV2(
    id,
    severity,
    title,
    description,
    evidenceCheckIds,
)
```

Every finding should reference the checks that support it. Descriptions should
use “可能”“未能确认”“建议检查” where the evidence is not conclusive.

### 7.4 Recommendation

```text
DiagnosticRecommendation(
    priority,
    title,
    action,
    reason,
)
```

Recommendations should be short and ordered. A normal report should usually
show two or three prioritized actions, not a long generic checklist.

Recommended groups:

1. 优先操作：retry, check another website, check another device/network;
2. 进一步检查：router WAN, Private DNS, VPN, address family;
3. 专业信息：review the target, method, timing, and raw result.

### 7.5 Compatibility with the current model

The current `DiagnosticReport`, `DiagnosticFinding`, and `FindingLevel` should
remain usable until the v2 analyzer and UI are introduced. The implementation
may use a versioned adapter or a new `DiagnosticReportV2` type rather than
silently changing the meaning of the existing model.

## 8. Probe Reuse Strategy

### 8.1 Ping v2

Diagnostic must call the existing `PingSessionEngine` with a diagnostic-owned
finite request. It must not implement another loop, statistics calculator, or
quality evaluator.

The request should:

- be finite and bounded;
- use a shorter diagnostic timeout than a user-selected long-running check
  where appropriate;
- make the protocol/address-family choice explicit in the result;
- report `SYSTEM_REACHABILITY` honestly when that is the mechanism;
- call `onProgress` for real stage/attempt progress if the report UI exposes it;
- disable Ping History persistence.

The final Diagnostic report records the aggregate check, not one History entry
per Ping attempt.

### 8.2 DNS v2

Diagnostic v2 should migrate to `DnsQueryEngine`/`LookupDnsV2UseCase` in its
first implementation because status, record type, TTL, and Fake-IP context are
needed by the fault matrix.

Migration should be staged:

1. Add a Diagnostic adapter that consumes `DnsLookupResult` and keeps the old
   `DnsResult` compatibility projection available for current callers/tests.
2. Make the v2 analyzer use the richer result directly.
3. Remove the old Diagnostic dependency only after existing report behavior and
   tests are intentionally replaced.

The Diagnostic invocation must set `persistHistory = false`. The DNS v2 query
must remain system-DNS only, with no hard-coded Google/Cloudflare/custom server
behavior.

### 8.3 TCP

Diagnostic may reuse `TcpPortChecker` for:

- a reviewed public endpoint on port 443;
- a returned domain address on port 443;
- a gateway or other target only if a separately approved target/port policy
  exists.

Each check remains a single host plus a single port. There is no port scan,
banner inspection, service identification, SSH client, or Telnet client.

The analyzer must treat “Ping failed, TCP succeeded” as evidence that the Ping
method is inconclusive or filtered, not as a contradiction to be hidden and
not as proof that all applications work.

## 9. UI and Execution-State Design

### 9.1 Default report view

The proposed user-facing structure is:

```text
网络诊断

总体状态
🟡 发现网络异常

本机网络       正常
本地网关       正常
公网连接       正常
DNS 解析       异常

诊断结论
公网连接正常，但 DNS 查询失败。
问题可能与 DNS 服务或网络配置有关。

建议
1. 重试 DNS 查询
2. 检查 Private DNS / VPN
3. 尝试其他网络

查看详细信息 >
```

The labels must describe each check, not imply a stronger conclusion than the
evidence supports. “公网连接” should not be shown as failed solely because a
system reachability check failed when TCP evidence passes.

### 9.2 Advanced details

The expanded area may show:

- captured network type, address family, gateway, VPN state, and confidence;
- target and protocol for each probe;
- Ping packet counts, loss, latency, jitter, and real method;
- DNS query types, status, record values, TTL when available, and configured
  DNS context;
- TCP host, port, result, latency, and classified error;
- skipped/not-applicable reasons;
- network-change marker;
- evidence links from findings to checks.

No configured DNS address may be labeled as the actual responder unless the
transport verifies that fact.

### 9.3 Real execution progress

The ViewModel should expose stage states such as:

```text
IDLE
RUNNING
COMPLETED
FAILED
CANCELLED
```

Each stage should have:

```text
PENDING
RUNNING
PASSED
NOTICE
FAILED
SKIPPED
```

The UI must receive callbacks from actual stage transitions. It must not use a
timer or simulated progress to imply that a probe ran. A stage that is skipped
because the prerequisite is unavailable should say why.

## 10. History Design

### 10.1 One report, one record

One automatic diagnostic run should continue to create exactly one shared
History entry:

```text
DiagnosticReportV2
    ↓
HistoryRecordFactory.reportV2
    ↓
HistoryRecorder
    ↓
HistoryRepository
    ↓
Room
```

Internal Gateway, Ping, DNS, public, and domain checks must not create separate
history records.

### 10.2 Versioned detail payload

The existing generic `detailJson` field is sufficient for the first design if
the payload is bounded and versioned. It should contain, when available:

- `schemaVersion`;
- report timestamp and duration;
- overall status and summary;
- compact network snapshot;
- each check's ID, stage, status, severity, target, method, and display data;
- findings and evidence check IDs;
- prioritized recommendations;
- DNS record details and TTL only when genuinely available;
- Ping and TCP aggregate results rather than every internal attempt.

No Room schema migration or report-specific repository is required by this
design.

### 10.3 Reopening a historical report

The current History UI shows a human-readable summary, not a full report detail
screen. The preferred future approach is:

- keep the title and summary useful even if a detail payload cannot be parsed;
- add a version-aware local detail projection for report records;
- let a future History detail UI reconstruct checks, findings, and suggestions
  from the stored payload;
- show “旧版本报告，部分详情不可用” rather than crashing when a payload is
  older or incomplete.

This task only records the design. It does not change History UI or the Room
schema.

## 11. Privacy and Permissions

- Diagnostic data remains on the device through the existing local History
  path.
- No account, cloud sync, analytics upload, or report upload is introduced.
- Public probes send only the normal packets needed by the selected check;
  they do not upload the user's report or local network inventory.
- The app must not log the full network environment or credentials.
- Local addresses, gateway, DNS configuration, and VPN state are sensitive
  context; only retain fields needed for local report usefulness and apply the
  existing local-first/privacy review to any new raw fields.
- `INTERNET` and `ACCESS_NETWORK_STATE` remain the relevant existing
  capabilities. No location permission is needed for this design.
- VPN presence may be reported when Android exposes it. The report must not
  infer a particular proxy/VPN product from DNS addresses or Fake-IP results.

## 12. Testing Matrix

### 12.1 Pure Kotlin unit tests

Use fake NetworkObservationProvider, PingSessionEngine, DnsQueryEngine,
TcpPortChecker, clocks, and stage callbacks. Cover at least:

- normal Wi-Fi with gateway/public/DNS/domain success;
- normal cellular data;
- explicit no active network;
- unknown network context caused by read failure;
- gateway absent (`NOT_APPLICABLE`);
- gateway failure with downstream stages continuing;
- public target fallback and all-public-target failure;
- gateway/public pass plus DNS timeout/network error;
- DNS `A` success + `AAAA` `NO_RECORDS`;
- DNS `A` success + `AAAA` timeout/error;
- explicit NXDOMAIN;
- Fake-IP address notice without DNS failure;
- IPv4 failure with IPv6 success;
- IPv6 failure with IPv4 success;
- Ping failure plus TCP success;
- DNS success plus domain TCP refusal/timeout;
- VPN notice;
- network switch during the run;
- one stage exception not stopping report generation;
- cancellation and no partial History write;
- one final report History record only;
- versioned History payload with unavailable fields omitted.

### 12.2 Android/platform tests

Use fakes for probe execution where possible and verify:

- active Network selection;
- LinkProperties/context mapping;
- Network change handling;
- DnsResolver v2 adapter status mapping;
- VPN/capability behavior;
- coroutine cancellation and lifecycle safety;
- no process-wide network binding side effects.

Run representative checks on API 31 and API 36/emulator or device because
Private DNS, VPN, captive portals, OEM network behavior, and validated-network
signals vary by platform.

### 12.3 Real-device matrix

The primary device is Sony Xperia 1 VII running Android 16. The planned manual
matrix includes:

- normal Wi-Fi;
- normal mobile data;
- Wi-Fi/mobile network switching during a run;
- gateway reachable and a controlled gateway-unavailable case;
- public path available/unavailable where safely testable;
- DNS normal, timeout, and Fake-IP environments;
- IPv4-only and dual-stack networks;
- VPN enabled and disabled;
- report completion, skipped stages, details, and one History entry.

Failures that are difficult to create reliably on a device must be injected
with fakes. Public services must not be a unit-test prerequisite.

## 13. Migration and Acceptance Plan

### Phase 1: Contracts and adapters

- add the v2 report/check/finding/recommendation contracts;
- define diagnostic-owned probe configuration;
- add adapters for Ping v2 and DNS v2;
- retain old report contracts for compatibility;
- add deterministic fake-based tests.

### Phase 2: Orchestration and analyzer

- implement the six-stage bounded flow;
- isolate stage errors;
- implement the judgment matrix and severity aggregation;
- implement one-report History serialization;
- keep all network work outside Compose.

### Phase 3: UI and device verification

- expose real stage progress;
- show summary/check rows by default;
- add expandable professional details;
- verify the report and History on API 31 and the Sony Xperia 1 VII / Android
  16;
- only then consider replacing the legacy report path.

Acceptance requires:

- every displayed finding references observable check evidence;
- no optional IPv6/Fake-IP/VPN observation is presented as a definite fault;
- Ping, DNS, and TCP mechanisms are accurately labeled;
- one automatic run produces one local History record;
- a single failed probe does not crash or erase the report;
- no public endpoint is the sole Internet verdict;
- unit, Android, and real-device verification are documented.

## 14. Non-Goals

This design does not authorize:

- AI or cloud analysis;
- automatic repair;
- LAN scanning or device discovery;
- port scanning;
- SSH/Telnet/SFTP functionality;
- HTTP monitoring or TLS inspection;
- DNS provider benchmarking, DoH, or DoT client implementation;
- background monitoring or unlimited Ping;
- new permissions without a separate review;
- Room schema changes in this design task;
- version changes, tags, or releases;
- changes to the current PRODUCT_PLAN.md.

## 15. Related Baseline Documents

- `docs/PRODUCT_PLAN.md`
- `docs/V0.2_PLAN.md`
- `docs/ARCHITECTURE.md`
- `docs/DECISIONS.md`
- `docs/PING_V2_DESIGN.md`
- `docs/DNS_V2_DESIGN.md`
