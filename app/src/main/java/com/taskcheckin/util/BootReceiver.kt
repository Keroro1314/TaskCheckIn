package com.taskcheckin.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.taskcheckin.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BootReceiver — 开机后重新注册所有待触发的日程闹钟
 * 
 * 闹钟在重启后会丢失，需要在开机后重新从数据库读取
 * 所有未完成且未触发过的日程任务，重新注册 AlarmManager。
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON" &&
            intent.action != "com.huawei.android.launcherthird.APP_HOME_MODE_RECOVERY"
        ) return

        Log.i(TAG, "设备开机，恢复闹钟...")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val pendingTasks = db.taskDao().getPendingAlarms(System.currentTimeMillis())

                pendingTasks.forEach { task ->
                    AlarmScheduler.scheduleTask(context, task)
                }

                Log.i(TAG, "已恢复 ${pendingTasks.size} 个闹钟")
            } catch (e: Exception) {
                Log.e(TAG, "恢复闹钟失败: ${e.message}")
            }
        }
    }
}
