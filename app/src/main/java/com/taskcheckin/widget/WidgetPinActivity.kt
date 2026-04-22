package com.taskcheckin.widget

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.taskcheckin.R

class WidgetPinActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appWidgetManager = AppWidgetManager.getInstance(this)
        val provider = ComponentName(this, TaskWidgetProvider::class.java)

        // 先尝试系统API，如果支持且有弹窗就用系统方式
        if (appWidgetManager.isRequestPinAppWidgetSupported) {
            try {
                val success = appWidgetManager.requestPinAppWidget(provider, null, null)
                if (success) {
                    // 等1.5秒看弹窗有没有出来
                    window.decorView.postDelayed({
                        // 如果Activity还没被系统弹窗覆盖，说明弹窗没出来，走手动引导
                        showManualGuide()
                    }, 1500)
                    return
                }
            } catch (e: Exception) {
                // 某些ROM会抛异常，走手动引导
            }
        }

        showManualGuide()
    }

    private fun showManualGuide() {
        AlertDialog.Builder(this)
            .setTitle("添加桌面小组件")
            .setMessage("请按以下步骤添加：\n\n" +
                    "1️⃣ 长按桌面空白处\n" +
                    "2️⃣ 点击「小组件」或「窗口小工具」\n" +
                    "3️⃣ 找到「任务打卡」\n" +
                    "4️⃣ 长按拖动到桌面即可")
            .setPositiveButton("知道了") { _, _ ->
                finish()
            }
            .setOnCancelListener {
                finish()
            }
            .setCancelable(true)
            .show()
    }
}
