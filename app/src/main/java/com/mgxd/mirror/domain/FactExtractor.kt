package com.mgxd.mirror.domain

import org.json.JSONArray
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 细节记忆提取器。
 * - extractLocal：本地关键词规则（同步、离线可靠），覆盖喜好/计划/人物/事件/烦恼/习惯/纪念日
 * - parseAiJson：解析 AI 返回的结构化 JSON（Worker 中批量增强）
 */
object FactExtractor {

    data class RawFact(
        val type: String,
        val content: String,
        val importance: Int = 2,
        val eventDate: String = ""
    )

    private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** 本地规则提取（用户消息 → 若干事实） */
    fun extractLocal(message: String, today: LocalDate): List<RawFact> {
        val msg = message.trim()
        if (msg.length < 4 || msg.length > 200) return emptyList()
        val facts = mutableListOf<RawFact>()

        // ---- 喜好 ----
        val like = Regex("我(?:超|很|好|最|特别)?(?:喜欢|爱吃|爱喝|爱玩|爱看|迷|上头)(?:的|这个|那个)?([\\u4e00-\\u9fa5A-Za-z0-9]{1,12})")
        like.find(msg)?.let {
            val target = it.groupValues[1]
            if (target.isNotBlank()) facts += RawFact("喜好", "喜欢$target", 3)
        }
        val dislike = Regex("我(?:不太|不|超不)?(?:喜欢|爱吃|爱喝|爱看|想)(?:的)?([\\u4e00-\\u9fa5A-Za-z0-9]{1,12})")
        dislike.find(msg)?.let {
            val target = it.groupValues[1]
            if (target.isNotBlank()) facts += RawFact("喜好", "不太喜欢$target", 2)
        }

        // ---- 计划 / 事件（含日期）----
        val dateInfo = parseDateClue(msg, today)
        val plan = Regex("(?:我)?(?:要|打算|准备|计划|得)(?:去|做|参加|办)?([\\u4e00-\\u9fa5A-Za-z0-9]{2,20})")
        plan.find(msg)?.let {
            val content = it.groupValues[1]
            if (content.isNotBlank() && !content.contains("睡觉") && !content.contains("吃饭")) {
                facts += RawFact(
                    if (dateInfo != null) "事件" else "计划",
                    content.trim(),
                    importance = if (dateInfo != null) 4 else 2,
                    eventDate = dateInfo ?: ""
                )
            }
        }

        // 明确的日期事件："周五要交报告 / 下周面试 / 明天体检"
        val dateEvent = Regex("(?:下[周个]|这[周个]|明天|后天|周[一二三四五六日天]|\\d{1,2}月\\d{1,2}日?|\\d{1,2}号)(?:的|要|有|去|交|考|面|体|开)?([\\u4e00-\\u9fa5A-Za-z0-9]{2,16})")
        dateEvent.find(msg)?.let {
            val content = it.groupValues[1]
            if (content.isNotBlank() && dateInfo != null) {
                facts += RawFact("事件", "${it.value.trim()}${content}", 4, dateInfo)
            }
        }

        // ---- 人物 ----
        val person = Regex("(?:我)?(?:朋友|同学|室友|同事|闺蜜|兄弟|发小)(?:有|叫|是|有个|认识)?([\\u4e00-\\u9fa5A-Za-z0-9]{1,8})")
        person.find(msg)?.let {
            val name = it.groupValues[1]
            if (name.isNotBlank() && !name.contains("叫")) {
                facts += RawFact("人物", "朋友${name}", 3)
            }
        }

        // ---- 烦恼 / 状态 ----
        val worryKeywords = listOf("好烦", "压力", "焦虑", "emo", "难过", "累死", "崩溃", "失眠", "内耗", "烦死")
        if (worryKeywords.any { msg.contains(it) }) {
            facts += RawFact("烦恼", msg.take(40), 4)
        }

        // ---- 习惯 ----
        val habit = Regex("我(?:一般|通常|习惯|喜欢)?(?:在|会|都)?([\\u4e00-\\u9fa5A-Za-z0-9]{2,16})")
        if (msg.contains("习惯") || msg.contains("一般") || msg.contains("经常") || msg.contains("每天")) {
            habit.find(msg)?.let {
                val content = it.groupValues[1]
                if (content.isNotBlank()) facts += RawFact("习惯", "习惯${content}", 2)
            }
        }

        // ---- 纪念日 ----
        if (msg.contains("纪念日") || msg.contains("周年") || msg.contains("生日")) {
            facts += RawFact("纪念日", msg.take(40), 4)
        }

        // 去重 & 过滤过短
        return facts.distinctBy { it.content }
            .filter { it.content.length >= 2 }
            .take(3)
    }

    /** 解析中文日期线索 → yyyy-MM-dd（无法解析返回 null） */
    fun parseDateClue(msg: String, today: LocalDate): String? {
        val weekdayMap = mapOf(
            "一" to 1, "二" to 2, "三" to 3, "四" to 4,
            "五" to 5, "六" to 6, "日" to 7, "天" to 7
        )
        return when {
            msg.contains("明天") -> today.plusDays(1).format(dateFmt)
            msg.contains("后天") -> today.plusDays(2).format(dateFmt)
            msg.contains("下周") && msg.contains("周") -> {
                val w = weekdayMap.entries.firstOrNull { msg.contains("周${it.key}") }?.value ?: return null
                // 下周一 = 从今天算到下一个"下"周期的该星期
                val daysUntil = (w - today.dayOfWeek.value + 7) % 7
                today.plusDays((7 + daysUntil).toLong()).format(dateFmt)
            }
            msg.contains("周") -> {
                val w = weekdayMap.entries.firstOrNull { msg.contains("周${it.key}") }?.value ?: return null
                val daysUntil = (w - today.dayOfWeek.value + 7) % 7
                val days = if (daysUntil == 0) 7 else daysUntil // 本周该天（若今天则取下周）
                today.plusDays(days.toLong()).format(dateFmt)
            }
            else -> {
                val m = Regex("(\\d{1,2})月(\\d{1,2})日?").find(msg)
                if (m != null) {
                    val month = m.groupValues[1].toIntOrNull() ?: return null
                    val day = m.groupValues[2].toIntOrNull() ?: return null
                    try {
                        LocalDate.of(today.year, month, day).format(dateFmt)
                    } catch (e: Exception) {
                        null
                    }
                } else {
                    val d = Regex("(\\d{1,2})号").find(msg)
                    if (d != null) {
                        val day = d.groupValues[1].toIntOrNull() ?: return null
                        try {
                            LocalDate.of(today.year, today.monthValue, day).format(dateFmt)
                        } catch (e: Exception) {
                            null
                        }
                    } else null
                }
            }
        }
    }

    /** 解析 AI 返回的 JSON 数组：[{"type":"计划","content":"下周去日本","importance":4,"date":"2026-08-29"}] */
    fun parseAiJson(json: String): List<RawFact> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val content = o.optString("content", "").trim()
                if (content.isBlank()) return@mapNotNull null
                RawFact(
                    type = o.optString("type", "其他"),
                    content = content,
                    importance = o.optInt("importance", 2).coerceIn(1, 5),
                    eventDate = o.optString("date", "")
                )
            }.take(5)
        } catch (e: Exception) {
            emptyList()
        }
    }
}