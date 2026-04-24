package com.taskcheckin.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    // ===== 每日任务 =====
    @Query("SELECT * FROM tasks WHERE taskType = :taskType ORDER BY orderIndex ASC")
    fun getTasksByType(taskType: Int): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY orderIndex ASC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("DELETE FROM tasks WHERE taskType = :taskType AND dueDate < :today")
    suspend fun deleteStaleTodayTasks(taskType: Int, today: Long)

    @Query("SELECT * FROM tasks WHERE taskType = :taskType ORDER BY orderIndex ASC")
    fun getTasksByTypeSync(taskType: Int): List<TaskEntity>

    // ===== 同步查询（避免在协程中使用 Flow）=====
    @Query("SELECT * FROM tasks WHERE taskType = :taskType AND isCompleted = 0 ORDER BY orderIndex ASC")
    fun getIncompleteTasksByTypeSync(taskType: Int): List<TaskEntity>

    // ===== 日程任务 =====
    @Query("SELECT * FROM tasks WHERE taskType = :taskType AND dueDate = :dueDate ORDER BY reminderTime ASC")
    fun getScheduledTasksByDate(taskType: Int, dueDate: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE taskType = :taskType AND isCompleted = 0 AND dueDate >= :today ORDER BY dueDate ASC, reminderTime ASC")
    fun getUpcomingScheduledTasks(taskType: Int, today: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE taskType = :taskType AND dueDate = :dueDate ORDER BY reminderTime ASC")
    fun getTasksByDateSync(taskType: Int, dueDate: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE taskType = :taskType AND dueDate = :today ORDER BY reminderTime ASC")
    fun getTodayScheduledTasks(taskType: Int, today: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE taskType = :taskType AND isCompleted = 0 AND dueDate >= :today ORDER BY dueDate ASC, reminderTime ASC")
    fun getUpcomingScheduledTasksSync(taskType: Int, today: Long): List<TaskEntity>

    // ===== 通用操作 =====
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Long)

    @Query("UPDATE tasks SET isCompleted = :completed WHERE id = :id")
    suspend fun updateCompletion(id: Long, completed: Boolean)

    @Query("UPDATE tasks SET isCompleted = 0 WHERE taskType = :taskType")
    suspend fun resetAllCompletions(taskType: Int)

    @Query("UPDATE tasks SET orderIndex = :newIndex WHERE id = :id")
    suspend fun updateOrderIndex(id: Long, newIndex: Int)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE alarmRequestCode > 0 AND reminderTime > :now AND isCompleted = 0")
    suspend fun getPendingAlarms(now: Long): List<TaskEntity>

    @Query("UPDATE tasks SET alarmRequestCode = :newCode WHERE id = :id")
    suspend fun updateAlarmCode(id: Long, newCode: Int)

    @Query("UPDATE tasks SET reminderFired = 1 WHERE id = :id")
    suspend fun markReminderFired(id: Long)

    @Query("UPDATE tasks SET reminderFired = 0, isCompleted = 0 WHERE dueDate < :today")
    suspend fun resetPastScheduledTasks(today: Long)

    @Query("SELECT * FROM tasks ORDER BY orderIndex ASC")
    fun getAllTasksSync(): List<TaskEntity>
}

@Dao
interface TaskHistoryDao {
    @Query("SELECT * FROM task_history ORDER BY completedAt DESC")
    fun getAllHistory(): Flow<List<TaskHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: TaskHistoryEntity)

    @Query("DELETE FROM task_history")
    suspend fun clearAllHistory()

    @Delete
    suspend fun deleteHistory(history: TaskHistoryEntity)
}

@Database(
    entities = [TaskEntity::class, TaskHistoryEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun taskHistoryDao(): TaskHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "task_checkin_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
