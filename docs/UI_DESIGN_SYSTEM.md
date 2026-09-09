# NetworkToolbox UI Design System

Status: accepted foundation for the v0.5.x development line.

This document records the first implementation-ready visual vocabulary for
NetworkToolbox. It is deliberately small: the foundation supplies stable
tokens and a few primitives while existing screens migrate incrementally.

## Brand direction

NetworkToolbox uses **Deep Network Blue** with a **Dark-first** brand
presentation. The visual intent is:

- Clear
- Reliable
- Focused
- Professional
- Calm
- Modern

The Chinese interpretation is 清晰、可信、克制、专业、现代、柔和。

The system intentionally avoids Hacker Terminal, Cyberpunk, Neon-heavy,
game-like styling, excessive glow, dashboard information overload, and an
engineering-demo appearance.

Dark-first describes the primary brand presentation, README/store screenshots,
and the main concept direction. It does not force users into dark mode.

## Color tokens

The Compose implementation is in `core/designsystem`. The following values are
the current first-version tokens.

### Dark theme

| Token | Value | Role |
| --- | --- | --- |
| Background | `#0E1420` | App background |
| Surface | `#151D2A` | Cards and primary surfaces |
| Surface Variant | `#1C2635` | Elevated/secondary surfaces |
| Surface Container Low | `#111A27` | Low tonal elevation |
| Surface Container | `#151D2A` | Standard component container |
| Surface Container High | `#1C2635` | Elevated component container |
| Surface Container Highest | `#243246` | Highest local emphasis |
| Surface Bright | `#212D3D` | Brightest dark surface |
| Primary | `#4C8DFF` | Brand, primary action, selection |
| On Primary | `#06204A` | High-contrast content on bright primary |
| Primary Container | `#1B3560` | Tonal selection/secondary emphasis |
| Secondary | `#76C8D2` | Restrained cyan accent |
| Primary Text | `#F3F7FC` | Main readable text |
| Secondary Text | `#AAB6C5` | Supporting text |
| Outline | `#2B394C` | Dividers and outlines |

### Light theme

| Token | Value | Role |
| --- | --- | --- |
| Background | `#F7F9FC` | App background |
| Surface | `#FFFFFF` | Cards and primary surfaces |
| Surface Variant | `#E9EEF5` | Elevated/secondary surfaces |
| Surface Container Low | `#FBFCFE` | Low tonal elevation |
| Surface Container | `#FFFFFF` | Standard component container |
| Surface Container High | `#E9EEF5` | Elevated component container |
| Surface Container Highest | `#DFE6EF` | Highest local emphasis |
| Primary | `#2F6FED` | Brand, primary action, selection |
| On Primary | `#FFFFFF` | High-contrast content on primary |
| Primary Container | `#DCE8FF` | Tonal selection/secondary emphasis |
| Secondary | `#2A7380` | Restrained cyan accent |
| Primary Text | `#172033` | Main readable text |
| Secondary Text | `#4F5E70` | Supporting text |
| Outline | `#738197` | Dividers and outlines |
| Outline Variant | `#C4CBD7` | Low-emphasis component outline |

### State colors

State colors are independent from the blue brand color.

| Meaning | Dark foreground | Light foreground |
| --- | --- | --- |
| Success / healthy | `#35C98B` | `#147A54` |
| Notice / warning | `#E7B94C` | `#7A5A00` |
| Confirmed error | `#FF5C68` | `#B3261E` |
| Unknown / secondary | `#AAB6C5` | `#506070` |

Light-theme state foregrounds are darker tonal counterparts. This is a
deliberate readability adjustment: the brighter brand-baseline values remain
appropriate as dark-theme accents and are not used as low-contrast text on a
light surface. Status containers and content colors are paired in the theme
implementation so a state is not communicated by color alone.

The dark surface hierarchy is intentionally blue-black rather than purple-gray:
`#0E1420` (background) → `#151D2A` (surface/container) → `#1C2635`
(elevated container) → `#243246` (highest container). All Material 3 surface,
outline, secondary-container, and tertiary roles are explicitly mapped to these
tokens or their approved blue/cyan tonal counterparts; the default purple-gray
fallback is not used.

Semantic rules:

