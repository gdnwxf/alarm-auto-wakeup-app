package com.alarm.qinghao.wakeup

import android.app.Application
import android.util.Log
import com.alarm.qinghao.wakeup.alarm.AlarmScheduler
import com.alarm.qinghao.wakeup.data.AlarmDatabase
import com.alarm.qinghao.wakeup.data.AlarmRepository
import com.alarm.qinghao.wakeup.util.NotificationChannels
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AlarmApplication : Application() {

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        // 后台补注册失败（如 DB 损坏）不应导致进程崩溃
        Log.e("AlarmApplication", "后台任务异常", throwable)
    }

    private val appScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)

    lateinit var repository: AlarmRepository
        private set

    lateinit var scheduler: AlarmScheduler
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        NotificationChannels.ensure(this)
        repository = AlarmRepository(AlarmDatabase.get(this).alarmDao())
        scheduler = AlarmScheduler(this)

        // 进程重新创建后补齐已启用闹钟的注册（幂等操作）
        appScope.launch {
            repository.getEnabled().forEach { scheduler.schedule(it) }
        }
    }

    companion object {
        lateinit var instance: AlarmApplication
            private set
    }
}
