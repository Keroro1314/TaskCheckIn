package com.taskcheckin.ui.main

import android.graphics.Canvas
import android.graphics.Paint
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

class TaskAdapter(
    private val editingTaskId: Long?,
    private val onToggle: (Long, Boolean) -> Unit,
    private val onTitleChanged: (Long, String) -> Unit,
    private val onDelete: (TaskEntity) -> Unit,
    private val onReorder: (Int, Int) -> Unit,
    private val onStartEditing: (Long) -> Unit,
    private val onStopEditing: () -> Unit
) : ListAdapter<TaskEntity, TaskAdapter.TaskViewHolder>(TaskDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = getItem(position)
        holder.bind(task, task.id == editingTaskId)
    }

    inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val checkBox: CheckBox = itemView.findViewById(R.id.checkBox)
        private val titleEditText: EditText = itemView.findViewById(R.id.titleEditText)
        private val titleTextView: TextView = itemView.findViewById(R.id.titleTextView)
        private val dragHandle: ImageButton = itemView.findViewById(R.id.dragHandle)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)

        fun bind(task: TaskEntity, isEditing: Boolean) {
            // 先移除所有 listener，防止复用和重绑定时触发旧回调
            checkBox.setOnCheckedChangeListener(null)
            titleEditText.setOnEditorActionListener(null)
            titleEditText.onFocusChangeListener = null
            titleTextView.setOnClickListener(null)
            deleteButton.setOnClickListener(null)

            checkBox.isChecked = task.isCompleted
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                itemView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    // 先退出编辑再 toggle，避免 Flow 更新和编辑状态冲突
                    if (editingTaskId != null) onStopEditing()
                    onToggle(getItem(pos).id, isChecked)
                }
            }

            itemView.alpha = if (task.isCompleted) 0.7f else 1.0f

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
                        titleEditText.onFocusChangeListener = null  // 防重入
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
                    titleTextView.paintFlags = titleTextView.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                    titleTextView.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_completed))
                } else {
                    titleTextView.paintFlags = titleTextView.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
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

    class TaskDiffCallback : DiffUtil.ItemCallback<TaskEntity>() {
        override fun areItemsTheSame(oldItem: TaskEntity, newItem: TaskEntity) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: TaskEntity, newItem: TaskEntity) = oldItem == newItem
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

    override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {
        // Swipe handled externally via adapter
    }

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