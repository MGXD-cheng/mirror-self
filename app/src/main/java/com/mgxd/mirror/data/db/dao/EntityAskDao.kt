package com.mgxd.mirror.data.db.dao

import android.content.ContentValues
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 实体追问状态 DAO：记录"哪个实体被追问过/用户是否拒绝回答"。
 * 用于避免重复追问同一实体（24h 内不再追问；用户拒绝后不再追问）。
 */
class EntityAskDao(private val helper: SQLiteOpenHelper) {

    /** 记录一次追问 */
    suspend fun markAsked(entityKey: String, entityType: String, ts: Long = System.currentTimeMillis()) =
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("entityKey", entityKey)
                put("entityType", entityType)
                put("askedAt", ts)
                put("status", "asked")
            }
            helper.writableDatabase.insertWithOnConflict(
                "entity_ask", null, values,
                android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
            )
        }

    /** 标记用户不愿回答（之后不再追问） */
    suspend fun markDeclined(entityKey: String, entityType: String) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("entityKey", entityKey)
            put("entityType", entityType)
            put("askedAt", System.currentTimeMillis())
            put("status", "declined")
        }
        helper.writableDatabase.insertWithOnConflict(
            "entity_ask", null, values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    /** 是否有"等待回答"的追问（24h 内发出的） */
    suspend fun hasPendingAsk(now: Long = System.currentTimeMillis()): Boolean = withContext(Dispatchers.IO) {
        val cutoff = now - 24 * 3600_000L
        helper.readableDatabase.query(
            "entity_ask", arrayOf("id"),
            "status = 'asked' AND askedAt >= ?", arrayOf(cutoff.toString()),
            null, null, "askedAt DESC", "1"
        ).use { it.moveToFirst() }
    }

    /** 最近一次被追问的实体（用于把用户回答对应到实体） */
    suspend fun lastAskedEntity(now: Long = System.currentTimeMillis()): Pair<String, String>? =
        withContext(Dispatchers.IO) {
            val cutoff = now - 24 * 3600_000L
            helper.readableDatabase.query(
                "entity_ask", arrayOf("entityKey", "entityType"),
                "status = 'asked' AND askedAt >= ?", arrayOf(cutoff.toString()),
                null, null, "askedAt DESC", "1"
            ).use { c ->
                if (c.moveToFirst()) c.getString(0) to c.getString(1) else null
            }
        }

    /** 是否已追问过该实体（24h 内，含已拒绝） */
    suspend fun wasAskedRecently(entityKey: String, now: Long = System.currentTimeMillis()): Boolean =
        withContext(Dispatchers.IO) {
            val cutoff = now - 24 * 3600_000L
            helper.readableDatabase.query(
                "entity_ask", arrayOf("id"),
                "entityKey = ? AND askedAt >= ?", arrayOf(entityKey, cutoff.toString()),
                null, null, null, "1"
            ).use { it.moveToFirst() }
        }

    /** 用户是否拒绝过该实体 */
    suspend fun wasDeclined(entityKey: String): Boolean = withContext(Dispatchers.IO) {
        helper.readableDatabase.query(
            "entity_ask", arrayOf("id"),
            "entityKey = ? AND status = 'declined'", arrayOf(entityKey),
            null, null, null, "1"
        ).use { it.moveToFirst() }
    }

    /** 答案已记录后删除追问标记（不再追问） */
    suspend fun markAnswered(entityKey: String) = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete("entity_ask", "entityKey = ?", arrayOf(entityKey))
    }
}