package com.taskcheckin.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.taskcheckin.databinding.ActivityBatteryOptimizationBinding
import com.taskcheckin.util.NotificationHelper

/**
 * BatteryOptimizationActivity — 引导用户关闭厂商后台管理/省电策略
 * 
 * 国产 ROM（华为/小米/OPPO/vivo/荣耀等）对后台应用限制严格，
 * 若不手动开启权限，闹钟和通知会被系统强制拦截。
 */
class BatteryOptimizationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBatteryOptimizationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBatteryOptimizationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupManufacturerInfo()
        setupButtons()
    }

    private fun setupManufacturerInfo() {
        val manufacturer = Build.MANUFACTURER
        binding.tvManufacturer.text = "检测到您的设备：${manufacturer.uppercase()}"
        binding.tvTip.text = getTipText(manufacturer)
    }

    private fun getTipText(manufacturer: String): String {
        val lower = manufacturer.lowercase()
        return when {
            lower.contains("huawei") || lower.contains("honor") -> """
                🔧 华为/荣耀操作步骤：
                1. 打开「手机管家」→「启动管理」
                2. 找到「任务打卡」App
                3. 开启「自动管理」开关，或手动开启「允许自启动」「允许后台活动」
            """.trimIndent()

            lower.contains("xiaomi") || lower.contains("redmi") -> """
                🔧 小米/红米操作步骤：
                1. 打开「设置」→「应用设置」→「应用管理」
                2. 找到「任务打卡」
                3. 点击「省电策略」→「无限制」
                4. 同时开启「自启动管理」
            """.trimIndent()

            lower.contains("oppo") -> """
                🔧 OPPO 操作步骤：
                1. 打开「设置」→「电池」→「耗电异常优化」
                2. 找到「任务打卡」，设为「不优化」
                3. 或在「权限管理」→「后台弹窗」中开启
            """.trimIndent()

            lower.contains("vivo") -> """
                🔧 vivo 操作步骤：
                1. 打开「设置」→「电池」→「后台耗电管理」
                2. 找到「任务打卡」，开启「允许后台运行」
                3. 在「自启动」中允许自启动
            """.trimIndent()

            else -> """
                🔧 通用操作步骤：
                1. 打开「设置」→「电池」→「省电策略」
                2. 找到「任务打卡」，设为「不限制」
                3. 或在「后台管理」中允许应用常驻后台
            """.trimIndent()
        }
    }

    private fun setupButtons() {
        // 打开厂商后台管理
        binding.btnOpenSettings.setOnClickListener {
            val success = NotificationHelper.openManufacturerBatterySettings(this)
            if (!success) {
                // 降级：打开电池优化页面
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                } catch (e: Exception) {
                    // 最低降级：打开应用设置
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = android.net.Uri.parse("package:$packageName")
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                }
            }
        }

        // 跳过
        binding.btnSkip.setOnClickListener {
            finish()
        }
    }
}
