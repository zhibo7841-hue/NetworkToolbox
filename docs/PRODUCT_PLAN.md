# Product Plan

## Project positioning

NetworkToolbox is an open-source Android network analysis and troubleshooting toolkit. It helps users understand network status, run focused checks, and obtain troubleshooting references.

NetworkToolbox is a network analysis tool, not an automatic troubleshooting or repair system. Its results provide observations and references; they do not claim to automatically identify every network failure or determine a single definitive cause.

## Target users

- General users who want to understand the network state of their device.
- HomeLab users who need focused, repeatable local network checks.
- Network learners who want to inspect and understand network behavior.
- Network operations personnel and support personnel who need local diagnostic information and reports.

The product is intended to assist investigation, not to replace network administrators or other qualified support personnel.

## Product principles

- Open source and auditable.
- Privacy first: network diagnostics should be transparent about what is observed and why.
- No ads.
- No account required.
- Local first: diagnostic data and history remain on the device unless a future, explicitly approved capability says otherwise.
- Evidence before conclusions: show measured results and relevant context without overstating certainty.
- Focused scope: network analysis and troubleshooting assistance only.

## User experience principle

NetworkToolbox combines an understandable experience for general users with professional-level underlying capabilities:

- By default, show network status, the result of the check, and a simple explanation.
- Allow advanced information to be expanded when needed, including raw detection data, technical parameters, and detailed results.

## Functional scope

### In scope

- Network information and network overview.
- IPv4 and IPv6 information.
- IPv4 subnet calculation.
- Ping.
- DNS checks.
- TCP port tests.
- Reports.
- Local history.
- Future directions: LAN scanning, Wi-Fi analysis, SSL/TLS inspection, iPerf, and WHOIS.

### Out of scope

- Automatic network repair.
- A system that automatically and accurately diagnoses all network failures.
- A full SSH terminal.
- A Telnet terminal.
- SFTP.
- Unrelated general-purpose utilities.

## Roadmap

### V0.1

The initial scope is frozen around a focused local diagnostic workflow:

- Dashboard and network overview.
- Network information.
- IPv4 subnet calculator.
- Ping.
- DNS.
- TCP port test.
- Reports.
- Local history.

### V0.2.0 — Released

NetworkToolbox v0.2.0 is released. Its published capabilities include:

- Enhanced Ping and DNS diagnostics.
- TCP Port Check and IPv4 subnet calculation.
- Enhanced network diagnostic reporting and local History.
- Improved Home network information, including IPv4/IPv6 presentation.
- LAN Scanner v1 with bounded automatic local-network scanning and custom IPv4
  start/end ranges.

The scope of the next version is TBD and requires a separate product decision.

### V0.2.x planning baseline

## V0.2 Network Diagnostic Enhancement

The goal of V0.2 is to evolve NetworkToolbox from a basic network toolkit into a more capable network diagnostic tool, while keeping the local-first and evidence-based product principles.

### First phase: diagnostic capability enhancements

#### Ping enhancement

Planned capabilities:

- Continuous Ping.
- Custom Ping count.
- Stop an in-progress check.
- Packet loss rate.
- Minimum latency.
- Maximum latency.
- Average latency.
- Jitter.
- IPv4/IPv6 selection.
- Network quality evaluation.

#### DNS enhancement

Planned capabilities:

- A records.
- AAAA records.
- CNAME records.
- MX records.
- TXT records.
- TTL information.
- DNS server display.
- Query duration.

#### Diagnostic Report enhancement

The report is planned to evolve from displaying detection results into a structured fault-analysis report. Planned improvements include:

- Explanations of detection results.
- Possible causes.
- Troubleshooting suggestions.

The report must continue to communicate uncertainty clearly. It is a troubleshooting aid, not a definitive automatic diagnosis system.

### Second phase: LAN Scanner

The LAN Scanner is planned as a later core module for local-network device discovery and analysis.

The confirmed LAN Scanner v1 scope includes two ways to select the IPv4 range:

- Automatic scanning of the current eligible local network, retaining the current /24 safety limit.
- An optional inclusive custom start/end range limited to RFC1918 private IPv4 addresses and at most 254 hosts.

Both modes use the same bounded discovery pipeline. Custom ranges do not add a port scanner, new discovery protocol, cloud service, or database schema migration. Cellular and VPN networks remain unavailable for LAN scanning.

Planned capabilities:

- Subnet scanning.
- Online device discovery.
- IP/MAC information.
- Vendor identification.
- Basic service identification.

Potential follow-up capabilities include:

- Favorite devices.
- Wake-on-LAN.
- mDNS.

### V0.3.0 — Released

The confirmed v0.3.0 release covers **LAN Device Identification Phase 1** and
**Traceroute Phase 1**. The release has passed its regression gate and is now
published on GitHub.

- LAN device identification will enrich already discovered LAN hosts with
  evidence-backed Hostname / Reverse DNS, mDNS / Bonjour, and UPnP / SSDP
  information when the local network and Android platform make it available.
- Traceroute will provide cancellable IPv4, IPv6, and automatic path checks for
  a domain or IP target, with per-hop results and cautious basic interpretation.
- Device identification and Traceroute results must preserve their actual
  source and uncertainty. They do not alter the v0.2.0 LAN Scanner online
  decision or claim a definitive network fault from a missing response.

MAC/OUI lookup, cloud identification, Wake-on-LAN, deep fingerprinting,
Traceroute maps, GeoIP, ASN data, and MTR are not part of v0.3.0 Phase 1. See
`docs/V0.3_PLAN.md` for the approved development phases and release gates.

### V0.4.0 — Released

The confirmed v0.4.0 product direction is **Automatic Diagnostics Phase 2**
and **Diagnostic Report Phase 1**. This version will strengthen the evidence-
driven diagnostic workflow before considering unrelated tool expansion.

The formal v0.4.0 baseline is documented in `docs/V0.4_PLAN.md` and
`docs/AUTOMATIC_DIAGNOSTICS_V2_DESIGN.md`. The planned experience will combine
local network observations, focused existing probes, conservative rule-based
interpretation, understandable recommendations, and an inspectable local
report. Diagnostic Report Phase 1 will provide one complete report with a
user-facing summary followed by bounded technical details. The same local
snapshot can be copied as text, saved as PDF, or shared as a PDF.

v0.4.0 does not include automatic network repair, cloud AI diagnosis, account
requirements, report upload, Wi-Fi Analyzer, Wake-on-LAN, SSL/TLS inspection,
WHOIS, iPerf, IPv6 Traceroute, Traceroute History, MAC/OUI, ASN, GeoIP, or MTR.

NetworkToolbox v0.4.0 is **Released**. The published GitHub Release and the
frozen `v0.4.0` tag contain this confirmed scope; no additional product scope
is implied by the release status.

### V0.5.x development line — Visual Refresh + LAN Device Management / Wake-on-LAN

The next development line is confirmed as a visual and device-management
refresh. It is intentionally expressed as a v0.5.x line rather than a locked
minor-version breakdown.

Core goals:

- **Visual Refresh Phase 1:** establish and incrementally adopt one
  NetworkToolbox design system across the app shell and existing screens.
- **LAN Device Center:** provide a focused home for managing already discovered
  local devices through separately approved implementation phases.
- **Wake-on-LAN:** add a local-device wake action only after its product,
  permission, and safety details are separately confirmed.

The v0.5 app shell uses three top-level destinations: **Home**, **Tools**, and
**Devices**. Home remains the concise network-status entry point, Tools remains
the home for the confirmed network utilities (including Wi-Fi Analyzer when it
is separately implemented), and Devices is the long-term entry point for the
LAN Device Center. Phase 2A now adds the approved Device Detail and local
Favorites foundation on top of the existing LAN Scanner state; it remains a
current-network device-management surface rather than a duplicate discovery
implementation.

The shared app Drawer provides three app-level secondary destinations:
**History**, **Privacy & Data**, and **About**. History is the unified home for
results from Ping, DNS, TCP, and automatic diagnostics; it is not a tool card
inside Tools. The current product has no user-facing Settings entry because it
has no confirmed configurable settings. A Settings destination may be
reintroduced only when a real configuration need is approved.

Tools uses a user-task-oriented taxonomy rather than a pure protocol or OSI
taxonomy:

- **Connectivity & Path**: Ping, TCP Port Check, and Traceroute.
- **Resolution & Services**: DNS Lookup and future service-oriented checks.
- **Network & Address**: IPv4 subnet calculation and LAN Scanner, plus future
  network-structure tools.
- **Performance**: shown only when an implemented performance tool such as
  iPerf exists.
- **Diagnostics**: the cross-tool Network Diagnostic entry.

The earlier app-shell change alone did not authorize Favorites, Wake-on-LAN,
MAC/OUI, device management, or any new discovery behavior. The Phase 2A scope
below is the separate approval for the limited Device Detail and Favorites
foundation; all other capabilities remain subject to their own product and
implementation decisions.

### V0.5 Phase 2A — LAN Device Center: Device Detail + Favorites Foundation