- Blue means brand, primary action, selection, or interaction.
- Green means healthy, online, or success.
- Amber means notice or attention.
- Red means a confirmed error or severe abnormal condition.
- Gray means unknown, not executed, cancelled, disabled, or secondary data.

## Typography

The system uses the Android system font and the Material 3 typography scale.
`NetworkToolboxTypography` gives screen and card headings a restrained
semi-bold hierarchy and uses medium weight for action labels.

The default hierarchy is:

- Screen title: `headlineMedium`
- Section title: `titleLarge`
- Card title: `titleMedium`
- Body: `bodyLarge` / `bodyMedium`
- Supporting text: `bodyMedium` / `bodySmall`
- Label: `labelLarge` / `labelMedium`
- Technical data: `NetworkToolboxTextStyles.TechnicalData`

Technical data uses the system monospace family only where it improves reading
of IP addresses, IPv6 addresses, MAC addresses, hostnames, or other exact
values. The whole application is not rendered as a terminal.

## Spacing

The foundation exposes a small 4 dp grid:

- `XS`: 4 dp
- `SM`: 8 dp
- `MD`: 12 dp
- `LG`: 16 dp
- `XL`: 24 dp
- `XXL`: 32 dp

Existing pages are not rewritten in one pass. New or migrated UI should prefer
these tokens over introducing one-off values such as 7, 11, 13, or 19 dp.

## Shapes

Material 3 shapes are configured as:

- Small: 14 dp
- Medium: 16 dp
- Large: 20 dp

Shared component guidance is:

- Card: 18 dp (`NetworkToolboxComponentShapes.Card`)
- Button: 16 dp (`NetworkToolboxComponentShapes.Button`)
- Chip: pill shape (`NetworkToolboxComponentShapes.Chip`)

The result is soft and modern without making every element excessively round.

## Action semantics

The design system exposes three small action primitives:

- `PrimaryActionButton`: filled `primary` container with `onPrimary` content
  for the page's main action.
- `SecondaryActionButton`: outlined, lower-emphasis action using the brand
  primary for content and the shared outline for its border.
- `DestructiveActionButton`: outlined error action for irreversible operations;
  confirmation dialogs may use a low-emphasis error-colored text action.

The current `onPrimary` values are intentional contrast choices. Dark theme
uses `#06204A` over `#4C8DFF`, while light theme uses white over `#2F6FED`.
The action role remains blue in both themes; error red is reserved for
destructive and confirmed-error semantics.

## Icons

Formal UI uses Material Icons / Material Symbols and a small number of
purpose-built brand vectors. Emoji are not functional icons.

The launcher direction is **Brand N + Network**:

- a simple N-shaped network path;
- three nodes;
- no text;
- no complete Wi-Fi symbol;
- no toolbox outline;
- no stacked network motifs.

The vector stays within the adaptive-icon safe area so circular, squircle, and
rounded-rectangle launcher masks do not clip the path or nodes.

## Status visuals

`StatusVisualState` and `NetworkStatusChip` provide a common visual mapping:

| Internal state | User-facing label | Visual intent |
| --- | --- | --- |
| `NORMAL` | 正常 | Green check |
| `NOTICE` | 提示 | Amber information |
| `WARNING` | 异常 | Amber warning |
| `ERROR` | 严重异常 | Red error |
| `UNKNOWN` | 未确定 | Gray help |
| `RUNNING` | 进行中 | Brand blue activity |
| `CANCELLED` | 已停止 | Gray cancellation |
| `NOT_EXECUTED` | 未执行 | Gray not-executed state |

Icons and labels accompany color. A normal state must not be blue merely
because blue is the brand color.

Bottom Navigation follows the same separation: selected icon and label use the
theme's primary blue, the selected indicator uses the primary container, and
unselected content uses secondary text. Success green is never used for
navigation selection.

## Theme architecture

`NetworkToolboxTheme` lives in `core:designsystem` and supports:

- `SYSTEM`: follows the Android system setting;
- `LIGHT`: deterministic light theme for previews or future settings;
- `DARK`: deterministic dark theme for previews or future settings.

