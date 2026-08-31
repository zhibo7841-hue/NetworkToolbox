# Release Plan

## V0.1 development scope

V0.1 is the first focused local network analysis workflow. Its scope is frozen to the following areas.

### Included

- Dashboard
- Network Info
- IPv4 Calculator
- Ping
- DNS
- TCP Port Test
- Reports
- History

### Not included

- LAN Scanner
- Wi-Fi Analyzer
- Traceroute
- iPerf
- SSL/TLS
- WHOIS

SSH/Telnet terminal functionality, SFTP, automatic network repair, automatic definitive fault diagnosis, and unrelated utilities are also outside the product boundary.

## Release readiness

Detailed implementation acceptance criteria will be defined when Android development begins. They must remain traceable to the included V0.1 scope, the privacy principles, and the architecture direction.

## V0.2.0

- Status: Released (v0.2.0)
- Goal: Network Diagnostic Enhancement

Implemented areas:

- History persistence fix.
- Ping enhancement.
- DNS enhancement.
- Diagnostic Report upgrade.
- LAN Scanner v1, including bounded custom RFC1918 IPv4 scan ranges.

The v0.2 implementation remains bounded by the documented privacy, local-first, and evidence-based product principles. It does not include mDNS, UPnP, MAC/OUI identification, device details, Wake-on-LAN, or IPv6 LAN discovery.

V0.3 and V1.0 feature commitments are not defined by this document.
