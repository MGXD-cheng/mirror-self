package com.mgxd.mirror.domain

import com.mgxd.mirror.data.db.entity.ScheduleEventEntity
import com.mgxd.mirror.data.db.entity.UserProfileEntity
import org.json.JSONArray
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * 拟人化日程生成器 —— 拒绝机器人式准点。
 *
 * ## 时间模糊化核心思想
 * - 每个事件 = 基准时间 + 随机偏移（偏移范围按事件"主观性"动态调整）
 * - 起床/睡觉等主观事件：±15~30 分钟
 * - 通勤/工作等客观事件：±5~10 分钟
 * - 硬性约束（如"周三 6:40 早课"）优先，但仍保留 ±3 分钟弹性
 * - 同一天内事件之间保持合理间隔，不互相重叠
 */
object ScheduleGenerator {

    private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /** 事件类型常量 */
    const val TYPE_WAKE = "WAKE"
    const val TYPE_OUT = "OUT"
    const val TYPE_WORK = "WORK"
    const val TYPE_MEAL = "MEAL"
    const val TYPE_REST = "REST"
    const val TYPE_FUN = "FUN"
    const val TYPE_SLEEP = "SLEEP"
    const val TYPE_SURPRISE = "SURPRISE"

    /**
     * 为指定日期生成一天的拟人化日程。
     * @param date yyyy-MM-dd
     */
    fun generateForDate(profile: UserProfileEntity, date: LocalDate): List<ScheduleEventEntity> {
        val weekday = date.dayOfWeek.value // 1=周一 ... 7=周日
        val isWeekend = weekday >= 6

        // ---- 1. 确定各事件基准时间（周末与工作日差异明显） ----
        // 起床/睡觉为核心（有默认值）；其余选填字段做智能推算
        val wakeBase = parseTime(profile.wakeTime, "07:00")
        val sleepBase = parseTime(profile.sleepTime, "23:00")
        val workStartBase = parseTime(profile.workStart, "09:00")
        val workEndBase = parseTime(profile.workEnd, "18:00")
        val commuteBase = if (profile.commuteTime.isBlank()) {
            // 出门时间未填：默认工作开始前 30 分钟，且不早于起床后 30 分钟
            workStartBase.minusMinutes(30)
                .let { if (it.isBefore(wakeBase.plusMinutes(30))) wakeBase.plusMinutes(30) else it }
        } else {
            parseTime(profile.commuteTime, "08:30")
        }

        val events = mutableListOf<ScheduleEventEntity>()

        // 起床：周末推迟 1~1.5 小时
        val wake = if (isWeekend) wakeBase.plusHours(1).plusMinutes(Random.nextInt(0, 30).toLong())
        else wakeBase
        addEvent(events, date, weekday, "起床", TYPE_WAKE, wake, offsetMin = 15)

        // 早餐
        val breakfast = wake.plusMinutes(40 + Random.nextInt(0, 20).toLong())
        addEvent(events, date, weekday, "早餐", TYPE_MEAL, breakfast, offsetMin = 10)

        if (isWeekend) {
            // ---- 周末日程：休闲为主 ----
            val gym = LocalTime.of(9, 30).plusMinutes(Random.nextInt(0, 60).toLong())
            addEvent(events, date, weekday, "去健身房/晨练", TYPE_FUN, gym, offsetMin = 10)
            val lunch = LocalTime.of(12, 0).plusMinutes(Random.nextInt(0, 45).toLong())
            addEvent(events, date, weekday, "午餐", TYPE_MEAL, lunch, offsetMin = 15)
            val rest = lunch.plusHours(2).plusMinutes(Random.nextInt(0, 30).toLong())
            addEvent(events, date, weekday, "在家休息/追剧", TYPE_REST, rest, offsetMin = 20)
            val funTime = rest.plusHours(3).plusMinutes(Random.nextInt(0, 60).toLong())
            addEvent(events, date, weekday, if (Random.nextBoolean()) "和朋友聚会" else "打游戏/看番", TYPE_FUN, funTime, offsetMin = 20)
            val dinner = LocalTime.of(18, 30).plusMinutes(Random.nextInt(0, 60).toLong())
            addEvent(events, date, weekday, "晚餐", TYPE_MEAL, dinner, offsetMin = 15)
            val evening = dinner.plusHours(2).plusMinutes(Random.nextInt(0, 30).toLong())
            addEvent(events, date, weekday, "散步/放松", TYPE_REST, evening, offsetMin = 15)
        } else {
            // ---- 工作日日程：通勤+工作为主 ----
            // 检查硬性约束（如周三早课）
            val hardWake = findHardConstraint(profile, weekday, "起床")
            val effectiveWake = hardWake ?: wake
            if (hardWake != null) {
                // 已有起床事件，修正其时间
                val idx = events.indexOfFirst { it.type == TYPE_WAKE }
                if (idx >= 0) {
                    events[idx] = events[idx].copy(
                        actualTime = formatTime(hardWake),
                        windowStart = formatTime(hardWake.minusMinutes(3)),
                        windowEnd = formatTime(hardWake.plusMinutes(3)),
                        note = "硬性约束"
                    )
                }
                // 早餐相应顺延
                addEvent(events, date, weekday, "早餐", TYPE_MEAL, hardWake.plusMinutes(35), offsetMin = 10)
            }

            val commute = commuteBase.plusMinutes(Random.nextLong(-5, 11))
            addEvent(events, date, weekday, "出门通勤", TYPE_OUT, commute, offsetMin = 5)

            val workStart = workStartBase.plusMinutes(Random.nextLong(-5, 11))
            addEvent(events, date, weekday, "开始工作/学习", TYPE_WORK, workStart, offsetMin = 5)

            val lunch = LocalTime.of(12, 0).plusMinutes(Random.nextLong(-10, 21))
            addEvent(events, date, weekday, "午餐", TYPE_MEAL, lunch, offsetMin = 15)

            val workEnd = workEndBase.plusMinutes(Random.nextLong(-10, 11))
            addEvent(events, date, weekday, "结束工作/学习", TYPE_WORK, workEnd, offsetMin = 5)

            val commuteHome = workEnd.plusMinutes(30 + Random.nextLong(0, 31))
            addEvent(events, date, weekday, "回家路上", TYPE_OUT, commuteHome, offsetMin = 5)

            val dinner = commuteHome.plusMinutes(30 + Random.nextLong(0, 41))
            addEvent(events, date, weekday, "晚餐", TYPE_MEAL, dinner, offsetMin = 15)

            val evening = dinner.plusHours(2).plusMinutes(Random.nextLong(0, 31))
            addEvent(events, date, weekday, "晚上放松", TYPE_REST, evening, offsetMin = 20)

            // 工作日傍晚小概率插入"临时加班/开会"突发事件
            if (Random.nextDouble() < 0.3) {
                val overtime = workEnd.plusMinutes(45 + Random.nextLong(0, 60))
                addEvent(events, date, weekday, "临时加班/开会", TYPE_SURPRISE, overtime, offsetMin = 5, isSurprise = true)
            }
        }

        // 睡觉（周末推迟）
        val sleep = if (isWeekend) sleepBase.plusHours(1).plusMinutes(Random.nextLong(0, 30))
        else sleepBase
        addEvent(events, date, weekday, "睡觉", TYPE_SLEEP, sleep, offsetMin = 30)

        // 周五晚/周末 偶尔插入社交突发事件
        if (weekday == 5 && Random.nextDouble() < 0.4) {
            val party = LocalTime.of(19, 0).plusMinutes(Random.nextLong(0, 90))
            addEvent(events, date, weekday, "朋友突然约饭", TYPE_SURPRISE, party, offsetMin = 15, isSurprise = true)
        } else if (isWeekend && Random.nextDouble() < 0.35) {
            val surprise = LocalTime.of(14, 0).plusMinutes(Random.nextLong(0, 240))
            addEvent(events, date, weekday, if (Random.nextBoolean()) "临时出门办事" else "突然想出去走走", TYPE_SURPRISE, surprise, offsetMin = 20, isSurprise = true)
        }

        return events.sortedBy { it.windowStart }
    }

