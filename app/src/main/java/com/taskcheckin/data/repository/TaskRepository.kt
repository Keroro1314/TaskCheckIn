package com.taskcheckin.data.repository

import com.taskcheckin.data.local.*
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.Locale

class TaskRepository(private val taskDao: TaskDao) {

    // ===== 每日任务 =====
    fun getAllTasks(): Flow<List<TaskEntity>> = taskDao.getAllTasks()

    fun getDailyTasks(): Flow<List<TaskEntity>> = taskDao.getTasksByType(TASK_TYPE_DAILY)

    fun getDailyTasksSync(): List<TaskEntity> = taskDao.getTasksByTypeSync(TASK_TYPE_DAILY)

    /** 同步获取未完成的每日任务（用于 CalendarActivity 等同步上下文）*/
    fun getIncompleteDailyTasksSync(): List<TaskEntity> = taskDao.getIncompleteTasksByTypeSync(TASK_TYPE_DAILY)

    // ===== 日程任务 =====
    fun getScheduledTasksByDate(date: Long): Flow<List<TaskEntity>> =
        taskDao.getScheduledTasksByDate(TASK_TYPE_SCHEDULED, normalizeDate(date))

    fun getUpcomingScheduledTasks(): Flow<List<TaskEntity>> =
        taskDao.getUpcomingScheduledTasks(TASK_TYPE_SCHEDULED, normalizeDate(System.currentTimeMillis()))

    fun getTodayScheduledTasks(): Flow<List<TaskEntity>> =
        taskDao.getTodayScheduledTasks(TASK_TYPE_SCHEDULED, normalizeDate(System.currentTimeMillis()))

    // ===== 创建任务 =====
    /** 添加每日任务（兼容旧接口） */
    suspend fun addTask(title: String, orderIndex: Int): Long {
        val task = TaskEntity(
            title = title.trim(),
            orderIndex = orderIndex,
            taskType = TASK_TYPE_DAILY
        )
        return taskDao.insertTask(task)
    }

    /** 添加日程任务 */
    suspend fun addScheduledTask(
        title: String,
        orderIndex: Int,
        dueDate: Long,
        reminderTime: Long,
        repeatMode: String = REPEAT_NONE,
        alarmRequestCode: Int = 0
    ): Long {
        val task = TaskEntity(
            title = title.trim(),
            orderIndex = orderIndex,
            taskType = TASK_TYPE_SCHEDULED,
            dueDate = normalizeDate(dueDate),
            reminderTime = reminderTime,
            repeatMode = repeatMode,
            alarmRequestCode = alarmRequestCode
        )
        return taskDao.insertTask(task)
    }

    // ===== 更新任务 =====
    suspend fun updateTask(task: TaskEntity) = taskDao.updateTask(task)

    suspend fun deleteTask(task: TaskEntity) = taskDao.deleteTask(task)

    suspend fun deleteTaskById(id: Long) = taskDao.deleteTaskById(id)

    suspend fun toggleCompletion(id: Long, completed: Boolean) {
        taskDao.updateCompletion(id, completed)
    }

    /** 重置每日任务（每日 0 点调用） */
    suspend fun resetAllToday() {
        taskDao.resetAllCompletions(TASK_TYPE_DAILY)
        // 同时重置今日之前的过期日程任务
        taskDao.resetPastScheduledTasks(normalizeDate(System.currentTimeMillis()))
    }

    suspend fun updateOrder(id: Long, newIndex: Int) = taskDao.updateOrderIndex(id, newIndex)

    suspend fun getTaskById(id: Long): TaskEntity? = taskDao.getTaskById(id)

    // ===== 闹钟相关 =====
    suspend fun updateAlarmCode(id: Long, newCode: Int) = taskDao.updateAlarmCode(id, newCode)

    suspend fun markReminderFired(id: Long) = taskDao.markReminderFired(id)

    suspend fun getPendingAlarms(): List<TaskEntity> =
        taskDao.getPendingAlarms(System.currentTimeMillis())

    /** 直接插入 TaskEntity（由 ViewModel addTaskDirectly 调用）*/
    suspend fun addTaskDirectly(task: TaskEntity): Long {
        return taskDao.insertTask(task)
    }

    // ===== 辅助函数 =====
    /** 将时间戳归一化到当天 0 点（年月日） */
    fun normalizeDate(timestamp: Long): Long {
        val cal = Calendar.getInstance(Locale.CHINA).apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}
