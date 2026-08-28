package com.mgxd.mirror.data.repository

import android.content.Context
import com.mgxd.mirror.data.db.AppDatabase
import com.mgxd.mirror.data.db.entity.ApiConfigEntity
import com.mgxd.mirror.data.db.entity.ChatMessageEntity
import com.mgxd.mirror.data.db.entity.FactMemoryEntity
import com.mgxd.mirror.data.db.entity.ScheduleEventEntity
import com.mgxd.mirror.data.db.entity.UserProfileEntity
import com.mgxd.mirror.data.network.AiRepository
import com.mgxd.mirror.domain.ActiveGreetings
import com.mgxd.mirror.domain.ActiveMessenger
import com.mgxd.mirror.domain.EntityCuriosity
import com.mgxd.mirror.domain.FactExtractor
import com.mgxd.mirror.domain.FallbackReplies
import com.mgxd.mirror.domain.ProfileAnalyzer
import com.mgxd.mirror.domain.PromptBuilder
import com.mgxd.mirror.domain.ScheduleGenerator
import com.mgxd.mirror.util.CryptoManager
import com.mgxd.mirror.util.SensitiveDataMasker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 数据聚合仓库：统一协调数据库、AI 服务、画像学习、日程生成。
 */
class MirrorRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val aiRepository = AiRepository()

    private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val weekdayNames = listOf("", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")

    // ---------- 用户画像 ----------

    suspend fun getProfile(): UserProfileEntity {
        return db.userProfileDao.getProfile() ?: UserProfileEntity().also {
            db.userProfileDao.upsert(it)
        }
    }

    suspend fun saveProfile(profile: UserProfileEntity) {
        db.userProfileDao.upsert(profile)
    }

    suspend fun completeOnboarding(
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
        val profile = getProfile()
        ProfileAnalyzer.applyOnboarding(
            profile, identity, wake, sleep, commute, workStart, workEnd,
            personality, interests, catchphrases, toneWords, psychologyJson
        )
        db.userProfileDao.upsert(profile)
        // 测评完成后立即生成未来一周日程
        generateWeekSchedule(profile)
    }

    // ---------- 日程 ----------

    suspend fun getEventsForDate(date: LocalDate): List<ScheduleEventEntity> {
        return db.scheduleEventDao.getByDate(date.toString())
    }

    suspend fun getUpcomingEvents(limit: Int = 30): List<ScheduleEventEntity> {
        return db.scheduleEventDao.getUpcoming(LocalDate.now().toString(), limit)
    }

    /** 确保某天有日程；没有则生成。返回当天日程。 */
    suspend fun ensureScheduleForDate(date: LocalDate): List<ScheduleEventEntity> {
        val existing = db.scheduleEventDao.getByDate(date.toString())
        if (existing.isNotEmpty()) return existing
        val profile = getProfile()
        val events = ScheduleGenerator.generateForDate(profile, date)
        db.scheduleEventDao.insertAll(events)
        return events
    }

    /** 生成未来 N 天日程（幂等：已存在的日期跳过） */
    suspend fun generateWeekSchedule(profile: UserProfileEntity, days: Int = 7) {
        val today = LocalDate.now()
        for (i in 0 until days) {
            val date = today.plusDays(i.toLong())
            if (db.scheduleEventDao.getByDate(date.toString()).isEmpty()) {
                val events = ScheduleGenerator.generateForDate(profile, date)
                db.scheduleEventDao.insertAll(events)
            }
        }
    }

    suspend fun updateEvent(event: ScheduleEventEntity) {
        db.scheduleEventDao.update(event)
    }

    /** 重新生成指定日期日程（用户手动触发） */
    suspend fun regenerateDate(date: LocalDate) {
        db.scheduleEventDao.deleteByDate(date.toString())
        val profile = getProfile()
        val events = ScheduleGenerator.generateForDate(profile, date)
        db.scheduleEventDao.insertAll(events)
    }

    // ---------- 聊天 ----------

    suspend fun getRecentMessages(limit: Int = 50): List<ChatMessageEntity> {
        return db.chatMessageDao.getRecent(limit).sortedBy { it.timestamp }
    }

    suspend fun saveMessage(
        sender: String,
        content: String,
        timestamp: Long = System.currentTimeMillis()
    ): ChatMessageEntity {
        val msg = ChatMessageEntity(
            sender = sender,
            content = content,
            timestamp = timestamp,
            isSensitive = isSensitive(content)
        )
        db.chatMessageDao.insert(msg)
        db.chatMessageDao.trimOldMessages()
        return msg
    }

    /**
     * 随机决定本次回复的目标长度（短:中:长 = 3:5:2）。
     * 短=1-5字/一个表情；中=10-30字；长=30字以上。
     */
    private fun pickReplyLength(): String {
        val roll = kotlin.random.Random.nextInt(10)
        return when {
            roll < 3 -> "极短（一两个字或一个表情即可，不要扩展）"
            roll < 8 -> "简短（10-30字，两三句话）"
            else -> "稍长（30字以上，但仍是口语聊天，不是小作文）"
        }
    }

    /**
     * 将一段文本按句末标点/换行拆成多条消息（像真人逐句发送）。
     * 保留标点符号，过滤空白；无法拆分时整体返回。
     */
    private fun splitSentences(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return emptyList()
        val parts = trimmed
            .split(Regex("(?<=[。！？!?…\\n])"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        return parts.ifEmpty { listOf(trimmed) }
    }

    /**
     * 用户发消息 → 学习画像 → 构建 XML 提示词 → 调用 AI → 失败走本地兜底。
     * AI 回复会拆成逐句的独立消息，模拟真人聊天节奏。
     * 返回 AI（或兜底）回复原文。
     */
    suspend fun sendUserMessage(userInput: String): String = withContext(Dispatchers.IO) {
        // 0. 处理上一轮追问的答案（先于回复逻辑，让回答立即生效）
        val profile0 = getProfile()
        if (profile0.askEnabled) {
            processFollowUpAnswer(userInput, profile0)
        }

        // 1. 保存用户消息并学习画像 + 提取细节记忆
        val userMsg = saveMessage("user", userInput)
        val profile = getProfile()
        ProfileAnalyzer.learnFromMessage(profile, userInput)
        db.userProfileDao.upsert(profile)
        extractFactsFromUserMessage(userInput, userMsg.id)

        // 2. 构建提示词（含随机回复长度 + 相关记忆引用）
        val replyLength = pickReplyLength()
        val now = LocalDate.now()
        val events = ensureScheduleForDate(now)
        val recent = getRecentMessages(10)
        val relevantFacts = relevantFactsFor(userInput)
        val systemPrompt = PromptBuilder.buildSystemPrompt(
            profile = profile,
            todayEvents = events,
            recentHistory = recent.map { msg ->
                msg.copy(content = SensitiveDataMasker.mask(msg.content))
            },
            currentDate = now.format(dateFmt),
            currentWeekday = weekdayNames[now.dayOfWeek.value],
            replyLength = replyLength,
            relevantFacts = relevantFacts
        )
        val userXml = PromptBuilder.wrapUserInput(SensitiveDataMasker.mask(userInput))

        // 3. 调用 AI（失败则本地兜底）
        val reply = try {
            val config = getApiConfig()
            if (config.enableFallback && config.apiKeyEncrypted.isEmpty()) {
                throw IllegalStateException("API Key 未配置")
            }
            aiRepository.chat(config, systemPrompt, userXml)
        } catch (e: Exception) {
            FallbackReplies.reply(profile, userInput, LocalTime.now().hour)
        }

        // 4. 逐句保存，模拟真人发消息节奏（时间戳 +1ms 保证顺序稳定）
        val sentences = splitSentences(reply)
        var ts = System.currentTimeMillis()
        sentences.forEach { sentence ->
            saveMessage("mirror", sentence, ts)
            ts += 1
        }

        // 5. 追问未知实体（先回复、间隔几秒再追问，拟人感更强；最多 2 个）
        if (profile.askEnabled) {
            val questions = generateFollowUpQuestions(userInput)
            if (questions.isNotEmpty()) {
                delay(2500)
                questions.take(2).forEach { q ->
                    saveMessage("mirror", q, ++ts)
                }
            }
        }

        reply
    }

    // ---------- 未知信息追问 ----------

    /**
     * 处理上一轮追问的答案：提取实体信息 → 存记忆库 → 更新画像。
     * 用户拒绝回答则标记"不愿透露"，之后不再追问。
     */
    private suspend fun processFollowUpAnswer(answer: String, profile: UserProfileEntity) {
        if (!db.entityAskDao.hasPendingAsk()) return
        val asked = db.entityAskDao.lastAskedEntity() ?: return
        val (entityKey, entityType) = asked

        // 拒绝回答
        if (EntityCuriosity.isDecline(answer)) {
            db.entityAskDao.markDeclined(entityKey, entityType)
            return
        }

        val content = EntityCuriosity.extractAnswer(answer, entityKey, entityType) ?: run {
            db.entityAskDao.markDeclined(entityKey, entityType) // 答非所问/敷衍 → 不再追问
            return
        }

        // 存入记忆库（去重）
        db.factMemoryDao.upsertDedupe(
            FactMemoryEntity(
                type = entityType,
                content = content,
                importance = 3,
                eventDate = ""
            )
        )
        // 画像联动（身份/兴趣 + 学习进度 +2）
        val p = getProfile()
        EntityCuriosity.applyToProfile(p, entityKey, content)
        db.userProfileDao.upsert(p)

        // 标记已回答，不再追问该实体
        db.entityAskDao.markAnswered(entityKey)
    }

    /** 检测消息中的未知实体，生成拟人化追问（AI 优先，失败走本地模板） */
    private suspend fun generateFollowUpQuestions(userInput: String): List<String> {
        val facts = db.factMemoryDao.getActive(30)
        val unknowns = EntityCuriosity.detectUnknownEntities(userInput, facts)
        if (unknowns.isEmpty()) return emptyList()

        val questions = mutableListOf<String>()
        for (entity in unknowns) {
            // 24h 内追问过 / 用户拒绝过 → 跳过
            if (db.entityAskDao.wasAskedRecently(entity.key)) continue
            if (db.entityAskDao.wasDeclined(entity.key)) continue

            val question = try {
                val config = getApiConfig()
                if (config.enableFallback && config.apiKeyEncrypted.isEmpty()) {
                    throw IllegalStateException("API Key 未配置")
                }
                val prompt = "你是用户的数字镜像，用 ta 平时语气（口语化、像真人）自然追问一个细节。实体：${entity.key}（${entity.type}）。要求：第一人称、短句、像朋友随口问的、可用'我忘了''想确认''想记住'等理由，40字以内，不要像问卷，不要用引号。"
                val raw = aiRepository.chat(config, prompt, "（追问${entity.key}）")
                raw.trim().removePrefix("\"").removeSuffix("\"").trim().take(60)
            } catch (e: Exception) {
                EntityCuriosity.localQuestion(entity)
            }
            questions += question
            db.entityAskDao.markAsked(entity.key, entity.type)
        }
        return questions
    }

    /**
     * 主动社交消息：基于主动消息引擎评估当前是否适合主动开启话题。
     * 触发类型：记忆事件 > 日程关联 > 兴趣话题 > 回访 > 时段闲聊 > 随机分享。
     * 带频率控制（每天上限按 activeLevel、间隔≥1h、深夜限制、用户未回复暂停）。
     * 消息以普通聊天消息形式入库（非系统通知）。未触发返回 null。
     */
    suspend fun proactiveMessage(): String? = withContext(Dispatchers.IO) {
        val profile = getProfile()
        if (!profile.onboardingDone) return@withContext null

        val now = System.currentTimeMillis()
        val recent = getRecentMessages(50)
        val lastMirror = recent.filter { it.sender == "mirror" }.maxByOrNull { it.timestamp }
        val lastUser = recent.filter { it.sender == "user" }.maxByOrNull { it.timestamp }
        val lastMirrorAt = lastMirror?.timestamp ?: 0L
        val lastUserAt = lastUser?.timestamp ?: 0L

        // 当天主动消息计数（防超量）
        val todayStart = LocalDate.now().atStartOfDay().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val todayCount = recent.count { it.sender == "mirror" && it.timestamp >= todayStart }
        val dailyCap = when (profile.activeLevel) {
            1 -> 1
            3 -> 3
            else -> 2
        }
        if (todayCount >= dailyCap) return@withContext null

        // 随机分享每日最多 1 条
        val randomCount = recent.count {
            it.sender == "mirror" && it.timestamp >= todayStart &&
                (it.content.contains("刚看到一只猫") || it.content.contains("好适合睡觉") ||
                    it.content.contains("突然想喝奶茶") || it.content.contains("好好听") || it.content.contains("好蓝"))
        }

        val activeFacts = db.factMemoryDao.getActive(20)
        val upcomingFacts = db.factMemoryDao.getUpcomingEvents(10)
        val todayEvents = ensureScheduleForDate(LocalDate.now())

        val decision = ActiveMessenger.decide(
            profile = profile,
            facts = activeFacts,
            upcomingFacts = upcomingFacts,
            todayEvents = todayEvents,
            lastMirrorAt = lastMirrorAt,
            lastUserAt = lastUserAt,
            now = now
        ) ?: return@withContext null

        // 随机分享限流
        if (decision.type == ActiveMessenger.TriggerType.RANDOM && randomCount >= 1) return@withContext null

        // 生成消息（AI 优先，失败用本地模板）
        val hour = LocalTime.now().hour
        val message = try {
            val config = getApiConfig()
            if (config.enableFallback && config.apiKeyEncrypted.isEmpty()) {
                throw IllegalStateException("API Key 未配置")
            }
            val factDesc = decision.fact?.let { "ta之前说过：${it.content}" } ?: ""
            val eventDesc = decision.event?.let { "当前日程：${it.title} ${it.actualTime}" } ?: ""
            val prompt = "你是用户的数字镜像，用 ta 平时的语气（参考画像）主动开启话题。触发类型：${decision.type.label}。$factDesc $eventDesc 现在时间：${LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))}。要求：第一人称、口语化、40字以内、像真人随口说的、不要问太多问题、不要用引号、不要像系统通知。"
            val raw = aiRepository.chat(config, prompt, "（主动开启话题）")
            raw.trim().removePrefix("\"").removeSuffix("\"").trim().take(80)
        } catch (e: Exception) {
            ActiveMessenger.templateFor(decision.type, profile, decision.fact, decision.event, hour)
        }

        saveMessage("mirror", message)

        // 引用记忆后更新 lastUsedAt
        decision.fact?.let { db.factMemoryDao.markUsed(it.id) }
        // 事件类记忆当天提醒后标记完成，避免反复提醒
        if (decision.type == ActiveMessenger.TriggerType.MEMORY_EVENT && decision.fact?.eventDate == LocalDate.now().toString()) {
            db.factMemoryDao.markCompleted(decision.fact.id)
        }

        message
    }

    // ---------- 细节记忆 ----------

    /** 从用户消息中提取细节记忆（本地规则），去重入库 */
    suspend fun extractFactsFromUserMessage(userInput: String, sourceMsgId: Long) = withContext(Dispatchers.IO) {
        val facts = FactExtractor.extractLocal(userInput, LocalDate.now())
        for (f in facts) {
            db.factMemoryDao.upsertDedupe(
                com.mgxd.mirror.data.db.entity.FactMemoryEntity(
                    type = f.type,
                    content = f.content,
                    sourceMessageId = sourceMsgId,
                    importance = f.importance,
                    eventDate = f.eventDate
                )
            )
        }
    }

    /** 全部有效记忆（供 UI 展示） */
    suspend fun getFacts(limit: Int = 100): List<com.mgxd.mirror.data.db.entity.FactMemoryEntity> {
        return db.factMemoryDao.getAll(limit)
    }

    suspend fun deleteFact(id: Long) {
        db.factMemoryDao.delete(id)
    }

    suspend fun clearFacts() {
        db.factMemoryDao.clearAll()
    }

    /** 构建与当前对话相关的记忆（最多 4 条，供 AI 自然引用） */
    private suspend fun relevantFactsFor(userInput: String): List<com.mgxd.mirror.data.db.entity.FactMemoryEntity> {
        val all = db.factMemoryDao.getActive(30)
        if (all.isEmpty()) return emptyList()
        // 关键词命中优先，其次高重要度
        val keywords = listOf("吃", "睡", "玩", "游戏", "工作", "学", "考试", "朋友", "累", "烦", "计划", "去", "买", "减肥", "运动", "音乐", "电影")
        val matched = all.filter { f -> keywords.any { userInput.contains(it) && f.content.contains(it) } }
        val picked = (matched + all.sortedByDescending { it.importance })
            .distinctBy { it.id }
            .take(4)
        picked.forEach { db.factMemoryDao.markUsed(it.id) }
        return picked
    }

    // ---------- AI 配置 ----------

    suspend fun getApiConfig(): ApiConfigEntity {
        val config = db.apiConfigDao.getConfig() ?: ApiConfigEntity().also {
            db.apiConfigDao.upsert(it)
        }
        // 旧版本默认模型名迁移：deepseek-v4-flash 不是官方模型，会导致 API 400
        if (config.model == "deepseek-v4-flash") {
            config.model = "deepseek-chat"
            db.apiConfigDao.upsert(config)
        }
        // Base URL 归一化：把只填域名的旧配置自动补全接口路径（避免 404）
        val normalizedUrl = AiRepository.normalizeUrl(config.baseUrl)
        if (config.baseUrl != normalizedUrl) {
            config.baseUrl = normalizedUrl
            db.apiConfigDao.upsert(config)
        }
        return config
    }

    /** 测试 AI 连接（委托给网络层） */
    suspend fun testConnection(config: ApiConfigEntity): String {
        return aiRepository.testConnection(config)
    }

    suspend fun saveApiConfig(
        baseUrl: String,
        apiKey: String,
        model: String,
        maxTokens: Int,
        temperature: Float,
        enableFallback: Boolean,
        timeoutSeconds: Int
    ) {
        val config = getApiConfig()
        // Base URL 智能归一化：填域名也会自动补全 /chat/completions 路径
        config.baseUrl = AiRepository.normalizeUrl(baseUrl)
        // 仅当用户输入了新 Key 才更新；留空表示保持原 Key，避免误覆盖
        if (apiKey.isNotBlank()) {
            config.apiKeyEncrypted = CryptoManager.encrypt(apiKey)
        }
        config.model = model.ifBlank { "deepseek-chat" }
        config.maxTokens = maxTokens
        config.temperature = temperature
        config.enableFallback = enableFallback
        config.timeoutSeconds = timeoutSeconds
        db.apiConfigDao.upsert(config)
    }

    /** 检测文本是否包含敏感信息 */
    private fun isSensitive(text: String): Boolean {
        return SensitiveDataMasker.mask(text) != text
    }
}