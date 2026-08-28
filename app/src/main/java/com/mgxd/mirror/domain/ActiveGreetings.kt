package com.mgxd.mirror.domain

import com.mgxd.mirror.data.db.entity.UserProfileEntity
import kotlin.random.Random

/**
 * 主动问候引擎：生成"另一个我"自主发来的消息（AI 失败时的本地兜底）。
 * 按时间段 + 用户昵称/口头禅个性化，让主动消息更像真人分享而非通知。
 */
object ActiveGreetings {

    private val morning = listOf(
        "早呀，新的一天开始咯！",
        "早上好~今天也要元气满满哦。",
        "醒啦？我在想今天该干点啥，你也是吧。",
        "早安！窗外天气看着不错，心情也跟着好了。"
    )

    private val daytime = listOf(
        "忙里偷闲说一句：别忘了喝水哦。",
        "这会儿在忙啥呢？记得歇一歇。",
        "刚刚突然想到你，就来跟你说一声~",
        "上午过得好快，事情办得顺利吗？"
    )

    private val lunch = listOf(
        "午饭吃了没？别光顾着忙呀。",
        "到饭点了，今天想吃点啥？",
        "干饭时间到！饿不饿？"
    )

    private val afternoon = listOf(
        "下午好呀，忙完这阵记得放松下。",
        "下午容易犯困，起来走走呗。",
        "今天进展咋样？慢慢来，别急。",
        "歇口气，喝杯水，再战下半场。"
    )

    private val evening = listOf(
        "晚上好呀，今天过得怎么样？",
        "回来啦？辛苦了，好好歇会儿。",
        "傍晚了，天边颜色挺好看的，抬头看看。",
        "忙了一天，终于可以松口气了。"
    )

    private val night = listOf(
        "夜深了，该睡啦，别熬太晚。",
        "这么晚还不睡？小心明天起不来哦。",
        "晚安啦，明天见~",
        "睡前别刷太久手机，对眼睛不好。"
    )

    private val cares = listOf(
        "记得好好吃饭！",
        "多喝热水，认真脸。",
        "今天也辛苦啦。",
        "别给自己太大压力。",
        "遇到烦心事随时找我聊。",
        "明天会更好的，我信你。",
        "累了就休息，不用硬撑。"
    )

    /** 生成一条主动问候（40字左右，自然口语） */
    fun greeting(profile: UserProfileEntity, hour: Int): String {
        val base = when (hour) {
            in 5..9 -> morning.random()
            in 10..12 -> daytime.random()
            in 12..14 -> lunch.random()
            in 14..17 -> afternoon.random()
            in 17..22 -> evening.random()
            else -> night.random()
        }

        // 昵称个性化：偶尔以昵称呼唤，更像真人
        val nick = profile.nickname.ifBlank { "" }
        val text = if (nick.isNotBlank() && Random.nextBoolean()) "$nick，$base" else base

        // 约一半概率追加一句关心，避免句句都有显得刻意
        return if (Random.nextBoolean()) "$text ${cares.random()}" else text
    }
}
