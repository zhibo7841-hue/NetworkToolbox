# Homepage Network Status v2 Design

## Status and scope

本文档是 NetworkToolbox 首页 Network Status v2 的设计与数据审计基线。
本轮只完成审计和设计，不修改 Compose UI、网络读取逻辑、Diagnostic、权限、版本号或产品范围。

设计目标是让首页做到：

- 普通用户快速知道当前网络是否已连接；
- IPv4、网关、DNS、IPv6 的含义清楚；
- 专业信息可以展开查看；
- 不把系统观察值包装成确定性诊断结论；
- Home 与 Diagnostic 继续使用同一个网络上下文来源。

## 1. 当前实现审计

### 1.1 数据链路

```text
Android ConnectivityManager / LinkProperties / NetworkCapabilities
                         ↓
             AndroidNetworkRepository
                         ↓
                  NetworkContext
                   ↙            ↘
        DashboardViewModel       DiagnosticPipeline
                   ↓                    ↓
                HomeScreen       DiagnosticAnalyzerV2
```

Hilt 将 `AndroidNetworkRepository` 以单例注入为 `NetworkRepository`。Home 的
`DashboardViewModel` 通过 `ObserveNetworkContextUseCase` 订阅该仓库；Diagnostic
通过同一个 `NetworkRepository` 读取检测开始时和检测过程中的网络上下文。因此，首页和
Diagnostic 没有两套独立的 Gateway 提取逻辑。

DNS v2 查询本身还会通过 `AndroidDnsServerInfoProvider` 读取当前
`LinkProperties` 的 DNS 配置和 Private DNS 信息，但这部分目前属于 DNS 查询链路，
并未合并进 Home 的 `NetworkContext`。

### 1.2 当前 `NetworkContext`

当前模型字段为：

| 字段 | 当前含义 | 当前限制 |
| --- | --- | --- |
| `connectionType` | Wi-Fi、蜂窝、以太网、蓝牙、VPN 或未知 | 来自当前 `NetworkCapabilities`；VPN 与底层 transport 的组合需要结合 `vpnActive` 解读 |
| `ipv4Address` | 当前链路找到的第一个 IPv4 地址 | 没有 prefix length；没有保留全部地址 |
| `ipv6Address` | 当前链路找到的第一个 IPv6 地址 | 没有地址类型；没有保留全部 IPv6 地址 |
| `gateway` | 默认路由的网关 | 仍是单字段，没有分别保存 IPv4/IPv6 Gateway |
| `dnsServers` | 当前链路的 DNS 地址列表 | 数据可用于“网络配置 DNS”，不能证明本次响应来自其中某一台 |
| `vpnActive` | `TRANSPORT_VPN` 是否存在 | 可为 `null`，且不是底层网络类型的替代品 |
| `wifiName` | `WifiInfo.ssid` 能够提供时的 SSID | 无位置相关权限时可能为 `UNKNOWN_SSID`，当前不强制申请权限 |
| `wifiSignalLevel` | RSSI 经 `WifiManager.calculateSignalLevel` 转换的系统分级 | 只在 Wi-Fi 信息和 RSSI 可用时存在 |
| `activeNetworkAvailable` | 是否取得活动默认网络 | `null` 表示未知，`false` 表示没有活动网络 |
| `validated` | `NET_CAPABILITY_VALIDATED` | 表示系统最近验证过公网能力，不等同于所有目标都可访问 |

### 1.3 当前首页卡片

`NetworkStatusCard` 当前行为：

- 顶部显示网络类型、连接状态和一个未带标签的 IPv4 值；
- 展开后显示单个 `gateway`、首个 DNS、单个 IPv6 和 Wi-Fi 信号；
- IPv4、IPv6、Gateway、DNS 在模型为空时统一显示“未知”；
- DNS 详情只取 `dnsServers.firstOrNull()`，长 IPv6 地址可能占据摘要区域；
- 当前没有显示 prefix、接口名、Private DNS、VALIDATED、metered 等信息。

### 1.4 B4 Gateway 现状

当前 Gateway 选择已经由 `DefaultGatewaySelector` 集中处理：只查看默认路由，
并在双栈场景优先选择 IPv4，不依赖 `LinkProperties.routes` 的顺序。

