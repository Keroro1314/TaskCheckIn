package com.taskcheckin.data.repository

import com.taskcheckin.data.local.TaskHistoryDao
import com.taskcheckin.data.local.TaskHistoryEntity
import kotlinx.coroutines.flow.Flow

class TaskHistoryRepository(private val historyDao: TaskHistoryDao) {

    fun getAllHistory(): Flow<List<TaskHistoryEntity>> = historyDao.getAllHistory()

    suspend fun addToHistory(title: String) {
        val history = TaskHistoryEntity(title = title)
        historyDao.insertHistory(history)
    }

    suspend fun clearAllHistory() {
        historyDao.clearAllHistory()
    }

    suspend fun deleteHistory(history: TaskHistoryEntity) {
        historyDao.deleteHistory(history)
    }
}