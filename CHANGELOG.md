# Changelog

## Unreleased

### Maintenance

- Updated GitHub Actions dependencies to remove deprecated Node.js 20 runtime warnings.

## [0.3.0] - 2026-09-01

### Added

- IPv4 Traceroute with per-hop results and three-probe latency details.
- Start, stop, partial-path, cancellation, and network-change states for
  Traceroute.
- LAN device identification enrichment using Reverse DNS, mDNS / Bonjour, and
  SSDP / UPnP information when devices provide it.
- Source-labelled manufacturer, model, hostname, and service information for
  supported devices.

### Improved

- LAN Scanner device presentation and identity aggregation.
- Traceroute timeout, partial-response, Fake-IP, cancellation, and hop-list
  explanations.
- Android 16 mDNS lifecycle handling and callback-executor stability.

### Fixed

- Android 16 mDNS late-callback crashes.
- UPnP Description HTTP/XML compatibility and safety handling on Android 16.
- LAN Scanner and Traceroute state and presentation issues.

### Current limitations

- Traceroute currently supports IPv4 only.
- Some devices do not expose a name, manufacturer, model, or service metadata.
- Some intermediate routers may not respond to Traceroute probes; this does
  not by itself indicate a network fault.

## [0.2.0] - 2026-08-29

### Added

- LAN Scanner for bounded local IPv4 host discovery.
- Automatic scanning of the current eligible LAN IPv4 range.
- Custom RFC1918 IPv4 start/end scan ranges with a maximum of 254 addresses.
- LAN scan history records.
- TCP Port Check.
- Enhanced automatic network diagnostics.

### Improved

- Ping real-time statistics, continuous checks, and detailed network-quality results.
- DNS advanced record lookup, TTL details, Fake-IP explanations, and result presentation.
- Home network-status summaries, IPv4/IPv6 presentation, Diagnostic Report, local History, and LAN Scanner scan performance.

### Fixed

- LAN Scanner TCP false positives.
- Dual-stack Wi-Fi gateway selection in diagnostics.
- DNS Fake-IP diagnostic handling and DNS result aggregation semantics.
- The inability to modify a LAN scan range after a completed scan.

## 0.1.0

Initial release.

Features:

- Ping
- DNS
- TCP Port
- Subnet Calculator
- Diagnostic Report
- Local History