The app currently uses the default `SYSTEM` mode and does not add a manual
theme setting in v0.5 foundation work. Dark and light system-bar icon
appearance is synchronized with the resolved theme, while the existing
edge-to-edge and bottom navigation architecture remains unchanged.
The App Shell supplies explicit NavigationBar colors so Material 3's
secondary/teal selection default cannot override the NetworkToolbox primary
selection semantics.

## Shared primitives

The foundation currently provides only stable primitives:

- `NetworkCard`: shared card surface, shape, padding, and content spacing;
- `NetworkStatusChip`: icon + label + semantic state color;
- `PrimaryActionButton`, `SecondaryActionButton`, and
  `DestructiveActionButton`: small semantic action wrappers;
- `SettingsSection`, `SettingsRow`, and `SettingsInfoRow`: compact outlined
  groups and full-width settings rows;
- `networkToolboxNavigationItemColors`: primary-blue selection and
  secondary-text unselected navigation mapping;
- `NetworkToolboxColors`, `NetworkToolboxSpacing`, component shapes, and
  technical text style tokens.

Existing feature-local components such as tool cards and section headers remain
in place until their individual migration tasks. No generic UI framework is
being introduced.

## Home pattern

The Home destination is the compact entry point for current network context and
the most-used checks. It does not repeat the App Icon, App Name, or a large
brand header after the user has entered NetworkToolbox. Its vertical order is
stable:

1. Network summary hero.
2. Automatic diagnostic primary action within the hero card.
3. Quick Tools, limited to four existing tools.
4. Recent Diagnosis.

The network summary hero is the Home Hero pattern. It uses the shared
`NetworkCard`, a network-type icon with real Wi-Fi signal-strength mapping, a
real SSID only when the existing permission boundary makes it available, and a
conservative `已连接` / `未连接` / `状态未知` status chip. A chevron with an
accessible content description opens the existing detail area. The summary has
exactly four compact metrics in a fixed order: IPv4 address, subnet mask,
default gateway, and DNS. IPv6 and numeric Wi-Fi signal details remain behind
the chevron; IPv6 is not represented as a public-connectivity claim. The Home
DNS summary uses one preferred address only, while the complete configured list
remains in details. Cellular uses a non-misleading
`不适用` gateway value and no Wi-Fi signal field; Ethernet uses the Ethernet
identity and no Wi-Fi signal field. The metrics fall back to a readable two
column layout for phone widths or larger font scales, rather than forcing tiny
four-column text.

Automatic diagnosis is the single prominent action on Home and uses
`PrimaryActionButton` inside the network hero. It keeps the existing navigation
callback and does not start a second data source or a new background check.
The action is presented without an additional privacy helper block in the
hero; complete privacy explanation remains in the appropriate diagnostics and
settings surfaces. Recent Diagnosis is a compact real-data preview; an empty
history uses `暂无诊断记录` rather than placeholder content.

## Tools pattern

Tools is the complete entry point for currently implemented tools. It uses the
following product-facing categories and preserves the existing callback
routes:

- `连通性检测`: Ping, DNS Lookup, TCP Port Check, Traceroute;
- `网络工具`: IPv4 子网计算, 局域网扫描;
- `诊断与记录`: 网络诊断, 历史记录.

Each category is rendered as a compact two-column grid. The grid is part of the
scrollable destination and respects the App Shell's safe-drawing and bottom
navigation insets. Future concepts such as Wake-on-LAN are not shown until
they are implemented. Home and Tools use the same tool definitions and
navigation callbacks so an entry cannot silently diverge between destinations.

## Quick tool pattern

Quick Tools is a curated subset of at most four existing tools. It is rendered
as a two-column grid with token-based vertical gaps so the Home destination
does not spend most of its first screen on repeated whitespace. A tool card is
clickable across its full surface and exposes one semantic action to
accessibility services. The icon and trailing arrow are decorative when the
card already supplies the label, so the same action is not announced twice.

Home `QuickToolCard` is a compact outlined surface: it uses the shared card
shape, theme surface, outline-variant border, 40 dp icon container, title,
short explanation, and trailing affordance. Its content is limited to the
real four quick-tool definitions and has no demo values or filler content.
The complete Tools destination may continue to use `ToolCard`; both variants
receive real callbacks and label data from the shared catalog.

