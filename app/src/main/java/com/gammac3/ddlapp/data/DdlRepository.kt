package com.gammac3.ddlapp.data

import com.gammac3.ddlapp.logic.ReminderTimeCalculator
import com.gammac3.ddlapp.notify.AlarmScheduler
import kotlinx.coroutines.flow.Flow

class DdlRepository(
    private val dao: DdlDao,
    private val scheduler: AlarmScheduler,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** 数据变更后回调（桌面小组件等外部观察者刷新用）；默认 null，不影响既有构造签名。 */
    var onChanged: (() -> Unit)? = null

    fun observeAll(): Flow<List<DdlEntity>> = dao.observeAll()

    suspend fun upsert(entity: DdlEntity): Long {
        val id = if (entity.id == 0L) dao.insert(entity) else {
            // Room @Update 对已删除的行影响 0 行；撤销删除时须以原 id 重新插入。
            if (dao.update(entity) == 0) dao.insert(entity) else entity.id
        }
        rescheduleFor(id)
        onChanged?.invoke()
        return id
    }

    /** 返回被删实体（供撤销还原），不存在则 null。 */
    suspend fun delete(id: Long): DdlEntity? {
        val entity = dao.byId(id) ?: return null
        scheduler.cancelAll(id)
        dao.deleteById(id)
        onChanged?.invoke()
        return entity
    }

    suspend fun setDone(id: Long, done: Boolean) {
        val entity = dao.byId(id) ?: return
        dao.update(entity.copy(isDone = done))
        if (done) scheduler.cancelAll(id) else rescheduleFor(id)
        onChanged?.invoke()
    }

    suspend fun rescheduleFor(id: Long) {
        val entity = dao.byId(id) ?: return
        scheduler.cancelAll(id)
        if (entity.isDone || entity.muted) return
        val triggers = ReminderTimeCalculator.futureTriggers(entity.deadlineEpochMillis, entity.leadsMinutes, clock())
        if (triggers.isNotEmpty()) scheduler.reschedule(id, entity.title, triggers)
    }

    suspend fun rescheduleAll() {
        dao.allActive().forEach { rescheduleFor(it.id) }
    }
}
