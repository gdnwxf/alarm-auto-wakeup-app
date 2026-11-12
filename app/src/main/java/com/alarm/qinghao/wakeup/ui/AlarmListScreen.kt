package com.alarm.qinghao.wakeup.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alarm.qinghao.wakeup.alarm.RingingStateHolder
import com.alarm.qinghao.wakeup.data.AlarmEntity
import com.alarm.qinghao.wakeup.data.RepeatDays
import com.alarm.qinghao.wakeup.util.InstalledApps
import com.alarm.qinghao.wakeup.util.WakeApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AlarmListScreen(
    alarms: List<AlarmEntity>,
    ringing: RingingStateHolder.Ringing?,
    onStopRinging: () -> Unit,
    onSnoozeRinging: () -> Unit,
    exactAlarmOk: Boolean,
    notificationsOk: Boolean,
    fullScreenOk: Boolean,
    batteryOptimizationsOk: Boolean,
    /** 返回列表时要定位到的闹钟 id，null 表示不定位 */
    focusAlarmId: Long?,
    onFocusConsumed: () -> Unit,
    onDeleteAlarms: (List<Long>) -> Unit,
    onToggle: (AlarmEntity, Boolean) -> Unit,
    onEdit: (AlarmEntity) -> Unit,
    onAdd: () -> Unit,
    onFixExactAlarm: () -> Unit,
    onFixNotifications: () -> Unit,
    onFixFullScreen: () -> Unit,
    onFixBatteryOptimizations: () -> Unit
) {
    // 列表倒计时共用一个 30 秒心跳，避免每个卡片各起一个协程
    var refreshTick by remember { mutableLongStateOf(0L) }

    // 多选删除状态
    var selectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<Long>() }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    fun exitSelectionMode() {
        selectionMode = false
        selectedIds.clear()
    }

    // 多选模式下返回键退出多选，而不是退出应用
    BackHandler(enabled = selectionMode) { exitSelectionMode() }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000L)
            refreshTick++
        }
    }

    // 从编辑页返回时定位到刚编辑/新建的闹钟
    val listState = rememberLazyListState()
    LaunchedEffect(focusAlarmId, alarms) {
        val target = focusAlarmId ?: return@LaunchedEffect
        val index = alarms.indexOfFirst { it.id == target }
        if (index >= 0) {
            listState.scrollToItem(index)
            onFocusConsumed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (selectionMode) "已选 ${selectedIds.size} 项" else "闹钟",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    if (selectionMode) {
                        IconButton(onClick = { exitSelectionMode() }) {
                            Icon(Icons.Filled.Close, contentDescription = "退出多选")
                        }
                    }
                },
                actions = {
                    if (selectionMode) {
                        val allSelected = alarms.isNotEmpty() && selectedIds.size == alarms.size
                        TextButton(onClick = {
                            if (allSelected) {
                                selectedIds.clear()
                            } else {
                                selectedIds.clear()
                                selectedIds.addAll(alarms.map { it.id })
                            }
                        }) {
                            Text(if (allSelected) "取消全选" else "全选")
                        }
                        IconButton(
                            onClick = {
                                if (selectedIds.isNotEmpty()) showBatchDeleteConfirm = true
                            }
                        ) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "删除所选")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!selectionMode) {
                FloatingActionButton(
                    onClick = onAdd,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "新建闹钟",
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            val ringingNow = ringing
            if (ringingNow != null) {
                RingingBanner(
                    alarm = ringingNow.alarm,
                    onStop = onStopRinging,
                    onSnooze = onSnoozeRinging
                )
            }
            if (!exactAlarmOk) {
                PermissionCard(
                    title = "需要精确闹钟权限",
                    message = "未授权时闹钟可能延迟几分钟响铃。",
                    actionText = "去授权",
                    onAction = onFixExactAlarm
                )
            }
            if (!notificationsOk) {
                PermissionCard(
                    title = "通知权限已关闭",
                    message = "闹钟响铃页与停止按钮依赖通知权限。",
                    actionText = "去开启",
                    onAction = onFixNotifications
                )
            }
            if (!fullScreenOk) {
                PermissionCard(
                    title = "缺少全屏通知权限",
                    message = "锁屏时可能无法自动弹出响铃页。",
                    actionText = "去开启",
                    onAction = onFixFullScreen
                )
            }
            if (!batteryOptimizationsOk) {
                PermissionCard(
                    title = "建议忽略电池优化",
                    message = "避免待机时被系统限制响铃与唤醒应用。",
                    actionText = "去设置",
                    onAction = onFixBatteryOptimizations
                )
            }

            // 表盘 + 距下次响铃倒计时（参考主流闹钟布局）
            ClockHeader(alarms = alarms, tick = refreshTick)

            if (alarms.isEmpty()) {
                EmptyState(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items = alarms, key = { it.id }) { alarm ->
                        AlarmCard(
                            alarm = alarm,
                            tick = refreshTick,
                            selectionMode = selectionMode,
                            selected = selectedIds.contains(alarm.id),
                            onToggle = { enabled -> onToggle(alarm, enabled) },
                            onClick = {
                                if (selectionMode) {
                                    if (selectedIds.contains(alarm.id)) {
                                        selectedIds.remove(alarm.id)
                                    } else {
                                        selectedIds.add(alarm.id)
                                    }
                                } else {
                                    onEdit(alarm)
                                }
                            },
                            onLongPress = {
                                if (!selectionMode) {
                                    selectionMode = true
                                    selectedIds.clear()
                                    selectedIds.add(alarm.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showBatchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text("删除闹钟") },
            text = { Text("确定删除选中的 ${selectedIds.size} 个闹钟吗？删除后不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    val ids = selectedIds.toList()
                    showBatchDeleteConfirm = false
                    exitSelectionMode()
                    onDeleteAlarms(ids)
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun AlarmCard(
    alarm: AlarmEntity,
    tick: Long,
    selectionMode: Boolean,
    selected: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val timeText = String.format("%02d:%02d", alarm.hour, alarm.minute)
    val repeatText = RepeatDays.describe(alarm.repeatMask)
    val context = LocalContext.current

    // 直接展开唤醒应用的名称（最多 5 个），而不是只显示数量
    val wakeAppLabels by produceState(
        initialValue = null as List<String>?,
        alarm.wakeAppPackages
    ) {
        value = withContext(Dispatchers.IO) {
            alarm.wakeAppPackages
                .take(WakeApps.MAX_TARGETS)
                .map { InstalledApps.labelOf(context, it) }
        }
    }

    val nextTriggerText = remember(alarm, tick) {
        if (!alarm.enabled) {
            null
        } else {
            RepeatDays.nextTriggerAt(alarm.hour, alarm.minute, alarm.repeatMask)
                ?.let { "下次 ${RepeatDays.describeTrigger(it)}" }
        }
    }
    val subtitle = buildList {
        add(repeatText)
        if (alarm.label.isNotBlank()) add(alarm.label)
        if (nextTriggerText != null) add(nextTriggerText)
    }.joinToString(" · ")

    val cardColor = when {
        selectionMode && selected -> MaterialTheme.colorScheme.primaryContainer
        isSystemInDarkTheme() -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        else -> Color.White
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = null)
                Spacer(Modifier.size(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = timeText,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (alarm.enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    }
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (alarm.wakeAppPackages.isNotEmpty()) {
                    val labels = wakeAppLabels
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Apps,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            // 始终占一行，避免标签加载完成前后卡片高度跳动
                            text = when {
                                labels == null -> "唤醒 加载中…"
                                labels.isEmpty() -> "唤醒 未选择应用"
                                else -> "唤醒 ${labels.joinToString("、")}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (!selectionMode) {
                Switch(checked = alarm.enabled, onCheckedChange = onToggle)
            }
        }
    }
}

@Composable
private fun ClockHeader(alarms: List<AlarmEntity>, tick: Long) {
    val countdown = remember(alarms, tick) {
        val now = System.currentTimeMillis()
        val next = alarms.filter { it.enabled }
            .mapNotNull { RepeatDays.nextTriggerAt(it.hour, it.minute, it.repeatMask, now) }
            .minOrNull()
        describeCountdown(next)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnalogClock(modifier = Modifier.size(190.dp))
        Spacer(Modifier.height(10.dp))
        Text(
            text = countdown,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
    }
}

private fun describeCountdown(triggerAt: Long?): String {
    if (triggerAt == null) return "暂无启用的闹钟"
    val remain = triggerAt - System.currentTimeMillis()
    if (remain <= 60_000L) return "即将响铃"

    val totalMinutes = remain / 60_000L
    if (totalMinutes < 1) return "即将响铃"
    if (totalMinutes < 60) return "$totalMinutes 分钟后响铃"

    val days = totalMinutes / (24 * 60)
    val hours = (totalMinutes % (24 * 60)) / 60
    val minutes = totalMinutes % 60

    if (days < 1) {
        return if (minutes > 0) "$hours 小时 $minutes 分钟后响铃" else "$hours 小时后响铃"
    }

    val parts = buildList {
        add("$days 天")
        if (hours > 0) add("$hours 小时")
        if (minutes > 0) add("$minutes 分钟")
    }
    return parts.joinToString(" ") + "后响铃"
}

@Composable
private fun RingingBanner(
    alarm: AlarmEntity,
    onStop: () -> Unit,
    onSnooze: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Alarm,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = "闹钟响铃中",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = String.format("%02d:%02d", alarm.hour, alarm.minute) +
                        if (alarm.label.isBlank()) "" else "  ${alarm.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
            if (alarm.snoozeMaxTimes > 0) {
                TextButton(onClick = onSnooze) { Text("贪睡") }
            }
            Button(onClick = onStop) { Text("关闭") }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    message: String,
    actionText: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
            TextButton(onClick = onAction) { Text(actionText) }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Icon(
                    imageVector = Icons.Filled.Alarm,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(20.dp)
                        .size(40.dp)
                )
            }
            Text(
                text = "还没有闹钟",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = "点击右下角新建，可设置重复、铃声、震动，\n并在响铃时自动打开你选择的应用。",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, start = 32.dp, end = 32.dp)
            )
        }
    }
}
