package com.mgxd.mirror.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mgxd.mirror.ui.MainViewModel

/**
 * 新手引导：基础测评。
 * 以自然对话形式收集用户身份、作息、性格、兴趣、口头禅等，
 * 完成后学习进度提升至 20% 并生成未来一周日程。
 */
@Composable
fun OnboardingScreen(viewModel: MainViewModel) {
    var step by remember { mutableStateOf(0) }
    val totalSteps = 5

    // 表单状态
    var identity by remember { mutableStateOf("上班族") }
    var wake by remember { mutableStateOf("07:00") }
    var sleep by remember { mutableStateOf("23:00") }
    // 以下为选填：留空表示"没固定习惯"，日程生成时会合理推算
    var commute by remember { mutableStateOf("") }
    var workStart by remember { mutableStateOf("") }
    var workEnd by remember { mutableStateOf("") }
    var personality by remember { mutableStateOf("") }
    var mbti by remember { mutableStateOf("") }
    var interests by remember { mutableStateOf("") }
    var catchphrases by remember { mutableStateOf("") }
    var toneWords by remember { mutableStateOf("") }
    // 心理学自然对话（默认未选，跳过也不影响）
    var psych1 by remember { mutableStateOf("") }   // 周末出门/在家躺 → 外向性
    var psych2 by remember { mutableStateOf("") }   // 纠结程度 → 决策风格
    var psych3 by remember { mutableStateOf("") }   // 心情不好怎么办 → 情绪调节
    var psych4 by remember { mutableStateOf("") }   // 人生最重要 → 价值观
    var psych5 by remember { mutableStateOf("") }   // deadline → 压力应对

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "认识另一个我",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "第 ${step + 1} / $totalSteps 步",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(Modifier.height(24.dp))

            when (step) {
                0 -> {
                    Text("你的身份是？", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
                    listOf("学生", "上班族", "自由职业", "其他").forEach { option ->
                        Button(
                            onClick = { identity = option },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            enabled = identity != option
                        ) {
                            Text(option)
                        }
                    }
                }

                1 -> {
                    Text("你的作息大致是怎样的？", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "出门/工作时间选填，留空会智能推算",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    TimeField("起床时间（必填）", wake) { wake = it }
                    TimeField("睡觉时间（必填）", sleep) { sleep = it }
                    TimeField("出门时间（选填）", commute) { commute = it }
                    TimeField("开始工作/学习（选填）", workStart) { workStart = it }
                    TimeField("结束工作/学习（选填）", workEnd) { workEnd = it }
                }

                2 -> {
                    Text("你的性格和兴趣呢？", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))

                    // ---- MBTI 快速性格（可选） ----
                    Text(
                        text = "MBTI 测试结果（可选，用于快速了解性格）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = mbti,
                        onValueChange = {
                            mbti = it.uppercase().filter { c -> c in "IENS TFJP" }.take(4)
                        },
                        label = { Text("MBTI 类型") },
                        placeholder = { Text("如 INTJ / ENFP") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val mbtiDesc = MBTI_TYPES[mbti]
                    if (mbti.length == 4 && mbtiDesc != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "$mbti ${mbtiDesc}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(6.dp))
                        Button(
                            onClick = {
                                val text = "MBTI $mbti：$mbtiDesc"
                                personality = if (personality.isBlank()) text else "$text；$personality"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("应用到性格")
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = personality,
                        onValueChange = { personality = it },
                        label = { Text("性格倾向（如：内向、理性、随和，或用上方 MBTI 快速填充）") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = interests,
                        onValueChange = { interests = it },
                        label = { Text("兴趣爱好（逗号分隔）") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                3 -> {
                    // 心理学自然对话（轻量、像聊天不像问卷，可跳过）
                    Text("再聊几个小问题，让我更懂你~", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "随便选选就行，不选也没关系",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    PsychQuestion("周末你更想出门玩，还是在家躺？", listOf("出门玩！", "在家躺", "看心情"), psych1) { psych1 = it }
                    PsychQuestion("点外卖或选电影的时候，你会纠结很久吗？", listOf("纠结半天", "很快能定", "看情况"), psych2) { psych2 = it }
                    PsychQuestion("心情不好的时候，你一般会？", listOf("找人倾诉", "自己消化", "听歌/运动转移"), psych3) { psych3 = it }
                    PsychQuestion("你觉得人生里最重要的是？", listOf("自由", "家人朋友", "事业成就", "安稳"), psych4) { psych4 = it }
                    PsychQuestion("deadline 快到了你会怎么搞？", listOf("提前准备", "最后冲刺", "先焦虑再动"), psych5) { psych5 = it }
                }

                4 -> {
                    Text("最后聊聊你的说话风格~", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = catchphrases,
                        onValueChange = { catchphrases = it },
                        label = { Text("常用口头禅（如：绝了、离谱、问题不大）") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = toneWords,
                        onValueChange = { toneWords = it },
                        label = { Text("语气词（如：哈哈哈、嗯、啊）") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = {
                    if (step < totalSteps - 1) {
                        step++
                    } else {
                        viewModel.completeOnboarding(
                            identity = identity,
                            wake = wake,
                            sleep = sleep,
                            commute = commute,
                            workStart = workStart,
                            workEnd = workEnd,
                            personality = personality,
                            interests = interests,
                            catchphrases = catchphrases,
                            toneWords = toneWords,
                            psychologyJson = buildPsychologyJson(psych1, psych2, psych3, psych4, psych5)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (step == totalSteps - 1) "开始生成我的镜像" else "下一步")
            }
        }
    }
}

/** 心理学自然对话问题：像聊天一样的选项按钮，可跳过 */
@Composable
private fun PsychQuestion(
    question: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(question, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(6.dp))
        options.forEach { opt ->
            Button(
                onClick = { onSelect(opt) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                enabled = selected != opt
            ) {
                Text(opt)
            }
        }
    }
}

/** 将 5 个心理学问题的选择映射为 PsychologyProfile JSON */
private fun buildPsychologyJson(
    psych1: String, psych2: String, psych3: String, psych4: String, psych5: String
): String {
    val psych = com.mgxd.mirror.domain.PsychologyProfile()
    when (psych1) {
        "出门玩！" -> psych.bigFive = "外向性较高，喜欢与人互动"
        "在家躺" -> psych.bigFive = "外向性较低，偏好独处，社交后需要充电"
        "看心情" -> psych.bigFive = "外向性中等，视状态而定"
    }
    when (psych2) {
        "纠结半天" -> psych.decisionStyle = "犹豫型，做决定要纠结很久，但重大选择会提前调研"
        "很快能定" -> psych.decisionStyle = "直觉型，凭感觉快速决定"
    }
    when (psych3) {
        "找人倾诉" -> psych.emotionRegulation = "喜欢找人倾诉，说出来就好"
        "自己消化" -> psych.emotionRegulation = "倾向于自己消化，不太主动倾诉"
        "听歌/运动转移" -> psych.emotionRegulation = "用转移注意力的方式调节（听歌/运动等）"
    }
    when (psych4) {
        "自由" -> psych.values = "看重自由与个人空间"
        "家人朋友" -> psych.values = "看重家人朋友与关系"
        "事业成就" -> psych.values = "看重事业与成就"
        "安稳" -> psych.values = "看重安稳与安全感"
    }
    when (psych5) {
        "提前准备" -> psych.stressCoping = "积极解决型，压力来了提前准备"
        "最后冲刺" -> psych.stressCoping = "拖延型，deadline 前冲刺"
        "先焦虑再动" -> psych.stressCoping = "先焦虑后行动，偶尔拖延但最后能完成"
    }
    return psych.toJson()
}

@Composable
private fun TimeField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text("HH:mm，如 07:00") },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    )
}

/** 16 型 MBTI 人格快速描述（用于快速了解性格） */
private val MBTI_TYPES: Map<String, String> = mapOf(
    "INTJ" to "建筑师 · 理性独立，目标明确，喜欢深度思考",
    "INTP" to "逻辑学家 · 思辨好奇，冷静缜密，热衷探索原理",
    "ENTJ" to "指挥官 · 果断自信，天生领导，行动力强",
    "ENTP" to "辩论家 · 机智灵活，脑洞大开，乐于挑战",
    "INFJ" to "提倡者 · 理想主义，洞察人心，坚定温柔",
    "INFP" to "调停者 · 理想化，敏感细腻，重视价值观",
    "ENFJ" to "主人公 · 热情利他，感染力强，乐于助人",
    "ENFP" to "竞选者 · 热情奔放，创造力满分，充满活力",
    "ISTJ" to "物流师 · 严谨可靠，务实守序，一板一眼",
    "ISFJ" to "守卫者 · 尽责温暖，细致体贴，默默付出",
    "ESTJ" to "总经理 · 高效组织，条理分明，天生管理者",
    "ESFJ" to "执政官 · 热心合作，责任感强，照顾他人",
    "ISTP" to "鉴赏家 · 冷静灵活，动手能力强，实用主义",
    "ISFP" to "探险家 · 随和低调，艺术气质，活在当下",
    "ESTP" to "企业家 · 精力充沛，务实冒险，反应敏捷",
    "ESFP" to "表演者 · 外向活泼，热爱分享，享受当下"
)