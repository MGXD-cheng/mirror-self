package com.mgxd.mirror.domain

import com.mgxd.mirror.data.db.entity.UserProfileEntity
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime

/**
 * 画像学习分析器（本地规则引擎兜底）。
 *
 * 当 AI 分析不可用时，通过关键词/模式匹配从用户消息中提取：
 * - 口头禅（高频重复短语，带频次，可被用户纠正）
 * - 语气词（哈哈、嗯、啊、吧...）
 * - 表情符号（😀😭👍...）
 * - 作息线索（"我一般7点起"、"12点睡"）
 * 并累计提升学习进度。
 */
object ProfileAnalyzer {

    // ---- 口头禅候选库：只从这些常见口头禅中学习，避免误提取 ----
    private val CATCHPHRASE_POOL = listOf(
        "绝了", "离谱", "麻了", "牛啊", "牛批", "无语", "救命", "笑死",
        "yyds", "栓Q", "真的会谢", "蚌埠住了", "谁懂", "家人们",
        "无所谓", "随便吧", "都行", "看情况", "问题不大", "差不多得了",
        "搞钱", "冲鸭", "躺平", "摆烂", "摸鱼", "内卷", "卷不动了",
        "真的假的", "好家伙", "怎么说", "离谱他妈给离谱开门", "fine",
        "没事没事", "算了吧", "完蛋", "凉了", "稳了", "冲了", "干了",
        "牛逼", "哈哈", "嘿嘿", "哎哟", "哇塞", "天呐", "我滴妈",
        "佛系", "随缘", "emmm", "emm", "hmm", "啧", "切"
    )

    // ---- 纠正信号：用户否定/纠正某个口头禅时的关键词模式 ----
    private val CORRECTION_PATTERNS = listOf(
        Regex("(才不|才没|别|少|不许|不要|不(?:爱|想|用|会|是|说))\\s*(?:说|用|讲|挂|带)?\\s*[“\"『]?([\\u4e00-\\u9fa5A-Za-z]{2,6})[”\"』]?"),
        Regex("(谁|没人|别)\\s*(?:会|说|用|讲)\\s*[“\"『]?([\\u4e00-\\u9fa5A-Za-z]{2,6})[”\"』]?"),
        Regex("(别学|别模仿|别说|少说|不要学)\\s*[“\"『]?([\\u4e00-\\u9fa5A-Za-z]{2,6})[”\"』]?")
    )

    /** 口头禅记录：文本 + 频次 + 使用时机提示 */
    data class Catchphrase(val text: String, var count: Int = 1, val hint: String = "")

    /** 从一条用户消息中学习，返回画像变更描述（用于日志/展示） */
    fun learnFromMessage(profile: UserProfileEntity, message: String): String {
        val changes = mutableListOf<String>()

        // 1. 作息线索：我(一般|通常|每天)(X点|X:XX)(起|睡)
        val wakeRegex = Regex("(?:一般|通常|每天|平时)?[我咱]?\\s*(\\d{1,2})[点时:]\\s*([0-5]?\\d)?\\s*(?:就)?(?:要)?(?:起床|起|醒)")
        wakeRegex.find(message)?.let { m ->
            val hour = m.groupValues[1].toIntOrNull() ?: return@let
            val minute = m.groupValues[2].ifBlank { "0" }.toIntOrNull() ?: 0
            if (hour in 4..12) {
                profile.wakeTime = formatTime(hour, minute)
                changes += "作息-起床"
            }
        }

        val sleepRegex = Regex("(?:一般|通常|每天|平时)?[我咱]?\\s*(\\d{1,2})[点时:]\\s*([0-5]?\\d)?\\s*(?:才)?(?:就)?(?:要)?(?:睡|睡觉|休息)")
        sleepRegex.find(message)?.let { m ->
            val hour = m.groupValues[1].toIntOrNull() ?: return@let
            val minute = m.groupValues[2].ifBlank { "0" }.toIntOrNull() ?: 0
            if (hour in 20..26) {
                val h = if (hour == 24) 0 else hour
                profile.sleepTime = formatTime(h, minute)
                changes += "作息-睡觉"
            }
        }

        // 2. 语气词 / 口头禅（含纠正：用户否定时移除错误提取的口头禅）
        if (detectToneWords(profile, message)) changes += "语气词"
        if (detectCatchphrase(profile, message)) changes += "口头禅"
        if (correctCatchphrase(profile, message)) changes += "口头禅-纠正"
        if (detectEmoji(profile, message)) changes += "表情风格"
        if (detectIdentity(profile, message)) changes += "身份"

        // 3. 性格线索
        if (detectPersonality(profile, message)) changes += "性格"
        if (detectInterests(profile, message)) changes += "兴趣"

        // 4. 语言风格（拟人化专项）
        if (detectLanguageStyle(profile, message)) changes += "语言风格"

        // 5. 心理学特征（大五人格/决策/情绪调节/社交/价值观/压力/依恋/幽默/完美主义）
        val psychChanged = detectPsychology(profile, message)
        if (psychChanged > 0) changes += "心理学×$psychChanged"

        // 6. 更新学习进度（每次最多 +3，防止刷屏暴涨；心理学维度每新识别一个 +2）
        if (changes.isNotEmpty()) {
            val gain = (changes.size * 1.5).toInt().coerceIn(1, 3) + psychChanged * 2
            profile.learningProgress = (profile.learningProgress + gain).coerceAtMost(100)
            profile.updatedAt = System.currentTimeMillis()
        }

        return changes.joinToString("、")
    }

