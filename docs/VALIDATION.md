# V0.1 编译与验收记录

日期：2026-10-09。真实 ColorOS 16 手机未连接到开发环境；模拟器结果不能替代 Garmin 来源数据和 ColorOS 真机验收。

## 已完成

运行环境：Linux x86_64、Temurin JDK 17、Android SDK 36、Build Tools 35.0.0、Gradle Wrapper 8.13。

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --console=plain
```

结果：**BUILD SUCCESSFUL**。24 个基础测试通过，0 failures / errors / skipped。

| 测试 | 数量 | 覆盖 |
| --- | ---: | --- |
| DomainTest | 5 | 夏令时 23 小时日、午夜空窗口、同 ID 更新与跨来源、缺失/零区分、空 ID 拒绝 |
| RepositoryTest | 8 | 最小授权、撤销清除、失败保留缓存与隔离、读中撤销、成功空结果删除旧记录、取消传播、不可用、中断恢复 |
| HealthConnectSourceTest | 5 | 官方 FakeHealthConnectClient 的 1,001 条分页、聚合取值、缺失、权限阻断、不可用、心率/距离/睡眠 payload 和单位 |
| RoomStoreTest | 3 | 真正 Room / SQLite 的重复刷新、更新、无效快照保护、撤销、清除和空值持久化 |
| ViewModelTest | 3 | 权限检查失败隐藏缓存、授权回调队列、进入后台隐藏健康值 |

Room 测试使用 Robolectric API 35；Health Connect Fake 测试使用 API 28。测试数据只存在于 test 源集，未进入正式业务。基础测试不证明真实厂商组件或来源应用的行为。

Lint：0 errors，18 warnings（16 个版本更新提示、2 个 KTX 用法提示）；未设置 lint baseline 或屏蔽错误。

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`；minSdk 28、targetSdk 36，APK Signature Scheme v2 校验通过。合并 Manifest 只有四类健康读取权限和 AndroidX 内部 receiver 权限，没有 INTERNET、健康写入、历史读取、后台读取、位置或账号权限。

本机准备过程中补齐 SDK/JDK、配置代理 CA 信任，并重新下载且核验了 Robolectric 测试依赖的 SHA-512。环境配置未提交到项目；没有关闭 TLS 或依赖完整性检查。

## 设备验证进度

Android 16 / API 36 Google APIs x86_64 软件模拟器已创建。开发主机没有 KVM，系统首次启动和 APK 优化较慢；安装、启动和 Compose instrumentation 验证结果在完成后补充。两项 instrumentation 测试 APK 已编译，覆盖默认不选择权限、单类型请求及不可用/缺失界面。

## ColorOS 16 真机验收步骤

| 场景 | 操作与通过条件 |
| --- | --- |
| 首次安装 | APK 可安装和启动，无自动授权弹窗；展示组件检测状态 |
| 最小授权 | 只勾步数，系统请求只有步数；其余三类保持未授权 |
| 拒绝/部分授权 | 拒绝后仍可浏览；只读取系统实际授权类型，不循环弹窗 |
| 四类型读取 | 在 Health Connect 先确认来源记录，授权后读到真实数据和包名；无记录则明确缺失 |
| 重复刷新 | 同 record ID 不重复；当天步数不会通过重复刷新翻倍 |
| 上游更新/删除 | 成功刷新后新版本替换旧版本，删除记录从本地缓存消失 |
| 撤销权限 | 在系统撤销一类，返回应用；该类不再读取和显示，Room 缓存清除，其他类型继续使用 |
| 读中撤销 | 刷新时撤销一类，失败可恢复，不崩溃，不提交半份该类快照 |
| 无数据 | 未写入的日期显示 `— / 缺失`，不伪造零；心率曲线不跨缺失日连接 |
| 数据口径 | 步数/距离对照 Health Connect Aggregate，心率为日均；睡眠区间可能按午夜拆分 |
| 同步状态 | 来源 lastModified 和本机 lastSuccess 分开展示；失败时清楚标记缓存 |
| 深色模式 | 切换系统深色模式，检查文字对比、按钮、卡片和趋势 |
| 大字体/旋转 | 系统大字体、旋转后权限勾选保留，内容可滚动，返回键返回概览 |
| 清除本地数据 | 确认后缓存和同步状态删除，HC 原始记录仍存在，可重新手动刷新 |
| 无组件/工作资料 | 能看到不可用解释，不创建不可用客户端强制读取；设置入口失败时有提示 |

任何尚未有数据的类型只能验收“缺失”状态，不能宣称 Garmin 已成功同步该类型。上架、生产签名、长时间后台、电池测试、FIT 导入和真实 AI 接入不在本版本范围内。
