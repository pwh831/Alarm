package com.fitwake.app.settings

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.fitwake.app.AppPrefs
import com.fitwake.app.R
import com.fitwake.app.edit.MissionPicker
import com.fitwake.app.home.PermissionPanel

@Composable
fun SettingsScreen(onBack: () -> Unit, onReplayOnboarding: () -> Unit) {
    val context = LocalContext.current
    var defaultMission by remember { mutableStateOf(AppPrefs.newAlarm(context)) }
    var saved by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall)

        PermissionPanel()

        Text(stringResource(R.string.default_mission), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.default_mission_desc), style = MaterialTheme.typography.bodySmall)
        MissionPicker(defaultMission, onChange = {
            defaultMission = it
            saved = false
        })
        Button(
            onClick = {
                AppPrefs.setDefaultMission(context, defaultMission)
                saved = true
            },
            enabled = !saved,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(if (saved) R.string.saved else R.string.save)) }

        HorizontalDivider()
        OutlinedButton(onClick = onReplayOnboarding, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.replay_onboarding))
        }

        val version = remember {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }.versionName
            }.getOrNull()
        }
        Text(
            stringResource(R.string.about, version.orEmpty()),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
