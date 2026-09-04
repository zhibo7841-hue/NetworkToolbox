# Diagnostic Rule Engine Acceptance

Task 051-A records the observable behavior of `DefaultDiagnosticAnalyzerV4`
against deterministic fixtures. The fixtures are pure Kotlin test data and do
not access the network, Android APIs, Room, or History.

The implementation field is named `evidenceCheckCodes`; this document calls it
“Evidence Check IDs” to match the acceptance checklist. `—` means an empty list
or a null primary finding.

## Acceptance invariants

- `CONNECT_SUCCESS` and `CONNECTION_REFUSED` are positive TCP path evidence.
  Timeout or unknown alone never becomes “Internet Down”.
- DNS `NO_RECORDS` means the query completed normally without records. A
  successful A query plus an AAAA `NO_RECORDS` result is not a DNS failure.
- VPN, Fake-IP, and a non-responsive gateway with an independently positive
  public path remain NOTICE-level context and do not override normal transport.
- Every emitted Finding references at least one observation ID or check code;
  every reference listed below exists in its fixture.

## A — Normal Wi-Fi

**Input Evidence Summary:** Active Wi-Fi, usable IPv4, gateway probe passed, both
public TCP probes connected, and baseline DNS succeeded.

**Findings**

1. `NETWORK_APPEARS_NORMAL` — Severity `HEALTHY`; EvidenceLevel `CONFIRMED`;
   Confidence `HIGH`.
   - Title: `基础网络连接正常`
   - Description: `在本次检测范围内，基础网络连接表现正常。`
   - Observation IDs: `fixture-0`, `fixture-1`, `fixture-3`, `fixture-4`,
     `fixture-5`, `fixture-6`
   - Evidence Check IDs: `NETWORK_STATE`, `IP_CONFIGURATION`, `GATEWAY`,
     `DNS_RESOLUTION`, `PUBLIC_CONNECTIVITY`
   - Recommendation Codes: `—`

**Overall Diagnosis:** Status `NORMAL`; Title `基础网络连接正常`; Explanation
`在本次检测范围内，基础网络连接表现正常；这不代表所有应用或网站都一定正常。`;
Confidence `HIGH`; Primary Finding Code `NETWORK_APPEARS_NORMAL`.

**Recommendations**

1. `COMPARE_ANOTHER_NETWORK` (`SECONDARY`) — Title `对比其他网络`; Action
   `尝试使用另一 Wi-Fi 或移动网络进行对比。`; Reason `不同网络的结果有助于区分本地环境与目标路径问题。`
2. `RUN_TARGET_CHECK` (`OPTIONAL`) — Title `运行目标检测`; Action
   `如果仍然无法访问某个服务，可以运行目标检测。`; Reason `基础检测正常不代表所有应用或网站都一定正常。`

符合设计预期：**PASS**。文案限定在本次检测范围内，没有扩大为所有网站正常。

## B — Gateway timeout + Internet success

**Input Evidence Summary:** Active Wi-Fi, usable IPv4, gateway probe timed out,
both public TCP probes connected, and baseline DNS succeeded.

**Findings**

1. `GATEWAY_PROBE_NO_RESPONSE` — Severity `NOTICE`; EvidenceLevel
   `CONTRADICTED`; Confidence `HIGH`.
   - Title: `默认网关未响应当前探测`
   - Description: `默认网关没有响应当前探测，但公网连接正常。部分设备可能不响应此类探测，因此不能据此判断网关故障。`
   - Observation IDs: `fixture-2`, `fixture-3`, `fixture-4`
   - Evidence Check IDs: `GATEWAY`, `PUBLIC_CONNECTIVITY`
   - Recommendation Codes: `RETRY_DIAGNOSTIC`
2. `NETWORK_APPEARS_NORMAL` — Severity `HEALTHY`; EvidenceLevel `CONFIRMED`;
   Confidence `HIGH`.
   - Title: `基础网络连接正常`
   - Description: `在本次检测范围内，基础网络连接表现正常。`
   - Observation IDs: `fixture-0`, `fixture-1`, `fixture-3`, `fixture-4`,
     `fixture-5`, `fixture-6`
   - Evidence Check IDs: `NETWORK_STATE`, `IP_CONFIGURATION`, `GATEWAY`,
     `DNS_RESOLUTION`, `PUBLIC_CONNECTIVITY`
   - Recommendation Codes: `—`

