package com.fitwake.app

import android.content.Intent
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
import com.fitwake.app.alarm.AlarmService
import com.fitwake.app.alarm.RingState
import com.fitwake.app.edit.EditAlarmScreen
import com.fitwake.app.home.HomeScreen
import com.fitwake.app.ringing.RingingActivity
import com.fitwake.app.ui.FitWakeTheme

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

    override fun onResume() {
        super.onResume()
        // 알람이 울리는 중에 앱을 열면 곧바로 알람 화면으로 보낸다.
        if (AlarmService.state.value != RingState.Idle) {
            startActivity(Intent(this, RingingActivity::class.java))
        }
    }
}

private sealed interface Screen {
    data object Home : Screen
    /** id가 null이면 새 알람. */
    data class Edit(val alarmId: Long?) : Screen
}

@Composable
private fun FitWakeApp() {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    when (val s = screen) {
        Screen.Home -> HomeScreen(
            onAdd = { screen = Screen.Edit(null) },
            onEdit = { screen = Screen.Edit(it.id) },
        )
        is Screen.Edit -> EditAlarmScreen(s.alarmId, onDone = { screen = Screen.Home })
    }
}
