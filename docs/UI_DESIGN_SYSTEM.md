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
| Primary | `#4C8DFF` | Brand, primary action, selection |
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
| Primary | `#2F6FED` | Brand, primary action, selection |
| Secondary | `#2A7380` | Restrained cyan accent |
| Primary Text | `#172033` | Main readable text |
| Secondary Text | `#4F5E70` | Supporting text |
| Outline | `#738197` | Dividers and outlines |

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

## Theme architecture

`NetworkToolboxTheme` lives in `core:designsystem` and supports:

- `SYSTEM`: follows the Android system setting;
- `LIGHT`: deterministic light theme for previews or future settings;
- `DARK`: deterministic dark theme for previews or future settings.

The app currently uses the default `SYSTEM` mode and does not add a manual
theme setting in v0.5 foundation work. Dark and light system-bar icon
appearance is synchronized with the resolved theme, while the existing
edge-to-edge and bottom navigation architecture remains unchanged.

## Shared primitives

The foundation currently provides only stable primitives:

- `NetworkCard`: shared card surface, shape, padding, and content spacing;
- `NetworkStatusChip`: icon + label + semantic state color;
- `NetworkToolboxColors`, `NetworkToolboxSpacing`, component shapes, and
  technical text style tokens.

Existing feature-local components such as tool cards and section headers remain
in place until their individual migration tasks. No generic UI framework is
being introduced.

## App shell and migration

The App Shell continues to contain exactly three top-level destinations:

- 首页 / Home
- 工具 / Tools
- 设置 / Settings

This task changes the top-level visual foundation only. It does not add a
fourth tab, change navigation, or implement the LAN Device Center.

Migration follows a staged path rather than a Big Bang rewrite:

1. Design foundation and launcher icon.
2. App Shell.
3. Home.
4. Tools.
5. Automatic Diagnostics, Report, and History.
6. Existing tool pages.
7. LAN Device Center.
8. Wake-on-LAN.

Each stage must preserve business semantics and pass unit tests, lint, build,
and Sony Android 16 smoke checks where applicable.

## Scope boundary

This foundation does not modify Ping, DNS, TCP, Traceroute, LAN Scanner,
Automatic Diagnostics, History, Report, Retry/Verify, or their data semantics.
It also does not start Home visual migration, LAN Device Center, or Wake-on-LAN.