Recent Diagnosis is a compact real-history card. It keeps the report title,
one-line summary, relative time, and a status icon derived from the stored
diagnostic result; an empty history shows only `暂无诊断记录` and does not
invent a sample report. The card remains a route to local History.

## Network summary pattern

The summary/detail boundary is intentional:

- Summary: network identity icon and name, `已连接` / `未连接` /
  `状态未知`, IPv4 address, subnet mask, default gateway (or an explicit
  cellular `不适用` value), and a concise DNS value.
- Details: network type, interface, prefix and subnet mask when available,
  every IPv6 address, IPv4 gateway, each configured DNS server on its own
  line, numeric Wi-Fi signal when present, Private DNS values when present, VPN
  state, and system validation.

IPv6 is labelled `未配置`, `仅链路本地`, `已配置`, or `未知`. `已配置` is
not a claim that public IPv6 connectivity works. Wi-Fi and Ethernet prefer the
IPv4 default gateway for the ordinary gateway summary; cellular does not show
an internal next-hop as a user-facing gateway. Home DNS shows one preferred
address directly, with no additional `+N` count; the full configured list is in
details. SSID
is shown only when already available and meaningful; the Home migration adds no
location or nearby-device permission and never displays `<unknown ssid>`.

These values come from the shared `NetworkContext` provided by the existing
network repository. Home does not parse Android `LinkProperties` or routes on
its own. Missing or restricted data is represented as an explicit empty,
unknown, or not-applicable state. The UI never invents latency, Wi-Fi speed,
SSID, device counts, or health conclusions.

## Tool accent policy

Tool accents are a small, stable vocabulary mapped to existing theme tokens:

- `PRIMARY`: primary blue for the main tool family and brand emphasis;
- `CYAN`: restrained secondary cyan for network-information utilities;
- `AMBER`: attention-oriented amber for diagnostics or port-oriented tools.

Accents are decorative grouping cues, not result states. Result status must use
the semantic `StatusVisualState` mapping. Cards must not introduce per-tool
rainbow colors, hard-coded feature hex values, gradients, neon/glow effects,
or heavy shadows. The same accent mapping is valid in both light and dark
themes, with contrast supplied by the theme's paired foreground/container
tokens.

Concept art and mockups are direction only. Production Home and Tools render
current repository data and real navigation callbacks; example values such as
sample IP addresses, SSIDs, speeds, latency, or device counts must never be
used as UI data.

## App shell and migration

The App Shell continues to contain exactly three top-level destinations:

- 首页 / Home
- 工具 / Tools
- 设置 / Settings

Tool entry routes retain their caller in the app shell. A tool opened from Home
returns to Home, while a tool opened from Tools returns to Tools; the Android
system back action and each tool's visible back action use this same source-aware
transition. Bottom navigation remains a direct top-level switch and does not
create an additional back-stack layer.

This task changes the top-level visual foundation only. It does not add a
fourth tab, change navigation, or implement the LAN Device Center.

Migration follows a staged path rather than a Big Bang rewrite:

1. Design foundation and launcher icon.
2. App Shell.
3. Home visual migration (implemented for the current v0.5.x line).
4. Tools visual migration (implemented for the current v0.5.x line).
5. Automatic Diagnostics, Report, and History.
6. Existing tool pages.
7. LAN Device Center.
8. Wake-on-LAN.

Each stage must preserve business semantics and pass unit tests, lint, build,
and Sony Android 16 smoke checks where applicable.

## Diagnostics, Report, and History patterns

The v0.5 visual migration applies a two-tier surface rule to these existing
destinations. Filled or tonal surfaces are reserved for a hero, an overall
status, a primary diagnosis, an important attention state, or the single
primary action. Tool/action entries and ordinary grouped information use the
shared outlined surface. This keeps emphasis meaningful without introducing a
new visual language.

### Automatic Diagnostics pattern

The idle state is concise: a short explanation, the local-only privacy note,
and one primary `开始诊断` action in an outlined information surface. The
running state uses one overall running hero, compact real check rows, a real
stage progress indicator, and a secondary `停止诊断` action. It does not
render six large parameter cards. The completed state follows this order:
status hero, diagnosis conclusion, findings, recommendations, stage checks,
optional technical details, and export actions.

