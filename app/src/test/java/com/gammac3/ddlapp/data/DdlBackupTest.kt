package com.gammac3.ddlapp.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** DdlBackup 编解码与导入落库策略：往返／拒绝／软去重／覆盖语义全在 JVM Fake 上跑。 */
class DdlBackupTest {

    private class FakeDdlDao : DdlDao {
        private val store = LinkedHashMap<Long, DdlEntity>()
        private val flow = MutableStateFlow<List<DdlEntity>>(emptyList())
        private fun refresh() { flow.value = store.values.toList() }
        override fun observeAll(): Flow<List<DdlEntity>> = flow.map { it }
        override suspend fun insert(entity: DdlEntity): Long {
            val id = if (entity.id != 0L) entity.id else (store.keys.maxOrNull() ?: 0L) + 1
            store[id] = entity.copy(id = id); refresh(); return id
        }
        override suspend fun update(entity: DdlEntity): Int {
            if (!store.containsKey(entity.id)) return 0
            store[entity.id] = entity; refresh(); return 1
        }
        override suspend fun deleteById(id: Long) { store.remove(id); refresh() }
        override suspend fun byId(id: Long): DdlEntity? = store[id]
        override suspend fun allActive(): List<DdlEntity> = store.values.filter { !it.isDone }
        override suspend fun all(): List<DdlEntity> = store.values.toList()
    }

    private val now = 200_000_000_000L
    private fun e(
        id: Long = 0,
        title: String = "试",
        note: String = "",
        deadline: Long = now,
        leads: List<Long> = listOf(5L),
        done: Boolean = false,
        muted: Boolean = false,
        createdAt: Long = now,
    ) = DdlEntity(id, title, note, deadline, leads, done, createdAt, muted)

    // ---- 编解码 ----

    @Test
    fun `往返保留全部字段与顺序`() {
        val src = listOf(
            e(title = "甲", note = "带\"引号\"与\n换行与\\斜杠与中英混排", deadline = now + 1, leads = emptyList(), done = true, muted = true, createdAt = now + 7),
            e(title = "乙", note = "", deadline = now + 2, leads = listOf(1440L, 5L, 0L)),
        )
        assertEquals(src, DdlBackup.fromJson(DdlBackup.toJson(src, 111L)))
    }

    @Test
    fun `空清单往返为空`() {
        assertEquals(emptyList<DdlEntity>(), DdlBackup.fromJson(DdlBackup.toJson(emptyList(), 1L)))
    }

    @Test
    fun `导出带信封与版本且不写id`() {
        val json = DdlBackup.toJson(listOf(e(title = "甲")), 111L)
        val root = JSONObject(json)
        assertEquals("com.gammac3.ddlapp", root.getString("app"))
        assertEquals(1, root.getInt("schema"))
        assertEquals(111L, root.getLong("exportedAt"))
        assertEquals("甲", root.getJSONArray("items").getJSONObject(0).getString("title"))
        assertEquals(false, root.getJSONArray("items").getJSONObject(0).has("id"))
    }

    @Test
    fun `非JSON文本拒绝`() {
        val thrown = runCatching { DdlBackup.fromJson("随便一段话") }.exceptionOrNull()
        assertEquals("invalid-json", thrown?.message)
    }

    @Test
    fun `信封App头不对拒绝`() {
        val thrown = runCatching {
            DdlBackup.fromJson("""{"app":"other.app","schema":1,"items":[]}""")
        }.exceptionOrNull()
        assertEquals("wrong-app", thrown?.message)
    }

    @Test
    fun `缺Schema拒绝`() {
        val thrown = runCatching {
            DdlBackup.fromJson("""{"app":"com.gammac3.ddlapp","items":[]}""")
        }.exceptionOrNull()
        assertEquals("bad-schema", thrown?.message)
    }

    @Test
    fun `更新版本文件拒绝`() {
        val thrown = runCatching {
            DdlBackup.fromJson("""{"app":"com.gammac3.ddlapp","schema":99,"items":[]}""")
        }.exceptionOrNull()
        assertEquals("schema-too-new", thrown?.message)
    }

    @Test
    fun `缺items拒绝`() {
        val thrown = runCatching {
            DdlBackup.fromJson("""{"app":"com.gammac3.ddlapp","schema":1}""")
        }.exceptionOrNull()
        assertEquals("no-items", thrown?.message)
    }

