# 健康数据权限与隐私

## 清单

| Android 权限 | 官方记录类型 | 用途 |
| --- | --- | --- |
| `android.permission.health.READ_STEPS` | StepsRecord | 步数和近 7 天趋势 |
| `android.permission.health.READ_HEART_RATE` | HeartRateRecord | 心率样本、记录及日均 |
| `android.permission.health.READ_DISTANCE` | DistanceRecord | 距离和近 7 天趋势 |
| `android.permission.health.READ_SLEEP` | SleepSessionRecord | 睡眠会话、阶段及日内累计 |

Manifest 声明四类可申请权限，但运行时只通过 `PermissionController.createRequestPermissionResultContract()` 请求用户勾选且尚未授权的类。没有开屏自动弹授权、全选默认、拒绝后循环提示或数据写入。

不申请 `INTERNET`、位置、Garmin 账号、存储全盘访问、Health Connect 写入、历史数据读取、后台健康数据读取权限。最近 7 天在默认可读范围内，无须扩大历史权限。

## 官方隐私入口

`PermissionsRationaleActivity` 处理 `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE`；Android 14+ 使用受 `android.permission.START_VIEW_PERMISSION_USAGE` 保护的 activity-alias，处理 `android.intent.action.VIEW_PERMISSION_USAGE` 和 `android.intent.category.HEALTH_PERMISSIONS`。用户在系统授权页点隐私说明时可访问相同页面。

如未来上架 Google Play，需要在 Play Console 声明实际数据类型并提供与应用一致的公开隐私政策 URL；当前为 Debug 侧载验证，不声称已获 Play 上架批准。

## 拒绝与撤销

- 拒绝、部分授权：未授权类显示未授权，不访问其 API；已授权类可继续使用。
- 前台检查与每次读取前检查实际权限。确认撤销后删除对应 Room 原始记录和日聚合；不删除 Health Connect 本身的记录。
- 系统在读取过程中拒绝访问：处理 SecurityException，停止该类读取并清除其缓存。
- 检查权限发生异常：健康值隐藏，提示重试。已保存数据库不因暂时 IPC 失败自动误删。
- 应用未运行时无法立即执行清理；下一次进入前台检查后清理。“清除本地数据”立即删除全部应用缓存；卸载也删除应用私有数据。

## 存储与传输

Room / SQLite 保存到 Android 应用私有目录，未额外实现 SQLCipher。数据库包含健康记录，设备获得 root 或受损时的隔离不作额外保证。Android 云备份和设备传输均明确排除所有应用数据；应用 allowBackup=false。无遥测、日志健康 payload、崩溃上传、广告 SDK、后台同步或网络请求。

界面打开官方商店由外部商店/浏览器执行，仅传组件页面 URL，不传健康数据。

## 不可用提示

运行时区分 `SDK_AVAILABLE`、`SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED` 和 `SDK_UNAVAILABLE`。前者才创建客户端、申请权限。更新态引导官方组件，未知/无入口状态提供系统设置及提示。不能以 Android 16 为条件强行假定国内 ColorOS 上存在可用 Health Connect。
