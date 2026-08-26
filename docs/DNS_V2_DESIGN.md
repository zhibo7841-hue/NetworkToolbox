# NetworkToolbox DNS v2 Design

Status: Design only
Date: 2026-08-26
Scope: v0.2 DNS diagnostic enhancement

This document defines the proposed DNS v2 architecture and capability boundaries. It does not implement DNS v2, change the current DNS UI, add dependencies, change the database schema, or change the product roadmap.

## 1. Design Goals and Product Boundary

The current DNS tool answers a narrow question: can the system resolver return an IPv4 or IPv6 address for a domain? DNS v2 should grow this into a DNS resolution analysis tool while preserving the project's two-layer experience:

- Ordinary users see a clear result, a short explanation, and a small number of safe defaults.
- Professional users can expand the result to inspect record type, name, value, TTL, query method, DNS configuration, and timing when those fields are genuinely available.
- The app remains local-first. Query results and history stay on the device; no DNS result is uploaded.
- The app reports observations and possible causes. It must not claim that a single DNS result proves an ISP, router, or service failure.

DNS v2 is still a lookup and analysis feature. It is not a DNS server manager, DNS privacy bypass, DNS benchmark, DNS poisoning detector, or automatic repair system.

## 2. Current DNS Capability Audit

The following is the capability of the repository at the time of this design, based on the current source rather than the v0.2 plan.

### 2.1 Current engine and API

Current files:

- core/network/.../dns/DnsEngine.kt defines suspend fun lookup(domain: String): DnsResult.
- core/network/.../dns/DnsResult.kt contains domain, success, records, durationMs, method, and errorMessage.
- DnsRecord contains only type and value.
- DnsRecordType contains only A and AAAA.
- DnsMethod currently contains SYSTEM_RESOLVER and UNAVAILABLE.
- core/network/.../data/AndroidDnsEngine.kt uses InetAddress.getAllByName(domain) on Dispatchers.IO and maps Inet4Address/Inet6Address to A/AAAA records.

The current engine therefore uses the Android/Java system name-resolution API. It does not construct a DNS packet and does not inspect a DNS response.

### 2.2 Current input and error behavior

The current engine trims the input and rejects empty input, whitespace, a leading/trailing dot, and consecutive dots. This is a small application-level check, not a complete DNS hostname or IDNA validator. A resolver call is not made for these rejected inputs.

UnknownHostException is mapped to a generic system-resolver failure. The current implementation does not reliably distinguish NXDOMAIN, a timeout, an unavailable network, a server that did not respond, or another resolver failure. It also does not expose the Java exception to the UI.

### 2.3 Current UI and use-case chain

The current chain is:

~~~text
DnsScreen
    -> DnsViewModel
    -> LookupDnsUseCase
    -> DnsEngine
    -> AndroidDnsEngine
    -> InetAddress.getAllByName()
~~~

DnsScreen is a simple domain input and lookup page. It displays status, system-resolver method text, duration, A records, AAAA records, and a user-facing error. There are no record-type controls, DNS-server controls, TTL fields, or advanced details yet.

The Compose UI does not call Android DNS APIs directly.

### 2.4 What the current API can and cannot know

| Information | Current status | Reason |
|---|---|---|
| A records | Supported | InetAddress results are mapped to IPv4 addresses. |
| AAAA records | Supported | InetAddress results are mapped to IPv6 addresses. |
| CNAME | Not supported | The API returns address objects, not DNS resource records. |
| MX | Not supported | No MX RDATA is exposed. |
| TXT | Not supported | No TXT RDATA is exposed. |
| TTL | Not available | No DNS response is parsed. Resolver cache timing is not a record TTL. |
| Actual responding DNS server | Not available | InetAddress does not identify the server used for this lookup. |
| Configured DNS servers | Available elsewhere, not in current DNS result | NetworkContext/LinkProperties can expose configured link DNS addresses when available, but that is not proof of the responder for one query. |
| NXDOMAIN vs other failures | Not reliably distinguishable | A generic UnknownHostException is not a DNS response with an inspected RCODE. |
| Query timeout vs no network | Not reliably distinguishable | The current API does not return a structured DNS transport outcome. |
| Cache hit vs network query | Not available | The system resolver may use local/platform caching and the current result has no cache provenance. |