    /** 将日程列表中的突发事件剔除（用于"每日最多1-2次突发"的统计） */
    fun countSurprises(events: List<ScheduleEventEntity>): Int =
        events.count { it.type == TYPE_SURPRISE }

    private fun addEvent(
        list: MutableList<ScheduleEventEntity>,
        date: LocalDate,
        weekday: Int,
        title: String,
        type: String,
        base: LocalTime,
        offsetMin: Int,
        isSurprise: Boolean = false
    ) {
        // 随机偏移：-offsetMin ~ +offsetMin
        val offset = Random.nextLong(-offsetMin.toLong(), offsetMin.toLong() + 1)
        val actual = base.plusMinutes(offset)
        list.add(
            ScheduleEventEntity(
                date = date.toString(),
                weekday = weekday,
                title = title,
                type = type,
                baseTime = formatTime(base),
                actualTime = formatTime(actual),
                windowStart = formatTime(actual.minusMinutes(offsetMin.toLong())),
                windowEnd = formatTime(actual.plusMinutes(offsetMin.toLong())),
                isTriggered = false,
                isManual = false,
                note = if (isSurprise) "突发事件" else ""
            )
        )
    }

    /** 查找硬性约束（JSON: [{"day":3,"time":"06:40","title":"早课起床"}]） */
    private fun findHardConstraint(
        profile: UserProfileEntity,
        weekday: Int,
        keyword: String
    ): LocalTime? {
        return try {
            val arr = JSONArray(profile.hardConstraints.ifEmpty { "[]" })
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (obj.optInt("day", -1) == weekday &&
                    obj.optString("title", "").contains(keyword)
                ) {
                    return parseTime(obj.optString("time", ""))
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun parseTime(s: String, fallback: String = "07:00"): LocalTime {
        return try {
            LocalTime.parse(s.ifBlank { fallback })
        } catch (e: Exception) {
            LocalTime.parse(fallback)
        }
    }

    private fun formatTime(t: LocalTime): String = t.format(TIME_FMT)
}