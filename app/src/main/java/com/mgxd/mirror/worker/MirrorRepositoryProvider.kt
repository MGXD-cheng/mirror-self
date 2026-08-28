package com.mgxd.mirror.worker

import android.content.Context
import com.mgxd.mirror.data.repository.MirrorRepository

/**
 * 仓库提供者：避免 ViewModel / Worker 各自重复构造 Repository 造成连接泄漏。
 */
object MirrorRepositoryProvider {

    @Volatile
    private var repository: MirrorRepository? = null

    fun get(context: Context): MirrorRepository {
        return repository ?: synchronized(this) {
            repository ?: MirrorRepository(context.applicationContext).also {
                repository = it
            }
        }
    }
}