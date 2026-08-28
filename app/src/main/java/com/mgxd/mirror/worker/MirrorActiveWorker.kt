package com.mgxd.mirror.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * 主动社交消息 Worker（主动消息检查器）。
 * 周期触发（15 分钟）：评估当前是否适合主动开启话题（记忆事件/日程/兴趣/时段/回访/随机），
 * 消息以普通聊天消息形式入库，不弹系统通知（通知栏仅用于日程提醒）。
 * 内部有严格频率控制（每天 1-3 条、间隔≥1h、深夜限制、用户未回复暂停）。
 */
class MirrorActiveWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val repository = MirrorRepositoryProvider.get(applicationContext)
            repository.proactiveMessage() // 返回 null 表示未触发，静默跳过
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "mirror_active_worker"
    }
}