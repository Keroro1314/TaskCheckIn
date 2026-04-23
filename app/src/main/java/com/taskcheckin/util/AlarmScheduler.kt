package com.taskcheckin.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.taskcheckin.data.local.TaskEntity

/**
 * AlarmScheduler — 管理日程任务的精确闹钟调度
 * 
 * 使用 AlarmManager.setExactAndAllowWhileIdle 保证即使在省电模式
 * 下也能准时触发通知。同时处理重复任务的下一次闹钟注册。
 */
object AlarmScheduler {

    private const val TAG = "AlarmScheduler"
    const val ACTION_TASK_REMINDER = "com.taskcheckin.ACTION_TASK_REMINDER"
    const val EXTRA_TASK_ID = "task_id"

    /**
     * 为单个日程任务注册闹钟
     */
    fun scheduleTask(context: Context, task: TaskEntity) {
        if (task.reminderTime <= System.currentTimeMillis()) {
            Log.w(TAG, "跳过已过期提醒: ${task.title}, 时间: ${task.reminderTime}")
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // 构建 PendingIntent（点击通知后打开 App）
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_TASK_REMINDER
            putExtra(EXTRA_TASK_ID, task.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.alarmRequestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            // Android 12+ 需要检查是否有 SCHEDULE_EXACT_ALARM 权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    Log.w(TAG, "没有精确闹钟权限，引导用户去设置")
                    return
                }
            }

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                task.reminderTime,
                pendingIntent
            )
            Log.i(TAG, "已注册闹钟: ${task.title}, 时间: ${java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA).format(java.util.Date(task.reminderTime))}, requestCode: ${task.alarmRequestCode}")
        } catch (e: SecurityException) {
            Log.e(TAG, "精确闹钟权限被拒: ${e.message}")
        }
    }

    /**
     * 取消某个任务的闹钟
     */
    fun cancelTask(context: Context, task: TaskEntity) {
        if (task.alarmRequestCode == 0) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_TASK_REMINDER
            putExtra(EXTRA_TASK_ID, task.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.alarmRequestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        Log.i(TAG, "已取消闹钟: ${task.title}, requestCode: ${task.alarmRequestCode}")
    }

    /**
     * 任务完成时：取消闹钟 + 处理重复规则（注册下一次）
     * @return 是否有下一次闹钟被注册
     */
    fun onTaskCompleted(context: Context, task: TaskEntity, nextTaskId: Long?) {
        cancelTask(context, task)

        // 如果是重复任务，创建下一次的任务
        if (task.repeatMode != com.taskcheckin.data.local.REPEAT_NONE && nextTaskId != null && nextTaskId > 0) {
            Log.i(TAG, "重复任务完成，注册下一次: ${task.repeatMode}")
            // 下一次任务的闹钟注册由调用方负责
        }
    }

    /**
     * 检查设备是否支持精确闹钟（国产 ROM 可能禁用）
     */
    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            // 使用反射调用，避免 minSdk 24 设备编译时找不到方法
            return try {
                val method = AlarmManager::class.java.getMethod("canScheduleExactAlarms")
                method.invoke(alarmManager) as Boolean
            } catch (e: Exception) {
                true
            }
        }
        return true
    }

    /**
     * 跳转到精确闹钟权限设置页面（Android 12+）
     */
    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
    }

    /**
     * 为重复任务计算下一次触发时间
     * @return 下一次提醒时间戳（毫秒），或 0 表示不重复
     */
    fun computeNextTriggerTime(currentReminderTime: Long, repeatMode: String): Long {
        val cal = java.util.Calendar.getInstance(java.util.Locale.CHINA).apply {
            timeInMillis = currentReminderTime
        }

        when (repeatMode) {
            com.taskcheckin.data.local.REPEAT_DAILY -> cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
            com.taskcheckin.data.local.REPEAT_WEEKLY -> cal.add(java.util.Calendar.WEEK_OF_YEAR, 1)
            com.taskcheckin.data.local.REPEAT_MONTHLY -> cal.add(java.util.Calendar.MONTH, 1)
            else -> return 0L
        }

        return cal.timeInMillis
    }
}
