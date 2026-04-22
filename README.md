# 任务打卡 — 桌面小组件版

一款 Android 本地任务管理应用，支持桌面小组件打卡、历史记录、自动每日重置。

## 功能一览

| 功能 | 说明 |
|---|---|
| 任务列表 | 添加/编辑/删除/拖动排序 |
| 打卡完成 | 点击即打卡，数据存入历史 |
| 历史记录 | 查看每天完成了哪些任务 |
| 每日重置 | 凌晨 0:01 自动重置所有任务 |
| 桌面小组件 | 3x2 小组件，不进 App 也能打卡 |
| 一键添加小组件 | 长按 App 图标 → 快捷方式 → 直接固定小组件到桌面 |

## 快速开始

### 安装 APK

APK 位于release：
```
https://github.com/Keroro1314/TaskCheckIn/releases/tag/v1.0.0
```
 APK 于手机安装即可。

### 添加桌面小组件

方式一：快捷方式（推荐）
1. 长按手机桌面空白处
2. 找到「任务打卡」App，长按图标
3. 选择「添加小组件」快捷方式
4. 按提示操作

方式二：手动添加
1. 长按桌面空白处 → 点击「小组件」/「窗口小工具」
2. 找到「任务打卡」，长按拖到桌面即可

## 工作原理

### 整体架构

App 层
- MainActivity：主页（任务列表）
- HistoryActivity：历史记录页
- TaskAdapter：RecyclerView 适配器
- MainViewModel：UI 状态 + 业务逻辑

Widget 层
- TaskWidgetProvider：小组件入口，处理点击事件
- TaskWidgetService：提供 RemoteViews 列表数据
- TaskWidgetFactory：将每条任务绑定 RemoteViews

数据层
- AppDatabase (Room)：SQLite 数据库
- TaskRepository：任务 CRUD
- TaskHistoryRepository：历史记录 CRUD

定时任务
- DailyResetWorker：每天 0:01 重置所有任务

### 数据库设计（Room）

两个表：

tasks — 当前任务
- id：主键，自增
- title：任务名称
- isCompleted：是否已完成
- orderIndex：排序索引
- createdAt：创建时间

task_history — 打卡历史
- id：主键
- title：任务名称（快照）
- completedAt：完成时间

### 数据流（App 内）

User Action → MainViewModel.toggleTask() → TaskRepository → Room DAO → SQLite
→ Flow 发出新数据 → MainViewModel 收集 → _uiState 更新
→ MainActivity 观察 uiState → TaskAdapter.submitList()
→ RecyclerView 刷新列表 + TaskWidgetProvider.updateAllWidgets() 通知小组件刷新

### 小组件工作原理

Android 桌面小组件是基于 AppWidgetProvider 的远程视图（RemoteViews）系统。

数据绑定流程：
AppWidgetManager.updateAppWidget() → 设置 RemoteViews + RemoteViewsService
→ AppWidgetManager.notifyAppWidgetViewDataChanged()
→ TaskWidgetFactory.onDataSetChanged() 被调用
→ 读取 Room 数据库（同步，非 Flow）→ 返回 RemoteViews 列表 → 显示在桌面上

点击打卡流程：
用户在小组件上点击任务行 → PendingIntent 触发 ACTION_CHECK_TASK
→ TaskWidgetProvider.onReceive() 接收广播 → 直接操作 Room 数据库（IO 线程）
→ updateAllWidgets() → 广播刷新所有小组件实例 → TaskWidgetFactory 重新读取数据 → 列表更新

为什么小组件需要同步读取数据库？
RecyclerView 用 Flow/Collector 在协程里异步读数据，但小组件的 RemoteViewsFactory.onDataSetChanged() 运行在 binder 线程，不支持协程。所以用 getAllTasksSync() 在 IO 线程同步读取。

### 快捷方式固定小组件原理

App 提供了一个 WidgetPinActivity，入口通过 Android 快捷方式系统暴露（shortcuts.xml）。

