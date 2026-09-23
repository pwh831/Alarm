package com.fitwake.app.ringing

import android.app.KeyguardManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.fitwake.app.ui.FitWakeTheme

/** 알람이 울릴 때 잠금 화면 위에 뜨는 화면. 알림의 전체 화면 인텐트로 열린다. */
class RingingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            FitWakeTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RingingScreen(onFinished = ::finishAndUnlock)
                }
            }
        }
    }

    /** 미션을 마치면 잠금 해제 화면을 띄워 바로 폰을 쓸 수 있게 한다. */
    private fun finishAndUnlock() {
        getSystemService(KeyguardManager::class.java).requestDismissKeyguard(this, null)
        finish()
    }
}
