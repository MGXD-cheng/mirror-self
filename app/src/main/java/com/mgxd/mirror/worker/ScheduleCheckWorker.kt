package com.mgxd.mirror.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mgxd.mirror.data.db.AppDatabase
import com.mgxd.mirror.data.db.entity.ScheduleEventEntity
import com.mgxd.mirror.domain.FallbackReplies
import com.mgxd.mirror.domain.ScheduleGenerator
import com.mgxd.mirror.util.NotificationHelper
import java.time.LocalDate
import java.time.LocalTime

/**
 * 日程检查 Worker。
 * 每 5-10 分钟执行一次，扫描当天未触发的事件，
 * 若当前时间落在事件触发窗口内，则推送第一人称通知并标记已触发。
 */
class ScheduleCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.getInstance(applicationContext)
            val today = LocalDate.now().toString()
            val pending = db.scheduleEventDao.getPendingByDate(today)
            val nowMinutes = nowMinutes()

            for (event in pending) {
                val start = parseMinutes(event.windowStart)
                val end = parseMinutes(event.windowEnd)
                if (nowMinutes in start..end) {
                    notifyEvent(event)
                    db.scheduleEventDao.update(event.copy(isTriggered = true))
                }
            }

            ensureUpcomingSchedule(db)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    /** 发送第一人称事件通知（本地模板保证毫秒级响应，绝不阻塞） */
    private fun notifyEvent(event: ScheduleEventEntity) {
        val title = when (event.type) {
            ScheduleGenerator.TYPE_WAKE -> "早呀"
            ScheduleGenerator.TYPE_SLEEP -> "晚安"
            ScheduleGenerator.TYPE_SURPRISE -> "突发情况"
            else -> "今天"
        }
        val body = FallbackReplies.eventNotification(event.type, event.actualTime, event.note)
        // 稳定且不溢出的通知 ID：用日期+事件的 hash，保证同一事件只推一次
        val notificationId = (event.date + "_" + event.id).hashCode()
        NotificationHelper.send(applicationContext, title, body, notificationId)
    }

    /** 顺带预生成未来 3 天日程，避免用户打开时空白 */
    private suspend fun ensureUpcomingSchedule(db: AppDatabase) {
        val today = LocalDate.now()
        val profile = db.userProfileDao.getProfile() ?: return
        for (i in 0 until 3) {
            val date = today.plusDays(i.toLong())
            if (db.scheduleEventDao.getByDate(date.toString()).isEmpty()) {
                db.scheduleEventDao.insertAll(ScheduleGenerator.generateForDate(profile, date))
            }
        }
    }

    private fun nowMinutes(): Int {
        val now = LocalTime.now()
        return now.hour * 60 + now.minute
    }

    private fun parseMinutes(time: String): Int {
        return try {
            val parts = time.split(":")
            parts[0].toInt() * 60 + parts[1].toInt()
        } catch (e: Exception) {
            0
        }
    }

    companion object {
        const val WORK_NAME = "schedule_check_worker"
    }
}