Phase 2A adds a conservative, local-only device-management foundation to the
top-level Devices surface:

- Read-only Device Detail for discovered devices and saved favorites.
- Local Favorites with immediate star toggle and Room persistence.
- Conservative identity matching that prefers a valid MAC, then a reliable
  protocol identity, and otherwise uses a network-scoped IPv4 identity.
- Opaque network scoping so favorites from a different local network are not
  mixed into the current Device Center.
- A favorite not observed in the current scan is shown as `本次未发现`; it is
  never presented as proof of being offline.
- Rescans update last-known metadata only when the conservative identity and
  network scope match.

Phase 2A does not add user-defined names or notes, automatic port scanning,
operating-system inference, background scanning, new discovery protocols,
Wake-on-LAN, or any cloud/account flow. Wake-on-LAN remains a later phase and
requires a separately confirmed data, permission, and safety design.

All new functionality must be introduced within the shared design system so
that the project does not continue accumulating inconsistent page styles and
visual debt. This line preserves the existing local-first, privacy, and
evidence-based product principles; it does not authorize automatic repair,
cloud analysis, or a new unrelated tool.

### V0.5 Phase 2B — LAN Device Center: Saved Device Profile and Custom Device Name

Phase 2B extends the Phase 2A Device Detail and Favorites foundation into a
generic local Saved Device Profile. The approved scope includes:

- Device Detail with an edit action for a user-defined display name.
- A saved profile that can independently retain `isFavorite` and `customName`.
- Local Room persistence for profile identity, network scope, last-known
  observation metadata, favorite state, and custom presentation name.
- Conservative identity matching using the existing MAC, protocol, and
  network-scoped IPv4 rules. This phase does not change the matching strategy.
- A display-name resolver whose priority is custom name, detected hostname /
  mDNS / UPnP identity, then the neutral unknown-device fallback.

Custom names are presentation overrides only. They do not rename a device,
alter detected hostname / mDNS / UPnP values, or create a new discovery
mechanism. A custom name may remain saved even when the device is not a
favorite; removing a favorite therefore does not remove a custom name, and
clearing a custom name does not clear the favorite flag. Ordinary scans only
enrich an existing profile and never create profiles for every discovered
host. Network scope continues to prevent a profile from being shown as the
same device on another local network.

This phase remains local-first and does not add notes, quick actions, device
type inference, background scanning, cloud/account behavior, or new
permissions. Wake-on-LAN remains the next separately approved phase and is
not implemented or implied by this custom-name work.

### V0.5 Phase 2C — LAN Device Center: Scan Session and Saved Profile Consistency

Phase 2C separates the transient result of a scan from the local profile that a
user intentionally saved:

- A `LanScanSession` is scoped to one captured network scope and opaque network
  fingerprint. Its observations, progress, completion state, and summary only
  describe that scan session.
- When the active network fingerprint changes, the old session is cancelled or
  invalidated immediately. Old observations, completion counts, and elapsed
  time are cleared; the app does not automatically start a scan on the new
  network.
- Saved device profiles remain local and persist across scans and app restarts.
  They mean that the user recognizes a device, not that the device is currently
  online. Profiles are shown only for the current eligible network scope before
  a scan and use neutral states such as `尚未进行本次扫描` or `本次未发现`.
- A completed session merges current observations with matching saved profiles
  without duplicating a device. Current observation metadata remains current;
  custom names and favorite state remain profile data. Ordinary discoveries are
  not auto-saved.

Phase 2C keeps the existing conservative discovery evidence and local-only
storage. It does not add Wake-on-LAN, quick actions, notes, device-type
inference, background or periodic scans, a historical network manager, new
discovery protocols, or a new Room migration. The Device Center and Tools LAN
Scanner share the same session boundary and presentation primitives while
retaining their existing page roles and range-selection boundaries.

### V1.0

Release scope and readiness criteria are not yet defined. The future directions listed above are not commitments for this release.

## SSH/Telnet boundary

SSH/Telnet-related scope is limited to service discovery, port detection, and basic identification. NetworkToolbox is not an SSH or Telnet client. It will not provide a complete interactive SSH terminal, SFTP, a Telnet client, terminal session management, or related remote-shell workflows.

## Privacy principles

- Collect and display only information needed for the selected diagnostic.
- Explain permissions and capability requirements before requesting them where practical.
- Keep reports and history local by default.
- Do not require an account for the core product.
- Do not include advertising or hidden tracking as part of the product principles.
- Do not present network observations as more certain than the available evidence supports.
