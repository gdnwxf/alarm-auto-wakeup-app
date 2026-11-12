package com.alarm.qinghao.wakeup.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.NumberPicker
import java.util.Locale

/**
 * 时间滚轮选择器：小时 / 分钟两列上下滚动。
 * 基于系统 NumberPicker，滚动惯性与无障碍能力由系统提供。
 */
@Composable
fun WheelTimePicker(
    hour: Int,
    minute: Int,
    onTimeChange: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        WheelColumn(
            min = 0,
            max = 23,
            selected = hour,
            onSelected = { onTimeChange(it, minute) }
        )
        Text(
            text = ":",
            fontSize = 34.sp,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onSurface
        )
        WheelColumn(
            min = 0,
            max = 59,
            selected = minute,
            onSelected = { onTimeChange(hour, it) }
        )
    }
}

@Composable
private fun WheelColumn(
    min: Int,
    max: Int,
    selected: Int,
    onSelected: (Int) -> Unit
) {
    // factory 只执行一次，必须通过 rememberUpdatedState 读取最新回调，
    // 否则滚动分钟后再滚小时会把分钟回退成首次组合的值
    val latestOnSelected by rememberUpdatedState(onSelected)

    AndroidView(
        factory = { context ->
            NumberPicker(context).apply {
                minValue = min
                maxValue = max
                wrapSelectorWheel = true
                descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                // 固定两位显示，如 07、30
                setFormatter { value -> String.format(Locale.getDefault(), "%02d", value) }
                value = selected
                setOnValueChangedListener { _, _, newValue -> latestOnSelected(newValue) }
            }
        },
        update = { picker ->
            if (picker.value != selected) {
                picker.value = selected
            }
        },
        modifier = Modifier
            .width(96.dp)
            .height(180.dp)
    )
}
