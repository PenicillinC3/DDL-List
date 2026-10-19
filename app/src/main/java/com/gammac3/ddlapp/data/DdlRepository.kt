package com.gammac3.ddlapp.data

import com.gammac3.ddlapp.logic.ReminderTimeCalculator
import com.gammac3.ddlapp.notify.AlarmScheduler
import kotlinx.coroutines.flow.Flow

class DdlRepository(
    private val dao: DdlDao,
    private val scheduler: AlarmScheduler,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observeAll(): Flow<List<DdlEntity>> = dao.observeAll()

    suspend fun upsert(entity: DdlEntity): Long {
        val id = if (entity.id == 0L) dao.insert(entity) else { dao.update(entity); entity.id }
        rescheduleFor(id)
        return id
    }

    /** 返回被删实体（供撤销还原），不存在则 null。 */
    suspend fun delete(id: Long): DdlEntity? {
        val entity = dao.byId(id) ?: return null
        scheduler.cancelAll(id)
        dao.deleteById(id)
        return entity
    }

    suspend fun setDone(id: Long, done: Boolean) {
        val entity = dao.byId(id) ?: return
        dao.update(entity.copy(isDone = done))
        if (done) scheduler.cancelAll(id) else rescheduleFor(id)
    }

    suspend fun rescheduleFor(id: Long) {
        val entity = dao.byId(id) ?: return
        scheduler.cancelAll(id)
        if (entity.isDone) return
        val triggers = ReminderTimeCalculator.futureTriggers(entity.deadlineEpochMillis, entity.leadsMinutes, clock())
        if (triggers.isNotEmpty()) scheduler.reschedule(id, entity.title, triggers)
    }

    suspend fun rescheduleAll() {
        dao.allActive().forEach { rescheduleFor(it.id) }
    }
}
