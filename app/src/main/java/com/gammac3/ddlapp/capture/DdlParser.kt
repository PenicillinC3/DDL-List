package com.gammac3.ddlapp.capture

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * 纯 Kotlin 的 DDL 解析器：从页面文字中提取标题与截止时间。
 *
 * 设计约束：
 * - 不依赖 Android / UI locale，中英文语法同时匹配；
 * - 只产出候选，绝不自动入库；结果始终由用户确认；
 * - 输入原文只在内存中停留，不落盘、不打日志。
 */
object DdlParser {

    private const val MAX_TEXT_CHARS = 12_000
    private const val MAX_TITLE_CHARS = 60
    private const val MAX_CANDIDATES = 3
    private const val MERGE_WINDOW_MILLIS = 60_000L

    private const val MONTH_PATTERN =
        "jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|" +
            "aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?"

    private val ISO_DATE = Regex("""(?<!\d)(20\d{2})[-/.](\d{1,2})[-/.](\d{1,2})(?!\d)""")
    private val CN_FULL_DATE = Regex("""(?<!\d)(\d{4})\s*年\s*(\d{1,2})\s*月\s*(\d{1,2})\s*[日号]?""")
    private val CN_MONTH_DAY = Regex("""(?<!\d)(\d{1,2})\s*月\s*(\d{1,2})\s*[日号]""")
    private val NUM_MONTH_DAY = Regex("""(?<!\d)(\d{1,2})[-/](\d{1,2})(?!\d)""")
    private val EN_MONTH_DAY = Regex(
        """\b($MONTH_PATTERN)\.?\s+(\d{1,2})(?:st|nd|rd|th)?(?:\s*,?\s*(20\d{2}))?\b""",
        RegexOption.IGNORE_CASE,
    )
    private val EN_DAY_MONTH = Regex(
        """\b(\d{1,2})(?:st|nd|rd|th)?\s+($MONTH_PATTERN)(?:\s*,?\s*(20\d{2}))?\b""",
        RegexOption.IGNORE_CASE,
    )