但 `NetworkContext` 仍只有一个 `gateway` 字段，且 Android 仓库的地址格式化会去掉
IPv6 地址中的 `%interface` scope。因而当前架构不能可靠地用无 scope 的
IPv6 Link-Local Gateway 执行探测。Diagnostic 已对这类地址安全降级为未确认，不将其
判定为故障；这项行为必须继续保持。

## 2. Android 12–16 数据可用性审计

| 数据 | Android 能力 | 当前实现 | 首页 v2 设计结论 |
| --- | --- | --- | --- |
| 网络类型 | `NetworkCapabilities` transport | 已读取 Wi-Fi、蜂窝、以太网、蓝牙、VPN | 可显示，但 VPN 应作为独立状态标记，不仅依赖 `connectionType` |
| 活动网络 | `ConnectivityManager.activeNetwork`、默认网络回调 | 已读取，并监听变化 | 可显示“已连接/未连接/未知”；不把已连接等同于公网正常 |
| IPv4 地址 | `LinkProperties.linkAddresses` | 只保留第一个 IPv4 | 摘要显示并明确标注“IPv4 地址”；后续详情可补 prefix |
| IPv4 prefix/subnet | `LinkAddress.prefixLength` | 未读取 | 只放详情；没有可靠值时不显示 |
| IPv6 地址 | `LinkProperties.linkAddresses` | 只保留第一个 IPv6 | 先显示分类，完整地址放详情；不能仅凭存在地址称为公网 IPv6 |
| IPv6 地址类型 | `Inet6Address`/地址范围判断 | 未分类 | 在数据层判断 Link-Local、ULA 或其他地址，UI 不自行解析 |
| IPv4 Gateway | `RouteInfo` 默认路由 | B4 已实现 IPv4 优先 | Wi-Fi/Ethernet 摘要优先使用；不依赖路由列表顺序 |
| IPv6 Gateway | `RouteInfo` 默认路由 | 可能读到，但单字段且 scope 已丢失 | 详情可展示观察值；无可靠 scope 时不得执行网关探测 |
| DNS Servers | `LinkProperties.dnsServers` | 已读取全部列表，首页只显示首个 | 摘要显示服务器数量，详情逐行显示全部配置 DNS |
| Private DNS | `isPrivateDnsActive()`、`getPrivateDnsServerName()` | DNS v2 provider 已读取，NetworkContext 未包含 | 详情可显示“Private DNS”；不能把配置名称为本次实际响应服务器 |
| SSID | `WifiInfo.getSSID()` | 从 `transportInfo` 读取 | 仅在系统返回可信值时显示；不能为此强制申请位置权限 |
| Wi-Fi RSSI/Signal | `WifiInfo.getRssi()`、`WifiManager.calculateSignalLevel()` | 已读取系统分级 | Wi-Fi 摘要可显示分级；缺失时显示“未知” |
| Wi-Fi Link Speed | `WifiInfo` link speed | 未读取 | 本轮不加入摘要；后续详情需单独验证权限、厂商和 MLO 行为 |
| Frequency/Band | `WifiInfo` frequency 等 | 未读取 | 本轮不加入，避免把频段信息误当作质量判断 |
| VPN | `TRANSPORT_VPN` | 已读取 `vpnActive` | 显示“VPN 已启用”；不推断具体 VPN 应用或代理软件 |
| VALIDATED | `NET_CAPABILITY_VALIDATED` | 已读取 | 详情显示“系统联网验证：已通过/未通过/未知”；不能替代实际探测结果 |
| Metered | `NET_CAPABILITY_NOT_METERED` 的反向含义 | 未读取 | 本轮不显示；未来若加入必须明确“计费/流量属性”，不解释为网络质量 |
| Interface name | `LinkProperties.getInterfaceName()` | 未读取 | 只建议放专业详情，不放摘要 |

Android 官方说明中，`LinkProperties` 能提供链路地址、路由、DNS、接口名和
Private DNS 状态；`NetworkCapabilities` 的 `VALIDATED` 表示系统探测到过公网能力，
但网络能力会动态变化；`WifiInfo` 的位置敏感字段在权限不足时可能被遮蔽。参见文末
官方参考资料。

