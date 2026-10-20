package com.gammac3.ddlapp.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gammac3.ddlapp.data.DdlEntity
import com.gammac3.ddlapp.data.DdlRepository
import com.gammac3.ddlapp.model.DdlRow
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

    companion object {
        const val WINDOW_MILLIS = 5_000L
    }
}
