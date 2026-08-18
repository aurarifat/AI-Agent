package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.TaskStatus

@Entity(tableName = "task_history")
data class TaskHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val command: String,
    val provider: String,
    val model: String,
    val timestamp: Long = System.currentTimeMillis(),
    val durationMs: Long = 0,
    val status: String = TaskStatus.COMPLETED.name,
    val actionCount: Int = 0,
    val summary: String = "",
    val actionsSummary: String = "",
    val errorMessage: String? = null
)
