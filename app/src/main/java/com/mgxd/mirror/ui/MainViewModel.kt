package com.mgxd.mirror.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mgxd.mirror.data.db.entity.ChatMessageEntity
import com.mgxd.mirror.data.db.entity.FactMemoryEntity
import com.mgxd.mirror.data.db.entity.ScheduleEventEntity
import com.mgxd.mirror.data.db.entity.UserProfileEntity
import com.mgxd.mirror.data.repository.MirrorRepository
import com.mgxd.mirror.worker.MirrorRepositoryProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 主 ViewModel：承载聊天、日程、画像、设置四大模块的 UI 状态。
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repository: MirrorRepository =
        MirrorRepositoryProvider.get(app)

    // ---- 导航状态 ----
    val screen = MutableStateFlow(AppScreen.CHAT)

    // ---- 画像 ----
    private val _profile = MutableStateFlow<UserProfileEntity?>(null)
    val profile: StateFlow<UserProfileEntity?> = _profile.asStateFlow()

    // ---- 聊天 ----
    private val _messages = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    val messages: StateFlow<List<ChatMessageEntity>> = _messages.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    // ---- AI 连接自检 ----
    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult.asStateFlow()

    // ---- 日程 ----
    private val _todayEvents = MutableStateFlow<List<ScheduleEventEntity>>(emptyList())
    val todayEvents: StateFlow<List<ScheduleEventEntity>> = _todayEvents.asStateFlow()

    private val _upcomingEvents = MutableStateFlow<List<ScheduleEventEntity>>(emptyList())
    val upcomingEvents: StateFlow<List<ScheduleEventEntity>> = _upcomingEvents.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    // ---- 学习记录 ----
    private val _learnLog = MutableStateFlow<String>("")
    val learnLog: StateFlow<String> = _learnLog.asStateFlow()

    // ---- API 配置表单 ----
    private val _apiConfigState = MutableStateFlow(ApiConfigUiState())
    val apiConfigState: StateFlow<ApiConfigUiState> = _apiConfigState.asStateFlow()

    init {
        viewModelScope.launch {
            val p = repository.getProfile()
            _profile.value = p
            if (p.onboardingDone) {
                refreshAll()
            }
        }
    }

    fun navigate(screen: AppScreen) {
        this.screen.value = screen
    }

    /** 刷新所有数据（进入主界面后调用） */
    fun refreshAll() {
        viewModelScope.launch {
            _profile.value = repository.getProfile()
            // 满足条件时"另一个我"主动发来消息（记忆事件/日程/兴趣/时段/回访/随机）
            repository.proactiveMessage()
            _messages.value = repository.getRecentMessages(100)
            _facts.value = repository.getFacts()
            val today = LocalDate.now()
            _todayEvents.value = repository.ensureScheduleForDate(today)
            _upcomingEvents.value = repository.getUpcomingEvents(30)
            loadApiConfig()
        }
    }

    // ---- 细节记忆 ----

    private val _facts = MutableStateFlow<List<FactMemoryEntity>>(emptyList())
    val facts: StateFlow<List<FactMemoryEntity>> = _facts.asStateFlow()

    fun loadFacts() {
        viewModelScope.launch {
            _facts.value = repository.getFacts()
        }
    }

    fun deleteFact(id: Long) {
        viewModelScope.launch {
            repository.deleteFact(id)
            _facts.value = repository.getFacts()
        }
    }

    fun clearFacts() {
        viewModelScope.launch {
            repository.clearFacts()
            _facts.value = emptyList()
        }
    }

    /** 调整主动消息程度（0=关闭 1=低 2=中 3=高） */
    fun setActiveLevel(level: Int) {
        viewModelScope.launch {
            val p = repository.getProfile()
            p.activeLevel = level
            repository.saveProfile(p)
            _profile.value = repository.getProfile()
        }
    }

    /** 主动追问开关（听到陌生细节时主动追问并记住） */
    fun setAskEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val p = repository.getProfile()
            p.askEnabled = enabled
            repository.saveProfile(p)
            _profile.value = repository.getProfile()
        }
    }

    /** 保存用户手动编辑的数字画像 */
    fun saveProfile(profile: UserProfileEntity) {
        viewModelScope.launch {
            repository.saveProfile(profile)
            _profile.value = repository.getProfile()
        }
    }

    // ---- 聊天 ----

    fun sendMessage(text: String) {
        if (text.isBlank() || _sending.value) return
        viewModelScope.launch {
            _sending.value = true
            try {
                // 模拟真人思考/打字：随机 2-8 秒，用户消息越长延迟稍长
                delay(computeTypingDelay(text.length))
                val reply = repository.sendUserMessage(text.trim())
                _messages.value = repository.getRecentMessages(100)
                _profile.value = repository.getProfile()
            } finally {
                _sending.value = false
            }
        }
    }

    /**
     * 计算模拟打字延迟（毫秒）。
     * 基础 2-6 秒随机 + 长消息最多追加 2 秒，控制在 2-8 秒区间。
     */
    private fun computeTypingDelay(textLength: Int): Long {
        val base = 2_000L + kotlin.random.Random.nextLong(4_000L)
        val lenFactor = (textLength / 10).coerceIn(0, 4) * 500L
        return (base + lenFactor).coerceIn(2_000L, 8_000L)
    }

    // ---- 日程 ----

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        viewModelScope.launch {
            _todayEvents.value = repository.ensureScheduleForDate(date)
        }
    }

    fun regenerateToday() {
        viewModelScope.launch {
            repository.regenerateDate(LocalDate.now())
            _todayEvents.value = repository.getEventsForDate(LocalDate.now())
        }
    }

    // ---- 设置 ----

    fun loadApiConfig() {
        viewModelScope.launch {
            val config = repository.getApiConfig()
            _apiConfigState.value = ApiConfigUiState(
                baseUrl = config.baseUrl,
                model = config.model,
                maxTokens = config.maxTokens,
                temperature = config.temperature,
                enableFallback = config.enableFallback
            )
        }
    }

    fun saveApiConfig(
        baseUrl: String,
        apiKey: String,
        model: String,
        maxTokens: Int,
        temperature: Float,
        enableFallback: Boolean
    ) {
        viewModelScope.launch {
            repository.saveApiConfig(
                baseUrl = baseUrl,
                apiKey = apiKey,
                model = model,
                maxTokens = maxTokens,
                temperature = temperature,
                enableFallback = enableFallback,
                timeoutSeconds = 15
            )
            loadApiConfig()
        }
    }

    /** 用当前配置测试 AI 连接，结果写入 [testResult] */
    fun testConnection() {
        viewModelScope.launch {
            _testResult.value = "测试中，请稍候…"
            val config = repository.getApiConfig()
            _testResult.value = repository.testConnection(config)
        }
    }

    // ---- Onboarding ----

    fun completeOnboarding(
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
        viewModelScope.launch {
            repository.completeOnboarding(
                identity, wake, sleep, commute, workStart, workEnd,
                personality, interests, catchphrases, toneWords, psychologyJson
            )
            refreshAll()
            navigate(AppScreen.CHAT)
        }
    }
}

enum class AppScreen {
    CHAT, SCHEDULE, PROFILE, SETTINGS
}

data class ApiConfigUiState(
    val baseUrl: String = "https://api.deepseek.com/v1/chat/completions",
    val model: String = "deepseek-chat",
    val maxTokens: Int = 1024,
    val temperature: Float = 0.8f,
    val enableFallback: Boolean = true
)