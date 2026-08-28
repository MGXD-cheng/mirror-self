package com.mgxd.mirror.data.db.entity

/**
 * 聊天消息实体。存储用户与"另一个自己"的对话记录。
 */
data class ChatMessageEntity(
    var id: Long = 0L,

    var sender: String = "user",        // user / mirror / system
    var content: String = "",
    var timestamp: Long = System.currentTimeMillis(),
    var isSensitive: Boolean = false    // 是否包含敏感信息（发送 AI 前脱敏）
)