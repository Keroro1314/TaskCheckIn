package com.taskcheckin.util

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * ReminderLaterReceiver — 15分钟后重新发送未完成任务提醒
 */
class ReminderLaterReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "ReminderLaterReceiver"

        private const val KEY_TASK_IDS = "task_ids"
        private const val KEY_TASK_COUNT = "task_count"
        private const val KEY_TASK_NAMES = "task_names"

        /**
         * 创建用于发送延迟提醒通知的 PendingIntent
         */
        fun createPendingIntent(
            context: Context,
            taskIds: LongArray,
            taskCount: Int,
            taskNames: Array<String>
        ): android.app.PendingIntent {
            val intent = Intent(context, ReminderLaterReceiver::class.java).apply {
                putExtra(KEY_TASK_IDS, taskIds)
                putExtra(KEY_TASK_COUNT, taskCount)
                putExtra(KEY_TASK_NAMES, taskNames)
            }
            return android.app.PendingIntent.getBroadcast(
                context,
                9001, // 固定 requestCode，避免重复
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskIds = intent.getLongArrayExtra(KEY_TASK_IDS) ?: longArrayOf()
        val taskCount = intent.getIntExtra(KEY_TASK_COUNT, 0)
        val taskNames = intent.getStringArrayExtra(KEY_TASK_NAMES) ?: emptyArray()

        Log.i(TAG, "延迟提醒触发，任务数: $taskCount")

        if (taskCount == 0 || taskNames.isEmpty()) {
            Log.w(TAG, "任务数据为空，跳过")
            return
        }

        // 构建「请稍后提醒」和「我已知晓」按钮的 PendingIntent
        val remindLaterIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_REMIND_LATER
            putExtra(ReminderActionReceiver.EXTRA_TASK_IDS, taskIds)
            putExtra(ReminderActionReceiver.EXTRA_TASK_COUNT, taskCount)
            putExtra(ReminderActionReceiver.EXTRA_TASK_NAMES, taskNames)
        }
        val remindLaterPending = android.app.PendingIntent.getBroadcast(
            context,
            9002,
            remindLaterIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_DISMISS
            putExtra(ReminderActionReceiver.EXTRA_TASK_IDS, taskIds)
            putExtra(ReminderActionReceiver.EXTRA_TASK_COUNT, taskCount)
            putExtra(ReminderActionReceiver.EXTRA_TASK_NAMES, taskNames)
        }
        val dismissPending = android.app.PendingIntent.getBroadcast(
            context,
            9003,
            dismissIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        IncompleteTaskReminder.sendReminder(
            context = context,
            taskCount = taskCount,
            taskNames = taskNames.toList(),
            onRemindLater = remindLaterPending,
            onDismiss = dismissPending
        )
    }
}