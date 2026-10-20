package com.gammac3.ddlapp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Build
import android.text.SpannableString
import android.text.style.TypefaceSpan
import android.widget.RemoteViews
import androidx.annotation.FontRes
import androidx.core.content.res.ResourcesCompat
import com.gammac3.ddlapp.DdlApplication
import com.gammac3.ddlapp.MainActivity
import com.gammac3.ddlapp.R
import com.gammac3.ddlapp.data.DdlEntity
import com.gammac3.ddlapp.model.DdlRow
import com.gammac3.ddlapp.ui.Format
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 桌面小组件渲染与刷新（RemoteViews + XML 布局）。数据变动 / 应用回前台 / 提醒闹钟触发 /
 * 开机重建 都会调 [refresh]，把两款（V1 单焦点卡 / V3 多行卡）重新渲染。
 * 装饰层：台历红签（右上角）+ 单焦点卡周尺 + 多行卡行内燃线与周多点条，全部
 * shape/layer-list/ProgressBar 等级驱动，零位图零新令牌。
 */
object DdlWidgets {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 幂等、可从任何线程调用；失败只吞掉（下次数据变动再刷）。 */
    fun refresh(context: Context) {
        val appContext = context.applicationContext
        scope.launch { runCatching { updateAll(appContext) } }
    }

    suspend fun updateAll(context: Context) {
        val app = context as? DdlApplication ?: return
        val manager = AppWidgetManager.getInstance(context) ?: return
        val rows = app.container.database.dao().allActive().map { it.toRow() }
        val model = DdlWidgetModel.build(rows, System.currentTimeMillis(), ZoneId.systemDefault())
        for (spec in SPECS) {
            val ids = manager.getAppWidgetIds(ComponentName(context, spec.receiver))
            if (ids.isEmpty()) continue
            manager.updateAppWidget(ids, render(context, spec, model))
        }
    }

    private data class Spec(val receiver: Class<out android.appwidget.AppWidgetProvider>, val layout: Int)
    private val SPECS = listOf(
        Spec(DdlWidgetV1Receiver::class.java, R.layout.widget_v1),
        Spec(DdlWidgetV3Receiver::class.java, R.layout.widget_v3),
    )

    // ---------- 调色（与 Theme.kt 令牌一致） ----------

    private class Palette(
        val bgRes: Int,
        val ink: Int,
        val gray: Int,
        val red: Int,
        val indigo: Int,
        val calpgBgRes: Int,
        val calpgInk: Int,
        val trackRes: Int,
        val indDotRes: Int,
        val cellRes: Int,
        val cellTodayRes: Int,
    )

    private fun palette(context: Context): Palette {
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        return if (night) Palette(
            R.drawable.widget_bg_dark, 0xFFE8ECF1.toInt(), 0xFF8B949E.toInt(),
            0xFFFF7A7F.toInt(), 0xFF8FA3FF.toInt(),
            R.drawable.widget_calpg_dark, 0xFF2B0D0F.toInt(), R.drawable.widget_ruler_track_dark,
            R.drawable.dot_ind_dark, R.drawable.widget_cell_dark, R.drawable.widget_cell_today_dark,
        ) else Palette(
            R.drawable.widget_bg_light, 0xFF101828.toInt(), 0xFF5F6771.toInt(),
            0xFFE5484D.toInt(), 0xFF3A5AF0.toInt(),
            R.drawable.widget_calpg_light, 0xFFFFFFFF.toInt(), R.drawable.widget_ruler_track_light,
            R.drawable.dot_ind_light, R.drawable.widget_cell_light, R.drawable.widget_cell_today_light,
        )
    }

    // ---------- 渲染 ----------

    private fun render(context: Context, spec: Spec, model: DdlWidgetModel): RemoteViews {
        val p = palette(context)
        val rv = RemoteViews(context.packageName, spec.layout)
        rv.setInt(R.id.widget_root, "setBackgroundResource", p.bgRes)
        rv.setOnClickPendingIntent(R.id.widget_root, openIntent(context))
        when (spec.layout) {
            R.layout.widget_v1 -> renderV1(context, rv, p, model)
            else -> renderV3(context, rv, p, model)
        }
        return rv
    }