    /** 根据关键词触发画像变更（用于 onboarding 基础测评） */
    fun applyOnboarding(
        profile: UserProfileEntity,
        identity: String,
        wake: String,
        sleep: String,
        commute: String,
        workStart: String,
        workEnd: String,
        personality: String,
        interests: String,
        catchphrases: String,
        toneWords: String,
        psychologyJson: String = "{}"
    ) {
        profile.identity = identity
        // 起床/睡觉为核心作息，必填；其余为选填（空串表示未填写）
        profile.wakeTime = normalizeTime(wake, "07:00")
        profile.sleepTime = normalizeTime(sleep, "23:00")
        profile.commuteTime = normalizeTime(commute, "", fallbackBlank = true)
        profile.workStart = normalizeTime(workStart, "09:00")
        profile.workEnd = normalizeTime(workEnd, "18:00")
        profile.personality = personality
        profile.interests = interests
        profile.catchphrases = catchphrases
        profile.toneWords = toneWords
        // 心理学画像（onboarding 自然对话采集结果，缺省保持原样）
        if (psychologyJson.isNotBlank() && psychologyJson != "{}") {
            profile.psychologyProfile = psychologyJson
        }
        profile.onboardingDone = true
        // 基础测评完成提升 20% + 心理学维度每填一个 +2%（上限 +15%）
        val psychCount = PsychologyProfile.fromJson(profile.psychologyProfile).filledCount()
        val boost = (psychCount * 2).coerceAtMost(15)
        profile.learningProgress = (20 + boost).coerceAtMost(100)
        profile.updatedAt = System.currentTimeMillis()
    }

    /**
     * 语言风格专项提取：从消息中统计高频语气词、网络用语、标点习惯，
     * 汇总成一句话写入 languageStyle，供 AI 直接模仿。
     */
    private fun detectLanguageStyle(profile: UserProfileEntity, msg: String): Boolean {
        val hints = mutableListOf<String>()

        // 高频口头式语气：哈哈哈 / hhh / 救命 / 笑死 / 真的假的 等
        val netSpeak = listOf(
            "哈哈哈", "哈哈", "hhh", "救命", "笑死", "真的假的", "yyds",
            "绝了", "离谱", "麻了", "无语", "尊嘟假嘟", "栓Q", "谁懂", "家人们",
            "问题不大", "差不多得了", "摆烂", "躺平", "摸鱼", "内卷", "搞钱", "冲鸭"
        )
        val hits = netSpeak.filter { msg.contains(it) }
        if (hits.isNotEmpty()) hints += "常用${hits.take(4).joinToString("、")}"

        // 标点习惯：波浪号 / 感叹号连用 / 省略号 / 句末无标点
        if (msg.contains("~") || msg.contains("～")) hints += "喜欢用波浪号～"
        if (msg.contains("!!") || msg.contains("！！")) hints += "激动时连用感叹号"
        if (msg.contains("…") || msg.contains("...")) hints += "常用省略号"
        val sentenceEnders = listOf("。", "！", "？", "~", "～", "…", "!", "?", ".", " ")
        if (msg.isNotBlank() && sentenceEnders.none { msg.endsWith(it) }) {
            hints += "习惯句末不加标点"
        }
        // emoji
        val emoji = Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]|[\u2600-\u27BF]").findAll(msg).map { it.value }.toList()
        if (emoji.isNotEmpty()) hints += "爱用${emoji.take(2).joinToString("")}"

