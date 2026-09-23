package com.fitwake.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.fitwake.app.mission.DoneScreen
import com.fitwake.app.mission.MissionConfig
import com.fitwake.app.mission.MissionScreen
import com.fitwake.app.setup.SetupScreen
import com.fitwake.app.ui.FitWakeTheme
import com.fitwake.pose.Difficulty
import com.fitwake.pose.Exercise

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FitWakeTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    FitWakeApp()
                }
            }
        }
    }
}

private sealed interface Screen {
    data object Setup : Screen
    data class Mission(val config: MissionConfig) : Screen
    data class Done(val config: MissionConfig, val elapsedSec: Int) : Screen
}

/**
 * M0 기술 검증용 흐름: 미션 설정 → 카메라 미션 → 완료.
 * 알람 스케줄링(M1)이 붙으면 알람이 울릴 때 Mission 화면으로 바로 진입한다.
 */
@Composable
private fun FitWakeApp() {
    var screen by remember { mutableStateOf<Screen>(Screen.Setup) }
    var lastConfig by remember { mutableStateOf(MissionConfig(Exercise.SQUAT, Difficulty.NORMAL, 15)) }

    when (val s = screen) {
        Screen.Setup -> SetupScreen(
            initial = lastConfig,
            onStart = {
                lastConfig = it
                screen = Screen.Mission(it)
            },
        )
        is Screen.Mission -> MissionScreen(
            config = s.config,
            onComplete = { sec -> screen = Screen.Done(s.config, sec) },
            onQuit = { screen = Screen.Setup },
        )
        is Screen.Done -> DoneScreen(
            config = s.config,
            elapsedSec = s.elapsedSec,
            onAgain = { screen = Screen.Mission(s.config) },
            onBack = { screen = Screen.Setup },
        )
    }
}
