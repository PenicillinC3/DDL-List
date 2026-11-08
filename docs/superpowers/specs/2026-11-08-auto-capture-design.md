# 自动记 DDL — 设计文档

- 日期：2026-11-08
- 状态：待用户批准（入口胶囊已定：A 墨色实心 / 26dp 紧凑版 / 文案「自动识别」）
- 类型：新增功能（非架构级；无 DB 迁移）
- 关联：`docs/superpowers/specs/2026-10-19-ddl-app-design.md`

## 1. 目标

在「DDL 清单」里增加一条从任意文字页面快速记 DDL 的路径：

1. 用户在 App 内开启「自动记 DDL」，系统无障碍服务授权一次；
2. 屏幕边缘出现一个可拖动的悬浮球；
3. 用户在微信 / QQ / 教务系统 / 学习通 / 邮件 / 浏览器 / 公众号等任意文字页面点一下悬浮球；
4. App 读取当前页文字，解析标题与截止时间，弹出「真·悬浮」编辑卡；
5. 用户确认（可改）后入库，3 秒内可撤销。

成功标准：

- 从点球到入库，正常路径不超过 10 秒、不超过 2 次输入；
- 中文 / 英文日期时间都能解析，解析结果始终由用户确认，不自动入库；
- 原文不落盘、不打日志、不上传；密码框跳过；App 不申请 `INTERNET`；
- 现有 64 个单测全绿，新增解析器测试后仍全绿，`assembleDebug` 通过；
- 真机上无障碍服务、悬浮球 overlay、后台 Activity 启动三项均可用。

非目标（v1 明确不做）：

- 图片 / 海报 OCR；只处理文字页面；
- 页面变化自动监听 / 连续抓取；只在点球时读一次；
- 云同步、账号、跨设备；
- 自动入库 / 静默添加；必须经过编辑卡确认；
- 对微信聊天记录做长期缓存或索引；
- Play Store 上架合规改造（当前为侧载分发）。

## 2. 已锁定决策

| 决策 | 结论 | 理由 |
|---|---|---|
| 入口 | 顶栏胶囊 `[星芒+加号] 自动识别`，A 墨色实心，26dp | 用户选定；一眼可见、一点即达 |
| 抓取方式 | 悬浮球＋点按抓屏 | 不做连续监听；隐私与耗电可控 |
| 悬浮球实现 | `TYPE_ACCESSIBILITY_OVERLAY` | 不申请 `SYSTEM_ALERT_WINDOW`；由无障碍服务托管 |
| 编辑卡实现 | Dialog 主题浮动 Activity（路线 A） | 真悬浮小卡；源 App 短暂失焦 |
| 识别范围 | 文字页面 | v1 不做图片 OCR |
| 解析器 | 纯 Kotlin，中英文日期/时间 | 可 JVM 单测；不依赖 Android |
| 入库 | 复用 `DdlRepository.upsert` | 闹钟、小组件、列表刷新管线不变 |
| 数据 | 无 DB 迁移 | 复用现有 `DdlEntity` |
| 版本 | 1.17.0 / code 28 | 功能版本 |

## 3. 用户流程

### 3.1 首次开启

1. 用户点顶栏胶囊「自动识别」→ 打开设置卡。
2. 设置卡显示：悬浮球开关（关）、无障碍服务（未开启）、识别范围、隐私说明。
3. 用户打开「悬浮球」→ 若无障碍未开启，设置卡提示并跳转系统无障碍设置；同时记一个一次性 `pendingEnable`。
4. 用户在系统设置里开启「DDL 清单 · 自动识别」；Android 13+ 侧载若开关灰色，按提示去「应用信息 → ⋮ → 允许受限设置」。
5. 服务连接后，若 `pendingEnable` 为真：自动打开悬浮球开关并显示球；首次显示 3 秒气泡「点我识别当前页」。

### 3.2 日常抓取

1. 用户在任意文字页面点悬浮球（拖动不触发）。
2. 服务读取当前活动窗口文字 → 解析候选。
3. 服务隐藏悬浮球，启动 `DdlCaptureActivity`；源 App 变暗、短暂失焦。
4. 编辑卡显示解析结果：
   - 多候选：顶部 chips 切换；
   - 单候选：直接填字段；
   - 无截止：默认 now+1h，并显示提示；
   - 无文字 / 读不到：空草稿＋提示，用户可手动补。
5. 用户确认或修改 → 点「添加」。
6. 若有相似 DDL：显示「已存在相似的 DDL」＋「仍然添加」；用户可取消或坚持。
7. 入库成功 → 编辑卡关闭，源 App 恢复；屏幕底部出现 3 秒「已添加 · 撤销」。
8. 点「撤销」→ 删除刚入库的条目（复用 `repository.delete`）；3 秒后自动消失。

### 3.3 关闭

- 设置卡关闭「悬浮球」→ 立即移除球；无障碍服务保持系统开启状态（用户可在系统设置里关）。
- App 内开关只控制悬浮球显隐，不控制无障碍服务本身；设置卡明确写出这一点。

## 4. 界面设计

### 4.1 顶栏胶囊入口（已定稿）

