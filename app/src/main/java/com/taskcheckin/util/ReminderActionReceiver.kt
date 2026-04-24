package com.taskcheckin.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * ReminderActionReceiver — 处理通知栏「请稍后提醒」和「我已知晓」按钮点击
 */
class ReminderActionReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "ReminderActionReceiver"
        const val ACTION_REMIND_LATER = "com.taskcheckin.ACTION_REMIND_LATER"
        const val ACTION_DISMISS = "com.taskcheckin.ACTION_DISMISS"
        const val EXTRA_TASK_IDS = "task_ids"
        const val EXTRA_TASK_COUNT = "task_count"
        const val EXTRA_TASK_NAMES = "task_names"

        // 延迟提醒的时间（15分钟）
        private const val REMIND_DELAY_MS = 15 * 60 * 1000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REMIND_LATER -> {
                Log.i(TAG, "用户点击「请稍后提醒」")
                handleRemindLater(context, intent)
            }
            ACTION_DISMISS -> {
                Log.i(TAG, "用户点击「我已知晓」")
                handleDismiss(context, intent)
            }
        }
    }

    private fun handleRemindLater(context: Context, intent: Intent) {
        // 取消当前通知
        IncompleteTaskReminder.cancelReminder(context)

        // 设置 15 分钟后重新提醒
        scheduleRemindLater(context, intent)
    }

    private fun handleDismiss(context: Context, intent: Intent) {
        // 取消当前通知
        IncompleteTaskReminder.cancelReminder(context)

        // 将所有任务标记为今日已知晓
        val taskIds = intent.getLongArrayExtra(EXTRA_TASK_IDS) ?: longArrayOf()
        taskIds.forEach { taskId ->
            IncompleteTaskReminder.dismissTaskForToday(context, taskId)
        }
    }

    private fun scheduleRemindLater(context: Context, intent: Intent) {
        // 使用 AlarmManager 设置 15 分钟后重新发送通知
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager

        val taskIds = intent.getLongArrayExtra(EXTRA_TASK_IDS) ?: longArrayOf()
        val taskCount = intent.getIntExtra(EXTRA_TASK_COUNT, 0)
        val taskNames = intent.getStringArrayExtra(EXTRA_TASK_NAMES) ?: emptyArray()

        val pendingIntent = ReminderLaterReceiver.createPendingIntent(
            context,
            taskIds,
            taskCount,
            taskNames
        )

        val triggerAt = System.currentTimeMillis() + REMIND_DELAY_MS

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    android.app.AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            }
            Log.i(TAG, "已设置 ${REMIND_DELAY_MS / 60000} 分钟后提醒")
        } catch (e: SecurityException) {
            Log.e(TAG, "无法设置精确闹钟: ${e.message}")
        }
    }
}