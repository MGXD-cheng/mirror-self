package com.mgxd.mirror.data.network

import com.mgxd.mirror.data.db.entity.ApiConfigEntity
import com.mgxd.mirror.util.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * AI 服务仓库：封装 DeepSeek（及其他 OpenAI 兼容接口）调用。
 * - 支持自定义 Base URL / API Key / 模型
 * - 超时控制，超时抛异常由上层走本地兜底
 */
class AiRepository {

    private var cachedService: AiApiService? = null

    private fun getService(timeoutSeconds: Int): AiApiService {
        cachedService?.let { return it }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl("https://api.deepseek.com/") // 占位，实际使用 @Url 传完整地址
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AiApiService::class.java)
            .also { cachedService = it }
    }

    /**
     * 调用 AI 生成回复。
     * @param systemPrompt 系统提示词（含 XML 结构的前缀，保持稳定以命中缓存）
     * @param userInput 当前用户输入
     * @return AI 回复文本；失败时抛出异常，由调用方兜底
     */
    suspend fun chat(
        config: ApiConfigEntity,
        systemPrompt: String,
        userInput: String
    ): String = withContext(Dispatchers.IO) {
        val apiKey = CryptoManager.decrypt(config.apiKeyEncrypted)
        if (apiKey.isEmpty()) throw IllegalStateException("API Key 未配置")

        val service = getService(config.timeoutSeconds.coerceAtLeast(5))
        val request = ChatCompletionRequest(
            model = config.model.ifEmpty { "deepseek-chat" },
            messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = userInput)
            ),
            maxTokens = config.maxTokens,
            temperature = config.temperature
        )

        val response = service.chatCompletion(
            url = normalizeUrl(config.baseUrl),
            auth = "Bearer $apiKey",
            request = request
        )

        response.error?.let {
            throw IllegalStateException(it.message ?: "API 返回错误")
        }

        response.choices?.firstOrNull()?.message?.content
            ?: throw IllegalStateException("AI 返回为空")
    }

    /**
     * 连接自检：用当前配置发送一条极短请求，返回人类可读的诊断结果。
     */
    suspend fun testConnection(config: ApiConfigEntity): String = withContext(Dispatchers.IO) {
        val apiKey = CryptoManager.decrypt(config.apiKeyEncrypted)
        if (apiKey.isEmpty()) return@withContext "❌ API Key 未配置（请先在设置页填入）"

        val service = getService(config.timeoutSeconds.coerceAtLeast(5))
        val request = ChatCompletionRequest(
            model = config.model.ifEmpty { "deepseek-chat" },
            messages = listOf(ChatMessage(role = "user", content = "ping")),
            maxTokens = 10,
            temperature = 0f
        )
        try {
            val response = service.chatCompletion(
                url = normalizeUrl(config.baseUrl),
                auth = "Bearer $apiKey",
                request = request
            )
            response.error?.let { return@withContext "❌ 接口返回错误：${it.message}" }
            val content = response.choices?.firstOrNull()?.message?.content
            if (content != null) {
                "✅ 连接成功！AI 响应：${content.take(40)}"
            } else {
                "⚠️ 已连接，但返回内容为空（模型：${request.model}）"
            }
        } catch (e: Exception) {
            val msg = e.message ?: "未知错误"
            // 404 常见原因：Base URL 缺 /chat/completions 路径
            if (msg.contains("404")) {
                "❌ HTTP 404：请检查 Base URL 是否为完整接口地址，如 https://api.deepseek.com/v1/chat/completions"
            } else {
                "❌ 连接失败：$msg"
            }
        }
    }

    companion object {
        private const val DEFAULT_URL = "https://api.deepseek.com/v1/chat/completions"

        /**
         * Base URL 智能归一化：兼容各种填法
         * - https://api.deepseek.com                  → + /chat/completions
         * - https://api.deepseek.com/v1                → + /chat/completions
         * - https://api.deepseek.com/v1/chat           → + /completions
         * - https://api.deepseek.com/v1/chat/completions → 原样
         * - https://api.xxx.com/chat/completions        → 原样
         */
        fun normalizeUrl(raw: String): String {
            val url = raw.trim().trimEnd('/')
            if (url.isBlank()) return DEFAULT_URL
            if (url.contains("/chat/completions")) return url
            val segments = url.split('/').filter { it.isNotEmpty() }
            val last = segments.lastOrNull()?.lowercase()
            return when {
                last == "v1" -> "$url/chat/completions"
                last == "chat" -> "$url/completions"
                else -> "$url/chat/completions"
            }
        }
    }
}