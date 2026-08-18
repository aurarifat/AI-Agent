package com.example.data

import kotlinx.coroutines.flow.Flow

class TaskHistoryRepository(private val dao: TaskHistoryDao) {
    val allHistory: Flow<List<TaskHistoryEntity>> = dao.getAllHistory()
    val recentHistory: Flow<List<TaskHistoryEntity>> = dao.getRecentHistory(5)

    suspend fun insertTask(task: TaskHistoryEntity): Long = dao.insertTask(task)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun clearAll() = dao.clearAll()
}
