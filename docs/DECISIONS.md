# Decision Log

This log records the confirmed project decisions. New scope or changes to these decisions require an explicit update.

## D001: 网络工具箱定位调整

- Status: Accepted
- Decision: NetworkToolbox is an open-source Android network analysis and troubleshooting assistance tool.
- Boundary: It is not an automatic network repair tool and not an automatic system that can accurately diagnose every network failure.
- Consequence: Results must emphasize observed network state, checks, and references rather than claiming definitive automated diagnoses.

## D002: SSH/Telnet边界

- Status: Accepted
- Decision: NetworkToolbox is not an SSH or Telnet client.
- Boundary: Full interactive SSH/Telnet terminals, SFTP, and related remote-shell workflows are out of scope.
- Consequence: TCP connectivity checks must not expand into terminal or remote file-transfer features.

## D003: OSS许可证策略

- Status: Accepted
- Decision: The project uses the Apache License 2.0.
- Consequence: The repository includes the complete license text and future contributions/distributions must follow its terms.

## D004: Android技术路线

- Status: Accepted
- Decision: Use Kotlin, Jetpack Compose, Android Native APIs, Clean Architecture, and modular design.
- Consequence: These choices guide future implementation; this planning task does not create code modules.

## D005: 最低Android版本

- Status: Accepted
- Decision: The minimum supported Android version is Android API 31.
- Consequence: Future implementation and compatibility decisions must support API 31 unless this decision is explicitly revised.

## D006: 完全本地化原则

- Status: Accepted
- Decision: The product follows a local-first privacy model: no ads, no account requirement for core use, and diagnostic reports/history remain local by default.
- Consequence: Any future external data flow requires explicit product and privacy review; network access used by a diagnostic is not permission to upload user data.

## D007: V0.1范围冻结

- Status: Accepted
- Decision: V0.1 is limited to Dashboard, Network Info, IPv4 Calculator, Ping, DNS, TCP Port Test, Reports, and History.
- Consequence: LAN Scanner, Wi-Fi Analyzer, Traceroute, iPerf, SSL/TLS, WHOIS, SSH/Telnet, SFTP, and unrelated utilities are not part of V0.1.

## Adopt bottom navigation architecture

- Status: Accepted
- Decision: V0.1 uses three top-level destinations: Home, Tools, and Settings.
- Reason: Keep the home screen focused on current network status, recent diagnostics, and quick actions while allowing the confirmed tool set to grow without making the home screen an ever-expanding list.
- Consequence: Home contains the network status card, recent diagnostic summary, and quick actions for Ping, DNS, and Network Diagnostic. Tools contains the confirmed V0.1 tools grouped by purpose. Settings contains project information, local history management, and the local-first privacy statement.

## Decision: NetworkToolbox v0.2 Product Direction

- Date: 2026-08-25
- Status: Accepted
- Decision: V0.2 will prioritize strengthening the existing diagnostic capabilities before introducing a LAN Scanner.
- Product experience: NetworkToolbox will provide a two-layer experience: ordinary users see understandable conclusions and explanations by default, while professional users can expand detailed network data, raw detection results, and technical parameters.
- Priority: Ping, DNS, and Diagnostic Report enhancements come first. LAN Scanner is the later core module for local-network device discovery and analysis.
- Privacy: The local-first and privacy-protection principles remain in force. Diagnostic data stays on the device by default, and no external data flow is implied by this planning decision.
- Consequence: V0.2 planning must not expand directly into a large collection of unrelated tools or cross the confirmed SSH/Telnet boundary.

## Decision: LAN Scanner v1 custom IPv4 range

- Date: 2026-08-28
- Status: Accepted
- Decision: LAN Scanner v1 supports automatic scanning of the current eligible local network and an optional user-defined inclusive IPv4 start/end range.
- Constraints: Custom ranges must use RFC1918 private IPv4 addresses, contain no more than 254 addresses, and use the same bounded discovery pipeline as automatic scanning. Automatic scanning retains the current-network /24 safety limit.
- Safety: Cellular and VPN scanning remain blocked. Reachability timeout (500 ms), TCP timeout (250 ms), host concurrency (32), fallback ports, and TCP CONNECT SUCCESS-only discovery semantics remain unchanged. No new discovery protocol, permission, or Room schema migration is introduced.
- Consequence: Range selection is a presentation/use-case concern; both modes converge on `LanScanRange` and `LanDiscoveryEngine`, so discovery evidence and false-positive protections remain consistent.

## Decision: v0.2.0 release boundary