位置：`TopAppBar` 的 `actions` 区，位于标题与「⋯」之间。

| 项 | 规格 |
|---|---|
| 视觉高度 | **26dp** |
| 实测宽度 | **≈81dp**（中文「自动识别」） |
| 圆角 | 999（全圆角） |
| 图标 | 15dp；24×24 VectorDrawable，星芒＋裸加号，**无外圈** |
| 文字 | 11sp / FontWeight.SemiBold（600） |
| 内边距 | 左 7dp、右 9dp |
| 图标与文字间距 | 4dp |
| 触摸热区 | ≥48dp（用 `minimumInteractiveComponentSize()` 补，不改视觉） |
| 开启态 | 背景 `inverseSurface`，内容 `inverseOnSurface`，1dp 同色边框 |
| 关闭态 | 透明背景，1dp `outline` 边框，内容 `onSurface` |
| 文案 | 中文「自动识别」/ 英文「Auto capture」 |
| 点击 | 打开设置卡；不直接切换开关 |

标题保持 22sp / Bold，不因胶囊缩小而放大；标题 `maxLines=1`、`overflow=Ellipsis`。360dp 下标题、胶囊、⋯ 有余量；320dp 下标题可省略，胶囊不缩。

图标资源：`res/drawable/ic_capture_sparkle.xml`。星芒为四角星，加号位于右下，两者之间留白，不再有圆形底。开启/关闭只换 tint，不换图形。

无障碍：胶囊可见文字已说明用途；额外加 `stateDescription`（「已开启」/「未开启」），读屏可感知状态。

### 4.2 设置卡

实现：`ModalBottomSheet`，与现有编辑器同一套容器语言；由胶囊点击打开。

结构：

```
自动记 DDL                        [运行中 / 未开启]   ×
────────────────────────────────────────────
悬浮球                              [Switch]
在别的 App 上方显示一个「＋」，点一下识别当前页

无障碍服务                          系统设置 ›
已开启 · 只在点悬浮球时读取当前页文字
（未开启时：未开启 · 需要手动授权一次）

识别范围
文字页面 · 图片/海报暂不支持

[隐私] 原文不保存、不上传；密码框会跳过。应用本身没有联网权限。

（Android 13+ 未连接时）开不了？应用信息 → ⋮ → 允许受限设置
（ColorOS）还需允许自启动 / 后台运行，悬浮球才不会被系统回收
```

行为：

- 「悬浮球」开关：
  - 服务已连接：立即显示/移除球，写 `CapturePrefs`。
  - 服务未连接：不直接打开；设置 `pendingEnable=true`，跳系统无障碍设置；服务连接后自动打开。
- 「系统设置 ›」：`Settings.ACTION_ACCESSIBILITY_SETTINGS`。
- 「应用信息」：`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`（Android 13+ 受限设置用）。
- 状态 chip：`运行中` = 服务已连接 && 悬浮球开关开；否则 `未开启`。
- 隐私说明常驻，不折叠。

### 4.3 悬浮球

| 项 | 规格 |
|---|---|
| 尺寸 | 44dp 圆形 |
| 图标 | 22dp，同一枚星芒＋加号；开启态反白 |
| 背景 | `inverseSurface`，alpha 0.94，4dp 阴影 |
| 初始位置 | 右侧边缘，垂直约 40% |
| 拖动 | 跟手移动；松手吸附最近左/右边缘（150ms 动画） |
| 位置持久化 | `CapturePrefs` 存「左/右边缘 + y 比例」；旋转后 clamp |
| 点击阈值 | 移动 < 8dp 视为点击；≥8dp 视为拖动，不触发抓取 |
| 触觉 | 点击触发时 `performHapticFeedback` |
| 隐藏 | 本 App 前台、桌面、锁屏、编辑卡打开、服务断开 |
| 首次提示 | 首次显示时球旁气泡「点我识别当前页」3 秒，只显示一次 |

隐藏逻辑只监听 `TYPE_WINDOW_STATE_CHANGED` 的包名/锁屏状态，**不读取页面文字**；文字提取只在点击时发生。

### 4.4 捕获编辑卡（路线 A）

实现：`DdlCaptureActivity`，Dialog 主题、全屏透明窗口、系统 dim 0.35；Compose 画一张居中的浮动卡。

| 项 | 规格 |
|---|---|
| 卡片宽度 | `min(screenWidth - 32dp, 420dp)` |
| 圆角 | 24dp |
| 背景 | `surface`，8dp elevation |
| 内边距 | 20dp |
| 最大高度 | 屏幕安全区 - 48dp，内部可滚动 |
| 位置 | 居中；`WindowInsets.safeDrawing` 避让状态栏/导航栏/输入法 |
| 关闭 | 点 ×、返回键、点卡片外（全屏透明 scrim 接收点击） |

内容从上到下：

