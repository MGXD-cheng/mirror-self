package com.mgxd.mirror.data.db.entity

/**
 * 细节记忆实体：从聊天中提取的"值得记住的小事"。
 * 本地存储，绝不外传（调用 AI 前会脱敏）。
 *
 * 类型枚举：喜好 / 计划 / 人物 / 事件 / 烦恼 / 纪念日 / 习惯 / 口头禅 / 其他
 * 状态：active 有效 / expired 已过期（不主动引用）/ archived 已归档（保留不主动用）
 */
data class FactMemoryEntity(
    var id: Long = 0L,
    var type: String = "其他",          // 喜好/计划/人物/事件/烦恼/纪念日/习惯/口头禅/其他
    var content: String = "",           // 提取的具体内容
    var sourceMessageId: Long = 0L,     // 来源聊天消息 ID
    var createdAt: Long = System.currentTimeMillis(),
    var lastUsedAt: Long = 0L,          // 最近一次被引用时间（0=从未引用）
    var importance: Int = 1,            // 1-5
    var status: String = "active",      // active / expired / archived
    var eventDate: String = "",         // 日期型记忆（如"2026-08-29"），非日期型为空
    var isCompleted: Boolean = false    // 日期型事件是否已完成
)