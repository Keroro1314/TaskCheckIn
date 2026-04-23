package com.taskcheckin.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.taskcheckin.R
import com.taskcheckin.data.local.AppDatabase
import com.taskcheckin.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * AlarmReceiver — 接收闹钟触发广播，发送通知
 * 
 * Android 8.0+ 需要先创建通知渠道才能发送通知。
 * 通知点击后直接打开 App。
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "AlarmReceiver"
        const val CHANNEL_ID = "schedule_reminder"
        const val CHANNEL_NAME = "日程提醒"
        const val CHANNEL_DESCRIPTION = "日程任务的定时提醒通知"
        const val CHANNEL_IMPORTANCE = android.app.NotificationManager.IMPORTANCE_HIGH
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_TASK_REMINDER) return

        val taskId = intent.getLongExtra(AlarmScheduler.EXTRA_TASK_ID, -1)
        if (taskId == -1L) {
            Log.w(TAG, "无效的 taskId")
            return
        }

        Log.i(TAG, "闹钟触发: taskId=$taskId")

        // 在后台协程中查询数据库并发送通知
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getInstance(context)
            val task = db.taskDao().getTaskById(taskId)

            if (task == null) {
                Log.w(TAG, "任务不存在: $taskId")
                return@launch
            }

            // 如果任务已完成（用户在提醒前手动打勾了），不发送通知
            if (task.isCompleted) {
                Log.i(TAG, "任务已完成，跳过通知: ${task.title}")
                return@launch
            }

            // 如果已经触发过（防重复），跳过
            if (task.reminderFired) {
                Log.i(TAG, "提醒已触发过，跳过: ${task.title}")
                return@launch
            }

            // 标记为已触发
            db.taskDao().markReminderFired(taskId)

            // 发送通知
            sendNotification(context, task.id, task.title, task.reminderTime)

            // 如果是重复任务，注册下一次闹钟
            if (task.repeatMode != com.taskcheckin.data.local.REPEAT_NONE) {
                val nextTime = AlarmScheduler.computeNextTriggerTime(task.reminderTime, task.repeatMode)
                if (nextTime > 0) {
                    // 创建下一次任务（复用同一 ID，重新设置时间）
                    val nextTask = task.copy(reminderTime = nextTime, reminderFired = false)
                    db.taskDao().updateTask(nextTask)
                    AlarmScheduler.scheduleTask(context, nextTask)
                }
            }
        }
    }

    private fun sendNotification(context: Context, taskId: Long, title: String, reminderTime: Long) {
        // 确保通知渠道存在（Android 8.0+）
        ensureNotificationChannel(context)

        // 点击通知后打开 App 并定位到对应任务
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("highlight_task_id", taskId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            taskId.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 格式化提醒时间
        val timeStr = formatReminderTime(reminderTime)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // 注意：需要创建通知图标
            .setContentTitle("📅 $title")
            .setContentText("提醒时间：$timeStr")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(taskId.toInt(), notification)
            Log.i(TAG, "通知已发送: $title")
        } catch (e: SecurityException) {
            Log.e(TAG, "通知权限被拒，请检查通知权限: ${e.message}")
        }
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                CHANNEL_IMPORTANCE
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun formatReminderTime(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("MM月dd日 HH:mm", java.util.Locale.CHINA)
        return sdf.format(java.util.Date(timestamp))
    }
}
