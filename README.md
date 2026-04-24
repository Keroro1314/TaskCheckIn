# TaskCheckIn — 任务打卡

一款 Android 本地任务管理应用，支持桌面小组件打卡、日程任务、日历视图、闹钟提醒、历史记录、自动每日重置。

<p align="center">
  <img src="https://img.shields.io/badge/version-1.1.2-blue" alt="v1.1.2">
  <img src="https://img.shields.io/badge/minSdk-24-green" alt="minSdk 24">
  <img src="https://img.shields.io/badge/targetSdk-34-green" alt="targetSdk 34">
  <img src="https://img.shields.io/badge/license-MIT-orange" alt="MIT">
</p>

## 功能一览

| 功能 | 说明 |
|---|---|
| 任务列表 | 添加/编辑/删除/拖动排序 |
| 打卡完成 | 点击即打卡，数据存入历史 |
| 撤回完成 | 底部栏常驻撤销按钮，误触打卡可一键撤回 |
| 撤回删除 | 底部栏撤销按钮同时支持恢复误删任务 |
| 编辑任务 | 所有任务均可点击编辑按钮修改内容，日程任务支持修改日期和时间 |
| 历史记录 | 查看每天完成了哪些任务 |
| 每日重置 | 凌晨 0:01 自动重置所有任务 |
| 桌面小组件 | 3x2 小组件，不进 App 也能打卡 |
| 一键添加小组件 | 长按 App 图标 → 快捷方式 → 直接固定小组件到桌面 |
| 日程任务 | 为任务设置指定日期，到期自动出现在当日列表 |
| 日历视图 | 月历展示，点击日期查看当天任务完成情况 |
| 闹钟提醒 | 为任务设置提醒时间，到点发送通知 |
| 电池优化白名单 | 引导用户将 App 加入电池优化白名单，确保闹钟准时触发 |
| 未完成任务提醒 | 每天 20:00 自动检查未完成任务，发送悬浮通知，支持「稍后提醒」和「已知晓」 |

## 快速开始

### 安装 APK