**Overall Diagnosis:** Status `NORMAL`; Title `基础网络连接正常`; Explanation
`在本次检测范围内，基础网络连接表现正常；这不代表所有应用或网站都一定正常。`;
Confidence `HIGH`; Primary Finding Code `NETWORK_APPEARS_NORMAL`.

**Recommendations**

1. `COMPARE_ANOTHER_NETWORK` (`SECONDARY`) — Title `对比其他网络`; Action
   `尝试使用另一 Wi-Fi 或移动网络进行对比。`; Reason `不同网络的结果有助于区分本地环境与目标路径问题。`
2. `RUN_TARGET_CHECK` (`OPTIONAL`) — Title `运行目标检测`; Action
   `如果仍然无法访问某个服务，可以运行目标检测。`; Reason `基础检测正常不代表所有应用或网站都一定正常。`

符合设计预期：**PASS**。没有 Gateway Failure、Router Failure 或 Local Network Failure。

## C — Gateway + public uncertain

**Input Evidence Summary:** Gateway timed out, both public TCP probes timed out,
`VALIDATED=false`, and DNS provided no strong counter-evidence.

**Findings**

1. `PUBLIC_CONNECTIVITY_UNCONFIRMED` — Severity `NOTICE`; EvidenceLevel
   `INCONCLUSIVE`; Confidence `MEDIUM`.
   - Title: `公网连接尚未确认`
   - Description: `当前未能确认公网连接可用；超时或适配器限制不等同于互联网已断开。`
   - Observation IDs: `fixture-3`, `fixture-4`
   - Evidence Check IDs: `PUBLIC_CONNECTIVITY`
   - Recommendation Codes: `RETRY_DIAGNOSTIC`, `COMPARE_ANOTHER_NETWORK`
2. `LOCAL_OR_UPSTREAM_PATH_UNCONFIRMED` — Severity `WARNING`; EvidenceLevel
   `SUPPORTED`; Confidence `MEDIUM`.
   - Title: `本地或上游网络路径未确认`
   - Description: `网关与公网探测均未提供成功证据，问题可能位于本地链路、接入点、VLAN、网关、WAN 或上游网络。`
   - Observation IDs: `fixture-2`, `fixture-3`, `fixture-4`
   - Evidence Check IDs: `GATEWAY`, `PUBLIC_CONNECTIVITY`
   - Recommendation Codes: `CHECK_ROUTER_WAN`, `COMPARE_ANOTHER_NETWORK`

**Overall Diagnosis:** Status `ATTENTION`; Title `发现需要关注的网络现象`;
Explanation equals the Local/Upstream description above; Confidence `MEDIUM`;
Primary Finding Code `LOCAL_OR_UPSTREAM_PATH_UNCONFIRMED`.

**Recommendations**

1. `CHECK_ROUTER_WAN` (`PRIMARY`) — Title `检查本地与上游连接`; Action
   `检查接入点、路由器 WAN 和上游连接状态。`; Reason `网关与公网探测均未提供成功证据。`
2. `COMPARE_ANOTHER_NETWORK` (`SECONDARY`) — Title `对比其他网络`; Action
   `尝试使用另一 Wi-Fi 或移动网络进行对比。`; Reason `不同网络的结果有助于区分本地环境与目标路径问题。`

符合设计预期：**PASS**。Local/Upstream 优先，且没有断言路由器、ISP 或宽带故障。

## D — Public timeout + refused

**Input Evidence Summary:** Public A timed out and Public B returned
`CONNECTION_REFUSED`; gateway and baseline DNS succeeded.

**Findings**

