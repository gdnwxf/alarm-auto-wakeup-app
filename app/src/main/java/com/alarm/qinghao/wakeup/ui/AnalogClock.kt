package com.alarm.qinghao.wakeup.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

/**
 * 模拟表盘：实时显示当前时间，秒针每秒走动。
 * 刻度、数字、指针全部 Canvas 绘制，尺寸由调用方通过 modifier 控制。
 */
@Composable
fun AnalogClock(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    var now by remember { mutableStateOf(Calendar.getInstance()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Calendar.getInstance()
            delay(1_000L)
        }
    }

    val textMeasurer = rememberTextMeasurer()
    val numberStyle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium)
    val dialColor = colors.surfaceVariant.copy(alpha = 0.45f)
    val tickColor = colors.onSurfaceVariant.copy(alpha = 0.45f)
    val handColor = colors.onSurface
    val secondColor = colors.error
    val numberLayouts = remember(textMeasurer, numberStyle, colors.onSurface) {
        (1..12).map { hour ->
            textMeasurer.measure(
                text = AnnotatedString(hour.toString()),
                style = numberStyle.copy(color = colors.onSurface)
            )
        }
    }

    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        drawCircle(color = dialColor, radius = radius, center = center)
        drawCircle(
            color = tickColor.copy(alpha = 0.5f),
            radius = radius,
            center = center,
            style = Stroke(width = 1.5.dp.toPx())
        )

        // 60 格刻度，每 5 格加粗
        for (index in 0 until 60) {
            val angle = Math.toRadians((index * 6 - 90).toDouble())
            val isHourTick = index % 5 == 0
            val outer = radius * 0.93f
            val inner = if (isHourTick) radius * 0.84f else radius * 0.89f
            drawLine(
                color = tickColor,
                start = Offset(
                    center.x + inner * cos(angle).toFloat(),
                    center.y + inner * sin(angle).toFloat()
                ),
                end = Offset(
                    center.x + outer * cos(angle).toFloat(),
                    center.y + outer * sin(angle).toFloat()
                ),
                strokeWidth = if (isHourTick) 2.5.dp.toPx() else 1.2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // 1~12 数字（布局预计算，避免每秒重复 measure）
        for (hour in 1..12) {
            val angle = Math.toRadians((hour * 30 - 90).toDouble())
            val distance = radius * 0.73f
            val layout = numberLayouts[hour - 1]
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    center.x + distance * cos(angle).toFloat() - layout.size.width / 2f,
                    center.y + distance * sin(angle).toFloat() - layout.size.height / 2f
                )
            )
        }

        val second = now.get(Calendar.SECOND)
        val minute = now.get(Calendar.MINUTE) + second / 60f
        val hour = (now.get(Calendar.HOUR) % 12) + minute / 60f

        rotate(degrees = hour * 30f, pivot = center) {
            drawLine(
                color = handColor,
                start = center,
                end = Offset(center.x, center.y - radius * 0.5f),
                strokeWidth = 7.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        rotate(degrees = minute * 6f, pivot = center) {
            drawLine(
                color = handColor,
                start = center,
                end = Offset(center.x, center.y - radius * 0.72f),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        rotate(degrees = second * 6f, pivot = center) {
            drawLine(
                color = secondColor,
                start = Offset(center.x, center.y + radius * 0.14f),
                end = Offset(center.x, center.y - radius * 0.82f),
                strokeWidth = 1.8.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        drawCircle(color = secondColor, radius = 4.dp.toPx(), center = center)
        drawCircle(color = Color.White, radius = 1.6.dp.toPx(), center = center)
    }
}