## 3. 首页摘要层设计

### 3.1 推荐结构

```text
Wi-Fi                                      ● 已连接

IPv4 地址
10.0.0.254

网关                         DNS
10.0.0.1                     2 个服务器

IPv6                        信号
仅链路本地                  4 / 4

查看详情 >
```

摘要只保留稳定、可快速理解的信息：

- IPv4 必须带“IPv4 地址”标签；没有 IPv4 时显示“未配置”，不留空；
- Gateway 只显示 IPv4 优先的摘要值；没有适用 Gateway 时显示“未确认”或“不适用”；
- DNS 显示数量，例如“2 个服务器”，避免长 IPv6 地址造成首页跳动；
- IPv6 显示语义分类，不在摘要中把 Link-Local 地址当作公网 IPv6；
- Signal 仅对 Wi-Fi 显示；蜂窝和以太网不显示伪造的信号值；
- `validated` 不作为“已连接”文本的替代，也不单独把卡片标红。

### 3.2 状态文案

| 数据状态 | 用户文案 | 说明 |
| --- | --- | --- |
| 有可靠值 | 值本身 | 例如 `10.0.0.254` |
| 当前没有该配置 | 未配置 | 例如没有 IPv4 地址 |
| 当前网络类型不适用 | 不适用 | 例如蜂窝网络的传统本地网关、非 Wi-Fi 信号 |
| API 未返回或权限受限 | 未知 | 不把读取不到解释成异常 |
| 仅能保守观察 | 未确认 | 例如无 scope 的 IPv6 Link-Local Gateway |

## 4. IPv6 分类方案

分类应在 Repository/mapper 或专门的纯 Kotlin formatter 中完成，Compose 不解析地址。
分类输入应使用当前链路的全部 IPv6 地址，而不是只看一个地址。

| 分类 | 判定 | 首页文案 | 解释 |
| --- | --- | --- | --- |
| `NONE` | 没有 IPv6 地址 | 未配置 | 当前上下文没有观察到 IPv6 地址 |
| `LINK_LOCAL_ONLY` | 所有地址均为 `fe80::/10` | 仅链路本地 | 只能说明本地链路地址存在，不能说明公网 IPv6 可用 |
| `CONFIGURED` | 至少一个非 Link-Local 地址 | 已配置 | 只说明地址已配置，不保证路由或公网连通 |
| `UNKNOWN` | 地址存在但分类失败 | 未知 | 保留原始地址到详情 |

ULA 等 `fc00::/7` 地址不应显示为“公网可用”；如需进一步区分，可在详情标记为
“IPv6 本地地址”。本轮不新增 IPv6 连通性测试，首页也不根据一个地址做故障判断。

当前 `NetworkContext` 只保存一个 IPv6 字符串，因此下一实现阶段若要可靠分类，建议
以向后兼容方式增加全部地址或地址分类字段。旧字段不应被突然删除。

## 5. Gateway 策略

### 5.1 摘要和详情

- Wi-Fi/Ethernet：摘要使用 IPv4 Default Gateway；双栈时 IPv6 默认路由不能抢占它；
- 详情分别展示“IPv4 网关”和“IPv6 网关”（如果真实读取到）；
- Cellular：摘要显示“本地网关：不适用”，不因为系统返回某个 10.x 或 IPv6 值就执行网关 Ping；
- IPv6-only Wi-Fi/Ethernet：若不能携带 `fe80::...%interface`，显示“未确认”，不显示“网关异常”；
- 网关地址是链路配置观察值，不等同于已经通过可达性验证。

推荐后续向模型增加独立的 `ipv4Gateway`、`ipv6Gateway` 和 IPv6 scope/interface
信息，并保留旧 `gateway` 兼容字段或迁移适配层。Gateway 选择只能在共享数据层完成，
不能由 Home 和 Diagnostic 各自实现。

### 5.2 与 Diagnostic 的关系

首页只是展示 NetworkContext；Diagnostic 才执行网关探测和综合判断。因此：

- 首页看到 IPv4 Gateway 不代表探测成功；
- Diagnostic 网关未确认不应反向修改首页地址；
- Gateway 探测失败但公网证据正常时，Diagnostic 继续采用保守的“未确定”语义；
- 本设计不修改 B3 的公网目标和 DNS+域名访问协调规则。