1. `NETWORK_APPEARS_NORMAL` — Severity `HEALTHY`; EvidenceLevel `CONFIRMED`;
   Confidence `HIGH`.
   - Title: `基础网络连接正常`
   - Description: `在本次检测范围内，基础网络连接表现正常。`
   - Observation IDs: `fixture-0`, `fixture-1`, `fixture-3`, `fixture-4`,
     `fixture-5`, `fixture-6`
   - Evidence Check IDs: `NETWORK_STATE`, `IP_CONFIGURATION`, `GATEWAY`,
     `DNS_RESOLUTION`, `PUBLIC_CONNECTIVITY`
   - Recommendation Codes: `—`

**Overall Diagnosis:** Status `NORMAL`; Title `基础网络连接正常`; Explanation
`在本次检测范围内，基础网络连接表现正常；这不代表所有应用或网站都一定正常。`;
Confidence `HIGH`; Primary Finding Code `NETWORK_APPEARS_NORMAL`.

**Recommendations:**

1. `COMPARE_ANOTHER_NETWORK` (`SECONDARY`) — `对比其他网络`; Action
   `尝试使用另一 Wi-Fi 或移动网络进行对比。`; Reason `不同网络的结果有助于区分本地环境与目标路径问题。`
2. `RUN_TARGET_CHECK` (`OPTIONAL`) — `运行目标检测`; Action
   `如果仍然无法访问某个服务，可以运行目标检测。`; Reason `基础检测正常不代表所有应用或网站都一定正常。`

符合设计预期：**PASS**。`CONNECTION_REFUSED` 提供明确响应证据，未被判为互联网不可用。

## E — VALIDATED conflict

**Input Evidence Summary:** `VALIDATED=true`; both public TCP probes timed out;
no other confirmed failure.

**Findings**

1. `PUBLIC_CONNECTIVITY_UNCONFIRMED` — Severity `NOTICE`; EvidenceLevel
   `INCONCLUSIVE`; Confidence `LOW`.
   - Title: `公网连通性证据存在冲突`
   - Description: `系统联网验证已通过，但本次公网 TCP 探测没有成功证据，当前无法确认公网连接状态。`
   - Observation IDs: `fixture-3`, `fixture-4`
   - Evidence Check IDs: `PUBLIC_CONNECTIVITY`
   - Recommendation Codes: `RETRY_DIAGNOSTIC`, `COMPARE_ANOTHER_NETWORK`

**Overall Diagnosis:** Status `UNKNOWN`; Title `当前无法确认整体网络状态`; Explanation
`证据不足或存在冲突，当前结果不足以形成可靠的整体网络结论。`; Confidence
`LOW`; Primary Finding Code `—`.

**Recommendations:**

1. `RETRY_DIAGNOSTIC` (`PRIMARY`) — `重新运行诊断`; Action
   `在网络稳定后重新运行诊断，并尝试对比其他网络。`; Reason `当前公网探测证据不足或与系统联网验证冲突。`
2. `COMPARE_ANOTHER_NETWORK` (`SECONDARY`) — `对比其他网络`; Action
   `尝试使用另一 Wi-Fi 或移动网络进行对比。`; Reason `不同网络的结果有助于区分本地环境与目标路径问题。`

符合设计预期：**PASS**。未生成 Internet Down 或 ERROR Finding。

## F — DNS problem

**Input Evidence Summary:** Public TCP probes connected and baseline DNS timed
out.

**Findings**

1. `DNS_RESOLUTION_FAILURE` — Severity `WARNING`; EvidenceLevel `SUPPORTED`;
   Confidence `HIGH`.
   - Title: `DNS 查询未正常完成`
   - Description: `公网连接正常，但当前 DNS 查询未正常完成。问题可能与 DNS 服务、Private DNS、VPN 或网络配置有关。`
   - Observation IDs: `fixture-5`, `fixture-6`
   - Evidence Check IDs: `DNS_RESOLUTION`
   - Recommendation Codes: `RETRY_DIAGNOSTIC`,
     `CHECK_PRIVATE_DNS_VPN_PROXY`, `COMPARE_ANOTHER_NETWORK`

**Overall Diagnosis:** Status `ATTENTION`; Title `发现需要关注的网络现象`; Explanation
equals the DNS description above; Confidence `HIGH`; Primary Finding Code
`DNS_RESOLUTION_FAILURE`.

