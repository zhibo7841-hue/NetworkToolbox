# NetworkToolbox Automatic Diagnostics v2 and Diagnostic Report Phase 1

Status: Design baseline only

Date: 2026-09-04

Target release: v0.4.0

This document defines the product, evidence, rule, architecture, privacy, and
testing baseline for v0.4.0. It does not implement production code, change
Android permissions, change the current probe defaults, change Room, or start
Task 049.

## 1. Product goal and boundary

NetworkToolbox v0.4.0 is the first version whose main experience is a
structured diagnostic rather than a collection of independent tools:

```text
observe network state
        ↓
run focused existing probes
        ↓
preserve measured evidence
        ↓
aggregate evidence
        ↓
describe a possible problem boundary
        ↓
recommend safe next actions
        ↓
show or share a local report
```

Automatic Diagnostics Phase 2 is not “run every tool once”. It should select
the smallest set of checks needed to narrow the likely failure boundary. The
result is a troubleshooting aid, not a replacement for a network engineer and
not a guarantee that every failure can be located.

The product remains:

- local-first and usable without an account;
- rule-driven and deterministic at the interpretation layer;
- evidence-driven and explicit about uncertainty;
- free of automatic network repair and Android network reset behavior;
- free of cloud AI dependency and report upload.

Diagnostic Report Phase 1 means an in-app report, text copy, and explicit
Android Share Sheet text sharing. It does not mean PDF generation, an online
report URL, QR export, cloud synchronization, or a new report database.

## 2. Current implementation audit

This section records the repository as found on 2026-09-04. It is deliberately
separate from the v0.4 proposal.

### 2.1 Current diagnostic call chain

The active v2 report path is:

```text
ReportScreen
    ↓
ReportViewModel
    ↓
RunDiagnosticV2UseCase
    ├─ DefaultDiagnosticPipeline
    │    ├─ NetworkRepository.observeNetworkContext().first()
    │    ├─ PingSessionEngine for a gateway, when testable
    │    ├─ TcpPortChecker for public targets
    │    ├─ DnsQueryEngine for A + AAAA
    │    └─ TcpPortChecker for returned domain addresses
    ├─ DefaultDiagnosticAnalyzerV2
    └─ HistoryRecorder (one final report record)
```

The legacy `GenerateDiagnosticReportUseCase`, `BasicDiagnosticAnalyzer`, and
the legacy `DiagnosticReport` remain in the repository for compatibility. The
v2 Report UI and ViewModel use `RunDiagnosticV2UseCase`; the old chain must not
be removed by design work alone.

### 2.2 Current automatic flow

`DefaultDiagnosticPipeline` currently performs these real stages:

1. Reads the first `NetworkContext` from `NetworkRepository` and creates a
   network check.
2. Stops network-dependent work after an explicit
   `activeNetworkAvailable == false`, marking dependent stages skipped.
3. Checks a non-cellular, usable gateway with the existing
   `PingSessionEngine`. The current diagnostic request is finite: three
   attempts, 100 ms interval, and 2,000 ms timeout.
4. Probes the centralized public targets `223.5.5.5:443` and `1.1.1.1:443`
   through `TcpPortChecker`. A successful target is positive evidence; one
   failed target is not enough to declare the Internet unavailable.
5. Uses `DnsQueryEngine` for A and AAAA against the current Android network,
   through the Android `DnsResolver.rawQuery` adapter. Configured DNS servers
   are context, not asserted response-server identity.
6. Uses TCP port 443 against up to two distinct A/AAAA addresses returned by
   the DNS result. This is a target-address path check, not HTTP, TLS, or
   website health verification.
7. Reconciles public evidence with domain evidence and records an explicit
   network-change check when the observed network identity changes.

Each stage catches ordinary adapter failures and continues where possible;
coroutine cancellation is rethrown. Internal probes do not create individual
history records. The final analyzed report is serialized once through the
existing `HistoryRecorder` boundary.

### 2.3 Current network information

`NetworkRepository` exposes `Flow<NetworkContext>`. The Android repository uses
`ConnectivityManager`, `NetworkCapabilities`, `LinkProperties`, and Wi-Fi
transport information where the platform exposes it. The current context can
contain:

- connection type;
- IPv4 and IPv6 addresses, including an address list;
- the selected gateway, with the shared IPv4-default-route preference;
- configured DNS addresses;
- VPN, validated, and active-network flags;
- Wi-Fi name and signal level when Android makes them available;
- IPv4 prefix length and interface name;
- Private DNS active/name fields where reported.

`NetworkContext.unknown()` and `noActiveNetwork()` are intentionally different,
but a platform read failure can still produce an unknown observation. v0.4
must not infer “no network” from a missing field alone.

### 2.4 Existing tool capabilities

| Capability | Current implementation | Reliable boundary for v0.4 |
|---|---|---|
| Local network information | `AndroidNetworkRepository` and `NetworkContext` | Observed active-network facts only; missing fields remain unknown |
| Ping | `PingSessionEngine` backed by system reachability | Best-effort reachability with its real method label; not guaranteed ICMP |
| DNS | `DnsQueryEngine` plus `DnsResolver.rawQuery` and parser | A/AAAA/CNAME/MX/TXT, TTL and structured response status when the response provides them |
| TCP Port Check | `TcpPortChecker` and Android `Socket` connect | One host plus one port; success means TCP handshake succeeded |
| Traceroute | App-owned UDP/NDK IPv4 Phase 1 implementation | Standalone, cancellable IPv4 path evidence; partial hops are not a fault verdict |
| LAN Scanner | Bounded local IPv4 discovery with 32 workers, 500 ms reachability, 250 ms TCP fallback, and identity enrichment | Explicit local-network tool; not a normal Internet diagnostic stage |
| VPN / Fake-IP | VPN transport observation and `198.18.0.0/15` classifier | Context/notice that changes interpretation; neither is a fault by itself |

The current implementation therefore already has a useful Diagnostic v2
pipeline, but it is not yet a full v0.4 diagnosis product. It lacks an
explicit observation model, finding confidence, a distinct primary diagnosis,
user-problem context, and a report export contract.

## 3. Formal v0.4 product scope

### 3.1 MUST HAVE

- Progressive Automatic Diagnostics for the current network.
- Network/IP/gateway/Internet/DNS basic evidence collection.
- Reuse of existing Ping, DNS, TCP, and Network Information abstractions.
- Conservative, deterministic evidence aggregation and rule evaluation.
- Six to eight high-confidence primary findings, with evidence references.
- Overall diagnosis separated from individual check status.
- Short, prioritized recommendations that a user can execute safely.
- Retry/Verify that reports whether an earlier observation is still present;
  it must not claim that NetworkToolbox repaired anything.