## 6. DNS 摘要和详情策略

### 6.1 首页摘要

首页显示：

```text
DNS
2 个服务器
```

若没有配置 DNS，显示“未配置”或“未知”（分别对应空列表和读取失败）。不根据 DNS
地址是否为私有地址、IPv6 地址或特殊地址判断网络故障。

### 6.2 详情

详情逐行展示真实读取到的配置 DNS：

```text
网络配置 DNS
10.0.0.1
2408:xxxx::53

Private DNS
已启用
Private DNS 名称
dns.example
```

“网络配置 DNS”不是“本次响应服务器”。只有真实 DNS Response 或系统 API 明确提供
证据时，才可使用“响应服务器”这类表述。Private DNS 的启用状态和名称只说明系统
配置/使用状态，不代表应用能够从普通配置字段推导每一次查询的实际路径。

Home v2 若要展示 Private DNS，建议由统一的网络上下文快照提供可选字段，或由同一
Repository 聚合已有 `DnsServerInfo`；不要让 Home 新建第三套 DNS provider。

## 7. 权限和隐私边界

当前相关权限：

- `ACCESS_NETWORK_STATE`：读取活动网络、NetworkCapabilities 和 LinkProperties；
- `ACCESS_WIFI_STATE`：访问 Wi-Fi 状态相关 API；
- `INTERNET`：执行已有网络检测；
- 当前没有 `ACCESS_FINE_LOCATION`、`ACCESS_COARSE_LOCATION` 或
  `NEARBY_WIFI_DEVICES`。

本轮不增加权限。SSID 属于可能受权限和系统隐私策略影响的 Wi-Fi 信息；在权限不足、
定位服务关闭、系统返回 `UNKNOWN_SSID` 或厂商做字段 redaction 时，显示“未知”即可。
不能为了首页显示一个 SSID 强制申请位置权限。

不在首页增加 BSSID、MAC、扫描结果、实时速率或流量统计；这些信息既不是当前目标，
也不应因理论 API 存在就默认纳入产品。

## 8. 不同网络类型的摘要行为

| 网络类型 | 摘要显示 | 不显示/不推断 |
| --- | --- | --- |
| Wi-Fi | Wi-Fi、连接状态、IPv4、IPv4 Gateway、DNS 数量、IPv6 分类、系统信号分级 | 不把 SSID 缺失当故障；不把 Link-Local 当公网 IPv6 |
| Cellular | 移动网络、连接状态、IPv4、DNS 数量、IPv6 分类、系统联网验证 | 不强制展示传统 Gateway；不执行蜂窝网关 Ping；不显示 Wi-Fi 信号 |
| Ethernet | 以太网、连接状态、IPv4、IPv4 Gateway、DNS 数量、IPv6 分类 | 不显示信号；不假定一定存在 Gateway |
| VPN active | VPN 已启用、当前网络环境的可用地址和 DNS 摘要 | 不断言底层是 Wi-Fi 或蜂窝；不推断 VPN/代理软件名称 |
| Unknown/无活动网络 | 未知网络或未连接、字段显示未知/未配置 | 不把空字段统一解释为网络故障 |

当前 `connectionType` 是由 transport 映射得到的，`vpnActive` 是独立字段。Android
允许 VPN 的底层 transport 动态变化，因此 VPN 场景应优先组合显示“VPN 已启用 + 当前
可观察网络环境”，而不是强行选择“底层网络”或把 VPN 当作普通 Wi-Fi。

## 9. 详情区域边界

点击“查看详情 >”后，建议按以下分组展示，只显示确实存在且可靠的数据：

### 网络环境

- 网络类型；
- 接口名；
- IPv4 地址/prefix（如果已读取）；
- 全部 IPv6 地址及分类；
- VPN；
- 系统联网验证；
- Metered（未来字段存在且完成语义审计后）。

### 路由

- IPv4 网关；
- IPv6 网关；
- IPv6 Link-Local scope/interface（如果真实保留）；
- 无法可靠探测的 IPv6 网关应标注“仅观察值，未执行探测”。

### DNS 环境

