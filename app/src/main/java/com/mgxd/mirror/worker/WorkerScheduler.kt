package com.mgxd.mirror.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 后台任务调度器。
 * - 日程检查：每 10 分钟一次（最短周期，用于命中事件触发窗口）
 * - 画像刷新：每天一次
 */
object WorkerScheduler {

    fun scheduleAll(context: Context) {
        val workManager = WorkManager.getInstance(context)

        // 日程检查：周期 10 分钟（Android 允许的最短周期）
        val scheduleRequest = PeriodicWorkRequestBuilder<ScheduleCheckWorker>(10, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(
            ScheduleCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            scheduleRequest
        )

        // 画像刷新：每天一次
        val profileRequest = PeriodicWorkRequestBuilder<ProfileRefreshWorker>(1, TimeUnit.DAYS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            ProfileRefreshWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            profileRequest
        )

        // 镜像主动消息检查器：每 15 分钟评估一次（内部有严格频率控制）
        val activeRequest = PeriodicWorkRequestBuilder<MirrorActiveWorker>(15, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(
            MirrorActiveWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            activeRequest
        )
    }
}