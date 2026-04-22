package com.taskcheckin.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val isCompleted: Boolean = false,
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "task_history")
data class TaskHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val completedAt: Long = System.currentTimeMillis()
)

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY orderIndex ASC")
    fun getAllTasks(): Flow<List<TaskEntity>>

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

    @Query("UPDATE tasks SET isCompleted = 0")
    suspend fun resetAllCompletions()

    @Query("UPDATE tasks SET orderIndex = :newIndex WHERE id = :id")
    suspend fun updateOrderIndex(id: Long, newIndex: Int)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): TaskEntity?

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

@Database(entities = [TaskEntity::class, TaskHistoryEntity::class], version = 1, exportSchema = false)
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
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}