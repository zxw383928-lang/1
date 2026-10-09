# Garmin AI Coach — V0.1

面向 Garmin 用户的独立开源 Android 应用。通过 **Health Connect 官方 SDK** 读取设备上已存在的步数、心率、距离、睡眠，以 Kotlin / Compose / Material 3 展示并保存到本地 Room / SQLite。

> 与 Garmin 无隶属关系。不会自动登录 Garmin Connect，不索取 Garmin 密码，不提供医学诊断。V0.1 没有 AI 服务和网络权限，不上传健康数据。Garmin 数据是否存在取决于来源应用是否已将该类型写入 Health Connect；应用不会补造缺失数据。

## 安装

最新修订版 **V0.1.1** 修正前后台权限检查队列、清除缓存时的待处理检查、Room 多表快照一致性，以及窄屏 / 大字体布局。

在 [GitHub Releases](https://github.com/zxw383928-lang/1/releases) 下载 `garmin-ai-coach-v0.1.1-debug.apk`。这是开发签名 APK，仅用于验证；安装时允许当前浏览器或文件管理器“安装未知应用”。若旧版因签名不同无法覆盖安装，见[真机安装说明](docs/DEVICE_ACCEPTANCE.md#安装前)。

```bash
adb install -r garmin-ai-coach-v0.1.1-debug.apk
```

Android 9+ 可安装，compileSdk / targetSdk 36（Android 16）。Android 14+ 使用系统 Health Connect；Android 9–13 需官方组件。**ColorOS 16 的地区版本、系统模块和 Google Play 服务可能影响可用性，以应用检测结果为准。** 工作资料不支持本功能。

## 在真实设备上使用

1. 在来源应用的官方设置中启用向 Health Connect 写入所需数据，先在 Health Connect 检查是否确实有记录。不要因为佩戴了 Garmin 设备，就假定四种记录均已同步。
2. 启动 Garmin AI Coach，查看 Health Connect 检测结果。不可用时按界面提示检查系统组件、系统更新及 Google Play 服务。
3. 点“数据权限”，勾选需要读取的类型。默认不选，可只授权步数。系统允许拒绝或部分授权。
4. 授权返回后应用读取已授权类型；之后点“手动刷新”更新。每次回到前台和读取前都会检查权限。
5. 查看今日值、近 7 天趋势、每日值、有效数据日均、聚合来源、本机最后成功同步时间和最近原始记录的来源更新时间。
6. 在 Health Connect 设置中撤销某类权限，返回应用后该类缓存清除。也可通过“清除本地数据”删除应用内全部缓存。

近 7 天包括今天及此前 6 个**本地日历日**，今天只读取到刷新时刻。切换系统时区后应刷新，旧时区的统计不会显示成新时区统计。

## 数据口径和边界

| 类型 | 趋势统计 | 原始记录 |
| --- | --- | --- |
| 步数 | `StepsRecord.COUNT_TOTAL` | count 与来源 metadata |
| 心率 | `HeartRateRecord.BPM_AVG`，每日平均值 | 原始样本存入 payload；列表显示该记录样本均值 |
| 距离 | `DistanceRecord.DISTANCE_TOTAL`，米转 km | distance 与来源 metadata |
| 睡眠 | `SleepSessionRecord.SLEEP_DURATION_TOTAL`，秒转 h | 会话及 stages；列表显示会话区间长度 |

- 活动和睡眠趋势由官方 Aggregate API 按用户设置的来源优先级处理重叠数据，不直接相加原始记录。心率按官方聚合语义计算，可能包含多个来源，不宣称跨来源的同一生理测量已被去重。
- 原始记录以 `Health Connect + 类型 + 来源包名 + record ID` 去重。同 ID 的版本保留最新修改时间；不同来源保留，方便审计来源。
- 每次完整读取一类记录的所有分页和 7 个日聚合后，在单个 Room 事务中替换该类型缓存。重复刷新不增加行，来源中删除的记录在下一次成功刷新后消失。V0.1 缓存限于最后一次成功读取的窗口；不维护无限历史。
- Sleep 聚合按当地午夜切分，跨夜会分到两天。与按“起床日”显示的 Garmin 睡眠可能不同。原始区间长度与官方睡眠聚合也可能不同。
- 缺失是 `null`，显示 `— / 缺失`；实际零保持零。图中缺失点为空心且不跨缺失连线。有数据日均只使用非空日，包含今天的部分日值。
- 来源包名由 Health Connect 返回；不硬编码或伪装为 Garmin。来源最后修改时间与本机读取成功时间分别展示；没有条件判断 Garmin 上游同步是否完成。
- 读取失败保留仍获授权的数据缓存，明确标记失败和时间。一类失败不阻断其余已授权类型。无法确认权限时隐藏健康数据；确认撤销则删除对应缓存。应用关闭期间不能实时清除缓存，需再次进入前台。
- 本地数据库处于应用私有目录，依靠 Android 沙箱保护，未额外实现数据库加密。备份与设备迁移已排除，不记录健康 payload 到日志。

## 开发与编译

工具链：JDK 17、Gradle Wrapper 8.13（校验 SHA-256）、AGP 8.13.2、Kotlin 2.1.20、Compose BOM 2025.05.01、Health Connect 1.1.0 稳定版、Room 2.7.2、KSP 2.1.20-1.0.32。

安装 Android SDK 36 与 Build Tools 35.0.0，在 Android Studio 打开根目录。配置本机 `local.properties`（不要提交）：

```properties
sdk.dir=/your/path/to/android-sdk
```

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
# 连接 Android 设备或启动模拟器后
./gradlew :app:connectedDebugAndroidTest
```

APK：`app/build/outputs/apk/debug/app-debug.apk`。测试报告：`app/build/reports/tests/testDebugUnitTest/index.html`；Lint：`app/build/reports/lint-results-debug.html`。

GitHub Actions 在 push / pull request 上编译、执行单元测试和 Lint，并保存 APK、测试和 Lint 报告。另有手动触发的 `Android 16 device verification` 工作流，使用带 KVM 的官方 Google APIs 模拟器安装 APK、启动真实 Activity、检查官方 Health Connect 可用性并运行 Compose 测试。没有自动上传任何用户设备健康数据的功能。发布构建与生产签名不属于 V0.1。

## 文档与交付

- [架构与数据流](docs/ARCHITECTURE.md)
- [健康数据权限与隐私](docs/PERMISSIONS.md)
- [编译、测试与真机验收](docs/VALIDATION.md)
- [ColorOS 16 真机操作清单](docs/DEVICE_ACCEPTANCE.md)
- [第二阶段 DeepSeek / FIT 接口设计](docs/PHASE2.md)
- [官方文档核查记录](docs/OFFICIAL_REFERENCES.md)

使用 MIT 许可证。健康数据始终留在使用者的设备上；提交 issue 时请勿附带健康记录或数据库。