- Date: 2026-08-31
- Status: Accepted
- Decision: v0.2.0 is formally released after completing LAN Scanner v1,
  custom IPv4 ranges, and the confirmed diagnostic enhancements.
- Boundary: Device identification, mDNS, UPnP, MAC/OUI information,
  Wake-on-LAN, and other candidate capabilities are not retroactively included
  in v0.2.0.
- Consequence: The v0.2.0 Tag and release commit remain immutable. Any future
  version scope requires a separate product decision.

## Decision: v0.3.0 product direction and scope boundary

- Date: 2026-08-31
- Status: Accepted
- Decision: The next confirmed feature version is v0.3.0. Its scope is LAN
  Device Identification Phase 1 and Traceroute Phase 1.
- LAN identification: First-stage enrichment prioritizes Hostname / Reverse
  DNS, mDNS / Bonjour, and UPnP / SSDP information that devices openly provide.
  Results must retain their source. MAC/OUI, cloud identification, device
  fingerprinting, and Wake-on-LAN are excluded from this phase.
- Traceroute: First-stage work prioritizes reliable per-hop probing, accurate
  response/timeout semantics, cancellation, and cautious basic interpretation.
  Maps, GeoIP, ASN, automatic carrier identification, and MTR are excluded.
- Consequence: The v0.2.0 LAN Scanner discovery Core remains frozen. Device
  identification is an enrichment layer and must not turn an identification
  failure into an offline decision. Traceroute is introduced as an independent
  tool before any later Diagnostic integration is considered.

## Decision: IPv4 traceroute Core implementation

- Date: 2026-09-01
- Status: Accepted
- Decision: Implement the first Traceroute Core as an IPv4-only, app-owned UDP
  probe using Linux extended error queues through a minimal NDK/JNI adapter.
- Evidence boundary: The approach was validated as a technical candidate by
  the Sony Xperia 1 VII / XQ-FS72 Android 16 App-UID spike. It remains
  device-qualified and must expose explicit unsupported, timeout, malformed,
  permission, and network-change results on other devices.
- Safety: The implementation does not use Root, `SOCK_RAW`, `CAP_NET_RAW`, a
  system traceroute command, `ProcessBuilder`, TCP-as-traceroute, or a third-
  party packet library. Socket operations are bound to the selected Android
  `Network` and run off the main thread.
- Scope: This decision authorizes the Core only. IPv6, UI, History, automatic
  Diagnostic integration, reverse DNS, maps, ASN/GeoIP, and background tracing
  remain outside this task and require separate decisions.

## Decision: v0.4.0 Automatic Diagnostics and Diagnostic Report direction

- Date: 2026-09-04
- Status: Accepted
- Decision: The confirmed v0.4.0 mainline is Automatic Diagnostics Phase 2 plus
  Diagnostic Report Phase 1.
- Principles: The design is local-first, rule-driven, evidence-driven, and
  conservative. It must not depend on cloud AI, require an account, upload
  network data, or automatically modify Android network configuration.
- Evidence boundary: The product must distinguish confirmed facts, supported
  interpretations, uncertainty, possible causes, and recommendations. When
  evidence is insufficient, it must say that the issue was not confirmed rather
  than assert a deterministic fault.
- Product boundary: v0.4.0 improves the existing Network Information, Ping,
  DNS, TCP, Traceroute, and Diagnostic capabilities. It does not authorize
  Wi-Fi Analyzer, Wake-on-LAN, SSL/TLS, WHOIS, iPerf, IPv6 Traceroute,
  Traceroute History, MAC/OUI, ASN, GeoIP, MTR, cloud analysis, automatic
  repair, or a new unrelated tool.
- Report boundary: Diagnostic Report Phase 1 is an in-app, locally generated
  report that can be copied or shared as text. PDF generation, online reports,
  cloud synchronization, and automatic history expansion require separate
  approval.
- Consequence: Detailed rules belong in the automatic-diagnostics design
  baseline and later implementation tasks, not in this decision record.

## Decision: v0.4.0 complete diagnostic report export

- Date: 2026-09-05
- Status: Accepted
- Decision: Diagnostic Report Phase 1 uses one Complete Diagnostic Report. It
  presents the readable summary and recommendations first, followed by the
  bounded technical details needed by IT and HomeLab users. There is no
  user-facing concise/technical export split.
- Export: The same `DiagnosticReportPresentation` snapshot is used for the
  live report, restored history, text copy, local PDF saving, and PDF sharing.
  The export actions are Copy Text, Save PDF, and Share PDF.
- Privacy: PDF save and share require a fixed notice that the report can
  contain local IP addresses, gateway, configured DNS, VPN/Private DNS state,
  and probe targets. Files are selected or shared through Android platform
  APIs; report data is not uploaded and no account is required.
