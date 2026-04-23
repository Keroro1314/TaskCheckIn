package com.taskcheckin.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.taskcheckin.R
import com.taskcheckin.data.local.AppDatabase
import com.taskcheckin.data.local.TaskEntity
import com.taskcheckin.data.local.TASK_TYPE_SCHEDULED
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 小组件 Factory — 日程任务（展示日期）
 */
class ScheduledTaskWidgetFactory(
    private val context: Context,
    private val intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private var tasks: List<TaskEntity> = emptyList()
    private val today = normalizeDate(System.currentTimeMillis())

    override fun onCreate() {}

    override fun onDataSetChanged() {
        try {
            val db = AppDatabase.getInstance(context)
            // 只取未来未完成的日程任务
            tasks = runBlocking {
                db.taskDao().getUpcomingScheduledTasksSync(TASK_TYPE_SCHEDULED, today)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            tasks = emptyList()
        }
    }

    override fun getCount(): Int = tasks.size

    override fun getViewAt(position: Int): RemoteViews {
        val task = tasks[position]
        val views = RemoteViews(context.packageName, R.layout.task_widget_scheduled_item)

        views.setTextViewText(R.id.tvTaskTitle, task.title)
        views.setTextViewText(R.id.tvDateTime, formatDateTime(task.dueDate, task.reminderTime))
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

    /** 格式化日期时间显示 */
    private fun formatDateTime(dueDate: Long, reminderTime: Long): String {
        if (dueDate == 0L) return ""
        val cal = Calendar.getInstance(Locale.CHINA).apply { timeInMillis = dueDate }
        val todayCal = Calendar.getInstance(Locale.CHINA).apply {
            timeInMillis = today
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val tomorrowCal = (todayCal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }

        val dateLabel = when {
            cal.timeInMillis < todayCal.timeInMillis -> "逾期"
            cal.timeInMillis >= todayCal.timeInMillis && cal.timeInMillis < tomorrowCal.timeInMillis -> "今"
            (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis < tomorrowCal.timeInMillis -> "明"
            else -> SimpleDateFormat("M/d", Locale.CHINA).format(Date(dueDate))
        }

        val timeLabel = if (reminderTime > 0) {
            SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(reminderTime))
        } else ""

        return if (timeLabel.isNotEmpty()) "$dateLabel $timeLabel" else dateLabel
    }

    /** 归一化到当天 0 点 */
    private fun normalizeDate(timestamp: Long): Long {
        return Calendar.getInstance(Locale.CHINA).apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = tasks.getOrNull(position)?.id ?: position.toLong()
    override fun hasStableIds(): Boolean = true
    override fun onDestroy() { tasks = emptyList() }
}
