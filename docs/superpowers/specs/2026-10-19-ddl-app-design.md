# DDL List 安卓应用 — 设计文档

- 日期：2026-10-19
- 状态：已获用户批准（2026-10-19 对话确认：精确提醒方案 + 纯本地存储）
- 类型：全新项目（架构级）
- 变更记录：实施裁决采纳 `compileSdk = 37`（钉死的最新 androidx 库要求 minCompileSdk=37，AGP 9.4.1 官方支持上限即 API 37）；`targetSdk` 维持 36。另因仓库路径含非 ASCII 字符与 AGP 9 默认值变更，`gradle.properties` 需三行兼容开关（overridePathCheck / builtInKotlin / newDsl），构建告警与 AGP 10 迁移记为已知债。

## 1. 目标

一个极简的 Deadline 管理安卓应用：用户录入 DDL 条目（标题、截止日期时间、备注），应用在截止时刻之前的用户自定义提前量（可多个）准点发送系统通知提醒，页面按时间状态分组展示并显示实时倒计时。

成功标准：
- 命令行在本机可编译出可安装的 debug APK；
- 添加 DDL 后，自定义提前提醒准点到达；
- 手机重启、应用被杀进程后提醒不丢；
- 过期条目自动标红置顶，不再触发无意义提醒。

非目标（V1 明确不做）：云同步/账号、桌面小组件、重复周期 DDL、打卡统计、iOS 版本。

## 2. 技术选型

| 项 | 选择 | 理由 |
|---|---|---|
| 语言 | Kotlin 2.x | 安卓官方首选，现代空安全 |
| UI | Jetpack Compose + Material 3 | 单 Activity、声明式、代码量少、观感新 |
| 架构 | MVVM：`ViewModel(StateFlow) → Repository → Room/AlarmScheduler` | 官方推荐的小型分层 |
| 依赖注入 | Application 内手动容器（不引入 Hilt） | 项目小，省构建复杂度 |
| 存储 | Room | 官方持久层，KSP 代码生成 |
| 提醒 | AlarmManager `setExactAndAllowWhileIdle` + 降级 `setAndAllowWhileIdle` | DDL 工具核心是准时 |
| 时区 | `java.time`（minSdk ≥ 26 原生自带） | 避免 java.util.Date 的坑、便于单测 |
| 构建 | Gradle Wrapper + version catalog（`libs.versions.toml`） | 本机无全局 Gradle 也可构建 |
| SDK | minSdk 26；compileSdk/targetSdk 36 | 覆盖 Android 8.0+（约 97% 设备） |

初始化环境（已获用户同意）：安装 Android 命令行工具 + SDK（platform-tools、platforms;android-36、build-tools）到 `%LOCALAPPDATA%\Android\Sdk`，全程命令行构建，不依赖 Android Studio。

## 3. 系统结构

```
UI（Compose：列表页 / 添加编辑弹窗）
   │ StateFlow
ViewModel
   │
Repository
   ├── Room DdlDao（持久化）
   └── AlarmScheduler（接口）
            └── AlarmManager 实现（精确→非精确降级）

系统组件：
  DdlApplication           初始化 Room、依赖容器、通知渠道
  AlarmReceiver            收到闹钟 → 构建/发送通知
  BootReceiver             BOOT_COMPLETED → 重排所有未完成 DDL 的未来提醒
  NotificationHelper       渠道创建（IMPORTANCE_HIGH）、通知内容构建
```

依赖方向单向：UI → VM → Repository → (Room, Scheduler)。Receiver 依赖 Repository 与 Scheduler 完成重排。

## 4. 数据模型

```kotlin
@Entity(tableName = "ddls")
data class DdlEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val title: String,
  val note: String,                  // 可为空字符串
  val deadlineEpochMillis: Long,     // 截止时刻（UTC epoch 毫秒）
  val reminderLeads: List<Long>,     // 提前量，单位分钟，经 TypeConverter 序列化存储
  val isDone: Boolean = false,
  val createdAt: Long
)
```

派生概念：
- 触发时刻 = `deadlineEpochMillis - lead*60_000`；只对「晚于当前时间 && DDL 未完成」的触发时刻排闹钟。
- 每个闹钟用 `requestCode = (ddlId, lead) 组合哈希` 或 PendingIntent `Intent extras(id, lead)` 区分，取消时按 ID 全部撤销。

## 5. 界面设计（中文）

### 5.1 列表页（首页）
- 顶栏：应用名「DDL 清单」，右侧「＋」
- 分组顺序：**已过期（红） → 今天 → 明天 → 未来 7 天 → 更晚 → 已完成（默认折叠，可展开）**
- 卡片：标题（粗体）、截止时间（`M月d日 HH:mm`）、相对倒计时（`剩 3天14:22` / `已过期 2小时` 飘红）、提醒铃铛图标（有提醒则显示）
- 手势：左滑删除 → Snackbar「已删除 · 撤销」；点卡片 → 编辑
- 过滤：右上角/顶栏下方 chips 切换「全部 / 未完成 / 已完成」

