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

### V0.4.0 — Ready for Release

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

The v0.4.0 release preparation is complete and the version is **Ready for
Release**. The GitHub Release and tag remain pending the final APK smoke test;
v0.4.0 is not marked as Released here.

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
