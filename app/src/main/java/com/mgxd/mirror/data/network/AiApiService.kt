package com.mgxd.mirror.data.network

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * OpenAI 兼容的 Chat Completions 接口。
 * Base URL 可动态配置，因此使用 @Url 传完整地址。
 */
interface AiApiService {

    @POST
    suspend fun chatCompletion(
        @Url url: String,
        @Header("Authorization") auth: String,
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse
}