`NORMAL` is shown with the green normal visual, `ATTENTION`/`NOTICE` with an
amber notice visual, `ERROR` with the red error visual, and `UNKNOWN` with the
neutral visual. These visuals are applied to the relevant hero, chip, or row;
the entire page is never tinted. A normal report with no material findings
does not repeat a second “network normal” finding. NOTICE findings use an
outlined, low-emphasis treatment; warning and error findings use the shared
semantic status visual without turning every section into an error banner.
The UI shows at most the current analyzer recommendation limit and never adds
recommendations of its own. VPN, Fake-IP context, a gateway timeout with
successful Internet evidence, and similar conservative notices remain
informational rather than being promoted to a fault.

Technical details are the lowest-priority expandable group. They use readable
Chinese labels and key/value rows, preserve long addresses without forced
single-line alignment, and never expose raw enum names or machine codes in
ordinary content. Retry/Verify remains a compact comparison section and does
not become a second hero; the existing retry and verification semantics are
unchanged.

### Report pattern

Live reports and restored History reports use the same
`DiagnosticReportPresentation` adapter and the same visual hierarchy. The
header carries the report title, time, and available network type. A compact
overall status hero is followed by the diagnosis conclusion, findings,
recommendations, stage checks, expandable technical details, and export
actions. Copy, save-PDF, and share-PDF remain available through the existing
renderer and FileProvider flow; export is an outlined or tonal secondary
action, not a competing primary action. Restoring a report displays the saved
snapshot and never reruns analysis.

### History pattern

History uses compact outlined surfaces with a semantic status chip derived from
the stored structured result, followed by the record type, title/target,
summary, available network context, date, and an explicit report affordance
when a report snapshot is restorable. The status is never inferred from a
free-form summary string. Delete and clear remain lower-emphasis actions, and
clear-history confirmation semantics are unchanged. Empty, loading, and error
states use the same compact outlined treatment and the shared local-history
wording. A report card opens the exact saved snapshot through the existing
source-aware navigation path; it does not re-run or re-analyze the report.

#### History List Pattern

- Each item is a compact outlined surface rather than a filled heavy card.
- The card itself is the primary open action for a restorable report and uses a
  trailing chevron as its affordance.
- Destructive actions remain secondary and are kept out of the open-action
  hierarchy.
- A type title is shown once; target, diagnosis summary, network context, and
  metrics occupy the supporting lines only when they are available.
- The stable reading order is status, title, time, summary, and compact
  metadata. Spacing uses the shared tokens and lets long text wrap naturally.

### Outlined surface policy and hero boundary

Use `OutlinedNetworkCard` for ordinary sections, check groups, findings,
recommendation groups, technical details, history records, idle/error
information, and running tool state. Use a filled or tonal `NetworkCard` only
where the surface itself establishes hierarchy: a completed overall-status
hero or another explicitly primary diagnosis surface. Do not place a Card
inside another Card. A `NetworkStatusChip` may appear inside a hero or an
outlined group as the compact semantic status indicator.

This boundary applies in both light and dark themes. Spacing uses the shared
`NetworkToolboxSpacing` tokens, typography uses Material 3 plus the shared
technical-data style for addresses and other raw values, and status icons use
the shared semantic mapping. Components must remain readable at large font
scales and on narrow phones; long addresses wrap within their value area
instead of forcing horizontal overflow. Existing navigation, system back,
history snapshot, export, and business rules are outside this visual layer.

## Scope boundary

This foundation does not modify Ping, DNS, TCP, Traceroute, LAN Scanner,
Automatic Diagnostics, History, Report, Retry/Verify, or their data semantics.
The current Home and Tools work is a visual/presentation migration only; it
does not start the later Automatic Diagnostics, LAN Device Center, or
Wake-on-LAN work.

## Core Tool Screen Pattern

The core network tools use one predictable Compose reading order:

1. **Tool header** — a source-aware back action, the tool icon, the tool name,
   and one short description. Core screens do not repeat the NetworkToolbox
   brand header.
2. **Input / target** — one `OutlinedNetworkCard` groups the target fields and
   any optional parameters. Each input uses the Material 3
   `OutlinedTextField`; validation remains inline and user-readable.
