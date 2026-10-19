# DDL 清单

极简 Android Deadline 管理应用：本地记录截止时间、按天/时/分倒计时、准点通知提醒。数据全部保存在设备本地，无账号、无网络请求。

## 功能

- **分组列表**：按「已过期 / 今天 / 明天 / 未来 7 天 / 更晚 / 已完成」自动分组，组内按截止时间升序。
- **实时倒计时**：每条 DDL 显示剩余时间（天/时/分），截止后转为逾期状态。
- **筛选**：全部 / 未完成 / 已完成 三档切换。
- **侧滑删除**：左滑删除并提供「撤销」，误删可恢复。
- **编辑面板**：底部弹出式编辑，支持标题、备注、截止时间，以及 5 分钟 ~ 7 天多档提前提醒（可多选），另有「不提醒」选项。
- **提醒投递**：优先使用 `AlarmManager` 精确闹钟（`setExactAndAllowWhileIdle`）；未获得精确闹钟权限时自动降级为非精确闹钟，提醒可能略有延迟。通知渠道为高优先级。
- **开机重排**：监听 `BOOT_COMPLETED` 与 `MY_PACKAGE_REPLACED`，重启或更新后自动恢复所有未到期的提醒。

## 安装使用

1. 在 Android 8.0+ 手机上安装 `app/build/outputs/apk/debug/app-debug.apk`（侧载即可，无需 Play 商店）。
2. Android 13+ 首次启动会请求「通知」权限——必须允许才能收到提醒；若误拒，页面顶部会出现提示条，点击可跳到系统设置。
3. 点击右下角 ＋ 添加 DDL，可勾选多个提前量（5 分钟 ~ 7 天）；不勾选则仅在截止时刻提醒，勾选「不提醒」则完全静音。
4. **精确闹钟**：安卓 13+ 需在系统设置允许精确闹钟，应用内横幅可跳转；安卓 12+ 同样需要在系统里允许「闹钟和提醒」权限。未开启时列表顶部会显示提示条，点击跳转系统设置；在开启之前提醒仍会送达，但可能不准时。

## 从命令行构建

需要 JDK 21 与 Android SDK（compileSdk 37、build-tools 37）。仓库已配置阿里云镜像源，国内网络可直接构建；APK 为 debug 签名，仅供侧载测试，发布前需替换为正式签名。在仓库根目录执行：

```powershell
# 构建 debug APK -> app/build/outputs/apk/debug/app-debug.apk
.\gradlew.bat assembleDebug

# 运行单元测试
.\gradlew.bat :app:testDebugUnitTest
```

## 技术要点

Kotlin 2.4.21 · Jetpack Compose Material3（BOM 2026.09.00）· Room 2.8.5 + KSP · AlarmManager（精确优先，无权限自动降级）· BOOT_COMPLETED / MY_PACKAGE_REPLACED 开机重排 · AGP 9.4.1，minSdk 26（Android 8.0）/ targetSdk 36。

## 项目结构

```
app/src/main/java/com/gammac3/ddlapp/
├── data/      Room 实体、DAO、数据库、仓库
├── logic/     分组与提醒触发时间计算（纯 Kotlin，有单测覆盖）
├── model/     列表行模型
├── notify/    闹钟调度、广播接收器、通知构建
└── ui/        列表页、编辑面板、主题
```
