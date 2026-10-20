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
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 桌面小组件渲染与刷新（RemoteViews + XML 布局，不走 Glance：免新依赖、
 * XML 直接引用 res/font 字体、R8 零增量）。数据变动 / 应用回前台 / 提醒闹钟触发 /
 * 开机重建 都会调 [refresh]，把三款重新渲染一遍（逐次整算，代价极小）。
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
            val ids = manager.getAppWidgetIds(ComponentName(context, spec.receiver)) ?: continue
            if (ids.isEmpty()) continue
            manager.updateAppWidget(ids, render(context, spec, model))
        }
    }

    private data class Spec(val receiver: Class<out android.appwidget.AppWidgetProvider>, val layout: Int)
    private val SPECS = listOf(
        Spec(DdlWidgetV1Receiver::class.java, R.layout.widget_v1),
        Spec(DdlWidgetV2Receiver::class.java, R.layout.widget_v2),
        Spec(DdlWidgetV3Receiver::class.java, R.layout.widget_v3),
    )

    // ---------- 调色（与 Theme.kt 令牌一致） ----------

    private class Palette(val bgRes: Int, val ink: Int, val gray: Int, val red: Int, val indigo: Int)

    private fun palette(context: Context): Palette {
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        return if (night) Palette(R.drawable.widget_bg_dark, 0xFFE8ECF1.toInt(), 0xFF8B949E.toInt(), 0xFFFF7A7F.toInt(), 0xFF8FA3FF.toInt())
        else Palette(R.drawable.widget_bg_light, 0xFF101828.toInt(), 0xFF5F6771.toInt(), 0xFFE5484D.toInt(), 0xFF3A5AF0.toInt())
    }

    // ---------- 渲染 ----------

    private fun render(context: Context, spec: Spec, model: DdlWidgetModel): RemoteViews {
        val p = palette(context)
        val rv = RemoteViews(context.packageName, spec.layout)
        rv.setInt(R.id.widget_root, "setBackgroundResource", p.bgRes)
        rv.setOnClickPendingIntent(R.id.widget_root, openIntent(context))

        when (spec.layout) {
            R.layout.widget_v1 -> renderV1(context, rv, p, model)
            R.layout.widget_v2 -> renderV2(context, rv, p, model)
            else -> renderV3(context, rv, p, model)
        }
        return rv
    }

    private fun openIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 100,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun renderV1(context: Context, rv: RemoteViews, p: Palette, model: DdlWidgetModel) {
        val nearest = model.nearest
        rv.setTextViewText(R.id.widget_header, "最近到期")
        rv.setTextColor(R.id.widget_header, p.gray)
        rv.setTextViewText(R.id.widget_total, mixed(context, "共 ${model.activeCount} 条未完成", R.font.timeless_serif_text, R.font.source_han_cn_500))
        rv.setTextColor(R.id.widget_total, p.gray)

        if (nearest == null) {
            rv.setTextViewText(R.id.widget_title, "现在很清净")
            rv.setTextColor(R.id.widget_title, p.ink)
            rv.setTextViewText(R.id.widget_countdown, mixed(context, "——", R.font.maple_mono_bold_data, R.font.source_han_cn_700))
            rv.setTextColor(R.id.widget_countdown, p.gray)
            rv.setTextViewText(R.id.widget_date, if (model.overdueCount > 0) "另有 ${model.overdueCount} 条已逾期" else "")
            rv.setTextColor(R.id.widget_date, p.gray)
            rv.setTextViewText(R.id.widget_meta, "点按小组件添加新的条目")
            rv.setTextColor(R.id.widget_meta, p.gray)
            return
        }
        val urgent = nearest.deadlineMillis - System.currentTimeMillis() < 24 * 3_600_000L
        rv.setTextViewText(R.id.widget_title, nearest.title)
        rv.setTextColor(R.id.widget_title, p.ink)
        rv.setTextViewText(R.id.widget_countdown, mixed(context, Format.remaining(nearest.deadlineMillis, System.currentTimeMillis()), R.font.maple_mono_bold_data, R.font.source_han_cn_700))
        rv.setTextColor(R.id.widget_countdown, if (urgent) p.red else p.ink)
        rv.setTextViewText(R.id.widget_date, mixed(context, absoluteLine(nearest.deadlineMillis), R.font.timeless_serif_text, R.font.source_han_cn_400))
        rv.setTextColor(R.id.widget_date, p.gray)
        rv.setTextViewText(R.id.widget_meta, mixed(context, "今日 ${model.todayCount} 条 · 本周 ${model.weekCount} 条", R.font.timeless_serif_text, R.font.source_han_cn_400))
        rv.setTextColor(R.id.widget_meta, p.gray)
    }

    private fun renderV2(context: Context, rv: RemoteViews, p: Palette, model: DdlWidgetModel) {
        val nearest = model.nearest
        if (nearest == null) {
            rv.setTextViewText(R.id.widget_title, "现在很清净")
            rv.setTextColor(R.id.widget_title, p.ink)
            rv.setTextViewText(R.id.widget_countdown, mixed(context, "——", R.font.maple_mono_bold_data, R.font.source_han_cn_700))
            rv.setTextColor(R.id.widget_countdown, p.gray)
            rv.setTextViewText(R.id.widget_date, "")
            return
        }
        val urgentLine = nearest.deadlineMillis < todayEnd()
        val sameDay = isSameDay(nearest.deadlineMillis)
        rv.setTextViewText(R.id.widget_title, nearest.title)
        rv.setTextColor(R.id.widget_title, p.ink)
        rv.setTextViewText(R.id.widget_countdown, mixed(context, Format.remaining(nearest.deadlineMillis, System.currentTimeMillis()), R.font.maple_mono_bold_data, R.font.source_han_cn_700))
        rv.setTextColor(R.id.widget_countdown, if (urgentLine) p.red else p.ink)
        rv.setTextViewText(
            R.id.widget_date,
            if (sameDay) "今晚 ${hhmm(nearest.deadlineMillis)}"
            else "${weekday(nearest.deadlineMillis)} ${hhmm(nearest.deadlineMillis)}",
        )
        rv.setTextColor(R.id.widget_date, p.gray)
    }

    private fun renderV3(context: Context, rv: RemoteViews, p: Palette, model: DdlWidgetModel) {
        rv.setTextViewText(R.id.widget_header, "最近到期")
        rv.setTextColor(R.id.widget_header, p.gray)
        rv.setTextViewText(R.id.widget_total, mixed(context, "共 ${model.activeCount} 条未完成", R.font.timeless_serif_text, R.font.source_han_cn_500))
        rv.setTextColor(R.id.widget_total, p.gray)

        val rows = model.upcoming
        renderRow(context, rv, p, R.id.widget_row1_title, R.id.widget_row1_cd,
            rows.getOrNull(0), emptyTitle = "现在很清净，没有即将到期的条目")
        val second = rows.getOrNull(1)
        rv.setViewVisibility(R.id.widget_row2_container, if (second == null) android.view.View.GONE else android.view.View.VISIBLE)
        renderRow(context, rv, p, R.id.widget_row2_title, R.id.widget_row2_cd, second, emptyTitle = "")

        if (rows.isEmpty()) {
            rv.setTextViewText(R.id.widget_meta, if (model.overdueCount > 0) "另有 ${model.overdueCount} 条已逾期" else "点按小组件添加新的条目")
        } else {
            rv.setTextViewText(R.id.widget_meta, mixed(context, "今日 ${model.todayCount} 条 · 本周 ${model.weekCount} 条", R.font.timeless_serif_text, R.font.source_han_cn_400))
        }
        rv.setTextColor(R.id.widget_meta, p.gray)
    }

    private fun renderRow(
        context: Context, rv: RemoteViews, p: Palette,
        titleId: Int, cdId: Int, row: DdlRow?, emptyTitle: String,
    ) {
        if (row == null) {
            rv.setTextViewText(titleId, emptyTitle)
            rv.setTextColor(titleId, p.gray)
            rv.setTextViewText(cdId, "——")
            rv.setTextColor(cdId, p.gray)
            return
        }
        val urgent = row.deadlineMillis - System.currentTimeMillis() < 24 * 3_600_000L
        rv.setTextViewText(titleId, row.title)
        rv.setTextColor(titleId, p.ink)
        rv.setTextViewText(cdId, mixed(context, Format.remaining(row.deadlineMillis, System.currentTimeMillis()), R.font.maple_mono_bold_data, R.font.source_han_cn_700))
        rv.setTextColor(cdId, if (urgent) p.red else p.indigo)
    }

    // ---------- 文案工具 ----------

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

    private fun isSameDay(deadlineMillis: Long): Boolean {
        val zone = ZoneId.systemDefault()
        val now = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone).toLocalDate()
        return Instant.ofEpochMilli(deadlineMillis).atZone(zone).toLocalDate() == now
    }

    private fun todayEnd(): Long {
        val zone = ZoneId.systemDefault()
        return Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone).toLocalDate()
            .plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    }

    // ---------- 西文×思源 混排（桌面版的 DataFont/FigureFont 链） ----------

    private val typefaceCache = mutableMapOf<Int, Typeface?>()

    private fun typeface(context: Context, @FontRes res: Int): Typeface? =
        typefaceCache.getOrPut(res) { ResourcesCompat.getFont(context, res) }

    /**
     * 键盘规则：CJK（含全角）每个连续段套思源，其余段套西文（数字/冒号走 Maple / Serif）。
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