**Recommendations:**

1. `RETRY_DIAGNOSTIC` (`PRIMARY`) — `重新进行 DNS 查询`; Action
   `再次执行诊断以确认 DNS 是否恢复。`; Reason `公网路径已有成功证据，但 DNS 查询未正常完成。`
2. `CHECK_PRIVATE_DNS_VPN_PROXY` (`SECONDARY`) — `检查 DNS 环境`; Action
   `检查 Private DNS、VPN 或代理设置。`; Reason `这些上下文可能影响名称解析与访问路径。`

符合设计预期：**PASS**。没有指定 DNS Provider，也没有断言 DNS 服务器或运营商故障。

## G — DNS + Internet both fail

**Input Evidence Summary:** Both public TCP probes and baseline DNS timed out.

**Findings**

1. `PUBLIC_CONNECTIVITY_UNCONFIRMED` — Severity `NOTICE`; EvidenceLevel
   `INCONCLUSIVE`; Confidence `MEDIUM`.
   - Title: `公网连接尚未确认`
   - Description: `当前未能确认公网连接可用；超时或适配器限制不等同于互联网已断开。`
   - Observation IDs: `fixture-3`, `fixture-4`
   - Evidence Check IDs: `PUBLIC_CONNECTIVITY`
   - Recommendation Codes: `RETRY_DIAGNOSTIC`, `COMPARE_ANOTHER_NETWORK`

**Overall Diagnosis:** Status `UNKNOWN`; Title `当前无法确认整体网络状态`; Explanation
`证据不足或存在冲突，当前结果不足以形成可靠的整体网络结论。`; Confidence
`LOW`; Primary Finding Code `—`.

**Recommendations:**

1. `RETRY_DIAGNOSTIC` (`PRIMARY`) — `重新运行诊断`; Action
   `在网络稳定后重新运行诊断，并尝试对比其他网络。`; Reason `当前公网探测证据不足或与系统联网验证冲突。`
2. `COMPARE_ANOTHER_NETWORK` (`SECONDARY`) — `对比其他网络`; Action
   `尝试使用另一 Wi-Fi 或移动网络进行对比。`; Reason `不同网络的结果有助于区分本地环境与目标路径问题。`

符合设计预期：**PASS**。DNS 未升级为高置信度 DNS failure，Primary 保持在公网边界。

## H — VPN + Fake-IP normal

**Input Evidence Summary:** VPN active, baseline DNS succeeded with a
`198.18.0.1` record, public TCP probes connected, and IPv4/gateway were usable.

**Findings**

1. `FAKE_IP_CONTEXT` — Severity `NOTICE`; EvidenceLevel `CONFIRMED`;
   Confidence `HIGH`.
   - Title: `检测到特殊用途地址`
   - Description: `检测到 198.18.0.0/15 特殊用途地址，可能存在 Fake-IP DNS 环境；这不等同于 DNS 错误。`
   - Observation IDs: `fixture-6`
   - Evidence Check IDs: `DNS_RESOLUTION`
   - Recommendation Codes: `CHECK_PRIVATE_DNS_VPN_PROXY`
2. `VPN_ACTIVE` — Severity `NOTICE`; EvidenceLevel `CONFIRMED`; Confidence
   `HIGH`.
   - Title: `检测到 VPN 网络`
   - Description: `当前通过 VPN 的联网路径在本次检测中表现正常或已被单独记录；以下结果可能反映 VPN 隧道后的网络环境。`
   - Observation IDs: `fixture-8`
   - Evidence Check IDs: `NETWORK_STATE`
   - Recommendation Codes: `—`
3. `NETWORK_APPEARS_NORMAL` — Severity `HEALTHY`; EvidenceLevel `CONFIRMED`;
   Confidence `HIGH`.
   - Title: `基础网络连接正常`
   - Description: `在本次检测范围内，基础网络连接表现正常。`
   - Observation IDs: `fixture-0`, `fixture-1`, `fixture-3`, `fixture-4`,
     `fixture-5`, `fixture-6`
   - Evidence Check IDs: `NETWORK_STATE`, `IP_CONFIGURATION`, `GATEWAY`,
     `DNS_RESOLUTION`, `PUBLIC_CONNECTIVITY`
   - Recommendation Codes: `—`

