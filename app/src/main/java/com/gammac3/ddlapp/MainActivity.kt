package com.gammac3.ddlapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gammac3.ddlapp.model.DdlRow
import com.gammac3.ddlapp.ui.editor.DdlEditorSheet
import com.gammac3.ddlapp.ui.list.DdlListScreen
import com.gammac3.ddlapp.ui.list.DdlListViewModel
import com.gammac3.ddlapp.ui.theme.DdlAppTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = applicationContext as DdlApplication
        setContent {
            DdlAppTheme {
                val model = viewModel { DdlListViewModel(app.container.repository) }
                val state by model.uiState.collectAsStateWithLifecycle()
                var editing by remember { mutableStateOf<DdlRow?>(null) }
                var sheetOpen by rememberSaveable { mutableStateOf(false) }
                // 行可能在弹窗打开后被系统外改动；派生最新快照，避免完成按钮状态陈旧。
                val editingFresh = editing?.let { e -> state.rows.firstOrNull { it.id == e.id } } ?: editing
                DdlListScreen(
                    viewModel = model,
                    onEdit = { row -> editing = row; sheetOpen = true },
                )
                DdlEditorSheet(
                    open = sheetOpen,
                    editing = editingFresh,
                    onDismiss = { sheetOpen = false },
                    onSave = { draft, editingId ->
                        model.save(draft, editingId)
                        sheetOpen = false
                    },
                    onToggleDone = { row -> model.toggleDone(row) },
                    onDelete = { row ->
                        model.delete(row)
                        sheetOpen = false
                    },
                )
            }
        }
    }

    companion object { const val EXTRA_OPEN_DDL_ID = "open_ddl_id" }
}
