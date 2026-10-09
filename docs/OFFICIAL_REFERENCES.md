# 官方文档核查

核查日期：2026-10-09。按官方网页及 Google Maven 的实际稳定版工件实施，没有使用非官方 Garmin 自动登录接口。

| 官方来源 | 本项目采用的规则 |
| --- | --- |
| [Health Connect Get started](https://developer.android.com/health-and-fitness/health-connect/get-started) | SDK status 检测、只读 Manifest、权限结果合约、实际 grant 检查、双版本 rationale 入口 |
| [Check availability](https://developer.android.com/health-and-fitness/health-connect/availability) | 系统模块与旧版 APK 的区别、工作资料和设备环境限制 |
| [Read aggregated data](https://developer.android.com/health-and-fitness/health-connect/aggregate-data) | 官方累计/统计聚合，保留无数据的空值；活动/睡眠按来源优先级去重 |
| [Read raw data](https://developer.android.com/health-and-fitness/health-connect/read-data) | 原始记录分页与元数据；步数统计使用 Aggregate |
| [Health Connect releases](https://developer.android.com/jetpack/androidx/releases/health-connect) | V0.1 使用 stable 1.1.0；不用 1.2.0 alpha 的新权限/特性 |
| [Health Connect data types](https://developer.android.com/health-and-fitness/health-connect/data-types) | 四种 Record 与对应只读权限 |
| [AGP 8.13 release notes](https://developer.android.com/build/releases/agp-8-13-0-release-notes) | AGP 8.13.2 / Gradle 8.13 / API 36 / JDK 17 的兼容工具链 |
| [Health Connect test cases](https://developer.android.com/health-and-fitness/health-connect/test/test-cases) | 设备授权、拒绝、撤销、不可用和读取失败验收 |
| [Garmin 分享到 Health Connect](https://support.garmin.com/en-GB/?faq=JToBEy0jfe6pIygark2Ui5) | 官方功能要求 Android 14+；单向写入，启用后随成功设备同步发送数据；读取授权不代表来源已写入 |
| [Google 关联与权限](https://support.google.com/android/answer/12201227?hl=zh-Hans) | 来源应用与本应用分别授权；部分来源需要先从自身启动连接，不虚构 Garmin 菜单路径 |
| [Google 查找数据](https://support.google.com/android/answer/12201872?hl=zh-Hans) | 按类别和类型查看所有条目、日期及来源，先区分上游空数据与下游读取异常 |

官方入门页当前示例推荐 alpha SDK，V0.1 所需四类型与授权/聚合功能已存在于 stable 1.1.0，因此选用稳定版。官方 sources JAR 核对了 `ReadRecordsRequest` 参数、聚合常量、Metadata 及设置 action，基础测试使用官方 `connect-testing` 的 FakeHealthConnectClient（测试依赖，未打入生产业务）。