The current duration is useful as observed resolver-call duration, but it must not be presented as guaranteed wire time to a named DNS server.

### 2.5 Current History integration

DNS uses the shared HistoryRecorder; there is no DnsHistoryRepository. LookupDnsUseCase calls HistoryRecordFactory.dns(...) after a lookup when persistence is enabled. The report flow disables persistence for its internal DNS probe and writes one report record separately.

The current DNS history detail contains:

- domain;
- success;
- A record values;
- AAAA record values;
- duration.

The current history title is DNS · <domain> and its summary is the generic DNS lookup completed or DNS lookup failed. Method, query types, DNS server context, TTL, and structured error status are not currently stored.

The shared HistoryRecord already has a generic detailJson field. This can carry the additional DNS v2 detail without a Room table/schema change. The implementation phase will need to extend the shared factory/serialization, not create a DNS-specific repository.

### 2.6 Current Diagnostic Report integration

GenerateDiagnosticReportUseCase calls the shared DnsUseCase with the fixed domain example.com and disables history persistence for that internal probe. It passes the current DnsResult to DiagnosticAnalyzer.

BasicDiagnosticAnalyzer currently uses only dns.success to decide whether to add a generic DNS finding. It does not inspect A versus AAAA, TTL, query method, DNS server context, or duration. DNS v2 must therefore remain adaptable to the existing DnsResult during migration, while exposing richer data for a future Diagnostic Report v2. This task does not change the report analyzer or report UI.

### 2.7 Current tests and OSS research state

The current DNS tests use a fake resolver and do not depend on public DNS. They cover A, AAAA, both record types, resolver failure, invalid input, empty resolver output, and cancellation. The feature tests cover use-case persistence and ViewModel idle/loading/success/error states.

docs/OSS_RESEARCH.md is still a placeholder and contains no audited DNS library, maintenance assessment, or license conclusion. No third-party DNS dependency can therefore be recommended as an already-approved project choice.

## 3. DNS v2 Domain Model

The following is a proposed model. Names may be adjusted during implementation to match the existing package conventions, but the semantics should remain explicit.

### 3.1 Request

~~~kotlin
data class DnsLookupRequest(
    val queryName: String,
    val recordTypes: Set<DnsRecordType> = setOf(A, AAAA),
    val server: DnsServerSelection = SYSTEM_DEFAULT,
    val timeoutMs: Int = 3_000,
)
~~~

The default request is A + AAAA. Each selected type should be represented by its own DNS question/query. The implementation must not build a multi-question packet merely to reduce the number of calls; Android's DnsResolver documentation warns that some DNS servers may not answer queries containing more than one question.

DnsServerSelection should initially contain only SYSTEM_DEFAULT. It may later gain an explicit server value after the custom-server security and transport design is separately approved.

### 3.2 Record model

~~~kotlin
enum class DnsRecordType {
    A,
    AAAA,
    CNAME,
    MX,
    TXT,
}

data class DnsRecord(
    val type: DnsRecordType,
    val name: String,
    val value: String,
    val ttlSeconds: Long?,
    val priority: Int?,
)
~~~

Field rules:

- name is the owner name in the DNS response, normalized only for presentation.
- value is the type-specific value: an address for A/AAAA, a domain name for CNAME, an exchange name for MX, and text for TXT.
- ttlSeconds is the TTL observed in the returned resource record. It is nullable and is never synthesized from resolver cache policy.
- priority is populated for MX and remains null for other record types.
- A record's type-specific unused fields remain null rather than being filled with sentinel values.

The parser should preserve multiple records and duplicate values when they represent separate response records, while de-duplicating only exact duplicates if the UI/result contract requires it.

### 3.3 Query result

~~~kotlin
data class DnsLookupResult(
    val queryName: String,
    val requestedTypes: Set<DnsRecordType>,
    val records: List<DnsRecord>,
    val server: DnsServerInfo?,
    val method: DnsQueryMethod,
    val status: DnsLookupStatus,
    val durationMs: Long?,
    val startTime: Long,
    val endTime: Long,
    val error: DnsLookupError?,
)
~~~

The result should use a structured status instead of making success the only outcome:

