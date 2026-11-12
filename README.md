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
| 响铃 | 全屏 + 锁屏展示 + 点亮屏幕；锁屏下提供「静音 / 关闭」，前台服务播铃并保证可停止；通知栏兜底「贪睡 / 停止」 |
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

## 已知限制

- 已使用 JDK 17.0.21 + Gradle 8.7 + Android SDK 34 实际编译通过（`assembleDebug` BUILD SUCCESSFUL），产物 `qinghao-alarm-debug.apk` 位于仓库根目录，为 debug 签名，可直接安装到 Android 8.0+ 设备。
- 尚未在真机上验证响铃、锁屏全屏页、唤起目标应用与国产 ROM 白名单行为。
- 国产 ROM（MIUI / EMUI / ColorOS 等）需要手动开启「自启动」「锁屏显示」「后台弹出界面」白名单，否则响铃页可能无法自动弹出。
- `QUERY_ALL_PACKAGES` 属于敏感权限，若上架 Google Play 需在控制台声明用途（闹钟唤醒目标应用）。
- 唤醒应用依赖目标应用存在桌面入口（`getLaunchIntentForPackage`），无入口的应用不会被列出。