1. 头部：15dp 星芒图标 +「自动识别」18sp/700 + 右侧 ×；副标题「来自当前页面」11sp `onSurfaceVariant`。
2. 候选 chips（仅多候选时）：`FlowRow`，高度 30dp，圆角 999；label 为格式化截止时间（如「11月13日 23:59」/「Nov 13, 23:59」）；选中态 `inverseSurface` / `inverseOnSurface`，未选中 `surface` + `outline`。
3. 字段区（复用 `DdlEditorFields`）：
   - 标题（必填，单行）
   - 日期 / 时间（只读框 + 点击选择器）
   - 备注（可选，单行）
   - 提前提醒 chips（沿用现有 `CandidateLeads` 与 `LeadLabels`）
   - 「不提醒」chip（muted）
4. 提示行（按需）：
   - 无截止：「未识别到截止时间，已默认 1 小时后」
   - 无文字：「当前页面没有读到文字，手动补一下」
   - 过去时间：沿用 `editor_past_error`
   - 重复：「已存在相似的 DDL」+ 文字按钮「仍然添加」
5. 底部：`取消` TextButton（weight 1）+ `添加` Button（weight 2）；标题为空或截止时间在过去（新条目）时禁用。
6. 隐私脚注：10sp `onSurfaceVariant`，「只读取当前页文字，不保存原文；密码框会跳过。」

焦点与键盘：

- 解析到标题时**不自动弹键盘**，让用户直接点「添加」。
- 标题为空时自动聚焦标题并弹键盘。
- 日期/时间选择器复用现有 Material3 `DatePicker` / `TimePicker`。

关闭语义：

- 取消 / 返回 / 点外部：清空 `CaptureSession.pending`，通知服务恢复悬浮球，结束 Activity。
- 保存成功：清空 pending，通知服务显示撤销条，结束 Activity。

### 4.5 添加后撤销

- 由无障碍服务添加一个 `TYPE_ACCESSIBILITY_OVERLAY` 的胶囊，底部居中，宽约 180dp、高 40dp，`inverseSurface` 背景、`inverseOnSurface` 文字。
- 文案：「已添加 · 撤销」；点「撤销」删除刚入库的条目。
- 3 秒后自动移除；再次抓取时先移除旧的。
- 该 overlay `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL`，只有胶囊本身可点，不挡页面其他区域。
- 撤销删除复用 `DdlRepository.delete`；删除后闹钟/小组件按既有管线刷新。

### 4.6 空态与失败

| 情况 | 处理 |
|---|---|
| 无障碍未连接 | 点球不会出现（服务没起来）；设置卡显示未开启与系统设置入口 |
| `rootInActiveWindow == null` | 打开空编辑卡 +「当前页面读不到文字」提示；用户可手动补或取消 |
| 提取到文字但无日期 | 打开卡，标题尽力提取，截止默认 now+1h + 提示 |
| 提取到多个日期 | 最多 3 个候选 chips；默认选置信度最高 |
| 解析到过去时间 | 保留解析值，显示「截止时间必须晚于现在」，禁用添加；用户改时间 |
| 相似 DDL 已存在 | 显示重复提示；用户点「仍然添加」才入库 |
| 服务被系统杀 | 球消失；下次服务连接时按 `CapturePrefs` 恢复 |

## 5. 解析器设计

### 5.1 输入输出

```kotlin
data class DdlCandidate(
    val title: String,
    val deadline: ZonedDateTime?,   // null = 未识别到截止
    val confidence: Float,          // 0..1，用于排序/默认选中
    val sourceLine: String,         // 命中的原文行（仅内存，不落盘）
)

object DdlParser {
    fun parse(text: String, now: ZonedDateTime): List<DdlCandidate>
}
```

- 纯 Kotlin，无 Android 依赖；`java.time`。
- 输入是 `ScreenTextExtractor` 产出的多行文本，保留页面顺序。
- 输出按置信度降序、截止时间升序；最多 3 个。
- 解析器不依赖 UI locale，同时匹配中英文；英文页面在中文 UI 下也能解析，反之亦然。
- 解析器对空文本返回空列表；服务在空列表/读不到根节点时构造一个空候选（`title=""`、`deadline=null`、`confidence=0f`）交给编辑卡，保证「空草稿兜底」路径统一。

### 5.2 支持的日期/时间语法（v1）

中文：

| 类型 | 示例 |
|---|---|
| 绝对日期 | `2026年11月13日`、`2026-11-13`、`2026/11/13`、`11月13日`、`11月13号`、`11-13`、`11/13` |
| 相对日 | `今天`、`明天`、`后天`、`大后天`、`N天后`（1–30，阿拉伯/中文数字）、`一周后` |
| 周 | `周五`、`星期五`、`礼拜五`、`这周五`、`本周五`、`下周五`、`下周一` |
| 时间 | `23:59`、`23：59`、`9:00`、`9点`、`9点半`、`9点30分`、`上午9点`、`下午3点`、`中午12点`、`晚上9点`、`凌晨1点` |
| 关键词 | `截止`、`截止时间`、`截止日期`、`前`、`之前`、`DDL`、`deadline`、`due`、`submit by`、`before` |

英文：

