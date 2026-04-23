package com.taskcheckin.ui.main

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.view.inputmethod.EditorInfo
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.taskcheckin.R
import com.taskcheckin.data.local.TaskEntity
import com.taskcheckin.data.local.TASK_TYPE_DAILY
import com.taskcheckin.data.local.TASK_TYPE_SCHEDULED
import java.text.SimpleDateFormat
import java.util.*

/**
 * 分组任务项类型
 */
sealed class TaskListItem {
    data class SectionHeader(val title: String) : TaskListItem()
    data class Task(val task: TaskEntity, val isHighlighted: Boolean = false) : TaskListItem()
}

class TaskAdapter(
    private var editingTaskId: Long?,
    private val onToggle: (Long, Boolean) -> Unit,
    private val onTitleChanged: (Long, String) -> Unit,
    private val onDelete: (TaskEntity) -> Unit,
    private val onReorder: (Int, Int) -> Unit,
    private val onStartEditing: (Long) -> Unit,
    private val onStopEditing: () -> Unit,
    private val onHighlight: (Long) -> Unit
) : ListAdapter<TaskListItem, RecyclerView.ViewHolder>(TaskDiffCallback()) {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_TASK = 1
    }

    private var highlightTaskId: Long? = null
    private val dateFormat = SimpleDateFormat("M月d日", Locale.CHINA)
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.CHINA)

    fun setHighlightTaskId(id: Long?) {
        highlightTaskId = id
        // 找到对应位置并刷新
        currentList.forEachIndexed { index, item ->
            if (item is TaskListItem.Task && item.task.id == id) {
                notifyItemChanged(index)
            }
        }
    }

    // 供外部更新编辑状态（不重建 adapter）
    fun setEditingTaskId(id: Long?) {
        val oldId = editingTaskId
        editingTaskId = id
        if (oldId != id) {
            // 找出受影响的位置，只刷新这两个
            currentList.forEachIndexed { index, item ->
                if (item is TaskListItem.Task && (item.task.id == oldId || item.task.id == id)) {
                    notifyItemChanged(index)
                }
            }
        }
    }

    fun submitGroupedList(
        dailyTasks: List<TaskEntity>,
        futureTasks: List<TaskEntity>,
        showFuture: Boolean
    ) {
        val items = mutableListOf<TaskListItem>()

        if (dailyTasks.isNotEmpty()) {
            items.add(TaskListItem.SectionHeader("📌 今日待办"))
            dailyTasks.forEach { task ->
                items.add(TaskListItem.Task(task, task.id == highlightTaskId))
            }
        }

        if (futureTasks.isNotEmpty()) {
            items.add(TaskListItem.SectionHeader("📅 未来日程"))
            futureTasks.forEach { task ->
                items.add(TaskListItem.Task(task, task.id == highlightTaskId))
            }
        }

        if (dailyTasks.isEmpty() && futureTasks.isEmpty()) {
            items.add(TaskListItem.SectionHeader("✨ 暂无任务"))
        }

        submitList(items)
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is TaskListItem.SectionHeader -> TYPE_HEADER
            is TaskListItem.Task -> TYPE_TASK
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_task_header, parent, false)
                SectionHeaderViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_task, parent, false)
                TaskViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is TaskListItem.SectionHeader -> (holder as SectionHeaderViewHolder).bind(item)
            is TaskListItem.Task -> (holder as TaskViewHolder).bind(item.task, item.task.id == editingTaskId, item.isHighlighted)
        }
    }

    // ===== 分组标题 =====
    class SectionHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvSectionTitle)

        fun bind(header: TaskListItem.SectionHeader) {
            tvTitle.text = header.title
        }
    }

    // ===== 任务项 =====
    inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val checkBox: CheckBox = itemView.findViewById(R.id.checkBox)
        private val titleEditText: EditText = itemView.findViewById(R.id.titleEditText)
        private val titleTextView: TextView = itemView.findViewById(R.id.titleTextView)
        private val tvSchedule: TextView = itemView.findViewById(R.id.tvSchedule)
        private val dragHandle: ImageButton = itemView.findViewById(R.id.dragHandle)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)

        fun bind(task: TaskEntity, isEditing: Boolean, isHighlighted: Boolean) {
            checkBox.setOnCheckedChangeListener(null)
            titleEditText.setOnEditorActionListener(null)
            titleEditText.onFocusChangeListener = null
            titleTextView.setOnClickListener(null)
            deleteButton.setOnClickListener(null)

            checkBox.isChecked = task.isCompleted

            // 高亮背景
            if (isHighlighted) {
                itemView.setBackgroundColor(
                    ContextCompat.getColor(itemView.context, R.color.highlight_yellow)
                )
            } else {
                itemView.setBackgroundColor(
                    ContextCompat.getColor(itemView.context, android.R.color.transparent)
                )
            }

            checkBox.setOnCheckedChangeListener { _, isChecked ->
                itemView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    val listItem = getItem(pos) as? TaskListItem.Task ?: return@setOnCheckedChangeListener
                    if (editingTaskId != null) onStopEditing()
                    onToggle(listItem.task.id, isChecked)
                }
            }

            // 每日任务正常透明度，日程任务已完成时半透明
            itemView.alpha = if (task.taskType == TASK_TYPE_DAILY) {
                if (task.isCompleted) 0.7f else 1.0f
            } else {
                if (task.isCompleted) 0.6f else 1.0f
            }

            // 日程任务显示时间和重复标记
            if (task.taskType == TASK_TYPE_SCHEDULED) {
                tvSchedule.visibility = View.VISIBLE
                val dateStr = if (task.dueDate > 0) dateFormat.format(Date(task.dueDate)) else ""
                val timeStr = if (task.reminderTime > 0) timeFormat.format(Date(task.reminderTime)) else ""
                val repeatIcon = when (task.repeatMode) {
                    "DAILY" -> " 🔄每天"
                    "WEEKLY" -> " 🔄每周"
                    "MONTHLY" -> " 🔄每月"
                    else -> ""
                }
                tvSchedule.text = "📅 $dateStr $timeStr$repeatIcon"
                tvSchedule.setTextColor(
                    ContextCompat.getColor(itemView.context,
                        if (task.isCompleted) R.color.text_completed else R.color.colorPrimary)
                )
            } else {
                tvSchedule.visibility = View.GONE
            }

            if (isEditing) {
                titleTextView.visibility = View.GONE
                titleEditText.visibility = View.VISIBLE
                titleEditText.setText(task.title)
                titleEditText.requestFocus()
                titleEditText.setSelection(titleEditText.text.length)

                titleEditText.setOnEditorActionListener { v, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_NULL) {
                        titleEditText.onFocusChangeListener = null
                        onTitleChanged(task.id, v.text.toString())
                        onStopEditing()
                        true
                    } else false
                }

                titleEditText.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) {
                        titleEditText.onFocusChangeListener = null
                        onTitleChanged(task.id, titleEditText.text.toString())
                        onStopEditing()
                    }
                }
            } else {
                titleEditText.onFocusChangeListener = null
                titleEditText.visibility = View.GONE
                titleTextView.visibility = View.VISIBLE
                titleTextView.text = task.title

                if (task.isCompleted) {
                    titleTextView.paintFlags = titleTextView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    titleTextView.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_completed))
                } else {
                    titleTextView.paintFlags = titleTextView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    titleTextView.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_normal))
                }

                titleTextView.setOnClickListener {
                    val pos = bindingAdapterPosition
                    if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                    if (task.isCompleted) {
                        onToggle(task.id, false)
                    } else {
                        onStartEditing(task.id)
                    }
                }
            }

            deleteButton.setOnClickListener {
                onDelete(task)
            }
        }
    }

    class TaskDiffCallback : DiffUtil.ItemCallback<TaskListItem>() {
        override fun areItemsTheSame(oldItem: TaskListItem, newItem: TaskListItem): Boolean {
            return when {
                oldItem is TaskListItem.SectionHeader && newItem is TaskListItem.SectionHeader ->
                    oldItem.title == newItem.title
                oldItem is TaskListItem.Task && newItem is TaskListItem.Task ->
                    oldItem.task.id == newItem.task.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: TaskListItem, newItem: TaskListItem): Boolean {
            return oldItem == newItem
        }
    }
}

class TaskItemTouchHelperCallback(
    private val deleteCallback: (TaskEntity) -> Unit
) : ItemTouchHelper.SimpleCallback(
    ItemTouchHelper.UP or ItemTouchHelper.DOWN,
    ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
) {
    private var onMoveCallback: ((Int, Int) -> Unit)? = null

    fun setOnMoveCallback(cb: (Int, Int) -> Unit) {
        onMoveCallback = cb
    }

    override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder): Boolean {
        onMoveCallback?.invoke(vh.bindingAdapterPosition, t.bindingAdapterPosition)
        return true
    }

    override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {}

    override fun onChildDraw(
        c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
        dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
    ) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            val alpha = 1f - Math.abs(dX) / vh.itemView.width
            vh.itemView.alpha = alpha
        }
        super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive)
    }

    override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
        super.clearView(rv, vh)
        vh.itemView.alpha = 1f
    }
}
