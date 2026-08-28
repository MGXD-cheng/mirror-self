package com.mgxd.mirror.domain

import com.mgxd.mirror.data.db.entity.FactMemoryEntity
import com.mgxd.mirror.data.db.entity.UserProfileEntity

/**
 * 未知信息主动追问引擎：像朋友一样，听到陌生细节时自然追问，并记住答案。
 *
 * 实体类别：地点（学校/公司/家/常去餐厅/健身房）、人物（朋友/家人/同事）、
 * 物品（宠物/车）、活动（固定日程/兴趣班）、事件（计划/纪念日）。
 *
 * 追问规则：
 * - 本地关键词识别实体（高效，不每次调 AI）
 * - 记忆库已有该实体详细信息 → 不追问
 * - 24h 内已追问过 / 用户拒绝过 → 不追问
 * - 追问只生成一次，独立消息发送（先回复、后追问，拟人感更强）
 */
object EntityCuriosity {

    /** 实体类别定义：实体 key + 类别 + 触发关键词 */
    private data class EntityRule(val key: String, val type: String, val keywords: List<String>)

    private val RULES = listOf(
        EntityRule("学校", "地点", listOf("学校", "大学", "上课", "教学楼", "自习", "去学校", "学校有", "下课", "开学")),
        EntityRule("公司", "地点", listOf("公司", "上班", "单位", "去公司", "公司有", "工位", "下班")),
        EntityRule("家", "地点", listOf("回家", "家里", "住", "家附近", "搬去")),
        EntityRule("健身房", "活动", listOf("健身房", "健身", "撸铁")),
        EntityRule("朋友", "人物", listOf("朋友", "同学", "室友", "闺蜜", "兄弟", "发小")),
        EntityRule("同事", "人物", listOf("同事", "同组", "领导")),
        EntityRule("宠物", "物品", listOf("养了", "宠物", "我家猫", "我家狗", "猫主子", "狗子")),
        EntityRule("车", "物品", listOf("开车", "买车", "我的车", "提车")),
        EntityRule("活动", "活动", listOf("兴趣班", "每周三", "每周五", "固定去", "上钢琴课", "学吉他", "练琴"))
    )

    /** 待追问实体 */
    data class UnknownEntity(val key: String, val type: String)

    /**
     * 检测消息中出现的、且记忆库中尚无详细记录的未知实体。
     * @param facts 现有记忆（content 含实体关键词即视为"已记录"）
     */
    fun detectUnknownEntities(message: String, facts: List<FactMemoryEntity>): List<UnknownEntity> {
        val hits = mutableListOf<UnknownEntity>()
        for (rule in RULES) {
            val mentioned = rule.keywords.any { message.contains(it) }
            if (!mentioned) continue
            // 已记录：记忆库存在 content 含实体关键词的 active 记录
            val known = facts.any { f ->
                f.status == "active" &&
                    (f.content.contains(rule.key) || f.content.contains("${rule.key}在") || f.content.contains("${rule.key}叫"))
            }
            if (!known && hits.none { it.key == rule.key }) {
                hits += UnknownEntity(rule.key, rule.type)
            }
        }
        return hits.take(3)
    }

    /** 本地追问模板（AI 不可用时兜底；拟人化，多用"我忘了/我想记住"） */
    fun localQuestion(entity: UnknownEntity): String {
        return when (entity.type) {
            "人物" -> listOf(
                "你说的${entity.key}是谁呀？我认识吗",
                "${entity.key}是哪位？我好像没见过"
            ).random()
            "地点" -> listOf(
                "对了，你${entity.key}在哪来着？我有点忘了",
                "你${entity.key}在哪个区呀？想记住"
            ).random()
            "物品" -> listOf(
                "你养的${entity.key}是啥样子的呀？好奇",
                "诶，你的${entity.key}我好像没听你细说过"
            ).random()
            else -> listOf(
                "你${entity.key}一般什么时候去？我想记住",
                "你${entity.key}是固定时间吗？怕记岔了"
            ).random()
        }
    }

    /** 用户回答是否在拒绝（"不告诉你"等 → 标记不愿透露，不再追问） */
    fun isDecline(answer: String): Boolean {
        val signals = listOf(
            "不告诉你", "不想说", "没啥好说的", "别问", "秘密", "懒得说", "不说",
            "你管那么多", "干嘛问这个", "不重要", "随便", "不清楚", "忘了"
        )
        return signals.any { answer.contains(it) }
    }

    /**
     * 从用户回答中提取实体信息（本地规则）。
     * 返回要存入记忆库的内容；提取失败返回 null（不记录，但也不再追问已标记）。
     */
    fun extractAnswer(answer: String, entityKey: String, entityType: String): String? {
        val a = answer.trim()
        if (a.length < 2 || a.length > 60) return null
        // 去掉无意义回答
        if (a.contains("不知道") || a.contains("忘了") || a.contains("emmm") && a.length < 8) return null

        // 学校/公司等地点实体：提取机构名
        if (entityType == "地点") {
            val school = Regex("(.{2,20}?(?:大学|学院|中学|小学|学校|高中|职高|技校))").find(a)
            if (school != null) {
                return "${entityKey}：${school.groupValues[1]}"
            }
            val company = Regex("(.{2,20}?(?:公司|集团|银行|厂|工作室|事务所))").find(a)
            if (company != null) {
                return "公司：${company.groupValues[1]}"
            }
            // 位置信息
            val loc = Regex("(.{2,12}?(?:区|市|县|镇|路|街道))").find(a)
            if (loc != null) {
                return "${entityKey}在${loc.groupValues[1]}"
            }
        }
        // 人物：名字或关系
        if (entityType == "人物") {
            val name = Regex("([\\u4e00-\\u9fa5]{1,6}?)(?:吧|呀|啊|咯)?").find(a)
            if (name != null && name.groupValues[1].isNotBlank()) {
                return "${entityKey}：${name.groupValues[1]}"
            }
        }
        // 通用：直接记录回答
        return "${entityKey}：$a"
    }

    /** 画像联动：实体信息更新用户身份 */
    fun applyToProfile(profile: UserProfileEntity, entityKey: String, content: String) {
        when {
            entityKey == "学校" && profile.identity.isBlank() -> profile.identity = "学生"
            entityKey == "公司" && profile.identity.isBlank() -> profile.identity = "上班族"
            entityKey == "健身房" || entityKey == "活动" -> {
                val tags = profile.interests.split(",").map { it.trim() }.toMutableSet()
                if (tags.add("运动")) profile.interests = tags.joinToString(",")
            }
        }
        // 学习进度 +2（每记录一个新实体）
        profile.learningProgress = (profile.learningProgress + 2).coerceAtMost(100)
        profile.updatedAt = System.currentTimeMillis()
    }
}