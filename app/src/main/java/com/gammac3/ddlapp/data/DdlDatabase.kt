package com.gammac3.ddlapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [DdlEntity::class], version = 1, exportSchema = false)
@TypeConverters(LeadsConverter::class)
abstract class DdlDatabase : RoomDatabase() {
    abstract fun dao(): DdlDao

    companion object {
        fun build(context: Context): DdlDatabase =
            Room.databaseBuilder(context, DdlDatabase::class.java, "ddl.db").build()
    }
}
