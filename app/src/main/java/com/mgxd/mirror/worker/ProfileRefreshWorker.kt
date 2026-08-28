package com.mgxd.mirror.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mgxd.mirror.data.db.AppDatabase
import com.mgxd.mirror.data.db.entity.FactMemoryEntity
import com.mgxd.mirror.data.network.AiRepository
import com.mgxd.mirror.domain.FactExtractor
import com.mgxd.mirror.domain.ProfileAnalyzer
import com.mgxd.mirror.domain.ScheduleGenerator
import com.mgxd.mirror.util.SensitiveDataMasker

/**
 * 画像刷新 Worker。
 * 每天执行一次：从近期聊天中批量提取特征（含 AI 增强的事实提取）、
 * 更新画像、清理过期记忆并预生成未来一周日程。
 */
class ProfileRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.getInstance(applicationContext)
            val profile = db.userProfileDao.getProfile() ?: return Result.success()

            // 1. 从近期聊天中批量学习（本地规则引擎，逐条分析）
            val recent = db.chatMessageDao.getRecent(30)
            for (msg in recent) {
                if (msg.sender == "user") {
                    ProfileAnalyzer.learnFromMessage(profile, msg.content)
                }
            }
            db.userProfileDao.upsert(profile)

            // 2. AI 增强事实提取（每日一次，对最近用户消息，去重入库；失败静默走本地规则兜底）
            val config = db.apiConfigDao.getConfig()
            if (config != null && config.apiKeyEncrypted.isNotEmpty()) {
                try {
                    val ai = AiRepository()
                    val userMsgs = recent.filter { it.sender == "user" }.takeLast(5)
                    for (msg in userMsgs) {
                        val masked = SensitiveDataMasker.mask(msg.content)
                        if (masked.isBlank()) continue
                        val prompt = "从用户消息中提取值得记住的细节，输出JSON数组，每项含type(喜好/计划/人物/事件/烦恼/纪念日/习惯/其他)、content、importance(1-5)、date(若有日期写yyyy-MM-dd，无则空)。没有值得记的就输出[]。只输出JSON。"
                        val raw = try {
                            ai.chat(config, prompt, masked)
                        } catch (e: Exception) {
                            continue
                        }
                        val facts = FactExtractor.parseAiJson(raw)
                        for (f in facts) {
                            db.factMemoryDao.upsertDedupe(
                                FactMemoryEntity(
                                    type = f.type,
                                    content = f.content,
                                    sourceMessageId = msg.id,
                                    importance = f.importance,
                                    eventDate = f.eventDate
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    // AI 增强失败不影响主流程
                }
            }

            // 3. 记忆维护：过期事件清理 + 陈旧记忆归档
            db.factMemoryDao.expirePastEvents(java.time.LocalDate.now().toString())
            db.factMemoryDao.archiveStale(System.currentTimeMillis(), 30L * 24 * 3600_000L)

            // 4. 预生成未来一周日程（已存在的日期跳过）
            val today = java.time.LocalDate.now()
            for (i in 0 until 7) {
                val date = today.plusDays(i.toLong())
                if (db.scheduleEventDao.getByDate(date.toString()).isEmpty()) {
                    db.scheduleEventDao.insertAll(ScheduleGenerator.generateForDate(profile, date))
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "profile_refresh_worker"
    }
}