package com.taskcheckin.ui.calendar

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.taskcheckin.R
import com.taskcheckin.data.local.TaskEntity
import com.taskcheckin.data.local.TASK_TYPE_DAILY
import java.text.SimpleDateFormat
import java.util.*

/**
 * ScheduledTaskAdapter — 日历视图中显示当日任务的适配器
 * 每日任务显示为「🔄 任务名」，日程任务显示为「📅 日期时间 任务名」
 */
class ScheduledTaskAdapter(
    private val onToggle: (Long, Boolean) -> Unit,
    private val onDelete: (TaskEntity) -> Unit
) : ListAdapter<TaskEntity, ScheduledTaskAdapter.ViewHolder>(DiffCallback()) {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.CHINA)
    private val dateFormat = SimpleDateFormat("M月d日", Locale.CHINA)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_scheduled_task, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val checkBox: CheckBox = itemView.findViewById(R.id.checkBox)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvSchedule: TextView = itemView.findViewById(R.id.tvSchedule)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)

        fun bind(task: TaskEntity) {
            checkBox.setOnCheckedChangeListener(null)
            checkBox.isChecked = task.isCompleted

            checkBox.setOnCheckedChangeListener { _, isChecked ->
                onToggle(task.id, isChecked)
            }

            // 任务标题
            tvTitle.text = task.title

            // 完成状态样式
            if (task.isCompleted) {
                tvTitle.paintFlags = tvTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                tvTitle.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_completed))
                itemView.alpha = 0.7f
            } else {
                tvTitle.paintFlags = tvTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                tvTitle.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_normal))
                itemView.alpha = 1.0f
            }

            // 任务类型标签
            if (task.taskType == TASK_TYPE_DAILY) {
                tvSchedule.visibility = View.VISIBLE
                tvSchedule.text = "🔄 每日任务"
                tvSchedule.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_secondary))
            } else {
                tvSchedule.visibility = View.VISIBLE
                val dateStr = dateFormat.format(Date(task.dueDate))
                val timeStr = if (task.reminderTime > 0) timeFormat.format(Date(task.reminderTime)) else ""
                tvSchedule.text = if (timeStr.isNotEmpty()) "📅 $dateStr $timeStr" else "📅 $dateStr"
                tvSchedule.setTextColor(ContextCompat.getColor(itemView.context, R.color.colorPrimary))
            }

            btnDelete.setOnClickListener {
                onDelete(task)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<TaskEntity>() {
        override fun areItemsTheSame(oldItem: TaskEntity, newItem: TaskEntity) =
            oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: TaskEntity, newItem: TaskEntity) =
            oldItem == newItem
    }
}
