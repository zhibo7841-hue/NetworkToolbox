# NetworkToolbox

An open-source Android network analysis and troubleshooting toolkit.

NetworkToolbox 是一个开源 Android 网络分析与故障排查工具箱，帮助用户了解网络状态、执行针对性的本地检测，并获得排障参考。它不是自动修复工具，也不承诺自动准确诊断所有网络故障。

## Current version

- Application version: `0.1.0`
- Minimum Android version: Android 12 (API 31)
- Target Android SDK: API 36

## Core principles

- Open source
- Privacy first
- No ads
- No account required
- Local first
- Network test results stay on the device and are not uploaded

## Features

当前 V0.1 功能包括：

- ✅ Network Information
- ✅ Ping
- ✅ DNS Lookup
- ✅ TCP Port Check
- ✅ IPv4 Subnet Calculator
- ✅ Network Diagnostic Report
- ✅ Local History

## Roadmap

以下内容仅为 Planned，不代表已承诺的发布范围：

- LAN Scanner
- Wi-Fi Analyzer
- mDNS
- Wake-on-LAN

## Screenshots

截图目录已预留在 [`docs/screenshots/`](docs/screenshots/)。当前仓库不包含伪造或占位图片。

## Installation

Download the APK from GitHub Releases.

Requires Android 12 or later.

The final `0.1.0` version is configured. Release APK publishing still requires a maintainer-provided signing configuration.

## Privacy

- All network test results are stored locally.
- No account required.
- No network data upload.

NetworkToolbox 不要求账号，检测结果和历史记录默认只保存在设备本地。应用访问网络是为了执行用户主动选择的检测，不代表会上传检测数据。

## Documentation

- [Product plan](docs/PRODUCT_PLAN.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Decision log](docs/DECISIONS.md)
- [Release plan](docs/RELEASE_PLAN.md)
- [Release notes draft](docs/RELEASE_v0.1.0.md)
- [Development workflow](docs/DEVELOPMENT_WORKFLOW.md)
- [OSS research](docs/OSS_RESEARCH.md)

## Contributing

请先阅读 [CONTRIBUTING.md](CONTRIBUTING.md)。

## Security

安全问题请按照 [SECURITY.md](SECURITY.md) 中的说明私下报告，不要公开发布未修复的漏洞细节。

## License

NetworkToolbox 使用 [Apache License 2.0](LICENSE) 发布。
