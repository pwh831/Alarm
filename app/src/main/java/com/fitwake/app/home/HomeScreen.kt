package com.fitwake.app.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitwake.alarm.Alarm
import com.fitwake.alarm.nextAlarm
import com.fitwake.app.R
import com.fitwake.app.alarmRepository
import com.fitwake.app.ui.difficultyAndReps
import com.fitwake.app.ui.formatRemaining
import com.fitwake.app.ui.formatRepeat
import com.fitwake.app.ui.formatTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.ZonedDateTime

@Composable
fun HomeScreen(onAdd: () -> Unit, onEdit: (Alarm) -> Unit) {
    val context = LocalContext.current
    val repository = context.alarmRepository
    val alarms by repository.alarms.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()

    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = ZonedDateTime.now()
        }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd) { Text(stringResource(R.string.add_alarm)) }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                    val next = alarms?.nextAlarm(now)
                    Text(
                        if (next == null) {
                            stringResource(R.string.no_upcoming_alarm)
                        } else {
                            formatRemaining(Duration.between(now, next.second))
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            item { PermissionPanel() }

            val list = alarms.orEmpty()
            if (alarms != null && list.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.empty_alarms),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 32.dp),
                    )
                }
            }
            items(list, key = { it.id }) { alarm ->
                AlarmCard(
                    alarm = alarm,
                    onClick = { onEdit(alarm) },
                    onToggle = { enabled -> scope.launch { repository.setEnabled(alarm, enabled) } },
                )
            }
        }
    }
}

@Composable
private fun AlarmCard(alarm: Alarm, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                val alpha = if (alarm.enabled) 1f else 0.5f
                val onSurface = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                Text(formatTime(alarm.hour, alarm.minute), style = MaterialTheme.typography.displaySmall, color = onSurface)
                Text(
                    listOf(alarm.label, formatRepeat(alarm.repeatDays)).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = onSurface,
                )
                Text(
                    difficultyAndReps(alarm.exercise, alarm.difficulty, alarm.targetReps),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                )
            }
            Switch(checked = alarm.enabled, onCheckedChange = onToggle)
        }
    }
}