- Diagnostic Report Phase 1 in the app.
- Copy and explicit Android Share Sheet text sharing.
- Unit-testable rule logic and fake-driven orchestration tests.

### 3.2 SHOULD HAVE / CONDITIONAL

- A user-supplied target for target-specific diagnosis.
- An optional problem-type entry point after the base flow is stable.
- A conditional “run path check” action using the existing Traceroute tool.
- Safe deep links to Android Wi-Fi or network settings where supported.
- A user-facing choice between a concise report and technical details before
  copy/share.

These items must not make the default diagnosis longer or turn a target-
specific observation into a whole-network verdict.

### 3.3 OUT OF SCOPE

- Automatic repair, DNS changes, VPN shutdown, route changes, or network reset.
- Cloud AI, account requirements, cloud synchronization, analytics upload, or
  report upload.
- PDF, online report URL, QR report, or server-hosted report.
- Automatic full `/24` LAN scanning during ordinary Internet diagnosis.
- Wi-Fi Analyzer, Wake-on-LAN, SSL/TLS inspection, WHOIS, iPerf, IPv6
  Traceroute, Traceroute History, MAC/OUI, ASN, GeoIP, MTR, or unrelated tools.
- Definitive claims that a router, ISP, DNS provider, proxy, or website is
  broken based on one probe.

## 4. Market / Product Reference

The following is a product-design reference, not an endorsement and not a
claim that all vendor internals are public.

### 4.1 Fing Connectivity Report / Network Health

Public Fing documentation describes a desktop Connectivity Report that covers
DNS, DHCP, gateway information, common destinations, and pass/fail connectivity
checks. It also provides more detail for a line item and can export a PDF for
email/support use. Fing's desktop product additionally describes background
network health monitoring, device visibility, alerts, and built-in Ping,
Traceroute, and DNS tools.

Sources:

- [Fing Connectivity Report](https://help.fing.com/hc/en-us/articles/14544543720860-Connectivity-Report-Check-Network-Health)
- [Fing Desktop](https://www.fing.com/desktop/)

| Question | Assessment |
|---|---|
| What it checks | Network/device health, DNS, DHCP, gateway, common destinations, and broader network inventory depending on product surface |
| How it concludes | Named health checks, pass/fail lines, explanations, and a dashboard-level health view |
| Repair | Fing documents network controls such as blocking devices; the cited Connectivity Report is primarily a report/check experience, not an Android network-reset workflow |
| Report | Yes; detailed report and PDF/email export are documented |
| Cloud/account | Fing documents account-based synchronization for remote access on its desktop product; the cited report page does not establish that every local check requires cloud service |
| Audience | General users plus HomeLab/network users, with desktop-oriented detail |
| Borrow | At-a-glance status, line-level detail, report export, and a dashboard that explains what each check means |
| Avoid | Broad monitoring/account assumptions, device-control behavior, and a dashboard that implies more certainty than the evidence supports |

### 4.2 RouteThis Helps / Self-Help

RouteThis publicly describes a commercial ISP and smart-home support platform.
Its Helps/Self-Help experience uses a phone on the home network, asks about the
user's issue, performs network scans, and presents context-driven troubleshooting
or action items. The platform also documents agent-facing workflows, support
integration, and verification of resolution. This is useful evidence for
problem-oriented UX, but the provider/agent workflow is not a local-only model.

Sources:

- [RouteThis FAQ](https://www.routethis.com/faq)
- [RouteThis Helps UI and context-driven troubleshooting](https://support.routethis.com/hc/en-us/articles/25972189340557-RouteThis-Helps-iOS-and-Android-New-UI-and-Features-May-24-Resolve)
- [RouteThis platform](https://www.routethis.com/)

| Question | Assessment |
|---|---|
| What it checks | Home Wi-Fi/network conditions, connected devices, coverage and issue-specific data depending on partner configuration |
| How it concludes | Context-driven problem flow, actionable issue items, and guided resolution paths |
| Repair | Some product/partner flows include fix-it or provider-assisted resolution and verification; it is not merely a passive report |
| Report | The support workflow preserves scan/action context for agents; the exact consumer report format varies by integration |
| Cloud/account | Its documented provider, agent, code, and Resolve workflows depend on a commercial service ecosystem; it is not an appropriate local-only dependency for NetworkToolbox |
| Audience | ISP customers, smart-home customers, support agents, and service providers |
| Borrow | Ask what the user is trying to solve, progressive guidance, clear next action, and verify-after-action experience |
| Avoid | Provider lock-in, mandatory remote service, opaque fleet analytics, and any “fixed” claim when the app did not perform a repair |

### 4.3 360 断网急救箱

360's official product page describes broad one-click inspection of hardware and
software/network configuration, system services, protocols, proxy settings,
and browser configuration. It also explicitly advertises automatic repair,
restoring defaults, and “strong repair”. A separate 360 help article describes
the flow as full diagnosis followed by repair and, if needed, strong repair.

Sources:

- [360 断网急救箱](https://weishi.360.cn/work/dwjjx/)
- [360 断网急救箱 usage description](https://news.safe.360.cn/n/10445.html)

| Question | Assessment |
|---|---|
| What it checks | Broad Windows-oriented system, service, protocol, proxy, browser, and network configuration items |
| How it concludes | Full diagnostic followed by suggested or automatic repair actions |
| Repair | Yes; restoring defaults and strong repair are central advertised behaviors |
| Report | The public description emphasizes diagnosis/repair rather than an auditable portable report schema |
| Cloud/account | The product page mentions the 360 Security Brain; implementation dependency details are not treated as verified here |
| Audience | General Windows users who want a one-click fix |
| Borrow | The understandable “diagnose → suggest → verify” mental model |
| Avoid | Automatic reset, strong repair, hidden changes, system-wide assumptions, and treating a single desktop heuristic as proof |

### 4.4 NetworkToolbox position

NetworkToolbox should sit between Fing and RouteThis:

- From Fing, take the readable network-health summary, detailed line-level
  evidence, and report-oriented presentation.
- From RouteThis, take issue-oriented entry points, progressive action guidance,
  and retry/verify framing, while remaining independent of an ISP backend.
- From 360, take the intuitive “diagnosis → suggestion → verification” rhythm,
  but not automatic repair or network-reset behavior.

The differentiator is an auditable local evidence chain: the app explains what
it observed, what it infers, what remains uncertain, and why it recommends the
next check.

## 5. User problem entry

Candidate entry choices are:

- 完全无法上网;
- 网页 / App 打不开;
- 网络速度慢或不稳定;
- 局域网设备无法访问;
- 不确定，全面检查.

Recommendation: do not make a problem selection mandatory in the first v0.4
MVP. Start with “不确定，全面检查” and run the short common evidence flow.
Problem selection is valuable for conditional tests and recommendations, but
making it mandatory would multiply rules, UI states, and test combinations
before the base evidence model is proven.

The extension should be designed as a `DiagnosticIntent` input for a later
2.1 increment. In v0.4, an optional target may be supplied for target-specific
diagnosis, but an absent target must not be treated as a failed service test.

## 6. Diagnostic stages and time budget

The proposed default flow is:

```text
Network State
    ↓
IP Configuration
    ↓
Gateway / Local Exit
    ↓
Internet Connectivity
    ↓
DNS
    ↓
Target / Service (only when a target is available)
    ↓
Conditional Advanced Test
    ↓
Finding Aggregation
    ↓
Diagnosis
    ↓
Recommendation
    ↓
Report
```

### Stage 1: Network State

Observe active-network availability, connection type, VPN, validated flag,
addresses, gateway and configured DNS context. A platform-confirmed absence of
an active network is strong evidence for `NO_ACTIVE_NETWORK`. A missing field,
permission-limited observation, or unknown transport is not automatically a
failure.

### Stage 2: IP Configuration

Check whether the active network exposes at least one usable local address and
the address family has a plausible route. Do not report an IP configuration
problem merely because IPv4 is absent: a valid IPv6-only network can be usable.
Likewise, a link-local-only IPv6 address is not proof of public IPv6 access.

### Stage 3: Gateway / Local Exit

For Wi-Fi/Ethernet with an IPv4 default gateway, prefer that gateway for the
local probe. A cellular network is `NOT_APPLICABLE` for traditional gateway
testing. An unscoped IPv6 link-local gateway is `UNKNOWN` unless interface
scope is reliably preserved.

Use the existing finite Ping session. A timeout means “the selected reachability
method did not receive a response”; it does not mean “the gateway is broken”.

### Stage 4: Internet Connectivity

Use Android `NET_CAPABILITY_VALIDATED` as supporting context, not as the sole
verdict. Use a small centralized set of DNS-independent TCP 443 targets. At
least one successful approved target is positive evidence. All approved target
probes failing is a warning/error candidate only when there is no contradictory
successful domain path and the probes actually completed.

Do not add a large target list or frequent retries. Target ownership, regional
availability, and privacy should be reviewed separately before changing the
current target set.

### Stage 5: DNS

Use the existing DNS v2 contract on the current Android network. A + AAAA
should be the default request. Treat `NO_RECORDS` as a normal response category,
not a transport failure. A success with no AAAA record is not DNS failure.
NXDOMAIN means the queried name was reported nonexistent; it does not by itself
prove that the DNS service or network is broken.

Do not compare the user's resolver with a public resolver by default and do not
automatically change DNS. v0.4 asks “does DNS work?”, not “which resolver is
fastest?”.

### Stage 6: Target / Service

Run only when the user supplied a target or the product explicitly labels a
neutral baseline domain check as such. Combine DNS records with a single TCP
443 check per selected address. A successful TCP handshake proves only that
the TCP connection was accepted. It does not prove HTTP, TLS, application
health, or that the website is operating normally.

### Stage 7: Conditional Advanced Tests

Traceroute is not part of every default run. Recommended trigger:

- base network/IP evidence exists;
- DNS is usable;
- an explicit target path fails or remains ambiguous;
- the user chooses “查看路由追踪” or an equivalent action.

Automatic triggering is allowed only as a future opt-in advanced mode with a
clear time budget. A `PARTIAL` Traceroute result is path evidence, not a target
failure verdict.

LAN Scanner must not run during an ordinary Internet diagnosis. It is relevant
only after an explicit “局域网设备无法访问” intent and a separate user
action, because a bounded `/24` scan is comparatively expensive and exposes
local-device information.

### Time budget

The initial user-visible result should arrive in approximately 5–15 seconds on
a normal network, with stage progress visible during that time. The exact
timeout values of existing Ping, DNS, TCP, and Traceroute tools are not changed
by this design. A user-triggered Traceroute may take tens of seconds and a
user-triggered LAN scan may take longer; those are advanced tools, not Quick
Diagnosis stages.

## 7. Evidence model

The model intentionally separates raw facts, check outcomes, interpretations,
and recommendations.

### 7.1 Observation

An observation is a bounded fact from a named source:

```text
DiagnosticObservation
├─ id
├─ stage
├─ source                  (NetworkRepository / Ping / DNS / TCP / Traceroute)
├─ value                   (typed or bounded display value)
├─ observedAt
├─ networkFingerprint
└─ evidenceState           (CONFIRMED / UNAVAILABLE / UNKNOWN)
```

Examples:

- `activeNetworkAvailable = false`, source `NetworkRepository`;
- `ipv4 = 10.0.1.206`, source `LinkProperties`;
- `gatewayProbe = TIMEOUT`, source `PingSessionEngine`;
- `tcp(1.1.1.1:443) = SUCCESS`, source `TcpPortChecker`;
- `DNS A = 203.0.113.10`, source `DnsQueryEngine`.

Platform objects, credentials, raw packet payloads, and unbounded logs do not
belong in the domain model.

### 7.2 Check outcome

Reuse the current stage/check concepts where possible:

```text
DiagnosticCheck
├─ id / stage / name
├─ status: PASS / FAIL / NO_RECORDS / NOT_APPLICABLE / SKIPPED / UNKNOWN
├─ severity
├─ summary
├─ target / method
├─ observedAt
├─ networkFingerprint
└─ bounded rawData
```

`PASS` is local to that check. It must not be interpreted as “all networking
works”.

### 7.3 Evidence level and confidence

Use two related but distinct concepts:

```text
EvidenceLevel = CONFIRMED | SUPPORTED | INCONCLUSIVE | CONTRADICTED
Confidence    = HIGH | MEDIUM | LOW
```

- `CONFIRMED`: the source has unambiguous semantics, such as an explicit no
  active network, a valid DNS NXDOMAIN response, or a successful TCP connect.
- `SUPPORTED`: multiple observations point in the same direction but do not
  prove the physical or administrative cause.
- `INCONCLUSIVE`: the platform did not expose enough information, a probe
  timed out, or results conflict.
- `CONTRADICTED`: a proposed interpretation is opposed by stronger evidence,
  such as gateway timeout while an independent Internet TCP path succeeds.

Confidence is about the interpretation, not the prettiness of the UI. A
single successful probe can be `CONFIRMED` for “this TCP connection succeeded”
but only `LOW` confidence for “the whole Internet is healthy”.

### 7.4 Network fingerprint

Each network-dependent observation should carry a stable, privacy-minimized
fingerprint for the active network identity. It may be derived from platform
network identity, transport, interface, and relevant link properties, but must
not contain credentials or be uploaded.

If the fingerprint changes during the run:

- stop scheduling new dependent stages where practical;
- mark the run `NETWORK_CHANGED`;
- do not combine results from both networks into a strong diagnosis;
- offer “重新检测”.

## 8. Finding, diagnosis, and recommendation models

### 8.1 Finding

A finding is an interpreted local boundary, not a raw probe:

```text
DiagnosticFinding
├─ id
├─ title
├─ description
├─ severity
├─ evidenceLevel
├─ confidence
├─ evidenceObservationIds / evidenceCheckIds
├─ possibleCauses
└─ recommendedActionIds
```

Example: “网关未响应当前可达性探测，但公网 TCP 连接正常” is a finding
with `NOTICE`, `CONTRADICTED` gateway-failure interpretation, and low/medium
confidence about the reason.

### 8.2 Overall diagnosis

The report needs a distinct overall diagnosis object:

```text
DiagnosticDiagnosis
├─ status: NORMAL / ATTENTION / LIMITED / UNKNOWN
├─ title
├─ explanation
├─ primaryFindingId?
├─ confidence
└─ possibleCauses
```

`NORMAL` means no tested required boundary currently shows a material problem;
it does not mean every app or website works. `UNKNOWN` means the run or the
evidence was insufficient; it is not a hidden failure state.

### 8.3 Recommendation

Recommendations are short, ordered, safe, and observable:

```text
DiagnosticRecommendation
├─ priority
├─ title
├─ action
├─ reason
├─ relatedFindingIds
└─ verificationHint
```

The first report should normally expose 2–3 actions. A recommendation may open
the existing DNS, Ping, Traceroute, or Android settings surface, but it must not
silently modify configuration.

## 9. Severity and status policy

Keep the existing severity vocabulary for compatibility:

| Severity | Meaning | Examples |
|---|---|---|
| HEALTHY | The selected check passed without a material limitation | TCP target connected; network context available |
| NOTICE | Context or limitation that is not itself a fault | VPN active, Fake-IP possibility, no AAAA record, gateway timeout while Internet works |
| WARNING | Partial, target-specific, or conflicting evidence requiring attention | DNS partial failure, gateway/public boundary unclear, domain TCP failed |
| ERROR | Strong evidence that a required user capability is unavailable | Explicit no active network; corroborated public path failure |

Check status and severity remain separate. `TIMEOUT` is a probe outcome; it is
not automatically `ERROR`. A cellular gateway is `NOT_APPLICABLE`, not failed.

False positive is more dangerous than incomplete diagnosis. The analyzer must
prefer “未能确认”“可能”“建议进一步检查” over a definitive claim when
evidence is insufficient.

## 10. Initial failure categories

The following eight categories are the v0.4 design candidates. They are kept
small because each category needs deterministic rules and real-device review.

### 10.1 No Active Network

Required evidence: platform explicitly reports `activeNetworkAvailable == false`.

Finding: 设备当前未连接可用网络。

Severity/confidence: `ERROR` / `HIGH` for the observed state.

Cannot conclude this when the repository read failed or only a field is null.

Recommendations: check Wi-Fi/mobile data, airplane mode, SIM/APN, then rerun.

### 10.2 IP Configuration Problem

Required evidence: an active network exists but no usable address of any
available family is exposed, or the active family has an explicit invalid/no
route condition.

Supporting evidence: missing local address plus failed local/public probes.

Contradicting evidence: a usable IPv6-only network or successful TCP path.

Severity/confidence: `WARNING` or `ERROR` only when the absence is explicit;
otherwise `UNKNOWN` / low confidence.

No IPv4 address alone is not an IP problem. No IPv6 address alone is not an IP
problem.

### 10.3 Local Gateway / Local Exit Problem

Required evidence for a meaningful diagnosis: a usable gateway is present,
the gateway probe fails, and public probes also fail or remain unavailable.

Supporting evidence: no local address or repeated local-path failures.

Contradicting evidence: successful independent public TCP connectivity or
successful domain access.

Severity/confidence: `WARNING` / `MEDIUM` at most for the first version. The
output must name possible local link, access-point, VLAN, gateway, WAN, or
upstream causes rather than one definite cause.

Gateway timeout + Internet success must produce an explanatory `NOTICE`, not a
gateway failure diagnosis.

### 10.4 Internet Connectivity Problem

Required evidence: all actually completed approved DNS-independent public TCP
targets fail, with no successful domain path and no strong contradictory
evidence.

Supporting evidence: Android `VALIDATED == false` or a gateway that responds.

Contradicting evidence: any independent approved TCP target or a user target
that successfully establishes a TCP path.

Severity/confidence: `WARNING` initially; `ERROR` only if the evidence set is
explicitly unavailable across all approved paths and the product policy makes
the required capability a hard prerequisite. Do not use one public IP as the
whole Internet verdict.

### 10.5 DNS Resolution Problem

Required evidence: direct/public connectivity is confirmed and the system DNS
query returns timeout, network error, invalid response, or a real transport
failure.

`NXDOMAIN` is a distinct finding about the queried name, not automatic proof
of a broken DNS service. A + AAAA `NO_RECORDS` is success for the selected DNS
query and should not be a failure.

Severity/confidence: `WARNING` or `ERROR` according to the exact status and
whether public connectivity is confirmed.

### 10.6 Target Service / Path Problem

Required evidence: a user-selected target resolves, base network evidence is
healthy, and the selected target's TCP 443 path fails or is refused.

Supporting evidence: alternate public target succeeds and the failure repeats.

Contradicting evidence: target TCP succeeds or only a Traceroute intermediate
hop is missing.

Severity/confidence: `WARNING` / `MEDIUM`; wording must be “目标服务或访问
路径可能异常”, never “网站已宕机”.

### 10.7 VPN / Proxy / Fake-IP Environment

Required evidence: Android reports VPN transport or DNS returns a value in the
known `198.18.0.0/15` special-use range.

Finding: context/notice only. It can reduce confidence in direct physical-path
interpretation and should be mentioned when explaining DNS, routing, target,
or Traceroute results.

Severity/confidence: `NOTICE` / `HIGH` for the observed context; no automatic
fault diagnosis.

### 10.8 Network Appears Normal

Required evidence: required checks pass sufficiently for the selected scope,
with no unresolved contradiction that changes the conclusion.

Finding: tested network path appears normal.

Severity/confidence: overall `NORMAL`, confidence may be `MEDIUM` rather than
absolute. The report should say that an untested app, service, speed problem,
or intermittent failure may still exist.

## 11. Initial rule matrix

| Inputs | Conditions | Finding | Diagnosis | Severity | Confidence | Recommendation |
|---|---|---|---|---|---|---|
| Network context | Explicit no active network | No Active Network | Device has no usable active network | ERROR | HIGH | Check Wi-Fi/mobile data and rerun |
| Network context | Read unavailable/unknown | Network state unconfirmed | Cannot determine current network state | NOTICE | LOW | Retry when network is stable |
| IP | Active network but no usable address of any family | IP configuration may be invalid | Local configuration may block connectivity | WARNING | MEDIUM | Reconnect network and inspect system settings |
| IP | IPv4 absent, usable IPv6 present | IPv4 unavailable only | Do not classify as whole-network failure | NOTICE | HIGH | Continue with IPv6-aware checks |
| Gateway/public | Gateway probe fails and all public paths fail | Local or upstream boundary needs attention | Gateway/local/WAN/upstream path may be involved | WARNING | MEDIUM | Check access point/router WAN and another network |
| Gateway/public | Gateway timeout but public TCP succeeds | Gateway probe response unavailable | Internet path is still observed working | NOTICE | HIGH for contradiction | Do not replace gateway; rerun or inspect details |
| Public | All completed approved public TCP targets fail and no domain path succeeds | Public path not confirmed | WAN/upstream/route/target policy may be involved | WARNING | MEDIUM | Check another device/network and router WAN |
| Public/DNS | Public TCP succeeds; DNS transport fails | DNS resolution problem | DNS path/configuration may be abnormal | WARNING/ERROR | HIGH for boundary | Retry DNS; check Private DNS/VPN/proxy |
| DNS | A records + AAAA `NO_RECORDS` | No IPv6 records published | IPv4 resolution remains normal | NOTICE | HIGH | No repair required; inspect only if IPv6 is expected |
| DNS | NXDOMAIN | Queried name reported nonexistent | Name may not exist or may be incorrectly queried | NOTICE/WARNING | HIGH for response | Verify name/zone; do not call DNS globally broken |
| DNS | `198.18.0.0/15` result | Possible Fake-IP environment | DNS result interpretation needs context | NOTICE | HIGH for range, LOW for cause | Check proxy/VPN/Private DNS if access is unexpected |
| VPN | VPN transport active | VPN context | Results may describe the tunnel path | NOTICE | HIGH | Interpret results in VPN context; rerun without VPN only manually |
| DNS/target | DNS succeeds; selected target TCP fails | Target path/service may be abnormal | Target-specific issue possible | WARNING | MEDIUM | Try another target and inspect target/address family |
| Traceroute | Intermediate hop absent but later hop responds | Intermediate response filtered/limited | Path continues; missing hop is not a fault | NOTICE | HIGH | No action unless end-to-end path also fails |
| Network change | Fingerprint changes during run | Mixed network evidence | Current report cannot make a strong combined diagnosis | NOTICE | HIGH | Rerun on a stable network |
| All required checks | Required checks pass; no material contradiction | Network appears normal | No problem confirmed in tested scope | HEALTHY | MEDIUM | If issue persists, run target-specific or advanced check |

## 12. Gateway false-positive policy

The gateway is a particularly dangerous source of false alarms:

- Wi-Fi/Ethernet with both default routes uses the IPv4 default gateway for the
  ordinary local probe, as established by the shared Network Context logic.
- Cellular gateway probing remains `NOT_APPLICABLE`, even when Android exposes
  an internal next-hop address.
- An IPv6 link-local gateway is probeable only if the interface scope is
  reliably preserved. Otherwise it is `UNKNOWN` / “未确认”, not failure.
- `Ping` system reachability timeout means “no response to this method”.
- If gateway probe fails but an independent public TCP path succeeds, emit an
  explanatory notice and downgrade any gateway-failure interpretation.
- Only gateway failure combined with public failure can support a local/upstream
  boundary finding, and even then the cause remains qualified.

The user-facing sentence should be:

> 默认网关没有响应当前探测，但公网连接正常。部分设备可能不响应此类探测，因此不能据此判断网关故障。

It must not be “路由器坏了”.

## 13. DNS, Internet, Fake-IP, VPN, and target rules

### DNS

The analyzer consumes the structured DNS v2 result. It retains A/AAAA
per-type semantics, CNAME/MX/TXT when a conditional query is selected, TTL only
when present in a valid response, and `actualResponder = null` unless the
transport truly proves the responder. Configured DNS addresses remain labeled
as configuration context.

### Internet

Public evidence is a combination of:

- Android validated-network context;
- multiple small, centralized, DNS-independent TCP 443 probes;
- optional domain-path evidence when a target exists.

The analyzer should report the observed boundary, not a provider identity. One
target failing means the target failed; it does not mean the Internet failed.

### Fake-IP

`198.18.0.0/15` is a special-use observation that may indicate a Fake-IP DNS
environment. It must:

- create a `NOTICE` environment finding;
- lower confidence in direct destination interpretation where relevant;
- keep DNS itself successful if the DNS response was valid;
- avoid claiming a specific proxy application;
- avoid claiming that the destination is physically located in that range;
- avoid turning Traceroute or target failure into a definite routing fault.

### VPN

VPN active is diagnostic context, not error. It may affect DNS, routing, target
address selection, and Fake-IP interpretation. The report says that results
may describe the VPN tunnel's path; it does not name an app or proxy without
direct evidence.

### Target-specific access

Target diagnosis is conditional and should be clearly labeled with the target
the user supplied. It separates:

1. DNS response;
2. TCP connect to the resolved address;
3. optional Traceroute path evidence;
4. untested application/HTTPS behavior.

Traceroute `PARTIAL`, a single TCP refusal, or a missing intermediate hop is
never enough to assert that a website is down.

## 14. Architecture and reuse strategy

The v0.4 implementation should extend the existing architecture in small
boundaries:

```text
DiagnosticScreen
    ↓
DiagnosticViewModel
    ↓
RunAutomaticDiagnosticUseCase
    ↓
DiagnosticOrchestrator
    ├─ NetworkRepository
    ├─ DiagnosticNetworkObserver
    ├─ PingSessionEngine adapter
    ├─ DnsQueryEngine / DNS v2 adapter
    ├─ TcpPortChecker adapter
    ├─ optional TracerouteEngine action
    ├─ optional LAN Scanner action (never default)
    └─ DiagnosticAnalyzer
            ↓
        DiagnosticReport
```

### Reuse existing capabilities

- `NetworkRepository` and `NetworkContext` remain the platform boundary.
- `PingSessionEngine` remains the only Ping session/statistics implementation.
- `DnsQueryEngine` remains the structured DNS v2 boundary.
- `TcpPortChecker` remains one host/one port and preserves refused/timeout/
  unknown semantics.
- `TracerouteEngine` remains independent and is invoked only on demand or a
  separately approved advanced branch.
- `LanDiscoveryEngine` and `RunLanScan` remain explicit local scanning flows;
  they are not silently called by Internet diagnosis.
- `HistoryRecorder` remains the shared local persistence boundary if a future
  product decision re-enables automatic report history.

### New abstractions worth adding

Only these are justified for v0.4:

- `DiagnosticObservation` / typed evidence projection;
- `DiagnosticIntent` for optional target/problem context;
- `DiagnosticOrchestrator` or an equivalent bounded coordinator;
- `DiagnosticRule`/`DiagnosticAnalyzer` with deterministic pure evaluation;
- `DiagnosticDiagnosis` and report/export projection;
- `NetworkFingerprint` boundary if the existing fingerprint cannot be reused.

Do not create a generic plug-in framework, a second Ping/DNS/TCP engine, or a
large rule DSL for this release.

No Compose code may call Android APIs, engines, sockets, resolvers, or Room.

## 15. Progressive UI and execution state

The UI should expose real stage callbacks rather than a timer:

```text
Idle
  ↓ start
Running(
  networkState = COMPLETED,
  ip = RUNNING,
  gateway = PENDING,
  internet = PENDING,
  dns = PENDING,
  target = NOT_TESTED
)
  ↓
Completed(report) / Cancelled / Error
```

Suggested user-facing progress:

```text
正在检查当前网络       ✓ 网络已连接
正在检查 IP 配置        ✓ IPv4 已配置
正在检查本地网关        → 检测中
正在检查互联网          ○ 未开始
正在检查 DNS            ○ 未开始
正在生成报告            ○ 未开始
```

Each stage status maps to understandable Chinese:

| Internal state | User-facing text |
|---|---|
| HEALTHY/PASS | 正常 |
| NOTICE | 提示 |
| WARNING | 异常 |
| ERROR | 严重异常 |
| UNKNOWN | 未确定 |
| NOT_APPLICABLE | 不适用 |
| SKIPPED/NOT_TESTED | 未执行 |

The default result page should show:

```text
网络诊断

总体状态
🟢 网络状态正常

本机网络       正常
本地网关       正常 / 未确定 / 不适用
公网连接       正常 / 未确定 / 异常
DNS 解析       正常 / 无记录 / 异常
域名访问       未执行 / 正常 / 异常

诊断结论
...

建议
1. ...
2. ...

查看详细信息
```

Technical details expand by stage and show target, method, timings, records,
latency, address family, and skip/unknown reasons. They must not show raw JSON
as the primary user experience.

## 16. Retry / Verify

After a finding, the user can choose “重新检测”. The new run receives a new
timestamp and network fingerprint. The report compares the relevant finding
IDs and says one of:

- “此前检测到的问题当前未再次出现。”
- “此前检测到的问题仍然存在或证据相似。”
- “当前无法确认，网络状态或证据发生了变化。”

The app must not display “已修复” unless NetworkToolbox itself later performs
and verifies an explicitly approved repair action. v0.4 does not perform repair.

## 17. Diagnostic Report Phase 1

### 17.1 Report schema

The design keeps the schema versioned and bounded:

```text
DiagnosticReportV1
├─ schemaVersion
├─ appVersion
├─ timestamp
├─ durationMs
├─ userIntent? / target?
├─ networkContextSummary
├─ checks[]
├─ observations[] (bounded, optional technical layer)
├─ findings[]
├─ diagnosis
├─ recommendations[]
├─ verificationContext?
└─ optionalTracerouteSummary?
```

The user report contains conclusion, explanation, recommendation, and compact
stage outcomes. Technical details may add local IP, prefix, gateway, DNS list,
VPN/Private DNS context, Ping statistics, TCP target/port, DNS records/TTL,
and a concise Traceroute summary when the user explicitly ran it.

### 17.2 Two report levels

**User Report** is for ordinary users:

- overall status;
- what was checked;
- confirmed boundary;
- possible explanation;
- 2–3 next actions;
- no raw packet payloads or verbose internal identifiers.

**Technical Details** is for IT/HomeLab users:

- timestamps and duration;
- connection type, local address/prefix, gateway, configured DNS;
- validated/VPN/Private DNS context;
- Ping method and aggregate measurements;
- DNS query type/status/records/TTL when real;
- TCP host/port/error classification;
- Traceroute hop summary if explicitly executed.

### 17.3 Phase 1 export

- In-app report viewing is required.
- Copy as plain text is required.
- Android Share Sheet text sharing is required after a deliberate user action.
- The share surface should let the user choose concise or technical text if the
  implementation cost remains small; otherwise default to concise text with a
  clear “包含技术网络信息” notice.
- PDF, cloud URL, QR, account sync, and report server are out of scope.

### 17.4 History decision

v0.4 MVP does not add a new automatic report-history policy. Existing v0.3
History behavior and schema must not be expanded by this design. If the current
app continues to record a completed Diagnostic through the existing shared
recorder, it remains one local record; no internal stage gets its own record.

Making reports a durable archive, comparing runs in History, or adding report
retention/privacy controls requires a separate decision because it affects
database lifecycle and sensitive-data retention.

## 18. Privacy and permissions

### 18.1 Information classification

Potentially sensitive network information includes:

- local IPv4/IPv6 addresses and prefix;
- gateway and configured DNS addresses;
- SSID, if Android exposes it;
- BSSID, if ever obtained;
- discovered LAN device names/addresses;
- user-selected target domain or IP;
- VPN/Private DNS context;
- Traceroute hop addresses.

The default user report should prefer summaries and omit BSSID, device
inventory, raw MAC, and full Traceroute details. Technical details may include
the local address/gateway/DNS needed for troubleshooting, but the share action
must tell the user that network information is included.

Field-level redaction is a useful future enhancement, not a reason to expand
the v0.4 MVP into a complex privacy editor. At minimum, provide a clear
pre-share confirmation and never upload automatically.

### 18.2 Network traffic

Starting a diagnostic authorizes the selected normal network tests to contact:

- approved DNS infrastructure through the system DNS path;
- approved direct public TCP probe targets;
- a user-selected target when target diagnosis is chosen;
- the local gateway or local network only for the selected check.

These packets are tests, not report uploads. NetworkToolbox must not send the
diagnostic report, local inventory, credentials, or user history to its own
server. Future server communication requires a separate product/privacy
decision.

### 18.3 Permission budget

Reuse the current permissions and platform capabilities:

- `INTERNET`;
- `ACCESS_NETWORK_STATE`;
- `ACCESS_WIFI_STATE`;
- `CHANGE_WIFI_MULTICAST_STATE` only for the already existing local discovery
  behavior.

No new dangerous permission is authorized by this design. Location, Nearby
Wi-Fi, notification, accessibility, VPN control, or storage permissions are
`REQUIRES PRODUCT APPROVAL` if a later implementation proposes them.

SSID should remain optional where Android privacy restrictions prevent reliable
access without extra permission or user settings.

## 19. Failure, cancellation, and network change

### 19.1 Diagnosis failed vs no problem found

- `NORMAL`: tested required evidence is sufficient and no material problem was
  found in the tested scope.
- `UNKNOWN`: the run could not establish enough evidence because of timeout,
  permission/platform limits, unavailable adapter, or contradiction.
- `FAILED`: reserved for an orchestration/implementation failure that prevented
  a valid report from being assembled; it is not a disguised network finding.
- `NOT_TESTED` / `SKIPPED`: a stage was intentionally not run, such as a
  cellular gateway or absent user target.

An unknown report must not become “网络故障” merely because a probe could not
run.

### 19.2 Cancellation

Cancellation stops scheduling new probes, propagates through existing engines,
and returns a user-visible “诊断已停止”. It must not save a completed report
for an incomplete run. The UI returns to a restartable state.

### 19.3 Network change

If Wi-Fi changes to mobile, Wi-Fi A changes to Wi-Fi B, or VPN transport changes
mid-run, the orchestrator records `NETWORK_CHANGED`, avoids a strong mixed
diagnosis, and recommends rerunning on a stable network. It should stop or
discard later stages where continuing would mix evidence, subject to the
existing engine's cancellation guarantees.

## 20. Five complete examples

### A. Network Normal

**Evidence**

- active network: Wi-Fi, available;
- IPv4 and gateway observed;
- gateway Ping session receives replies;
- at least one approved public TCP 443 target succeeds;
- DNS A succeeds and AAAA either succeeds or returns `NO_RECORDS`;
- target-specific check not selected.

**Finding**

- Local network, gateway, public path, and DNS checks pass;
- no IPv6 record, if observed, is a `NOTICE` only.

**Diagnosis**

`NORMAL`, confidence `MEDIUM`: “基础网络连接在本次检测中表现正常。”

**Explanation**

The selected local and public paths responded. This does not test every app or
website.

**Recommendation**

1. If the problem continues, run a target-specific check.
2. Compare another device or network if the issue is intermittent.

**Report output**

```text
总体状态：网络状态正常
本机网络：正常
本地网关：正常
公网连接：正常
DNS 解析：正常
诊断结论：基础网络连接在本次检测中表现正常。
```

### B. DNS Problem

**Evidence**

- active network and IP available;
- public TCP 443 target succeeds;
- DNS v2 returns `TIMEOUT` or `NETWORK_ERROR` for the selected query;
- no successful domain path is available because resolution failed.

**Finding**

“公网连接正常，但当前 DNS 查询未正常完成。” Evidence level `CONFIRMED`
for the boundary, confidence `HIGH` for DNS-path abnormality, not for the DNS
provider's cause.

**Diagnosis**

`ATTENTION`: “可能存在 DNS 解析问题。”

**Explanation**

The device reached a public TCP endpoint, while the system DNS request did not
complete. Possible causes include DNS service, Private DNS, VPN/proxy, or
network configuration.

**Recommendation**

1. 重新执行一次 DNS 查询。
2. 检查 Private DNS、VPN 或代理设置。
3. 尝试切换 Wi-Fi 与移动网络。

**Report output**

```text
总体状态：发现网络异常
公网连接：正常
DNS 解析：异常
诊断结论：公网连接正常，但 DNS 查询未正常完成。
```

### C. Local / Gateway Problem

**Evidence**

- active Wi-Fi network and local IPv4 exist;
- IPv4 default gateway is present;
- gateway system reachability probe times out;
- all approved public TCP targets fail;
- no successful domain path contradicts this boundary.

**Finding**

“网关与公网连通性检测均未通过，问题可能位于本地链路、接入点、路由器
WAN 或上游路径。” Evidence level `SUPPORTED`, confidence `MEDIUM`.

**Diagnosis**

`ATTENTION`: “本地或上游网络路径可能需要检查。”

**Explanation**

The evidence stops at the local/public boundary. It cannot identify a bad
router, cable, ISP, or VLAN from this run.

**Recommendation**

1. 检查 Wi-Fi/接入点与路由器状态。
2. 查看路由器 WAN 或上游连接。
3. 用其他设备或移动网络进行对比。

**Report output**

```text
总体状态：发现网络异常
本地网关：异常
公网连接：未通过
诊断结论：问题可能位于本地链路、网关、WAN 或上游路径。
```

### D. Target Service Problem

**Evidence**

- user selected `service.example`;
- base network and DNS checks pass;
- resolved address is available;
- TCP 443 to the selected target address is refused or times out;
- an independent public target succeeds.

**Finding**

“域名解析成功，但目标 TCP 连接未建立。” Evidence level `SUPPORTED`,
confidence `MEDIUM` for a target-specific path issue.

**Diagnosis**

`ATTENTION`: “目标服务或访问路径可能异常。”

**Explanation**

The selected target path did not accept the test. This does not prove that the
website is down; service policy, address family, firewall, or target-side
filtering remain possible.

**Recommendation**

1. 尝试打开其他网站或服务。
2. 对比 IPv4/IPv6 结果和 TCP 错误类型。
3. 按需运行路由追踪。

**Report output**

```text
总体状态：发现目标访问异常
公网连接：正常
DNS 解析：正常
目标访问：未建立
诊断结论：目标服务或访问路径可能异常。
```

### E. VPN + Fake-IP Environment

**Evidence**

- Android reports VPN transport;
- DNS response is valid and contains `198.18.0.0/15`;
- public/domain outcomes may describe the VPN path.

**Finding**

“检测到 VPN 网络和特殊用途地址，可能存在 Fake-IP DNS 环境。” Evidence
level `CONFIRMED` for the observed context, confidence `LOW` for the exact
proxy product or cause.

**Diagnosis**

`NORMAL` or `ATTENTION` according to actual connectivity; the VPN/Fake-IP
notice alone never raises the overall result to an error.

**Explanation**

The address is in a special-use range and may be supplied by a proxy/Fake-IP
environment. It is not automatically a DNS error or a real destination
location.

**Recommendation**

1. If the target cannot be reached, inspect VPN/Private DNS/proxy settings.
2. Repeat the test on a stable network or with the user's normal configuration.

**Report output**

```text
总体状态：网络状态正常 / 需要关注
提示：检测到 VPN 网络
提示：检测到特殊用途地址，可能存在 Fake-IP DNS 环境。
说明：这些提示用于解释检测环境，不等同于网络故障。
```

## 21. Rule testing baseline

The rule layer must be pure Kotlin, deterministic, and independent of Compose,
Android `Context`, sockets, resolvers, and wall-clock timing. Each rule should
be testable from a small fixture of observations/checks.

Minimum scenarios:

- normal Wi-Fi;
- normal cellular without gateway applicability;
- Ethernet;
- no active network;
- unknown network read;
- IP configuration with IPv4-only, IPv6-only, and dual-stack context;
- gateway success;
- gateway timeout + Internet success;
- gateway failure + public failure;
- public multi-target success/failure combinations;
- public success + DNS timeout/network error/invalid response;
- A success + AAAA `NO_RECORDS`;
- explicit NXDOMAIN;
- Fake-IP notice;
- VPN notice;
- IPv4/IPv6 contradictory path evidence;
- DNS success + target TCP refusal/timeout;
- Traceroute partial result not becoming target failure;
- network fingerprint change;
- cancellation without a completed report;
- permission/platform unavailability as UNKNOWN/NOT_TESTED;
- retry where a previous finding disappears or remains.

## 22. Android and real-device test matrix

### Pure unit tests

Use fake `NetworkRepository`, Ping, DNS, TCP, optional Traceroute, clock, and
stage callbacks. Assert rule outputs, evidence references, confidence, and
recommendations. No public network is allowed as a unit-test prerequisite.

### Android/platform tests

Cover active-network selection, LinkProperties mapping, validated capability,
VPN transport, Private DNS fields, current-network DNS selection, cancellation,
and network-change callbacks on API 31 and API 36 where practical.

### Sony Xperia 1 VII / Android 16

Manual scenarios:

- normal HomeLab Wi-Fi;
- mobile data with no traditional gateway;
- VPN/Fake-IP environment;
- controlled DNS failure if safely reproducible;
- target-specific failure;
- network switch during diagnosis;
- cancellation;
- report details, copy, share, and retry/verify.

Hard-to-create failures must be injected with fakes rather than manufactured by
dangerous device changes.

## 23. Implementation task split

The following is a proposed sequence after this design task. Each item should
be independently reviewable and testable.

### Task 049 — Diagnostic evidence and report contracts

Add the pure Kotlin observation, check projection, severity/confidence,
diagnosis, recommendation, intent, and versioned report contracts. Keep legacy
models compiling. Add serialization fixtures only.

### Task 050 — Diagnostic orchestration adapter

Extract a bounded orchestrator around the existing Network Repository, Ping v2,
DNS v2, and TCP adapters. Preserve current timeout, gateway, multi-target,
cellular, cancellation, and network-change semantics. Add fake-driven stage
tests.

### Task 051 — Conservative rule engine

Implement the rule matrix as pure deterministic rules. Add the eight primary
categories, evidence references, confidence, severity aggregation, and
recommendation prioritization. Do not add Compose conditions.

### Task 052 — Progressive Automatic Diagnostics UI

Connect real stage callbacks to the current Diagnostic screen. Add simple
problem/target context only if the contract remains optional. Keep unknown,
not-tested, and not-applicable visible in understandable language.

### Task 053 — Diagnostic Report Phase 1 export

Add in-app user/technical report projections, copy, Share Sheet text export, and
pre-share sensitive-information notice. No PDF, cloud, QR, or schema migration.

### Task 054 — Retry/Verify and historical compatibility

Add re-run comparison for finding IDs and versioned local report projection
without changing Room schema. Preserve one-report/one-record behavior if the
existing recorder remains enabled.

### Task 055 — Conditional advanced validation

Validate explicit target diagnosis and user-invoked Traceroute. Confirm that
Traceroute partial/no-response results do not become service failures and that
LAN Scanner is never silently launched by ordinary diagnosis.

### Task 056 — Sony Android 16 scenario gate

Run the real-device matrix, record evidence and limitations, and decide whether
v0.4.0 is ready for release. This task must not expand scope to v0.4.1 or v0.5.

## 24. Performance and energy goals

These are design targets, not changes to current probe defaults:

- Quick Diagnosis: first useful stage result in under a few seconds and a
  normal complete result approximately within 5–15 seconds.
- Advanced Traceroute: explicitly user-triggered, with visible progress and a
  clear longer-duration expectation.
- LAN Scan: explicitly user-triggered and never part of ordinary Internet
  diagnosis.
- Keep public target count small, avoid repeated background checks, and stop
  scheduling work after cancellation or network change.
- Reuse current timeout policies until device evidence justifies a separate
  decision; this document does not change Ping, DNS, TCP, or Traceroute values.

## 25. Acceptance criteria for the design baseline

The design is considered complete when:

- raw observation is distinct from finding and diagnosis;
- every finding can point to evidence;
- severity does not stand in for confidence;
- no active-network, IPv4, gateway, DNS, VPN, Fake-IP, or Traceroute rule is
  stronger than its evidence;
- a gateway timeout plus working Internet is explicitly safe;
- A + AAAA `NO_RECORDS` remains DNS success;
- public connectivity is not decided by one endpoint;
- target failure remains target-specific;
- Traceroute and LAN Scanner are conditional, not default heavy checks;
- cancellation and network changes are visible and do not create a false
  completed diagnosis;
- report content is local, bounded, versioned, and privacy-aware;
- MUST/SHOULD/OUT boundaries are explicit;
- the next implementation tasks are independently testable.

## 26. Related repository baselines

- `docs/PRODUCT_PLAN.md`
- `docs/V0.2_PLAN.md`
- `docs/V0.3_PLAN.md`
- `docs/V0.4_PLAN.md`
- `docs/ARCHITECTURE.md`
- `docs/DECISIONS.md`
- `docs/PING_V2_DESIGN.md`
- `docs/DNS_V2_DESIGN.md`
- `docs/DIAGNOSTIC_V2_DESIGN.md`
- `docs/TRACEROUTE_TECHNICAL_BASELINE.md`
- `docs/LAN_SCANNER_V1_DESIGN.md`
