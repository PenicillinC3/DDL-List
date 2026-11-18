package com.gammac3.ddlapp.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.FontFamily
import android.os.Build
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/**
 * 一套字面，两个文件：
 *   Han   思源宋体（OFL）——汉字与中文标点，作为底字；
 *   Latin Timeless Serif Text（Timeless Free Font License）——西文、数字与拉丁标点。
 *
 * 两者不合并。Timeless 许可禁止修改、合并、改名，所以两份 ttf 原样随包发布，
 * 运行时用 Typeface.CustomFallbackBuilder（API 29+）把 Timeless 挂成底字的
 * 逐字回退：缺字才回退，因此汉字永远走宋体，西文与数字永远走 Timeless。
 * 底字用宋体也把行高/基线保持在中文侧（1.151 / -0.286 em），中英混排不跳行。
 *
 * API 28 及以下没有 CustomFallbackBuilder，只能退到底字本身：中文仍是宋体，
 * 西文会落到思源宋体自带的拉丁（Source Serif），字形不同但版式不破。
 */
object ScriptTypeface {

    private const val HAN_400 = "fonts/han_serif_400.ttf"
    private const val HAN_600 = "fonts/han_serif_600.ttf"
    private const val LATIN_400 = "fonts/timeless_serif_400.ttf"
    private const val LATIN_600 = "fonts/timeless_serif_600.ttf"

    private val cache = HashMap<Boolean, Typeface>()

    /** FontFamily/Font 持有字体数据的 native 缓冲，必须活到进程结束。 */
    private val keepAlive = ArrayList<Any>(4)

    /** 小组件与 Compose 共用；按字重取（600 及以上为 semibold）。 */
    @Synchronized
    fun get(context: Context, semibold: Boolean): Typeface {
        cache[semibold]?.let { return it }
        val app = context.applicationContext
        val han = if (semibold) HAN_600 else HAN_400
        val latin = if (semibold) LATIN_600 else LATIN_400
        return compose(app, han, latin).also { cache[semibold] = it }
    }

    private fun compose(context: Context, hanPath: String, latinPath: String): Typeface {
        // API 28 及以下没有 CustomFallbackBuilder：直接交底字，汉字正确、西文走宋体自带拉丁。
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return loadTypeface(context, hanPath) ?: Typeface.DEFAULT
        }
        return try {
            // 底字与回退字都必须是 FontFamily（不是 Typeface）才能组合。
            val family = loadFamily(context, hanPath)
            val latin = loadFamily(context, latinPath)
            keepAlive += family
            keepAlive += latin
            Typeface.CustomFallbackBuilder(family).addCustomFallback(latin).build()
        } catch (_: Throwable) {
            // 某些 ROM 的自定义字体栈不接受组合；宁可只丢西文字形，也不让界面崩。
            loadTypeface(context, hanPath) ?: Typeface.DEFAULT
        }
    }

    private fun loadTypeface(context: Context, path: String): Typeface? = try {
        Typeface.Builder(context.assets, path).build()
    } catch (_: Throwable) {
        null
    }

    private fun loadFamily(context: Context, path: String): FontFamily {
        val bytes = context.assets.open(path).use { it.readBytes() }
        val buffer = java.nio.ByteBuffer.allocateDirect(bytes.size)
            .put(bytes)
            .apply { rewind() }
        return FontFamily.Builder(android.graphics.fonts.Font.Builder(buffer).build()).build()
    }
}

/**
 * Compose 侧的一档字重。[semibold] 与 [weight] 分开：标题用 400 档，
 * 但样式里偶尔会请求 600/700（继承自 Material 基线），此时仍保持正体。
 */
internal class ScriptFont(
    override val weight: FontWeight,
    private val semibold: Boolean,
) : AndroidFont(
    FontLoadingStrategy.Blocking,
    object : AndroidFont.TypefaceLoader {
        override fun loadBlocking(context: Context, font: AndroidFont): Typeface =
            ScriptTypeface.get(context, (font as? ScriptFont)?.semibold ?: false)

        override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface =
            loadBlocking(context, font)
    },
    FontVariation.Settings(),
) {
    override val style: FontStyle = FontStyle.Normal
}

internal fun scriptFont(
    weight: FontWeight,
    semibold: Boolean = weight.weight >= 600,
): Font = ScriptFont(weight, semibold)