- 网络配置 DNS，逐行显示；
- Private DNS 启用状态；
- Private DNS 名称（系统返回时）；
- 不显示虚构的“响应服务器”。

### Wi-Fi（仅 Wi-Fi）

- SSID（可信可用时）；
- Signal 分级；
- Link Speed/Frequency 只有在后续确认权限、厂商差异和数据意义后再加入。

## 10. 建议的数据模型演进（仅设计）

为支持上述摘要与详情，下一实现阶段可以对 `NetworkContext` 做可选字段的向后兼容
扩展，而不是建立 Home 专用 provider：

```text
ipv4Address
ipv4PrefixLength?
ipv6Addresses[] / ipv6Classification
ipv4Gateway?
ipv6Gateway?
ipv6GatewayScope?
interfaceName?
dnsServers[]
privateDnsActive?
privateDnsServerName?
vpnActive?
validated?
metered?
```

实现时应：

1. 继续让 `NetworkRepository` 成为 Home 和 Diagnostic 的单一来源；
2. 旧 `gateway` 与旧单地址字段保留兼容适配，避免一次性破坏旧 Diagnostic、History
   反序列化和测试；
3. 由 mapper 从同一份 `LinkProperties` 生成所有字段；
4. 将“未读取”“无配置”“不适用”“未确认”保留为不同状态，而不是全部转换为空字符串。

本轮不实施模型变更，也不修改 Room Schema。

## 11. 测试矩阵

### 纯 Kotlin/Repository 映射测试

| 场景 | 期望 |
| --- | --- |
| Wi-Fi 双栈，IPv4/IPv6 默认路由顺序互换 | IPv4 Gateway 摘要值不变 |
| IPv4-only Wi-Fi | 显示 IPv4 地址、IPv4 Gateway；IPv6 为未配置 |
| IPv6-only Wi-Fi，只有无 scope 的 Link-Local Gateway | IPv6 显示仅链路本地；Gateway 未确认，不判故障 |
| 多个 DNS，包含长 IPv6 DNS | 摘要显示数量，详情逐行显示 |
| IPv4 地址缺失 | 显示“IPv4 地址：未配置”，不留空 |
| Wi-Fi 无 RSSI | 信号显示未知，不显示伪造等级 |
| Cellular 即使存在系统 Gateway | 摘要 Gateway 不适用，Diagnostic 不探测 |
| VPN active | `vpnActive` 与网络类型分别保留，UI 显示 VPN 提示 |
| `validated = true/false/null` | 只映射为系统联网验证状态，不替代连接状态 |
| `LinkProperties`/Capabilities 读取异常 | 显示未知状态，不崩溃 |

### Sony Xperia 1 VII / Android 16 真机测试

1. 正常双栈 Wi-Fi：IPv4 地址有明确标签，首页 Gateway 显示 IPv4，DNS 显示数量；
2. 展开详情：IPv4/IPv6、两个 Gateway（如有）、DNS、Private DNS 逐项可读；
3. 关闭 Wi-Fi 使用移动数据：Gateway 不适用，不出现网关故障提示；
4. Wi-Fi + VPN/代理：显示 VPN 已启用，不断言具体代理软件；
5. 未授予位置权限或 SSID 被系统隐藏：页面不崩溃，SSID 显示未知；
6. Wi-Fi 与移动数据切换：Home 随同一 NetworkContext 更新，不出现两套 Gateway/DNS 逻辑；
7. 无活动网络：显示未连接/未知，其他字段不误报为故障。

## 12. 非目标

本设计不包含：

- 修改 Compose UI；
- 新增权限或 Wi-Fi 扫描；
- 修改 Diagnostic、Ping、DNS、TCP；
- 新增实时速率、流量统计或图表；
- 新增 LAN Scanner；
- 修改版本号、产品路线或数据库结构。

## 官方 Android 参考资料

- [Read network state](https://developer.android.com/develop/connectivity/network-ops/reading-network-state)
- [LinkProperties API reference](https://developer.android.com/reference/android/net/LinkProperties)
- [NetworkCapabilities API reference](https://developer.android.com/reference/android/net/NetworkCapabilities)
- [WifiInfo API reference](https://developer.android.com/reference/android/net/wifi/WifiInfo)
