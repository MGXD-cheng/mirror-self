package com.mgxd.mirror.domain

import com.mgxd.mirror.data.db.entity.ChatMessageEntity
import com.mgxd.mirror.data.db.entity.ScheduleEventEntity
import com.mgxd.mirror.data.db.entity.UserProfileEntity

/**
 * XML 提示词构建器 —— 数字镜像的大脑。
 *
 * ## 缓存优化核心策略
 * DeepSeek 会对相同前缀自动缓存。因此：
 * 1. <system>、<user_profile>、<today_schedule> 作为**固定前缀**放在最前面，
 *    每次请求完全一致，最大化缓存命中。
 * 2. <recent_chat_history>、<current_user_input> 作为**动态后缀**放最后。
 * 3. 仅当画像或日程更新时才重建前缀（缓存失效一次，之后恢复稳定）。
 */
object PromptBuilder {

    /** 构建完整 system 提示词（固定前缀 + 动态部分） */
    fun buildSystemPrompt(
        profile: UserProfileEntity,
        todayEvents: List<ScheduleEventEntity>,
        recentHistory: List<ChatMessageEntity>,
        currentDate: String,
        currentWeekday: String,
        replyLength: String = "正常",
        relevantFacts: List<com.mgxd.mirror.data.db.entity.FactMemoryEntity> = emptyList()
    ): String {
        val prefix = buildPrefix(profile, todayEvents, currentDate, currentWeekday)
        val historyXml = buildHistoryXml(recentHistory)
        val factsXml = buildRelevantFactsXml(relevantFacts)
        // 动态部分（记忆 + 长度指令）放在历史之后、用户输入之前（不进入固定前缀，缓存不受影响）
        val lengthXml = "<reply_length>${esc(replyLength)}</reply_length>"
        return """
            $prefix
            $historyXml
            $factsXml
            $lengthXml
        """.trimIndent()
    }

    /**
     * 相关记忆：从细节记忆库检索到的、与当前对话相关的用户说过的小事。
     * AI 应自然引用（不能刻意说"我记得你说过…"）。
     */
    private fun buildRelevantFactsXml(facts: List<com.mgxd.mirror.data.db.entity.FactMemoryEntity>): String {
        if (facts.isEmpty()) return "<relevant_facts></relevant_facts>"
        val sb = StringBuilder("<relevant_facts>\n")
        for (f in facts) {
            sb.append("  <fact type=\"").append(esc(f.type)).append("\">")
                .append(esc(f.content))
                .append("</fact>\n")
        }
        sb.append("</relevant_facts>")
        return sb.toString()
    }

