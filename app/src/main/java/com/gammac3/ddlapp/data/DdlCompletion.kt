package com.gammac3.ddlapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A completion snapshot remains after its source DDL is deleted. */
@Entity(tableName = "completion_events")
data class DdlCompletion(
    @PrimaryKey val ddlId: Long,
    val completedAt: Long,
)
