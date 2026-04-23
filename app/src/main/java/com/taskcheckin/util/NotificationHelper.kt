package com.taskcheckin.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * NotificationHelper — 统一管理通知渠道创建和权限申请
 */
object NotificationHelper {

    const val CHANNEL_ID = "schedule_reminder"
    const val CHANNEL_NAME = "日程提醒"
    const val CHANNEL_DESCRIPTION = "日程任务的定时提醒通知"

    /**
     * 创建通知渠道（Android 8.0+）
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                setShowBadge(true)
            }

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 检查是否有通知权限（Android 13+）
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * 检查能否发送通知（渠道存在 + 权限）
     */
    fun canNotify(context: Context): Boolean {
        return hasNotificationPermission(context)
    }

    /**
     * 跳转到应用通知权限设置页
     */
    fun openNotificationSettings(context: Context) {
        val intent = Intent().apply {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
                else -> {
                    action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    data = Uri.parse("package:${context.packageName}")
                }
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * 获取厂商后台管理设置页 Intent
     * 适配：华为、小米、OPPO、vivo、荣耀、一加、三星
     */
    fun getManufacturerBatterySettingsIntent(context: Context): Intent? {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val intent = Intent()

        when {
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                intent.setClassName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
            }
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> {
                intent.setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            }
            manufacturer.contains("oppo") -> {
                intent.setClassName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                )
            }
            manufacturer.contains("vivo") -> {
                intent.setClassName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                )
            }
            manufacturer.contains("oneplus") -> {
                intent.setClassName(
                    "com.oneplus.security",
                    "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                )
            }
            manufacturer.contains("samsung") -> {
                intent.setClassName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.ui.battery.BatteryActivity"
                )
            }
            else -> {
                // 通用降级：打开电池设置
                intent.action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            }
        }

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        return intent
    }

    /**
     * 检查是否存在厂商后台管理页面
     */
    fun hasManufacturerBatterySettings(context: Context): Boolean {
        return try {
            val intent = getManufacturerBatterySettingsIntent(context)
            intent?.resolveActivity(context.packageManager) != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 打开厂商后台管理设置页
     */
    fun openManufacturerBatterySettings(context: Context): Boolean {
        return try {
            val intent = getManufacturerBatterySettingsIntent(context) ?: return false
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            // 降级到通用电池优化页面
            try {
                val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                fallback.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(fallback)
                true
            } catch (e2: Exception) {
                false
            }
        }
    }
}
