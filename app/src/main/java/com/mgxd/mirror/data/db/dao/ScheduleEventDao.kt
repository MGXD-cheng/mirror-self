package com.mgxd.mirror.data.db.dao

import android.content.ContentValues
import android.database.sqlite.SQLiteOpenHelper
import com.mgxd.mirror.data.db.entity.ScheduleEventEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 日程事件 DAO（手写 SQL）。
 */
class ScheduleEventDao(private val helper: SQLiteOpenHelper) {

    suspend fun getByDate(date: String): List<ScheduleEventEntity> = withContext(Dispatchers.IO) {
        queryList("date = ? ORDER BY windowStart ASC", arrayOf(date))
    }

    suspend fun getByDateAndType(date: String, type: String): ScheduleEventEntity? =
        withContext(Dispatchers.IO) {
            helper.readableDatabase.query(
                "schedule_event", null,
                "date = ? AND type = ?", arrayOf(date, type), null, null, null, "1"
            ).use { c -> if (c.moveToFirst()) c.toEvent() else null }
        }

    suspend fun getPendingByDate(date: String): List<ScheduleEventEntity> =
        withContext(Dispatchers.IO) {
            queryList("date = ? AND isTriggered = 0 ORDER BY windowStart ASC", arrayOf(date))
        }

    suspend fun getUpcoming(date: String, limit: Int = 30): List<ScheduleEventEntity> =
        withContext(Dispatchers.IO) {
            queryList("date >= ? ORDER BY date ASC, windowStart ASC LIMIT $limit", arrayOf(date))
        }

    suspend fun insertAll(events: List<ScheduleEventEntity>) = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            for (e in events) {
                val values = e.toValues()
                if (e.id <= 0) {
                    // id<=0 时不写主键列，让 SQLite AUTOINCREMENT 自动分配，
                    // 避免多条 id=0 互相 REPLACE 覆盖（之前会导致日程只剩一条）
                    values.remove("id")
                }
                val newId = db.insertWithOnConflict(
                    "schedule_event", null, values,
                    android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
                )
                // 回填生成的 id，保证内存中的事件列表 key 唯一，防止 LazyColumn 崩溃
                if (e.id <= 0 && newId > 0) {
                    e.id = newId
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun update(event: ScheduleEventEntity) = withContext(Dispatchers.IO) {
        helper.writableDatabase.update("schedule_event", event.toValues(), "id = ?", arrayOf(event.id.toString()))
    }

    suspend fun delete(event: ScheduleEventEntity) = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete("schedule_event", "id = ?", arrayOf(event.id.toString()))
    }

    suspend fun deleteByDate(date: String) = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete("schedule_event", "date = ?", arrayOf(date))
    }

    private fun queryList(where: String, args: Array<String>): List<ScheduleEventEntity> {
        val list = mutableListOf<ScheduleEventEntity>()
        helper.readableDatabase.query(
            "schedule_event", null, where, args, null, null, null
        ).use { c ->
            while (c.moveToNext()) list.add(c.toEvent())
        }
        return list
    }

    private fun ScheduleEventEntity.toValues(): ContentValues {
        return ContentValues().apply {
            put("id", id)
            put("date", date)
            put("weekday", weekday)
            put("title", title)
            put("type", type)
            put("baseTime", baseTime)
            put("actualTime", actualTime)
            put("windowStart", windowStart)
            put("windowEnd", windowEnd)
            put("isTriggered", if (isTriggered) 1 else 0)
            put("isManual", if (isManual) 1 else 0)
            put("note", note)
        }
    }

    private fun android.database.Cursor.toEvent(): ScheduleEventEntity {
        return ScheduleEventEntity(
            id = getLong(getColumnIndexOrThrow("id")),
            date = getStringOr("date"),
            weekday = getInt(getColumnIndexOrThrow("weekday")),
            title = getStringOr("title"),
            type = getStringOr("type"),
            baseTime = getStringOr("baseTime"),
            actualTime = getStringOr("actualTime"),
            windowStart = getStringOr("windowStart"),
            windowEnd = getStringOr("windowEnd"),
            isTriggered = getInt(getColumnIndexOrThrow("isTriggered")) == 1,
            isManual = getInt(getColumnIndexOrThrow("isManual")) == 1,
            note = getStringOr("note")
        )
    }

    private fun android.database.Cursor.getStringOr(column: String, default: String = ""): String {
        val idx = getColumnIndex(column)
        return if (idx >= 0 && !isNull(idx)) getString(idx) else default
    }
}