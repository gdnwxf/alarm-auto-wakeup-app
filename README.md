# 轻好闹钟（Alarm + 自动唤醒应用）

一个纯 Kotlin + Jetpack Compose 的 Android 闹钟应用。除常规的重复设置、铃声、震动、贪睡之外，重点是**闹钟响起时自动唤醒一个或多个指定应用**。

- 包名：`com.alarm.qinghao.wakeup`
- minSdk 26 / targetSdk 34
- AGP 8.5.2、Kotlin 2.0.20、Compose BOM 2024.09.00、Room 2.6.1（KSP）

## 功能

| 模块 | 说明 |
| --- | --- |
| 重复 | 周一~周日 **Checkbox 多选**，快捷「每天 / 工作日 / 仅一次」；响铃后自动重排下一次，仅一次的响完自动关闭 |
| 时间设置 | 双列滚轮（小时 00-23 / 分钟 00-59），固定 24 小时制；新建闹钟默认取系统当前时间，编辑时回显已保存值 |
| 铃声 | 系统铃声选择器（闹钟 / 铃声 / 通知音均可选）、静音、音量渐强 0~30 秒、试听 |
| 震动 | 关闭 / 短震 / 长震 / 心跳 / 渐强（有限时长波形 + 定时重投） |
| 贪睡 | 间隔 5/10/15/20 分钟（默认 10 分钟），最多 0/1/2/3/5 次（默认 3 次），用独立 PendingIntent 不干扰下次响铃 |
| 响铃 | 全屏 + 锁屏展示 + 点亮屏幕；锁屏下提供「静音 / 关闭」，**按电源键（熄屏）或音量键也可静音**，前台服务播铃并保证可停止；通知栏兜底「贪睡 / 停止」 |
| 主界面 | 模拟表盘（秒针实时走动）+「X 小时 Y 分钟后响铃」倒计时 + 白色圆角卡片 + 圆形新建按钮 |
| **唤醒应用** | 从已安装的可启动应用中多选（最多 5 个），按「启动间隔」依次 `startActivity` 或同时启动；拉起失败时**通知栏说明具体原因** |
| 其他 | 标签、响铃时长上限（超时自动停）、列表显示下次响铃时间、权限缺失提示、响铃中主界面横幅可直接关闭 |

## 工程结构

```
app/src/main/java/com/alarm/qinghao/wakeup/
├── AlarmApplication.kt          # 进程入口：DB / Scheduler / 通知渠道 / 启动时补注册
├── MainActivity.kt              # Compose 导航（列表 / 编辑）+ 权限状态
├── data/                        # Room：AlarmEntity、Dao、Repository、重复规则、震动模式
├── alarm/
│   ├── AlarmScheduler.kt        # setAlarmClock 注册、贪睡、取消
│   ├── AlarmReceiver.kt         # 到点入口：重排下次 + 起服务 + 拉起全屏页
│   ├── BootReceiver.kt          # 开机 / 时间变更重建
│   ├── AlarmService.kt          # 前台服务：播放、震动、通知、超时
│   ├── AlarmRingActivity.kt     # 全屏响铃页 + 唤醒目标应用
│   └── RingingStateHolder.kt    # 进程内响铃状态（服务 → 页面）
├── ui/                          # 列表页、编辑页、应用选择页、响铃页、主题
└── util/                        # 播放器、铃声、震动、应用列表、权限
```

## 构建

需要 JDK 17 与 Android SDK Platform 34。

```bash
# 方式一：Android Studio 直接打开工程（会自动同步 Gradle 与依赖）
# 方式二：命令行
gradle wrapper --gradle-version 8.7   # 仓库未内置 gradle-wrapper.jar，需先生成
./gradlew assembleDebug               # 产物：app/build/outputs/apk/debug/app-debug.apk
```

> 注意：仓库内只有 `gradle/wrapper/gradle-wrapper.properties`，没有 `gradle-wrapper.jar` 与 `gradlew` 脚本（二进制文件无法离线生成）。用 Android Studio 打开会自动补齐；或用已安装的 Gradle 执行上面的 `gradle wrapper`。

## 关键实现说明

1. **精准到点**：`AlarmManager.setAlarmClock()` 注册，锁屏/息屏也能准时触发并显示系统闹钟图标。无精确闹钟权限时降级为非精确闹钟，并在列表页提示去授权。
2. **响铃链路**：`AlarmReceiver`（重排下次 → 启动 `AlarmService` 前台服务 → 拉起 `AlarmRingActivity`）→ 服务播放铃声与震动，并发送带 `fullScreenIntent` 的通知兜底；页面仅为控制层，因此页面被销毁也不会失声。
3. **唤醒目标应用**：必须由**可见的 Activity** 发起 `startActivity`，否则会被 Android 10+ 的后台启动限制拦截。响铃页先稳定展示约 1.2 秒，再按设置间隔依次拉起目标应用；用户按返回即可回到响铃页关闭。被拉起应用后仍可通过通知栏「停止」结束响铃。
4. **应用列表**：通过 `ACTION_MAIN` + `CATEGORY_LAUNCHER` 查询，Manifest 中声明 `QUERY_ALL_PACKAGES` 与 `<queries>` 保证 Android 11+ 可见性。
5. **静音与震动独立**：铃声可设为静音，同时保留震动，适合会议等场景。

