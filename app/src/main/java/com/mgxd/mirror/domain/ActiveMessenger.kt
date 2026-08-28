package com.mgxd.mirror.domain

import com.mgxd.mirror.data.db.entity.FactMemoryEntity
import com.mgxd.mirror.data.db.entity.ScheduleEventEntity
import com.mgxd.mirror.data.db.entity.UserProfileEntity
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * 主动社交消息引擎：像真实朋友一样，在合理时机主动开启话题。
 *
 * 触发类型（优先级从高到低）：
 * - MEMORY_EVENT 记忆事件（今天/明天有事件 → 主动关心）
 * - SCHEDULE 日程关联（当前处于某日程事件时段）
 * - INTEREST 用户兴趣（基于高重要度记忆发起话题）
 * - TIMESLOT 时段闲聊（午休/晚饭后/睡前）
 * - FOLLOW_UP 回访（超 24h 未聊且处于活跃时段）
 * - RANDOM 随机生活分享（概率最低，每天最多 1 条）
 */
object ActiveMessenger {

    enum class TriggerType(val label: String) {
        MEMORY_EVENT("记忆事件"),
        SCHEDULE("日程关联"),
        INTEREST("兴趣话题"),
        TIMESLOT("时段闲聊"),
        FOLLOW_UP("回访"),
        RANDOM("生活分享")
    }

    data class Decision(
        val type: TriggerType,
        val fact: FactMemoryEntity? = null,
        val event: ScheduleEventEntity? = null
    )

    private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * 评估当前是否应主动发消息。
     * @param activeLevel 0=关闭 1=低 2=中 3=高
     * @param lastMirrorAt 上一条 mirror 消息时间（0=无）
     * @param lastUserAt 上一条用户消息时间（0=无）
     * @param todayEvents 今日日程
     */
    fun decide(
        profile: UserProfileEntity,
        facts: List<FactMemoryEntity>,
        upcomingFacts: List<FactMemoryEntity>,
        todayEvents: List<ScheduleEventEntity>,
        lastMirrorAt: Long,
        lastUserAt: Long,
        now: Long = System.currentTimeMillis()
    ): Decision? {
        if (!profile.onboardingDone) return null
        val level = profile.activeLevel
        if (level <= 0) return null
        val nowTime = LocalTime.now()
        val hour = nowTime.hour
        val today = LocalDate.now().toString()
        val tomorrow = LocalDate.now().plusDays(1).toString()

        // ---- 频率硬限制 ----
        // 距上次主动消息不足 1 小时 → 不发
        if (lastMirrorAt > 0 && now - lastMirrorAt < 3600_000L) return null
        // 用户长时间未回复（>2h 未发消息，且上一条是镜像）→ 等用户
        if (lastUserAt > 0 && now - lastUserAt > 2 * 3600_000L && lastMirrorAt > lastUserAt) return null

        // ---- 深夜限制（23:00-7:00 禁止闲聊，仅允许明日重要事件提醒）----
        val isLateNight = hour >= 23 || hour < 7

        // ---- 1. 记忆事件（最高优先级）----
        val factToday = upcomingFacts.firstOrNull { it.eventDate == today && !it.isCompleted }
        val factTomorrow = upcomingFacts.firstOrNull { it.eventDate == tomorrow && !it.isCompleted }
        if (!isLateNight || factTomorrow != null) {
            factToday?.let { return Decision(TriggerType.MEMORY_EVENT, fact = it) }
            // 明天有事件：晚上（19点后）提前关心，或在活跃时段
            factTomorrow?.let {
                if (hour in 18..22 || Random.nextInt(100) < 40) {
                    return Decision(TriggerType.MEMORY_EVENT, fact = it)
                }
            }
        }

        if (isLateNight) return null // 深夜不再发其他类型

        // ---- 2. 日程关联（当前时间处于事件窗口内）----
        val nowMin = nowTime.hour * 60 + nowTime.minute
        todayEvents.firstOrNull { e ->
            val start = parseMin(e.windowStart) ?: return@firstOrNull false
            val end = parseMin(e.windowEnd) ?: return@firstOrNull false
            nowMin in start..end
        }?.let { return Decision(TriggerType.SCHEDULE, event = it) }

        // ---- 3. 兴趣话题（高重要度记忆 + 概率）----
        val topicFact = facts.firstOrNull { it.importance >= 3 && it.lastUsedAt < now - 24 * 3600_000L }
        if (topicFact != null && roll(level, 45)) {
            return Decision(TriggerType.INTEREST, fact = topicFact)
        }

        // ---- 4. 回访（超 24h 未聊，活跃时段）----
        if (lastUserAt > 0 && now - lastUserAt > 24 * 3600_000L && isActiveHour(hour) && roll(level, 50)) {
            return Decision(TriggerType.FOLLOW_UP)
        }

        // ---- 5. 时段闲聊 ----
        if (isChatTime(hour) && roll(level, 35)) {
            return Decision(TriggerType.TIMESLOT)
        }

        // ---- 6. 随机生活分享（低概率，一天最多一条由上层控制）----
        if (Random.nextInt(100) < 15) {
            return Decision(TriggerType.RANDOM)
        }

        return null
    }