| 类型 | 示例 |
|---|---|
| 月名日期 | `Nov 13`、`November 13`、`13 Nov`、`Nov 13, 2026`、`13 November 2026` |
| ISO | `2026-11-13` |
| 相对日 | `today`、`tomorrow`、`tonight`、`this Friday`、`next Monday`、`Friday` |
| 时间 | `23:59`、`11:59 PM`、`9am`、`9:30 am`、`noon`、`midnight` |
| 关键词 | `deadline`、`due`、`submit by`、`before`、`by` |

明确不支持（v1）：

- 纯数字斜杠且无上下文的 `11/13`、`13/11`（避免把比例/电话/日期混读）；仅当同一行有截止关键词或时间时按 `M/d` 解析；
- `下个月 5 号`、`月底`、`季度末`、`每週` 等模糊/周期表达；
- 时区缩写（`UTC+8`、`PST`）；统一按设备本地时区；
- 图片/海报 OCR。

### 5.3 解析规则

1. **日期解析**
   - 有年份：按该年。
   - 无年份：取「下一个发生」的日期；若今年的该日期已过，则明年。
   - 仅日期：默认时间 **23:59:00**（当天结束）。
   - 仅时间：今天该时刻；若已过，顺延到明天同一时刻。
   - `今晚` / `明晚` / `今晚 23:59`：锚定今天/明天，不因已过而顺延。
   - 周几：
     - `周五` / `星期五`：下一个周五（今天周五且时间未过则今天）。
     - `这周五` / `本周五`：本周（周一为一周起点）的周五；已过则保留过去时间，由编辑器提示。
     - `下周五`：下周的周五。
2. **时间解析**
   - `上午` / `凌晨`：0–11 点语义；`下午` / `晚上`：12 小时制转 24 小时（下午 3 点 → 15:00；晚上 12 点 → 00:00）。
   - `中午`：12:00；`noon` 12:00；`midnight` 00:00。
   - `9点半` → 09:30；`9点30分` → 09:30。
   - `11:59 PM` → 23:59；`12:00 AM` → 00:00。
3. **候选提取**
   - 按行扫描；每行找日期/时间片段。
   - 行内含截止关键词：置信度 +0.2；同时含日期+时间：+0.15；仅日期：+0.05；仅时间：−0.1。
   - 标题候选：
     - 优先取同一行日期/时间之前的文字（去掉关键词、标点）；
     - 否则取前 1–2 行中第一个「像标题」的行：长度 2–50、不是纯日期/时间、不是问候语/按钮文案；
     - 再否则取页面第一行非空文本。
   - 标题清理：去行首编号/项目符号（`1.`、`-`、`•`）、去尾部「截止/之前」等从句、合并空白、去首尾标点；最长 60 字符。
   - 相同截止时间（±1 分钟）的候选合并，保留置信度高者。
4. **无截止**
   - 返回一个 `deadline=null` 的候选，`confidence` 低；标题按上述规则尽力提取。
   - UI 默认 now+1h，并显示提示。
5. **置信度**
   - 0.9：关键词 + 日期 + 时间
   - 0.8：关键词 + 日期（默认 23:59）
   - 0.75：相对日 + 时间
   - 0.65：日期 + 时间，无关键词
   - 0.5：仅时间
   - 0.2：无截止
   - 仅用于排序/默认选中，不用于自动入库。

### 5.4 测试样本（至少覆盖）

中文：

- `【作业】第三章习题\n截止时间：11月13日 23:59\n请提交到学习通`
- `明天下午3点前交实验报告`
- `本周五 18:00 前提交小组作业`
- `下周一 09:00 答辩`
- `2026年12月1日 中午12点 报名截止`
- `今晚 23:59 前完成问卷`
- `3天后截止`
- `截止：11月13日`（无时间 → 23:59）
- `周五`（无时间 → 23:59）
- `11月13日 23:59`（无标题 → 第一行）
- `2026-11-13 23:59 DDL`
- `9点半前交`
- `下周五 18:00`
- `1月5日`（跨年 → 明年）
- `10月1日`（已过 → 明年）
- `11月13日前`（`前` 关键词）
- `没有截止时间的通知`（无截止 → null）

英文：

- `Deadline: Nov 13, 2026 11:59 PM`
- `Submit by Friday 5pm`
- `Due tomorrow at 9:00`
- `Assignment due November 13 at 23:59`
- `Next Monday 09:00 defense`
- `2026-11-13 23:59`
- `No deadline here`（null）
- `tonight 11:59 PM`
- `noon tomorrow`
- `this Friday 18:00`
- `13 Nov 2026, 9:30 am`

边界：

- 空文本 → 空列表。
- 超长文本（>12,000 字符）→ 截断后解析。
- 多个不同截止 → 最多 3 个候选，按置信度排序。
- 相同截止重复行 → 合并。
- 密码框内容不进入解析器（由提取器过滤）。
- 纯数字/电话/比例不误判为日期。

### 5.5 重复判定

纯 Kotlin 工具：

```kotlin
object CaptureDuplicate {
    fun isDuplicate(
        existing: List<DdlEntity>,
        title: String,
        deadlineMillis: Long,
    ): Boolean
}
```

规则：标题归一化（小写、去空白、去中英文标点）后相同，且截止时间差 ≤ 60 秒。仅同标题、不同截止视为不同条目（允许「每周作业」多次添加）。