~~~kotlin
enum class DnsLookupStatus {
    SUCCESS,
    PARTIAL,
    INVALID_NAME,
    NXDOMAIN,
    TIMEOUT,
    SERVER_UNAVAILABLE,
    NETWORK_UNAVAILABLE,
    RESPONSE_PARSE_ERROR,
    FAILED,
}
~~~

PARTIAL is appropriate when a multi-type request obtains a valid answer for one type but another type fails. A result with no reliable raw response must not claim NXDOMAIN, a specific server, or TTL merely because the platform returned a generic exception.

Suggested query-method values:

~~~kotlin
enum class DnsQueryMethod {
    ANDROID_DNS_RESOLVER,
    SYSTEM_RESOLVER_ADDRESSES_ONLY,
    UNAVAILABLE,
}
~~~

SYSTEM_RESOLVER_ADDRESSES_ONLY is reserved for a clearly labeled compatibility fallback. It cannot be used for a result that displays raw-response-only fields such as TTL or RCODE.

### 3.4 DNS server information

~~~kotlin
data class DnsServerInfo(
    val configuredAddresses: List<String>,
    val privateDnsActive: Boolean?,
    val privateDnsServerName: String?,
    val actualResponder: String?,
)
~~~

Semantics must be visible in the UI and history:

- configuredAddresses means DNS addresses reported for the active link by Android. It means configured/observed DNS, not “the server that answered this query”.
- privateDnsActive and privateDnsServerName describe Android's Private DNS state when exposed by LinkProperties.
- actualResponder is null for system-resolver queries unless the transport genuinely identifies the endpoint that returned the response.
- The UI must not turn configuredAddresses[0] into a claim that the first address handled the lookup.

## 4. Query Modes and User Experience

### 4.1 Ordinary mode

The default DNS Lookup page remains simple:

~~~text
DNS Lookup
检查域名解析结果

Domain
[ example.com ]

[ 查询 ]

解析成功
IPv4
...

IPv6
...

查询耗时
32 ms

域名解析正常。
~~~

The default query is A + AAAA. The user does not need to choose a record type or DNS provider before performing a normal lookup.

### 4.2 Advanced mode

The advanced section may expose:

- record type selection: A, AAAA, CNAME, MX, TXT;
- system default DNS as the only initial server selection;
- response details: owner name, value, TTL, and MX priority when present;
- query method and Private DNS context.

The domain layer supports a set of selected types. The first UI should keep the default A + AAAA flow prominent and use compact multi-select controls only after the result model and raw-response parser are tested. Selected types are separate queries aggregated into one user action, not a claim that one DNS packet contained all types.

### 4.3 Result presentation

Ordinary users see:

- success or failure;
- the queried domain;
- IPv4/IPv6 results when returned;
- observed duration;
- a short explanation.

Professional details may show:

- query types;
- query method;
- configured DNS addresses, explicitly labeled as system configuration;
- Private DNS active/name state;
- record owner name, value, TTL, and MX priority.

If a field is unavailable, omit it or show “Unavailable”. Do not show an invented DNS server address or a made-up TTL.

## 5. Android DNS Server and Private DNS Strategy

### 5.1 Discovering the active system DNS context

The active Network and its LinkProperties can be used to obtain the link DNS list. LinkProperties.getDnsServers() returns the DNS server addresses configured for that link. getPrivateDnsServerName() and isPrivateDnsActive() expose Private DNS state on supported Android versions.

This information is configuration context, not per-query provenance. Android may select, retry, cache, or route through a Private DNS path without exposing the exact responder for a particular lookup to the app.

The proposed provider boundary is:

~~~text
DnsEngine
    -> DnsTransport
        -> Android DnsResolver + active Network
    -> DnsResponseParser
    -> DnsServerInfo mapper
~~~

The Android adapter may use ConnectivityManager to obtain an active network and link properties. The domain layer receives a value object or interface result and does not depend on Context, ConnectivityManager, or LinkProperties.

### 5.2 Private DNS / DoT behavior

Private DNS is an Android-managed policy, not an ordinary DNS server choice:

- In strict mode, Android exposes a Private DNS hostname. The app must not claim that the hostname is an IP responder for an individual query.
- In opportunistic mode, Private DNS is active but the strict hostname may be null. Android's system DNS handles the policy; an app implementing its own DNS lookup must not send unencrypted DNS in violation of that policy.
- When Private DNS is active, the recommended v0.2 system path is to let Android's resolver enforce the platform policy. A custom plaintext UDP implementation must not silently bypass it.

