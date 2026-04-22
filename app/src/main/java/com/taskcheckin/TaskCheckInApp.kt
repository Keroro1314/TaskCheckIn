package com.taskcheckin

import android.app.Application
import com.taskcheckin.data.local.AppDatabase
import com.taskcheckin.data.repository.TaskHistoryRepository
import com.taskcheckin.data.repository.TaskRepository
import com.taskcheckin.util.DailyResetWorker

class TaskCheckInApp : Application() {

    val database by lazy { AppDatabase.getInstance(this) }
    val taskRepository by lazy { TaskRepository(database.taskDao()) }
    val historyRepository by lazy { TaskHistoryRepository(database.taskHistoryDao()) }

    override fun onCreate() {
        super.onCreate()
        DailyResetWorker.schedule(this)
    }
}