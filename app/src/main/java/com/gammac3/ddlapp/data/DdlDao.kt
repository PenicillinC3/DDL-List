package com.gammac3.ddlapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DdlDao {
    @Query("SELECT * FROM ddls ORDER BY deadlineEpochMillis ASC")
    fun observeAll(): Flow<List<DdlEntity>>

    @Insert
    suspend fun insert(entity: DdlEntity): Long

    /** @return 受影响行数；行不存在时为 0（Room @Update 语义）。 */
    @Update
    suspend fun update(entity: DdlEntity): Int

    @Query("DELETE FROM ddls WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM ddls WHERE id = :id")
    suspend fun byId(id: Long): DdlEntity?

    @Query("SELECT * FROM ddls WHERE isDone = 0")
    suspend fun allActive(): List<DdlEntity>
}
