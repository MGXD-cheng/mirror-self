package com.mgxd.mirror.data.db.entity

/**
 * AI 服务配置实体。单例行（id=1）。
 * apiKey 字段存储的是经 Keystore 加密后的密文。
 */
data class ApiConfigEntity(
    val id: Long = 1L,

    var baseUrl: String = "https://api.deepseek.com/v1/chat/completions",
    var apiKeyEncrypted: String = "",   // 加密后的 API Key
    var model: String = "deepseek-chat",
    var maxTokens: Int = 1024,
    var temperature: Float = 0.8f,
    var enableFallback: Boolean = true,
    var timeoutSeconds: Int = 15
)