用户点击快捷方式后：
→ WidgetPinActivity.onCreate()
→ AppWidgetManager.isRequestPinAppWidgetSupported() 检查系统是否支持
  - 支持：requestPinAppWidget(provider, callback) → 系统弹出确认框
           1.5s 内无弹窗 → 显示手动引导弹窗保底
  - 不支持：显示手动引导弹窗（长按桌面 → 小组件 → 任务打卡）

国产 ROM（华为、小米、OPPO 等）对 requestPinAppWidget() 支持不完整，所以加了保底的引导弹窗。

### 每日自动重置原理

使用 WorkManager 的 PeriodicWorkRequest，每天 0:01 执行一次：

```kotlin
PeriodicWorkRequestBuilder<ResetWorker>(1, TimeUnit.DAYS)
    .setInitialDelay(计算到次日 0:01 的毫秒数, TimeUnit.MILLISECONDS)
    .build()
```

ResetWorker.doWork() 中调用 taskDao.resetAllCompletions()，把所有任务的 isCompleted 设为 false，实现"新的一天重新开始"。

## 项目结构

```
task-checkin/
├── build.gradle                      根构建配置
├── settings.gradle
├── gradle.properties                Gradle + Java 配置
├── local.properties                 SDK 路径
└── app/
    ├── build.gradle                 App 模块配置
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/taskcheckin/
        │   ├── TaskCheckInApp.kt    Application 类（初始化数据库）
        │   ├── data/
        │   │   ├── local/Database.kt Room 数据库 + DAO
        │   │   └── repository/       Repository 层
        │   ├── ui/
        │   │   ├── main/            主页（Activity + ViewModel + Adapter）
        │   │   └── history/         历史页
        │   ├── widget/
        │   │   ├── TaskWidgetProvider.kt   小组件
        │   │   ├── TaskWidgetService.kt    RemoteViewsService
        │   │   ├── TaskWidgetFactory.kt    RemoteViewsFactory
        │   │   └── WidgetPinActivity.kt    固定小组件 Activity
        │   └── util/
        │       ├── DailyResetWorker.kt    每日重置 Worker
        │       └── DateUtils.kt
        └── res/
            ├── layout/              Activity 和 Widget 布局
            ├── xml/                 shortcuts.xml + widget_info.xml
            ├── drawable/            图标和背景
            ├── values/              字符串、颜色、样式
            └── mipmap-*             App 图标（多分辨率）
```

## 技术栈

组件：语言
- Kotlin 1.8 + Java 17

组件：构建
- Gradle 8.5 / AGP 7.4.2

组件：数据库
- Room 2.6.1（SQLite 封装）

组件：异步
- Kotlin Coroutines + Flow

组件：组件
- ViewModel + StateFlow

组件：小组件
- AppWidgetProvider + RemoteViews

组件：定时任务
- WorkManager 2.9.0

组件：依赖注入
- 手动 Factory（无 Hilt）

## 开发构建

环境要求：
- JDK 17（不是 JDK 21，否则会有 jlink 错误）
- Android SDK（compileSdk 34）
- Windows PowerShell 或 macOS/Linux 终端

编译命令：
```bash
cd task-checkin
./gradlew assembleDebug --no-daemon
```

指定 JDK 路径（如遇到 jlink 错误），在 gradle.properties 中配置：
```properties
org.gradle.java.home=C:\\jdk17\\jdk-17.0.2
```
（路径根据实际 JDK 安装位置修改）

安装到手机：
```bash
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

Android Studio 中打开：
1. File → Open → 选择 task-checkin 文件夹
2. 等待 Gradle Sync 完成
3. Build → Make Project
4. Run → Run 'app'（连接手机或模拟器）

## 已知问题 & 未来改进

- Checkbox 复用 Bug：快速切换任务时勾选状态可能错位（RecyclerView ViewHolder 复用未正确重置）
- AGP 版本 7.4.2 偏旧，compileSdk 34 存在警告，建议升级到 AGP 8.x
- Kotlin 版本 1.8.22 偏旧，建议升级到 1.9.x
- 无网络功能，纯本地应用
- 无备份/恢复功能
- 小组件最大显示数量受屏幕尺寸限制，任务过多时需要滚动
