package com.alarm.qinghao.wakeup

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alarm.qinghao.wakeup.data.AlarmEntity
import com.alarm.qinghao.wakeup.alarm.AlarmService
import com.alarm.qinghao.wakeup.alarm.RingingStateHolder
import com.alarm.qinghao.wakeup.ui.AlarmEditRoute
import com.alarm.qinghao.wakeup.ui.AlarmListScreen
import com.alarm.qinghao.wakeup.ui.theme.AlarmQinghaoTheme
import com.alarm.qinghao.wakeup.util.Permissions
import com.alarm.qinghao.wakeup.util.WakeApps
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AlarmQinghaoTheme {
                AlarmApp()
            }
        }
    }

    @Composable
    private fun AlarmApp() {
        val app = application as AlarmApplication
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val alarms by app.repository.alarms.collectAsStateWithLifecycle(
            initialValue = emptyList<AlarmEntity>()
        )
        // 主界面也要能看到并关闭正在响的闹钟（唤醒应用会把响铃页顶到后台）
        val ringing by RingingStateHolder.current.collectAsStateWithLifecycle()

        var route by rememberSaveable { mutableStateOf(ROUTE_LIST) }
        var editingId by rememberSaveable { mutableStateOf(0L) }
        var focusAlarmId by rememberSaveable { mutableStateOf(-1L) }

        var exactAlarmOk by remember { mutableStateOf(Permissions.canScheduleExactAlarms(context)) }
        var notificationsOk by remember { mutableStateOf(Permissions.canPostNotifications(context)) }
        var fullScreenOk by remember { mutableStateOf(Permissions.canUseFullScreenIntent(context)) }
        var batteryOptimizationsOk by remember {
            mutableStateOf(Permissions.isIgnoringBatteryOptimizations(context))
        }

        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            exactAlarmOk = Permissions.canScheduleExactAlarms(context)
            notificationsOk = Permissions.canPostNotifications(context)
            fullScreenOk = Permissions.canUseFullScreenIntent(context)
            batteryOptimizationsOk = Permissions.isIgnoringBatteryOptimizations(context)
        }

        val notificationPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted -> notificationsOk = granted }

        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !Permissions.canPostNotifications(context)
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        fun startSystemIntent(intent: Intent?) {
            if (intent == null) return
            runCatching { context.startActivity(intent) }
        }

        when (route) {
            ROUTE_EDIT -> AlarmEditRoute(
                alarmId = editingId,
                repository = app.repository,
                onSave = { draft ->
                    // 超出上限的残留数据在保存时裁剪，保证任何入口都不会批量拉起应用
                    val normalized = draft.copy(
                        wakeAppPackages = draft.wakeAppPackages.take(WakeApps.MAX_TARGETS)
                    )
                    scope.launch {
                        val newId = app.repository.upsert(normalized)
                        val saved = if (normalized.id == 0L) {
                            normalized.copy(id = newId)
                        } else {
                            normalized
                        }
                        app.scheduler.schedule(saved)
                        focusAlarmId = saved.id
                        route = ROUTE_LIST
                    }
                },
                onDelete = { id ->
                    scope.launch {
                        app.scheduler.cancel(id)
                        app.repository.delete(id)
                        focusAlarmId = -1L
                        route = ROUTE_LIST
                    }
                },
                onBack = {
                    focusAlarmId = editingId.takeIf { it > 0L } ?: -1L
                    route = ROUTE_LIST
                }
            )

            else -> AlarmListScreen(
                alarms = alarms,
                ringing = ringing,
                onStopRinging = {
                    AlarmService.sendAction(context, AlarmService.ACTION_STOP)
                    RingingStateHolder.clear()
                },
                onSnoozeRinging = {
                    val current = RingingStateHolder.currentOrNull()
                    if (current != null) {
                        val scheduled = app.scheduler.scheduleSnooze(
                            current.alarm,
                            current.snoozeCount + 1
                        )
                        if (!scheduled) {
                            Toast.makeText(context, "已达最大贪睡次数", Toast.LENGTH_SHORT).show()
                        }
                    }
                    AlarmService.sendAction(context, AlarmService.ACTION_STOP)
                    RingingStateHolder.clear()
                },
                exactAlarmOk = exactAlarmOk,
                notificationsOk = notificationsOk,
                fullScreenOk = fullScreenOk,
                batteryOptimizationsOk = batteryOptimizationsOk,
                focusAlarmId = focusAlarmId.takeIf { it > 0L },
                onFocusConsumed = { focusAlarmId = -1L },
                onDeleteAlarms = { ids ->
                    scope.launch {
                        ids.forEach { id ->
                            app.scheduler.cancel(id)
                            app.repository.delete(id)
                        }
                    }
                },
                onToggle = { alarm, enabled ->
                    scope.launch {
                        val updated = alarm.copy(enabled = enabled)
                        app.repository.upsert(updated)
                        if (enabled) app.scheduler.schedule(updated) else app.scheduler.cancel(alarm.id)
                    }
                },
                onEdit = { alarm ->
                    editingId = alarm.id
                    route = ROUTE_EDIT
                },
                onAdd = {
                    if (alarms.size >= MAX_ALARMS) {
                        Toast.makeText(
                            context,
                            "最多支持 $MAX_ALARMS 个闹钟，请先删除不用的闹钟",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        editingId = 0L
                        route = ROUTE_EDIT
                    }
                },
                onFixExactAlarm = { startSystemIntent(Permissions.exactAlarmSettingsIntent(context)) },
                onFixNotifications = { startSystemIntent(Permissions.notificationSettingsIntent(context)) },
                onFixFullScreen = { startSystemIntent(Permissions.fullScreenIntentSettingsIntent(context)) },
                onFixBatteryOptimizations = {
                    val request = Permissions.batteryOptimizationSettingsIntent(context)
                    runCatching { context.startActivity(request) }
                        .onFailure {
                            // 部分 ROM 不支持直接授权页，回退到电池优化列表
                            runCatching {
                                context.startActivity(Permissions.batteryOptimizationListIntent())
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    "无法打开电池优化设置",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                }
            )
        }
    }

    companion object {
        private const val ROUTE_LIST = "list"
        private const val ROUTE_EDIT = "edit"

        /** 闹钟数量上限：避免上百个 setAlarmClock 拖累系统调度。 */
        private const val MAX_ALARMS = 50
    }
}