## 6. 技术架构

### 6.1 包结构

```
com.gammac3.ddlapp.capture/
  DdlCaptureAccessibilityService.kt   // 无障碍服务：悬浮球、读屏、启动编辑卡
  ScreenTextExtractor.kt              // 节点树 → 多行文本
  DdlParser.kt                        // 纯 Kotlin 解析器
  CaptureModels.kt                    // DdlCandidate / CapturePayload
  CaptureSession.kt                   // 进程内会话（pending / 回调 / 撤销 id）
  CapturePrefs.kt                     // SharedPreferences + StateFlow
  CaptureServiceState.kt              // 服务连接状态 StateFlow
  CaptureDuplicate.kt                 // 重复判定
  DdlCaptureActivity.kt               // Dialog 主题浮卡
  CaptureSettingsSheet.kt             // 设置卡
  CaptureEntryCapsule.kt              // 顶栏胶囊
```

复用/改动：

- `DdlEditorSheet.kt`：抽取无状态 `DdlEditorFields`（标题/日期/时间/备注/提前量/静音），供编辑器和捕获卡共用；`DdlEditorSheet` 公开签名不变。
- `DdlListScreen.kt`：TopAppBar actions 增加 `CaptureEntryCapsule`；`DdlListScreen` 持有 `settingsOpen` 状态，胶囊点击置 true，`CaptureSettingsSheet(open, onDismiss)` 观察 `CapturePrefs` / `CaptureServiceState`。
- `DdlApplication.kt`：`AppContainer` 已提供 `repository` / `notifications`；服务通过 `applicationContext as DdlApplication` 获取，无需新依赖。
- `MainActivity.kt`：不改启动路径；捕获 Activity 独立。
- `AndroidManifest.xml`：新增 service / activity。
- `res/xml/accessibility_service_config.xml`、`res/values/styles.xml`、drawable、strings。

依赖方向：`DdlCaptureActivity → CaptureSession / DdlRepository`；`Service → ScreenTextExtractor / DdlParser / CaptureSession / CapturePrefs`；`UI capsule → CapturePrefs / CaptureServiceState`。解析器不依赖 Android。

### 6.2 AccessibilityService

```kotlin
class DdlCaptureAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var instance: DdlCaptureAccessibilityService? = null
            private set
    }
    // onServiceConnected: instance = this; CaptureServiceState.connected = true;
    //                     按 prefs 创建悬浮球；pendingEnable 则自动开启
    // onAccessibilityEvent: 只更新悬浮球显隐；不提取文字
    // onInterrupt: no-op
    // onDestroy/onUnbind: 移除 overlay；connected = false; instance = null
}
```

- `accessibilityServiceInfo`：`canRetrieveWindowContent=true`；事件类型只开 `typeWindowStateChanged`（用于显隐），不开 `typeAllMask`。
- 服务声明 `android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"`、`android:exported="true"`、`android:label`、`android:description`。
- `isAccessibilityTool` 设为 `false`（这是便利功能，不是残障辅助工具；保持诚实，Android 13+ 会走受限设置提示）。
- 服务不申请 `FOREGROUND_SERVICE`；无障碍服务由系统托管。
- 服务不写任何页面文字到日志/文件；异常只记通用消息，不带正文。

### 6.3 ScreenTextExtractor

```kotlin
object ScreenTextExtractor {
    fun extract(root: AccessibilityNodeInfo?): String
}
```

规则：

- 从 `rootInActiveWindow` 开始；为 null 时尝试 `windows.firstOrNull { it.isActive }?.root`。
- 深度优先/广度优先均可；按节点顺序收集 `text` 与 `contentDescription`。
- 跳过：
  - `!isVisibleToUser`
  - `packageName == 本应用包名`（跳过自己的悬浮球/撤销条）
  - `isPassword == true`（密码框）
  - 空文本 / 纯空白
- 每个节点的文本按 `\n` 拆行、trim；连续重复行去重。
- 上限：2,000 个节点、40 层深度、12,000 字符；超限截断。
- 不调用 `ClipboardManager`；不截图；不写磁盘。
- 节点回收：按当前 Android API 约定处理（API 33+ `recycle()` 为 no-op），避免在遍历中重复回收。

### 6.4 CaptureSession

进程内单例，不持久化：

```kotlin
object CaptureSession {
    @Volatile var pending: CapturePayload? = null
    @Volatile var cardOpen: Boolean = false
    @Volatile var lastSavedId: Long? = null
    var onCardClosed: (() -> Unit)? = null
    var onSaved: ((Long) -> Unit)? = null
}
```

- 服务：提取 → 解析 → `pending = CapturePayload(...)` → `cardOpen = true` → 隐藏球 → 启动 Activity。
- Activity：`onCreate` 读取 `pending`；旋转重建时不清空（编辑值用 `rememberSaveable`），`onFinish` 清空。
- 保存成功：`lastSavedId = id`，`onSaved?.invoke(id)`；服务显示撤销条。
- 取消/关闭：`pending = null`，`onCardClosed?.invoke()`；服务恢复球。

