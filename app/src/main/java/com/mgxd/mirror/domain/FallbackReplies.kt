package com.mgxd.mirror.domain

import com.mgxd.mirror.data.db.entity.UserProfileEntity
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * 本地兜底回复引擎（拟人化版）。
 * 当 AI API 调用失败（网络错误/超时/额度不足）时立即启用，
 * 保证聊天不中断。回复全部采用日常社交软件语气，随机选择避免重复。
 */
object FallbackReplies {

    private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /** 通用兜底：像真人一样自然接话，不再"客服腔" */
    private val genericReplies = listOf(
        "emmm……刚刚走神了，你再说一遍？",
        "网络卡了一下…你刚说啥？",
        "害，我这会儿脑子有点短路，你重说下？",
        "啊？没太听清，再说一次呗",
        "让我缓一下……刚才信号不太好",
        "啧，说到一半断片了，你继续",
        "哈哈稍等，我捋一下思路哈",
        "umm 你刚说的我记下了，然后呢？"
    )

    /** 用户带情绪的回复（共情优先） */
    private val emotionalReplies = listOf(
        "太惨了吧，抱抱你",
        "我去，这也太难了……",
        "心疼你，先歇会儿吧",
        "唉，懂你，这种时候真的烦",
        "抱抱！会好起来的",
        "烦死了……我陪你骂两句",
        "摸摸头，不慌，咱慢慢来"
    )

    /** 用户提问时的兜底 */
    private val questionReplies = listOf(
        "这个问题嘛……让我想想哈",
        "哈哈你问倒我了，稍等我琢磨下",
        "我猜你心里其实有答案了，对吧？",
        "嗯……这个我真得想想，回头跟你说",
        "害，这种问题我也常纠结"
    )

    /** 时间相关提醒 */
    private val timeAwareReplies = listOf(
        "这个点啦，记得起来活动活动",
        "快到饭点了，今天想吃啥？",
        "夜深了，早点休息别熬太狠"
    )

    /** 事件通知模板（第一人称，口语化） */
    private val eventTemplates = mapOf(
        ScheduleGenerator.TYPE_WAKE to listOf(
            "早，今天{time}醒的，比平时稍微{delta}了点……你起了吗？",
            "{time}了，闹钟响的时候我还想再眯五分钟哈哈。"
        ),
        ScheduleGenerator.TYPE_OUT to listOf(
            "我出门了，路上人好多。",
            "刚出门，今天地铁应该不会太挤吧……"
        ),
        ScheduleGenerator.TYPE_WORK to listOf(
            "开始搬砖了，今天任务有点多。",
            "开工！希望能早点搞完下班。"
        ),
        ScheduleGenerator.TYPE_MEAL to listOf(
            "到点吃饭了，今天想吃{meal}。",
            "干饭时间到！饿死我了。"
        ),
        ScheduleGenerator.TYPE_SLEEP to listOf(
            "不早了，我准备睡了，你也早点休息。",
            "{time}了，该睡觉了，晚安~"
        ),
        ScheduleGenerator.TYPE_FUN to listOf(
            "难得有空，好好放松一下~",
            "今天想干点自己喜欢的事。"
        ),
        ScheduleGenerator.TYPE_REST to listOf(
            "歇一会儿，放空一下脑子。",
            "现在是我的躺平时间，谁都别打扰哈哈。"
        ),
        ScheduleGenerator.TYPE_SURPRISE to listOf(
            "刚接到通知，临时有事，计划泡汤了……",
            "突然冒出个事要处理，唉，人生无常。"
        )
    )

    private val meals = listOf("火锅", "面条", "盖饭", "沙拉", "饺子", "烧烤", "家常菜", "麻辣烫")

    /**
     * 生成一条带用户语气的兜底回复（拟人化）。
     */
    fun reply(profile: UserProfileEntity, userInput: String, hour: Int): String {
        // 用户情绪强烈时优先共情
        val hasEmotion = hasEmotionalContext(userInput)
        val isQuestion = userInput.contains("?") || userInput.contains("？") || userInput.contains("吗")
        val base = when {
            hasEmotion && Random.nextInt(100) < 60 -> emotionalReplies.random()
            isQuestion -> questionReplies.random()
            hour >= 22 || hour <= 5 -> timeAwareReplies[2]
            hour in 11..13 || hour in 17..19 -> timeAwareReplies[1]
            hour in 7..9 -> timeAwareReplies[0]
            else -> genericReplies.random()
        }
        // 少量拼接口头禅（口语语境下自然使用）
        return if (!hasEmotion && Random.nextInt(100) < 25) {
            "$base ${weightedTone(profile)}"
        } else {
            base
        }
    }

    /** 生成事件通知文案（本地模板兜底，不使用 AI） */
    fun eventNotification(type: String, actualTime: String, note: String): String {
        val template = eventTemplates[type]?.random() ?: genericReplies.random()
        val now = LocalTime.now().format(timeFmt)
        return template
            .replace("{time}", actualTime.ifBlank { now })
            .replace("{meal}", meals.random())
            .replace("{delta}", if (Random.nextBoolean()) "早" else "晚")
    }

    /** 判断用户输入是否带情绪/感叹语境 */
    private fun hasEmotionalContext(input: String): Boolean {
        val signals = listOf("！", "!", "哈哈", "唉", "烦", "爽", "绝", "服", "气死",
            "无语", "笑死", "救命", "牛", "太", "好耶", "完蛋", "麻了", "无语子", "yyds",
            "冲", "加油", "emo", "难", "累", "饿", "困", "开心", "难过", "离谱", "震惊")
        return signals.any { input.contains(it) }
    }

    /**
     * 按频次加权随机抽取口头禅（JSON 格式），并兼容旧分号格式。
     */
    private fun weightedTone(profile: UserProfileEntity): String {
        val phrases = parseCatchphrases(profile.catchphrases)
        if (phrases.isEmpty()) return ""
        val pool = mutableListOf<String>()
        for ((text, count) in phrases) {
            repeat(count.coerceIn(1, 10)) { pool += text }
        }
        return pool.random()
    }

    /** 解析口头禅 JSON（兼容旧分号格式），返回 (文本, 频次) 列表 */
    private fun parseCatchphrases(raw: String): List<Pair<String, Int>> {
        if (raw.isBlank()) return emptyList()
        if (!raw.trimStart().startsWith("[")) {
            return raw.split(";").map { it.trim() }.filter { it.isNotEmpty() }.map { it to 1 }
        }
        return try {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val text = obj.optString("t", "")
                if (text.isBlank()) null else text to obj.optInt("n", 1)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}