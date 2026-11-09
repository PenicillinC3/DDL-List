# 自动记 DDL — 图片 / 海报 OCR 设计文档

- 日期：2026-11-09
- 状态：设计已获用户批准（只保留 arm64）
- 类型：新增 OCR 子系统（无 DB 迁移）
- 关联：`docs/superpowers/specs/2026-11-08-auto-capture-design.md`

## 1. 目标

在现有「自动记 DDL」上补图片 / 海报识别：

1. 文字页面继续走无障碍节点取词，不额外增加延迟；
2. 当取词没有解析出带截止时间的候选，或页面本身是图片 / 海报时，截取当前窗口做本地 OCR；
3. OCR 结果与取词候选合并去重后，仍走原来的浮卡确认流程；
4. 截图只在内存中停留，OCR 完成立即回收；不落盘、不打日志、不上传；
5. 应用保持无 `INTERNET` 权限。

成功标准：

- 微信图片、公众号海报、学习通图文页能识别出标题 / 截止时间并进入浮卡；
- 纯文字页不触发 OCR（保持现有速度）；
- 截图失败、安全窗口、Android 10 及以下时自动回退到取词结果，不阻塞用户；
- 现有 109 个单测全绿；新增 `CaptureText` 单测；`assembleDebug` 通过；
- APK 只包含 `arm64-v8a`，压缩 native 库后预计 22–24MB。

非目标：

- 云端 OCR / 联网识别；
- Android 10 及以下的 MediaProjection 授权流程；
- 手动框选识别区域、图片编辑、翻译；
- OCR 开关（默认按需触发）；
- 识别结果自动入库（仍必须经过浮卡确认）。

## 2. 已锁定决策

| 决策 | 结论 | 理由 |
|---|---|---|
| OCR 引擎 | ML Kit `text-recognition-chinese:16.0.1` | 中英混排、离线、模型小、维护成本低 |
| 打包 ABI | 仅 `arm64-v8a` | 用户选定；现代手机均为 64 位 |
| native 打包 | `jniLibs.useLegacyPackaging = true` | 压缩 `.so`，控制 APK 体积 |
| 截图方式 | `AccessibilityService.takeScreenshot`（API 30+） | 无需 MediaProjection 授权；只在点球时截一次 |
| OCR 触发 | 取词没有带截止时间的候选时才 OCR | 文字页保持快路径；图片页自动补识别 |
| 截图范围 | 当前活动应用窗口 | 裁掉状态栏 / 导航栏，减少时钟等噪声 |
| 结果合并 | `CaptureText.merge` 去重后最多 3 条 | 复用现有候选 chips |
| 版本 | 1.18.0 / code 29 | 功能版本 |

## 3. 用户流程

### 3.1 纯文字页（不触发 OCR）

1. 用户点悬浮球；
2. 服务移除悬浮球，读取活动窗口节点文字；
3. `DdlParser.parse` 得到候选；
4. 若候选中存在 `deadline != null`：直接打开浮卡，流程与 1.17.0 完全一致；
5. 不截图、不初始化 OCR。

### 3.2 图片 / 海报页（触发 OCR）

1. 用户点悬浮球；
2. 服务移除悬浮球、提示气泡、撤销条；
3. 读取节点文字并解析；没有带截止时间的候选；
4. 等待约 120ms，让悬浮球窗口从屏幕上消失；
5. `takeScreenshot(DEFAULT_DISPLAY)` 截取全屏；
6. 按当前活动应用窗口 bounds 裁剪，去掉状态栏 / 导航栏；
7. 显示「识别中…」小胶囊；
8. ML Kit 本地识别 → 文本；
9. `DdlParser.parse` 解析 OCR 文本；
10. `CaptureText.merge(取词候选, OCR 候选)` 合并去重，最多 3 条；
11. 隐藏「识别中…」；打开浮卡，后续确认 / 重复提示 / 撤销不变。

### 3.3 回退路径

以下任一情况直接使用取词候选打开浮卡（若取词也为空，则空草稿 + 提示）：

- Android 版本 < 11（无 `takeScreenshot`）；
- `takeScreenshot` 失败（安全窗口、限频、服务权限异常等）；
- OCR 8 秒超时或返回空文本；
- 当前活动窗口属于本应用；
- 当前窗口包含可见密码框（避免把敏感画面送进 OCR）。

## 4. 架构与组件

### 4.1 新增 `CaptureOcr.kt`

职责：托管 ML Kit `TextRecognizer`，提供 suspend 识别接口，并负责回收传入的 bitmap。

```kotlin
class CaptureOcr {
    suspend fun recognize(bitmap: Bitmap): String
    fun close()
}
```

约束：

- `ChineseTextRecognizerOptions`，中英混排；
- 传入的 bitmap 由 `CaptureOcr` 在任务完成 / 失败 / 取消时 `recycle()`，调用方不再持有；
- 任务完成监听里统一回收，避免超时后提前 recycle 导致 ML Kit 使用已回收内存；
- `close()` 在服务 `cleanup()` 调用，幂等；
- 不写日志、不落盘。

### 4.2 新增 `CaptureText.kt`（纯 Kotlin，可单测）

```kotlin
object CaptureText {
    fun needsOcr(candidates: List<DdlCandidate>): Boolean
    fun merge(node: List<DdlCandidate>, ocr: List<DdlCandidate>): List<DdlCandidate>
}
```

规则：