- Boundary: PDF uses the Android platform PDF API and the existing History
  schema. This decision does not authorize a PDF service, cloud report, new
  database columns, automatic repair, or any new diagnostic capability.

## Decision: NetworkToolbox v0.5 visual direction

- Date: 2026-09-07
- Status: Accepted
- Decision: The v0.5 development line adopts **Deep Network Blue** with a
  **Dark-first** visual direction and a formal Compose visual foundation.
- Brand keywords: Clear, Reliable, Focused, Professional, Calm, and Modern.
  The intended Chinese semantics are 清晰、可信、克制、专业、现代、柔和。
- Avoid: Hacker Terminal, Cyberpunk, Neon-heavy, game-like treatment,
  excessive glow, dashboard information overload, and an engineering-demo
  appearance.
- Consequence: New pages and new functionality must use the shared visual
  foundation. Existing screens will be migrated incrementally; this decision
  does not authorize a Big Bang UI rewrite or change any network behavior.

## Decision: v0.5 App Shell information architecture and Devices entry

- Date: 2026-09-09
- Status: Accepted
- Decision: The v0.5 app shell uses three top-level destinations: Home, Tools,
  and Devices. The shared Drawer directly contains History, Privacy & Data,
  and About. There is no user-facing Settings entry while the product has no
  confirmed configurable setting.
- Devices boundary: Devices is a real, usable top-level entry by reusing the
  existing LAN Scanner screen, ViewModel, and state. It is not a placeholder,
  fake device list, or second discovery implementation. A future LAN Device
  Center, Favorites, and Wake-on-LAN flow require separate implementation
  approval.
- Tool placement: Wi-Fi Analyzer remains part of Tools when it is implemented;
  it does not become a fourth top-level destination.
- Navigation: Secondary pages use caller-aware Back navigation. History,
  Privacy & Data, and About return to the Home, Tools, or Devices caller that
  opened the Drawer, and secondary pages do not expose the top-level Drawer
  action. Back returns to the originating top-level destination without
  automatically reopening the Drawer; History opened from a non-Drawer flow
  follows the same ordinary Back behavior. The Drawer remains transient
  rather than becoming a route. A report opened from History returns to
  History, while live diagnostic and saved-report presentation contexts keep
  their distinct titles.
- Affordances: Tool cards and Home quick-tool cards do not require a trailing
  chevron. Chevrons remain only where they communicate a meaningful detail or
  open action, including the Home Network Hero and existing History/Report
  affordances.
- Information architecture: History is an app-level secondary destination,
  not a Tools entry. Tools categories are user-task-oriented: Connectivity &
  Path, Resolution & Services, Network & Address, Performance only when an
  implemented tool exists, and Diagnostics.
- Visual cohesion: the Home Network Hero has a complete outlined/tonal
  boundary, Recent Diagnosis uses a compact outlined surface, and Tools uses
  compact outlined cards with one short description and no trailing chevron.
- Product evolution: Devices currently hosts the existing LAN Scanner as a
  transition surface. LAN Device Center and its later Favorites/Wake-on-LAN
  flows require separate implementation approval.
- Consequence: This information-architecture change does not modify LAN
  discovery, probe semantics, network tools, history storage, or any business
  logic. It only provides a stable shell for the current features and a future
  Devices evolution.

## Decision: Devices and LAN Scanner have separate roles

- Date: 2026-09-10
- Status: Accepted
- Decision: The top-level Devices destination is the Phase 1 foundation of the
  LAN Device Center. It presents the current network, offers a manual scan of
  the current IPv4 range, and shows the discovered device list using the
  existing LAN Scanner capability.
- Tool boundary: Tools -> 局域网扫描 remains the one-shot scanner and keeps
  both current-network and custom-range workflows. The Devices destination
  does not expose custom range inputs.
- Reuse: Both surfaces share the existing `LanScannerViewModel`,
  `RunLanScan`, discovery engine, and identity enrichment. They use separate
  presentation surfaces for their different roles; no second scanner or
  duplicate device domain model is introduced.
- Non-goals for Phase 1: This decision did not authorize Favorites, Wake-on-LAN,
  a full Device Detail page, background scanning, new discovery protocols,
  IPv6 LAN scanning, new permissions, or new persistence tables. Those items
  require a later decision; the separate Phase 2A decision below authorizes
  only the limited detail/favorites foundation.
- Consequence: A Devices scan preserves the existing scanner's real progress,
  cancellation, network-change handling, identification enrichment, and
  history behavior. The Device Center is a current-network entry point, not a
  new device-management feature.

