package com.taskcheckin.widget

import android.content.Intent
import android.widget.RemoteViewsService

/**
 * 小组件 — 日程任务列表服务
 */
class ScheduledTaskWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return ScheduledTaskWidgetFactory(applicationContext, intent)
    }
}
