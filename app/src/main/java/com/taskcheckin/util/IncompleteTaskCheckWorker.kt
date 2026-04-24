package com.taskcheckin.util

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.*
import com.taskcheckin.TaskCheckInApp
import com.taskcheckin.data.local.TaskEntity
import com.taskcheckin.data.local.TASK_TYPE_DAILY
import com.taskcheckin.data.local.TASK_TYPE_SCHEDULED
import com.taskcheckin.data.local.TASK_TYPE_TODAY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * IncompleteTaskCheckWorker — 每日定时检查未完成任务并发送提醒
 * 使用 WorkManager 保证即使 App 未启动也能执行。
 * 触发时间：每天 20:00（可通过设置调整）
 */
class IncompleteTaskCheckWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "IncompleteTaskCheckWorker"
        const val WORK_NAME = "incomplete_task_check"

        // 每日触发时间（小时）
        private const val CHECK_HOUR = 20 // 晚上8点
        private const val CHECK_MINUTE = 0

        /**
         * 调度每日检查任务（App 启动时调用一次即可）
         */
        fun schedule(context: Context) {
            val now = java.util.Calendar.getInstance(java.util.Locale.CHINA)
            val target = java.util.Calendar.getInstance(java.util.Locale.CHINA).apply {
                set(java.util.Calendar.HOUR_OF_DAY, CHECK_HOUR)
                set(java.util.Calendar.MINUTE, CHECK_MINUTE)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
                // 如果已过今天的触发时间，设置为明天
                if (timeInMillis <= now.timeInMillis) {
                    add(java.util.Calendar.DAY_OF_MONTH, 1)
                }
            }

            val delayMs = target.timeInMillis - now.timeInMillis

            val request = OneTimeWorkRequestBuilder<IncompleteTaskCheckWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    request
                )

            Log.i(TAG, "已调度每日未完成任务检查，触发时间: " +
                java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA).format(target.time))
        }

        /**
         * 立即触发一次检查（用于测试或手动调用）
         */
        fun triggerNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<IncompleteTaskCheckWorker>()
                .addTag(WORK_NAME)
                .build()
            WorkManager.getInstance(context).enqueue(request)
            Log.i(TAG, "已立即触发未完成任务检查")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "开始检查未完成任务")

            val app = applicationContext as TaskCheckInApp
            val repository = app.taskRepository

            // 获取所有未完成的每日任务
            val dailyIncomplete = repository.getDailyTasksSync().filter { !it.isCompleted }

            // 获取所有未完成的仅今日任务
            val todayOnlyIncomplete = repository.getAllTasksSync().filter {
                it.taskType == TASK_TYPE_TODAY && !it.isCompleted
            }

            // 获取今日未完成的日程任务
            val today = repository.normalizeDate(System.currentTimeMillis())
            val todayCal = java.util.Calendar.getInstance(java.util.Locale.CHINA)
            todayCal.timeInMillis = today

            // 获取今日未完成的日程任务（从数据库直接查询）
            val db = (applicationContext as TaskCheckInApp).database
            val todayScheduled = db.taskDao()
                .getTasksByDateSync(TASK_TYPE_SCHEDULED, today)
                .filter { !it.isCompleted }

            // 合并三类任务
            val incompleteTasks: List<TaskEntity> = dailyIncomplete + todayOnlyIncomplete + todayScheduled

            if (incompleteTasks.isEmpty()) {
                Log.i(TAG, "没有未完成任务，跳过提醒")
                // 仍然调度明天的检查
                scheduleAgain()
                return@withContext Result.success()
            }

            // 过滤掉今日已被用户忽略的任务
            val effectiveTasks = incompleteTasks.filter { task ->
                !IncompleteTaskReminder.isTaskDismissedToday(applicationContext, task.id)
            }

            if (effectiveTasks.isEmpty()) {
                Log.i(TAG, "所有未完成任务已被用户忽略，跳过提醒")
                scheduleAgain()
                return@withContext Result.success()
            }

            val taskNames: List<String> = effectiveTasks.map { it.title }
            val taskIds: LongArray = effectiveTasks.map { it.id }.toLongArray()
            val taskCount: Int = effectiveTasks.size

            // 构建两个按钮的 PendingIntent
            val remindLaterIntent = Intent(applicationContext, ReminderActionReceiver::class.java).apply {
                action = ReminderActionReceiver.ACTION_REMIND_LATER
                putExtra(ReminderActionReceiver.EXTRA_TASK_IDS, taskIds)
                putExtra(ReminderActionReceiver.EXTRA_TASK_COUNT, taskCount)
                putExtra(ReminderActionReceiver.EXTRA_TASK_NAMES, taskNames.toTypedArray())
            }
            val remindLaterPending = android.app.PendingIntent.getBroadcast(
                applicationContext,
                9002,
                remindLaterIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val dismissIntent = Intent(applicationContext, ReminderActionReceiver::class.java).apply {
                action = ReminderActionReceiver.ACTION_DISMISS
                putExtra(ReminderActionReceiver.EXTRA_TASK_IDS, taskIds)
                putExtra(ReminderActionReceiver.EXTRA_TASK_COUNT, taskCount)
                putExtra(ReminderActionReceiver.EXTRA_TASK_NAMES, taskNames.toTypedArray())
            }
            val dismissPending = android.app.PendingIntent.getBroadcast(
                applicationContext,
                9003,
                dismissIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            IncompleteTaskReminder.sendReminder(
                context = applicationContext,
                taskCount = taskCount,
                taskNames = taskNames,
                onRemindLater = remindLaterPending,
                onDismiss = dismissPending
            )

            Log.i(TAG, "已发送未完成任务提醒，共 $taskCount 个任务")

            // 调度明天的检查
            scheduleAgain()

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "检查失败: ${e.message}")
            scheduleAgain()
            Result.retry()
        }
    }

    private fun scheduleAgain() {
        schedule(applicationContext)
    }
}