### 6.5 DdlCaptureActivity

- `ComponentActivity` + `enableEdgeToEdge()` + `DdlAppTheme`。
- 窗口：`onCreate` 里显式 `window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)` + `window.setDimAmount(0.35f)`；全屏透明、无标题。卡片外由 Compose 全屏 scrim 接收点击并关闭（不依赖系统 outside-touch）。
- 读取 `CaptureSession.pending`；为空则 `finish()`。
- 状态：
  - `selectedCandidate`
  - `title` / `note` / `z` / `leads` / `muted`
  - `duplicateWarning` / `saving`
- 默认：
  - 有候选：用第一个候选的 title / deadline；
  - 无截止：`now.plusHours(1).withSecond(0).withNano(0)`；
  - 无标题：空，聚焦标题。
- 保存：
  1. `title.isBlank()` → 禁用；
  2. 新条目且 `z <= now` → 显示 `editor_past_error`，禁用；
  3. 重复判定：`repository.observeAll().first()` → `CaptureDuplicate.isDuplicate`；命中且未确认 → 显示重复提示；用户点「仍然添加」后置 `confirmedDuplicate=true`；
  4. `repository.upsert(DdlEntity(...))`；`isDone=false`、`createdAt=now`、`muted`、`leadsMinutes` 去重排序；
  5. `CaptureSession.lastSavedId = id`；`onSaved?.invoke(id)`；finish。
- 取消/返回：清空 pending；`onCardClosed`；finish。
- 旋转：`rememberSaveable` 保存编辑值；`pending` 保留到 finish。

### 6.6 悬浮球与撤销条

- 悬浮球与撤销条都用 `WindowManager` + `TYPE_ACCESSIBILITY_OVERLAY`。
- 悬浮球参数：`WRAP_CONTENT`、`FLAG_NOT_FOCUSABLE`、`PixelFormat.TRANSLUCENT`、`gravity=TOP|START`；触摸监听自己处理拖动/点击。
- 拖动：`ACTION_DOWN` 记起点；`ACTION_MOVE` 超过 slop 后 `updateViewLayout`；`ACTION_UP` 若未超 slop → `captureCurrentPage()`；否则吸附边缘并保存。
- 吸附：计算到左/右边缘距离，150ms `ValueAnimator` 到目标 x；y clamp 到安全区。
- 撤销条：底部居中，`FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL`；点击「撤销」→ 服务协程 `repository.delete(id)`；3s 后 remove。
- 服务持有一个 `CoroutineScope(Dispatchers.Main + SupervisorJob())`，`onDestroy` 取消。

### 6.7 Manifest / 资源

`AndroidManifest.xml` 新增：

```xml
<service
    android:name=".capture.DdlCaptureAccessibilityService"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:exported="true"
    android:label="@string/capture_service_label">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config" />
</service>

<activity
    android:name=".capture.DdlCaptureActivity"
    android:exported="false"
    android:excludeFromRecents="true"
    android:launchMode="singleTop"
    android:taskAffinity=""
    android:theme="@style/Theme.DdlCaptureDialog"
    android:windowSoftInputMode="adjustResize" />
```

`res/xml/accessibility_service_config.xml`：

```xml
<accessibility-service
    android:accessibilityEventTypes="typeWindowStateChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:notificationTimeout="100"
    android:canRetrieveWindowContent="true"
    android:description="@string/capture_accessibility_description"
    android:settingsActivity="com.gammac3.ddlapp.MainActivity"
    android:isAccessibilityTool="false" />
```

`res/values/styles.xml`：

```xml
<style name="Theme.DdlCaptureDialog" parent="@android:style/Theme.Material.Light.NoActionBar">
    <item name="android:windowIsTranslucent">true</item>
    <item name="android:windowBackground">@android:color/transparent</item>
    <item name="android:windowNoTitle">true</item>
    <item name="android:backgroundDimEnabled">true</item>
    <item name="android:windowIsFloating">false</item>
    <item name="android:windowSoftInputMode">adjustResize</item>
</style>
```

暗色：Activity 内 Compose `DdlAppTheme` 按系统暗色切换；窗口背景透明，不额外设 `windowLightStatusBar`。

### 6.8 状态与偏好

`CapturePrefs`（SharedPreferences，文件名 `capture_prefs`）：

| key | 类型 | 说明 |
|---|---|---|
| `floating_ball_enabled` | Boolean | 用户开关 |
| `ball_edge` | String (`left`/`right`) | 吸附边 |
| `ball_y_ratio` | Float | 垂直位置比例 0..1 |
| `first_hint_shown` | Boolean | 首次气泡只显示一次 |
| `pending_enable` | Boolean | 一次性：去系统设置后回来自动开启 |

`CaptureServiceState`：

```kotlin
object CaptureServiceState {
    val connected = MutableStateFlow(false)
}
```

胶囊状态 = `connected && CapturePrefs.floatingBallEnabled`。设置卡观察两者。

## 7. 权限与兼容性

