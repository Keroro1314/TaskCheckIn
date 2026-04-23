package com.taskcheckin.util

import android.content.Context
import androidx.work.*
import java.util.*
import java.util.concurrent.TimeUnit

object DailyResetWorker {

    private const val WORK_NAME = "daily_task_reset"

    fun schedule(context: Context) {
        val now = Calendar.getInstance()
        val due = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 1)
            set(Calendar.SECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_MONTH, 1)
        }

        val initialDelay = due.timeInMillis - now.timeInMillis

        val workRequest = PeriodicWorkRequestBuilder<ResetWorker>(
            1, TimeUnit.DAYS
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(false)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    class ResetWorker(private val context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
        override fun doWork(): Result {
            val db = com.taskcheckin.data.local.AppDatabase.getInstance(context)
            kotlinx.coroutines.runBlocking {
                db.taskDao().resetAllCompletions(com.taskcheckin.data.local.TASK_TYPE_DAILY)
            }
            return Result.success()
        }
    }
}