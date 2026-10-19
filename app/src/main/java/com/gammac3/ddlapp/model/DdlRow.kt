package com.gammac3.ddlapp.model

data class DdlRow(
    val id: Long,
    val title: String,
    val note: String,
    val deadlineMillis: Long,
    val leadsMinutes: List<Long>,
    val isDone: Boolean,
)

enum class DdlGroup { OVERDUE, TODAY, TOMORROW, THIS_WEEK, LATER, DONE }
