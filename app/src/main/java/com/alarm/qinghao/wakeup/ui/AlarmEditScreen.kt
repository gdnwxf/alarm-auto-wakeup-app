package com.alarm.qinghao.wakeup.ui

import android.app.Activity
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.IntentCompat
import com.alarm.qinghao.wakeup.data.AlarmEntity
import com.alarm.qinghao.wakeup.data.AlarmRepository
import com.alarm.qinghao.wakeup.data.RepeatDays
import com.alarm.qinghao.wakeup.data.VibrateMode
import com.alarm.qinghao.wakeup.util.AlarmSounds
import com.alarm.qinghao.wakeup.util.InstalledApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/** 编辑页入口：负责按 id 加载数据。alarmId 为 null/0 表示新建。 */
@Composable
fun AlarmEditRoute(
    alarmId: Long?,
    repository: AlarmRepository,
    onSave: (AlarmEntity) -> Unit,
    onDelete: (Long) -> Unit,
    onBack: () -> Unit
) {
    var draft by remember { mutableStateOf<AlarmEntity?>(null) }

    LaunchedEffect(alarmId) {
        if (alarmId == null || alarmId == 0L) {
            // 新建时默认取系统当前时间
            val now = Calendar.getInstance()
            draft = AlarmEntity(
                hour = now.get(Calendar.HOUR_OF_DAY),
                minute = now.get(Calendar.MINUTE)
            )
        } else {
            val loaded = repository.get(alarmId)
            if (loaded == null) {
                onBack()
            } else {
                draft = loaded
            }
        }
    }

    val current = draft
    if (current == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        AlarmEditContent(
            draft = current,
            onChange = { draft = it },
            onSave = onSave,
            onDelete = { onDelete(current.id) },
            onBack = onBack
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AlarmEditContent(
    draft: AlarmEntity,
    onChange: (AlarmEntity) -> Unit,
    onSave: (AlarmEntity) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showTimePicker by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val soundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val picked: Uri? = result.data?.let {
                IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            }
            val (soundUri, soundName) = AlarmSounds.parseResult(context, picked)
            onChange(draft.copy(soundUri = soundUri, soundName = soundName))
        }
    }

    if (showAppPicker) {
        AppPickerScreen(
            selected = draft.wakeAppPackages,
            onConfirm = { packages ->
                onChange(draft.copy(wakeAppPackages = packages))
                showAppPicker = false
            },
            onBack = { showAppPicker = false }
        )
        return
    }

    val selectedLabels by produceState(initialValue = emptyList<String>(), draft.wakeAppPackages) {
        value = withContext(Dispatchers.IO) {
            draft.wakeAppPackages.map { InstalledApps.labelOf(context, it) }
        }
    }

    val vibrateIndex = VibrateMode.fromValue(draft.vibrateMode)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (draft.id == 0L) "新建闹钟" else "编辑闹钟") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (draft.id != 0L) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "删除")
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Button(
                    onClick = { onSave(draft) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(52.dp)
                ) {
                    Text("保存", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 时间
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(24.dp)
                    )
                    .clickable { showTimePicker = true }
                    .padding(vertical = 26.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = String.format("%02d:%02d", draft.hour, draft.minute),
                        fontSize = 62.sp,
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "${RepeatDays.describe(draft.repeatMask)} · 点击修改时间",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                    )
                }
            }

            // 重复
            SectionCard(title = "重复") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val selectedDays = RepeatDays.indicesOf(draft.repeatMask)
                    (0..6).forEach { index ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val days = RepeatDays.indicesOf(draft.repeatMask).toMutableSet()
                                    if (days.contains(index)) days.remove(index) else days.add(index)
                                    onChange(draft.copy(repeatMask = RepeatDays.masksOf(days)))
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedDays.contains(index),
                                onCheckedChange = null
                            )
                            Text(
                                text = "周${RepeatDays.shortName(index)}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onChange(draft.copy(repeatMask = RepeatDays.ALL_MASK)) }) {
                        Text("每天")
                    }
                    TextButton(onClick = { onChange(draft.copy(repeatMask = RepeatDays.WORKDAY_MASK)) }) {
                        Text("工作日")
                    }
                    TextButton(onClick = { onChange(draft.copy(repeatMask = 0)) }) {
                        Text("仅一次")
                    }
                }
            }

            // 标签
            SectionCard(title = "标签") {
                OutlinedTextField(
                    value = draft.label,
                    onValueChange = { onChange(draft.copy(label = it.take(30))) },
                    placeholder = { Text("例如：起床、吃药、跑步") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 铃声
            SectionCard(title = "铃声") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val pickerIntent = AlarmSounds.pickerIntent(draft.soundUri)
                            runCatching { soundPicker.launch(pickerIntent) }
                                .onFailure { error ->
                                    // 部分精简 ROM 没有系统铃声选择器，避免直接崩溃
                                    Log.w(TAG, "无法打开系统铃声选择器", error)
                                    Toast.makeText(
                                        context,
                                        "当前设备不支持系统铃声选择器",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    ) {
                        Text(
                            text = AlarmSounds.describe(context, draft.soundUri),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "点击选择系统铃声或静音",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { AlarmSounds.preview(context, draft.soundUri) }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "试听")
                    }
                }
                Text(
                    text = "音量渐强",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OptionChips(
                    options = listOf(
                        0 to "关闭",
                        5 to "5 秒",
                        10 to "10 秒",
                        20 to "20 秒",
                        30 to "30 秒"
                    ),
                    selected = draft.fadeInSeconds,
                    onSelect = { onChange(draft.copy(fadeInSeconds = it)) }
                )
            }

            // 震动
            SectionCard(title = "震动") {
                OptionChips(
                    options = VibrateMode.entries.map { it.value to it.label },
                    selected = vibrateIndex.value,
                    onSelect = { onChange(draft.copy(vibrateMode = it)) }
                )
                Text(
                    text = "静音时仍会震动，适合不想吵到别人的场景",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 贪睡
            SectionCard(title = "贪睡") {
                Text("贪睡间隔", style = MaterialTheme.typography.bodyMedium)
                OptionChips(
                    options = listOf(5 to "5 分钟", 10 to "10 分钟", 15 to "15 分钟", 20 to "20 分钟"),
                    selected = draft.snoozeMinutes,
                    onSelect = { onChange(draft.copy(snoozeMinutes = it)) }
                )
                Text("最多贪睡次数", style = MaterialTheme.typography.bodyMedium)
                OptionChips(
                    options = listOf(0 to "不贪睡", 1 to "1 次", 2 to "2 次", 3 to "3 次", 5 to "5 次"),
                    selected = draft.snoozeMaxTimes,
                    onSelect = { onChange(draft.copy(snoozeMaxTimes = it)) }
                )
            }

            // 响铃时长
            SectionCard(title = "响铃时长") {
                OptionChips(
                    options = listOf(5 to "5 分钟", 10 to "10 分钟", 15 to "15 分钟", 30 to "30 分钟"),
                    selected = draft.ringTimeoutMinutes,
                    onSelect = { onChange(draft.copy(ringTimeoutMinutes = it)) }
                )
                Text(
                    text = "无人操作时到时会自动停止响铃",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 唤醒应用
            SectionCard(title = "唤醒应用（重点）") {
                Text(
                    text = "闹钟响起时自动打开所选应用，可选择一个或多个。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (selectedLabels.isEmpty()) {
                    Text(
                        text = "尚未选择应用",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = buildSelectedSummary(selectedLabels),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Apps,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    TextButton(onClick = { showAppPicker = true }) {
                        Text(if (draft.wakeAppPackages.isEmpty()) "选择应用" else "修改选择（${draft.wakeAppPackages.size}）")
                    }
                }
                Text("启动间隔", style = MaterialTheme.typography.bodyMedium)
                OptionChips(
                    options = listOf(0 to "立即", 2 to "2 秒", 3 to "3 秒", 5 to "5 秒", 10 to "10 秒"),
                    selected = draft.wakeAppDelaySeconds,
                    onSelect = { onChange(draft.copy(wakeAppDelaySeconds = it)) }
                )
                SwitchRow(
                    title = "同时启动全部应用",
                    subtitle = "开启后忽略启动间隔，连续拉起所有应用",
                    checked = draft.wakeAppParallel,
                    onCheckedChange = { onChange(draft.copy(wakeAppParallel = it)) }
                )
                Text(
                    text = "提示：应用被拉起后可返回本应用关闭铃声，也可在通知栏点击「停止」。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (showTimePicker) {
        AlarmTimePickerDialog(
            initialHour = draft.hour,
            initialMinute = draft.minute,
            onConfirm = { hour, minute ->
                onChange(draft.copy(hour = hour, minute = minute))
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除闹钟") },
            text = { Text("确定删除这个闹钟吗？删除后不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun AlarmTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var hour by remember { mutableIntStateOf(initialHour) }
    var minute by remember { mutableIntStateOf(initialMinute) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "设置时间",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                WheelTimePicker(
                    hour = hour,
                    minute = minute,
                    onTimeChange = { h, m ->
                        hour = h
                        minute = m
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(onClick = { onConfirm(hour, minute) }) { Text("确定") }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionChips(
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            content()
        }
    }
}

private fun buildSelectedSummary(labels: List<String>): String {
    if (labels.isEmpty()) return ""
    val head = labels.take(3).joinToString("、")
    return if (labels.size <= 3) head else "$head 等 ${labels.size} 个应用"
}

private const val TAG = "AlarmEditScreen"
