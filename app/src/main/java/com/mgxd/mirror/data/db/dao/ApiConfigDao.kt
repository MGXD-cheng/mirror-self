package com.mgxd.mirror.data.db.dao

import android.content.ContentValues
import android.database.sqlite.SQLiteOpenHelper
import com.mgxd.mirror.data.db.entity.ApiConfigEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AI 服务配置 DAO（手写 SQL）。
 */
class ApiConfigDao(private val helper: SQLiteOpenHelper) {

    suspend fun getConfig(): ApiConfigEntity? = withContext(Dispatchers.IO) {
        helper.readableDatabase.query(
            "api_config", null, "id = 1", null, null, null, null, "1"
        ).use { c ->
            if (c.moveToFirst()) {
                ApiConfigEntity(
                    id = 1,
                    baseUrl = getStringOr(c, "baseUrl", "https://api.deepseek.com/v1/chat/completions"),
                    apiKeyEncrypted = getStringOr(c, "apiKeyEncrypted"),
                    model = getStringOr(c, "model", "deepseek-chat"),
                    maxTokens = c.getInt(c.getColumnIndexOrThrow("maxTokens")),
                    temperature = c.getFloat(c.getColumnIndexOrThrow("temperature")),
                    enableFallback = c.getInt(c.getColumnIndexOrThrow("enableFallback")) == 1,
                    timeoutSeconds = c.getInt(c.getColumnIndexOrThrow("timeoutSeconds"))
                )
            } else {
                null
            }
        }
    }

    suspend fun upsert(config: ApiConfigEntity) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("id", config.id)
            put("baseUrl", config.baseUrl)
            put("apiKeyEncrypted", config.apiKeyEncrypted)
            put("model", config.model)
            put("maxTokens", config.maxTokens)
            put("temperature", config.temperature)
            put("enableFallback", if (config.enableFallback) 1 else 0)
            put("timeoutSeconds", config.timeoutSeconds)
        }
        helper.writableDatabase.insertWithOnConflict(
            "api_config", null, values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    private fun getStringOr(c: android.database.Cursor, column: String, default: String = ""): String {
        val idx = c.getColumnIndex(column)
        return if (idx >= 0 && !c.isNull(idx)) c.getString(idx) else default
    }
}