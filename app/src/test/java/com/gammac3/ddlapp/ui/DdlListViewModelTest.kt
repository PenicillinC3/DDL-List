package com.gammac3.ddlapp.ui

import com.gammac3.ddlapp.data.DdlDao
import com.gammac3.ddlapp.data.DdlEntity
import com.gammac3.ddlapp.data.DdlRepository
import com.gammac3.ddlapp.logic.TriggerPoint
import com.gammac3.ddlapp.notify.AlarmScheduler
import com.gammac3.ddlapp.ui.list.DdlDraft
import com.gammac3.ddlapp.ui.list.DdlFilter
import com.gammac3.ddlapp.ui.list.DdlListViewModel
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DdlListViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val now = 200_000_000_000L

    private class FakeDdlDao : DdlDao {
        private val store = LinkedHashMap<Long, DdlEntity>()
        private val flow = MutableStateFlow<List<DdlEntity>>(emptyList())
        private fun refresh() { flow.value = store.values.toList() }
        override fun observeAll(): Flow<List<DdlEntity>> = flow
        override suspend fun insert(entity: DdlEntity): Long {
            val id = (store.keys.maxOrNull() ?: 0L) + 1
            store[id] = entity.copy(id = id); refresh(); return id
        }
        override suspend fun update(entity: DdlEntity) { store[entity.id] = entity; refresh() }
        override suspend fun deleteById(id: Long) { store.remove(id); refresh() }
        override suspend fun byId(id: Long): DdlEntity? = store[id]
        override suspend fun allActive(): List<DdlEntity> = store.values.filter { !it.isDone }
    }

    private class FakeScheduler : AlarmScheduler {
        val scheduled = ConcurrentHashMap<Long, List<TriggerPoint>>()
        override fun reschedule(ddlId: Long, title: String, triggers: List<TriggerPoint>) { scheduled[ddlId] = triggers }
        override fun cancelAll(ddlId: Long) { scheduled.remove(ddlId) }
        override fun canScheduleExact(): Boolean = true
    }

    private lateinit var vm: DdlListViewModel
    private lateinit var dao: FakeDdlDao

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        dao = FakeDdlDao()
        vm = DdlListViewModel(DdlRepository(dao, FakeScheduler()) { now })
    }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `save 后列表出现新条目`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        vm.save(DdlDraft("交报告", "周报", now + 3_600_000L, listOf(5L)), null)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.uiState.value.rows.size)
        assertEquals("交报告", vm.uiState.value.rows.first().title)
    }

    @Test
    fun `delete 移除并记录 lastDeleted`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        vm.save(DdlDraft("A", "", now + 3_600_000L, emptyList()), null)
        dispatcher.scheduler.advanceUntilIdle()
        val row = vm.uiState.value.rows.first()
        vm.delete(row)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, vm.uiState.value.rows.size)
        assertEquals(row.id, vm.uiState.value.lastDeleted?.id)
    }

    @Test
    fun `过滤器 DONE 只显示已完成`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        vm.save(DdlDraft("A", "", now + 3_600_000L, emptyList()), null)
        dispatcher.scheduler.advanceUntilIdle()
        val row = vm.uiState.value.rows.first()
        vm.toggleDone(row)
        dispatcher.scheduler.advanceUntilIdle()
        vm.setFilter(DdlFilter.DONE)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.uiState.value.rows.size)
        vm.setFilter(DdlFilter.ACTIVE)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, vm.uiState.value.rows.size)
    }

    @Test
    fun `编辑已完成条目保持完成状态`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        val deadline = now + 3_600_000L
        vm.save(DdlDraft("A", "", deadline, emptyList()), null)
        dispatcher.scheduler.advanceUntilIdle()
        val row = vm.uiState.value.rows.first()
        vm.toggleDone(row)
        dispatcher.scheduler.advanceUntilIdle()
        vm.save(DdlDraft("A2", "", deadline, emptyList()), row.id)
        dispatcher.scheduler.advanceUntilIdle()
        vm.setFilter(DdlFilter.DONE)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.uiState.value.rows.size)
        assertEquals("A2", vm.uiState.value.rows.first().title)
        vm.setFilter(DdlFilter.ACTIVE)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, vm.uiState.value.rows.size)
    }

    @Test
    fun `撤销删除还原原始 createdAt`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        vm.save(DdlDraft("A", "", now + 3_600_000L, emptyList()), null)
        dispatcher.scheduler.advanceUntilIdle()
        val row = vm.uiState.value.rows.first()
        val before = dao.byId(row.id)!!
        vm.delete(row)
        dispatcher.scheduler.advanceUntilIdle()
        vm.restoreDeleted()
        dispatcher.scheduler.advanceUntilIdle()
        val after = dao.byId(row.id)!!
        assertEquals(before.createdAt, after.createdAt)
        assertEquals(before.isDone, after.isDone)
    }
}
