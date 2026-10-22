package com.gammac3.ddlapp.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gammac3.ddlapp.data.DdlBackup
import com.gammac3.ddlapp.data.DdlEntity
import com.gammac3.ddlapp.data.DdlRepository
import com.gammac3.ddlapp.model.DdlRow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DdlDraft(
    val title: String,
    val note: String,
    val deadlineMillis: Long,
    val leadsMinutes: List<Long>,
    val muted: Boolean = false,
)

enum class DdlFilter { ALL, ACTIVE, DONE }

/** 墓碑槽：删除后先在原分组原位立一块可撤销的空槽，窗口（5 秒）走完才真正放弃。
 *  row 用于展示与回插定位，entity 用于以原 id 还原。 */
data class Tombstone(
    val row: DdlRow,
    val entity: DdlEntity,
    val expiresAtMillis: Long,
)

/** 顺延撤销槽：一次「逾期顺延」的原值/新值快照，窗口（5 秒）内可撤销。 */
data class Postponement(
    val rowId: Long,
    val oldDeadlineMillis: Long,
    val newDeadlineMillis: Long,
    val expiresAtMillis: Long,
)

data class DdlListUiState(
    val rows: List<DdlRow> = emptyList(),
    val filter: DdlFilter = DdlFilter.ALL,
    val tombstones: List<Tombstone> = emptyList(),
)

private fun DdlEntity.toRow() = DdlRow(id, title, note, deadlineEpochMillis, leadsMinutes, isDone, muted)

class DdlListViewModel(private val repository: DdlRepository) : ViewModel() {

    private val filter = MutableStateFlow(DdlFilter.ALL)
    private val tombstones = MutableStateFlow<List<Tombstone>>(emptyList())

    val uiState: StateFlow<DdlListUiState> =
        combine(repository.observeAll(), filter, tombstones) { entities, f, tombs ->
            DdlListUiState(
                rows = entities
                    .filter { e ->
                        when (f) {
                            DdlFilter.ALL -> true
                            DdlFilter.ACTIVE -> !e.isDone
                            DdlFilter.DONE -> e.isDone
                        }
                    }
                    .map { it.toRow() },
                filter = f,
                tombstones = tombs,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DdlListUiState())

    fun setFilter(f: DdlFilter) { filter.value = f }

    fun save(draft: DdlDraft, editingId: Long?) {
        viewModelScope.launch {
            var isDone = false
            var createdAt = System.currentTimeMillis()
            if (editingId != null) {
                val current = repository.observeAll().first().firstOrNull { it.id == editingId }
                if (current != null) {
                    isDone = current.isDone
                    createdAt = current.createdAt
                }
            }
            repository.upsert(
                DdlEntity(
                    id = editingId ?: 0L,
                    title = draft.title.trim(),
                    note = draft.note.trim(),
                    deadlineEpochMillis = draft.deadlineMillis,
                    leadsMinutes = draft.leadsMinutes.distinct(),
                    isDone = isDone,
                    createdAt = createdAt,
                    muted = draft.muted,
                )
            )
        }
    }

    fun delete(row: DdlRow) {
        viewModelScope.launch {
            val snapshot = repository.delete(row.id) ?: return@launch
            tombstones.value = tombstones.value + Tombstone(
                row = row,
                entity = snapshot,
                expiresAtMillis = System.currentTimeMillis() + WINDOW_MILLIS,
            )
        }
    }

    /** 点墓碑槽撤销：以原 id 还原（仓库负责重建闹钟）。 */
    fun undoTombstone(id: Long) {
        val tomb = tombstones.value.firstOrNull { it.entity.id == id } ?: return
        // 先从槽列表摘除（防双击双还原），再异步落库
        tombstones.value = tombstones.value.filter { it.entity.id != id }
        viewModelScope.launch { repository.upsert(tomb.entity) }
    }

    /** 窗口走完即收摊（真正的删除已在 delete 时落库，这里只丢掉还原副本）；UI 秒心跳驱动。 */
    fun gcTombstones(nowMillis: Long) {
        val cur = tombstones.value
        if (cur.isEmpty()) return
        val next = cur.filter { it.expiresAtMillis > nowMillis }
        if (next.size != cur.size) tombstones.value = next
    }

    fun toggleDone(row: DdlRow) {
        viewModelScope.launch { repository.setDone(row.id, !row.isDone) }
    }

    // ---------- 逾期一键顺延（v1.15.0）----------
    // 改截止走既有 upsert 管线：闹钟自动重排、onChanged 带动小组件刷新、分组即时重算。

    private val postponementsFlow = MutableStateFlow<Map<Long, Postponement>>(emptyMap())

    /** 顺延撤销槽（按条目 id 键，一次一条的最新值）。 */
    val postponements: StateFlow<Map<Long, Postponement>> = postponementsFlow

    /** 顺延完成事件（Snackbar「撤销」的源头；upsert 成功后才发）。 */
    private val _postponeEvents = MutableSharedFlow<Postponement>(extraBufferCapacity = 8)
    val postponeEvents: MutableSharedFlow<Postponement> = _postponeEvents

    /** 逾期一键顺延：条目未完成时把截止改为 newDeadlineMillis，并留撤销槽。 */
    fun postpone(rowId: Long, newDeadlineMillis: Long) {
        viewModelScope.launch {
            val current = repository.observeAll().first().firstOrNull { it.id == rowId } ?: return@launch
            if (current.isDone) return@launch
            postponementsFlow.value = postponementsFlow.value + (
                rowId to Postponement(
                    rowId = rowId,
                    oldDeadlineMillis = current.deadlineEpochMillis,
                    newDeadlineMillis = newDeadlineMillis,
                    expiresAtMillis = System.currentTimeMillis() + WINDOW_MILLIS,
                )
                )
            repository.upsert(current.copy(deadlineEpochMillis = newDeadlineMillis))
            _postponeEvents.tryEmit(
                Postponement(
                    rowId = rowId,
                    oldDeadlineMillis = current.deadlineEpochMillis,
                    newDeadlineMillis = newDeadlineMillis,
                    expiresAtMillis = 0L, // 事件载体不需要窗口；窗口以撤销槽为准
                )
            )
        }
    }

    /** 撤销顺延：以撤销槽记录的原截止写回（先摘槽防双击双还原）。 */
    fun undoPostpone(rowId: Long) {
        val record = postponementsFlow.value[rowId] ?: return
        postponementsFlow.value = postponementsFlow.value - rowId
        viewModelScope.launch {
            val current = repository.observeAll().first().firstOrNull { it.id == rowId } ?: return@launch
            if (current.isDone) return@launch
            repository.upsert(current.copy(deadlineEpochMillis = record.oldDeadlineMillis))
        }
    }

    /** 顺延撤销槽窗口走完即失效（UI 秒心跳驱动，与墓碑同一拍）。 */
    fun gcPostponements(nowMillis: Long) {
        val cur = postponementsFlow.value
        if (cur.isEmpty()) return
        val next = cur.filterValues { it.expiresAtMillis > nowMillis }
        if (next.size != cur.size) postponementsFlow.value = next
    }

    /** 导出全部条目为备份 JSON v1（含已完成与静音）。 */
    suspend fun exportJson(): String = repository.exportJson()

    /** 备份导入；文件不合法时 IllegalArgumentException 原样抛出（screen 侧 Toast 提示）。 */
    suspend fun importJson(text: String, replace: Boolean): DdlBackup.ImportResult =
        repository.importBackup(text, replace)

    companion object {
        const val WINDOW_MILLIS = 5_000L
    }
}
