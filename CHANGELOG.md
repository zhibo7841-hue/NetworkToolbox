# Changelog

## Unreleased

### Maintenance

- Updated GitHub Actions dependencies to remove deprecated Node.js 20 runtime warnings.

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
