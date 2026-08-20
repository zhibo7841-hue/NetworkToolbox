# Product Plan

## Project positioning

NetworkToolbox is an open-source Android network analysis and troubleshooting toolkit. It helps users understand network status, run focused checks, and obtain troubleshooting references.

NetworkToolbox is a network analysis tool, not an automatic troubleshooting or repair system. Its results provide observations and references; they do not claim to automatically identify every network failure or determine a single definitive cause.

## Target users

- Android users who want to understand the network state of their device.
- Developers and testers who need focused, repeatable network checks.
- Network learners and support personnel who need local diagnostic information and reports.

The product is intended to assist investigation, not to replace network administrators or other qualified support personnel.

## Product principles

- Open source and auditable.
- Privacy first: network diagnostics should be transparent about what is observed and why.
- No ads.
- No account required.
- Local first: diagnostic data and history remain on the device unless a future, explicitly approved capability says otherwise.
- Evidence before conclusions: show measured results and relevant context without overstating certainty.
- Focused scope: network analysis and troubleshooting assistance only.

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

### V0.2

Release scope is not yet defined. Feature commitments will be recorded only after a separate planning decision.

### V0.3

Release scope is not yet defined. Feature commitments will be recorded only after a separate planning decision.

### V1.0

Release scope and readiness criteria are not yet defined. The future directions listed above are not commitments for this release.

## SSH/Telnet boundary

NetworkToolbox may inspect network reachability and TCP connectivity, but it is not an SSH or Telnet client. It will not provide a complete interactive SSH/Telnet terminal, terminal session management, SFTP, or related remote-shell workflows.

## Privacy principles

- Collect and display only information needed for the selected diagnostic.
- Explain permissions and capability requirements before requesting them where practical.
- Keep reports and history local by default.
- Do not require an account for the core product.
- Do not include advertising or hidden tracking as part of the product principles.
- Do not present network observations as more certain than the available evidence supports.
