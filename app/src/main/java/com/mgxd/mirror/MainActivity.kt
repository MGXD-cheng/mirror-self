package com.mgxd.mirror

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mgxd.mirror.ui.AppScreen
import com.mgxd.mirror.ui.MainViewModel
import com.mgxd.mirror.ui.screens.chat.ChatScreen
import com.mgxd.mirror.ui.screens.onboarding.OnboardingScreen
import com.mgxd.mirror.ui.screens.profile.ProfileScreen
import com.mgxd.mirror.ui.screens.schedule.ScheduleScreen
import com.mgxd.mirror.ui.screens.settings.SettingsScreen
import com.mgxd.mirror.ui.theme.MirrorTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()

        setContent {
            MirrorTheme {
                MirrorAppRoot(viewModel)
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
    }
}

@Composable
private fun MirrorAppRoot(viewModel: MainViewModel) {
    val profile = viewModel.profile.collectAsStateWithLifecycle().value

    if (profile == null || !profile.onboardingDone) {
        OnboardingScreen(viewModel)
        return
    }

    val screen by viewModel.screen.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                val items = listOf(
                    AppScreen.CHAT to TabItem("聊天", Icons.Filled.ChatBubble),
                    AppScreen.SCHEDULE to TabItem("日程", Icons.Filled.CalendarMonth),
                    AppScreen.PROFILE to TabItem("画像", Icons.Filled.Face),
                    AppScreen.SETTINGS to TabItem("设置", Icons.Filled.Settings)
                )
                items.forEach { (target, item) ->
                    NavigationBarItem(
                        selected = screen == target,
                        onClick = { viewModel.navigate(target) },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)
        when (screen) {
            AppScreen.CHAT -> ChatScreen(viewModel, modifier)
            AppScreen.SCHEDULE -> ScheduleScreen(viewModel, modifier)
            AppScreen.PROFILE -> ProfileScreen(viewModel, modifier)
            AppScreen.SETTINGS -> SettingsScreen(viewModel, modifier)
        }
    }
}

private data class TabItem(val label: String, val icon: ImageVector)