# 第二阶段：DeepSeek 和 FIT 接口设计

本文件和 `domain/Contracts.kt` 是接口设计。V0.1 不实例化 AI/FIT 实现，不含 API key、HTTP 客户端或网络权限。

## 用户主动触发的 AI 流程

1. 用户主动选择日期范围与允许分享的指标。
2. `CoachAiRepository.prepareSummary(AiSummaryRequest)` 只从当前授权的本地日聚合生成 `AiSummaryDraft`。请求包含起止时间、选择的指标和时区。
3. 展示确切 JSON、缺失日、统计口径、将发送的字段和目标服务；用户可以取消。
4. 仅在明确允许本次上传后生成 `UploadConsent(draftId, payloadSha256, providerId, grantedAt, expiresAt)`，绑定数据内容和目标服务。
5. `analyzeApproved()` 验证草稿 ID、SHA-256、授权时限、健康数据当前权限和范围；内容变化必须重新预览。每个草稿授权只消费一次。
6. 完成后显示观察和局限，可删除本地 AI 结果。失败、取消和断网不会自动发送或后台重试。

`rangeStart` 包含、`rangeEnd` 不包含，时间为 UTC Instant；草稿内明确本地统计时区和部分日。请求的指标必须是当前授权指标的子集，空指标集拒绝生成草稿。

## 最小 payload（结构示例，不是应用中的健康数据）

```json
{
  "schema_version": 1,
  "timezone": "Asia/Shanghai",
  "range_start": "<ISO-8601>",
  "range_end": "<ISO-8601>",
  "aggregation": {
    "steps": "health_connect_count_total",
    "heart_rate": "health_connect_bpm_avg",
    "distance": "health_connect_distance_total",
    "sleep": "health_connect_sleep_duration_total_local_midnight"
  },
  "days": [
    {
      "date": "<YYYY-MM-DD>",
      "partial_day": false,
      "steps": null,
      "heart_rate_bpm_avg": null,
      "distance_meters": null,
      "sleep_seconds": null,
      "missing_metrics": ["steps", "heart_rate", "distance", "sleep"]
    }
  ]
}
```

默认不上传 HC record ID、来源包名、设备标识、心率原始样本、精确事件时间、睡眠 notes、FIT GPS 或个人身份。只上传明确选择的汇总字段；`null` 保留缺失，不能给 AI 填写假零。提示词要求按数据观察、注明缺失与统计边界、避免医学诊断和强制训练建议。

## DeepSeek 适配器

新增独立 `DeepSeekCoachAiRepository` 与 `DeepSeekTransport`，通过协程 suspend 调用。

```kotlin
interface DeepSeekTransport {
    suspend fun explain(request: ExplainRequest): ExplainResponse
}
data class ExplainRequest(val summaryJson: String, val requestId: String)
data class ExplainResponse(
    val observations: List<String>,
    val limitations: List<String>,
    val model: String,
)
```

接入时重新核查 [DeepSeek 官方 API 文档](https://api-docs.deepseek.com/)，使用官方 OpenAI 兼容 Chat Completions HTTPS 接口，模型名可配置，不在 V0.1 中固定生产模型或伪造连接成功。实现超时、协程取消、401/403/429/5xx、请求大小和 schema 验证；限流与服务失败均在前台显示，由用户决定重试。

凭证不能写进源码/APK/日志。为开源个人使用实现用户自带 key（Android Keystore 包装加密、本机保存、可删除），或另行设计用户明确选择的后端；后端意味着额外的数据处理方与隐私说明。V0.1 不决定或启用该部署。

安全边界由代码执行：固定 schema、授权 token、摘要 hash、删除/撤销后禁止读取、输出格式验证。不能只依赖提示词让模型遵守权限。AI 文本作为非可信输出显示，不能自动执行训练计划或修改设备数据。

## FIT 导入

`FitImportRepository.preview(contentUri)` 读取用户通过系统文件选择器主动选择的文件；校验 FIT 结构、CRC、尺寸和支持的记录类型，返回预览 ID、文件 hash、条数与警告。`importApproved(previewId)` 重新核对 hash 后按 `fit:<sha256>:<message-index>` 去重，在 Room 事务中写入带独立来源的实体。

FIT 训练记录与 Health Connect 日累计是不同数据口径，不能直接相加。先实现独立训练会话视图和明确的来源选择策略，再考虑跨来源关联。不请求 Garmin 密码，不自动获取 Garmin 云端文件。

## 第二阶段验收

- 未授权、草稿不匹配、数据已撤销或用户取消时，网络请求次数为零。
- payload 仅含选择的聚合指标，缺失字段与部分日明确。
- key 不进入 Git、APK 常量、日志或崩溃报告。
- 断网、限流、错误 JSON、超时和取消可恢复，无自动上传重试。
- 无医学诊断、无自动执行、无强制训练建议。
