package com.mgxd.mirror.data.network

import com.google.gson.annotations.SerializedName

/**
 * OpenAI Chat Completions 格式的请求/响应模型。
 * 兼容 DeepSeek 及其他主流大模型接口。
 */
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    @SerializedName("max_tokens") val maxTokens: Int = 1024,
    val temperature: Float = 0.8f,
    val stream: Boolean = false
)

data class ChatMessage(
    val role: String,       // system / user / assistant
    val content: String
)

data class ChatCompletionResponse(
    val id: String? = null,
    val choices: List<Choice>? = null,
    val error: ApiError? = null
)

data class Choice(
    val index: Int = 0,
    val message: ChatMessage? = null,
    @SerializedName("finish_reason") val finishReason: String? = null
)

data class ApiError(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)