## Decision: v0.5 LAN Device Center Phase 2B — Saved Device Profile and Custom Device Name

- Date: 2026-09-11
- Status: Accepted
- Decision: Phase 2B evolves the favorite-only persistence into a generic
  local Saved Device Profile. A database row is not synonymous with a
  favorite: the profile explicitly stores `isFavorite` and may independently
  store `customName` and future device-action configuration fields.
- Presentation: `customName` is a user-facing presentation override only. The
  automatically detected hostname, mDNS, UPnP, and other identity metadata
  remain retained as observed values and remain available to the detail view.
  The custom name does not rename or mutate the device itself.
- Independent state: Un-favoriting a profile does not delete a custom name.
  Clearing a custom name does not un-favorite the device. A profile with
  neither a favorite flag nor a custom name may be cleaned up as an orphan.
- Identity: The existing conservative matcher and network-scope boundary are
  unchanged. A valid MAC, reliable protocol identity, or network-scoped IPv4
  identity remains the basis for matching; hostname alone is not an identity.
- Persistence: Profiles remain on-device in Room through an additive v2 -> v3
  migration. Existing favorite rows migrate to explicit `isFavorite = true`
  with `customName = null`, while identity, scope, last-known metadata, and
  History remain intact.
- Consequence: LAN Scanner and Device Center may use the resolved custom
  display name when a safe profile match exists, but they do not gain device
  management actions. Wake-on-LAN remains a later phase requiring its own
  product, permission, and safety decision.

## Decision: v0.5 LAN Device Center Phase 2A — Device Detail and Favorites

- Date: 2026-09-11
- Status: Accepted
- Decision: Phase 2A adds a read-only Device Detail route and local Favorites
  to the top-level Devices surface. Tools -> 局域网扫描 remains a discovery
  tool whose device cards are not detail-navigation entry points.
- Identity: A saved device prefers a valid normalized MAC, then a reliable
  protocol identity such as a stable UPnP UDN, and otherwise a network-scoped
  IPv4 identity. Hostname alone is never a saved identity. A false merge is
  more dangerous than a missed match, so stronger identities never silently
  fall back to weaker identities.
- Persistence: Favorites are stored locally in Room through one repository and
  an additive v1 -> v2 migration. Existing History data must be preserved; no
  destructive migration is permitted.
- Observation semantics: A favorite not seen in the current scan is retained
  and shown as `本次未发现`. Missing from one scan is not equivalent to offline,
  and `lastSeenAt` is not an offline-duration claim.
- Network scope: Matching is limited to an opaque scope derived from the
  current eligible local network. Raw SSID or scope data is not used as the
  sole device identity and is not shown as a device identifier.
- Detail boundary: Device Detail may show observed identity metadata, roles,
  network relation, and observation status. It does not rename devices, add
  notes, scan ports automatically, infer an operating system, or perform
  device actions.
- Product boundary: Wake-on-LAN remains a later phase. Its data basis,
  permission behavior, and safety semantics require a separate decision.

## Decision: v0.5 LAN Device Center Phase 2C — Scan Session and Network Consistency

- Date: 2026-09-11
- Status: Accepted
- Decision: Current LAN observations belong to a transient `LanScanSession`.
  Saved Device Profiles are a separate local persistence concept and are not a
  synonym for current online state.
- Network boundary: Every scan captures the existing opaque network scope and
  a separate opaque network fingerprint. Session validity is not decided from
  the device IP address alone; a same-subnet network change must be able to
  invalidate the old session when the fingerprint changes.
- Network change: When the observed fingerprint changes, the active scan is
  cancelled, old observations and terminal summary data are discarded, and the
  UI returns to the current network's not-scanned state. The app does not
  automatically rescan, and switching back to a previous network does not
  restore its old transient session.
- Profiles: Profiles for the current scope may be shown before a scan with a
  neutral `尚未进行本次扫描` state. A profile absent from a completed scan is
  shown as `本次未发现`, never as online or offline. Only an intentional save
  action creates a profile; ordinary scan observations only update a matching
  existing profile's metadata.
- Presentation: Device Center and Tools -> LAN Scanner reuse the same
  profile/observation merge and compact device-card presentation while keeping
  the Device Center current-network-only boundary and the Tools custom-range
  workflow.
- Persistence and scope: Room remains at version 3; no migration or schema
  change is introduced. The session lifecycle is held by the ViewModel and
  stale callbacks are rejected by scan generation. No Wake-on-LAN, quick
  actions, notes, background scans, historical network manager, or new
  discovery protocol is authorized by this decision.
