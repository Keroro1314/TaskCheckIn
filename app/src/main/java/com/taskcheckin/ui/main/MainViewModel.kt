package com.taskcheckin.ui.main

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.taskcheckin.data.local.TaskEntity
import com.taskcheckin.data.repository.TaskHistoryRepository
import com.taskcheckin.data.repository.TaskRepository
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

    private val _editingTaskId = MutableStateFlow<Long?>(null)
    val editingTaskId: StateFlow<Long?> = _editingTaskId.asStateFlow()

    init {
        viewModelScope.launch {
            taskRepository.getAllTasks().collect { tasks ->
                _uiState.update { it.copy(tasks = tasks, isLoading = false) }
                // 通知桌面小组件刷新
                TaskWidgetProvider.updateAllWidgets(application)
            }
        }
    }

    fun startEditing(taskId: Long) {
        _editingTaskId.value = taskId
    }

    fun stopEditing() {
        _editingTaskId.value = null
    }

    fun saveTitleAndStopEditing(taskId: Long, newTitle: String) {
        _editingTaskId.value = null  // 先退出编辑
        updateTaskTitle(taskId, newTitle)
    }

    fun toggleTask(id: Long, completed: Boolean) {
        _editingTaskId.value = null  // 先退出编辑
        viewModelScope.launch {
            taskRepository.toggleCompletion(id, completed)
            if (completed) {
                val task = taskRepository.getTaskById(id)
                task?.let { historyRepository.addToHistory(it.title) }
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

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            taskRepository.deleteTask(task)
        }
    }

    fun selectAll() {
        viewModelScope.launch {
            _uiState.value.tasks.filter { !it.isCompleted }.forEach { task ->
                taskRepository.toggleCompletion(task.id, true)
                historyRepository.addToHistory(task.title)
            }
        }
    }

    fun deselectAll() {
        viewModelScope.launch {
            _uiState.value.tasks.filter { it.isCompleted }.forEach { task ->
                taskRepository.toggleCompletion(task.id, false)
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