下载最新的 [Release](https://github.com/Keroro1314/TaskCheckIn/releases) 中的 APK，传到手机安装即可。

从源码构建：
```bash
cd task-checkin
./gradlew assembleDebug
```

APK 输出路径：
```
app/build/outputs/apk/debug/app-debug.apk
```

### 添加桌面小组件

**方式一：快捷方式（推荐）**
1. 长按手机桌面空白处
2. 找到「任务打卡」App，长按图标
3. 选择「添加小组件」快捷方式
4. 按提示操作

**方式二：手动添加**
1. 长按桌面空白处 → 点击「小组件」/「窗口小工具」
2. 找到「任务打卡」，长按拖到桌面即可

## 更新日志

### v1.1.2 (2026-04-24)

**新增功能**
- **未完成任务提醒**：每天 20:00 自动检查当日未完成任务，推送悬浮通知提醒用户，最多显示3个任务名称。支持「请稍后提醒」（15分钟后再次通知）和「我已知晓」（今日不再提示）两种操作。
- **删除恢复**：底部栏撤销按钮同时支持恢复误删任务，删除任务时会自动保存任务信息供撤销使用。

**Bug 修复**
- 修复撤回按钮偶发不响应的问题（StateFlow 初始化顺序修正）
- 修复撤回按钮状态更新延迟问题（协程执行顺序调整，确保 UI 立即响应）
- 修复「任务已添加」「已删除」等提示 Snackbar 遮挡底部操作栏的问题，改为 Toast 显示
- 修复 Database.kt 中重复 `getAllTasksSync()` 方法导致的编译错误
- 修复 IncompleteTaskCheckWorker 中类型推断失败的问题
- 修复 TaskWidgetFactory 调用同步方法时的空指针问题

**UI 优化**
- 底部操作栏按钮新增 MaterialCardView 边框样式，视觉更清晰

### v1.1.1 (2026-04-24)

**新增功能**
- **撤回完成**：底部栏新增常驻「撤销」按钮，打卡后可随时撤回，无需赶 Snackbar 超时
- **编辑所有任务**：编辑按钮对所有任务可见（不仅是日程任务），点击即可修改任务内容
- **日程任务编辑**：日程任务支持修改日期、时间和重复设置，保存后自动更新闹钟

**优化**
- 编辑按钮始终显示，编辑模式下自动隐藏
- 撤回按钮有任务可撤回时高亮，无任务时显示提示

### v1.1.0 (2026-04-23)

**新增功能**
- **日程任务**：为任务设置指定日期，任务到期时自动出现在当日打卡列表
- **日历视图**：新增日历页面，月历展示每天的任务完成情况，点击日期查看详情
- **闹钟提醒**：为任务设置提醒时间，到点发送系统通知（需通知权限 + 电池优化白名单）
- **底部弹窗**：新增任务改为 BottomSheetDialog，交互更自然
- **电池优化引导**：新增引导页面，帮助用户将 App 加入电池优化白名单

**技术改进**
- 从 kapt 迁移到 KSP（Room 注解处理），构建速度提升
- Kotlin 升级到 1.9.24，AGP 升级到 8.2.2
- Room 数据库新增 scheduled_date 字段，支持日程任务查询
- 修复日历页面主线程数据库查询导致的崩溃
- 修复电池优化页面主题冲突导致的崩溃
- 修复 Widget 布局中不支持的 `<View>` 标签

**已知限制**
- 小组件固定弹窗在部分 MIUI/HarmonyOS 设备上可能不显示（系统限制）
- 首次安装可能遇到 `INSTALL_FAILED_VERIFICATION`，需手动开启 USB 安装权限

### v1.0.0 (2026-04-22)

**初始发布**
- 任务列表管理（添加/编辑/删除/拖动排序）
- 打卡完成 + 历史记录
- 每日自动重置（WorkManager）
- 桌面小组件（AppWidgetProvider + RemoteViews）
- 一键添加小组件快捷方式

## 工作原理

### 整体架构

```
┌─────────────────────────────────────────────┐
│                   App 层                     │
│  MainActivity · HistoryActivity · CalendarActivity  │
│  TaskAdapter · MainViewModel               │
├─────────────────────────────────────────────┤
│                  Widget 层                   │
│  TaskWidgetProvider · WidgetPinActivity     │
│  DailyTaskWidgetService · ScheduledTaskWidgetService │
├─────────────────────────────────────────────┤
│                   数据层                     │
│  AppDatabase (Room) · TaskRepository        │
│  TaskHistoryRepository                     │
├─────────────────────────────────────────────┤
│                  定时任务                    │
│  DailyResetWorker · AlarmScheduler          │
│  BootReceiver · AlarmReceiver               │
│  IncompleteTaskCheckWorker                 │
└─────────────────────────────────────────────┘
```

### 数据库设计（Room）

**tasks** — 当前任务
| 字段 | 类型 | 说明 |
|---|---|---|
| id | Int (PK, auto) | 主键 |
| title | String | 任务名称 |
| isCompleted | Boolean | 是否已完成 |
| orderIndex | Int | 排序索引 |
| scheduledDate | String? | 日程日期（yyyy-MM-dd），null 表示每日任务 |
| reminderTime | String? | 提醒时间（HH:mm） |
| createdAt | Long | 创建时间戳 |

**task_history** — 打卡历史
| 字段 | 类型 | 说明 |
|---|---|---|
| id | Int (PK, auto) | 主键 |
| title | String | 任务名称（快照） |
| completedAt | Long | 完成时间戳 |

### 每日自动重置

使用 WorkManager 的 PeriodicWorkRequest，每天 0:01 执行一次，把所有每日任务（无 scheduledDate）的 isCompleted 设为 false。

### 闹钟提醒

使用 Android AlarmManager 精确定时：
- 设置提醒时通过 AlarmScheduler 注册 PendingIntent
- 到时间由 AlarmReceiver 接收广播，发送系统通知
- BootReceiver 监听开机广播，重新注册所有闹钟
- 需要用户授予通知权限并加入电池优化白名单

### 未完成任务提醒

每天 20:00 通过 WorkManager 定时检查：
- 查询当日未完成的每日任务和日程任务
- 发送带操作按钮的悬浮通知
- 「请稍后提醒」通过 AlarmManager 延迟15分钟再次触发
- 「我已知晓」通过 SharedPreferences 记录当日已忽略，下次检查跳过

### 小组件

基于 AppWidgetProvider 的 RemoteViews 系统：
- **每日任务小组件**：显示当日每日任务列表
- **日程任务小组件**：显示当天到期的日程任务
- 点击任务行直接打卡，不进 App
- 使用同步数据库读取（RemoteViewsFactory.onDataSetChanged 不支持协程）

## 项目结构

```
task-checkin/
├── build.gradle                      根构建配置
├── settings.gradle
├── gradle.properties
└── app/
    ├── build.gradle                 App 模块配置
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/taskcheckin/
        │   ├── TaskCheckInApp.kt    Application 类
        │   ├── data/
        │   │   ├── local/
        │   │   │   ├── Database.kt          Room 数据库 + DAO
        │   │   │   ├── TaskEntity.kt        任务实体
        │   │   │   └── TaskHistoryEntity.kt  历史实体
        │   │   └── repository/
        │   │       ├── TaskRepository.kt
        │   │       └── TaskHistoryRepository.kt
        │   ├── ui/
        │   │   ├── main/
        │   │   │   ├── MainActivity.kt
        │   │   │   ├── MainViewModel.kt
        │   │   │   ├── TaskAdapter.kt
        │   │   │   └── AddTaskBottomSheetDialog.kt
        │   │   ├── history/
        │   │   │   ├── HistoryActivity.kt
        │   │   │   └── HistoryViewModel.kt
        │   │   ├── calendar/
        │   │   │   ├── CalendarActivity.kt
        │   │   │   └── ScheduledTaskAdapter.kt
        │   │   └── BatteryOptimizationActivity.kt
        │   ├── widget/
        │   │   ├── TaskWidgetProvider.kt
        │   │   ├── WidgetPinActivity.kt
        │   │   ├── TaskWidgetService.kt / Factory.kt
        │   │   ├── DailyTaskWidgetService.kt / Factory.kt
        │   │   └── ScheduledTaskWidgetService.kt / Factory.kt
        │   └── util/
        │       ├── DailyResetWorker.kt
        │       ├── DateUtils.kt
        │       ├── AlarmScheduler.kt
        │       ├── AlarmReceiver.kt
        │       ├── BootReceiver.kt
        │       ├── NotificationHelper.kt
        │       ├── IncompleteTaskReminder.kt
        │       ├── ReminderActionReceiver.kt
        │       ├── ReminderLaterReceiver.kt
        │       └── IncompleteTaskCheckWorker.kt
        └── res/
            ├── layout/
            ├── xml/
            ├── drawable/
            ├── values/
            └── mipmap-*/
```

## 技术栈

| 类别 | 技术 |
|---|---|
| 语言 | Kotlin 1.9.24 |
| 构建 | Gradle 8.5 / AGP 8.2.2 |
| 注解处理 | KSP 1.9.24-1.0.20 |
| 数据库 | Room 2.5.2 |
| 异步 | Kotlin Coroutines + Flow |
| UI 架构 | ViewModel + StateFlow |
| 小组件 | AppWidgetProvider + RemoteViews |
| 定时任务 | WorkManager 2.9.0 + AlarmManager |
| 最低系统 | Android 7.0 (API 24) |
| 目标系统 | Android 14 (API 34) |

## 开发构建

### 环境要求

- **JDK 17**（不是 JDK 21，否则会有 jlink 错误）
- Android SDK compileSdk 34
- Windows PowerShell / macOS / Linux 终端

### 编译

```bash
cd task-checkin
./gradlew assembleDebug
```

如遇 jlink 错误，在 `gradle.properties` 中指定 JDK 路径：
```properties
org.gradle.java.home=C:\\jdk17\\jdk-17.0.2
```

### 安装到手机

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Android Studio

1. File → Open → 选择 task-checkin 文件夹
2. 等待 Gradle Sync 完成
3. Build → Make Project
4. Run → Run 'app'

## 已知问题

- 部分设备安装可能遇到 `INSTALL_FAILED_VERIFICATION`，需开启「USB 安装应用」权限
- 小组件固定弹窗在部分 MIUI/HarmonyOS 设备上可能不显示（系统对 requestPinAppWidget 支持不完整）
- 小组件任务过多时需要滚动，受屏幕尺寸限制

## License

[MIT](LICENSE)