## 资源与稳定性（防卡死）

响铃是需要持有系统资源的场景，代码对每条资源路径都做了释放与上限约束：

| 资源 | 处理方式 |
| --- | --- |
| `MediaPlayer` | 播放失败（`prepare()` 抛异常）时显式 `release()` 局部实例；停止/切换闹钟/服务销毁均释放，不会叠加播放 |
| 震动 | 波形**有限时长且不循环**（`repeat = -1`），由服务按周期重新投递；即使进程被系统杀死，最多再震 2~3 秒，不会无限震动 |
| `WakeLock` | 超时与响铃时长对齐（响铃时长 + 1 分钟），`releasePlayback()` 与 `onDestroy` 双重释放 |
| 延迟任务 | `AlarmService` 的 Handler 在 `onDestroy` 统一 `removeCallbacksAndMessages`；唤醒应用的延迟队列可取消（`WakeApps.cancelPending()`），页面销毁后不再弹出应用 |
| 协程 | 服务级 `CoroutineScope` 在 `onDestroy` cancel；页面级使用 `rememberCoroutineScope` 自动取消 |
| DB 访问 | 广播接收器/服务均改为 `goAsync` + 后台协程或 `Dispatchers.IO`，主线程零阻塞，不会因查库 ANR |
| 唤醒数量 | 单次响铃最多唤醒 **5 个**应用（UI 限制 + 保存时裁剪 + 执行时 `take`，三重保护） |
| 闹钟数量 | 最多 **50 个**，避免大量 `setAlarmClock` 拖累系统调度 |
| 重复触发 | 同一闹钟 + 同一贪睡次数在 10 秒内的重复回调直接忽略（兜底非精确闹钟抖动） |
| 响铃时长 | 1~60 分钟强制约束，超时自动停止；响铃页时间刷新随 `Lifecycle.RESUMED` 暂停，后台不空转 |
| 图标内存 | 目标应用图标按 72px 解码为 `ImageBitmap`，离开选择页即随之释放 |

## 兼容性验证

| 设备 / 系统 | 验证项 | 结果 |
| --- | --- | --- |
| 华为 ALN-AL00（Mate 60，Android 12 / HarmonyOS 4.x） | 响铃页自动显示、目标应用自动拉起、电源键静音、无崩溃 | ✅ 通过 |
| 同上：**亮屏 + App 在后台**，未开「后台活动」权限 | 响铃声正常，但 `activity start fail: Background activity start`，页面不弹、不唤起应用 | ❌ 复现并定位 |
| 同上：开启「后台活动」后亮屏复测 | 响铃页正常显示，`唤起 com.larksuite.suite 成功=true`，Lark 在前台 | ✅ 通过 |
| 同上（后台启动被拦截场景） | 点通知进入 App 后自动重试未成功的目标应用，且不重复弹出已成功的 | ✅ 逻辑已实现，待更多场景复测 |
| Android 模拟器（API 28） | 响铃、贪睡、唤醒应用、失败通知、多选删除、50 条列表滚动（Janky 2.01%） | ✅ 通过 |
| 小米 / OPPO / vivo / 荣耀等 | 后台弹出、自启动限制 | ⏳ 未实测，需按下方白名单设置 |

华为实测日志（2026-11-12）：
```
# 未开后台活动权限（亮屏）
W ActivityTaskManager: activity start fail: Background activity start
  callingUidProcState: FOREGROUND_SERVICE, allowBackgroundActivityStart: false

# 开启后（亮屏）
I ActivityTaskManager: Making visible and scheduling visibility: AlarmRingActivity
I AlarmRingActivity: 唤起 com.larksuite.suite 成功=true
D HwActivityTaskManagerServiceEx: pkg:com.larksuite.suite visible:true
```

## 已知限制

- 已使用 JDK 17.0.21 + Gradle 8.7 + Android SDK 34 实际编译通过（`assembleDebug` BUILD SUCCESSFUL），产物 `qinghao-alarm-debug.apk` 位于仓库根目录，为 debug 签名，可直接安装到 Android 8.0+ 设备。
- 已在华为 ALN-AL00（Android 12 / HarmonyOS 4.x）真机验证响铃页、唤醒应用与电源键静音；小米 / OPPO / vivo / 荣耀等未实测。
- 国产 ROM（MIUI / EMUI / ColorOS 等）建议开启「自启动」「锁屏显示」「后台弹出界面」白名单。华为实测：**不开启「后台活动」时，亮屏且 App 在后台会被拦截**（`allowBackgroundActivityStart: false`），开启后正常。
- 同一时间可创建多个闹钟（已验证），但同时触发时服务为单实例，仅播放一次铃声/保留一个通知。
- `QUERY_ALL_PACKAGES` 与 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 属于敏感权限，上架 Google Play 需在控制台申报用途（闹钟类应用允许）。
- 唤醒应用依赖目标应用存在桌面入口（`getLaunchIntentForPackage`），无入口的应用不会被列出。
- 华为等部分 ROM 移除了 `sqlite3`，调试时无法通过 `run-as sqlite3` 改库，需走 UI 操作。
