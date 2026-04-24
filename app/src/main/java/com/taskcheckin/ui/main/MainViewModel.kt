package com.taskcheckin.ui.main

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.taskcheckin.data.local.TaskEntity
import com.taskcheckin.data.local.TASK_TYPE_DAILY
import com.taskcheckin.data.local.TASK_TYPE_SCHEDULED
import com.taskcheckin.data.repository.TaskHistoryRepository
import com.taskcheckin.data.repository.TaskRepository
import com.taskcheckin.util.AlarmScheduler
import com.taskcheckin.widget.TaskWidgetProvider
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MainUiState(
    val tasks: List<TaskEntity> = emptyList(),
    val isLoading: Boolean = false
)

class MainViewModel(
    private val application: Application,
    private val taskRepository: TaskRepository,
    private val historyRepository: TaskHistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState(isLoading = true))
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // ====== 编辑状态 ======
    private val _editingTaskId = MutableStateFlow<Long?>(null)
    val editingTaskId: StateFlow<Long?> = _editingTaskId.asStateFlow()

    // ====== 高亮任务（从通知跳转时）======
    private val _highlightTaskId = MutableStateFlow<Long?>(null)
    val highlightTaskId: StateFlow<Long?> = _highlightTaskId.asStateFlow()

    // ====== 撤回完成 ======
    private var _lastCompletedTaskId: Long? = null
    val canUndo: StateFlow<Boolean> = MutableStateFlow(false)
    private val _canUndo = canUndo as MutableStateFlow

    // ====== 每日任务数据流 ======
    val dailyTasks: Flow<List<TaskEntity>> = taskRepository.getAllTasks()
        .map { tasks -> tasks.filter { it.taskType == TASK_TYPE_DAILY } }

    // ====== 日程任务数据流 ======
    val upcomingScheduledTasks: Flow<List<TaskEntity>> = taskRepository.getUpcomingScheduledTasks()

    init {
        viewModelScope.launch {
            // 原始任务列表（用于 widget 通知）
            taskRepository.getAllTasks().collect { tasks ->
                _uiState.update { it.copy(tasks = tasks, isLoading = false) }
                TaskWidgetProvider.updateAllWidgets(application)
            }
        }
    }

    fun setHighlightTaskId(id: Long) {
        _highlightTaskId.value = id
        // 3秒后自动清除高亮
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            if (_highlightTaskId.value == id) {
                _highlightTaskId.value = null
            }
        }
    }

    // ====== 编辑操作 ======
    fun startEditing(taskId: Long) {
        _editingTaskId.value = taskId
    }

    fun stopEditing() {
        _editingTaskId.value = null
    }

    fun saveTitleAndStopEditing(taskId: Long, newTitle: String) {
        _editingTaskId.value = null
        updateTaskTitle(taskId, newTitle)
    }

    // ====== 任务操作 ======
    fun toggleTask(id: Long, completed: Boolean) {
        _editingTaskId.value = null
        viewModelScope.launch {
            val task = taskRepository.getTaskById(id)
            taskRepository.toggleCompletion(id, completed)

            // 如果是日程任务且完成，取消闹钟
            if (task != null && task.taskType == TASK_TYPE_SCHEDULED) {
                AlarmScheduler.cancelTask(application, task)
            }

            if (completed && task != null) {
                _lastCompletedTaskId = id
                _canUndo.value = true
                historyRepository.addToHistory(task.title)
            } else if (!completed) {
                // 如果撤回的就是上次完成的，清除撤回状态
                if (_lastCompletedTaskId == id) {
                    _lastCompletedTaskId = null
                    _canUndo.value = false
                }
            }
        }
    }

    /** 直接添加实体（由 AddTaskBottomSheetDialog 调用）*/
    fun addTaskDirectly(task: TaskEntity) {
        viewModelScope.launch {
            val id = taskRepository.addTaskDirectly(task)
            // 如果是日程任务，注册闹钟
            if (task.taskType == TASK_TYPE_SCHEDULED && task.reminderTime > System.currentTimeMillis()) {
                val savedTask = taskRepository.getTaskById(id)
                savedTask?.let {
                    // 更新 alarmRequestCode（插入后才知道真正的 id）
                    val updatedTask = it.copy(alarmRequestCode = it.id.toInt())
                    taskRepository.updateTask(updatedTask)
                    AlarmScheduler.scheduleTask(application, updatedTask)
                }
            }
        }
    }

    fun addTask(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val orderIndex = _uiState.value.tasks.size
            taskRepository.addTask(title.trim(), orderIndex)
        }
    }

    private fun updateTaskTitle(id: Long, newTitle: String) {
        viewModelScope.launch {
            if (newTitle.isBlank()) {
                taskRepository.deleteTaskById(id)
            } else {
                val task = taskRepository.getTaskById(id)
                task?.let { taskRepository.updateTask(it.copy(title = newTitle.trim())) }
            }
        }
    }

    /** 更新日程任务（编辑日期/时间/标题等）*/
    fun updateScheduledTask(task: TaskEntity) {
        viewModelScope.launch {
            val oldTask = taskRepository.getTaskById(task.id)
            // 如果是日程任务且日期/时间变了，重新设置闹钟
            if (task.taskType == TASK_TYPE_SCHEDULED) {
                // 先取消旧闹钟
                oldTask?.let { AlarmScheduler.cancelTask(application, it) }
                // 设置新闹钟
                if (task.reminderTime > System.currentTimeMillis()) {
                    AlarmScheduler.scheduleTask(application, task)
                }
            }
            taskRepository.updateTask(task)
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            // 如果是日程任务，先取消闹钟
            if (task.taskType == TASK_TYPE_SCHEDULED) {
                AlarmScheduler.cancelTask(application, task)
            }
            taskRepository.deleteTask(task)
        }
    }

    fun selectAll() {
        viewModelScope.launch {
            _uiState.value.tasks.filter { it.taskType == TASK_TYPE_DAILY && !it.isCompleted }.forEach { task ->
                taskRepository.toggleCompletion(task.id, true)
                historyRepository.addToHistory(task.title)
            }
        }
    }

    fun deselectAll() {
        viewModelScope.launch {
            _uiState.value.tasks.filter { it.isCompleted }.forEach { task ->
                if (task.taskType == TASK_TYPE_DAILY) {
                    taskRepository.toggleCompletion(task.id, false)
                }
            }
        }
    }

    fun resetToday() {
        viewModelScope.launch {
            taskRepository.resetAllToday()
        }
    }

    fun onItemMoved(fromPosition: Int, toPosition: Int) {
        val currentList = _uiState.value.tasks.toMutableList()
        val item = currentList.removeAt(fromPosition)
        currentList.add(toPosition, item)
        _uiState.update { it.copy(tasks = currentList) }

        viewModelScope.launch {
            currentList.forEachIndexed { index, task ->
                taskRepository.updateOrder(task.id, index)
            }
        }
    }

    /** 撤回上一步完成的任务 */
    fun undoLastComplete() {
        val taskId = _lastCompletedTaskId ?: return
        toggleTask(taskId, false)
        // toggleTask(false) 内部已清除 _lastCompletedTaskId
    }

    class Factory(
        private val application: Application,
        private val taskRepository: TaskRepository,
        private val historyRepository: TaskHistoryRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(application, taskRepository, historyRepository) as T
        }
    }
}