    @Test
    fun `条目缺必填字段或数字型非法拒绝`() {
        val base = """{"app":"com.gammac3.ddlapp","schema":1,"items":[%s]}"""
        // 缺 title
        assertEquals("invalid-item-0", runCatching {
            DdlBackup.fromJson(base.format("""{"deadline":1,"createdAt":1}"""))
        }.exceptionOrNull()?.message)
        // 缺 deadline
        assertEquals("invalid-item-0", runCatching {
            DdlBackup.fromJson(base.format("""{"title":"甲","createdAt":1}"""))
        }.exceptionOrNull()?.message)
        // 缺 createdAt
        assertEquals("invalid-item-0", runCatching {
            DdlBackup.fromJson(base.format("""{"title":"甲","deadline":1}"""))
        }.exceptionOrNull()?.message)
        // leads 里有非数字
        assertEquals("invalid-item-0", runCatching {
            DdlBackup.fromJson(base.format("""{"title":"甲","deadline":1,"createdAt":1,"leads":["x"]}"""))
        }.exceptionOrNull()?.message)
        // 第二个条目问题报第二个下标
        assertEquals("invalid-item-1", runCatching {
            DdlBackup.fromJson("""{"app":"com.gammac3.ddlapp","schema":1,"items":[{"title":"甲","deadline":1,"createdAt":1},{"title":"乙"}]}""")
        }.exceptionOrNull()?.message)
    }

    @Test
    fun `可选字段缺省按无提醒未完成静音关`() {
        val back = DdlBackup.fromJson(
            """{"app":"com.gammac3.ddlapp","schema":1,"items":[{"title":"甲","deadline":5,"createdAt":6,"note":""}]}""",
        )
        assertEquals(listOf(DdlEntity(0, "甲", "", 5, emptyList(), false, 6, false)), back)
    }

    // ---- 追加（软去重） ----

    @Test
    fun `追加跳过库里同题同限并新增其余`() = runBlocking {
        val dao = FakeDdlDao()
        dao.insert(e(id = 1, title = "概率论", deadline = now + 100))
        val r = DdlBackup.importAppend(
            dao,
            listOf(e(title = "概率论", deadline = now + 100, leads = listOf(9L)), e(title = "高数", deadline = now + 200)),
        )
        assertEquals(1, r.affected)
        assertEquals(1, r.skipped)
        assertEquals(listOf("概率论", "高数"), dao.all().map { it.title })
    }

    @Test
    fun `追加时文件内重复也跳过`() = runBlocking {
        val dao = FakeDdlDao()
        val r = DdlBackup.importAppend(dao, listOf(e(title = "甲", deadline = 1), e(title = "甲", deadline = 1), e(title = "甲", deadline = 2)))
        assertEquals(2, r.affected)
        assertEquals(1, r.skipped)
    }

    @Test
    fun `追加标题先去空白再比对`() = runBlocking {
        val dao = FakeDdlDao()
        dao.insert(e(id = 1, title = "概率论", deadline = 100))
        val r = DdlBackup.importAppend(dao, listOf(e(title = " 概率论 ", deadline = 100)))
        assertEquals(0, r.affected)
        assertEquals(1, r.skipped)
    }

    // ---- 覆盖（先插新再清旧，中途炸不零收） ----

    @Test
    fun `覆盖写入全部并清掉原有`() = runBlocking {
        val dao = FakeDdlDao()
        dao.insert(e(id = 1, title = "旧一", deadline = now + 3))
        dao.insert(e(id = 2, title = "旧二", deadline = now + 4))
        val parsed = listOf(e(title = "新一", deadline = now + 1), e(title = "新二", deadline = now + 2, done = true, muted = true))
        val r = DdlBackup.importReplace(dao, parsed)
        assertEquals(2, r.affected)
        assertEquals(0, r.skipped)
        assertEquals(2, r.discarded)
        assertEquals(setOf("新一", "新二"), dao.all().map { it.title }.toSet())
        assertEquals(true, dao.all().any { it.title == "新二" && it.isDone && it.muted })
    }

    @Test
    fun `覆盖到空库等于纯导入`() = runBlocking {
        val dao = FakeDdlDao()
        val r = DdlBackup.importReplace(dao, listOf(e(title = "甲", deadline = 1), e(title = "乙", deadline = 2)))
        assertEquals(2, r.affected)
        assertEquals(0, r.discarded)
    }
}
