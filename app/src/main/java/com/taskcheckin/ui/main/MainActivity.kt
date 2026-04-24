package com.taskcheckin.ui.main

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.taskcheckin.R
import com.taskcheckin.TaskCheckInApp
import com.taskcheckin.data.local.TaskEntity
import com.taskcheckin.data.local.TASK_TYPE_DAILY
import com.taskcheckin.data.local.TASK_TYPE_SCHEDULED
import com.taskcheckin.data.local.REPEAT_NONE
import com.taskcheckin.data.repository.TaskRepository
import com.taskcheckin.ui.calendar.CalendarActivity
import com.taskcheckin.util.AlarmScheduler
import com.taskcheckin.util.NotificationHelper
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var taskRepository: TaskRepository
    private lateinit var adapter: TaskAdapter

    // 分组状态
    private var showFutureTasks = false

    private val dateFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.CHINA)
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.CHINA)

    // ====== 权限申请 ======
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Snackbar.make(
                findViewById(R.id.rootLayout),
                "通知权限未开启，提醒将无法推送",
                Snackbar.LENGTH_LONG
            ).setAction("去设置") {
                NotificationHelper.openNotificationSettings(this)
            }.show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val app = application as TaskCheckInApp
        taskRepository = TaskRepository(app.database.taskDao())
        viewModel = androidx.lifecycle.ViewModelProvider(
            this,
            MainViewModel.Factory(application, app.taskRepository, app.historyRepository)
        )[MainViewModel::class.java]

        setupToolbar()
        setupRecyclerView()
        setupFab()
        setupBottomActions()
        observeState()
        // 检查通知权限
        requestNotificationPermissionIfNeeded()

        // 检查精确闹钟权限
        checkExactAlarmPermission()

        // 处理从通知点进来的高亮
        handleNotificationIntent()
    }

    private fun handleNotificationIntent() {
        val highlightId = intent.getLongExtra("highlight_task_id", -1L)
        if (highlightId > 0) {
            viewModel.setHighlightTaskId(highlightId)
        }
    }

    // ====== 权限 ======
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        // 创建通知渠道
        NotificationHelper.createNotificationChannel(this)
    }

    private fun checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!AlarmScheduler.canScheduleExactAlarms(this)) {
                // 提示用户去开启精确闹钟权限
                Snackbar.make(
                    findViewById(R.id.rootLayout),
                    "需要精确闹钟权限才能准时提醒",
                    Snackbar.LENGTH_LONG
                ).setAction("去设置") {
                    AlarmScheduler.openExactAlarmSettings(this)
                }.show()
            }
        }
    }

    // ====== 工具栏 ======
    private fun setupToolbar() {
        findViewById<ImageButton>(R.id.btnHistory).setOnClickListener {
            startActivity(Intent(this, com.taskcheckin.ui.history.HistoryActivity::class.java))
        }
        findViewById<ImageButton>(R.id.btnCalendar).setOnClickListener {
            startActivity(Intent(this, CalendarActivity::class.java))
        }
        findViewById<ImageButton>(R.id.btnBattery).setOnClickListener {
            startActivity(Intent(this, com.taskcheckin.ui.BatteryOptimizationActivity::class.java))
        }
    }

    // ====== 列表 ======
    private fun setupRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)

        adapter = TaskAdapter(
            editingTaskId = null,
            onToggle = { id, completed ->
                viewModel.toggleTask(id, completed)
            },
            onTitleChanged = { id, title -> viewModel.saveTitleAndStopEditing(id, title) },
            onDelete = { task -> confirmDelete(task) },
            onReorder = { f, t -> viewModel.onItemMoved(f, t) },
            onStartEditing = { id -> viewModel.startEditing(id) },
            onStopEditing = { viewModel.stopEditing() },
            onHighlight = { id -> viewModel.setHighlightTaskId(id) },
            onEditSchedule = { task -> showEditTaskBottomSheet(task) }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val touchHelper = ItemTouchHelper(TaskItemTouchHelperCallback { task ->
            confirmDelete(task)
        }.also { callback ->
            callback.setOnMoveCallback { f, t -> viewModel.onItemMoved(f, t) }
        })
        touchHelper.attachToRecyclerView(recyclerView)
    }

    // ====== FAB ======
    private fun setupFab() {
        findViewById<View>(R.id.fabAdd).setOnClickListener {
            showAddTaskBottomSheet()
        }
    }

    // ====== 底部按钮 ======
    private fun setupBottomActions() {
        findViewById<View>(R.id.btnSelectAll).setOnClickListener { viewModel.selectAll() }
        findViewById<View>(R.id.btnDeselectAll).setOnClickListener { viewModel.deselectAll() }
        findViewById<View>(R.id.btnReset).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("重置今日")
                .setMessage("确定取消所有每日任务的勾选状态？日程任务不受影响。")
                .setPositiveButton("确定") { _, _ -> viewModel.resetToday() }
                .setNegativeButton("取消", null)
                .show()
        }
        findViewById<View>(R.id.btnToggleFuture).setOnClickListener {
            showFutureTasks = !showFutureTasks
            updateFutureToggleButton()
            observeState()
        }
        updateFutureToggleButton()

        // 撤回按钮
        val btnUndo = findViewById<MaterialButton>(R.id.btnUndo)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.canUndo.collect { canUndo ->
                    btnUndo.isEnabled = canUndo
                    btnUndo.alpha = if (canUndo) 1f else 0.4f
                }
            }
        }
        btnUndo.setOnClickListener {
            if (viewModel.canUndo.value) {
                viewModel.undoLastComplete()
            } else {
                Snackbar.make(findViewById(R.id.rootLayout), "无任务可撤回", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateFutureToggleButton() {
        val btn = findViewById<MaterialButton>(R.id.btnToggleFuture)
        btn.text = if (showFutureTasks) "收起未来日程" else "查看未来日程"
    }

    // ====== 数据观察 ======
    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 合并：每日任务Flow + 日程任务Flow + 编辑状态
                combine(
                    viewModel.dailyTasks,
                    viewModel.upcomingScheduledTasks,
                    viewModel.editingTaskId
                ) { daily, upcoming, editingId ->
                    Triple(daily, upcoming, editingId)
                }.collect { (daily, upcoming, editingId) ->
                    // 今日待办：未完成的每日任务 + 今日日程
                    val todayCal = taskRepository.normalizeDate(System.currentTimeMillis())
                    val todayScheduled = upcoming.filter { it.dueDate == todayCal }
                    val todayItems = daily.filter { !it.isCompleted } + todayScheduled

                    // 未来日程：明天及以后的日程任务
                    val futureItems = upcoming.filter { it.dueDate > todayCal }

                    // 只更新编辑状态（不重建 adapter）
                    adapter.setEditingTaskId(editingId)
                    adapter.submitGroupedList(todayItems, futureItems, showFutureTasks)
                }
            }
        }
    }

    // ====== 新建任务底部面板 ======
    private fun showAddTaskBottomSheet() {
        val dialog = AddTaskBottomSheetDialog(this, taskRepository)
        dialog.setOnTaskAddedListener { task ->
            viewModel.addTaskDirectly(task)
            Snackbar.make(findViewById(R.id.rootLayout), "任务已添加", Snackbar.LENGTH_SHORT).show()
        }
        dialog.show()
    }

    // ====== 编辑任务底部面板 ======
    private fun showEditTaskBottomSheet(task: TaskEntity) {
        val dialog = AddTaskBottomSheetDialog(this, taskRepository, existingTask = task)
        dialog.setOnTaskUpdatedListener { updatedTask ->
            viewModel.updateScheduledTask(updatedTask)
            Snackbar.make(findViewById(R.id.rootLayout), getString(R.string.task_updated), Snackbar.LENGTH_SHORT).show()
        }
        dialog.show()
    }

    // ====== 删除确认 ======
    private fun confirmDelete(task: TaskEntity) {
        AlertDialog.Builder(this)
            .setTitle("删除任务")
            .setMessage("确定删除「${task.title}」？")
            .setPositiveButton("删除") { _, _ ->
                viewModel.deleteTask(task)
                Snackbar.make(findViewById(R.id.rootLayout), "已删除", Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
