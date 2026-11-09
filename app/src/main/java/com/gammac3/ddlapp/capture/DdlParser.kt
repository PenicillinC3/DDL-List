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

    // 标题清洗：只留下任务名本身，去掉称呼、字段标签和正文从句。
    private val LEADING_VOCATIVE = Regex(
        """^(?:各位|亲爱的|尊敬的)?\s*(?:同学|老师|家长|同事|朋友|大家)\s*[们]?\s*[，,：:]\s*""",
    )
    private val LEADING_TITLE_LABEL = Regex(
        """^(?:作业名称|作业标题|任务名称|活动名称|项目名称|课程名称|考试名称|论文题目|标题|题目|主题|名称)\s*[:：]\s*""",
    )
    private val LEADING_SUBMIT_LABEL = Regex("""^(?:提交|上交|上传)\s*(?=截止|时间|日期)""")
    private val TRAILING_BODY_CLAUSE = Regex(
        """[，,；;]\s*(?:请|于|在|将|把|从|对|为|如|若|需|应|可|各|注意|联系|咨询|如有|凡|所有|全体)[^，,；;]*$""",
    )
    private val SENTENCE_END = Regex("""[。！？!?]""")
    private val CLAUSE_PUNCT = Regex("""[，,；;]""")

    // 正文/句子开头的信号：出现在标题位置时通常只是句子片段。
    private val BODY_STARTER = Regex(
        """^(?:请|于|在|将|把|从|对|为|如|若|需|应|可|各|其中|注意|温馨提示|联系|咨询|电话|邮箱|地址|时间|地点|对象|要求|说明|备注|附件|来源|编辑|审核|发布|点击|扫描|长按|关注|转发|分享|收藏|评论|点赞|阅读|返回|首页|消息|通讯录|发现|微信|文件|更多|搜索|取消|确定|保存|如有|凡|所有|全体)""",
    )
    private val SENTENCE_STARTER = Regex(
        """^(?:本次|本学期|本周|本月|这学期|大家|我们|你们|同学们|各位|所有|凡|如有|请注意)""",
    )
    private val SENTENCE_STARTER_EN = Regex(
        """^(?:please|submit|send|upload|complete|finish|make\s+sure|remember|note\s+that|all\s+students|students)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val INSTRUCTION_HINT = Regex(
        """要求|需要|必须|务必|按时|及时|独立完成|认真|仔细|不得|严禁|统一|按照|根据|通过|进行""",
    )
    private val SECTION_HEADING = Regex(
        """^[一二三四五六七八九十百]+[、.．]\s*|^[（(][一二三四五六七八九十]+[)）]\s*""",
    )
    private val BARE_ACTION = Regex(
        """^(?:提交|上交|上传|发送|完成|交|做|写|看|参加|出席|报名|回复|确认|填写)$""",
    )
    private val ACTION_FRAGMENT = Regex(
        """^(?:提交|上交|上传|发送|完成|交|做|写|参加|出席|填写|回复|确认)""",
    )
    private val DEADLINE_TITLE_WORD = Regex(
        """截止|ddl|deadline|due|submit\s+by""",
        RegexOption.IGNORE_CASE,
    )
    private val URL_OR_EMAIL = Regex(
        """https?://|www\.|@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""",
        RegexOption.IGNORE_CASE,
    )
    private val STRONG_TITLE_WORD = Regex(
        """作业|实验|报告|论文|考试|测验|答辩|报名|会议|讲座|活动|比赛|竞赛|项目|任务|课程|问卷|申请|评选|公示|通知|公告|安排|计划|总结|检查|培训|招聘|宣讲|社团|志愿|实习|设计|习题|练习|期中|期末|补考|选课|缴费|注册|材料|展示|汇报|调研|实践|读书|观后感|心得|毕业|开题|复试|面试|笔试|预约|打卡|签到|投票|测评|\b(?:assignment|homework|essay|report|paper|project|exam|quiz|test|midterm|final|thesis|dissertation|presentation|defense|submission|registration|application|meeting|seminar|workshop|lecture|event|competition|contest|survey|questionnaire|proposal|lab|reading|discussion|exercise)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val HEADING_HINT = Regex(
        """^关于|^【|^《|^「|^“|(?:通知|公告|安排|计划|要求|说明|方案|须知|细则|办法)$""",
    )
    private val META_LINE = Regex(
        """^\s*(?:发布时间|更新时间|发布日期|发表时间|编辑时间|修改时间|创建时间|上传时间|发布于|发表于)\s*[:：]?\s*""",
        RegexOption.IGNORE_CASE,
    )
    private val UI_CHROME = setOf(
        "首页", "消息", "通讯录", "发现", "我", "返回", "关闭", "取消", "确定", "完成",
        "保存", "编辑", "删除", "复制", "粘贴", "分享", "转发", "收藏", "评论", "点赞",
        "在看", "阅读原文", "更多", "搜索", "微信", "文件", "图片", "视频", "链接",
        "表情", "相册", "拍摄", "位置", "语音", "名片", "红包", "转账", "发送", "举报",
        "投诉", "关注", "已关注", "朋友", "朋友圈", "公众号", "小程序", "视频号", "扫一扫",
        "看一看", "搜一搜", "订阅号", "服务号", "文件传输助手", "新的朋友", "群聊", "标签",
        "设置", "反馈", "帮助", "登录", "注册", "退出", "个人中心", "我的", "推荐", "热门",
        "最新", "全部", "筛选", "排序", "展开", "收起", "查看全文", "阅读全文", "展开全文",
        "长按识别", "扫描二维码", "点击查看", "点击这里", "知道了", "我知道了", "跳过",
        "下一步", "上一步", "主页", "菜单", "个人主页",
        "home", "back", "close", "cancel", "ok", "done", "save", "edit", "delete", "copy",
        "share", "comment", "like", "more", "search", "settings", "profile", "menu", "next",
        "previous", "skip", "continue", "send", "download", "upload", "open", "view",
        "read more", "see more", "subscribe", "follow", "following", "message", "messages",
        "notifications", "notification",
    )

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

        // 发布/更新/编辑时间不是截止时间，避免把文章元信息当成 DDL。
        if (META_LINE.containsMatchIn(line)) return null

        val deadline = resolveDeadline(dateHit, timeHit, now) ?: return null
        val hasKeyword = DEADLINE_KEYWORD.containsMatchIn(line)
        val start = listOfNotNull(dateHit?.start, timeHit?.start).min()
        val end = listOfNotNull(dateHit?.end, timeHit?.end).max()

        // 只有日期/时间、没有任务名的行更像文章时间戳，排序时让有标题的行优先。
        val hasTitleText = looksLikeTitle(line.substring(0, start)) ||
            looksLikeTitle(line.substring(end))
        val bare = !hasKeyword && !hasTitleText
        val confidence = when {
            hasKeyword && dateHit != null && timeHit != null -> 0.9f
            hasKeyword && dateHit != null -> 0.8f
            dateHit?.relative == true && timeHit != null -> if (bare) 0.6f else 0.75f
            dateHit != null && timeHit != null -> if (bare) 0.5f else 0.65f
            dateHit != null -> if (bare) 0.45f else 0.6f
            timeHit != null -> if (bare) 0.4f else 0.5f
            else -> return null
        }

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

    /**
     * 从同一页的多行文字里挑标题：优先同一行的前后片段，其次邻近行，
     * 最后才回退到整页；所有候选按「像不像一个任务名」打分，而不是见到第一行就用。
     */
    private fun pickTitle(lines: List<String>, index: Int, parsed: LineParse): String {
        val line = lines[index]
        val options = mutableListOf<Pair<String, Int>>()

        fun consider(raw: String, base: Int, rejectBodyStarter: Boolean = false) {
            val title = cleanTitle(raw)
            if (!looksLikeTitle(title)) return
            if (UI_CHROME.any { title.equals(it, ignoreCase = true) }) return
            if (BARE_ACTION.matches(title)) return
            if (rejectBodyStarter && BODY_STARTER.containsMatchIn(title)) return
            options += title to (base + titleScore(title))
        }

        val before = line.substring(0, parsed.matchStart.coerceIn(0, line.length))
        consider(before, base = 60, rejectBodyStarter = true)

        val after = line.substring(parsed.matchEnd.coerceIn(0, line.length))
        consider(stripLeadingClause(after), base = 56, rejectBodyStarter = true)

        // 标题通常在截止时间的上方；下两行则覆盖 OCR 把标题排到日期后面的情况。
        for (distance in 1..4) {
            val i = index - distance
            if (i < 0) break
            consider(lines[i], base = 52 - distance * 4)
        }
        for (distance in 1..2) {
            val i = index + distance
            if (i >= lines.size) break
            consider(lines[i], base = 40 - distance * 4)
        }

        lines.forEachIndexed { i, raw ->
            if (i != index) consider(raw, base = 16 + if (i == 0) 8 else 0)
        }

        return options.maxByOrNull { it.second }?.first ?: ""
    }

    private fun stripLeadingClause(raw: String): String = raw
        .replace(Regex("""^\s*(?:前|之前|以前|before|by)\s*""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""^\s*[，,。.;；:：\-–—]\s*"""), "")

    private fun cleanTitle(raw: String): String {
        var title = raw.trim()
        title = LEADING_BULLET.replace(title, "")
        title = LEADING_VOCATIVE.replace(title, "")
        title = LEADING_TITLE_LABEL.replace(title, "")
        title = LEADING_SUBMIT_LABEL.replace(title, "")
        title = LEADING_KEYWORD.replace(title, "")
        title = TRAILING_STRONG.replace(title, "")
        title = TRAILING_WEAK.replace(title, "")
        title = TRAILING_BODY_CLAUSE.replace(title, "")
        title = cutAtSentence(title)
        title = EDGE_PUNCT.replace(title, "")
        title = WHITESPACE.replace(title, " ").trim()
        if (title.length > MAX_TITLE_CHARS) title = title.take(MAX_TITLE_CHARS).trim()
        return title
    }

    /** 只保留第一个完整短句，避免把整段正文带进标题。 */
    private fun cutAtSentence(raw: String): String {
        val end = SENTENCE_END.find(raw)?.range?.first ?: return raw
        return if (end >= 2) raw.take(end).trim() else raw
    }

    /** 标题质量分：短、干净、像任务名的行得分高；正文片段、导航文字得分低。 */
    private fun titleScore(title: String): Int {
        var score = when {
            title.length <= 24 -> 6
            title.length <= 36 -> 3
            title.length <= 48 -> 0
            else -> -6
        }
        if (SENTENCE_END.containsMatchIn(title)) score -= 12
        if (CLAUSE_PUNCT.containsMatchIn(title)) score -= 6
        val sentenceStart = title.length > 8 &&
            (SENTENCE_STARTER.containsMatchIn(title) || SENTENCE_STARTER_EN.containsMatchIn(title))
        if (sentenceStart) score -= 14
        if (title.length > 10 && INSTRUCTION_HINT.containsMatchIn(title)) score -= 8
        if (SECTION_HEADING.containsMatchIn(title)) score -= 24
        if (BODY_STARTER.containsMatchIn(title)) score -= 24
        if (DEADLINE_TITLE_WORD.containsMatchIn(title)) score -= 10
        if (URL_OR_EMAIL.containsMatchIn(title)) score -= 30
        if (title.length <= 8 && ACTION_FRAGMENT.containsMatchIn(title)) score -= 10
        if (STRONG_TITLE_WORD.containsMatchIn(title)) score += 6
        if (HEADING_HINT.containsMatchIn(title)) score += 3
        return score
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
                sameDeadline(existing.deadline, candidate.deadline) &&
                    sameTitle(existing.title, candidate.title)
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

    private fun sameDeadline(a: ZonedDateTime?, b: ZonedDateTime?): Boolean = when {
        a == null && b == null -> true
        a != null && b != null -> kotlin.math.abs(
            a.toInstant().toEpochMilli() - b.toInstant().toEpochMilli(),
        ) <= MERGE_WINDOW_MILLIS
        else -> false
    }

    /** 同标题或一方没标题才合并；同截止时间的两个不同作业要保留成两条。 */
    private fun sameTitle(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return true
        val left = CaptureDuplicate.normalizeTitle(a)
        val right = CaptureDuplicate.normalizeTitle(b)
        if (left == right) return true
        val shorter = minOf(left.length, right.length)
        return shorter >= 3 && (left.contains(right) || right.contains(left))
    }

    private fun noDeadlineCandidate(lines: List<String>): DdlCandidate {
        val title = lines.mapIndexedNotNull { index, line ->
            val cleaned = cleanTitle(line)
            if (!looksLikeTitle(cleaned)) return@mapIndexedNotNull null
            if (UI_CHROME.any { cleaned.equals(it, ignoreCase = true) }) return@mapIndexedNotNull null
            if (BARE_ACTION.matches(cleaned)) return@mapIndexedNotNull null
            cleaned to (titleScore(cleaned) + if (index == 0) 8 else 0)
        }.maxByOrNull { it.second }?.first ?: ""
        return DdlCandidate(
            title = title,
            deadline = null,
            confidence = 0.2f,
            sourceLine = "",
        )
    }
}