| 项 | 结论 |
|---|---|
| 无障碍服务 | 必需；用户手动开启一次；服务由系统托管 |
| `SYSTEM_ALERT_WINDOW` | **不申请**；悬浮球用 `TYPE_ACCESSIBILITY_OVERLAY` |
| `INTERNET` | **不申请**；保持现有 manifest 无网络权限 |
| 前台服务 | 不需要 |
| 通知权限 | 不新增；撤销条是 overlay，不是通知 |
| Android 13+ 受限设置 | 侧载应用开启无障碍可能被灰；设置卡给「应用信息 → ⋮ → 允许受限设置」路径 |
| ColorOS / 厂商 | 设置卡提示允许自启动/后台运行；真机验证服务存活、overlay、后台 Activity 启动 |
| 多窗口/分屏 | 取当前活动窗口；读不到时走空草稿兜底 |
| 安全页面 / `FLAG_SECURE` | `rootInActiveWindow` 可能为空；不绕过，走空草稿兜底 |
| WebView / Compose / Flutter 页面 | 依赖各 App 暴露的无障碍节点；读不到就走兜底 |

## 8. 隐私与安全

- 页面文字只在内存中经过「提取 → 解析 → 编辑卡」；不写数据库、不写文件、不打日志、不上传。
- `sourceLine` 只存在于 `CapturePayload`，Activity finish 即释放。
- 密码框由 `AccessibilityNodeInfo.isPassword` 过滤；不读取、不记录。
- 不做截图、不做 OCR、不读剪贴板。
- 应用无 `INTERNET` 权限；代码中不引入网络库。
- 入库的是用户确认后的标题/截止/备注/提醒设置；备注默认空，不由解析器自动填充原文。
- 设置卡与首次引导明确写出「只在点悬浮球时读取当前页」。
- 服务描述文案同时说明用途与隐私，系统设置页可见。

## 9. 国际化

- 所有新增 UI 文案同时写入 `res/values/strings.xml` 与 `res/values-en/strings.xml`，key 一一对应。
- 解析器同时匹配中英文，不依赖 UI locale。
- 日期/时间显示用现有 `fmt_editor_date` / `fmt_editor_time`；候选 chip 新增 `fmt_capture_chip`（中文 `M月d日 HH:mm`，英文 `MMM d, HH:mm`）。
- 无障碍服务 label / description 双语。
- 通知渠道名不涉及；小组件不受影响。

新增字符串（建议 key）：

```
capture_entry                     自动识别 / Auto capture
capture_service_label             自动识别 / Auto capture
capture_settings_title            自动记 DDL / Auto-capture DDL
capture_state_running             运行中 / Running
capture_state_off                 未开启 / Off
capture_floating_ball             悬浮球 / Floating ball
capture_floating_ball_desc        在别的 App 上方显示一个「＋」，点一下识别当前页 / ...
capture_accessibility             无障碍服务 / Accessibility service
capture_accessibility_on          已开启 · 只在点悬浮球时读取当前页文字 / ...
capture_accessibility_off         未开启 · 需要手动授权一次 / ...
capture_open_settings             系统设置 › / System settings ›
capture_scope                     识别范围 / Scope
capture_scope_value               文字页面 · 图片/海报暂不支持 / ...
capture_privacy                   隐私：原文不保存、不上传；密码框会跳过。应用本身没有联网权限。 / ...
capture_restricted_hint           Android 13+ 侧载应用需在「应用信息 → ⋮ → 允许受限设置」后才能开启无障碍 / ...
capture_coloros_hint              ColorOS：还需允许自启动/后台运行，悬浮球才不会被系统回收 / ...
capture_card_title                自动识别 / Auto capture
capture_card_subtitle             来自当前页面 / From the current page
capture_no_deadline               未识别到截止时间，已默认 1 小时后 / ...
capture_no_text                   当前页面没有读到文字，手动补一下 / ...
capture_duplicate                 已存在相似的 DDL / A similar DDL already exists
capture_add_anyway                仍然添加 / Add anyway
capture_added                     已添加 / Added
capture_undo                      撤销 / Undo
capture_btn_add                   添加 / Add
capture_ball_desc                 自动识别悬浮球 / Auto-capture floating ball
capture_first_hint                点我识别当前页 / Tap to read this page
capture_accessibility_description 用于在你点悬浮球时读取当前页文字，识别 DDL。原文不保存、不上传。 / ...
fmt_capture_chip                  M月d日 HH:mm / MMM d, HH:mm
```

（英文文案在实现时逐条补齐，保持与中文语义一致；不省略、不机翻。）

## 10. 版本与发布

- `app/build.gradle.kts`：`versionCode = 28`，`versionName = "1.17.0"`。
- `CHANGELOG.md` 新增 `[1.17.0]`：
  - 新增自动记 DDL：顶栏「自动识别」胶囊、无障碍悬浮球、点按读屏、中英文日期解析、浮卡确认入库、3 秒撤销。
  - 隐私：原文不落盘、不打日志、不上传；跳过密码框；无 `INTERNET` 权限。
  - i18n：新增文案中英双语。
  - 兼容：Android 13+ 受限设置、ColorOS 引导。
