package com.taskcheckin.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.widget.RemoteViews
import com.taskcheckin.R
import com.taskcheckin.data.local.AppDatabase
import com.taskcheckin.data.local.TASK_TYPE_DAILY
import com.taskcheckin.data.local.TASK_TYPE_SCHEDULED
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

class TaskWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_CHECK_TASK = "com.taskcheckin.ACTION_CHECK_TASK"
        const val EXTRA_TASK_ID = "task_id"

        fun updateAllWidgets(context: Context) {
            val ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, TaskWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                val intent = Intent(context, TaskWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            updateWidget(context, manager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_CHECK_TASK -> {
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1)
                if (taskId != -1L) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val db = AppDatabase.getInstance(context)
                        val task = db.taskDao().getTaskById(taskId)
                        if (task != null) {
                            db.taskDao().updateCompletion(taskId, !task.isCompleted)
                            if (!task.isCompleted) {
                                db.taskHistoryDao().insertHistory(
                                    com.taskcheckin.data.local.TaskHistoryEntity(title = task.title)
                                )
                            }
                            updateAllWidgets(context)
                        }
                    }
                }
            }
        }
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.task_widget)

        // ---- 今日待办：每日任务列表 ----
        val dailyIntent = Intent(context, DailyTaskWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        views.setRemoteAdapter(R.id.dailyTaskList, dailyIntent)
        views.setEmptyView(R.id.dailyTaskList, R.id.tvDailyEmpty)

        // ---- 未来日程：日程任务列表 ----
        val scheduledIntent = Intent(context, ScheduledTaskWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        views.setRemoteAdapter(R.id.scheduledTaskList, scheduledIntent)
        views.setEmptyView(R.id.scheduledTaskList, R.id.tvScheduledEmpty)

        // ---- 点击模板（复选框勾选）----
        val checkTemplate = Intent(context, TaskWidgetProvider::class.java).apply {
            action = ACTION_CHECK_TASK
        }
        val checkPendingIntent = PendingIntent.getBroadcast(
            context, 0, checkTemplate,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        views.setPendingIntentTemplate(R.id.dailyTaskList, checkPendingIntent)
        views.setPendingIntentTemplate(R.id.scheduledTaskList, checkPendingIntent)

        // ---- Header 点击打开 App ----
        val appIntent = Intent(context, com.taskcheckin.ui.main.MainActivity::class.java)
        val appPendingIntent = PendingIntent.getActivity(
            context, 0, appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.tvPendingCount, appPendingIntent)

        // ---- 更新待办数量 ----
        try {
            val db = AppDatabase.getInstance(context)
            val today = normalizeDate(System.currentTimeMillis())
            val pendingCount = runBlocking {
                db.taskDao().getIncompleteTasksByTypeSync(TASK_TYPE_DAILY).size +
                    db.taskDao().getUpcomingScheduledTasksSync(TASK_TYPE_SCHEDULED, today).size
            }
            views.setTextViewText(R.id.tvPendingCount, "待办 $pendingCount")
        } catch (e: Exception) {
            views.setTextViewText(R.id.tvPendingCount, "")
        }

        manager.updateAppWidget(widgetId, views)

        // 触发两个列表数据刷新
        manager.notifyAppWidgetViewDataChanged(widgetId, R.id.dailyTaskList)
        manager.notifyAppWidgetViewDataChanged(widgetId, R.id.scheduledTaskList)
    }

    private fun normalizeDate(timestamp: Long): Long {
        return Calendar.getInstance(Locale.CHINA).apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
