package com.taskcheckin.ui.main

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.taskcheckin.R
import com.taskcheckin.TaskCheckInApp
import com.taskcheckin.data.local.TaskEntity
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: TaskAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val app = application as TaskCheckInApp
        viewModel = ViewModelProvider(
            this,
            MainViewModel.Factory(application, app.taskRepository, app.historyRepository)
        )[MainViewModel::class.java]

        setupToolbar()
        setupRecyclerView()
        setupFab()
        setupBottomActions()
        observeState()
    }

    private fun setupToolbar() {
        findViewById<View>(R.id.btnHistory).setOnClickListener {
            startActivity(android.content.Intent(this, com.taskcheckin.ui.history.HistoryActivity::class.java))
        }
    }

    private fun setupRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)

        adapter = TaskAdapter(
            editingTaskId = null,
            onToggle = { id, completed -> viewModel.toggleTask(id, completed) },
            onTitleChanged = { id, title -> viewModel.saveTitleAndStopEditing(id, title) },
            onDelete = { task -> confirmDelete(task) },
            onReorder = { f, t -> viewModel.onItemMoved(f, t) },
            onStartEditing = { id -> viewModel.startEditing(id) },
            onStopEditing = { viewModel.stopEditing() }
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

    private fun setupFab() {
        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            showAddTaskDialog()
        }
    }

    private fun setupBottomActions() {
        findViewById<View>(R.id.btnSelectAll).setOnClickListener { viewModel.selectAll() }
        findViewById<View>(R.id.btnDeselectAll).setOnClickListener { viewModel.deselectAll() }
        findViewById<View>(R.id.btnReset).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("重置今日")
                .setMessage("确定取消所有任务的勾选状态？")
                .setPositiveButton("确定") { _, _ -> viewModel.resetToday() }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.submitList(state.tasks)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.editingTaskId.collect { editingId ->
                    adapter = TaskAdapter(
                        editingTaskId = editingId,
                        onToggle = { id, completed -> viewModel.toggleTask(id, completed) },
                        onTitleChanged = { id, title -> viewModel.saveTitleAndStopEditing(id, title) },
                        onDelete = { task -> confirmDelete(task) },
                        onReorder = { f, t -> viewModel.onItemMoved(f, t) },
                        onStartEditing = { id -> viewModel.startEditing(id) },
                        onStopEditing = { viewModel.stopEditing() }
                    )
                    findViewById<RecyclerView>(R.id.recyclerView).adapter = adapter
                    adapter.submitList(viewModel.uiState.value.tasks)
                }
            }
        }
    }

    private fun showAddTaskDialog() {
        val editText = EditText(this).apply {
            hint = "输入任务名称"
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("添加任务")
            .setView(editText)
            .setPositiveButton("添加") { _, _ ->
                val title = editText.text.toString()
                if (title.isNotBlank()) {
                    viewModel.addTask(title)
                    Snackbar.make(findViewById(R.id.rootLayout), "任务已添加", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()

        editText.postDelayed({
            editText.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(editText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }, 100)
    }

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