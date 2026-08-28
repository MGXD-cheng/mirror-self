package com.mgxd.mirror.data.db.dao

import android.content.ContentValues
import android.database.sqlite.SQLiteOpenHelper
import com.mgxd.mirror.data.db.entity.FactMemoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 细节记忆 DAO（手写 SQL）。
 */
class FactMemoryDao(private val helper: SQLiteOpenHelper) {

    suspend fun insert(fact: FactMemoryEntity): Long = withContext(Dispatchers.IO) {
        helper.writableDatabase.insert("fact_memory", null, fact.toValues())
    }

    /** 去重插入：内容相同（模糊：移除空白后一致）则更新重要度/时间，返回 true 表示新增 */
    suspend fun upsertDedupe(fact: FactMemoryEntity): Boolean = withContext(Dispatchers.IO) {
        val key = fact.content.trim()
        if (key.isEmpty()) return@withContext false
        val db = helper.writableDatabase
        db.query(
            "fact_memory", arrayOf("id", "importance"),
            "status != 'expired'", null, null, null, "createdAt DESC", "20"
        ).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val oldImp = c.getInt(1)
                // 内容相似度：包含或长度接近（用于"去重"）
                val existing = getContentById(id)
                if (existing != null && isSimilar(existing, key)) {
                    val values = ContentValues().apply {
                        put("importance", maxOf(oldImp, fact.importance))
                        put("lastUsedAt", fact.lastUsedAt)
                        put("status", "active")
                        put("eventDate", if (fact.eventDate.isNotBlank()) fact.eventDate else "")
                    }
                    db.update("fact_memory", values, "id = ?", arrayOf(id.toString()))
                    return@withContext false
                }
            }
        }
        db.insert("fact_memory", null, fact.toValues())
        true
    }

    private fun getContentById(id: Long): String? = helper.readableDatabase.query(
        "fact_memory", arrayOf("content"), "id = ?", arrayOf(id.toString()), null, null, null
    ).use { c -> if (c.moveToFirst()) c.getString(0) else null }

    /** 内容相似：完全相同，或一方包含另一方且长度差 < 4 */
    private fun isSimilar(a: String, b: String): Boolean {
        if (a == b) return true
        val (short, long) = if (a.length <= b.length) a to b else b to a
        return long.contains(short) && short.length >= 3 && long.length - short.length < 8
    }

    /** 有效记忆（可主动引用） */
    suspend fun getActive(limit: Int = 50): List<FactMemoryEntity> = withContext(Dispatchers.IO) {
        query("status = 'active'", "importance DESC, lastUsedAt ASC", limit)
    }

    /** 全部记忆（含归档） */
    suspend fun getAll(limit: Int = 200): List<FactMemoryEntity> = withContext(Dispatchers.IO) {
        query(null, "createdAt DESC", limit)
    }

    /** 带日期且未完成的有效事件记忆（用于主动关心） */
    suspend fun getUpcomingEvents(limit: Int = 20): List<FactMemoryEntity> = withContext(Dispatchers.IO) {
        query("status = 'active' AND eventDate != '' AND isCompleted = 0", "eventDate ASC", limit)
    }

    /** 随机取一条高重要度、近期未引用的记忆（用于主动话题） */
    suspend fun pickForTopic(): FactMemoryEntity? = withContext(Dispatchers.IO) {
        query("status = 'active' AND importance >= 3", "lastUsedAt ASC", 10).randomOrNull()
    }

    suspend fun markUsed(id: Long, ts: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put("lastUsedAt", ts) }
        helper.writableDatabase.update("fact_memory", values, "id = ?", arrayOf(id.toString()))
    }

    suspend fun markCompleted(id: Long) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("isCompleted", 1)
            put("status", "expired")
        }
        helper.writableDatabase.update("fact_memory", values, "id = ?", arrayOf(id.toString()))
    }

    /** 过期清理：事件日期已过的日期型记忆 → expired */
    suspend fun expirePastEvents(today: String) = withContext(Dispatchers.IO) {
        helper.writableDatabase.execSQL(
            "UPDATE fact_memory SET status='expired', isCompleted=1 WHERE status='active' AND eventDate != '' AND eventDate < ?",
            arrayOf(today)
        )
    }

    /** 归档：重要度低且长期未用 */
    suspend fun archiveStale(now: Long, thresholdMs: Long) = withContext(Dispatchers.IO) {
        helper.writableDatabase.execSQL(
            "UPDATE fact_memory SET status='archived' WHERE status='active' AND importance < 3 AND lastUsedAt > 0 AND (? - lastUsedAt) > ?",
            arrayOf(now.toString(), thresholdMs.toString())
        )
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete("fact_memory", "id = ?", arrayOf(id.toString()))
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete("fact_memory", null, null)
    }

    suspend fun count(): Int = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM fact_memory", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    private fun query(selection: String?, order: String, limit: Int): List<FactMemoryEntity> {
        val list = mutableListOf<FactMemoryEntity>()
        helper.readableDatabase.query(
            "fact_memory", null, selection, null, null, null, order, limit.toString()
        ).use { c ->
            while (c.moveToNext()) list.add(c.toFact())
        }
        return list
    }

    private fun FactMemoryEntity.toValues(): ContentValues {
        return ContentValues().apply {
            put("type", type)
            put("content", content)
            put("sourceMessageId", sourceMessageId)
            put("createdAt", createdAt)
            put("lastUsedAt", lastUsedAt)
            put("importance", importance)
            put("status", status)
            put("eventDate", eventDate)
            put("isCompleted", if (isCompleted) 1 else 0)
        }
    }

    private fun android.database.Cursor.toFact(): FactMemoryEntity {
        return FactMemoryEntity(
            id = getLong(getColumnIndexOrThrow("id")),
            type = getStringOr("type", "其他"),
            content = getStringOr("content"),
            sourceMessageId = getLong(getColumnIndexOrThrow("sourceMessageId")),
            createdAt = getLong(getColumnIndexOrThrow("createdAt")),
            lastUsedAt = getLong(getColumnIndexOrThrow("lastUsedAt")),
            importance = getInt(getColumnIndexOrThrow("importance")),
            status = getStringOr("status", "active"),
            eventDate = getStringOr("eventDate"),
            isCompleted = getInt(getColumnIndexOrThrow("isCompleted")) == 1
        )
    }

    private fun android.database.Cursor.getStringOr(column: String, default: String = ""): String {
        val idx = getColumnIndex(column)
        return if (idx >= 0 && !isNull(idx)) getString(idx) else default
    }
}