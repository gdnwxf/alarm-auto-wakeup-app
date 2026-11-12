package com.alarm.qinghao.wakeup.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.alarm.qinghao.wakeup.data.AlarmEntity
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 全屏响铃界面，深色固定样式。 */
@Composable
fun RingScreen(
    alarm: AlarmEntity,
    snoozeCount: Int,
    isLocked: Boolean,
    isMuted: Boolean,
    onMute: () -> Unit,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var timeText by remember { mutableStateOf(currentTimeText()) }
    // 仅在页面处于前台时刷新，避免响铃页进入后台后每秒重组空耗电量
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                timeText = currentTimeText()
                delay(1_000L)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1D1738), Color(0xFF0A0910))
                )
            )
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Alarm,
                contentDescription = null,
                tint = Color(0xFFB9A6FF),
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = timeText,
                fontSize = 76.sp,
                fontWeight = FontWeight.Light,
                color = Color.White
            )
            if (alarm.label.isNotBlank()) {
                Text(
                    text = alarm.label,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color(0xFFD8CFFF),
                    textAlign = TextAlign.Center
                )
            }
            Text(
                text = if (snoozeCount > 0) "贪睡提醒 · 第 $snoozeCount 次" else "该起床了",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.72f)
            )

            Spacer(Modifier.height(6.dp))

            if (alarm.vibrateMode != 0) {
                Text(
                    text = "震动已开启",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }

            if (alarm.wakeAppPackages.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Apps,
                        contentDescription = null,
                        tint = Color(0xFFB9A6FF),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "响铃后自动打开 ${alarm.wakeAppPackages.size} 个应用",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB9A6FF)
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            if (isMuted) {
                Text(
                    text = "已静音，铃声与震动已停止",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (isLocked) {
                    // 锁屏状态：提供静音与关闭
                    OutlinedButton(
                        onClick = onMute,
                        enabled = !isMuted,
                        modifier = Modifier
                            .weight(1f)
                            .height(62.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White,
                            disabledContentColor = Color.White.copy(alpha = 0.4f)
                        )
                    ) {
                        Text(if (isMuted) "已静音" else "静音")
                    }
                } else {
                    OutlinedButton(
                        onClick = onSnooze,
                        enabled = alarm.snoozeMaxTimes > 0,
                        modifier = Modifier
                            .weight(1f)
                            .height(62.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("贪睡 ${alarm.snoozeMinutes} 分钟")
                    }
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(62.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF7C5CFF),
                        contentColor = Color.White
                    )
                ) {
                    Text("关闭闹钟")
                }
            }
        }
    }
}

private fun currentTimeText(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
