package com.gammac3.ddlapp.capture

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.gammac3.ddlapp.DdlApplication
import com.gammac3.ddlapp.R
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZonedDateTime
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * 自动记 DDL 的无障碍服务。
 *
 * 职责：托管悬浮球、点按时读取当前页文字、启动浮卡、显示 3 秒撤销条。
 * 不在页面变化时读取文字；不写日志、不落盘、不上传。
 */
class DdlCaptureAccessibilityService : AccessibilityService() {

    companion object {
        private const val OCR_TIMEOUT_MILLIS = 8_000L

        @Volatile
        var instance: DdlCaptureAccessibilityService? = null
            private set

        fun refreshBall() {
            instance?.refreshBallVisibility()
        }
    }

    private lateinit var prefs: CapturePrefs
    private lateinit var windowManager: WindowManager
    private var scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())

    private var ballView: View? = null
    private var ballParams: WindowManager.LayoutParams? = null
    private var hintView: View? = null
    private var undoView: View? = null
    private var progressView: View? = null
    private var ocrClient: CaptureOcr? = null
    private var undoRunnable: Runnable? = null
    private var undoShowRunnable: Runnable? = null

    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var moved = false

    private val touchSlop: Int by lazy { ViewConfiguration.get(this).scaledTouchSlop }
    private val ballSizePx: Int get() = dp(44)
    private val edgeMarginPx: Int get() = dp(8)

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        scope.cancel()
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        prefs = CapturePrefs.get(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        CaptureServiceState.setConnected(true)
        // 服务重启时若没有待确认的浮卡，清掉可能残留的打开标记
        if (CaptureSession.pending == null) CaptureSession.cardOpen = false

        if (prefs.consumePendingEnable()) {
            prefs.setFloatingBallEnabled(true)
        }

        CaptureSession.onCardClosed = { restoreAfterCard() }
        CaptureSession.onSaved = { id ->
            restoreAfterCard()
            showUndo(id)
        }

        refreshBallVisibility()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 旋转/分屏后把球夹回屏内；位置比例不变，横竖屏切换不丢
        ballView?.let { view ->
            ballParams?.let { params ->
                clampBall(params)
                runCatching { windowManager.updateViewLayout(view, params) }
            }
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        // 旧实例晚于新实例回调时，不要把新实例的全局状态清掉
        if (instance === this) {
            instance = null
            CaptureServiceState.setConnected(false)
            CaptureSession.onCardClosed = null
            CaptureSession.onSaved = null
        }
        removeBall()
        removeHint()
        removeUndo()
        removeProgress()
        ocrClient?.close()
        ocrClient = null
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
    }

    // ------------------------------------------------------------ ball

    fun refreshBallVisibility() {
        if (!this::prefs.isInitialized) return
        // 任何界面都显示：不按前台应用/桌面/锁屏过滤，只受开关与浮卡状态控制
        val show = prefs.floatingBallEnabled.value && !CaptureSession.cardOpen
        if (show) ensureBall() else removeBall()
    }

    private fun ensureBall() {
        if (ballView != null) return
        val screen = screenSize()
        val params = WindowManager.LayoutParams(
            ballSizePx,
            ballSizePx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            y = ((screen.y - ballSizePx).coerceAtLeast(0) * prefs.ballYRatio).toInt()
            x = if (prefs.ballEdge == CapturePrefs.EDGE_LEFT) {
                edgeMarginPx
            } else {
                (screen.x - ballSizePx - edgeMarginPx).coerceAtLeast(edgeMarginPx)
            }
            clampBall(this)
        }

        val view = buildBall()
        attachTouch(view, params)
        runCatching {
            windowManager.addView(view, params)
            ballView = view
            ballParams = params
            maybeShowFirstHint()
        }
    }

    private fun buildBall(): View {
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_capture_sparkle)
            setColorFilter(if (isNight()) Color.parseColor("#0E1116") else Color.WHITE)
        }
        return FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (isNight()) Color.parseColor("#F2F4F8") else Color.parseColor("#101828"))
            }
            elevation = dp(4).toFloat()
            contentDescription = getString(R.string.capture_ball_desc)
            addView(
                icon,
                FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER),
            )
        }
    }

    private fun attachTouch(view: View, params: WindowManager.LayoutParams) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!moved && hypot(dx.toDouble(), dy.toDouble()) > touchSlop) moved = true
                    if (moved) {
                        val screen = screenSize()
                        params.x = (startX + dx).toInt()
                            .coerceIn(0, (screen.x - ballSizePx).coerceAtLeast(0))
                        params.y = (startY + dy).toInt()
                            .coerceIn(dp(24), (screen.y - ballSizePx).coerceAtLeast(dp(24)))
                        runCatching { windowManager.updateViewLayout(v, params) }
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    if (moved) {
                        snapToEdge(params)
                        persistPosition(params)
                    } else {
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        captureCurrentPage()
                    }
                    true
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    if (moved) {
                        snapToEdge(params)
                        persistPosition(params)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun snapToEdge(params: WindowManager.LayoutParams) {
        val screen = screenSize()
        val target = if (params.x + ballSizePx / 2 < screen.x / 2) {
            edgeMarginPx
        } else {
            (screen.x - ballSizePx - edgeMarginPx).coerceAtLeast(edgeMarginPx)
        }
        val view = ballView ?: return
        ValueAnimator.ofInt(params.x, target).apply {
            duration = 150
            addUpdateListener { animation ->
                params.x = animation.animatedValue as Int
                runCatching { windowManager.updateViewLayout(view, params) }
            }
            start()
        }
    }

    private fun persistPosition(params: WindowManager.LayoutParams) {
        val screen = screenSize()
        prefs.ballEdge = if (params.x + ballSizePx / 2 < screen.x / 2) {
            CapturePrefs.EDGE_LEFT
        } else {
            CapturePrefs.EDGE_RIGHT
        }
        val maxY = (screen.y - ballSizePx).coerceAtLeast(1)
        prefs.ballYRatio = params.y.toFloat() / maxY
    }

    private fun clampBall(params: WindowManager.LayoutParams) {
        val screen = screenSize()
        val maxX = (screen.x - ballSizePx).coerceAtLeast(0)
        val maxY = (screen.y - ballSizePx).coerceAtLeast(0)
        val minY = dp(24).coerceAtMost(maxY)
        params.x = params.x.coerceIn(0, maxX)
        params.y = params.y.coerceIn(minY, maxY)
    }

    private fun removeBall() {
        val view = ballView ?: return
        ballView = null
        ballParams = null
        runCatching { windowManager.removeView(view) }
    }

    private fun maybeShowFirstHint() {
        if (prefs.firstHintShown) return
        prefs.firstHintShown = true
        val params = ballParams ?: return
        val screen = screenSize()
        val hint = TextView(this).apply {
            text = getString(R.string.capture_first_hint)
            setTextColor(if (isNight()) Color.parseColor("#0E1116") else Color.WHITE)
            textSize = 12f
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(999).toFloat()
                setColor(if (isNight()) Color.parseColor("#F2F4F8") else Color.parseColor("#101828"))
            }
        }
        val hintParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            y = (params.y + ballSizePx + dp(8)).coerceIn(dp(24), (screen.y - dp(48)).coerceAtLeast(dp(24)))
            x = if (prefs.ballEdge == CapturePrefs.EDGE_LEFT) {
                edgeMarginPx
            } else {
                (screen.x - dp(160)).coerceAtLeast(edgeMarginPx)
            }
        }
        runCatching {
            windowManager.addView(hint, hintParams)
            hintView = hint
            handler.postDelayed({ removeHint() }, 3_000)
        }
    }

    private fun removeHint() {
        val view = hintView ?: return
        hintView = null
        runCatching { windowManager.removeView(view) }
    }

    // ------------------------------------------------------------ capture

    private fun captureCurrentPage() {
        if (CaptureSession.cardOpen) return
        val activeWindow = windows.firstOrNull { it.isActive }
        val root = rootInActiveWindow ?: activeWindow?.root
        val foregroundPackage = activeWindow?.root?.packageName?.toString()
            ?: root?.packageName?.toString()
        val ownWindow = foregroundPackage == packageName
        val text = ScreenTextExtractor.extract(root, packageName)
        val passwordVisible = !ownWindow && ScreenTextExtractor.containsVisiblePassword(root)
        removeBall()
        removeHint()
        removeUndo()
        CaptureSession.cardOpen = true
        CaptureSession.cardShown = false

        scope.launch {
            val now = ZonedDateTime.now()
            val nodeCandidates = withContext(Dispatchers.Default) {
                DdlParser.parse(text, now)
            }
            val ocrCandidates = if (
                !ownWindow &&
                !passwordVisible &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                CaptureText.needsOcr(nodeCandidates)
            ) {
                captureWithOcr(now)
            } else {
                emptyList()
            }
            val candidates = CaptureText.merge(nodeCandidates, ocrCandidates).ifEmpty {
                listOf(DdlCandidate(title = "", deadline = null, confidence = 0f, sourceLine = ""))
            }
            CaptureSession.pending = CapturePayload(
                candidates = candidates,
                capturedAtMillis = System.currentTimeMillis(),
            )
            launchCard()
        }
    }

    /** 截图 → 裁剪 → 进度胶囊 → 本机 OCR → 解析；任何失败都返回空列表。 */
    @SuppressLint("NewApi")
    private suspend fun captureWithOcr(now: ZonedDateTime): List<DdlCandidate> {
        // 让悬浮球窗口先真正从屏幕上消失，避免球入镜
        delay(120)
        val shot = takeScreenshotBitmap() ?: return emptyList()
        val bounds = activeWindowBounds()
        val screen = screenSize()
        val cropped = withContext(Dispatchers.Default) {
            runCatching { cropToActiveWindow(shot, bounds, screen) }.getOrElse { shot }
        }
        showProgress()
        return try {
            val ocrText = withTimeoutOrNull(OCR_TIMEOUT_MILLIS) {
                ocr().recognize(cropped)
            }
            if (ocrText.isNullOrBlank()) {
                emptyList()
            } else {
                withContext(Dispatchers.Default) { DdlParser.parse(ocrText, now) }
            }
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            emptyList()
        } finally {
            removeProgress()
        }
    }

    @SuppressLint("NewApi")
    private suspend fun takeScreenshotBitmap(): Bitmap? = suspendCancellableCoroutine { cont ->
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                ContextCompat.getMainExecutor(this),
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        val bitmap = try {
                            Bitmap.wrapHardwareBuffer(
                                screenshot.hardwareBuffer,
                                screenshot.colorSpace,
                            )?.copy(Bitmap.Config.ARGB_8888, false)
                        } catch (t: Throwable) {
                            null
                        } finally {
                            screenshot.hardwareBuffer.close()
                        }
                        if (cont.isActive) cont.resume(bitmap)
                    }

                    override fun onFailure(errorCode: Int) {
                        if (cont.isActive) cont.resume(null)
                    }
                },
            )
        } catch (t: Throwable) {
            if (cont.isActive) cont.resume(null)
        }
    }

    /** 按当前应用窗口 bounds 裁剪，去掉状态栏 / 导航栏；bounds 异常时保留原图。 */
    private fun cropToActiveWindow(bitmap: Bitmap, bounds: Rect?, screen: Point): Bitmap {
        if (bounds == null || screen.x <= 0 || screen.y <= 0) return bitmap
        val scaleX = bitmap.width.toFloat() / screen.x
        val scaleY = bitmap.height.toFloat() / screen.y
        val left = (bounds.left * scaleX).roundToInt().coerceIn(0, bitmap.width - 1)
        val top = (bounds.top * scaleY).roundToInt().coerceIn(0, bitmap.height - 1)
        val right = (bounds.right * scaleX).roundToInt().coerceIn(left + 1, bitmap.width)
        val bottom = (bounds.bottom * scaleY).roundToInt().coerceIn(top + 1, bitmap.height)
        // 过小的窗口多半是分屏 / 悬浮窗异常值，裁了反而丢正文
        if (right - left < bitmap.width / 4 || bottom - top < bitmap.height / 4) return bitmap
        if (left == 0 && top == 0 && right == bitmap.width && bottom == bitmap.height) return bitmap
        val cropped = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        if (cropped !== bitmap) bitmap.recycle()
        return cropped
    }

    private fun activeWindowBounds(): Rect? {
        val window = windows.firstOrNull {
            it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isActive
        } ?: windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        if (window == null) return null
        return Rect().also { window.getBoundsInScreen(it) }
    }

    private fun ocr(): CaptureOcr = ocrClient ?: CaptureOcr().also { ocrClient = it }

    private fun launchCard() {
        val intent = Intent(this@DdlCaptureAccessibilityService, DdlCaptureActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }.onFailure {
            CaptureSession.pending = null
            CaptureSession.cardOpen = false
            refreshBallVisibility()
        }
        // 部分 OEM 会静默拦截后台启动 Activity：3 秒内没露卡就恢复悬浮球
        handler.postDelayed({
            if (CaptureSession.cardOpen && !CaptureSession.cardShown) {
                CaptureSession.cardOpen = false
                CaptureSession.pending = null
                refreshBallVisibility()
                Toast.makeText(
                    this@DdlCaptureAccessibilityService,
                    R.string.capture_launch_failed,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }, 3_000)
    }

    // ------------------------------------------------------------ progress

    private fun showProgress() {
        if (progressView != null) return
        val screen = screenSize()
        val pill = TextView(this).apply {
            text = getString(R.string.capture_reading)
            setTextColor(if (isNight()) Color.parseColor("#0E1116") else Color.WHITE)
            textSize = 12.5f
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(999).toFloat()
                setColor(if (isNight()) Color.parseColor("#F2F4F8") else Color.parseColor("#101828"))
            }
            elevation = dp(6).toFloat()
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(72).coerceAtMost((screen.y / 4).coerceAtLeast(dp(24)))
        }
        runCatching {
            windowManager.addView(pill, params)
            progressView = pill
        }
    }

    private fun removeProgress() {
        val view = progressView ?: return
        progressView = null
        runCatching { windowManager.removeView(view) }
    }

    private fun restoreAfterCard() {
        CaptureSession.cardOpen = false
        // 浮卡 finish 前根窗口仍是本应用；等源 App 回来再判断显隐
        handler.postDelayed({ refreshBallVisibility() }, 400)
    }

    // ------------------------------------------------------------ undo

    private fun showUndo(id: Long) {
        removeUndo()
        // 等浮卡收起动画结束再露头，避免撤销条压在正在关闭的卡上
        val show = Runnable { addUndoPill(id) }
        undoShowRunnable = show
        handler.postDelayed(show, 260)
    }

    private fun addUndoPill(id: Long) {
        undoShowRunnable = null
        val screen = screenSize()
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            contentDescription = getString(R.string.capture_added) + " · " +
                getString(R.string.capture_undo)
            background = GradientDrawable().apply {
                cornerRadius = dp(999).toFloat()
                setColor(if (isNight()) Color.parseColor("#F2F4F8") else Color.parseColor("#101828"))
            }
            elevation = dp(6).toFloat()
        }
        val ink = if (isNight()) Color.parseColor("#0E1116") else Color.WHITE
        pill.addView(TextView(this).apply {
            text = getString(R.string.capture_added)
            setTextColor(ink)
            textSize = 12.5f
        })
        pill.addView(TextView(this).apply {
            text = " · "
            setTextColor(ink)
            textSize = 12.5f
        })
        pill.addView(TextView(this).apply {
            text = getString(R.string.capture_undo)
            setTextColor(ink)
            textSize = 12.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        pill.setOnClickListener {
            removeUndo()
            scope.launch {
                (application as DdlApplication).container.repository.delete(id)
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(72).coerceAtMost((screen.y / 4).coerceAtLeast(dp(24)))
        }
        runCatching {
            windowManager.addView(pill, params)
            undoView = pill
            val runnable = Runnable { removeUndo() }
            undoRunnable = runnable
            handler.postDelayed(runnable, 3_000)
        }
    }

    private fun removeUndo() {
        undoShowRunnable?.let { handler.removeCallbacks(it) }
        undoShowRunnable = null
        undoRunnable?.let { handler.removeCallbacks(it) }
        undoRunnable = null
        val view = undoView ?: return
        undoView = null
        runCatching { windowManager.removeView(view) }
    }

    // ------------------------------------------------------------ helpers

    private fun screenSize(): Point {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            Point(bounds.width(), bounds.height())
        } else {
            val metrics = resources.displayMetrics
            Point(metrics.widthPixels, metrics.heightPixels)
        }
    }

    private fun isNight(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()
}