    private fun openIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 100,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** 头部統計（红签占了右上角，统计移到底部行）。 */
    private fun metaFilled(model: DdlWidgetModel): String =
        "共 ${model.activeCount} 条未完成 · 今日 ${model.todayCount} 条 · 本周 ${model.weekCount} 条"

    private fun metaEmpty(model: DdlWidgetModel): String =
        if (model.overdueCount > 0) "共 ${model.activeCount} 条未完成 · 其中 ${model.overdueCount} 条已逾期"
        else "共 ${model.activeCount} 条未完成 · 点按添加条目"

    private fun renderV1(context: Context, rv: RemoteViews, p: Palette, model: DdlWidgetModel) {
        val nearest = model.nearest
        rv.setTextViewText(R.id.widget_header, "最近到期")
        rv.setTextColor(R.id.widget_header, p.gray)
        applyCalTab(rv, p, nearest)
        applyRuler(rv, p, model)
        if (nearest == null) {
            rv.setTextViewText(R.id.widget_title, "现在很清净")
            rv.setTextColor(R.id.widget_title, p.ink)
            rv.setTextViewText(R.id.widget_countdown, mixed(context, "——", R.font.maple_mono_bold_data, R.font.source_han_cn_700))
            rv.setTextColor(R.id.widget_countdown, p.gray)
            rv.setTextViewText(R.id.widget_date, if (model.overdueCount > 0) "另有 ${model.overdueCount} 条已逾期" else "")
            rv.setTextColor(R.id.widget_date, p.gray)
            rv.setTextViewText(R.id.widget_meta, mixed(context, metaEmpty(model), R.font.timeless_serif_text, R.font.source_han_cn_400))
            rv.setTextColor(R.id.widget_meta, p.gray)
            return
        }
        val now = System.currentTimeMillis()
        val urgent = nearest.deadlineMillis - now < 24 * 3_600_000L
        rv.setTextViewText(R.id.widget_title, nearest.title)
        rv.setTextColor(R.id.widget_title, p.ink)
        rv.setTextViewText(R.id.widget_countdown, mixed(context, Format.remaining(nearest.deadlineMillis, now), R.font.maple_mono_bold_data, R.font.source_han_cn_700))
        rv.setTextColor(R.id.widget_countdown, if (urgent) p.red else p.ink)
        rv.setTextViewText(R.id.widget_date, mixed(context, absoluteLine(nearest.deadlineMillis), R.font.timeless_serif_text, R.font.source_han_cn_400))
        rv.setTextColor(R.id.widget_date, p.gray)
        rv.setTextViewText(R.id.widget_meta, mixed(context, metaFilled(model), R.font.timeless_serif_text, R.font.source_han_cn_400))
        rv.setTextColor(R.id.widget_meta, p.gray)
    }

    private fun renderV3(context: Context, rv: RemoteViews, p: Palette, model: DdlWidgetModel) {
        rv.setTextViewText(R.id.widget_header, "最近到期")
        rv.setTextColor(R.id.widget_header, p.gray)
        applyCalTab(rv, p, model.nearest)
        val rows = model.upcoming
        rv.setViewVisibility(R.id.widget_row3_container, if (rows.size >= 3) android.view.View.VISIBLE else android.view.View.GONE)
        renderRow(context, rv, p, R.id.widget_row1_title, R.id.widget_row1_cd, R.id.widget_row1_fuse, rows.getOrNull(0), "现在很清净，没有即将到期的条目")
        renderRow(context, rv, p, R.id.widget_row2_title, R.id.widget_row2_cd, R.id.widget_row2_fuse, rows.getOrNull(1), "")
        renderRow(context, rv, p, R.id.widget_row3_title, R.id.widget_row3_cd, R.id.widget_row3_fuse, rows.getOrNull(2), "")
        applyWeekStrip(rv, p, model)
    }

