package com.alarm.qinghao.wakeup.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.alarm.qinghao.wakeup.data.AlarmEntity

/**
 * 闹钟铃声播放器：走 USAGE_ALARM 通道，支持循环播放与音量渐强。
 *
 * 两条稳定性约束：
 * 1. 使用 prepareAsync，不在响铃主线程同步解码（避免大文件/慢存储导致 ANR）；
 * 2. MediaPlayer 打不开 URI（例如媒体库铃声缺少读权限）时回退到系统 Ringtone 播放，
 *    保证"至少有声"；回退路径不支持音量渐强。
 */
class AlarmPlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var ringtone: Ringtone? = null
    private var ringtoneLoop: Runnable? = null
    private var fadeRunnable: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())

    fun start(soundUri: String?, fadeInSeconds: Int) {
        if (AlarmEntity.isSilent(soundUri)) {
            Log.i(TAG, "闹钟设为静音，仅震动")
            return
        }
        val uri = resolveUri(soundUri) ?: run {
            Log.w(TAG, "未找到可用铃声")
            return
        }
        val fadeSeconds = fadeInSeconds.coerceIn(0, MAX_FADE_SECONDS)

        try {
            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            player.setDataSource(context, uri)
            player.isLooping = true
            player.setOnPreparedListener { prepared ->
                // 准备期间已被 stop，则不再启动
                if (mediaPlayer !== prepared) return@setOnPreparedListener
                if (fadeSeconds == 0) {
                    prepared.setVolume(TARGET_VOLUME, TARGET_VOLUME)
                } else {
                    prepared.setVolume(0f, 0f)
                }
                runCatching { prepared.start() }
                    .onFailure { Log.e(TAG, "铃声启动失败", it) }
                if (fadeSeconds > 0) startFadeIn(fadeSeconds)
            }
            player.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer 错误 what=$what extra=$extra，回退系统铃声")
                releasePlayer()
                fallbackToRingtone(uri)
                true
            }
            mediaPlayer = player
            player.prepareAsync()
        } catch (e: Exception) {
            // setDataSource 失败通常是缺少媒体读取权限，回退系统铃声
            Log.e(TAG, "铃声准备失败，回退系统铃声 uri=$uri", e)
            releasePlayer()
            fallbackToRingtone(uri)
        }
    }

    fun stop() {
        releasePlayer()
        ringtoneLoop?.let { handler.removeCallbacks(it) }
        ringtoneLoop = null
        ringtone?.let { current ->
            runCatching { if (current.isPlaying) current.stop() }
        }
        ringtone = null
    }

    private fun fallbackToRingtone(uri: Uri) {
        val system = runCatching { RingtoneManager.getRingtone(context, uri) }.getOrNull() ?: run {
            Log.w(TAG, "系统铃声获取失败 uri=$uri")
            return
        }
        runCatching {
            system.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { system.isLooping = true }
        }
        ringtone = system
        runCatching { system.play() }.onFailure { Log.e(TAG, "系统铃声播放失败", it) }

        // API 26/27 的 Ringtone 不支持循环，用定时重播兜底
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            val loop = object : Runnable {
                override fun run() {
                    val current = ringtone ?: return
                    runCatching { current.stop() }
                    runCatching { current.play() }
                    handler.postDelayed(this, RINGTONE_LOOP_MS)
                }
            }
            ringtoneLoop = loop
            handler.postDelayed(loop, RINGTONE_LOOP_MS)
        }
    }

    private fun startFadeIn(fadeSeconds: Int) {
        val steps = (fadeSeconds * 1000 / FADE_STEP_MS).coerceAtLeast(1)
        var step = 0
        val runnable = object : Runnable {
            override fun run() {
                step++
                val volume = (TARGET_VOLUME * step / steps.toFloat()).coerceAtMost(TARGET_VOLUME)
                runCatching { mediaPlayer?.setVolume(volume, volume) }
                if (step < steps) {
                    handler.postDelayed(this, FADE_STEP_MS.toLong())
                }
            }
        }
        fadeRunnable = runnable
        handler.postDelayed(runnable, FADE_STEP_MS.toLong())
    }

    private fun releasePlayer() {
        fadeRunnable?.let { handler.removeCallbacks(it) }
        fadeRunnable = null
        mediaPlayer?.let { player ->
            runCatching { if (player.isPlaying) player.stop() }
            runCatching { player.release() }
        }
        mediaPlayer = null
    }

    private fun resolveUri(soundUri: String?): Uri? {
        if (!soundUri.isNullOrBlank() && !AlarmEntity.isSilent(soundUri)) {
            runCatching { Uri.parse(soundUri) }.getOrNull()?.let { return it }
        }
        return RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    }

    companion object {
        private const val TAG = "AlarmPlayer"
        private const val TARGET_VOLUME = 0.95f
        private const val MAX_FADE_SECONDS = 60
        private const val FADE_STEP_MS = 250
        private const val RINGTONE_LOOP_MS = 3_000L
    }
}
