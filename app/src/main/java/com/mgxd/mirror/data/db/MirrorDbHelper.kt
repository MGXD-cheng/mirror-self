package com.mgxd.mirror.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 手写 SQLite 数据库助手（无 ORM，零注解处理，构建稳定）。
 * 数据全部本地存储，保护用户隐私。
 */
class MirrorDbHelper(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS user_profile (
                id INTEGER PRIMARY KEY,
                identity TEXT NOT NULL DEFAULT '',
                nickname TEXT NOT NULL DEFAULT '',
                wakeTime TEXT NOT NULL DEFAULT '07:00',
                sleepTime TEXT NOT NULL DEFAULT '23:00',
                commuteTime TEXT NOT NULL DEFAULT '08:30',
                workStart TEXT NOT NULL DEFAULT '09:00',
                workEnd TEXT NOT NULL DEFAULT '18:00',
                personality TEXT NOT NULL DEFAULT '',
                interests TEXT NOT NULL DEFAULT '',
                catchphrases TEXT NOT NULL DEFAULT '',
                toneWords TEXT NOT NULL DEFAULT '',
                emojiStyle TEXT NOT NULL DEFAULT '',
                decisionStyle TEXT NOT NULL DEFAULT '',
                emotionPattern TEXT NOT NULL DEFAULT '',
                hardConstraints TEXT NOT NULL DEFAULT '[]',
                languageStyle TEXT NOT NULL DEFAULT '',
                psychologyProfile TEXT NOT NULL DEFAULT '{}',
                activeLevel INTEGER NOT NULL DEFAULT 2,
                askEnabled INTEGER NOT NULL DEFAULT 1,
                learningProgress INTEGER NOT NULL DEFAULT 0,
                onboardingDone INTEGER NOT NULL DEFAULT 0,
                updatedAt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS schedule_event (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date TEXT NOT NULL,
                weekday INTEGER NOT NULL DEFAULT 1,
                title TEXT NOT NULL DEFAULT '',
                type TEXT NOT NULL DEFAULT 'OTHER',
                baseTime TEXT NOT NULL DEFAULT '',
                actualTime TEXT NOT NULL DEFAULT '',
                windowStart TEXT NOT NULL DEFAULT '',
                windowEnd TEXT NOT NULL DEFAULT '',
                isTriggered INTEGER NOT NULL DEFAULT 0,
                isManual INTEGER NOT NULL DEFAULT 0,
                note TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS chat_message (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sender TEXT NOT NULL DEFAULT 'user',
                content TEXT NOT NULL DEFAULT '',
                timestamp INTEGER NOT NULL DEFAULT 0,
                isSensitive INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS api_config (
                id INTEGER PRIMARY KEY,
                baseUrl TEXT NOT NULL DEFAULT '',
                apiKeyEncrypted TEXT NOT NULL DEFAULT '',
                model TEXT NOT NULL DEFAULT 'deepseek-chat',
                maxTokens INTEGER NOT NULL DEFAULT 1024,
                temperature REAL NOT NULL DEFAULT 0.8,
                enableFallback INTEGER NOT NULL DEFAULT 1,
                timeoutSeconds INTEGER NOT NULL DEFAULT 15
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS entity_ask (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                entityKey TEXT NOT NULL DEFAULT '',
                entityType TEXT NOT NULL DEFAULT '',
                askedAt INTEGER NOT NULL DEFAULT 0,
                status TEXT NOT NULL DEFAULT 'asked'
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS fact_memory (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                type TEXT NOT NULL DEFAULT '其他',
                content TEXT NOT NULL DEFAULT '',
                sourceMessageId INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL DEFAULT 0,
                lastUsedAt INTEGER NOT NULL DEFAULT 0,
                importance INTEGER NOT NULL DEFAULT 1,
                status TEXT NOT NULL DEFAULT 'active',
                eventDate TEXT NOT NULL DEFAULT '',
                isCompleted INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        // 索引：加速按日期查询日程
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_schedule_date ON schedule_event(date)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_chat_time ON chat_message(timestamp)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_fact_status ON fact_memory(status)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 逐版本迁移：只加列/加表、不删表，保留用户已有画像与聊天记录
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE user_profile ADD COLUMN languageStyle TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE user_profile ADD COLUMN psychologyProfile TEXT NOT NULL DEFAULT '{}'")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE user_profile ADD COLUMN activeLevel INTEGER NOT NULL DEFAULT 2")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS fact_memory (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "type TEXT NOT NULL DEFAULT '其他'," +
                        "content TEXT NOT NULL DEFAULT ''," +
                        "sourceMessageId INTEGER NOT NULL DEFAULT 0," +
                        "createdAt INTEGER NOT NULL DEFAULT 0," +
                        "lastUsedAt INTEGER NOT NULL DEFAULT 0," +
                        "importance INTEGER NOT NULL DEFAULT 1," +
                        "status TEXT NOT NULL DEFAULT 'active'," +
                        "eventDate TEXT NOT NULL DEFAULT ''," +
                        "isCompleted INTEGER NOT NULL DEFAULT 0)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_fact_status ON fact_memory(status)")
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE user_profile ADD COLUMN askEnabled INTEGER NOT NULL DEFAULT 1")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS entity_ask (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "entityKey TEXT NOT NULL DEFAULT ''," +
                        "entityType TEXT NOT NULL DEFAULT ''," +
                        "askedAt INTEGER NOT NULL DEFAULT 0," +
                        "status TEXT NOT NULL DEFAULT 'asked')"
            )
        }
    }

    companion object {
        const val DB_NAME = "mirror.db"
        const val DB_VERSION = 4
    }
}