The UI should say “Private DNS active” or “Private DNS hostname configured” where the platform reports it. It should not imply that the app audited certificate validation or measured DoT itself.

### 5.3 Custom server policy

Google, Cloudflare, Alibaba, Tencent, and arbitrary custom server inputs are technical candidates only. They should not be added as default choices in the first DNS v2 implementation.

Android's DnsResolver selects a Network; its public query methods do not take an arbitrary DNS server IP as the destination. A custom server requires a separate transport that sends DNS packets to an explicit endpoint, handles UDP/TCP behavior, and respects Android network binding and Private DNS security rules. That is a materially different feature from system lookup.

Recommended policy:

1. v0.2 first phase: system default only, with honest system-DNS and Private DNS context.
2. Do not hard-code public DNS providers.
3. Defer custom DNS until a separate design validates encryption, network binding, timeout behavior, IPv6 server addresses, fallback, and user expectations.
4. If custom DNS is later implemented, display the explicit destination and actual transport separately from the system resolver method.

## 6. Implementation Options

| Option | A/AAAA | CNAME/MX/TXT | TTL | Specified server | IPv6/network selection | Android 12+ | Maintenance/testing | Decision |
|---|---|---|---|---|---|---|---|---|
| A. InetAddress | Yes, addresses only | No | No | No | System default only | Yes | Lowest cost, but cache and failure cause are opaque; current tests are straightforward | Keep only as current compatibility behavior |
| B. Android DnsResolver typed query | Yes | No for the typed address result | No | No arbitrary server parameter | Network-aware | Yes; API 29+ | Platform callbacks and cancellation; typed result is still too narrow | Useful transport/API, not sufficient alone |
| B+. Android DnsResolver.rawQuery + pure Kotlin parser | Yes | Yes, by building per-type DNS questions and parsing responses | Yes, from response RRs | Still system-selected, not arbitrary endpoint | Network-aware | Yes; API 29+ | Requires a bounded parser and fixtures, but keeps transport platform-native | Recommended v0.2 first implementation |
| C. Own DNS UDP/TCP client | Yes | Yes | Yes | Yes | Possible with Network-bound sockets | Possible, but Private DNS and network policy are responsibilities of the app | Highest protocol/security/timeout/fallback cost; more device variance | Defer until custom-server design is approved |
| D. Audited OSS DNS library | Depends on library | Usually yes | Usually yes | Usually yes | Depends on library | Depends on library | Could reduce parser work, but license, maintenance, and dependency risk must be audited | Not selected; OSS research has no usable conclusion |

### 6.1 Recommendation

Use Android DnsResolver.rawQuery as the v0.2 system transport, with a small, independently tested pure Kotlin DNS wire parser. Use one question per selected record type. Keep the Android adapter in the data/platform layer and inject a transport interface into the parser/engine so all network behavior can be faked in unit tests.

This recommendation has three important limits:

- It can inspect the response returned through the Android system DNS path, but it cannot promise an arbitrary DNS server unless a future custom transport is added.
- It can report TTL and RCODE only when a valid raw response is received and parsed. A typed InetAddress result or a generic exception cannot be upgraded retroactively into those fields.
- It must preserve Android's Private DNS policy. The app must not use this design as a way to force plaintext queries around system settings.

### 6.2 Proposed layer structure

~~~text
DnsScreen
    -> DnsViewModel
        -> LookupDnsUseCase
            -> DnsEngine
                -> DnsTransport (interface)
                    -> AndroidDnsResolverTransport
                -> DnsResponseParser (pure Kotlin)
                -> DnsServerContextProvider (interface)
                    -> AndroidDnsServerContextProvider
~~~

DnsResponseParser should have no Android dependency. AndroidDnsResolverTransport should handle DnsResolver, Network, callbacks, cancellation, and platform errors. LookupDnsUseCase should aggregate records and save one shared history record. Compose should only observe the ViewModel state.

### 6.3 Parser safety requirements

The raw response parser should:

