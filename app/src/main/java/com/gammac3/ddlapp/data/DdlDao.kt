package com.gammac3.ddlapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    /** 全量快照（含已完成）：导出备份用。 */
    @Query("SELECT * FROM ddls")
    suspend fun all(): List<DdlEntity>

    @Query("SELECT * FROM completion_events ORDER BY completedAt")
    fun observeCompletionEvents(): Flow<List<DdlCompletion>>

    @Query("UPDATE ddls SET isDone = :done WHERE id = :id")
    suspend fun updateDone(id: Long, done: Boolean): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCompletion(completion: DdlCompletion)

    @Query("DELETE FROM completion_events WHERE ddlId = :ddlId")
    suspend fun deleteCompletion(ddlId: Long)

    @Transaction
    suspend fun setCompletion(id: Long, done: Boolean, completedAt: Long): Boolean {
        if (updateDone(id, done) == 0) return false
        if (done) saveCompletion(DdlCompletion(id, completedAt)) else deleteCompletion(id)
        return true
    }
}
