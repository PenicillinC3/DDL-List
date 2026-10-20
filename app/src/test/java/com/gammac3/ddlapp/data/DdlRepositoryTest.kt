package com.gammac3.ddlapp.data

import com.gammac3.ddlapp.logic.TriggerPoint
import com.gammac3.ddlapp.notify.AlarmScheduler
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DdlRepositoryTest {

    private class FakeDdlDao : DdlDao {
        private val store = LinkedHashMap<Long, DdlEntity>()
        private val flow = MutableStateFlow<List<DdlEntity>>(emptyList())
        private fun refresh() { flow.value = store.values.toList() }

        override fun observeAll(): Flow<List<DdlEntity>> = flow.map { it }
        override suspend fun insert(entity: DdlEntity): Long {
            val id = if (entity.id != 0L) entity.id else (store.keys.maxOrNull() ?: 0L) + 1
            store[id] = entity.copy(id = id); refresh(); return id
        }
        override suspend fun update(entity: DdlEntity): Int {
            if (!store.containsKey(entity.id)) return 0
            store[entity.id] = entity; refresh(); return 1
        }
        override suspend fun deleteById(id: Long) { store.remove(id); refresh() }
        override suspend fun byId(id: Long): DdlEntity? = store[id]
        override suspend fun allActive(): List<DdlEntity> = store.values.filter { !it.isDone }
        override suspend fun all(): List<DdlEntity> = store.values.toList()
    }

    private class FakeAlarmScheduler : AlarmScheduler {
        val scheduled = ConcurrentHashMap<Long, MutableList<TriggerPoint>>()
        val cancelled = mutableListOf<Long>()
        override fun reschedule(ddlId: Long, title: String, triggers: List<TriggerPoint>) {
            scheduled[ddlId] = triggers.toMutableList()
        }
        override fun cancelAll(ddlId: Long) { cancelled.add(ddlId); scheduled.remove(ddlId) }
        override fun canScheduleExact(): Boolean = true
    }

    private val now = 200_000_000_000L
    private fun newEntity(deadline: Long, leads: List<Long> = listOf(5L), done: Boolean = false, id: Long = 0) =
        DdlEntity(id, "t", "", deadline, leads, done, now)

    @Test
    fun `新增后按未来触发点重排`() = runBlocking {
        val dao = FakeDdlDao(); val sch = FakeAlarmScheduler()
        val repo = DdlRepository(dao, sch) { now }
        val deadline = now + 2 * 24 * 60 * 60_000L
        val id = repo.upsert(newEntity(deadline, listOf(1440L, 5L)))
        val points = sch.scheduled.getValue(id).map { it.leadMinutes }
        assertEquals(listOf(1440L, 5L, 0L).sorted(), points.sorted())
    }

    @Test
    fun `全部提前量已过期时只排到期时刻`() = runBlocking {
        val dao = FakeDdlDao(); val sch = FakeAlarmScheduler()
        val repo = DdlRepository(dao, sch) { now }
        val id = repo.upsert(newEntity(now + 60_000L, listOf(1440L)))
        assertEquals(listOf(0L), sch.scheduled.getValue(id).map { it.leadMinutes })
    }

    @Test
    fun `标记完成会取消闹钟 不再重排`() = runBlocking {
        val dao = FakeDdlDao(); val sch = FakeAlarmScheduler()
        val repo = DdlRepository(dao, sch) { now }
        val id = repo.upsert(newEntity(now + 3_600_000L))
        repo.setDone(id, true)
        assertNull(sch.scheduled[id])
    }

    @Test
    fun `取消完成会重排`() = runBlocking {
        val dao = FakeDdlDao(); val sch = FakeAlarmScheduler()
        val repo = DdlRepository(dao, sch) { now }
        val id = repo.upsert(newEntity(now + 3_600_000L))
        repo.setDone(id, true)
        repo.setDone(id, false)
        assertEquals(listOf(5L, 0L), sch.scheduled.getValue(id).map { it.leadMinutes })
    }

    @Test
    fun `删除返回被删实体并取消闹钟`() = runBlocking {
        val dao = FakeDdlDao(); val sch = FakeAlarmScheduler()
        val repo = DdlRepository(dao, sch) { now }
        val id = repo.upsert(newEntity(now + 3_600_000L))
        val deleted = repo.delete(id)!!
        assertEquals(id, deleted.id)
        assertTrue(sch.cancelled.contains(id))
        assertNull(sch.scheduled[id])
        assertNull(dao.byId(id))
    }

    @Test
    fun `删除后用原实体恢复时重新插入原行`() = runBlocking {
        val dao = FakeDdlDao(); val sch = FakeAlarmScheduler()
        val repo = DdlRepository(dao, sch) { now }
        val id = repo.upsert(newEntity(now + 3_600_000L))
        val deleted = repo.delete(id)!!
        val restored = repo.upsert(deleted)
        assertEquals(id, restored)
        assertNotNull(dao.byId(id))
        assertNotNull(sch.scheduled[id])
    }

    @Test
    fun `静音条目不排任何闹钟`() = runBlocking {
        val dao = FakeDdlDao(); val sch = FakeAlarmScheduler()
        val repo = DdlRepository(dao, sch) { now }
        val id = repo.upsert(newEntity(now + 3_600_000L).copy(muted = true))
        assertNull(sch.scheduled[id])
        assertTrue(sch.cancelled.contains(id))
    }

    @Test
    fun `rescheduleAll 只处理未完成条目`() = runBlocking {
        val dao = FakeDdlDao(); val sch = FakeAlarmScheduler()
        val repo = DdlRepository(dao, sch) { now }
        val a = repo.upsert(newEntity(now + 3_600_000L))
        val b = repo.upsert(newEntity(now + 5_400_000L))
        repo.setDone(b, true)
        repo.rescheduleAll()
        assertTrue(sch.scheduled.keys.contains(a))
        assertFalse(sch.scheduled.keys.contains(b))
    }

    @Test
    fun `导出回读等于全部条目`() = runBlocking {
        val dao = FakeDdlDao(); val sch = FakeAlarmScheduler()
        val repo = DdlRepository(dao, sch) { now }
        repo.upsert(newEntity(now + 3_600_000L).copy(title = "甲"))
        repo.upsert(newEntity(now + 7_200_000L).copy(title = "乙", isDone = true))
        val back = DdlBackup.fromJson(repo.exportJson())
        assertEquals(setOf("甲", "乙"), back.map { it.title }.toSet())
        assertTrue(back.any { it.isDone })
    }

    @Test
    fun `追加导入跳过重复并为新条目重建闹钟`() = runBlocking {
        val dao = FakeDdlDao(); val sch = FakeAlarmScheduler()
        val repo = DdlRepository(dao, sch) { now }
        repo.upsert(newEntity(now + 3_600_000L).copy(title = "概率论"))
        val json = DdlBackup.toJson(
            listOf(
                DdlEntity(0, "概率论", "", now + 3_600_000L, listOf(5L), false, now),
                DdlEntity(0, "高数", "", now + 86_400_000L, listOf(10L), false, now),
            ), now,
        )
        val r = repo.importBackup(json, replace = false)
        assertEquals(1, r.affected)
        assertEquals(1, r.skipped)
        val gaoshuId = dao.all().first { it.title == "高数" }.id
        assertTrue(sch.scheduled.containsKey(gaoshuId))
    }

    @Test
    fun `覆盖导入恢复全部并只给未完成非静音排闹钟`() = runBlocking {
        val dao = FakeDdlDao(); val sch = FakeAlarmScheduler()
        val repo = DdlRepository(dao, sch) { now }
        repo.upsert(newEntity(now + 3_600_000L).copy(title = "将被清掉"))
        val json = DdlBackup.toJson(
            listOf(
                DdlEntity(0, "甲", "", now + 3_600_000L, listOf(5L), false, now),
                DdlEntity(0, "乙", "", now + 3_600_000L, listOf(5L), true, now),
                DdlEntity(0, "丙", "", now + 3_600_000L, listOf(5L), false, now, true),
            ), now,
        )
        val r = repo.importBackup(json, replace = true)
        assertEquals(3, r.affected)
        assertEquals(1, r.discarded)
        assertEquals(setOf("甲", "乙", "丙"), dao.all().map { it.title }.toSet())
        val jiadaId = dao.all().first { it.title == "甲" }.id
        val yidaId = dao.all().first { it.title == "乙" }.id
        val bingdaId = dao.all().first { it.title == "丙" }.id
        assertTrue(sch.scheduled.containsKey(jiadaId))
        assertFalse(sch.scheduled.containsKey(yidaId))
        assertFalse(sch.scheduled.containsKey(bingdaId))
    }
}