    private fun renderRow(
        context: Context, rv: RemoteViews, p: Palette,
        titleId: Int, cdId: Int, fuseId: Int, row: DdlRow?, emptyTitle: String,
    ) {
        if (row == null) {
            rv.setTextViewText(titleId, emptyTitle)
            rv.setTextColor(titleId, p.gray)
            rv.setTextViewText(cdId, "——")
            rv.setTextColor(cdId, p.gray)
            rv.setViewVisibility(fuseId, android.view.View.GONE)
            return
        }
        val urgent = row.deadlineMillis - System.currentTimeMillis() < 24 * 3_600_000L
        rv.setTextViewText(titleId, row.title)
        rv.setTextColor(titleId, p.ink)
        rv.setTextViewText(cdId, mixed(context, Format.remaining(row.deadlineMillis, System.currentTimeMillis()), R.font.maple_mono_bold_data, R.font.source_han_cn_700))
        rv.setTextColor(cdId, if (urgent) p.red else p.ink)
        applyFuse(rv, fuseId, row.deadlineMillis)
    }

    // ---------- 装饰：台历红签 ----------

    private fun applyCalTab(rv: RemoteViews, p: Palette, row: DdlRow?) {
        if (row == null) {
            rv.setViewVisibility(R.id.widget_calpg, android.view.View.GONE)
            return
        }
        rv.setViewVisibility(R.id.widget_calpg, android.view.View.VISIBLE)
        rv.setInt(R.id.widget_calpg, "setBackgroundResource", p.calpgBgRes)
        val z = Instant.ofEpochMilli(row.deadlineMillis).atZone(ZoneId.systemDefault())
        rv.setTextViewText(R.id.widget_calpg_month, "${z.monthValue}月")
        rv.setTextColor(R.id.widget_calpg_month, p.calpgInk)
        rv.setTextViewText(R.id.widget_calpg_day, z.dayOfMonth.toString())
        rv.setTextColor(R.id.widget_calpg_day, p.calpgInk)
    }

    // ---------- 装饰：单焦点卡周尺 ----------

    private fun applyRuler(rv: RemoteViews, p: Palette, model: DdlWidgetModel) {
        if (!model.rulerVisible) {
            rv.setViewVisibility(R.id.widget_ruler, android.view.View.GONE)
            return
        }
        rv.setViewVisibility(R.id.widget_ruler, android.view.View.VISIBLE)
        rv.setInt(R.id.widget_ruler_track, "setBackgroundResource", p.trackRes)
        rv.setProgressBar(R.id.widget_ruler_bar, 168, model.weekProgressHours, false)
    }

    // ---------- 装饰：多行卡行内燃线 ----------

    /** 燃线 = 今日 0 点烧到该条截止；截止在 7 日窗外（稍后）只留空轨。 */
    private fun applyFuse(rv: RemoteViews, fuseId: Int, deadlineMillis: Long) {
        val startToday = startOfToday()
        val weekEnd = startToday + 7 * 86_400_000L
        if (deadlineMillis >= weekEnd) {
            rv.setViewVisibility(fuseId, android.view.View.GONE)
            return
        }
        rv.setViewVisibility(fuseId, android.view.View.VISIBLE)
        val span = deadlineMillis - startToday
        val pct = if (span <= 0) 100
        else (((System.currentTimeMillis() - startToday).toDouble() / span) * 100).toInt().coerceIn(0, 100)
        rv.setProgressBar(fuseId, 100, pct, false)
    }

    // ---------- 装饰：多行卡周多点条 ----------

    private val STRIP_LABELS = intArrayOf(
        R.id.widget_week_day0, R.id.widget_week_day1, R.id.widget_week_day2,
        R.id.widget_week_day3, R.id.widget_week_day4, R.id.widget_week_day5, R.id.widget_week_day6,
    )
    private val STRIP_CELLS = intArrayOf(
        R.id.widget_week_cell0, R.id.widget_week_cell1, R.id.widget_week_cell2,
        R.id.widget_week_cell3, R.id.widget_week_cell4, R.id.widget_week_cell5, R.id.widget_week_cell6,
    )
    private val STRIP_DOTS = intArrayOf(
        R.id.widget_week_dot0_a, R.id.widget_week_dot0_b,
        R.id.widget_week_dot1_a, R.id.widget_week_dot1_b,
        R.id.widget_week_dot2_a, R.id.widget_week_dot2_b,
        R.id.widget_week_dot3_a, R.id.widget_week_dot3_b,
        R.id.widget_week_dot4_a, R.id.widget_week_dot4_b,
        R.id.widget_week_dot5_a, R.id.widget_week_dot5_b,
        R.id.widget_week_dot6_a, R.id.widget_week_dot6_b,
    )

