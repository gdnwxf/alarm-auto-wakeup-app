package com.alarm.qinghao.wakeup.ui

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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alarm.qinghao.wakeup.alarm.RingingStateHolder
import com.alarm.qinghao.wakeup.data.AlarmEntity
import com.alarm.qinghao.wakeup.data.RepeatDays

@OptIn(ExperimentalMaterial3Api::class)
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
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000L)
            refreshTick++
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("闹钟", fontWeight = FontWeight.SemiBold) })
        },
        floatingActionButton = {
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
                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items = alarms, key = { it.id }) { alarm ->
                        AlarmCard(
                            alarm = alarm,
                            tick = refreshTick,
                            onToggle = { enabled -> onToggle(alarm, enabled) },
                            onClick = { onEdit(alarm) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmCard(
    alarm: AlarmEntity,
    tick: Long,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val timeText = String.format("%02d:%02d", alarm.hour, alarm.minute)
    val repeatText = RepeatDays.describe(alarm.repeatMask)

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

    val cardColor = if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    } else {
        Color.White
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                            text = "唤醒 ${alarm.wakeAppPackages.size} 个应用",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Switch(checked = alarm.enabled, onCheckedChange = onToggle)
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
    val minutes = remain / 60_000L
    return if (minutes < 60) {
        "$minutes 分钟后响铃"
    } else {
        "${minutes / 60} 小时 ${minutes % 60} 分钟后响铃"
    }
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