        if (hints.isEmpty()) return false
        val combined = hints.distinct().joinToString("；")
        if (profile.languageStyle.isBlank()) {
            profile.languageStyle = combined
        } else {
            // 合并但不重复
            val existing = profile.languageStyle.split("；").toMutableSet()
            existing.addAll(hints)
            profile.languageStyle = existing.joinToString("；")
        }
        return true
    }

    /**
     * 心理学特征提取：通过关键词规则识别 9 个维度的特征，更新 psychologyProfile。
     * @return 本次新增识别的维度数量
     */
    private fun detectPsychology(profile: UserProfileEntity, msg: String): Int {
        val psych = PsychologyProfile.fromJson(profile.psychologyProfile)
        var added = 0

        // ---- 大五人格（外向性/尽责性/神经质/开放性/宜人性）----
        val bigFiveHints = mutableListOf<String>()
        when {
            listOf("社恐", "不想社交", "喜欢独处", "宅", "i人", "不爱出门", "懒得社交").any { msg.contains(it) } ->
                bigFiveHints += "外向性低，偏好独处，社交后需要充电"
            listOf("喜欢热闹", "爱社交", "朋友多", "喜欢聚会", "e人", "组局").any { msg.contains(it) } ->
                bigFiveHints += "外向性高，喜欢与人互动"
        }
        when {
            listOf("拖延", "拖到最后", "deadline", "赶工", "拖延症").any { msg.contains(it) } ->
                bigFiveHints += "尽责性偏低，容易拖延"
            listOf("提前准备", "计划好", "列清单", "按计划", "自律").any { msg.contains(it) } ->
                bigFiveHints += "尽责性高，做事有计划"
        }
        when {
            listOf("焦虑", "emo", "紧张", "失眠", "想太多", "容易慌", "内耗").any { msg.contains(it) } ->
                bigFiveHints += "神经质偏高，情绪波动较大"
        }
        when {
            listOf("好奇", "想试试", "新鲜", "探索", "尝新").any { msg.contains(it) } ->
                bigFiveHints += "开放性高，愿意尝试新鲜事物"
        }
        when {
            listOf("懒得吵", "算了", "都行", "好说话", "不想计较").any { msg.contains(it) } ->
                bigFiveHints += "宜人性较高，待人随和"
        }
        if (bigFiveHints.isNotEmpty() && psych.bigFive.isBlank()) {
            psych.bigFive = bigFiveHints.distinct().joinToString("，")
            added++
        }

        // ---- 决策风格 ----
        if (psych.decisionStyle.isBlank()) {
            when {
                listOf("纠结", "选择困难", "犹豫", "想半天", "做不了决定").any { msg.contains(it) } -> {
                    psych.decisionStyle = "犹豫型，做决定要纠结很久，但重大选择会提前调研"
                    added++
                }
                listOf("秒决定", "不想太多", "直接冲", "果断", "凭感觉").any { msg.contains(it) } -> {
                    psych.decisionStyle = "直觉冲动型，凭感觉快速决定"
                    added++
                }
            }
        }

        // ---- 情绪调节 ----
        if (psych.emotionRegulation.isBlank()) {
            when {
                listOf("自己消化", "不想说", "憋着", "一个人待着").any { msg.contains(it) } -> {
                    psych.emotionRegulation = "倾向于自己消化，不太主动倾诉"
                    added++
                }
                listOf("找人聊", "倾诉", "吐槽", "跟朋友说").any { msg.contains(it) } -> {
                    psych.emotionRegulation = "喜欢找人倾诉，说出来就好"
                    added++
                }
                listOf("听歌", "运动", "打游戏", "吃东西", "转移").any { msg.contains(it) } -> {
                    psych.emotionRegulation = "用转移注意力的方式调节（听歌/运动/游戏等）"
                    added++
                }
            }
        }

        // ---- 社交偏好 ----
        if (psych.socialPreference.isBlank()) {
            when {
                listOf("小圈子", "几个好朋友", "深聊", "不想认识新朋友").any { msg.contains(it) } -> {
                    psych.socialPreference = "小圈子深交，不喜欢无意义社交"
                    added++
                }
                listOf("一大群人", "热闹", "认识新朋友", "聚会").any { msg.contains(it) } -> {
                    psych.socialPreference = "喜欢热闹，享受多人社交"
                    added++
                }
            }
        }

        // ---- 价值观 ----
        if (psych.values.isBlank()) {
            when {
                listOf("自由", "不想被管", "随性").any { msg.contains(it) } -> {
                    psych.values = "看重自由与个人空间"
                    added++
                }
                listOf("家人", "家庭", "爸妈", "亲情").any { msg.contains(it) } -> {
                    psych.values = "看重家庭与亲情"
                    added++
                }
                listOf("搞钱", "赚钱", "事业", "升职", "成就").any { msg.contains(it) } -> {
                    psych.values = "看重事业与成就"
                    added++
                }
            }
        }

        // ---- 压力应对 ----
        if (psych.stressCoping.isBlank()) {
            when {
                listOf("提前准备", "先规划", "立刻处理").any { msg.contains(it) } -> {
                    psych.stressCoping = "积极解决型，压力来了立刻行动"
                    added++
                }
                listOf("拖到最后", "最后冲刺", "先玩再说").any { msg.contains(it) } -> {
                    psych.stressCoping = "拖延型，deadline 前冲刺"
                    added++
                }
                listOf("先焦虑", "先慌", "焦虑再行动").any { msg.contains(it) } -> {
                    psych.stressCoping = "先焦虑后行动，偶尔拖延但最后能完成"
                    added++
                }
            }
        }

        // ---- 依恋风格 ----
        if (psych.attachmentStyle.isBlank()) {
            when {
                listOf("主动和好", "先道歉", "主动沟通").any { msg.contains(it) } -> {
                    psych.attachmentStyle = "安全型，冲突后愿意主动修复"
                    added++
                }
                listOf("等对方先", "不敢主动", "怕被拒绝").any { msg.contains(it) } -> {
                    psych.attachmentStyle = "回避/焦虑型，冲突后倾向等对方先开口"
                    added++
                }
            }
        }

        // ---- 幽默风格 ----
        if (psych.humorStyle.isBlank()) {
            when {
                listOf("自嘲", "自黑").any { msg.contains(it) } -> {
                    psych.humorStyle = "以自嘲为主"
                    added++
                }
                listOf("冷笑话", "冷场").any { msg.contains(it) } -> {
                    psych.humorStyle = "喜欢冷笑话"
                    added++
                }
                listOf("玩梗", "梗", "恶搞").any { msg.contains(it) } -> {
                    psych.humorStyle = "热衷玩梗与恶搞"
                    added++
                }
            }
        }

        // ---- 完美主义 ----
        if (psych.perfectionism.isBlank()) {
            when {
                listOf("必须", "一定要", "不能出错", "完美", "要求高").any { msg.contains(it) } -> {
                    psych.perfectionism = "偏高，对自己要求严苛"
                    added++
                }
                listOf("差不多就行", "能过就行", "无所谓").any { msg.contains(it) } -> {
                    psych.perfectionism = "偏低，差不多就行"
                    added++
                }
            }
        }

        if (added > 0) {
            profile.psychologyProfile = psych.toJson()
        }
        return added
    }

    // ---- 私有检测方法 ----

    private fun detectToneWords(profile: UserProfileEntity, msg: String): Boolean {
        val tones = listOf("哈哈", "嘿嘿", "嗯嗯", "啊", "吧", "呢", "啦", "嘛", "哦", "唉", "哇")
        val found = tones.filter { msg.contains(it) }
        if (found.isNotEmpty()) {
            val existing = profile.toneWords.split(";").map { it.trim() }.toMutableSet()
            existing.addAll(found)
            profile.toneWords = existing.joinToString(";")
            return true
        }
        return false
    }

    /**
     * 从消息中学习口头禅：只在候选库中匹配，命中则累加频次。
     * 存储格式为 JSON 数组：[{"t":"绝了","n":3,"hint":"感叹/吐槽时"}]
     */
    private fun detectCatchphrase(profile: UserProfileEntity, msg: String): Boolean {
        // 先从候选库中找出本条消息命中的口头禅
        val hit = CATCHPHRASE_POOL.firstOrNull { msg.contains(it) } ?: return false
        val list = parseCatchphrases(profile.catchphrases).toMutableList()
        val existing = list.firstOrNull { it.text == hit }
        if (existing != null) {
            existing.count++
        } else {
            list.add(Catchphrase(hit))
        }
        profile.catchphrases = encodeCatchphrases(list)
        return true
    }

    /**
     * 纠正机制：用户表达"我才不说XX/别学我说XX"时，从口头禅库中移除该短语。
     */
    private fun correctCatchphrase(profile: UserProfileEntity, msg: String): Boolean {
        val list = parseCatchphrases(profile.catchphrases).toMutableList()
        if (list.isEmpty()) return false
        var removed = false
        for (pattern in CORRECTION_PATTERNS) {
            pattern.findAll(msg).forEach { m ->
                val target = m.groupValues.getOrNull(2)?.trim() ?: return@forEach
                // 精确匹配或模糊匹配（目标短语是已存口头禅的子串/包含）
                val removedItem = list.filter { it.text == target || it.text.contains(target) || target.contains(it.text) }
                if (removedItem.isNotEmpty()) {
                    list.removeAll(removedItem)
                    removed = true
                }
            }
        }
        if (removed) {
            profile.catchphrases = encodeCatchphrases(list)
        }
        return removed
    }

    // ---- 口头禅 JSON 编解码（兼容旧的分号分隔格式） ----

    private fun parseCatchphrases(raw: String): List<Catchphrase> {
        if (raw.isBlank()) return emptyList()
        // 兼容旧格式：分号分隔（无频次信息）
        if (!raw.trimStart().startsWith("[")) {
            return raw.split(";").map { it.trim() }.filter { it.isNotEmpty() }.map { Catchphrase(it) }
        }
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                Catchphrase(
                    text = obj.optString("t", ""),
                    count = obj.optInt("n", 1),
                    hint = obj.optString("hint", "")
                )
            }.filter { it.text.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun encodeCatchphrases(list: List<Catchphrase>): String {
        val arr = JSONArray()
        for (c in list) {
            arr.put(JSONObject().apply {
                put("t", c.text)
                put("n", c.count)
                put("hint", c.hint)
            })
        }
        return arr.toString()
    }

    private fun detectEmoji(profile: UserProfileEntity, msg: String): Boolean {
        val emojiRegex = Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]|[\u2600-\u27BF]")
        val found = emojiRegex.findAll(msg).map { it.value }.toList()
        if (found.isNotEmpty()) {
            profile.emojiStyle = found.take(3).joinToString(" ")
            return true
        }
        return false
    }

    private fun detectIdentity(profile: UserProfileEntity, msg: String): Boolean {
        val map = mapOf(
            "学生" to listOf("上课", "考试", "作业", "老师", "同学", "自习", "高考"),
            "上班族" to listOf("上班", "加班", "开会", "老板", "同事", "工位", "通勤", "地铁"),
            "自由职业" to listOf("接单", "自由职业", "在家办公", "甲方", "项目")
        )
        for ((identity, keywords) in map) {
            if (keywords.any { msg.contains(it) }) {
                profile.identity = identity
                return true
            }
        }
        return false
    }

    private fun detectPersonality(profile: UserProfileEntity, msg: String): Boolean {
        val tags = mutableListOf<String>()
        if (msg.contains("懒") || msg.contains("不想动")) tags += "慵懒"
        if (msg.contains("纠结") || msg.contains("选择困难")) tags += "纠结"
        if (msg.contains("随便") || msg.contains("都行")) tags += "随和"
        if (msg.contains("冲") || msg.contains("直接")) tags += "果断"
        if (msg.contains("emo") || msg.contains("难过") || msg.contains("烦")) tags += "感性"
        if (tags.isNotEmpty() && !profile.personality.contains(tags.first())) {
            profile.personality = (profile.personality.split(",").map { it.trim() } + tags).distinct().joinToString(",")
            return true
        }
        return false
    }

    private fun detectInterests(profile: UserProfileEntity, msg: String): Boolean {
        val map = mapOf(
            "游戏" to listOf("游戏", "打排位", "开黑", "原神", "王者", "switch"),
            "音乐" to listOf("音乐", "听歌", "唱歌", "吉他", "乐队", "演唱会"),
            "电影" to listOf("电影", "剧", "番", "追剧", "b站", "综艺"),
            "运动" to listOf("跑步", "健身", "篮球", "羽毛球", "游泳", "打球"),
            "美食" to listOf("吃", "火锅", "奶茶", "外卖", "下厨", "做饭")
        )
        val existing = profile.interests.split(",").map { it.trim() }.toMutableSet()
        var changed = false
        for ((interest, keywords) in map) {
            if (keywords.any { msg.contains(it) }) {
                if (existing.add(interest)) changed = true
            }
        }
        if (changed) {
            profile.interests = existing.joinToString(",")
            return true
        }
        return false
    }

    private fun normalizeTime(s: String, fallback: String, fallbackBlank: Boolean = false): String {
        if (s.isBlank()) {
            return if (fallbackBlank) "" else fallback
        }
        return try {
            LocalTime.parse(s).toString().take(5)
        } catch (e: Exception) {
            fallback
        }
    }

    private fun formatTime(hour: Int, minute: Int): String {
        return "%02d:%02d".format(hour, minute)
    }
}