- validate the DNS header, transaction ID, flags, RCODE, and section lengths;
- handle compressed DNS names with loop and bounds protection;
- treat TTL as an unsigned 32-bit value represented safely in Kotlin;
- validate RDATA lengths before reading addresses or names;
- parse MX priority separately from the exchange name;
- preserve TXT character-string boundaries or document a deterministic presentation join;
- reject malformed/truncated responses without crashing;
- cap work and allocation for unexpectedly large or malicious responses;
- keep parser errors distinct from server/network errors.

DNSSEC, EDNS option interpretation, DNS-over-HTTPS, DNS-over-TLS client implementation, and service discovery are outside this first parser scope unless separately planned.

## 7. Error and Status Semantics

The result model should map observations, not guess causes:

| User-facing category | Reliable source | UI meaning |
|---|---|---|
| Domain format error | Local validation before transport | “域名格式无效，请检查输入。” |
| NXDOMAIN | Parsed DNS response RCODE=3 | “该域名不存在或 DNS 服务报告不存在。” Do not state which cause is true without more evidence. |
| DNS server no response | Explicit transport timeout with a known query attempt | “DNS 服务未及时响应。” |
| Query timeout | DnsResolver/transport cancellation or timeout | “查询超时，当前网络或 DNS 服务可能没有及时响应。” |
| Network unavailable | No suitable active network or platform network failure | “当前没有可用网络连接。” |
| Response parse error | Bytes received but invalid/unsupported | “收到的 DNS 响应无法识别。” |
| Generic resolver failure | Platform error without a reliable classification | “无法解析该域名，可能是 DNS 服务或网络暂时不可用。” |

UnknownHostException alone must not be mapped to NXDOMAIN. Similarly, a failed lookup must not be rendered as “the network is broken”. Technical exception text may be retained for local diagnostics if needed, but it should not be the primary user-facing message.

For a multi-type lookup, one type may succeed while another fails. The aggregate status should be SUCCESS when all requested queries complete successfully, PARTIAL when at least one requested type has a valid result and another has a classified failure, and a specific failure status when no requested type succeeds.

## 8. History Integration

Continue to use the shared HistoryRecorder:

~~~text
One DNS Lookup action
    -> one DnsLookupResult
    -> one shared HistoryRecord
~~~

The future DNS detail payload should include, when available:

- query name;
- requested record types;
- status;
- primary A/AAAA/CNAME/MX/TXT values;
- per-record TTL and MX priority;
- observed duration;
- query method;
- system DNS configuration context and Private DNS state;
- user-facing summary and structured error category.

The existing generic HistoryEntity/HistoryRecord.detailJson design can carry these fields. DNS v2 should extend HistoryRecordFactory or its shared serialization contract and must not introduce DnsHistoryRepository. One DNS lookup remains one history entry, including when it contains several record-type queries.

History must retain only locally useful diagnostic data. It must not add upload, analytics, account, or cloud-sync behavior.

## 9. Diagnostic Report Integration

DNS v2 should expose a result that can be adapted to the existing report input without making the report depend on Android classes:

~~~text
DnsLookupResult
    -> report adapter / compatibility projection
    -> DiagnosticAnalyzer
~~~

For the future Diagnostic Report v2, useful signals include:

- whether any requested record resolved;
- whether A and AAAA differed in availability;
- whether the response explicitly reported NXDOMAIN;
- whether the query timed out or had no response;
- observed duration as context, not as a universal failure threshold;
- whether the result came from the system resolver or an explicit custom server.

The analyzer must continue to describe possible causes and checks. It must not infer a broken router, ISP, DNS provider, or service from DNS data alone. This task does not modify the current analyzer or report flow.

## 10. Android 12–16 Constraints and Permissions

The project targets API 36 and has minSdk 31, so DnsResolver (introduced in API 29) is available across the supported range. Android API behavior, network transitions, OEM resolver behavior, captive portals, split-horizon DNS, and Private DNS settings still need device validation.

Relevant existing permissions:

- INTERNET: already present through the network module and needed for network queries.
- ACCESS_NETWORK_STATE: already present and needed when inspecting active network/link properties.
- ACCESS_WIFI_STATE: not required for DNS lookup itself; it remains unrelated existing permission context.
- No location permission should be added for DNS v2.

The Android adapter should use an individually selected Network/network-bound resolver where available rather than changing process-wide network binding. A network can disappear during a callback; cancellation and stale-network failures must become structured results rather than crashes.

