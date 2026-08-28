package com.mgxd.mirror.domain

import org.json.JSONObject

/**
 * 心理学画像模型：9 个维度的性格模拟数据。
 * 存储为 UserProfileEntity.psychologyProfile 的 JSON 字符串，兼容旧数据（缺省为空）。
 *
 * 维度：
 * - bigFive：大五人格（开放性/尽责性/外向性/宜人性/神经质）
 * - decisionStyle：决策风格（理性分析/直觉冲动/依赖他人/回避犹豫）
 * - emotionRegulation：情绪调节方式（倾诉/自我消化/转移/压抑）
 * - socialPreference：社交偏好（独处/小圈子/泛泛之交，主动/被动）
 * - values：价值观与动机（自由/家庭/事业/成就/安全感）
 * - stressCoping：压力应对模式（积极解决/拖延/逃避/寻求支持）
 * - attachmentStyle：依恋风格（安全/焦虑/回避/恐惧）
 * - humorStyle：幽默风格（自嘲/冷笑话/恶搞/讽刺）
 * - perfectionism：完美主义倾向（高/中/低）
 */
data class PsychologyProfile(
    var bigFive: String = "",
    var decisionStyle: String = "",
    var emotionRegulation: String = "",
    var socialPreference: String = "",
    var values: String = "",
    var stressCoping: String = "",
    var attachmentStyle: String = "",
    var humorStyle: String = "",
    var perfectionism: String = ""
) {

    /** 已填写的维度数量（9 个） */
    fun filledCount(): Int = listOf(
        bigFive, decisionStyle, emotionRegulation, socialPreference, values,
        stressCoping, attachmentStyle, humorStyle, perfectionism
    ).count { it.isNotBlank() }

    /** 心理学维度完整度 0-100（用于性格模拟置信度） */
    fun completeness(): Int = (filledCount() * 100 / 9)

    fun toJson(): String {
        return JSONObject().apply {
            put("bigFive", bigFive)
            put("decisionStyle", decisionStyle)
            put("emotionRegulation", emotionRegulation)
            put("socialPreference", socialPreference)
            put("values", values)
            put("stressCoping", stressCoping)
            put("attachmentStyle", attachmentStyle)
            put("humorStyle", humorStyle)
            put("perfectionism", perfectionism)
        }.toString()
    }

    /** 非空维度列表（用于展示/日志） */
    fun filledLabels(): List<Pair<String, String>> {
        val all = listOf(
            "大五人格" to bigFive,
            "决策风格" to decisionStyle,
            "情绪调节" to emotionRegulation,
            "社交偏好" to socialPreference,
            "价值观" to values,
            "压力应对" to stressCoping,
            "依恋风格" to attachmentStyle,
            "幽默风格" to humorStyle,
            "完美主义" to perfectionism
        )
        return all.filter { it.second.isNotBlank() }
    }

    companion object {
        fun fromJson(raw: String?): PsychologyProfile {
            if (raw.isNullOrBlank()) return PsychologyProfile()
            return try {
                val o = JSONObject(raw)
                PsychologyProfile(
                    bigFive = o.optString("bigFive", ""),
                    decisionStyle = o.optString("decisionStyle", ""),
                    emotionRegulation = o.optString("emotionRegulation", ""),
                    socialPreference = o.optString("socialPreference", ""),
                    values = o.optString("values", ""),
                    stressCoping = o.optString("stressCoping", ""),
                    attachmentStyle = o.optString("attachmentStyle", ""),
                    humorStyle = o.optString("humorStyle", ""),
                    perfectionism = o.optString("perfectionism", "")
                )
            } catch (e: Exception) {
                PsychologyProfile()
            }
        }
    }
}