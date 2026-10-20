package com.gammac3.ddlapp.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 备份编解码 + 导入落库策略（纯 JVM，org.json，零 Android 依赖）。
 *
 * JSON v1 信封：`{"app":"com.gammac3.ddlapp","schema":1,"exportedAt":…,"items":[…]}`。
 * 条目字段 title/note/deadline/leads/isDone/createdAt/muted；**不写 id**——id 属于本机库，
 * 导入一律由库重新分配（撤销墓碑等以原 id 还原的路径不受影响）。
 * 导入原则：先整体校验再落库，任何一条不合法整个文件拒绝（invalid-item-下标）。
 */
object DdlBackup {

    const val APP_ID = "com.gammac3.ddlapp"
    const val SCHEMA = 1

    /** 导入结果：affected=落库条数；skipped=软去重跳过（追加）；discarded=被清掉的原有行数（覆盖）。 */
    data class ImportResult(val affected: Int, val skipped: Int, val discarded: Int)

    fun toJson(items: List<DdlEntity>, exportedAtMillis: Long): String {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject()
                    .put("title", item.title)
                    .put("note", item.note)
                    .put("deadline", item.deadlineEpochMillis)
                    .put("leads", JSONArray(item.leadsMinutes))
                    .put("isDone", item.isDone)
                    .put("createdAt", item.createdAt)
                    .put("muted", item.muted),
            )
        }
        return JSONObject()
            .put("app", APP_ID)
            .put("schema", SCHEMA)
            .put("exportedAt", exportedAtMillis)
            .put("items", arr)
            .toString(2)
    }

    fun fromJson(text: String): List<DdlEntity> {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("invalid-json", e)
        }
        if (root.optString("app", "") != APP_ID) throw IllegalArgumentException("wrong-app")
        val schema = root.optInt("schema", Int.MIN_VALUE)
        if (schema == Int.MIN_VALUE) throw IllegalArgumentException("bad-schema")
        if (schema > SCHEMA) throw IllegalArgumentException("schema-too-new")
        if (schema != SCHEMA) throw IllegalArgumentException("bad-schema")
        val items = root.optJSONArray("items") ?: throw IllegalArgumentException("no-items")
        return (0 until items.length()).map { i ->
            val o = items.optJSONObject(i) ?: throw IllegalArgumentException("invalid-item-$i")
            DdlEntity(
                id = 0L,
                title = o.requireString(i, "title"),
                note = o.optString("note", ""),
                deadlineEpochMillis = o.requireLong(i, "deadline"),
                leadsMinutes = o.leads(i),
                isDone = o.optBoolean("isDone", false),
                createdAt = o.requireLong(i, "createdAt"),
                muted = o.optBoolean("muted", false),
            )
        }
    }

    private fun JSONObject.requireString(index: Int, key: String): String {
        if (!has(key) || isNull(key)) throw IllegalArgumentException("invalid-item-$index")
        return getString(key)
    }

    private fun JSONObject.requireLong(index: Int, key: String): Long {
        if (!has(key) || isNull(key)) throw IllegalArgumentException("invalid-item-$index")
        return try {
            getLong(key)
        } catch (e: Exception) {
            throw IllegalArgumentException("invalid-item-$index", e)
        }
    }

    private fun JSONObject.leads(index: Int): List<Long> {
        val arr = optJSONArray("leads") ?: return emptyList()
        return (0 until arr.length()).map { j ->
            try {
                arr.getLong(j)
            } catch (e: Exception) {
                throw IllegalArgumentException("invalid-item-$index", e)
            }
        }
    }

    /** 追加导入：软去重（标题去空白后＋截止毫秒完全相同即跳过），库内与文件内重复都算。 */
    suspend fun importAppend(dao: DdlDao, parsed: List<DdlEntity>): ImportResult {
        val seen = HashSet<Pair<String, Long>>()
        dao.all().forEach { seen.add(it.title.trim() to it.deadlineEpochMillis) }
        var affected = 0
        var skipped = 0
        parsed.forEach { source ->
            val key = source.title.trim() to source.deadlineEpochMillis
            if (!seen.add(key)) {
                skipped++
                return@forEach
            }
            dao.insert(source.copy(id = 0L))
            affected++
        }
        return ImportResult(affected, skipped, 0)
    }

    /** 覆盖导入：先按新 id 插入全部，再清旧行——中途失败最多「两份并存」，绝不只剩空库。 */
    suspend fun importReplace(dao: DdlDao, parsed: List<DdlEntity>): ImportResult {
        val old = dao.all()
        parsed.forEach { item -> dao.insert(item.copy(id = 0L)) }
        old.forEach { item -> dao.deleteById(item.id) }
        return ImportResult(parsed.size, 0, old.size)
    }
}
