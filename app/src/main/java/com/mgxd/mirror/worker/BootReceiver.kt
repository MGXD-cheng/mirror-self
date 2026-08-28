package com.mgxd.mirror.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机/应用更新后重新调度后台任务的接收器。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            WorkerScheduler.scheduleAll(context)
        }
    }
}