**Overall Diagnosis:** Status `NORMAL`; Title `基础网络连接正常`; Explanation
`在本次检测范围内，基础网络连接表现正常；这不代表所有应用或网站都一定正常。`;
Confidence `HIGH`; Primary Finding Code `NETWORK_APPEARS_NORMAL`.

**Recommendations:**

1. `CHECK_PRIVATE_DNS_VPN_PROXY` (`SECONDARY`) — `检查 DNS 环境`; Action
   `检查 Private DNS、VPN 或代理设置。`; Reason `这些上下文可能影响名称解析与访问路径。`
2. `COMPARE_ANOTHER_NETWORK` (`SECONDARY`) — `对比其他网络`; Action
   `尝试使用另一 Wi-Fi 或移动网络进行对比。`; Reason `不同网络的结果有助于区分本地环境与目标路径问题。`
3. `RUN_TARGET_CHECK` (`OPTIONAL`) — `运行目标检测`; Action
   `如果仍然无法访问某个服务，可以运行目标检测。`; Reason `基础检测正常不代表所有应用或网站都一定正常。`

符合设计预期：**PASS**。VPN/Fake-IP 都是 NOTICE，仅产生一条共享代理/DNS 建议；没有命名具体代理软件或断言 Fake-IP 是真实目标地址。

## I — Target REFUSED

**Input Evidence Summary:** Base transport normal, target domain DNS succeeded,
and target TCP returned `CONNECTION_REFUSED`.

**Findings**

1. `TARGET_TCP_REFUSED` — Severity `WARNING`; EvidenceLevel `SUPPORTED`;
   Confidence `HIGH`.
   - Title: `目标端口未接受连接`
   - Description: `目标端口未接受连接，但目标地址路径存在明确响应；这不等同于路由或互联网故障。`
   - Observation IDs: `fixture-8`
   - Evidence Check IDs: `TARGET_CONNECTIVITY`
   - Recommendation Codes: `RUN_TARGET_CHECK`

**Overall Diagnosis:** Status `ATTENTION`; Title `发现需要关注的网络现象`; Explanation
equals the Target REFUSED description above; Confidence `HIGH`; Primary Finding
Code `TARGET_TCP_REFUSED`.

**Recommendations:**

1. `RUN_TARGET_CHECK` (`PRIMARY`) — `核对目标`; Action
   `确认目标名称、地址族、端口和服务配置。`; Reason `当前现象更接近目标特定问题，不能直接归因于整体网络。`

符合设计预期：**PASS**。没有 Route broken、Website down 或 Internet down 结论。

## J — Target TIMEOUT

**Input Evidence Summary:** Base transport normal, target domain DNS succeeded,
and target TCP timed out.

**Findings**

1. `TARGET_TCP_TIMEOUT` — Severity `WARNING`; EvidenceLevel `INCONCLUSIVE`;
   Confidence `MEDIUM`.
   - Title: `目标连接未及时响应`
   - Description: `公网路径已有成功证据，但目标服务或访问路径未及时响应；不能据此判断网站或服务已停止。`
   - Observation IDs: `fixture-8`
   - Evidence Check IDs: `TARGET_CONNECTIVITY`
   - Recommendation Codes: `RUN_TARGET_CHECK`

**Overall Diagnosis:** Status `ATTENTION`; Title `发现需要关注的网络现象`; Explanation
equals the Target TIMEOUT description above; Confidence `MEDIUM`; Primary Finding
Code `TARGET_TCP_TIMEOUT`.

**Recommendations:**

1. `RUN_TARGET_CHECK` (`PRIMARY`) — `核对目标`; Action
   `确认目标名称、地址族、端口和服务配置。`; Reason `当前现象更接近目标特定问题，不能直接归因于整体网络。`

符合设计预期：**PASS**。EvidenceLevel 为 INCONCLUSIVE、Confidence 为 MEDIUM，未断言目标服务器或路由故障。

