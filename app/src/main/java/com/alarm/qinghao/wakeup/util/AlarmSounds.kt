package com.alarm.qinghao.wakeup.util

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import com.alarm.qinghao.wakeup.data.AlarmEntity

/** 系统铃声选择器的封装。 */
object AlarmSounds {

    fun describe(context: Context, soundUri: String?): String {
        if (AlarmEntity.isSilent(soundUri)) return "静音"
        if (soundUri.isNullOrBlank()) return AlarmEntity.DEFAULT_SOUND_NAME
        val uri = runCatching { Uri.parse(soundUri) }.getOrNull() ?: return "自定义铃声"
        return runCatching {
            RingtoneManager.getRingtone(context, uri)?.getTitle(context)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "自定义铃声"
    }

    fun pickerIntent(soundUri: String?): Intent {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            )
        }
        val existing = soundUri?.takeIf { it.isNotBlank() && !AlarmEntity.isSilent(it) }
        if (existing != null) {
            runCatching { Uri.parse(existing) }.getOrNull()?.let {
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, it)
            }
        }
        return intent
    }

    /**
     * 解析选择器结果。
     * @return first = 持久化用的 soundUri（null 表示默认、silent 表示静音），second = 展示名
     */
    fun parseResult(context: Context, uri: Uri?): Pair<String?, String> {
        if (uri == null) return AlarmEntity.SILENT_URI to "静音"

        val defaultAlarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        if (uri == defaultAlarmUri) return null to AlarmEntity.DEFAULT_SOUND_NAME

        val title = runCatching {
            RingtoneManager.getRingtone(context, uri)?.getTitle(context)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "自定义铃声"

        return uri.toString() to title
    }

    fun preview(context: Context, soundUri: String?) {
        if (AlarmEntity.isSilent(soundUri)) return
        val uri = if (soundUri.isNullOrBlank()) {
            RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
        } else {
            runCatching { Uri.parse(soundUri) }.getOrNull()
        } ?: return
        runCatching {
            RingtoneManager.getRingtone(context, uri)?.play()
        }
    }
}
