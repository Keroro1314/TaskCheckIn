package com.taskcheckin.ui.main

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.taskcheckin.R
import com.taskcheckin.data.local.TaskEntity
import com.taskcheckin.data.local.REPEAT_NONE
import com.taskcheckin.data.local.REPEAT_DAILY
import com.taskcheckin.data.local.REPEAT_WEEKLY
import com.taskcheckin.data.local.REPEAT_MONTHLY
import com.taskcheckin.data.repository.TaskRepository
import com.taskcheckin.util.AlarmScheduler
import java.text.SimpleDateFormat
import java.util.*

/**
 * AddTaskBottomSheetDialog — 添加/编辑任务底部面板
 * 
 * 支持：
 * - 每日任务（默认）
 * - 日程任务（开启开关后显示日期/时间选择）
 * - 重复设置（每天/每周/每月）
 * - 编辑模式：传入已有 TaskEntity，修改标题/日期/时间
 */
class AddTaskBottomSheetDialog(
    private val context: Context,
    private val taskRepository: TaskRepository,
    private val existingTask: TaskEntity? = null  // 编辑模式
) : BottomSheetDialog(context) {

    private var onTaskAddedListener: ((TaskEntity) -> Unit)? = null
    private var onTaskUpdatedListener: ((TaskEntity) -> Unit)? = null

    private lateinit var tvDialogTitle: TextView
    private lateinit var etTitle: EditText
    private lateinit var tvCharCount: TextView
    private lateinit var switchSchedule: Switch
    private lateinit var scheduleOptions: LinearLayout
    private lateinit var tvSelectDate: TextView
    private lateinit var tvSelectTime: TextView
    private lateinit var spinnerRepeat: Spinner
    private lateinit var btnCreate: Button

    private var selectedDate: Long = 0L
    private var selectedTime: Long = 0L
    private var selectedRepeat: String = REPEAT_NONE
    private val todayCal = Calendar.getInstance(Locale.CHINA)

    private val dateFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.CHINA)
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.CHINA)
    private val time24h = SimpleDateFormat("HH:mm", Locale.CHINA)

    private val repeatOptions = arrayOf("仅一次", "每天", "每周", "每月")
    private val repeatValues = arrayOf(REPEAT_NONE, REPEAT_DAILY, REPEAT_WEEKLY, REPEAT_MONTHLY)

    private val isEditMode get() = existingTask != null

    init {
        setContentView(R.layout.bottom_sheet_add_task)
        initViews()
        setupListeners()
        if (isEditMode) fillExistingData()
    }

    private fun initViews() {
        tvDialogTitle = findViewById(R.id.tvDialogTitle)!!
        etTitle = findViewById(R.id.etTaskTitle)!!
        tvCharCount = findViewById(R.id.tvCharCount)!!
        switchSchedule = findViewById(R.id.switchSchedule)!!
        scheduleOptions = findViewById(R.id.scheduleOptions)!!
        tvSelectDate = findViewById(R.id.tvSelectDate)!!
        tvSelectTime = findViewById(R.id.tvSelectTime)!!
        spinnerRepeat = findViewById(R.id.spinnerRepeat)!!
        btnCreate = findViewById(R.id.btnCreate)!!

        // 默认选中明天
        todayCal.add(Calendar.DAY_OF_MONTH, 1)
        todayCal.set(Calendar.HOUR_OF_DAY, 9)
        todayCal.set(Calendar.MINUTE, 0)
        todayCal.set(Calendar.SECOND, 0)
        todayCal.set(Calendar.MILLISECOND, 0)
        selectedDate = taskRepository.normalizeDate(todayCal.timeInMillis)
        selectedTime = todayCal.timeInMillis

        // 默认重复：仅一次
        spinnerRepeat.adapter = ArrayAdapter(
            context, android.R.layout.simple_spinner_dropdown_item, repeatOptions
        )

        updateDateTimeDisplay()
        updateCharCount()
    }

    private fun fillExistingData() {
        val task = existingTask!!

        // 标题
        etTitle.setText(task.title)

        // 日程开关
        val isScheduled = task.taskType == com.taskcheckin.data.local.TASK_TYPE_SCHEDULED
        switchSchedule.isChecked = isScheduled
        scheduleOptions.visibility = if (isScheduled) View.VISIBLE else View.GONE

        // 日期和时间
        if (task.dueDate > 0) selectedDate = task.dueDate
        if (task.reminderTime > 0) selectedTime = task.reminderTime
        updateDateTimeDisplay()

        // 重复模式
        val repeatIndex = repeatValues.indexOf(task.repeatMode)
        if (repeatIndex >= 0) spinnerRepeat.setSelection(repeatIndex)

        // UI 更新
        tvDialogTitle.text = context.getString(R.string.edit_task)
        btnCreate.text = context.getString(R.string.save_task)
        updateCharCount()
    }

    private fun setupListeners() {
        // 字数统计
        etTitle.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateCharCount()
            }
        })

        // 日程开关
        switchSchedule.setOnCheckedChangeListener { _, isChecked ->
            scheduleOptions.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        // 日期选择
        tvSelectDate.setOnClickListener {
            showDatePicker()
        }

        // 时间选择
        tvSelectTime.setOnClickListener {
            showTimePicker()
        }

        // 重复选择
        spinnerRepeat.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedRepeat = repeatValues[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 创建/保存按钮
        btnCreate.setOnClickListener {
            if (isEditMode) {
                updateTask()
            } else {
                createTask()
            }
        }
    }

    private fun updateCharCount() {
        val count = etTitle.text.length
        tvCharCount.text = "$count / 100"
        tvCharCount.setTextColor(
            ContextCompat.getColor(
                context,
                if (count > 100) R.color.delete_red else R.color.text_secondary
            )
        )
    }

    private fun updateDateTimeDisplay() {
        tvSelectDate.text = dateFormat.format(Date(selectedDate))
        tvSelectTime.text = time24h.format(Date(selectedTime))
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance(Locale.CHINA).apply { timeInMillis = selectedDate }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                cal.set(year, month, dayOfMonth)
                selectedDate = taskRepository.normalizeDate(cal.timeInMillis)
                updateDateTimeDisplay()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showTimePicker() {
        val cal = Calendar.getInstance(Locale.CHINA).apply { timeInMillis = selectedTime }
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                cal.set(Calendar.MINUTE, minute)
                selectedTime = cal.timeInMillis
                updateDateTimeDisplay()
            },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            true  // 24小时制
        ).show()
    }

    private fun createTask() {
        val title = etTitle.text.toString().trim()
        if (title.isEmpty()) {
            etTitle.error = "请输入任务名称"
            return
        }
        if (title.length > 100) {
            etTitle.error = "任务名称不能超过100字"
            return
        }

        // 生成唯一的 alarmRequestCode（用时间戳+随机数）
        val alarmCode = (System.currentTimeMillis() % Int.MAX_VALUE).toInt() +
                (0..999).random()

        val isScheduled = switchSchedule.isChecked
        val taskType = if (isScheduled) com.taskcheckin.data.local.TASK_TYPE_SCHEDULED
        else com.taskcheckin.data.local.TASK_TYPE_DAILY

        // 计算提醒时间（用于日程任务）
        val reminderTime = if (isScheduled) {
            // 合并日期和时间
            val cal = Calendar.getInstance(Locale.CHINA).apply { timeInMillis = selectedDate }
            val timeCal = Calendar.getInstance(Locale.CHINA).apply { timeInMillis = selectedTime }
            cal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
            cal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
            cal.timeInMillis
        } else 0L

        val task = TaskEntity(
            title = title,
            taskType = taskType,
            dueDate = if (isScheduled) selectedDate else 0L,
            reminderTime = reminderTime,
            repeatMode = if (isScheduled) selectedRepeat else REPEAT_NONE,
            alarmRequestCode = if (isScheduled) alarmCode else 0
        )

        onTaskAddedListener?.invoke(task)
        dismiss()
    }

    private fun updateTask() {
        val title = etTitle.text.toString().trim()
        if (title.isEmpty()) {
            etTitle.error = "请输入任务名称"
            return
        }
        if (title.length > 100) {
            etTitle.error = "任务名称不能超过100字"
            return
        }

        val task = existingTask!!
        val isScheduled = switchSchedule.isChecked
        val newTaskType = if (isScheduled) com.taskcheckin.data.local.TASK_TYPE_SCHEDULED
        else com.taskcheckin.data.local.TASK_TYPE_DAILY

        // 计算提醒时间
        val reminderTime = if (isScheduled) {
            val cal = Calendar.getInstance(Locale.CHINA).apply { timeInMillis = selectedDate }
            val timeCal = Calendar.getInstance(Locale.CHINA).apply { timeInMillis = selectedTime }
            cal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
            cal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
            cal.timeInMillis
        } else 0L

        val updatedTask = task.copy(
            title = title,
            taskType = newTaskType,
            dueDate = if (isScheduled) selectedDate else 0L,
            reminderTime = reminderTime,
            repeatMode = if (isScheduled) selectedRepeat else REPEAT_NONE
        )

        onTaskUpdatedListener?.invoke(updatedTask)
        dismiss()
    }

    fun setOnTaskAddedListener(listener: (TaskEntity) -> Unit) {
        onTaskAddedListener = listener
    }

    fun setOnTaskUpdatedListener(listener: (TaskEntity) -> Unit) {
        onTaskUpdatedListener = listener
    }
}
