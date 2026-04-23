package com.taskcheckin.widget

import android.content.Intent
import android.widget.RemoteViewsService

/**
 * 小组件 — 每日任务列表服务
 */
class DailyTaskWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return DailyTaskWidgetFactory(applicationContext, intent)
    }
}
