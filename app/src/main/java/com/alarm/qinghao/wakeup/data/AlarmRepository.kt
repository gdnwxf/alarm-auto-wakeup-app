package com.alarm.qinghao.wakeup.data

import kotlinx.coroutines.flow.Flow

class AlarmRepository(private val dao: AlarmDao) {

    val alarms: Flow<List<AlarmEntity>> = dao.observeAll()

    suspend fun get(id: Long): AlarmEntity? = dao.getById(id)

    suspend fun getAll(): List<AlarmEntity> = dao.getAll()

    suspend fun getEnabled(): List<AlarmEntity> = dao.getEnabled()

    suspend fun upsert(alarm: AlarmEntity): Long = dao.upsert(alarm)

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)
}
