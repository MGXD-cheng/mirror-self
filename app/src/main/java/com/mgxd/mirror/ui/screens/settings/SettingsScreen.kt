package com.mgxd.mirror.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mgxd.mirror.ui.MainViewModel

/**
 * 设置界面：AI 服务配置（API 地址、Key、模型、参数）。
 * API Key 将加密存储于 Keystore。
 */
@Composable
fun SettingsScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val config by viewModel.apiConfigState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var baseUrl by remember { mutableStateOf(config.baseUrl) }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(config.model) }
    var maxTokens by remember { mutableStateOf(config.maxTokens.toString()) }
    var temperature by remember { mutableStateOf(config.temperature.toString()) }
    var enableFallback by remember { mutableStateOf(config.enableFallback) }

    // 配置加载后同步到表单
    androidx.compose.runtime.LaunchedEffect(config) {
        baseUrl = config.baseUrl
        model = config.model
        maxTokens = config.maxTokens.toString()
        temperature = config.temperature.toString()
        enableFallback = config.enableFallback
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "AI 服务配置",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "默认内置 DeepSeek（deepseek-chat），可自由切换任意 OpenAI 兼容接口",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("API 地址 (Base URL)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API Key") },
            placeholder = { Text("已加密保存，留空则保持不变") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            label = { Text("模型名称") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = maxTokens,
                onValueChange = { maxTokens = it },
                label = { Text("最大 Token") },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(
                value = temperature,
                onValueChange = { temperature = it },
                label = { Text("温度") },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("本地兜底回复", fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                Text(
                    "API 失败时用本地模板保证聊天不中断",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enableFallback,
                onCheckedChange = { enableFallback = it }
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                viewModel.saveApiConfig(
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    model = model,
                    maxTokens = maxTokens.toIntOrNull() ?: 1024,
                    temperature = temperature.toFloatOrNull() ?: 0.8f,
                    enableFallback = enableFallback
                )
                Toast.makeText(
                    context,
                    "配置已保存（API Key 已加密存储）",
                    Toast.LENGTH_SHORT
                ).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存配置")
        }

        Spacer(Modifier.height(24.dp))

        // ---- 主动消息程度 ----
        val profile by viewModel.profile.collectAsStateWithLifecycle()
        Text(
            text = "主动社交",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "「另一个我」主动找你聊天的频率（每天 1-3 条，深夜不打扰）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        val level = profile?.activeLevel ?: 2
        listOf(
            0 to "关闭",
            1 to "低调（每天约1条）",
            2 to "适中（每天约2条）",
            3 to "热情（每天约3条）"
        ).forEach { (v, label) ->
            Button(
                onClick = { viewModel.setActiveLevel(v) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                enabled = level != v
            ) {
                Text(label)
            }
        }

        Spacer(Modifier.height(20.dp))

        // ---- 主动追问开关 ----
        Text(
            text = "主动追问",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "听到陌生细节时主动追问",
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
                Text(
                    "如「学校在哪」「你朋友是谁」——先回复再追问，答案会记住",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = profile?.askEnabled ?: true,
                onCheckedChange = { viewModel.setAskEnabled(it) }
            )
        }

        Spacer(Modifier.height(20.dp))

        // ---- 细节记忆管理 ----
        Text(
            text = "细节记忆",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "「另一个我」会记住你提过的小事并在聊天中自然提及。全部仅存本地。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                viewModel.clearFacts()
                Toast.makeText(context, "已清空所有记忆", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("清空记忆库（不可恢复）")
        }

        Spacer(Modifier.height(16.dp))

        // ---- 连接自检 ----
        val testResult by viewModel.testResult.collectAsStateWithLifecycle()
        Button(
            onClick = { viewModel.testConnection() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("测试连接")
        }
        if (testResult != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = testResult.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (testResult?.startsWith("✅") == true)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "🔒 安全说明：API Key 使用 Android Keystore 加密存储，聊天记录仅保存在本地。发送给 AI 的内容会先经过脱敏处理。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}