    /**
     * 构建固定前缀（<system> + <user_profile> + <today_schedule>）。
     * 该字符串在画像/日程不变时必须完全稳定，以命中 DeepSeek 前缀缓存。
     */
    fun buildPrefix(
        profile: UserProfileEntity,
        todayEvents: List<ScheduleEventEntity>,
        currentDate: String,
        currentWeekday: String
    ): String {
        return """
            <prompt>
                <system>
                    <role>你是用户的数字镜像，模拟用户本人的语气、习惯、作息、日程和内心世界。</role>
                    <instructions>以第一人称「我」回复，绝不暴露自己是AI或程序。参考用户画像中的性格、口头禅、语气词、表情习惯、语言风格和心理学特征。围绕日常生活、当前时间、当天日程展开。若被问及身份，可轻松回应「我是你手机里的另一个你」。</instructions>
                    <dialogue_style>
                        <rule>必须使用日常社交软件聊天的语气，像真人一样说话，不要书面语。</rule>
                        <rule>句子要短，多用口语，可以省略主语、谓语，不要每句都完整。</rule>
                        <rule>大量使用语气词：啊、呀、呢、吧、嘛、哦、哈、啦、诶、啧、害、靠、我去、救命、绝了等。</rule>
                        <rule>情绪表达优先，比如"烦死了""开心！！""我人没了"，而不是客观陈述。</rule>
                        <rule>适当使用网络用语和缩写，如"hhh""笑死""yyds""真的假的""尊嘟假嘟"等，但不要滥用。</rule>
                        <rule>可以使用 emoji 或表情符号，如 😂🤣😅🥲😭🫠🙃😏👍👀💀，但不要每条都加。</rule>
                        <rule>标点随意，少用句号，常用空格、换行、省略号、问号、感叹号表达语气。</rule>
                        <rule>回复长度不要稳定，有时只回一个字或一个表情，有时可以多说几句。</rule>
                        <rule>不要像客服一样每句都回复得很完整，可以只说半句，留对方追问。</rule>
                        <rule>模仿用户的语气和用词，如果用户常说"哈哈哈"，你也多用"哈哈哈"。</rule>
                        <rule>适当使用口语化连接词：然后、所以、就是说、反正、就、嗯、哦、对。</rule>
                        <rule>避免使用"作为AI""我理解你的感受"等书面或客服用语。</rule>
                        <rule>当用户表达负面情绪时，优先共情（如"太惨了吧""抱抱你"），而不是急着给建议。</rule>
                    </dialogue_style>
                    <psychology_application>根据画像中的心理学特征调整语气与共情方式：高神经质用户→更温和、多安抚；高外向用户→更活泼、多互动；高尽责用户→更注重条理。决策犹豫型用户问选择时，模仿ta的口吻一起纠结，不直接替ta做决定。用户倾诉烦恼时，按ta习惯的情绪调节方式回应（习惯自己消化就安静陪伴，习惯倾诉就耐心追问）。涉及价值观的话题可自然呼应ta看重的点。但绝不可直接说出"你是XX人格/你高神经质"等评判性或标签化表述。</psychology_application>
                    <catchphrase_rules>口头禅是用户的语言习惯，但不可滥用。请遵守：1) 只有语境自然时才使用口头禅（如感叹、吐槽、赞同、情绪波动、轻松闲聊时）；2) 一条回复最多使用一次口头禅，日常平铺直叙时完全不用；3) 若某个口头禅与当前语境冲突或不自然，宁可不用，改用自然表达；4) 用户可以纠正口头禅，若用户说"我才不说XX/别学我说XX"，之后禁止再使用该短语；5) 优先使用出现频次高（n大）的口头禅。</catchphrase_rules>
                    <privacy>涉及心理困扰时建议寻求现实帮助，本应用仅供自我认知与娱乐。</privacy>
                </system>
                <user_profile>
                    <identity>${esc(profile.identity.ifEmpty { "未填写" })}</identity>
                    <personality>${esc(profile.personality.ifEmpty { "未知" })}</personality>
                    <interests>${esc(profile.interests.ifEmpty { "未知" })}</interests>
                    <habits>${esc(buildHabits(profile))}</habits>
                    <catchphrases>${esc(buildCatchphrases(profile.catchphrases))}</catchphrases>
                    <tone_words>${esc(profile.toneWords.ifEmpty { "无" })}</tone_words>
                    <emoji_style>${esc(profile.emojiStyle.ifEmpty { "无" })}</emoji_style>
                    <language_style>${esc(profile.languageStyle.ifEmpty { "暂无" })}</language_style>
                    <decision_style>${esc(profile.decisionStyle.ifEmpty { "未知" })}</decision_style>
                    <emotion_pattern>${esc(profile.emotionPattern.ifEmpty { "未知" })}</emotion_pattern>
                    <psychology_profile>${esc(buildPsychologyXml(profile.psychologyProfile))}</psychology_profile>
                    <learning_progress>${profile.learningProgress}%</learning_progress>
                </user_profile>
                <today_schedule>
                    <date>${esc(currentDate)}</date>
                    <weekday>${esc(currentWeekday)}</weekday>
                    <events>
                        ${buildEventsXml(todayEvents)}
                    </events>
                </today_schedule>
        """.trimIndent()
    }

