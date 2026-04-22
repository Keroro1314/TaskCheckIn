package com.taskcheckin.data.repository

import com.taskcheckin.data.local.TaskDao
import com.taskcheckin.data.local.TaskEntity
import kotlinx.coroutines.flow.Flow

class TaskRepository(private val taskDao: TaskDao) {

    fun getAllTasks(): Flow<List<TaskEntity>> = taskDao.getAllTasks()

    suspend fun addTask(title: String, orderIndex: Int): Long {
        val task = TaskEntity(title = title, orderIndex = orderIndex)
        return taskDao.insertTask(task)
    }

    suspend fun updateTask(task: TaskEntity) {
        taskDao.updateTask(task)
    }

    suspend fun deleteTask(task: TaskEntity) {
        taskDao.deleteTask(task)
    }

    suspend fun deleteTaskById(id: Long) {
        taskDao.deleteTaskById(id)
    }

    suspend fun toggleCompletion(id: Long, completed: Boolean) {
        taskDao.updateCompletion(id, completed)
    }

    suspend fun resetAllToday() {
        taskDao.resetAllCompletions()
    }

    suspend fun updateOrder(id: Long, newIndex: Int) {
        taskDao.updateOrderIndex(id, newIndex)
    }

    suspend fun getTaskById(id: Long): TaskEntity? {
        return taskDao.getTaskById(id)
    }
}