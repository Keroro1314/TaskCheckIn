package com.taskcheckin.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.taskcheckin.R
import com.taskcheckin.ui.main.MainActivity

/**
 * IncompleteTaskReminder — 今日未完成任务提醒功能
 * 每天检查一次，向用户推送未完成任务通知，提供「稍后提醒」和「已知晓」两个选项。
 */
object IncompleteTaskReminder {

    private const val PREFS_NAME = "incomplete_reminder_prefs"
    private const val KEY_DISMISSED_TASKS = "dismissed_task_ids"
    private const val KEY_DISMISSED_DATE = "dismissed_date"

    const val CHANNEL_ID = "incomplete_reminder"
    const val CHANNEL_NAME = "未完成任务提醒"
    const val CHANNEL_DESCRIPTION = "每日晚间提醒您完成当天未完成的任务"

    // 通知 ID（避免与日程提醒冲突）
    const val NOTIFICATION_ID = 8888

    /**
     * 发送未完成任务提醒通知
     * @param context
     * @param taskCount 未完成任务数量
     * @param taskNames 未完成任务名称列表（取前3个，多余的显示"等"）
     * @param onRemindLater 点击「请稍后提醒」跳转的 PendingIntent
     * @param onDismiss 点击「我已知晓」跳转的 PendingIntent
     */
    fun sendReminder(
        context: Context,
        taskCount: Int,
        taskNames: List<String>,
        onRemindLater: PendingIntent,
        onDismiss: PendingIntent
    ) {
        ensureNotificationChannel(context)

        val displayNames = taskNames.take(3)
        val contentText = if (taskCount > 3) {
            "「${displayNames.joinToString("」「")}」等 $taskCount 个任务"
        } else {
            "「${displayNames.joinToString("」「")}」共 $taskCount 个任务"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("📌 您今日还有任务未完成")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            // 稍后提醒按钮
            .addAction(0, "请稍后提醒", onRemindLater)
            // 我已知晓按钮
            .addAction(0, "我已知晓", onDismiss)

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            // 无通知权限，静默忽略
        }
    }

    /**
     * 取消今日提醒通知
     */
    fun cancelReminder(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /**
     * 标记某个任务在今日不再提醒
     */
    fun dismissTaskForToday(context: Context, taskId: Long) {
        val prefs = getPrefs(context)
        val today = getTodayKey()
        val dateKey = prefs.getString(KEY_DISMISSED_DATE, "") ?: ""

        val dismissedIds = if (dateKey == today) {
            // 今日已有记录，追加
            (prefs.getStringSet(KEY_DISMISSED_TASKS, emptySet()) ?: emptySet()).toMutableSet()
        } else {
            // 新的一天，清空旧记录
            mutableSetOf()
        }
        dismissedIds.add(taskId.toString())

        prefs.edit()
            .putString(KEY_DISMISSED_DATE, today)
            .putStringSet(KEY_DISMISSED_TASKS, dismissedIds)
            .apply()
    }

    /**
     * 检查某个任务今日是否已被忽略
     */
    fun isTaskDismissedToday(context: Context, taskId: Long): Boolean {
        val prefs = getPrefs(context)
        val today = getTodayKey()
        val dateKey = prefs.getString(KEY_DISMISSED_DATE, "") ?: ""
        if (dateKey != today) return false

        val dismissedIds = prefs.getStringSet(KEY_DISMISSED_TASKS, emptySet()) ?: emptySet()
        return dismissedIds.contains(taskId.toString())
    }

    /**
     * 重置今日忽略记录（每天0点由系统自动清理）
     */
    fun clearTodayDismissals(context: Context) {
        getPrefs(context).edit()
            .putString(KEY_DISMISSED_DATE, "")
            .putStringSet(KEY_DISMISSED_TASKS, emptySet())
            .apply()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getTodayKey(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
        return sdf.format(java.util.Date())
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}