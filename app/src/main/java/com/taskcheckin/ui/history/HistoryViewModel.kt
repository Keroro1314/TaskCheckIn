package com.taskcheckin.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.taskcheckin.data.local.TaskHistoryEntity
import com.taskcheckin.data.repository.TaskHistoryRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HistoryUiState(
    val historyList: List<TaskHistoryEntity> = emptyList(),
    val isLoading: Boolean = false
)

class HistoryViewModel(
    private val repository: TaskHistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState(isLoading = true))
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllHistory().collect { list ->
                _uiState.update { it.copy(historyList = list, isLoading = false) }
            }
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clearAllHistory()
        }
    }

    fun deleteItem(history: TaskHistoryEntity) {
        viewModelScope.launch {
            repository.deleteHistory(history)
        }
    }

    class Factory(private val repository: TaskHistoryRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HistoryViewModel(repository) as T
        }
    }
}