The app should not claim that it can always reveal the physical DNS server when Private DNS, caching, VPN, or OEM behavior is involved. “System resolver” is the honest method label unless the transport has a verifiable endpoint.

## 11. v0.2 First-Phase Scope Decision

### Supported record types

The v0.2 DNS v2 model and parser are designed for these five types:

- A;
- AAAA;
- CNAME;
- MX;
- TXT.

The default ordinary-user lookup remains A + AAAA. CNAME, MX, and TXT are advanced record types and should be enabled only through the advanced section after parser fixtures and device tests pass. This keeps the default experience simple without making the professional model artificially narrow.

### Server support

The first phase supports the Android system DNS path only:

- system default selection;
- configured DNS addresses as clearly labeled context;
- Private DNS active/name status where the platform exposes it;
- no hard-coded Google, Cloudflare, Alibaba, or Tencent choices;
- no arbitrary custom server entry.

### TTL support

TTL is reliable enough to display only when it comes from a valid parsed DNS response resource record. It is not available through the current InetAddress result and must remain null in address-only fallback results. The UI should label it as “response TTL” rather than an authoritative guarantee about future cache lifetime.

### Deferred items

- custom DNS server selection;
- DNS provider comparison or benchmarking;
- DoH/DoT client implementation;
- DNS pollution or poisoning detection;
- DNSSEC validation;
- a third-party DNS library before the OSS research and license audit is completed.

## 12. Testing Plan

### 12.1 Pure Kotlin unit tests

Use fake transport responses and no public DNS dependency. Cover:

- valid A response with TTL;
- valid AAAA response with TTL;
- CNAME response and owner/value mapping;
- MX response with priority and exchange name;
- TXT response, including multiple character strings;
- multiple records and duplicate handling;
- RCODE=3 mapped to NXDOMAIN;
- malformed header, truncated section, invalid compression pointer, and invalid RDATA length;
- parser cancellation and bounded failure behavior;
- partial A + AAAA aggregate result;
- timeout, server-unavailable, and generic transport failures.

### 12.2 Android/platform tests

On supported Android versions, use fakes for the transport where possible and verify the adapter's callback, cancellation, Network, and error mapping behavior. Run representative checks on API 31 and API 36/emulator or device because Private DNS, VPN, and OEM resolver behavior can vary.

Manual scenarios on the Sony Xperia 1 VII / Android 16 should include:

- example.com with the default A + AAAA query;
- a known IPv4-only test domain;
- a known IPv6-capable test domain on an IPv6-capable network;
- a domain with a CNAME chain;
- a domain with MX records;
- a domain with TXT records;
- a deliberately nonexistent domain;
- an invalid input such as abc..123;
- network unavailable / airplane mode;
- Private DNS disabled, opportunistic, and strict hostname modes where available.

Public domains are suitable for manual/device verification only. Unit tests must use deterministic raw response fixtures and must not fail because the Internet or a public DNS server is unavailable.

### 12.3 History and report contract tests

Verify that:

- one multi-type DNS lookup records one shared history entry;
- only available fields are serialized;
- TTL and server provenance are not fabricated;
- the existing report compatibility projection preserves success/failure behavior;
- richer DNS v2 status can be consumed by a future report analyzer without importing Android types.

## 13. Non-Goals and Acceptance Boundary

This design does not authorize:

- changes to Kotlin code;
- changes to the current DNS UI;
- new Gradle dependencies;
- database migrations;
- version changes, tags, or releases;
- changes to Ping, TCP, or LAN Scanner;
- DoH/DoT implementation;
- automatic diagnosis or repair.

DNS v2 implementation is ready to begin only after the transport/parser boundary, result status semantics, and system-only server policy in this document are accepted.

## References

- Android DnsResolver API reference: https://developer.android.com/reference/android/net/DnsResolver
- Android DnsResolver.Callback API reference: https://developer.android.com/reference/android/net/DnsResolver.Callback
- Android Network API reference: https://developer.android.com/reference/android/net/Network
- Android LinkProperties API reference: https://developer.android.com/reference/android/net/LinkProperties
- Android ConnectivityManager API reference: https://developer.android.com/reference/android/net/ConnectivityManager
- Android InetAddress API reference: https://developer.android.com/reference/java/net/InetAddress
