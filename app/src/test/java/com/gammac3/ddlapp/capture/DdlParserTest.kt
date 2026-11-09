package com.gammac3.ddlapp.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class DdlParserTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val now: ZonedDateTime = ZonedDateTime.of(2026, 11, 8, 10, 0, 0, 0, zone)

    private fun parse(text: String): List<DdlCandidate> = DdlParser.parse(text, now)
    private fun first(text: String): DdlCandidate = parse(text).first()
    private fun deadline(text: String): ZonedDateTime? = first(text).deadline

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): ZonedDateTime =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone)

    // ------------------------------------------------------------- basics

    @Test
    fun `blank text yields no candidates`() {
        assertTrue(parse("").isEmpty())
        assertTrue(parse("   \n\t\n  ").isEmpty())
    }

    @Test
    fun `chinese homework sample`() {
        val text = "【作业】第三章习题\n截止时间：11月13日 23:59\n请提交到学习通"
        val candidate = first(text)
        assertEquals(at(2026, 11, 13, 23, 59), candidate.deadline)
        assertEquals("【作业】第三章习题", candidate.title)
        assertEquals(0.9f, candidate.confidence, 0.001f)
    }

    @Test
    fun `relative day with afternoon time`() {
        val candidate = first("明天下午3点前交实验报告")
        assertEquals(at(2026, 11, 9, 15, 0), candidate.deadline)
        assertTrue(candidate.title.contains("实验报告"))
    }

    @Test
    fun `this week friday keeps past time`() {
        val candidate = first("本周五 18:00 前提交小组作业")
        assertEquals(at(2026, 11, 6, 18, 0), candidate.deadline)
        assertTrue(candidate.title.contains("小组作业"))
    }

    @Test
    fun `next monday with time`() {
        val candidate = first("下周一 09:00 答辩")
        assertEquals(at(2026, 11, 9, 9, 0), candidate.deadline)
        assertEquals("答辩", candidate.title)
    }

    @Test
    fun `chinese full date with noon`() {
        val candidate = first("2026年12月1日 中午12点 报名截止")
        assertEquals(at(2026, 12, 1, 12, 0), candidate.deadline)
        assertEquals("报名截止", candidate.title)
    }

    @Test
    fun `tonight keeps today anchor`() {
        val candidate = first("今晚 23:59 前完成问卷")
        assertEquals(at(2026, 11, 8, 23, 59), candidate.deadline)
        assertEquals("完成问卷", candidate.title)
    }

    @Test
    fun `days later defaults to end of day`() {
        assertEquals(at(2026, 11, 11, 23, 59), deadline("3天后截止"))
        assertEquals(at(2026, 11, 11, 23, 59), deadline("三天后截止"))
    }

    @Test
    fun `date only defaults to 2359`() {
        assertEquals(at(2026, 11, 13, 23, 59), deadline("截止：11月13日"))
    }

    @Test
    fun `bare weekday resolves to next occurrence`() {
        assertEquals(at(2026, 11, 13, 23, 59), deadline("周五"))
    }

    @Test
    fun `date without title yields empty title`() {
        val candidate = first("11月13日 23:59")
        assertEquals(at(2026, 11, 13, 23, 59), candidate.deadline)
        assertEquals("", candidate.title)
    }

    @Test
    fun `iso date with ddl keyword`() {
        val candidate = first("2026-11-13 23:59 DDL")
        assertEquals(at(2026, 11, 13, 23, 59), candidate.deadline)
        assertEquals(0.9f, candidate.confidence, 0.001f)
    }

    @Test
    fun `half past nine time only rolls to tomorrow`() {
        assertEquals(at(2026, 11, 9, 9, 30), deadline("9点半前交"))
    }

    @Test
    fun `next friday with time`() {
        assertEquals(at(2026, 11, 13, 18, 0), deadline("下周五 18:00"))
    }

    @Test
    fun `month day in january rolls to next year`() {
        assertEquals(at(2027, 1, 5, 23, 59), deadline("1月5日"))
    }

    @Test
    fun `passed month day rolls to next year`() {
        assertEquals(at(2027, 10, 1, 23, 59), deadline("10月1日"))
    }

    @Test
    fun `keyword before date`() {
        assertEquals(at(2026, 11, 13, 23, 59), deadline("11月13日前"))
    }

    @Test
    fun `text without deadline returns null candidate`() {
        val candidates = parse("没有截止时间的通知")
        assertEquals(1, candidates.size)
        assertNull(candidates.first().deadline)
        assertEquals(0.2f, candidates.first().confidence, 0.001f)
        assertEquals("没有截止时间的通知", candidates.first().title)
    }

    // ------------------------------------------------------------- title quality

    @Test
    fun `complex page picks article heading over sentence fragment`() {
        val text = """
            关于做好2026年秋季学期期中教学检查工作的通知
            各学院：
            为做好本学期期中教学检查工作，现将有关事项通知如下。
            一、检查时间
            11月10日至11月14日
            请各学院于11月13日17:00前将检查总结交至教务处。
        """.trimIndent()
        val candidate = parse(text).first { it.deadline == at(2026, 11, 13, 17, 0) }
        assertEquals("关于做好2026年秋季学期期中教学检查工作的通知", candidate.title)
    }

    @Test
    fun `submit deadline label is not used as title`() {
        val text = "期中论文提交要求\n提交截止时间：2026年11月20日 23:59"
        assertEquals("期中论文提交要求", first(text).title)
    }

    @Test
    fun `instruction sentence is not chosen as title`() {
        val text = "第三章习题\n本次作业要求独立完成\n截止时间：11月13日 23:59"
        assertEquals("第三章习题", first(text).title)
    }

    @Test
    fun `title label prefix is removed`() {
        val text = "标题：第三章习题\n截止：11月13日 23:59"
        assertEquals("第三章习题", first(text).title)
    }

    @Test
    fun `vocative prefix is removed from title`() {
        val text = "各位同学，本次作业\n截止：11月13日 23:59"
        assertEquals("本次作业", first(text).title)
    }

    @Test
    fun `chat chrome is not used as title`() {
        val text = "文件传输助手\n今天 14:30"
        assertEquals("", first(text).title)
    }

    @Test
    fun `publish time is not a deadline`() {
        val text = "发布时间：2026-11-01 10:00\n正文\n截止：11月13日 23:59"
        val candidates = parse(text)
        assertTrue(candidates.none { it.deadline == at(2026, 11, 1, 10, 0) })
    }

    @Test
    fun `same deadline with different titles stays separate`() {
        val text = "作业A 11月13日 23:59\n作业B 11月13日 23:59"
        assertEquals(2, parse(text).size)
    }

    @Test
    fun `generic action fragment loses to heading`() {
        val text = "第三章习题\n请于11月13日23:59前提交材料"
        assertEquals("第三章习题", first(text).title)
    }

    @Test
    fun `english heading beats generic submit phrase`() {
        val text = "CS101 - Assignment 3\nPlease submit your essay by Friday 5pm"
        assertEquals("CS101 - Assignment 3", first(text).title)
    }

    @Test
    fun `bare action is not used as title`() {
        val text = "关于XX的通知\n请于11月13日前提交"
        assertEquals("关于XX的通知", first(text).title)
    }

    @Test
    fun `bare timestamp ranks below titled deadline`() {
        val text = "关于XX的通知\n2026-11-01 10:00\n第三章习题 11月13日 23:59"
        assertEquals(at(2026, 11, 13, 23, 59), parse(text).first().deadline)
    }

    // ------------------------------------------------------------- english

    @Test
    fun `english deadline with month name`() {
        val candidate = first("Deadline: Nov 13, 2026 11:59 PM")
        assertEquals(at(2026, 11, 13, 23, 59), candidate.deadline)
    }

    @Test
    fun `english submit by weekday`() {
        val candidate = first("Submit by Friday 5pm")
        assertEquals(at(2026, 11, 13, 17, 0), candidate.deadline)
    }

    @Test
    fun `english due tomorrow`() {
        assertEquals(at(2026, 11, 9, 9, 0), deadline("Due tomorrow at 9:00"))
    }

    @Test
    fun `english assignment title`() {
        val candidate = first("Assignment due November 13 at 23:59")
        assertEquals(at(2026, 11, 13, 23, 59), candidate.deadline)
        assertEquals("Assignment", candidate.title)
    }

    @Test
    fun `english next monday`() {
        val candidate = first("Next Monday 09:00 defense")
        assertEquals(at(2026, 11, 9, 9, 0), candidate.deadline)
        assertEquals("defense", candidate.title)
    }

    @Test
    fun `english iso date`() {
        assertEquals(at(2026, 11, 13, 23, 59), deadline("2026-11-13 23:59"))
    }

    @Test
    fun `english no deadline`() {
        val candidate = first("No deadline here")
        assertNull(candidate.deadline)
        assertEquals(0.2f, candidate.confidence, 0.001f)
    }

    @Test
    fun `english tonight`() {
        assertEquals(at(2026, 11, 8, 23, 59), deadline("tonight 11:59 PM"))
    }

    @Test
    fun `english noon tomorrow`() {
        assertEquals(at(2026, 11, 9, 12, 0), deadline("noon tomorrow"))
    }

    @Test
    fun `english this friday keeps past time`() {
        assertEquals(at(2026, 11, 6, 18, 0), deadline("this Friday 18:00"))
    }

    @Test
    fun `english day month with am`() {
        assertEquals(at(2026, 11, 13, 9, 30), deadline("13 Nov 2026, 9:30 am"))
    }

    @Test
    fun `english month day without year`() {
        assertEquals(at(2026, 11, 13, 23, 59), deadline("Nov 13"))
    }

    // ------------------------------------------------------------- time

    @Test
    fun `chinese periods`() {
        assertEquals(at(2026, 11, 8, 15, 0), deadline("下午3点"))
        assertEquals(at(2026, 11, 8, 21, 0), deadline("晚上9点"))
        assertEquals(at(2026, 11, 9, 1, 0), deadline("凌晨1点"))
        assertEquals(at(2026, 11, 8, 12, 0), deadline("中午12点"))
        assertEquals(at(2026, 11, 9, 9, 30), deadline("上午9点30分"))
    }

    @Test
    fun `time only rolls past to tomorrow`() {
        assertEquals(at(2026, 11, 9, 9, 0), deadline("9:00"))
    }

    // ------------------------------------------------------------- multi

    @Test
    fun `multiple deadlines are capped at three and sorted`() {
        val text = """
            作业A 11月13日 23:59
            作业B 11月14日 12:00
            作业C 11月15日 09:00
            作业D 11月16日 08:00
        """.trimIndent()
        val candidates = parse(text)
        assertEquals(3, candidates.size)
        assertEquals(at(2026, 11, 13, 23, 59), candidates[0].deadline)
        assertEquals(at(2026, 11, 14, 12, 0), candidates[1].deadline)
        assertEquals(at(2026, 11, 15, 9, 0), candidates[2].deadline)
    }

    @Test
    fun `same deadline lines merge`() {
        val text = "作业A 11月13日 23:59\n作业A 11月13日 23:59"
        assertEquals(1, parse(text).size)
    }

    @Test
    fun `phone numbers and ratios are not dates`() {
        assertNull(first("电话 13800138000 比例 16:9 谢谢").deadline)
    }

    @Test
    fun `long text is truncated without crash`() {
        val text = "通知\n" + "x".repeat(20_000) + "\n截止 11月13日 23:59"
        val candidate = first(text)
        assertEquals(at(2026, 11, 13, 23, 59), candidate.deadline)
    }

    @Test
    fun `confidence for keyword date time`() {
        val candidate = first("截止 11月13日 23:59 提交")
        assertEquals(0.9f, candidate.confidence, 0.001f)
    }

    @Test
    fun `confidence for date time without keyword`() {
        val candidate = first("11月13日 23:59 开会")
        assertEquals(0.65f, candidate.confidence, 0.001f)
    }
}
