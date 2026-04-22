package com.taskcheckin.ui.history

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.taskcheckin.R
import com.taskcheckin.TaskCheckInApp
import com.taskcheckin.data.local.TaskHistoryEntity
import com.taskcheckin.util.DateUtils
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity() {

    private lateinit var viewModel: HistoryViewModel
    private lateinit var adapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        val app = application as TaskCheckInApp
        viewModel = ViewModelProvider(this, HistoryViewModel.Factory(app.historyRepository))[HistoryViewModel::class.java]

        setupToolbar()
        setupRecyclerView()
        setupClearButton()
        observeState()
    }

    private fun setupToolbar() {
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = HistoryAdapter { history ->
            AlertDialog.Builder(this)
                .setTitle("删除记录")
                .setMessage("确定删除「${history.title}」？")
                .setPositiveButton("删除") { _, _ ->
                    viewModel.deleteItem(history)
                }
                .setNegativeButton("取消", null)
                .show()
        }
        findViewById<RecyclerView>(R.id.recyclerView).layoutManager = LinearLayoutManager(this)
        findViewById<RecyclerView>(R.id.recyclerView).adapter = adapter
    }

    private fun setupClearButton() {
        findViewById<View>(R.id.btnClearAll).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("清空历史")
                .setMessage("确定清空所有打卡记录？")
                .setPositiveButton("确定") { _, _ ->
                    viewModel.clearAll()
                    Snackbar.make(findViewById(R.id.rootLayout), "已清空", Snackbar.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.submitList(state.historyList)
                    findViewById<View>(R.id.emptyView).visibility =
                        if (state.historyList.isEmpty() && !state.isLoading) View.VISIBLE else View.GONE
                }
            }
        }
    }
}

class HistoryAdapter(
    private val onDelete: (TaskHistoryEntity) -> Unit
) : androidx.recyclerview.widget.ListAdapter<TaskHistoryEntity, HistoryAdapter.HistoryViewHolder>(
    object : androidx.recyclerview.widget.DiffUtil.ItemCallback<TaskHistoryEntity>() {
        override fun areItemsTheSame(oldItem: TaskHistoryEntity, newItem: TaskHistoryEntity) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: TaskHistoryEntity, newItem: TaskHistoryEntity) = oldItem == newItem
    }
) {
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): HistoryViewHolder {
        return HistoryViewHolder(
            android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
        )
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class HistoryViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        private val titleText: android.widget.TextView = itemView.findViewById(R.id.titleText)
        private val timeText: android.widget.TextView = itemView.findViewById(R.id.timeText)
        private val deleteBtn: android.widget.ImageButton = itemView.findViewById(R.id.deleteButton)

        fun bind(history: TaskHistoryEntity) {
            titleText.text = history.title
            timeText.text = DateUtils.formatTimestamp(history.completedAt)
            deleteBtn.setOnClickListener { onDelete(history) }
        }
    }
}