### 5.2 添加/编辑弹窗（BottomSheet）
- 标题输入（必填）
- 截止日期选择器 + 时间选择器（Material3 `DatePicker`/`TimePicker`）
- 备注（可选）
- 提前提醒多选 chips：不提醒 / 5 分钟 / 15 分钟 / 30 分钟 / 1 小时 / 3 小时 / 1 天 / 3 天 / 7 天
- 「保存」按钮；编辑态多一个「标记完成/标记未完成」与「删除」入口

## 6. 提醒机制（核心流程）

1. 保存 DDL → Repository 落库 → `AlarmScheduler.reschedule(id)`：取消该 id 全部 PendingIntent → 遍历 `reminderLeads + 0min(到期时刻)`，过滤掉已过去的时刻 → 逐个 `setExactAndAllowWhileIdle()`。
2. `AlarmReceiver` 收到 → NotificationHelper 发通知：标题=ddl.title，正文=「已到期」或「还有 x 分钟/小时/天到期」，点击 → DeepLink 打开列表页。
3. 通知渠道：`ddl_reminders`，IMPORTANCE_HIGH（横幅+声音），Android 8+ 全部经由渠道。
4. `isDone=true` → 同时取消全部未发 alarm；状态回未完成时重排。
5. `BootReceiver` 开机时遍历全部未完成 DDL → 全量重排。
6. 时区/夏令时：保存的是绝对 epoch 毫秒，展示用 ZonedDateTime；排闹钟不受时区变更影响重算（epoch 不变）。

## 7. 权限与降级

| 权限 | 版本 | 处理 |
|---|---|---|
| POST_NOTIFICATIONS | 13+（API 33） | 首次启动运行时请求；拒绝后显示页内提示条 |
| SCHEDULE_EXACT_ALARM | 12+（API 31/33 细节） | 触发提醒排期前检查 `canScheduleExactAlarms()`；未授权 → 引导页跳系统设置；拒绝 → 降级 setAndAllowWhileIdle（非精确但可用，误差至分钟级） |
| RECEIVE_BOOT_COMPLETED | 全版本 | 清单声明，无需运行时请求 |

## 8. 边界情况

- 过期 DDL 在详情/列表持久置红，不再排任何触发点（含到期那一刻，都视为过期不响）。
- 提前量晚于当前时间但早于其他 lead：无相互影响，逐个独立排。
- 标题空 → 禁用保存按钮；日期时间必须晚于当前时间才允许保存（编辑历史条目除外）。
- 已完成的 DDL 不进倒计时、不排闹钟，但数据保留，可在「已完成」组删除。
- 数据库 schemaVersion = 2（执行期因新增 muted 字段由 1 升级并采用重建式迁移，见 §11 变更记录）。

## 9. 测试策略

- 纯 JVM 单测（不需要真机/模拟器）：
  - 触发时刻计算（`ReminderTimeCalculator`）：过滤过去时刻、lead 转换、epoch 换算；
  - 分组逻辑（`DdlGrouper`）：过期/今天/明天/7 天/更晚/已完成 的边界（23:59 与 00:00 等）；
  - Room Dao 用 instrumented 或跳过（V1 至少单测前两者）。
- 构建验证：`gradlew assembleDebug` 通过即产出 `app-debug.apk`。

## 10. 里程碑

1. 环境就绪（SDK + licenses + Gradle Wrapper）
2. 工程骨架可编译（空 Compose 页）
3. 数据层 + 仓库（Room、TypeConverter、DAO 测试可选）
4. 列表页 + 分组/倒计时显示
5. 添加/编辑弹窗 + 校验
6. 提醒机制（Scheduler、Receiver、Boot、渠道）
7. 权限引导 + 降级
8. 单测 + 全量构建 APK

## 11. 变更记录（执行期裁决）

- **「不提醒」= muted 静音（R24）**：§5.2 的「不提醒」chip 落地为实体字段 `muted`；与 §6.1 的冲突按 muted 优先——静音条目不排任何闹钟（含到期时刻）。schemaVersion 1 → 2，重建式迁移（未发布阶段）。非静音且未选提前量 = 仅到期时刻一条提醒。
- **权限/闹钟状态刷新（R22）**：精确闹钟横幅与通知提示条在回到前台（ON_RESUME）时重新核验；通知权限真源为 `areNotificationsEnabled()`。
- **已完成组默认折叠（R23）**：§5.1 的默认折叠在实现期补齐，头部可展开。
- **精确闹钟权限修正（R25）**：§7 权限清单移除 USE_EXACT_ALARM，仅保留 SCHEDULE_EXACT_ALARM；安卓 13+ 由用户授予，应用内横幅引导跳转系统设置。
- **恢复语义（R19）**：仓库 upsert 对已删行的恢复场景回退为按原 id 插入——「撤销删除」在生产可用；测试 Fake 与 Room 同语义。
- **DatePicker 锚定（R20）**：日期选择器初始选中与确认均按 UTC 午夜语义双向换算（纯显示锚定约定）。