    private fun applyWeekStrip(rv: RemoteViews, p: Palette, model: DdlWidgetModel) {
        if (model.upcoming.isEmpty() || model.weekDots.all { it == 0 }) {
            rv.setViewVisibility(R.id.widget_week_strip, android.view.View.GONE)
            return
        }
        rv.setViewVisibility(R.id.widget_week_strip, android.view.View.VISIBLE)
        val today = LocalDate.now(ZoneId.systemDefault())
        for (n in 0..6) {
            val date = today.plusDays(n.toLong())
            rv.setTextViewText(STRIP_LABELS[n], date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.CHINA))
            rv.setTextColor(STRIP_LABELS[n], p.gray)
            rv.setInt(STRIP_CELLS[n], "setBackgroundResource", if (n == 0) p.cellTodayRes else p.cellRes)
            val count = model.weekDots[n]
            rv.setViewVisibility(STRIP_DOTS[n * 2], if (count > 0) android.view.View.VISIBLE else android.view.View.GONE)
            rv.setInt(STRIP_DOTS[n * 2], "setBackgroundResource", if (n == 0) R.drawable.dot_red else p.indDotRes)
            rv.setViewVisibility(STRIP_DOTS[n * 2 + 1], if (count > 1) android.view.View.VISIBLE else android.view.View.GONE)
            rv.setInt(STRIP_DOTS[n * 2 + 1], "setBackgroundResource", if (n == 0) R.drawable.dot_red_soft else p.indDotRes)
        }
    }

    // ---------- 文案工具 ----------

    private fun startOfToday(): Long =
        LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun absoluteLine(deadlineMillis: Long): String {
        val z = Instant.ofEpochMilli(deadlineMillis).atZone(ZoneId.systemDefault())
        return "${z.monthValue}月${z.dayOfMonth}日 ${weekday(deadlineMillis)} ${hhmm(deadlineMillis)}"
    }

    private fun weekday(deadlineMillis: Long): String =
        Instant.ofEpochMilli(deadlineMillis).atZone(ZoneId.systemDefault())
            .dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINA)

    private fun hhmm(deadlineMillis: Long): String {
        val z = Instant.ofEpochMilli(deadlineMillis).atZone(ZoneId.systemDefault())
        return "${z.hour.toString().padStart(2, '0')}:${z.minute.toString().padStart(2, '0')}"
    }

    // ---------- 西文×思源 混排（桌面版的 DataFont/FigureFont 链） ----------

    private val typefaceCache = mutableMapOf<Int, Typeface?>()

    private fun typeface(context: Context, @FontRes res: Int): Typeface? =
        typefaceCache.getOrPut(res) { ResourcesCompat.getFont(context, res) }

    /**
     * 分段规则：CJK（含全角）每个连续段套思源，其余段套西文（数字/冒号走 Maple / Serif）。
     * API < 28 无 TypefaceSpan(Typeface)，保持原样由 XML fontFamily + 系统回退兜底。
     */
    private fun mixed(context: Context, text: String, latinRes: Int, hanRes: Int): CharSequence {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return text
        val ss = SpannableString(text)
        var i = 0
        while (i < text.length) {
            val han = isHanChar(text[i])
            var j = i
            while (j < text.length && isHanChar(text[j]) == han) j++
            val tf = typeface(context, if (han) hanRes else latinRes)
            if (tf != null) ss.setSpan(TypefaceSpan(tf), i, j, 0)
            i = j
        }
        return ss
    }

    private fun isHanChar(c: Char): Boolean =
        c.code in 0x2E80..0x9FFF || c.code in 0xF900..0xFAFF || c.code in 0xFF00..0xFFEF ||
            c.code == 0x00B7 || c.code == 0x2014 || c.code == 0x2026
}

/** 仓库内部映射：实体 → 行（与 ViewModel 内字段拷贝保持同义）。 */
private fun DdlEntity.toRow(): DdlRow =
    DdlRow(id, title, note, deadlineEpochMillis, leadsMinutes, isDone, muted)
