package io.github.garminaicoach.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.garminaicoach.domain.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Stamp = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault())
private fun stamp(value: Instant?) = value?.let(Stamp::format) ?: "尚未同步"

fun displayValue(metric: Metric, value: Double?): String {
    if (value == null) return "—"
    return when (metric) {
        Metric.STEPS -> String.format(Locale.ROOT, "%.0f", value)
        Metric.HEART_RATE -> String.format(Locale.ROOT, "%.0f", value)
        Metric.DISTANCE -> String.format(Locale.ROOT, "%.2f", value / 1000)
        Metric.SLEEP -> String.format(Locale.ROOT, "%.1f", value / 3600)
    }
}

@Composable
fun CoachScreen(
    state: CoachUiState,
    onRefresh: () -> Unit,
    onPermissions: (Set<Metric>) -> Unit,
    onSettings: () -> Unit,
    onInstall: () -> Unit,
    onPrivacy: () -> Unit,
    onClear: () -> Unit,
) {
    var permissionsPage by rememberSaveable { mutableStateOf(false) }
    var clearDialog by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = permissionsPage) { permissionsPage = false }
    Surface(Modifier.fillMaxSize()) {
        if (permissionsPage) {
            PermissionScreen(state, { permissionsPage = false }, onPermissions, onSettings, onPrivacy)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("dashboard"),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Text("GARMIN AI COACH", style = MaterialTheme.typography.labelLarge, letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified)
                    Text("你的数据，留在本机", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    Text("V0.1 · Health Connect · 近 7 天", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(when (state.availability) {
                                Availability.AVAILABLE -> "Health Connect 可用"
                                Availability.UPDATE_REQUIRED -> "需要安装或更新 Health Connect"
                                Availability.UNAVAILABLE -> "此设备无法使用 Health Connect"
                                null -> "正在检查 Health Connect"
                            }, fontWeight = FontWeight.SemiBold)
                            Text(when (state.availability) {
                                Availability.AVAILABLE -> "已授权 ${state.granted.size}/4 类只读数据。数据是否存在取决于来源应用是否写入。"
                                Availability.UPDATE_REQUIRED -> "请先安装或更新官方组件，再返回本应用刷新。"
                                Availability.UNAVAILABLE -> "请检查系统组件和 Google Play 服务；部分 ColorOS 地区版本或工作资料不支持。"
                                null -> "检查后才能申请权限和读取数据。"
                            }, style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = onRefresh, enabled = !state.busy, modifier = Modifier.testTag("refresh")) { Text(if (state.busy) "检查 / 同步中…" else "手动刷新") }
                                if (state.availability == Availability.AVAILABLE) {
                                    OutlinedButton(onClick = { permissionsPage = true }, enabled = !state.busy) { Text("数据权限") }
                                }
                            }
                            when (state.availability) {
                                Availability.UPDATE_REQUIRED -> TextButton(onClick = onInstall) { Text("安装 / 更新官方组件") }
                                Availability.UNAVAILABLE -> TextButton(onClick = onSettings) { Text("打开系统设置") }
                                Availability.AVAILABLE -> TextButton(onClick = onSettings) { Text("Health Connect 设置") }
                                null -> Unit
                            }
                            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                }
                if (state.message != null) item { Text(state.message, modifier = Modifier.testTag("message"), style = MaterialTheme.typography.bodyMedium) }
                items(Metric.entries, key = { it.name }) { metric -> MetricCard(metric, state) }
                item {
                    Text("数据来源与原始记录", style = MaterialTheme.typography.titleLarge)
                    Text("以下是最近 12 条原始记录。来源包名由 Health Connect 返回；来源时间是记录的最后修改时间。", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
                val records = state.snapshot.records.filter { state.accessVerified && it.metric in state.granted }.take(12)
                if (records.isEmpty()) item { Text("暂无可展示的记录。请授权并刷新，或检查来源应用的 Health Connect 同步设置。", style = MaterialTheme.typography.bodyMedium) }
                items(records, key = { it.key }) { record ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${record.metric.label} · ${displayValue(record.metric, record.value)} ${record.metric.unit}", fontWeight = FontWeight.SemiBold)
                            Text(record.origin, style = MaterialTheme.typography.bodySmall)
                            Text("记录：${stamp(record.start)} → ${stamp(record.end)}", style = MaterialTheme.typography.bodySmall)
                            Text("来源更新：${stamp(record.modified)}", style = MaterialTheme.typography.bodySmall)
                            Text("ID：${record.recordId}", style = MaterialTheme.typography.bodySmall)
                            if (record.metric == Metric.SLEEP) Text("此处为原始睡眠会话区间长度；趋势以官方聚合结果为准", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item {
                    Text("Garmin 数据只有被来源应用写入 Health Connect 后才能读取。此应用不登录 Garmin Connect，不要求 Garmin 密码。", style = MaterialTheme.typography.bodySmall)
                    Text("AI 尚未启用 · 不上传健康数据 · 不提供医学诊断", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onPrivacy) { Text("隐私与权限说明") }
                        TextButton(onClick = { clearDialog = true }, enabled = !state.busy) { Text("清除本地数据") }
                    }
                }
            }
        }
    }
    if (clearDialog) AlertDialog(
        onDismissRequest = { clearDialog = false },
        title = { Text("清除本地健康数据？") },
        text = { Text("删除本应用缓存、趋势和同步状态。Health Connect 原始记录保留，之后可手动刷新重新读取。") },
        confirmButton = { TextButton(onClick = { clearDialog = false; onClear() }) { Text("清除") } },
        dismissButton = { TextButton(onClick = { clearDialog = false }) { Text("取消") } },
    )
}

@Composable private fun MetricCard(metric: Metric, state: CoachUiState) {
    val authorized = state.accessVerified && metric in state.granted
    val status = state.snapshot.statuses.firstOrNull { it.metric == metric }
    val today = LocalDate.now()
    val dates = (6L downTo 0L).map { today.minusDays(it) }
    val days = if (authorized) state.snapshot.days.filter { it.metric == metric && it.zone == ZoneId.systemDefault().id }.associateBy { it.date } else emptyMap()
    val values = dates.map { days[it]?.value }
    val current = days[today]?.value
    val hasCache = days.isNotEmpty()
    OutlinedCard(Modifier.fillMaxWidth().testTag("metric-${metric.name}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (metric == Metric.HEART_RATE) "心率 · 日均" else if (metric == Metric.SLEEP) "睡眠 · 日内累计" else metric.label, style = MaterialTheme.typography.titleMedium)
                Text(when {
                    !state.accessVerified -> "等待权限检查"
                    !authorized -> "未授权 / 不可用"
                    status?.phase == SyncPhase.SYNCING -> "同步中"
                    status?.phase == SyncPhase.ERROR -> "读取失败 · 缓存"
                    status?.phase == SyncPhase.PERMISSION_REQUIRED -> "权限被系统拒绝"
                    status?.phase == SyncPhase.SUCCESS && current == null -> "今日无数据"
                    status?.phase == SyncPhase.SUCCESS -> "已同步"
                    else -> "待刷新"
                }, style = MaterialTheme.typography.labelSmall)
            }
            Text("${displayValue(metric, current)} ${metric.unit}", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("value-${metric.name}"))
            if (authorized && hasCache) {
                Trend(values)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    dates.forEach { Text(it.format(DateTimeFormatter.ofPattern("MM/dd")), style = MaterialTheme.typography.labelSmall) }
                }
                // Exact daily values stay accessible alongside the chart; missing points are explicit.
                Text(dates.zip(values).joinToString(" · ") { (date, value) -> "${date.dayOfMonth}日 ${if (value == null) "缺失" else displayValue(metric, value)}" }, style = MaterialTheme.typography.bodySmall)
                val known = values.filterNotNull()
                Text("有数据 ${known.size}/7 天 · 有数据日均 ${displayValue(metric, known.takeIf { it.isNotEmpty() }?.average())} ${metric.unit}", style = MaterialTheme.typography.bodySmall)
                val origins = days.values.flatMap { it.origins }.toSortedSet()
                Text("聚合来源：${origins.joinToString().ifBlank { "未提供" }}", style = MaterialTheme.typography.bodySmall)
            } else {
                Text(if (authorized) "尚无本地数据，请手动刷新。" else "授权后读取；缺失数据不会用零或示例数据替代。", style = MaterialTheme.typography.bodySmall)
            }
            Text("本机最后成功同步：${stamp(status?.succeededAt)}", style = MaterialTheme.typography.bodySmall)
            if (status?.message != null) Text(status.message, style = MaterialTheme.typography.bodySmall)
            if (hasCache && (status?.phase != SyncPhase.SUCCESS || status.succeededAt?.atZone(ZoneId.systemDefault())?.toLocalDate() != today)) {
                Text("当前展示本地缓存，请刷新以确认最新数据。", style = MaterialTheme.typography.bodySmall)
            }
            if (metric == Metric.SLEEP) Text("按本机时区的午夜边界聚合，跨夜睡眠会分到两天。", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun Trend(values: List<Double?>) {
    val color = MaterialTheme.colorScheme.onSurface
    val axis = MaterialTheme.colorScheme.outline
    val description = values.joinToString { it?.toString() ?: "缺失" }
    Canvas(Modifier.fillMaxWidth().height(70.dp).semantics { contentDescription = "近七天趋势：$description；空心点代表缺失" }) {
        val max = (values.filterNotNull().maxOrNull() ?: 1.0).coerceAtLeast(1.0)
        val bottom = size.height - 8.dp.toPx()
        val top = 8.dp.toPx()
        val left = 5.dp.toPx()
        val step = (size.width - 2 * left) / 6
        drawLine(axis, Offset(left, bottom), Offset(size.width - left, bottom), 1.dp.toPx())
        values.forEachIndexed { index, value ->
            val x = left + index * step
            if (value == null) {
                drawCircle(axis, 3.dp.toPx(), Offset(x, bottom), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            } else {
                val y = bottom - ((value / max) * (bottom - top)).toFloat()
                drawCircle(color, 3.dp.toPx(), Offset(x, y))
                val previous = values.getOrNull(index - 1)
                if (previous != null) drawLine(color, Offset(x - step, bottom - ((previous / max) * (bottom - top)).toFloat()), Offset(x, y), 2.dp.toPx())
            }
        }
    }
}

@Composable private fun PermissionScreen(state: CoachUiState, onBack: () -> Unit, onRequest: (Set<Metric>) -> Unit, onSettings: () -> Unit, onPrivacy: () -> Unit) {
    var selectedNames by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val requested = selectedNames.map(Metric::valueOf).toSet() - state.granted
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.testTag("permissions")) {
        item { TextButton(onClick = onBack) { Text("返回数据概览") } }
        item {
            Text("选择要读取的数据", style = MaterialTheme.typography.headlineMedium)
            Text("全部为只读权限。默认不勾选，可只选一种。我们仅在本机保存和统计，不申请写入、后台读取或历史数据权限。", modifier = Modifier.padding(top = 12.dp))
        }
        items(Metric.entries, key = { it.name }) { metric ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = metric in state.granted || metric.name in selectedNames,
                        enabled = metric !in state.granted && !state.busy,
                        onCheckedChange = { checked -> selectedNames = if (checked) selectedNames + metric.name else selectedNames - metric.name },
                        modifier = Modifier.testTag("select-${metric.name}"),
                    )
                    Column {
                        Text(metric.label, fontWeight = FontWeight.SemiBold)
                        Text(if (metric in state.granted) "已授权；可在系统设置撤销" else when (metric) {
                            Metric.STEPS -> "用于步数展示与近 7 天趋势"
                            Metric.HEART_RATE -> "用于心率记录与每日平均值"
                            Metric.DISTANCE -> "用于距离展示与近 7 天趋势"
                            Metric.SLEEP -> "用于睡眠会话与日内时长统计"
                        }, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            Button(onClick = { onRequest(requested) }, enabled = requested.isNotEmpty() && !state.busy && state.availability == Availability.AVAILABLE, modifier = Modifier.fillMaxWidth().testTag("request-permissions")) { Text("申请所选 ${requested.size} 类权限") }
            TextButton(onClick = onSettings) { Text("管理或撤销系统权限") }
            TextButton(onClick = onPrivacy) { Text("查看完整隐私与权限说明") }
            state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable fun PrivacyScreen(onClose: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text("隐私与健康数据权限", style = MaterialTheme.typography.headlineMedium) }
            item { Text("Garmin AI Coach V0.1 是独立开源项目，与 Garmin 无隶属关系。") }
            item { Text("按需读取步数、心率、距离和睡眠。只读取最近 7 个本地日历日，用于记录展示、来源追踪和趋势统计。系统授权随时可拒绝或撤销。") }
            item { Text("健康记录保存在本应用私有 Room / SQLite 数据库。应用没有网络权限，不包含 AI、分析或广告服务，不会自动上传数据，也不登录 Garmin Connect。Android 云备份和设备迁移已排除此数据。") }
            item { Text("返回应用时及读取前会检查权限。确认撤销某类型权限后，清除该类型本地缓存；无法确认权限时暂时隐藏数据。撤销后应用需再次进入前台才能执行清除。也可通过“清除本地数据”删除全部缓存，或卸载应用。") }
            item { Text("缺失数据显示“— / 缺失”；读取失败保留已授权的旧缓存并标记。步数、距离和睡眠使用官方聚合结果，心率为官方日均值。睡眠按本机午夜分日，可能与来源应用按起床日统计的结果不同。") }
            item { Text("Garmin 数据并不保证全部进入 Health Connect；请检查来源应用的官方同步选项。本应用不作医学诊断，也不提供强制训练建议。第二阶段 AI 必须另行审核、预览上传内容并明确授权。") }
            item { Button(onClick = onClose) { Text("关闭") } }
        }
    }
}
