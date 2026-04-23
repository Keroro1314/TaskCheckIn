package com.taskcheckin.ui.calendar

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.CalendarView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.taskcheckin.R
import com.taskcheckin.TaskCheckInApp
import com.taskcheckin.data.local.TASK_TYPE_DAILY
import com.taskcheckin.data.local.TASK_TYPE_SCHEDULED
import com.taskcheckin.data.local.TaskEntity
import com.taskcheckin.data.repository.TaskRepository
import com.taskcheckin.databinding.ActivityCalendarBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * CalendarActivity — 日历视图页面
 * 
 * 显示月历，点击某一天可查看当天的日程任务（含每日任务）。
 * 每日任务在每天的日历格中显示为一个小红点标记。
 */
class CalendarActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCalendarBinding
    private lateinit var taskRepository: TaskRepository
    private lateinit var adapter: ScheduledTaskAdapter

    /** 当前选中的日期（归一化到0点）*/
    private var selectedDate: Long = 0L

    /** 所有日程任务的映射：日期 → 任务列表（用于显示小红点）*/
    private val scheduledTaskDates = mutableMapOf<Long, List<TaskEntity>>()

    private val dateFormat = SimpleDateFormat("yyyy年MM月dd日 EEEE", Locale.CHINA)
    private val monthFormat = SimpleDateFormat("yyyy年MM月", Locale.CHINA)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCalendarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val app = application as TaskCheckInApp
        taskRepository = TaskRepository(app.database.taskDao())

        selectedDate = taskRepository.normalizeDate(System.currentTimeMillis())

        setupToolbar()
        setupCalendar()
        setupTaskList()
        observeScheduledTasks()
        observeDailyTasks()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupCalendar() {
        // 设置当前选中日期
        binding.calendarView.date = selectedDate

        // 监听日期选择变化
        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val cal = Calendar.getInstance(Locale.CHINA).apply {
                set(year, month, dayOfMonth, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            selectedDate = cal.timeInMillis
            updateSelectedDateLabel()
            loadTasksForDate(selectedDate)
        }

        updateSelectedDateLabel()
    }

    private fun updateSelectedDateLabel() {
        binding.tvSelectedDate.text = dateFormat.format(Date(selectedDate))
        binding.tvSelectedDate.visibility = View.VISIBLE
    }

    private fun setupTaskList() {
        adapter = ScheduledTaskAdapter(
            onToggle = { id, completed ->
                lifecycleScope.launch {
                    taskRepository.toggleCompletion(id, completed)
                }
            },
            onDelete = { task ->
                lifecycleScope.launch {
                    taskRepository.deleteTask(task)
                }
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        // 默认加载选中日期的任务
        loadTasksForDate(selectedDate)
    }

    private fun observeScheduledTasks() {
        lifecycleScope.launch {
            taskRepository.getUpcomingScheduledTasks().collectLatest { tasks ->
                // 建立日期到任务的映射
                scheduledTaskDates.clear()
                tasks.forEach { task ->
                    val date = task.dueDate
                    val list = scheduledTaskDates.getOrPut(date) { emptyList() } + task
                    scheduledTaskDates[date] = list
                }
                // 更新小红点
                updateTaskDots()
                // 刷新当前选中日期的任务列表
                loadTasksForDate(selectedDate)
            }
        }
    }

    private fun observeDailyTasks() {
        lifecycleScope.launch {
            taskRepository.getDailyTasks().collectLatest { tasks ->
                // 每日任务每天都要显示小红点
                // 当前月的每日任务每天都显示（简化处理）
                updateTaskDots()
            }
        }
    }

    private fun loadTasksForDate(date: Long) {
        lifecycleScope.launch {
            taskRepository.getScheduledTasksByDate(date).collectLatest { scheduledTasks ->
                // 每日任务（所有日期都显示未完成的），切到 IO 线程查库
                val dailyTasks = withContext(Dispatchers.IO) {
                    taskRepository.getIncompleteDailyTasksSync()
                }
                val allTasks = scheduledTasks + dailyTasks
                updateTaskList(allTasks)
            }
        }
    }

    private fun updateTaskList(tasks: List<TaskEntity>) {
        if (tasks.isEmpty()) {
            binding.emptyView.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        } else {
            binding.emptyView.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
            adapter.submitList(tasks.sortedBy { if (it.taskType == TASK_TYPE_DAILY) Long.MAX_VALUE else it.reminderTime })
        }
    }

    private fun updateTaskDots() {
        // TODO: 定制 CalendarView 添加小红点（需自定义 View 替代原生 CalendarView）
        // 计划方案：每月切换时遍历 scheduledTaskDates，
        // 在对应日期的格子位置动态添加 View 实现红点标记。
        // 这需要自定义 CalendarView，成本较高，v1.1.0 可先跳过。
    }
}