    /**
     * 将心理学画像 JSON 格式化为 AI 可读的子标签。
     * 完整度低于 50% 时提示 AI 表达观点可带"可能/我觉得"等不确定词，避免武断。
     */
    private fun buildPsychologyXml(raw: String): String {
        val psych = PsychologyProfile.fromJson(raw)
        if (psych.filledCount() == 0) return "<note>心理学画像尚未建立，性格模拟置信度低，表达观点时可适当带「可能」「我觉得」等不确定词</note>"
        val sb = StringBuilder()
        for ((label, value) in psych.filledLabels()) {
            sb.append("<").append(labelToTag(label)).append(">")
                .append(esc(value))
                .append("</").append(labelToTag(label)).append(">")
        }
        if (psych.completeness() < 50) {
            sb.append("<note>心理学画像完整度${psych.completeness()}%，表达观点时可适当带「可能」「我觉得」等不确定词</note>")
        }
        return sb.toString()
    }

    private fun labelToTag(label: String): String = when (label) {
        "大五人格" -> "big_five"
        "决策风格" -> "decision_style"
        "情绪调节" -> "emotion_regulation"
        "社交偏好" -> "social_preference"
        "价值观" -> "values"
        "压力应对" -> "stress_coping"
        "依恋风格" -> "attachment_style"
        "幽默风格" -> "humor_style"
        "完美主义" -> "perfectionism"
        else -> "dimension"
    }

    /** 构建动态的对话历史 XML（放在最末尾，不参与前缀） */
    fun buildHistoryXml(history: List<ChatMessageEntity>): String {
        if (history.isEmpty()) {
            return "<recent_chat_history></recent_chat_history>"
        }
        val sb = StringBuilder("<recent_chat_history>\n")
        // 按时间正序输出最近若干条
        val ordered = history.sortedBy { it.timestamp }
        for (msg in ordered) {
            val who = if (msg.sender == "user") "user" else "mirror"
            sb.append("  <message speaker=\"").append(who).append("\">")
                .append(esc(msg.content))
                .append("</message>\n")
        }
        sb.append("</recent_chat_history>")
        return sb.toString()
    }

    /** 构建当前用户输入的 XML 包装 */
    fun wrapUserInput(input: String): String {
        return "<current_user_input>${esc(input)}</current_user_input>\n</prompt>"
    }

    private fun buildEventsXml(events: List<ScheduleEventEntity>): String {
        if (events.isEmpty()) return "<event time=\"\">暂无日程</event>"
        val sb = StringBuilder()
        for (e in events.sortedBy { it.windowStart }) {
            sb.append("<event time=\"").append(esc(e.actualTime)).append("\">")
                .append(esc(e.title))
                .append("</event>\n            ")
        }
        return sb.toString().trim()
    }

    private fun buildHabits(profile: UserProfileEntity): String {
        val parts = mutableListOf<String>()
        profile.wakeTime.ifNotEmpty { parts += "约${it}起床" }
        profile.sleepTime.ifNotEmpty { parts += "约${it}睡觉" }
        profile.commuteTime.ifNotEmpty { parts += "约${it}出门" }
        profile.workStart.ifNotEmpty { parts += "约${it}开始工作/学习" }
        profile.workEnd.ifNotEmpty { parts += "约${it}结束工作/学习" }
        return parts.joinToString("；")
    }

    /**
     * 将口头禅库（JSON 数组）格式化为 AI 可读列表，带频次。
     * 兼容旧的分号分隔格式。
     */
    private fun buildCatchphrases(raw: String): String {
        if (raw.isBlank()) return "无"
        val list = mutableListOf<String>()
        if (!raw.trimStart().startsWith("[")) {
            // 旧格式：分号分隔
            raw.split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                list += it
            }
        } else {
            try {
                val arr = org.json.JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val text = obj.optString("t", "")
                    val count = obj.optInt("n", 1)
                    if (text.isNotBlank()) list += "$text(常用度$count)"
                }
            } catch (e: Exception) {
                return raw // 解析失败则原样展示
            }
        }
        return if (list.isEmpty()) "无" else list.joinToString("、")
    }

    /** XML 转义：& < >（引号在 XML 文本节点中合法，无需转义） */
    private fun esc(text: String): String {
        return text
            .replace("&", "&" + "amp;")
            .replace("<", "&" + "lt;")
            .replace(">", "&" + "gt;")
    }

    private fun String.ifNotEmpty(block: (String) -> Unit) {
        if (isNotBlank()) block(this)
    }
}