    /** 根据触发类型生成消息（AI 失败时的本地模板） */
    fun templateFor(
        type: TriggerType,
        profile: UserProfileEntity,
        fact: FactMemoryEntity?,
        event: ScheduleEventEntity?,
        hour: Int
    ): String {
        val nick = profile.nickname.ifBlank { "" }
        val nickPrefix = if (nick.isNotBlank() && Random.nextBoolean()) "$nick，" else ""
        return when (type) {
            TriggerType.MEMORY_EVENT -> {
                val f = fact ?: return nickPrefix + ActiveGreetings.greeting(profile, hour)
                val dateDesc = if (f.eventDate == LocalDate.now().toString()) "今天" else "明天"
                listOf(
                    "$nickPrefix$dateDesc${describeFact(f)}，加油啊！",
                    "$nickPrefix$dateDesc${describeFact(f)}，你准备得咋样了？",
                    "$nickPrefix$dateDesc${describeFact(f)}，别忘啦！需要我提醒你吗？"
                ).random()
            }
            TriggerType.SCHEDULE -> {
                val e = event ?: return nickPrefix + "到点啦，该${"活动一下"}了"
                when (e.type) {
                    "WAKE" -> listOf(
                        "早，我醒了，你起了吗？",
                        "起床啦！今天又是新的一天~"
                    ).random()
                    "WORK" -> "开工了，今天任务有点多，一起加油吧"
                    "MEAL" -> "到饭点了！今天想吃啥？"
                    "SLEEP" -> "不早了，我准备睡了，你也早点休息~"
                    "FUN" -> "现在是我的放松时间，你呢？"
                    "OUT" -> "我出门了，路上人有点多"
                    else -> "到这个点了，记得歇口气"
                }
            }
            TriggerType.INTEREST -> {
                val f = fact ?: return nickPrefix + ActiveGreetings.greeting(profile, hour)
                listOf(
                    "突然想到你之前${describeFact(f)}，最近还在弄吗？",
                    "你上次说${describeFact(f)}，现在咋样啦？",
                    "诶，你不是喜欢${extractTopic(f)}嘛，最近有没有继续？"
                ).random()
            }
            TriggerType.TIMESLOT -> when (hour) {
                in 12..14 -> listOf("午饭吃了没？别光顾着忙呀", "干饭时间到！饿不饿？").random()
                in 18..21 -> listOf("晚上好呀，今天过得怎么样？", "回来了吗？辛苦啦，好好歇会儿").random()
                in 21..23 -> listOf("在干嘛呀", "睡前聊两句？今天有啥新鲜事吗").random()
                else -> nickPrefix + ActiveGreetings.greeting(profile, hour)
            }
            TriggerType.FOLLOW_UP -> listOf(
                "今天咋样？在忙啥呢",
                "好久没聊了，最近还好吗",
                "突然想找你唠两句，你今天过得咋样"
            ).random()
            TriggerType.RANDOM -> listOf(
                "刚看到一只猫，好可爱，想摸但跑了",
                "今天天气好适合睡觉……",
                "突然想喝奶茶了，你呢",
                "刚听到一首歌好好听，安利给你~",
                "今天的天好蓝，心情莫名不错"
            ).random()
        }
    }

    /** 记忆内容的自然描述（去类型前缀） */
    private fun describeFact(f: FactMemoryEntity): String {
        var c = f.content
        listOf("喜欢", "不太喜欢", "习惯", "计划", "下周", "明天", "要去", "要做", "要交", "要考", "要面").forEach {
            c = c.removePrefix(it)
        }
        return c.trim().ifBlank { f.content }
    }

    private fun extractTopic(f: FactMemoryEntity): String {
        return describeFact(f).trim().ifBlank { "那个" }
    }

    /** 用户可能空闲的时段 */
    private fun isChatTime(hour: Int): Boolean {
        return hour in 12..14 || hour in 18..23
    }

    /** 用户通常活跃时段（用于回访） */
    private fun isActiveHour(hour: Int): Boolean {
        return hour in 8..23
    }

    /** 按主动程度滚动概率：高=更主动 */
    private fun roll(level: Int, basePct: Int): Boolean {
        val pct = when (level) {
            1 -> basePct / 2
            3 -> (basePct * 1.5).toInt().coerceAtMost(95)
            else -> basePct
        }
        return Random.nextInt(100) < pct
    }

    private fun parseMin(t: String): Int? {
        return try {
            val parts = t.split(":")
            if (parts.size != 2) null else parts[0].toInt() * 60 + parts[1].toInt()
        } catch (e: Exception) {
            null
        }
    }
}