3. **Primary action** — one shared `PrimaryActionButton` starts the existing
   operation. Stop or retry actions use the appropriate secondary or
   destructive style and do not change the operation semantics.
4. **Running state** — the shared `ToolRunningSection` uses an outlined (or,
   if a future screen needs it, very light tonal) surface to show real
   progress or live values supplied by the existing ViewModel. It never uses
   fake timers, simulated progress, or a full-screen loading treatment.
5. **Result summary** — the first result surface shows the localized status,
   the target, and the most important conclusion. Tool status is not promoted
   into a claim about the entire network.
6. **Key metrics** — related values use the compact `ToolMetricGrid` instead
   of one large card per metric. Details use shared key/value rows and the
   technical-data style for addresses, hostnames, and other raw values.
7. **Detailed result** — secondary fields, per-record/per-hop rows, and
   technical information stay in the same clear hierarchy and may be
   collapsed where the existing tool already supports it.

Ordinary input, result, detail, and list sections use outlined surfaces. Filled
or tonal surfaces are reserved for the primary action context, running state,
or an explicitly primary overall result. Cards are not nested inside cards.
The shared spacing tokens (`XS`, `SM`, `MD`, `LG`, `XL`, `XXL`) and Material 3
typography are used instead of screen-specific spacing values.

The common status vocabulary is localized for ordinary users: `NORMAL` is
shown as 正常, `NOTICE` as 提示, `WARNING` as 异常, `ERROR` as 严重异常,
`UNKNOWN` as 未确定, and `CANCELLED` as 已停止. Raw enum names, exception
strings, and machine codes are not displayed as ordinary result labels. The
technical meaning of each tool result remains owned by its existing engine,
UseCase, and presentation mapping.

Ping, DNS Lookup, TCP Port Check, Traceroute, and IPv4 Subnet Calculator keep
their current parameters, detection/calculation rules, limits, history
behavior, and source-aware navigation. This pattern changes only their
Compose presentation; it does not add a network protocol, alter a timeout,
or redefine a result.

## Running State Pattern

All core tools with a visible in-flight operation use the shared
`ToolRunningSection`. Its default surface is `OutlinedNetworkCard` with the
existing Deep Network Blue theme surfaces and outline variant. The process
accent is `StatusVisualState.RUNNING` / primary blue; it is never rendered as
healthy green, attention amber, or error red. Progress indicators consume the
real progress supplied by the current ViewModel and use primary blue on a
low-contrast track. Existing compact metrics remain visible without turning
each metric into a filled card. Ping and Traceroute keep their outlined
destructive stop action; DNS and TCP retain their existing short loading state
without inventing a stop control. A running surface is intentionally weaker
than a completed result hero.

## Home Network Summary Preference

The Home Hero DNS value is a presentation-only compact summary. It selects the
first valid IPv4 configured DNS address, then falls back to the first valid
IPv6 address when no IPv4 address exists. It always shows only that one
preferred address and never appends a `+N` count. An empty or unrecognized list
uses the existing unavailable wording. The full configured DNS list remains
unchanged in Network Detail, and Automatic Diagnostics and DNS Lookup continue
to consume the complete `NetworkContext`/DNS result rather than this Home-only
preference.

## Settings Pattern

Settings keeps the existing v0.5 shell and uses a compact Android Settings-like
hierarchy: a short page title, followed by About, Data Management, and Privacy
Protection groups. Each group is one `OutlinedNetworkCard` containing full-width
rows separated by dividers; rows may include an icon, title, supporting copy,
and a trailing value or action. Interactive rows preserve a comfortable touch
target and expose button semantics. Destructive actions use low-emphasis error
content on the row and keep the existing confirmation dialog; they never become
a blue filled primary action.

The About group keeps the real app name, open-source description, compact brand
network icon, and the dynamic `BuildConfig.VERSION_NAME`. Data Management keeps
local History and Clear History. Privacy Protection explains local-first data
handling, no account requirement, and no upload of network test results or
history. Settings does not add a language picker, theme picker, analytics,
account system, cloud backup, or other unimplemented controls. Light and dark
themes reuse the same NetworkToolbox surface, outline, typography, and semantic
destructive tokens.
