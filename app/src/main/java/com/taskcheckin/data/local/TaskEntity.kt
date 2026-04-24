package com.taskcheckin.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 任务类型常量 */
const val TASK_TYPE_DAILY = 0
const val TASK_TYPE_SCHEDULED = 1
const val TASK_TYPE_TODAY = 2

/** 重复模式常量 */
const val REPEAT_NONE = "NONE"
const val REPEAT_DAILY = "DAILY"
const val REPEAT_WEEKLY = "WEEKLY"
const val REPEAT_MONTHLY = "MONTHLY"

/**
 * TaskEntity — 任务实体（支持每日任务和日程任务）
 */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val isCompleted: Boolean = false,
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),

    // ===== v1.1.0 新增字段 =====
    val taskType: Int = TASK_TYPE_DAILY,
    val dueDate: Long = 0L,
    val reminderTime: Long = 0L,
    val repeatMode: String = REPEAT_NONE,
    val alarmRequestCode: Int = 0,
    val reminderFired: Boolean = false
)
