package com.fitwake.app.onboarding

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.fitwake.app.AppPrefs
import com.fitwake.app.R
import com.fitwake.app.home.Requirement
import com.fitwake.app.home.fixRequirement
import com.fitwake.app.home.isSatisfied
import com.fitwake.app.home.openSettings
import com.fitwake.app.mission.MissionConfig
import com.fitwake.app.mission.MissionScreen
import com.fitwake.pose.Difficulty
import com.fitwake.pose.Exercise

private enum class Step { WELCOME, HOW_IT_WORKS, PRIVACY, PERMISSIONS, BATTERY, TRIAL }

/** 첫 실행 안내 (PRD SE-01, 8장 온보딩): 소개 → 권한 → 제조사 배터리 설정 → 첫 미션 체험. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    val step = Step.entries[stepIndex]
    val next = { stepIndex = (stepIndex + 1).coerceAtMost(Step.entries.lastIndex) }
    val finish = {
        AppPrefs.setOnboardingDone(context)
        onDone()
    }
    BackHandler(enabled = stepIndex > 0) { stepIndex-- }

    when (step) {
        Step.WELCOME -> IntroPage(R.string.onboarding_welcome_title, R.string.onboarding_welcome_body, next)
        Step.HOW_IT_WORKS -> IntroPage(R.string.onboarding_how_title, R.string.onboarding_how_body, next)
        Step.PRIVACY -> IntroPage(R.string.onboarding_privacy_title, R.string.onboarding_privacy_body, next)
        Step.PERMISSIONS -> PermissionsPage(next)
        Step.BATTERY -> BatteryPage(next)
        Step.TRIAL -> TrialPage(finish)
    }
}

@Composable
private fun Page(
    title: String,
    primary: String,
    onPrimary: () -> Unit,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(32.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium)
            content()
        }
        Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth()) { Text(primary) }
        if (secondary != null) {
            TextButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth()) { Text(secondary) }
        }
    }
}

@Composable
private fun IntroPage(title: Int, body: Int, onNext: () -> Unit) {
    Page(stringResource(title), stringResource(R.string.next), onNext) {
        Text(stringResource(body), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PermissionsPage(onNext: () -> Unit) {
    val context = LocalContext.current
    fun statuses() = Requirement.entries.associateWith { context.isSatisfied(it) }
    var satisfied by remember { mutableStateOf(statuses()) }
    LifecycleResumeEffect(Unit) {
        satisfied = statuses()
        onPauseOrDispose { }
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        satisfied = statuses()
    }
    val allDone = satisfied.values.all { it }

    Page(
        title = stringResource(R.string.onboarding_permissions_title),
        primary = stringResource(if (allDone) R.string.next else R.string.onboarding_later),
        onPrimary = onNext,
    ) {
        Text(stringResource(R.string.onboarding_permissions_body), style = MaterialTheme.typography.bodyMedium)
        // 알람 동작에 중요한 순서대로 (Requirement 선언 순서)
        Requirement.entries.forEachIndexed { i, r ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${i + 1}. ${stringResource(r.title)}", style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(r.description), style = MaterialTheme.typography.bodySmall)
                    }
                    if (satisfied[r] == true) {
                        Text(stringResource(R.string.done_check), color = MaterialTheme.colorScheme.primary)
                    } else {
                        OutlinedButton(onClick = { context.fixRequirement(r) { request.launch(it) } }) {
                            Text(stringResource(R.string.allow))
                        }
                    }
                }
            }
        }
        if (!allDone) {
            Text(stringResource(R.string.onboarding_permissions_later_note), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BatteryPage(onNext: () -> Unit) {
    val context = LocalContext.current
    val manufacturer = remember { Manufacturer.current() }
    Page(stringResource(R.string.onboarding_battery_title), stringResource(R.string.next), onNext) {
        Text(stringResource(R.string.onboarding_battery_body), style = MaterialTheme.typography.bodyMedium)
        Card(Modifier.fillMaxWidth()) {
            Text(stringResource(manufacturer.guide), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedButton(
            onClick = {
                context.openSettings(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.open_app_settings)) }
        TextButton(
            onClick = { context.openSettings(Intent(Intent.ACTION_VIEW, Uri.parse(DONT_KILL_MY_APP_URL))) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.battery_more_info)) }
    }
}

/** 스쿼트 3회(쉬움)로 인식이 잘 되는지 체험한다. */
@Composable
private fun TrialPage(onFinish: () -> Unit) {
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Int?>(null) }

    if (running) {
        BackHandler { running = false }
        MissionScreen(
            config = MissionConfig(Exercise.SQUAT, Difficulty.EASY, TRIAL_REPS),
            onComplete = { sec ->
                result = sec
                running = false
            },
            onQuit = { running = false },
        )
        return
    }

    val done = result
    Page(
        title = stringResource(if (done == null) R.string.onboarding_trial_title else R.string.done_title),
        primary = stringResource(if (done == null) R.string.onboarding_trial_start else R.string.onboarding_finish),
        onPrimary = { if (done == null) running = true else onFinish() },
        secondary = if (done == null) stringResource(R.string.onboarding_skip_trial) else null,
        onSecondary = onFinish,
    ) {
        Text(
            if (done == null) {
                stringResource(R.string.onboarding_trial_body, TRIAL_REPS)
            } else {
                stringResource(R.string.onboarding_trial_done, done)
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Start,
        )
    }
}

private const val TRIAL_REPS = 3
