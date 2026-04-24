package com.taskcheckin

import android.app.Application
import com.taskcheckin.data.local.AppDatabase
import com.taskcheckin.data.repository.TaskHistoryRepository
import com.taskcheckin.data.repository.TaskRepository
import com.taskcheckin.util.DailyResetWorker
import com.taskcheckin.util.IncompleteTaskCheckWorker

class TaskCheckInApp : Application() {

    val database by lazy { AppDatabase.getInstance(this) }
    val taskRepository by lazy { TaskRepository(database.taskDao()) }
    val historyRepository by lazy { TaskHistoryRepository(database.taskHistoryDao()) }

    override fun onCreate() {
        super.onCreate()
        DailyResetWorker.schedule(this)
        // 调度每日未完成任务提醒检查（每天20:00触发）
        IncompleteTaskCheckWorker.schedule(this)
    }
}