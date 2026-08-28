package com.mgxd.mirror.data.db.entity

/**
 * 用户画像实体。
 * 存储通过学习逐步积累的"数字镜像"特征。单例行（id=1）。
 */
data class UserProfileEntity(
    val id: Long = 1L,

    // ---- 基础身份 ----
    var identity: String = "",          // 学生 / 上班族 / 自由职业 / 其他
    var nickname: String = "",          // 用户对"自己"的称呼

    // ---- 作息 ----
    var wakeTime: String = "07:00",     // 起床基准时间
    var sleepTime: String = "23:00",    // 睡觉基准时间
    var commuteTime: String = "08:30",  // 出门通勤基准时间
    var workStart: String = "09:00",    // 工作/学习开始
    var workEnd: String = "18:00",      // 工作/学习结束

    // ---- 性格与语言风格 ----
    var personality: String = "",       // 如：内向、理性
    var interests: String = "",         // 兴趣爱好
    var catchphrases: String = "",      // 口头禅，分号分隔
    var toneWords: String = "",         // 语气词，分号分隔
    var emojiStyle: String = "",        // 常用表情
    var decisionStyle: String = "",     // 决策习惯
    var emotionPattern: String = "",    // 情绪反应模式

    // ---- 硬性约束（JSON 数组），如 [{"day":3,"time":"06:40","title":"早课起床"}]
    var hardConstraints: String = "[]",

    // ---- 语言风格（从聊天中提取，供 AI 模仿）----
    var languageStyle: String = "",     // 如"常用哈哈哈、救命，喜欢用～，习惯不加句号"

    // ---- 心理学画像（JSON，存储大五人格等维度，由 PsychologyProfile 编解码）----
    var psychologyProfile: String = "{}",

    // ---- 主动消息程度：0=关闭 1=低 2=中 3=高 ----
    var activeLevel: Int = 2,

    // ---- 主动追问开关：1=开启 0=关闭 ----
    var askEnabled: Boolean = true,

    // ---- 学习状态 ----
    var learningProgress: Int = 0,      // 0-100
    var onboardingDone: Boolean = false,

    var updatedAt: Long = System.currentTimeMillis()
)