    private val CN_RELATIVE = Regex("""大后天|后天|明天|明晚|今晚|今天""")
    private val CN_DAYS_LATER = Regex("""(?<!\d)(\d{1,2}|[一二三四五六七八九十]{1,3})\s*天\s*后""")
    private val CN_WEEKS_LATER = Regex("""(\d{1,2}|[一二三四五六七八九十]{1,3})\s*(?:周|星期|礼拜)\s*后""")
    private val CN_WEEKDAY = Regex("""(这|本|下)?\s*(?:周|星期|礼拜)\s*([一二三四五六日天])""")
    private val EN_RELATIVE = Regex("""\b(today|tomorrow|tonight)\b""", RegexOption.IGNORE_CASE)
    private val EN_WEEKDAY = Regex(
        """\b(this|next)?\s*(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val EN_CLOCK = Regex("""(?<!\d)(\d{1,2})(?::(\d{2}))?\s*(am|pm)\b""", RegexOption.IGNORE_CASE)
    private val NOON_MIDNIGHT = Regex("""\b(noon|midnight)\b""", RegexOption.IGNORE_CASE)
    private val CN_CLOCK = Regex("""(凌晨|早上|上午|中午|下午|傍晚|晚上)?\s*(\d{1,2})\s*[点時时]\s*(半|\d{1,2}\s*分?)?""")
    private val COLON_CLOCK = Regex("""(?<!\d)(\d{1,2})[:：](\d{2})(?!\d)""")

    private val DEADLINE_KEYWORD = Regex(
        """截止时间|截止日期|截止|ddl|deadline|due|submit\s+by|before|\bby\b|之前|以前|前""",
        RegexOption.IGNORE_CASE,
    )

    private val LEADING_BULLET = Regex("""^(?:[\s\-–—•·*]+|\d{1,3}[.、)）]\s*|[（(]\d{1,3}[)）]\s*)""")
    private val LEADING_KEYWORD = Regex(
        """^(?:截止时间|截止日期|截止|ddl|deadline|due|submit\s+by|before)[:：]?\s*""",
        RegexOption.IGNORE_CASE,
    )
    private val TRAILING_STRONG = Regex(
        """\s*(?:截止时间|截止日期|deadline|due|submit\s+by)[:：]?\s*$""",
        RegexOption.IGNORE_CASE,
    )
    private val TRAILING_WEAK = Regex(
        """(?:^|[\s，,。.;；:：、])(?:截止|之前|以前|前|before|by)[:：]?\s*$""",
        RegexOption.IGNORE_CASE,
    )
    private val EDGE_PUNCT = Regex("""^[\s，,。.;；:：!！?？、\-–—]+|[\s，,。.;；:：!！?？、\-–—]+$""")
    private val WHITESPACE = Regex("""\s+""")
    private val TITLE_NOISE = Regex(
        """昨天|前天|刚刚|上午|下午|中午|晚上|凌晨|早上|今晚|明晚|周[一二三四五六日天]|星期[一二三四五六日天]|礼拜[一二三四五六日天]""",
    )
    private val RESIDUAL_NOISE = Regex("""[\s\d，,。.;；:：!！?？、\-–—/\\（）()【】\[\]<>《》""''“”‘’]+""")

    fun parse(text: String, now: ZonedDateTime): List<DdlCandidate> {
        if (text.isBlank()) return emptyList()

        val lines = truncate(text)
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (lines.isEmpty()) return emptyList()

        val candidates = mutableListOf<DdlCandidate>()
        lines.forEachIndexed { index, line ->
            val parsed = parseLine(line, now) ?: return@forEachIndexed
            val title = pickTitle(lines, index, parsed)
            candidates += DdlCandidate(
                title = title,
                deadline = parsed.deadline,
                confidence = parsed.confidence,
                sourceLine = line,
            )
        }

        val merged = merge(candidates)
        val sorted = merged.sortedWith(
            compareByDescending<DdlCandidate> { it.confidence }
                .thenBy { it.deadline?.toInstant()?.toEpochMilli() ?: Long.MAX_VALUE },
        )
        if (sorted.isEmpty()) return listOf(noDeadlineCandidate(lines))
        return sorted.take(MAX_CANDIDATES)
    }

    /** 超长页面保留开头与结尾：开头通常是标题，结尾通常是截止信息。 */
    private fun truncate(text: String): String {
        if (text.length <= MAX_TEXT_CHARS) return text
        val headLength = MAX_TEXT_CHARS * 2 / 3
        val head = text.take(headLength)
        val tail = text.takeLast(MAX_TEXT_CHARS - headLength - 1)
        return "$head\n$tail"
    }

    // ---------------------------------------------------------------- line

    private data class DateHit(
        val date: LocalDate,
        val start: Int,
        val end: Int,
        val relative: Boolean,
    )

    private data class TimeHit(
        val time: LocalTime,
        val start: Int,
        val end: Int,
        val rollover: Boolean = false,
    )

    private data class LineParse(
        val deadline: ZonedDateTime,
        val confidence: Float,
        val matchStart: Int,
        val matchEnd: Int,
    )

    private fun parseLine(line: String, now: ZonedDateTime): LineParse? {
        val timeHit = findTime(line)
        val dateHit = findDate(line, now, timeHit)
        if (dateHit == null && timeHit == null) return null

        val deadline = resolveDeadline(dateHit, timeHit, now) ?: return null
        val hasKeyword = DEADLINE_KEYWORD.containsMatchIn(line)
        val confidence = when {
            hasKeyword && dateHit != null && timeHit != null -> 0.9f
            hasKeyword && dateHit != null -> 0.8f
            dateHit?.relative == true && timeHit != null -> 0.75f
            dateHit != null && timeHit != null -> 0.65f
            dateHit != null -> 0.6f
            timeHit != null -> 0.5f
            else -> return null
        }

        val start = listOfNotNull(dateHit?.start, timeHit?.start).min()
        val end = listOfNotNull(dateHit?.end, timeHit?.end).max()
        return LineParse(deadline, confidence, start, end)
    }

    private fun resolveDeadline(
        dateHit: DateHit?,
        timeHit: TimeHit?,
        now: ZonedDateTime,
    ): ZonedDateTime? {
        val time = timeHit?.time
        val date = dateHit?.date
        return when {
            date != null -> {
                val target = if (timeHit?.rollover == true) date.plusDays(1) else date
                ZonedDateTime.of(target, time ?: LocalTime.of(23, 59), now.zone)
            }
            time != null -> {
                var candidate = ZonedDateTime.of(now.toLocalDate(), time, now.zone)
                if (timeHit.rollover) candidate = candidate.plusDays(1)
                if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)
                candidate
            }
            else -> null
        }
    }

    // ---------------------------------------------------------------- time

    private fun findTime(line: String): TimeHit? {
        val hits = mutableListOf<TimeHit>()

        EN_CLOCK.findAll(line).forEach { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return@forEach
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            val suffix = match.groupValues[3].lowercase()
            if (hour !in 1..12 || minute !in 0..59) return@forEach
            val h = when {
                suffix == "pm" && hour < 12 -> hour + 12
                suffix == "am" && hour == 12 -> 0
                else -> hour
            }
            hits += TimeHit(LocalTime.of(h, minute), match.range.first, match.range.last + 1)
        }

        NOON_MIDNIGHT.findAll(line).forEach { match ->
            val time = if (match.groupValues[1].equals("noon", ignoreCase = true)) {
                LocalTime.NOON
            } else {
                LocalTime.MIDNIGHT
            }
            hits += TimeHit(time, match.range.first, match.range.last + 1)
        }

        CN_CLOCK.findAll(line).forEach { match ->
            val period = match.groupValues[1].ifEmpty { null }
            val hour = match.groupValues[2].toIntOrNull() ?: return@forEach
            if (hour !in 0..23) return@forEach
            val minute = when {
                match.groupValues[3] == "半" -> 30
                match.groupValues[3].isNotEmpty() ->
                    match.groupValues[3].filter { it.isDigit() }.toIntOrNull() ?: 0
                else -> 0
            }
            if (minute !in 0..59) return@forEach
            val (h, rollover) = applyPeriod(hour, period)
            hits += TimeHit(LocalTime.of(h, minute), match.range.first, match.range.last + 1, rollover)
        }

        COLON_CLOCK.findAll(line).forEach { match ->
            val overlaps = hits.any { hit ->
                match.range.first < hit.end && match.range.last + 1 > hit.start
            }
            if (overlaps) return@forEach
            val hour = match.groupValues[1].toIntOrNull() ?: return@forEach
            val minute = match.groupValues[2].toIntOrNull() ?: return@forEach
            if (hour !in 0..23 || minute !in 0..59) return@forEach
            val (h, rollover) = applyPeriod(hour, periodBefore(line, match.range.first))
            hits += TimeHit(LocalTime.of(h, minute), match.range.first, match.range.last + 1, rollover)
        }

        return hits.minWithOrNull(compareBy({ it.start }, { -(it.end - it.start) }))
    }

    private fun applyPeriod(hour: Int, period: String?): Pair<Int, Boolean> = when (period) {
        "凌晨", "早上", "上午" -> (if (hour == 12) 0 else hour) to false
        "中午" -> (if (hour in 1..10) hour + 12 else hour) to false
        "下午", "傍晚" -> (if (hour < 12) hour + 12 else hour) to false
        "晚上" -> when {
            hour == 12 -> 0 to true
            hour < 12 -> hour + 12 to false
            else -> hour to false
        }
        else -> hour to false
    }

    private fun periodBefore(line: String, index: Int): String? {
        val prefix = line.substring(0, index).trimEnd()
        return PERIOD_WORDS.firstOrNull { prefix.endsWith(it) }
    }

    private val PERIOD_WORDS = listOf("凌晨", "早上", "上午", "中午", "下午", "傍晚", "晚上")

    // ---------------------------------------------------------------- date

    private fun findDate(line: String, now: ZonedDateTime, timeHit: TimeHit?): DateHit? {
        val hits = mutableListOf<DateHit>()
        val today = now.toLocalDate()

        ISO_DATE.findAll(line).forEach { match ->
            val date = localDateOrNull(
                match.groupValues[1].toIntOrNull(),
                match.groupValues[2].toIntOrNull(),
                match.groupValues[3].toIntOrNull(),
            ) ?: return@forEach
            hits += DateHit(date, match.range.first, match.range.last + 1, relative = false)
        }

        CN_FULL_DATE.findAll(line).forEach { match ->
            val date = localDateOrNull(
                match.groupValues[1].toIntOrNull(),
                match.groupValues[2].toIntOrNull(),
                match.groupValues[3].toIntOrNull(),
            ) ?: return@forEach
            hits += DateHit(date, match.range.first, match.range.last + 1, relative = false)
        }

        CN_MONTH_DAY.findAll(line).forEach { match ->
            val date = resolveNoYear(
                match.groupValues[1].toIntOrNull(),
                match.groupValues[2].toIntOrNull(),
                today,
            ) ?: return@forEach
            hits += DateHit(date, match.range.first, match.range.last + 1, relative = false)
        }

        val numericContext = timeHit != null || DEADLINE_KEYWORD.containsMatchIn(line)
        if (numericContext) {
            NUM_MONTH_DAY.findAll(line).forEach { match ->
                val date = resolveNoYear(
                    match.groupValues[1].toIntOrNull(),
                    match.groupValues[2].toIntOrNull(),
                    today,
                ) ?: return@forEach
                hits += DateHit(date, match.range.first, match.range.last + 1, relative = false)
            }
        }

        EN_MONTH_DAY.findAll(line).forEach { match ->
            val month = monthNumber(match.groupValues[1]) ?: return@forEach
            val day = match.groupValues[2].toIntOrNull() ?: return@forEach
            val year = match.groupValues[3].toIntOrNull()
            val date = if (year != null) {
                localDateOrNull(year, month, day)
            } else {
                resolveNoYear(month, day, today)
            } ?: return@forEach
            hits += DateHit(date, match.range.first, match.range.last + 1, relative = false)
        }

        EN_DAY_MONTH.findAll(line).forEach { match ->
            val month = monthNumber(match.groupValues[2]) ?: return@forEach
            val day = match.groupValues[1].toIntOrNull() ?: return@forEach
            val year = match.groupValues[3].toIntOrNull()
            val date = if (year != null) {
                localDateOrNull(year, month, day)
            } else {
                resolveNoYear(month, day, today)
            } ?: return@forEach
            hits += DateHit(date, match.range.first, match.range.last + 1, relative = false)
        }

        CN_RELATIVE.findAll(line).forEach { match ->
            val date = when (match.value) {
                "今天", "今晚" -> today
                "明天", "明晚" -> today.plusDays(1)
                "后天" -> today.plusDays(2)
                "大后天" -> today.plusDays(3)
                else -> null
            } ?: return@forEach
            hits += DateHit(date, match.range.first, match.range.last + 1, relative = true)
        }

        CN_DAYS_LATER.findAll(line).forEach { match ->
            val days = parseCount(match.groupValues[1]) ?: return@forEach
            if (days !in 1..30) return@forEach
            hits += DateHit(today.plusDays(days.toLong()), match.range.first, match.range.last + 1, relative = true)
        }

        CN_WEEKS_LATER.findAll(line).forEach { match ->
            val weeks = parseCount(match.groupValues[1]) ?: return@forEach
            if (weeks !in 1..8) return@forEach
            hits += DateHit(
                today.plusWeeks(weeks.toLong()),
                match.range.first,
                match.range.last + 1,
                relative = true,
            )
        }

        CN_WEEKDAY.findAll(line).forEach { match ->
            val prefix = match.groupValues[1]
            val dayOfWeek = chineseWeekday(match.groupValues[2]) ?: return@forEach
            val date = resolveWeekday(
                now = now,
                target = dayOfWeek,
                thisWeek = prefix == "这" || prefix == "本",
                nextWeek = prefix == "下",
                time = timeHit?.time,
            )
            hits += DateHit(date, match.range.first, match.range.last + 1, relative = true)
        }

        EN_RELATIVE.findAll(line).forEach { match ->
            val date = when (match.groupValues[1].lowercase()) {
                "today", "tonight" -> today
                "tomorrow" -> today.plusDays(1)
                else -> null
            } ?: return@forEach
            hits += DateHit(date, match.range.first, match.range.last + 1, relative = true)
        }

        EN_WEEKDAY.findAll(line).forEach { match ->
            val prefix = match.groupValues[1].lowercase()
            val dayOfWeek = englishWeekday(match.groupValues[2]) ?: return@forEach
            val date = resolveWeekday(
                now = now,
                target = dayOfWeek,
                thisWeek = prefix == "this",
                nextWeek = prefix == "next",
                time = timeHit?.time,
            )
            hits += DateHit(date, match.range.first, match.range.last + 1, relative = true)
        }

        return hits.minWithOrNull(compareBy({ it.start }, { -(it.end - it.start) }))
    }

    private fun localDateOrNull(year: Int?, month: Int?, day: Int?): LocalDate? {
        if (year == null || month == null || day == null) return null
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }

    private fun resolveNoYear(month: Int?, day: Int?, today: LocalDate): LocalDate? {
        if (month == null || day == null) return null
        val date = runCatching { LocalDate.of(today.year, month, day) }.getOrNull() ?: return null
        return if (date < today) date.plusYears(1) else date
    }

    private fun resolveWeekday(
        now: ZonedDateTime,
        target: DayOfWeek,
        thisWeek: Boolean,
        nextWeek: Boolean,
        time: LocalTime?,
    ): LocalDate {
        val today = now.toLocalDate()
        if (thisWeek || nextWeek) {
            val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
            val base = if (nextWeek) monday.plusWeeks(1) else monday
            return base.plusDays((target.value - 1).toLong())
        }
        var delta = (target.value - today.dayOfWeek.value + 7) % 7
        if (delta == 0) {
            val targetTime = time ?: LocalTime.of(23, 59)
            if (!now.toLocalTime().isBefore(targetTime)) delta = 7
        }
        return today.plusDays(delta.toLong())
    }

    private fun monthNumber(name: String): Int? = when (name.take(3).lowercase()) {
        "jan" -> 1
        "feb" -> 2
        "mar" -> 3
        "apr" -> 4
        "may" -> 5
        "jun" -> 6
        "jul" -> 7
        "aug" -> 8
        "sep" -> 9
        "oct" -> 10
        "nov" -> 11
        "dec" -> 12
        else -> null
    }

    private fun chineseWeekday(char: String): DayOfWeek? = when (char) {
        "一" -> DayOfWeek.MONDAY
        "二" -> DayOfWeek.TUESDAY
        "三" -> DayOfWeek.WEDNESDAY
        "四" -> DayOfWeek.THURSDAY
        "五" -> DayOfWeek.FRIDAY
        "六" -> DayOfWeek.SATURDAY
        "日", "天" -> DayOfWeek.SUNDAY
        else -> null
    }

    private fun englishWeekday(name: String): DayOfWeek? = when (name.lowercase()) {
        "monday" -> DayOfWeek.MONDAY
        "tuesday" -> DayOfWeek.TUESDAY
        "wednesday" -> DayOfWeek.WEDNESDAY
        "thursday" -> DayOfWeek.THURSDAY
        "friday" -> DayOfWeek.FRIDAY
        "saturday" -> DayOfWeek.SATURDAY
        "sunday" -> DayOfWeek.SUNDAY
        else -> null
    }

    private fun parseCount(raw: String): Int? {
        raw.toIntOrNull()?.let { return it }
        return chineseNumber(raw)
    }

    private fun chineseNumber(raw: String): Int? {
        if (raw.isEmpty()) return null
        val digits = mapOf(
            '一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5,
            '六' to 6, '七' to 7, '八' to 8, '九' to 9,
        )
        if (raw == "十") return 10
        val tenIndex = raw.indexOf('十')
        return if (tenIndex >= 0) {
            val tens = if (tenIndex == 0) 1 else digits[raw[tenIndex - 1]] ?: return null
            val ones = if (tenIndex == raw.lastIndex) 0 else digits[raw[tenIndex + 1]] ?: return null
            tens * 10 + ones
        } else {
            var value = 0
            raw.forEach { char -> value = value * 10 + (digits[char] ?: return null) }
            value
        }
    }

    // ---------------------------------------------------------------- title

    private fun pickTitle(lines: List<String>, index: Int, parsed: LineParse): String {
        val line = lines[index]

        val before = line.substring(0, parsed.matchStart.coerceIn(0, line.length))
        cleanTitle(before).takeIf { looksLikeTitle(it) }?.let { return it }

        val after = line.substring(parsed.matchEnd.coerceIn(0, line.length))
        cleanTitle(stripLeadingClause(after)).takeIf { looksLikeTitle(it) }?.let { return it }

        for (previous in index - 1 downTo maxOf(0, index - 3)) {
            cleanTitle(lines[previous]).takeIf { looksLikeTitle(it) }?.let { return it }
        }

        for (candidate in lines) {
            cleanTitle(candidate).takeIf { looksLikeTitle(it) }?.let { return it }
        }
        return ""
    }

    private fun stripLeadingClause(raw: String): String = raw
        .replace(Regex("""^\s*(?:前|之前|以前|before|by)\s*""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""^\s*[，,。.;；:：\-–—]\s*"""), "")

    private fun cleanTitle(raw: String): String {
        var title = raw.trim()
        title = LEADING_BULLET.replace(title, "")
        title = LEADING_KEYWORD.replace(title, "")
        title = TRAILING_STRONG.replace(title, "")
        title = TRAILING_WEAK.replace(title, "")
        title = EDGE_PUNCT.replace(title, "")
        title = WHITESPACE.replace(title, " ").trim()
        if (title.length > MAX_TITLE_CHARS) title = title.take(MAX_TITLE_CHARS).trim()
        return title
    }

    private fun looksLikeTitle(raw: String): Boolean {
        if (raw.length < 2) return false
        var residual = raw
        DATE_TIME_PATTERNS.forEach { residual = it.replace(residual, " ") }
        residual = TITLE_NOISE.replace(residual, " ")
        residual = DEADLINE_KEYWORD.replace(residual, " ")
        residual = RESIDUAL_NOISE.replace(residual, "")
        return residual.length >= 2 && residual.any { it.isLetter() }
    }

    private val DATE_TIME_PATTERNS = listOf(
        ISO_DATE, CN_FULL_DATE, CN_MONTH_DAY, NUM_MONTH_DAY,
        EN_MONTH_DAY, EN_DAY_MONTH,
        CN_RELATIVE, CN_DAYS_LATER, CN_WEEKS_LATER, CN_WEEKDAY,
        EN_RELATIVE, EN_WEEKDAY,
        EN_CLOCK, NOON_MIDNIGHT, CN_CLOCK, COLON_CLOCK,
    )

    // ---------------------------------------------------------------- merge

    private fun merge(candidates: List<DdlCandidate>): List<DdlCandidate> {
        val merged = mutableListOf<DdlCandidate>()
        for (candidate in candidates) {
            val existingIndex = merged.indexOfFirst { existing ->
                val a = existing.deadline
                val b = candidate.deadline
                when {
                    a == null && b == null -> true
                    a != null && b != null -> kotlin.math.abs(
                        a.toInstant().toEpochMilli() - b.toInstant().toEpochMilli(),
                    ) <= MERGE_WINDOW_MILLIS
                    else -> false
                }
            }
            if (existingIndex < 0) {
                merged += candidate
            } else {
                val existing = merged[existingIndex]
                val better = if (candidate.confidence > existing.confidence) candidate else existing
                val title = better.title.ifBlank {
                    existing.title.ifBlank { candidate.title }
                }
                merged[existingIndex] = better.copy(title = title)
            }
        }
        return merged
    }

    private fun noDeadlineCandidate(lines: List<String>): DdlCandidate {
        val title = lines.firstNotNullOfOrNull { line ->
            cleanTitle(line).takeIf { looksLikeTitle(it) }
        } ?: ""
        return DdlCandidate(
            title = title,
            deadline = null,
            confidence = 0.2f,
            sourceLine = "",
        )
    }
}