- `.superpowers/sdd/2026-10-19-ddl-app/progress.md` 挂 R62（本地台账，gitignored，不提交）。
- 提交按显式路径 `git add`；禁止 `git add -A`；`Debug/` 设计稿不入库；`.superpowers/` 不入库。

## 11. 测试与验收

### 11.1 自动化

- 新增 `DdlParserTest.kt`：§5.4 全部样本 + 边界；固定 `now` 与 `ZoneId`。
- 新增 `CaptureDuplicateTest.kt`：同标题同截止、同标题不同截止、标点/空白归一化。
- 现有 64 个单测保持全绿。
- 构建门：

```
.\gradlew.bat :app:testDebugUnitTest assembleDebug --console=plain
```

### 11.2 真机手动验收

1. 设置卡：胶囊点击打开；开关、无障碍状态、隐私文案、系统设置跳转正常。
2. Android 13+ 受限设置：按引导能开启无障碍；服务连接后状态变「运行中」。
3. 悬浮球：出现、拖动、吸附、位置持久化；本 App/桌面/锁屏隐藏；重启后恢复。
4. 抓取：微信/QQ 文字消息、教务系统/学习通页面、邮件、浏览器文章、公众号各抓一次。
5. 多候选：同一页有多个截止时间，chips 可切换，字段跟随。
6. 无截止：默认 now+1h + 提示；可手动改。
7. 无文字/安全页：空草稿 + 提示，不崩溃。
8. 过去时间：提示并禁用添加；改时间后可保存。
9. 重复：同标题同截止触发「仍然添加」；不同截止不误报。
10. 保存：入库后列表/小组件/闹钟刷新；3 秒撤销可删除；撤销后闹钟取消。
11. 英文 UI：所有新增文案英文；英文页面解析正常；中文页面在英文 UI 下也正常。
12. 暗色：设置卡、编辑卡、胶囊、悬浮球、撤销条均正常。
13. ColorOS：服务存活、overlay 显示、后台 Activity 启动；按需加自启动/后台白名单。
14. 回归：现有添加/编辑/删除/顺延/导入导出/小组件不受影响。

### 11.3 验收门

- 自动化门全绿；
- 真机清单 1–14 通过（至少 ColorOS 真机 + 一个其他 Android 版本/模拟器）；
- 无新增 `INTERNET` / `SYSTEM_ALERT_WINDOW` 权限；
- 原文无落盘/日志（抽查 `adb logcat` 与应用私有目录）。

## 12. 风险与待验证

| 风险 | 影响 | 应对 |
|---|---|---|
| **后台 Activity 启动被拦**（Android 10+ / ColorOS） | 点球后编辑卡不出现 | **第一步做 spike**：service + overlay + startActivity 真机验证；若被拦，回退到「服务内全屏 overlay 卡片」（路线 B）或通知兜底 |
| 无障碍服务被厂商杀 | 球消失 | 设置卡给自启动/后台白名单引导；服务重连恢复；真机验证 |
| 微信/学习通等页面不暴露文字节点 | 抓不到内容 | 空草稿兜底；不承诺 100% 覆盖；后续版本再评估 OCR |
| 日期歧义/误判 | 解析错 | 用户确认制；多候选 chips；歧义格式跳过；单测覆盖 |
| 页面文字过长/节点过多 | 卡顿/ANR | 提取上限 12,000 字符 / 2,000 节点 / 40 层；节点遍历在主线程（带上限），解析放 `Dispatchers.Default`，完成后再回主线程启动 Activity |
| 无障碍权限被滥用质疑 | 信任/合规 | 明示「只在点球时读」；无网络；无日志；密码框跳过；设置卡常驻隐私说明 |
| Play Store 政策 | 无法上架 | 当前侧载；如上架需单独做无障碍 API 声明与审核 |
| 编辑卡旋转重建丢状态 | 用户输入丢失 | `pending` 保留到 finish；编辑值 `rememberSaveable`；真机旋转测试 |

## 13. 实施顺序

1. **Spike（真机门）**：Manifest service + overlay 球 + 点按 `startActivity`；验证 ColorOS 三项（服务开启、overlay、后台 Activity）。不通过就先解决路线，不写 UI 细节。
2. **解析器 TDD**：`DdlParser` + `CaptureDuplicate` + 单测；先跑绿。
3. **提取器 + 服务**：`ScreenTextExtractor`、`CaptureSession`、`CapturePrefs`、服务生命周期、悬浮球拖动/吸附/显隐。
4. **捕获编辑卡**：抽取 `DdlEditorFields`；`DdlCaptureActivity`；保存/重复/撤销；3 秒 overlay。
5. **入口与设置**：顶栏胶囊（26dp A 款）、设置卡、首次引导、i18n。
6. **收尾**：版本号、CHANGELOG、R62、全量测试、真机清单、回归。

## 14. 变更记录

- 2026-11-08：初稿。入口定为 A 墨色实心胶囊、26dp、文案「自动识别」；抓取定为悬浮球点按；编辑卡定为 Dialog 主题 Activity；解析器 v1 文字-only；无 DB 迁移；版本 1.17.0/code28。
