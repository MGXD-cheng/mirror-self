package com.mgxd.mirror

import android.app.Application
import com.mgxd.mirror.util.NotificationHelper
import com.mgxd.mirror.worker.WorkerScheduler

/**
 * 应用入口：初始化通知渠道、调度后台任务。
 */
class MirrorApp : Application() {

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)
        WorkerScheduler.scheduleAll(this)
    }
}