- `needsOcr`：候选中没有任何 `deadline != null` 时返回 `true`；
- `merge`：带截止时间的候选优先，其次按 `confidence` 降序；
- 去重：标题归一化后相同，且截止时间相同或相差 ≤ 60 秒；两边都无截止时间时同标题也去重；
- 标题为空不参与去重；
- 最多返回 3 条，保持现有候选 chips 上限。

### 4.3 `DdlCaptureAccessibilityService.kt`

新增：

- `CaptureOcr` 实例与生命周期；
- `takeScreenshotBitmap()`：`suspendCancellableCoroutine` 包装 `takeScreenshot`，把 `HardwareBuffer` 转成软件 `Bitmap` 后关闭 buffer；
- `cropToActiveWindow(bitmap)`：取 `AccessibilityWindowInfo.TYPE_APPLICATION` 的活动窗口 bounds，按截图 / 屏幕尺寸比例缩放后裁剪；bounds 异常时返回原图；
- `captureWithOcr(...)`：截图 → 裁剪 → 进度胶囊 → OCR → 解析 → 合并 → 隐藏进度；
- `showProgress()` / `hideProgress()`：底部居中的「识别中…」小胶囊，`FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL | FLAG_NOT_TOUCHABLE`；
- `cleanup()` 中移除进度胶囊并关闭 OCR。

`captureCurrentPage()` 改为：

1. 取活动 root 与节点文字；
2. 判断是否本应用、是否有可见密码框；
3. 移除悬浮球 / 气泡 / 撤销条；
4. 解析节点文字；
5. 按条件决定是否 OCR；
6. 合并候选；
7. 写 `CaptureSession.pending` 并启动浮卡。

### 4.4 `ScreenTextExtractor.kt`

新增 `containsVisiblePassword(root)`：按现有 2000 节点 / 40 层上限遍历，遇到 `isVisibleToUser && isPassword` 返回 `true`。只用于决定是否跳过 OCR，不改变 `extract()` 的现有行为。

### 4.5 `accessibility_service_config.xml`

增加：

```xml
android:canTakeScreenshot="true"
```

### 4.6 构建

- `gradle/libs.versions.toml` 增加 ML Kit 中文依赖；
- `app/build.gradle.kts`：
  - `ndk { abiFilters += "arm64-v8a" }`
  - `packaging { jniLibs { useLegacyPackaging = true } }`
- 版本 `1.18.0` / `29`；
- 合并 manifest 后检查 `INTERNET`：ML Kit 当前 manifest 不含该权限；若出现则用 `tools:node="remove"` 移除。

## 5. 隐私

- 截图仅存在于内存；OCR 完成后由 `CaptureOcr` 回收；
- 不写缓存文件、不写日志、不上传；
- 应用不申请 `INTERNET`；
- 跳过本应用窗口；
- 窗口含可见密码框时跳过 OCR；
- 安全窗口（`FLAG_SECURE`）拿不到截图，自然回退；
- 无障碍描述与设置卡隐私文案明确写出「截图仅在本机识别，不保存、不上传」。

## 6. UI 与文案

设置卡「识别范围」：

- 中文：`文字页面 · 图片/海报可识别（Android 11+）`
- 英文：`Text pages · images/posters supported on Android 11+`

隐私说明追加：

- 中文：`图片识别也在本机完成，截图不保存、不上传。`
- 英文：`Image OCR also runs on-device; screenshots are never saved or uploaded.`

无障碍服务描述追加截图说明。

新增：

- `capture_reading`：`识别中…` / `Reading…`

浮卡结构不变，不加 OCR 开关，不改现有候选 chips 交互。

## 7. 错误处理

| 情况 | 行为 |
|---|---|
| API < 30 | 跳过 OCR，取词结果开卡 |
| `takeScreenshot` 失败 | 跳过 OCR，取词结果开卡 |
| 安全窗口 | 截图失败，回退 |
| OCR 超时（8s） | 隐藏进度，取词结果开卡；后台任务继续并在完成时回收 bitmap |
| OCR 空文本 | 取词结果开卡 |
| 服务销毁 | 取消协程、移除进度胶囊、关闭 recognizer |
| 浮卡启动失败 | 沿用现有 3 秒 OEM 兜底：恢复悬浮球并 toast |

## 8. 测试与验收

### 8.1 单测

新增 `CaptureTextTest`：

- `needsOcr`：空候选 / 仅标题 / 有截止时间；
- `merge`：截止时间优先、同标题同截止时间去重、同标题不同截止时间保留、最多 3 条、空列表；
- 现有 109 个测试保持全绿。

### 8.2 构建

```bash
.\gradlew.bat :app:testDebugUnitTest assembleDebug --console=plain
```

- BUILD SUCCESSFUL；
- 报告测试总数与 APK 体积；
- 检查 APK 只含 `lib/arm64-v8a`；
- 检查合并 manifest 无 `INTERNET`。

### 8.3 真机清单（设备恢复后）

- 微信图片 / 海报：能识别并进浮卡；
- 学习通 / 教务图文页：图片内截止时间可识别；
- 纯文字页：确认不触发 OCR（无「识别中…」延迟）；
- 无文字页面：空草稿 + 提示；
- 安全窗口（银行 / 密码页）：回退不崩；
- 英文海报：标题 / 日期可解析；
- 暗色 / 浅色、横竖屏、连续多次识别；
- Android 10 及以下：只走取词。

## 9. 版本与发布

- `versionCode = 29`，`versionName = "1.18.0"`；
- CHANGELOG 增加 `[1.18.0]`；
- 仅构建 debug APK 侧载；不推 Play Store。
