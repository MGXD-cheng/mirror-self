package com.mgxd.mirror.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mgxd.mirror.data.db.entity.UserProfileEntity
import com.mgxd.mirror.ui.MainViewModel

/**
 * 画像界面：展示"数字镜像"当前学习到的用户画像，并支持手动编辑校正。
 */
@Composable
fun ProfileScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    // 草稿：进入编辑时从当前画像拷贝，避免直接污染已保存数据
    var draft by remember { mutableStateOf(UserProfileEntity()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // 标题 + 编辑/完成切换
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "数字画像",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (editing) "手动校正，让「另一个我」更懂你" else "这是「另一个我」目前对你的了解",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            TextButton(onClick = {
                if (editing) {
                    // 完成编辑：保存草稿
                    viewModel.saveProfile(draft)
                    editing = false
                } else {
                    profile?.let { draft = it }
                    editing = true
                }
            }) {
                Text(if (editing) "完成" else "编辑")
            }
        }

        Spacer(Modifier.height(16.dp))

        profile?.let {
            // 学习进度（只读）
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("学习进度", fontWeight = FontWeight.Medium)
                        Text("${it.learningProgress}%", color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { it.learningProgress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (editing) {
                EditableField("身份（学生/上班族/…）", draft.identity) { draft = draft.copy(identity = it) }
                EditableField("昵称（「另一个我」怎么叫你）", draft.nickname) { draft = draft.copy(nickname = it) }
                EditableField("性格", draft.personality) { draft = draft.copy(personality = it) }
                EditableField("兴趣爱好", draft.interests) { draft = draft.copy(interests = it) }
                EditableField("口头禅（分号分隔）", draft.catchphrases) { draft = draft.copy(catchphrases = it) }
                EditableField("语气词（分号分隔）", draft.toneWords) { draft = draft.copy(toneWords = it) }
                EditableField("常用表情", draft.emojiStyle) { draft = draft.copy(emojiStyle = it) }
                EditableField("决策习惯", draft.decisionStyle) { draft = draft.copy(decisionStyle = it) }
                EditableField("情绪模式", draft.emotionPattern) { draft = draft.copy(emotionPattern = it) }
                EditableField("语言风格", draft.languageStyle) { draft = draft.copy(languageStyle = it) }
                EditableField("心理学画像（JSON，高级）", draft.psychologyProfile) { draft = draft.copy(psychologyProfile = it) }
                EditableField("起床时间", draft.wakeTime) { draft = draft.copy(wakeTime = it) }
                EditableField("睡觉时间", draft.sleepTime) { draft = draft.copy(sleepTime = it) }
                EditableField("出门时间", draft.commuteTime) { draft = draft.copy(commuteTime = it) }
                EditableField("工作/学习开始", draft.workStart) { draft = draft.copy(workStart = it) }
                EditableField("工作/学习结束", draft.workEnd) { draft = draft.copy(workEnd = it) }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        viewModel.saveProfile(draft)
                        editing = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("保存修改")
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "修改后会立即生效：日程、聊天语气都会同步调整",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                InfoCard("身份", it.identity)
                if (it.nickname.isNotBlank()) InfoCard("昵称", it.nickname)
                InfoCard("性格", it.personality)
                InfoCard("兴趣爱好", it.interests)
                InfoCard("口头禅", formatCatchphrases(it.catchphrases))
                InfoCard("语气词", it.toneWords)
                InfoCard("常用表情", it.emojiStyle)
                InfoCard("语言风格", it.languageStyle)
                InfoCard("决策习惯", it.decisionStyle)
                InfoCard("情绪模式", it.emotionPattern)
                InfoCard("作息习惯", buildHabits(it))

                // 心理学特征区块
                val psych = com.mgxd.mirror.domain.PsychologyProfile.fromJson(it.psychologyProfile)
                if (psych.filledCount() > 0) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "心理学特征 · 完整度 ${psych.completeness()}%",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LinearProgressIndicator(
                        progress = { psych.completeness() / 100f },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    for ((label, value) in psych.filledLabels()) {
                        InfoCard(label, value)
                    }
                }

                // 我记得的事（细节记忆）
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "我记得的事",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "你提过的小事，我会在聊天里自然提起（可删除，仅存本地）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Spacer(Modifier.height(4.dp))
                val facts by viewModel.facts.collectAsStateWithLifecycle()
                androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.loadFacts() }
                if (facts.isEmpty()) {
                    Text(
                        text = "还没记住什么，多聊两句我就记住了～",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    facts.take(20).forEach { f ->
                        FactCard(
                            type = f.type,
                            content = f.content,
                            importance = f.importance,
                            onDelete = { viewModel.deleteFact(f.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditableField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        singleLine = true
    )
}

/** 单条细节记忆卡片：类型标签 + 内容 + 重要度 + 删除 */
@Composable
private fun FactCard(
    type: String,
    content: String,
    importance: Int,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$type · 重要度${"★".repeat(importance.coerceIn(1, 5))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(2.dp))
                Text(text = content, style = MaterialTheme.typography.bodyLarge)
            }
            TextButton(onClick = onDelete) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, value: String) {
    if (value.isBlank()) return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun buildHabits(p: UserProfileEntity): String {
    val parts = mutableListOf<String>()
    parts += "起床 ${p.wakeTime}"
    if (p.commuteTime.isNotBlank()) parts += "出门 ${p.commuteTime}"
    if (p.workStart.isNotBlank() || p.workEnd.isNotBlank()) {
        val start = p.workStart.ifBlank { "—" }
        val end = p.workEnd.ifBlank { "—" }
        parts += "工作/学习 $start - $end"
    }
    parts += "睡觉 ${p.sleepTime}"
    return parts.joinToString(" · ")
}

/** 将口头禅 JSON 格式化为可读文本（兼容旧分号格式） */
private fun formatCatchphrases(raw: String): String {
    if (raw.isBlank()) return ""
    if (!raw.trimStart().startsWith("[")) return raw // 旧格式原样展示
    return try {
        val arr = org.json.JSONArray(raw)
        val parts = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val text = obj.optString("t", "")
            val count = obj.optInt("n", 1)
            if (text.isNotBlank()) parts += if (count > 1) "$text（常用度$count）" else text
        }
        parts.joinToString("、")
    } catch (e: Exception) {
        raw
    }
}