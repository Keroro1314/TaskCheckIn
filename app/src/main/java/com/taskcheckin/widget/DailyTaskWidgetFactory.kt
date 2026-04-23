package com.taskcheckin.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.taskcheckin.R
import com.taskcheckin.data.local.AppDatabase
import com.taskcheckin.data.local.TaskEntity
import com.taskcheckin.data.local.TASK_TYPE_DAILY
import java.util.Calendar
import java.util.Locale

/**
 * 小组件 Factory — 每日任务
 */
class DailyTaskWidgetFactory(
    private val context: Context,
    private val intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private var tasks: List<TaskEntity> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        try {
            val db = AppDatabase.getInstance(context)
            tasks = db.taskDao()
                .getIncompleteTasksByTypeSync(TASK_TYPE_DAILY)
                .filter { it.isCompleted == false }
        } catch (e: Exception) {
            e.printStackTrace()
            tasks = emptyList()
        }
    }

    override fun getCount(): Int = tasks.size

    override fun getViewAt(position: Int): RemoteViews {
        val task = tasks[position]
        val views = RemoteViews(context.packageName, R.layout.task_widget_item)

        views.setTextViewText(R.id.tvTaskTitle, task.title)
        views.setImageViewResource(
            R.id.ivCheckbox,
            if (task.isCompleted) R.drawable.ic_widget_checked else R.drawable.ic_widget_unchecked
        )

        val fillIntent = Intent().apply {
            putExtra(TaskWidgetProvider.EXTRA_TASK_ID, task.id)
        }
        views.setOnClickFillInIntent(R.id.ivCheckbox, fillIntent)
        views.setOnClickFillInIntent(R.id.tvTaskTitle, fillIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = tasks.getOrNull(position)?.id ?: position.toLong()
    override fun hasStableIds(): Boolean = true
    override fun onDestroy() { tasks = emptyList() }
}
