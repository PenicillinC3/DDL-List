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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DdlDraft(
    val title: String,
    val note: String,
    val deadlineMillis: Long,
    val leadsMinutes: List<Long>,
)

enum class DdlFilter { ALL, ACTIVE, DONE }

data class DdlListUiState(
    val rows: List<DdlRow> = emptyList(),
    val filter: DdlFilter = DdlFilter.ALL,
    val lastDeleted: DdlRow? = null,
)

private fun DdlEntity.toRow() = DdlRow(id, title, note, deadlineEpochMillis, leadsMinutes, isDone)

class DdlListViewModel(private val repository: DdlRepository) : ViewModel() {

    private val filter = MutableStateFlow(DdlFilter.ALL)
    private val lastDeleted = MutableStateFlow<DdlRow?>(null)

    val uiState: StateFlow<DdlListUiState> =
        combine(repository.observeAll(), filter, lastDeleted) { entities, f, deleted ->
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
                lastDeleted = deleted,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DdlListUiState())

    fun setFilter(f: DdlFilter) { filter.value = f }

    fun save(draft: DdlDraft, editingId: Long?) {
        viewModelScope.launch {
            repository.upsert(
                DdlEntity(
                    id = editingId ?: 0L,
                    title = draft.title.trim(),
                    note = draft.note.trim(),
                    deadlineEpochMillis = draft.deadlineMillis,
                    leadsMinutes = draft.leadsMinutes.distinct(),
                    isDone = false,
                    createdAt = System.currentTimeMillis(),
                )
            )
        }
    }

    fun delete(row: DdlRow) {
        viewModelScope.launch {
            val snapshot = repository.delete(row.id) ?: return@launch
            lastDeleted.value = snapshot.toRow()
        }
    }

    fun restoreDeleted() {
        val d = lastDeleted.value ?: return
        viewModelScope.launch {
            repository.upsert(
                DdlEntity(
                    id = d.id,
                    title = d.title,
                    note = d.note,
                    deadlineEpochMillis = d.deadlineMillis,
                    leadsMinutes = d.leadsMinutes,
                    isDone = d.isDone,
                    createdAt = d.deadlineMillis,
                )
            )
            lastDeleted.value = null
        }
    }

    fun clearDeleted() { lastDeleted.value = null }

    fun toggleDone(row: DdlRow) {
        viewModelScope.launch { repository.setDone(row.id, !row.isDone) }
    }
}