## K — No Active Network

**Input Evidence Summary:** Explicit `ACTIVE_NETWORK_AVAILABLE=false`; downstream
network probes are not treated as meaningful.

**Findings**

1. `NO_ACTIVE_NETWORK` — Severity `ERROR`; EvidenceLevel `CONFIRMED`; Confidence
   `HIGH`.
   - Title: `没有可用的活动网络`
   - Description: `设备当前没有可用的活动网络连接。`
   - Observation IDs: `fixture-0`
   - Evidence Check IDs: `NETWORK_STATE`
   - Recommendation Codes: `CHECK_WIFI_OR_MOBILE_NETWORK`, `RETRY_DIAGNOSTIC`

**Overall Diagnosis:** Status `ATTENTION`; Title `设备当前没有可用网络`; Explanation
`设备当前没有可用的活动网络连接，请先检查 Wi-Fi 或移动数据。`; Confidence
`HIGH`; Primary Finding Code `NO_ACTIVE_NETWORK`.

**Recommendations:**

1. `CHECK_WIFI_OR_MOBILE_NETWORK` (`PRIMARY`) — `检查网络连接`; Action
   `检查 Wi-Fi 或移动数据，并确认未开启飞行模式。`; Reason `当前没有可用的活动网络。`
2. `RETRY_DIAGNOSTIC` (`SECONDARY`) — `重新运行诊断`; Action
   `连接网络后重新运行诊断。`; Reason `重新检测可以确认网络状态是否已经恢复。`

符合设计预期：**PASS**。本场景没有生成 DNS 或 Target 主建议；推荐顺序在 Task 051-A 中做了最小修复。

## L — Network Changed

**Input Evidence Summary:** `runStatus=NETWORK_CHANGED`; other fixture observations
are ignored by the run-status gate so they cannot form one conclusion.

**Findings:** none.

**Overall Diagnosis:** Status `UNKNOWN`; Title `检测结果无法合并判断`; Explanation
`检测过程中网络发生变化，当前结果可能来自不同网络环境。`; Confidence `HIGH`;
Primary Finding Code `—`.

**Recommendations**

1. `RETRY_DIAGNOSTIC` (`PRIMARY`) — `重新运行诊断`; Action
   `请在网络稳定后重新运行诊断。`; Reason `本次检测跨越了不同网络环境，不能合并为一个强结论。`

符合设计预期：**PASS**。没有保存或表达为 Network failure。

## Cross-scenario acceptance

### Evidence references

All findings in A–K reference at least one real observation or check. The
fixture test validates each emitted reference against the fixture's observation
IDs and check codes. L intentionally emits no Finding.

### Recommendation limits and deduplication

- Maximum: 3 recommendations per result.
- Deduplication key: `DiagnosticRecommendationCode`.
- VPN + Fake-IP emits one shared `CHECK_PRIVATE_DNS_VPN_PROXY` recommendation.
- Primary recommendation follows the primary material Finding where one exists.
- Context NOTICE findings do not override material warnings or normal transport.
- No recommendation performs automatic repair, reset, or configuration mutation.

### Machine-code acceptance

Logic and comparisons use `DiagnosticFindingCode`, `DiagnosticCheckCode`, and
`DiagnosticRecommendationCode`. Chinese titles and descriptions are output
content only and are not rule keys.

### Test and build record

- Task 051 baseline rule tests: 35.
- Task 051-A regression assertions: 1 recommendation-order regression was added
  to the existing no-network test.
- `./gradlew :feature:report:test --no-daemon`: PASS.
- `./gradlew test --no-daemon`: PASS.
- `./gradlew lint --no-daemon`: PASS.
- `./gradlew assembleDebug --no-daemon`: PASS.
- No real public network was accessed by these fixtures.

### Scope result

- `PRODUCT_PLAN.md`: unchanged.
- `DECISIONS.md`: unchanged.
- No UI, Android permission, dependency, Ping/DNS/TCP behavior, Room, History,
  version, tag, or release changes.
- Task 051-A validates the Rule Engine only; Task 052 UI work has not started.
