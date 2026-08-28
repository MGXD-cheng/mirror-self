package com.mgxd.mirror.data.db

import android.content.Context
import com.mgxd.mirror.data.db.dao.ApiConfigDao
import com.mgxd.mirror.data.db.dao.ChatMessageDao
import com.mgxd.mirror.data.db.dao.EntityAskDao
import com.mgxd.mirror.data.db.dao.FactMemoryDao
import com.mgxd.mirror.data.db.dao.ScheduleEventDao
import com.mgxd.mirror.data.db.dao.UserProfileDao

/**
 * 数据访问入口：单例持有各 DAO。
 */
class AppDatabase private constructor(context: Context) {

    private val helper = MirrorDbHelper(context.applicationContext)

    val userProfileDao = UserProfileDao(helper)
    val scheduleEventDao = ScheduleEventDao(helper)
    val chatMessageDao = ChatMessageDao(helper)
    val apiConfigDao = ApiConfigDao(helper)
    val factMemoryDao = FactMemoryDao(helper)
    val entityAskDao = EntityAskDao(helper)

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppDatabase(context).also { INSTANCE = it }
            }
        }
    }
}