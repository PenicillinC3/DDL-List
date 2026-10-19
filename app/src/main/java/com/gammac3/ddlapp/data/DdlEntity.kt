package com.gammac3.ddlapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

@Entity(tableName = "ddls")
data class DdlEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String,
    val deadlineEpochMillis: Long,
    val leadsMinutes: List<Long>,
    val isDone: Boolean = false,
    val createdAt: Long,
)

class LeadsConverter {
    @TypeConverter fun fromList(list: List<Long>): String =
        if (list.isEmpty()) "" else list.joinToString(",")
    @TypeConverter fun toList(s: String): List<Long> =
        if (s.isBlank()) emptyList() else s.split(",").map { it.toLong() }
}
