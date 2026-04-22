package com.taskcheckin.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.taskcheckin.R
import com.taskcheckin.data.local.AppDatabase
import com.taskcheckin.data.local.TaskEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
                            // Toggle completion
                            db.taskDao().updateCompletion(taskId, !task.isCompleted)
                            if (!task.isCompleted) {
                                db.taskHistoryDao().insertHistory(
                                    com.taskcheckin.data.local.TaskHistoryEntity(title = task.title)
                                )
                            }
                            // Refresh widget
                            updateAllWidgets(context)
                        }
                    }
                }
            }
        }
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.task_widget)

        // Set up list with service
        val serviceIntent = Intent(context, TaskWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        views.setRemoteAdapter(R.id.taskList, serviceIntent)
        views.setEmptyView(R.id.taskList, R.id.tvEmpty)

        // Set up click intent for list items
        val checkTemplate = Intent(context, TaskWidgetProvider::class.java).apply {
            action = ACTION_CHECK_TASK
        }
        val checkPendingIntent = PendingIntent.getBroadcast(
            context, 0, checkTemplate,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        views.setPendingIntentTemplate(R.id.taskList, checkPendingIntent)

        // Open app on header click
        val appIntent = Intent(context, com.taskcheckin.ui.main.MainActivity::class.java)
        val appPendingIntent = PendingIntent.getActivity(
            context, 0, appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.tvPendingCount, appPendingIntent)

        manager.updateAppWidget(widgetId, views)

        // 触发列表数据刷新
        manager.notifyAppWidgetViewDataChanged(widgetId, R.id.taskList)
    }
}
