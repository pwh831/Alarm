package com.fitwake.app.home

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.fitwake.app.R
import com.fitwake.app.alarm.AlarmScheduler

/** 알람이 제대로 울리는 데 필요한 권한·설정 (PRD SE-01). */
enum class Requirement(@StringRes val title: Int, @StringRes val description: Int) {
    NOTIFICATIONS(R.string.perm_notifications, R.string.perm_notifications_desc),
    EXACT_ALARM(R.string.perm_exact_alarm, R.string.perm_exact_alarm_desc),
    FULL_SCREEN(R.string.perm_full_screen, R.string.perm_full_screen_desc),
    CAMERA(R.string.perm_camera, R.string.perm_camera_desc),
    BATTERY(R.string.perm_battery, R.string.perm_battery_desc),
}

fun Context.missingRequirements(): List<Requirement> = Requirement.entries.filterNot { isSatisfied(it) }

private fun Context.granted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

fun Context.isSatisfied(r: Requirement): Boolean = when (r) {
    Requirement.NOTIFICATIONS ->
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || granted(Manifest.permission.POST_NOTIFICATIONS)
    Requirement.EXACT_ALARM -> AlarmScheduler(this).canScheduleExact()
    Requirement.FULL_SCREEN ->
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    Requirement.CAMERA -> granted(Manifest.permission.CAMERA)
    Requirement.BATTERY -> getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
}

/** 빠진 권한이 있을 때만 홈 화면 위에 보이는 안내 카드. 설정에서 돌아오면 다시 확인한다. */
@Composable
fun PermissionPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var missing by remember { mutableStateOf(context.missingRequirements()) }
    LifecycleResumeEffect(Unit) {
        missing = context.missingRequirements()
        onPauseOrDispose { }
    }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        missing = context.missingRequirements()
    }
    if (missing.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.perm_panel_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            for (r in missing) {
                Text(
                    stringResource(r.description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                TextButton(onClick = { context.fixRequirement(r) { requestPermission.launch(it) } }) {
                    Text(stringResource(r.title))
                }
            }
        }
    }
}

/** 권한 요청 대화상자나 해당 설정 화면을 연다. [request]는 런타임 권한 요청 런처. */
fun Context.fixRequirement(r: Requirement, request: (String) -> Unit) {
    val pkg = Uri.parse("package:$packageName")
    when (r) {
        Requirement.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            request(Manifest.permission.POST_NOTIFICATIONS)
        }
        Requirement.CAMERA -> request(Manifest.permission.CAMERA)
        Requirement.EXACT_ALARM -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            openSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg))
        }
        Requirement.FULL_SCREEN -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            openSettings(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg))
        }
        // 앱 목록에서 직접 '제한 없음'을 고르게 한다. 제조사별 추가 설정은 M2 온보딩에서 안내.
        Requirement.BATTERY -> openSettings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}

fun Context.openSettings(intent: Intent) {
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
}
