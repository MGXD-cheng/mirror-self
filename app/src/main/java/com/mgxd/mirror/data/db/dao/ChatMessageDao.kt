package com.mgxd.mirror.data.db.dao

import android.content.ContentValues
import android.database.sqlite.SQLiteOpenHelper
import com.mgxd.mirror.data.db.entity.ChatMessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 聊天消息 DAO（手写 SQL）。
 */
class ChatMessageDao(private val helper: SQLiteOpenHelper) {

    suspend fun insert(message: ChatMessageEntity): Long = withContext(Dispatchers.IO) {
        helper.writableDatabase.insert("chat_message", null, message.toValues())
    }

    suspend fun getRecent(limit: Int): List<ChatMessageEntity> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ChatMessageEntity>()
        helper.readableDatabase.query(
            "chat_message", null, null, null, null, null,
            "timestamp DESC", limit.toString()
        ).use { c ->
            while (c.moveToNext()) list.add(c.toMessage())
        }
        list
    }

    suspend fun getAll(): List<ChatMessageEntity> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ChatMessageEntity>()
        helper.readableDatabase.query(
            "chat_message", null, null, null, null, null, "timestamp ASC"
        ).use { c ->
            while (c.moveToNext()) list.add(c.toMessage())
        }
        list
    }

    suspend fun trimOldMessages() = withContext(Dispatchers.IO) {
        // 只保留最近 500 条
        helper.writableDatabase.execSQL(
            "DELETE FROM chat_message WHERE id NOT IN " +
                    "(SELECT id FROM chat_message ORDER BY id DESC LIMIT 500)"
        )
    }

    suspend fun count(): Int = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM chat_message", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    private fun ChatMessageEntity.toValues(): ContentValues {
        return ContentValues().apply {
            put("sender", sender)
            put("content", content)
            put("timestamp", timestamp)
            put("isSensitive", if (isSensitive) 1 else 0)
        }
    }

    private fun android.database.Cursor.toMessage(): ChatMessageEntity {
        return ChatMessageEntity(
            id = getLong(getColumnIndexOrThrow("id")),
            sender = getStringOr("sender"),
            content = getStringOr("content"),
            timestamp = getLong(getColumnIndexOrThrow("timestamp")),
            isSensitive = getInt(getColumnIndexOrThrow("isSensitive")) == 1
        )
    }

    private fun android.database.Cursor.getStringOr(column: String, default: String = ""): String {
        val idx = getColumnIndex(column)
        return if (idx >= 0 && !isNull(idx)) getString(idx) else default
    }
}