package com.gammac3.ddlapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [DdlEntity::class, DdlCompletion::class], version = 3, exportSchema = false)
@TypeConverters(LeadsConverter::class)
abstract class DdlDatabase : RoomDatabase() {
    abstract fun dao(): DdlDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `completion_events` " +
                        "(`ddlId` INTEGER NOT NULL, `completedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`ddlId`))",
                )
            }
        }

        fun build(context: Context): DdlDatabase =
            Room.databaseBuilder(context, DdlDatabase::class.java, "ddl.db")
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigration(true)
                .build()
    }
}
