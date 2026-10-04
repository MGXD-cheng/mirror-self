package com.mgxd.mirror.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

/**
 * 手写 SQLite 数据库助手（无 ORM，零注解处理，构建稳定）。
 * 数据全部本地存储，保护用户隐私。
 *
 * 迁移设计（三重保险）：
 * 1. `migrations` 注册表：每代结构变更是一段独立函数，升级时逐代接力执行。
 *    新增结构版本只需写一个 migrateToN() 并在 map 里加一行，onUpgrade 本身永远不用改。
 * 2. ensureColumns() 列级自愈：迁移结束后校验关键列，历史上漏迁移的列自动补回。
 * 3. onDowngrade() 放行：结构只增不减，覆盖安装旧包时保留数据而不是抛异常。
 *
 * 建表语句只写在 TABLE_CREATE 一处，onCreate 与迁移共用，杜绝"两份结构描述漂移"。
 */
class MirrorDbHelper(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    /**
     * 迁移注册表：key 为目标版本号。
     * 从 oldVersion 升级时会依次执行 (oldVersion+1)..newVersion 的每一段。
     */
    private val migrations: Map<Int, (SQLiteDatabase) -> Unit> = mapOf(
        2 to ::migrateTo2,
        3 to ::migrateTo3,
        4 to ::migrateTo4
    )

    override fun onCreate(db: SQLiteDatabase) {
        // 全新安装：直接给终极结构
        TABLE_CREATE.values.forEach { db.execSQL(it) }
        INDEX_STATEMENTS.forEach { db.execSQL(it) }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 逐版本接力：如 2 → 4 会依次跑 migrateTo3、migrateTo4
        // 只加列/加表、不删表，保留用户已有画像与聊天记录
        ((oldVersion + 1)..newVersion).forEach { v ->
            migrations[v]?.let { migrate ->
                Log.d(TAG, "数据库迁移 → v$v")
                migrate(db)
            }
        }
        ensureColumns(db)
    }

    /**
     * 降级（覆盖安装旧版本包）：本项目结构只增不减，旧代码会忽略多出来的列，数据保留即可。
     * 不重写此方法时 SQLiteOpenHelper 默认抛异常，用户会直接崩在启动页且无法自愈。
     */
    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.w(TAG, "数据库降级 v$oldVersion → v$newVersion，保留数据、不做结构变更")
    }

    // ---------------- 各代迁移（升级到第 N 代结构） ----------------

    /** V1 → V2：画像新增语言风格与心理学画像。 */
    private fun migrateTo2(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE user_profile ADD COLUMN languageStyle TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE user_profile ADD COLUMN psychologyProfile TEXT NOT NULL DEFAULT '{}'")
    }

    /** V2 → V3：画像新增主动社交程度；新建细节记忆表。 */
    private fun migrateTo3(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE user_profile ADD COLUMN activeLevel INTEGER NOT NULL DEFAULT 2")
        db.execSQL(TABLE_CREATE.getValue("fact_memory"))
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_fact_status ON fact_memory(status)")
    }

    /** V3 → V4：画像新增主动追问开关；新建追问状态表。 */
    private fun migrateTo4(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE user_profile ADD COLUMN askEnabled INTEGER NOT NULL DEFAULT 1")
        db.execSQL(TABLE_CREATE.getValue("entity_ask"))
    }

    /**
     * 列级自愈：核对每张表的关键列，缺失则补上。
     * 用于修复"代码加列但忘记写迁移"造成的历史遗漏（老用户会报 no such column）。
     * PRAGMA 查询 + ALTER 均为幂等操作，可安全重复执行。
     */
    private fun ensureColumns(db: SQLiteDatabase) {
        REQUIRED_COLUMNS.forEach { (table, cols) ->
            val existing = mutableSetOf<String>()
            try {
                db.rawQuery("PRAGMA table_info($table)", null).use { c ->
                    val nameIdx = c.getColumnIndex("name")
                    if (nameIdx < 0) return@use
                    while (c.moveToNext()) existing.add(c.getString(nameIdx))
                }
            } catch (e: Exception) {
                Log.w(TAG, "读取表结构失败：$table", e)
                return@forEach
            }
            cols.forEach { (column, ddl) ->
                if (column !in existing) {
                    Log.w(TAG, "补列 $table.$column（历史迁移遗漏）")
                    try {
                        db.execSQL("ALTER TABLE $table ADD COLUMN $column $ddl")
                    } catch (e: Exception) {
                        Log.e(TAG, "补列失败 $table.$column", e)
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "MirrorDb"

        const val DB_NAME = "mirror.db"
        const val DB_VERSION = 4

        /** 全部建表语句（唯一结构描述源，onCreate 与迁移共用）。 */
        private val TABLE_CREATE: Map<String, String> = mapOf(
            "user_profile" to
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
                    """.trimIndent(),
            "schedule_event" to
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
                    """.trimIndent(),
            "chat_message" to
                    """
                    CREATE TABLE IF NOT EXISTS chat_message (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        sender TEXT NOT NULL DEFAULT 'user',
                        content TEXT NOT NULL DEFAULT '',
                        timestamp INTEGER NOT NULL DEFAULT 0,
                        isSensitive INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent(),
            "api_config" to
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
                    """.trimIndent(),
            "fact_memory" to
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
                    """.trimIndent(),
            "entity_ask" to
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

        /** 索引语句。 */
        private val INDEX_STATEMENTS: List<String> = listOf(
            "CREATE INDEX IF NOT EXISTS idx_schedule_date ON schedule_event(date)",
            "CREATE INDEX IF NOT EXISTS idx_chat_time ON chat_message(timestamp)",
            "CREATE INDEX IF NOT EXISTS idx_fact_status ON fact_memory(status)"
        )

        /**
         * 每张表的关键列及补齐用 DDL（不含列名）。
         * 供 [ensureColumns] 自愈历史遗漏列：老用户升级时若缺列，跑一次补齐即不再崩。
         */
        private val REQUIRED_COLUMNS: Map<String, List<Pair<String, String>>> = mapOf(
            "user_profile" to listOf(
                "hardConstraints" to "TEXT NOT NULL DEFAULT '[]'",
                "languageStyle" to "TEXT NOT NULL DEFAULT ''",
                "psychologyProfile" to "TEXT NOT NULL DEFAULT '{}'",
                "activeLevel" to "INTEGER NOT NULL DEFAULT 2",
                "askEnabled" to "INTEGER NOT NULL DEFAULT 1",
                "learningProgress" to "INTEGER NOT NULL DEFAULT 0",
                "onboardingDone" to "INTEGER NOT NULL DEFAULT 0",
                "updatedAt" to "INTEGER NOT NULL DEFAULT 0"
            ),
            "schedule_event" to listOf(
                "type" to "TEXT NOT NULL DEFAULT 'OTHER'",
                "baseTime" to "TEXT NOT NULL DEFAULT ''",
                "actualTime" to "TEXT NOT NULL DEFAULT ''",
                "windowStart" to "TEXT NOT NULL DEFAULT ''",
                "windowEnd" to "TEXT NOT NULL DEFAULT ''",
                "isTriggered" to "INTEGER NOT NULL DEFAULT 0",
                "isManual" to "INTEGER NOT NULL DEFAULT 0",
                "note" to "TEXT NOT NULL DEFAULT ''"
            ),
            "chat_message" to listOf(
                "isSensitive" to "INTEGER NOT NULL DEFAULT 0"
            ),
            "api_config" to listOf(
                "model" to "TEXT NOT NULL DEFAULT 'deepseek-chat'",
                "maxTokens" to "INTEGER NOT NULL DEFAULT 1024",
                "temperature" to "REAL NOT NULL DEFAULT 0.8",
                "enableFallback" to "INTEGER NOT NULL DEFAULT 1",
                "timeoutSeconds" to "INTEGER NOT NULL DEFAULT 15"
            ),
            "fact_memory" to listOf(
                "type" to "TEXT NOT NULL DEFAULT '其他'",
                "content" to "TEXT NOT NULL DEFAULT ''",
                "sourceMessageId" to "INTEGER NOT NULL DEFAULT 0",
                "createdAt" to "INTEGER NOT NULL DEFAULT 0",
                "lastUsedAt" to "INTEGER NOT NULL DEFAULT 0",
                "importance" to "INTEGER NOT NULL DEFAULT 1",
                "status" to "TEXT NOT NULL DEFAULT 'active'",
                "eventDate" to "TEXT NOT NULL DEFAULT ''",
                "isCompleted" to "INTEGER NOT NULL DEFAULT 0"
            ),
            "entity_ask" to listOf(
                "entityKey" to "TEXT NOT NULL DEFAULT ''",
                "entityType" to "TEXT NOT NULL DEFAULT ''",
                "askedAt" to "INTEGER NOT NULL DEFAULT 0",
                "status" to "TEXT NOT NULL DEFAULT 'asked'"
            )
        )
    }
}