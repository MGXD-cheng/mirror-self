package com.mgxd.mirror.data.db.dao

import android.content.ContentValues
import android.database.sqlite.SQLiteOpenHelper
import com.mgxd.mirror.data.db.entity.UserProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 用户画像 DAO（手写 SQL）。
 */
class UserProfileDao(private val helper: SQLiteOpenHelper) {

    suspend fun getProfile(): UserProfileEntity? = withContext(Dispatchers.IO) {
        helper.readableDatabase.query(
            "user_profile", null, "id = 1", null, null, null, null, "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toProfile() else null
        }
    }

    suspend fun upsert(profile: UserProfileEntity) = withContext(Dispatchers.IO) {
        helper.writableDatabase.insertWithOnConflict(
            "user_profile", null, profile.toValues(),
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    suspend fun updateProgress(progress: Int, ts: Long = System.currentTimeMillis()) =
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("learningProgress", progress)
                put("updatedAt", ts)
            }
            helper.writableDatabase.update("user_profile", values, "id = 1", null)
        }

    suspend fun markOnboardingDone(ts: Long = System.currentTimeMillis()) =
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("onboardingDone", 1)
                put("updatedAt", ts)
            }
            helper.writableDatabase.update("user_profile", values, "id = 1", null)
        }

    private fun UserProfileEntity.toValues(): ContentValues {
        return ContentValues().apply {
            put("id", id)
            put("identity", identity)
            put("nickname", nickname)
            put("wakeTime", wakeTime)
            put("sleepTime", sleepTime)
            put("commuteTime", commuteTime)
            put("workStart", workStart)
            put("workEnd", workEnd)
            put("personality", personality)
            put("interests", interests)
            put("catchphrases", catchphrases)
            put("toneWords", toneWords)
            put("emojiStyle", emojiStyle)
            put("decisionStyle", decisionStyle)
            put("emotionPattern", emotionPattern)
            put("hardConstraints", hardConstraints)
            put("languageStyle", languageStyle)
            put("psychologyProfile", psychologyProfile)
            put("activeLevel", activeLevel)
            put("askEnabled", if (askEnabled) 1 else 0)
            put("learningProgress", learningProgress)
            put("onboardingDone", if (onboardingDone) 1 else 0)
            put("updatedAt", updatedAt)
        }
    }

    private fun android.database.Cursor.toProfile(): UserProfileEntity {
        return UserProfileEntity(
            id = getLong(getColumnIndexOrThrow("id")),
            identity = getStringOr("identity"),
            nickname = getStringOr("nickname"),
            wakeTime = getStringOr("wakeTime", "07:00"),
            sleepTime = getStringOr("sleepTime", "23:00"),
            commuteTime = getStringOr("commuteTime", "08:30"),
            workStart = getStringOr("workStart", "09:00"),
            workEnd = getStringOr("workEnd", "18:00"),
            personality = getStringOr("personality"),
            interests = getStringOr("interests"),
            catchphrases = getStringOr("catchphrases"),
            toneWords = getStringOr("toneWords"),
            emojiStyle = getStringOr("emojiStyle"),
            decisionStyle = getStringOr("decisionStyle"),
            emotionPattern = getStringOr("emotionPattern"),
            hardConstraints = getStringOr("hardConstraints", "[]"),
            languageStyle = getStringOr("languageStyle"),
            psychologyProfile = getStringOr("psychologyProfile", "{}"),
            activeLevel = getIntOr("activeLevel", 2),
            askEnabled = getIntOr("askEnabled", 1) == 1,
            learningProgress = getInt(getColumnIndexOrThrow("learningProgress")),
            onboardingDone = getInt(getColumnIndexOrThrow("onboardingDone")) == 1,
            updatedAt = getLong(getColumnIndexOrThrow("updatedAt"))
        )
    }

    private fun android.database.Cursor.getStringOr(column: String, default: String = ""): String {
        val idx = getColumnIndex(column)
        return if (idx >= 0 && !isNull(idx)) getString(idx) else default
    }

    private fun android.database.Cursor.getIntOr(column: String, default: Int): Int {
        val idx = getColumnIndex(column)
        return if (idx >= 0 && !isNull(idx)) getInt(idx) else default
    }
}