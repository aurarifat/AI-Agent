package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskHistoryDao {
    @Query("SELECT * FROM task_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<TaskHistoryEntity>>

    @Query("SELECT * FROM task_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 5): Flow<List<TaskHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskHistoryEntity): Long

    @Query("DELETE FROM task_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM task_history")
    suspend fun clearAll()
}
