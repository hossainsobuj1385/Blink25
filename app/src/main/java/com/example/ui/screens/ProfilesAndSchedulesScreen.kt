package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.BlinkValidators
import com.example.domain.ProfileType
import com.example.domain.ReminderProfile
import com.example.domain.ReminderSchedule
import com.example.domain.SoundType
import com.example.domain.ValidationResult
import com.example.i18n.LocalStrings
import com.example.viewmodel.BlinkDashboardData
import java.util.TimeZone

@Composable
fun ProfilesAndSchedulesScreen(
    data: BlinkDashboardData,
    onActivateProfile: (String) -> Unit,
    onSaveProfile: (ReminderProfile, Boolean, (Boolean, String?) -> Unit) -> Unit,
    onDuplicateProfile: (ReminderProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onSaveSchedule: (ReminderSchedule, Boolean, (Boolean, String?) -> Unit) -> Unit,
    onDeleteSchedule: (String) -> Unit,
    onPreviewSound: (SoundType, Float) -> Unit,
    onFullScreenMode: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var editingProfile by remember { mutableStateOf<ReminderProfile?>(null) }
    var isNewProfile by remember { mutableStateOf(false) }
    var editingSchedule by remember { mutableStateOf<ReminderSchedule?>(null) }
    var isNewSchedule by remember { mutableStateOf(false) }

    when {
        editingProfile != null -> {
            ProfileEditorScreen(
                initial = editingProfile!!,
                onFullScreenMode = onFullScreenMode,
                onDismiss = { editingProfile = null },
                onPreviewSound = onPreviewSound,
                onSave = { updated, cb ->
                    onSaveProfile(updated, isNewProfile) { ok, err ->
                        if (ok) editingProfile = null
                        cb(ok, err)
                    }
                },
                modifier = modifier
            )
        }
        editingSchedule != null -> {
            ScheduleEditorScreen(
                initial = editingSchedule!!,
                profiles = data.profiles,
                existingSchedules = data.schedules,
                onFullScreenMode = onFullScreenMode,
                onDismiss = { editingSchedule = null },
                onSave = { updated, cb ->
                    onSaveSchedule(updated, isNewSchedule) { ok, err ->
                        if (ok) editingSchedule = null
                        cb(ok, err)
                    }
                },
                modifier = modifier
            )
        }
        else -> {
            Scaffold(
                modifier = modifier.fillMaxSize(),
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = {
                            if (selectedTab == 0) {
                                isNewProfile = true
                                editingProfile = ReminderProfile(
                                    userId = data.account?.userId ?: "",
                                    name = "Custom Focus",
                                    profileType = ProfileType.CUSTOM.name
                                )
                            } else {
                                isNewSchedule = true
                                editingSchedule = ReminderSchedule(
                                    userId = data.account?.userId ?: "",
                                    profileId = data.activeProfile?.id ?: "work_default",
                                    scheduleName = "Morning Screen Block",
                                    timezone = TimeZone.getDefault().id
                                )
                            }
                        },
                        modifier = Modifier.testTag("add_profile_or_schedule_fab")
                    ) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = if (selectedTab == 0) strings.createProfile else strings.createSchedule
                        )
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            icon = { Icon(Icons.Outlined.Tune, contentDescription = null) }
                        ) {
                            Text(strings.profilesTitle)
                        }
                        SegmentedButton(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            icon = { Icon(Icons.Outlined.Schedule, contentDescription = null) }
                        ) {
                            Text(strings.schedulesTitle)
                        }
                    }

                    if (selectedTab == 0) {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 84.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(data.profiles, key = { it.id }) { profile ->
                                val isActive = profile.id == data.activeProfile?.id
                                ProfileCard(
                                    profile = profile,
                                    isActive = isActive,
                                    canDelete = data.profiles.size > 1 && !isActive,
                                    onActivate = { onActivateProfile(profile.id) },
                                    onEdit = {
                                        isNewProfile = false
                                        editingProfile = profile
                                    },
                                    onDuplicate = { onDuplicateProfile(profile) },
                                    onDelete = { onDeleteProfile(profile.id) }
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 84.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (data.schedules.isEmpty()) {
                                item {
                                    Card(
                                        shape = RoundedCornerShape(22.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.Schedule,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(36.dp)
                                            )
                                            Text(
                                                text = "No weekly schedules yet",
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Text(
                                                text = "Create a weekday time window to pair a profile with your routine.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(data.schedules, key = { it.id }) { schedule ->
                                    val profileName = data.profiles.firstOrNull { it.id == schedule.profileId }?.name ?: "Work"
                                    val conflicts = BlinkValidators.findScheduleConflicts(schedule, data.schedules)
                                    ScheduleCard(
                                        schedule = schedule,
                                        profileName = profileName,
                                        hasConflict = conflicts.isNotEmpty(),
                                        onToggleEnabled = { enabled ->
                                            onSaveSchedule(schedule.copy(enabled = enabled), false) { _, _ -> }
                                        },
                                        onEdit = {
                                            isNewSchedule = false
                                            editingSchedule = schedule
                                        },
                                        onDelete = { onDeleteSchedule(schedule.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(
    profile: ReminderProfile,
    isActive: Boolean,
    canDelete: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    val strings = LocalStrings.current
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(profile.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Blink ${profile.blinkIntervalSeconds}s • Early ${profile.earlyWarningSeconds}s • Rest ${profile.eyeRestIntervalMinutes}m/${profile.eyeRestDurationSeconds}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isActive) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(100.dp)
                    ) {
                        Text(
                            text = strings.activeBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    FilledTonalButton(onClick = onActivate) {
                        Text(strings.activate)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${SoundType.fromRaw(profile.soundType).displayNameEn} • Snooze ${profile.snoozeMinutes}m",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row {
                    IconButton(onClick = onDuplicate) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = strings.duplicate)
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Outlined.Edit, contentDescription = strings.edit)
                    }
                    if (canDelete) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Outlined.Delete, contentDescription = strings.delete)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleCard(
    schedule: ReminderSchedule,
    profileName: String,
    hasConflict: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val strings = LocalStrings.current
    val dayNames = mapOf(1L to "Mon", 2L to "Tue", 3L to "Wed", 4L to "Thu", 5L to "Fri", 6L to "Sat", 7L to "Sun")
    val daysText = schedule.weekdays.mapNotNull { dayNames[it] }.joinToString(" ")
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(schedule.scheduleName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${schedule.startTime} – ${schedule.endTime} • $profileName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = schedule.enabled, onCheckedChange = onToggleEnabled)
            }
            Text(
                text = "$daysText • ${schedule.timezone}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (hasConflict) {
                Text(
                    text = "Overlaps another enabled schedule on shared weekdays",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = strings.edit)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = strings.delete)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileEditorScreen(
    initial: ReminderProfile,
    onFullScreenMode: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onPreviewSound: (SoundType, Float) -> Unit,
    onSave: (ReminderProfile, (Boolean, String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onDismiss)
    DisposableEffect(Unit) {
        onFullScreenMode(true)
        onDispose { onFullScreenMode(false) }
    }
    val strings = LocalStrings.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var name by remember { mutableStateOf(initial.name) }
    var blinkText by remember { mutableStateOf(initial.blinkIntervalSeconds.toString()) }
    var earlyText by remember { mutableStateOf(initial.earlyWarningSeconds.toString()) }
    var restIntervalText by remember { mutableStateOf(initial.eyeRestIntervalMinutes.toString()) }
    var restDurationText by remember { mutableStateOf(initial.eyeRestDurationSeconds.toString()) }
    var twentyPreset by remember { mutableStateOf(initial.twentyTwentyTwentyEnabled) }
    var soundType by remember { mutableStateOf(SoundType.fromRaw(initial.soundType)) }
    var volume by remember { mutableFloatStateOf(initial.soundVolume.toFloat()) }
    var visualEnabled by remember { mutableStateOf(initial.visualReminderEnabled) }
    var snoozeText by remember { mutableStateOf(initial.snoozeMinutes.toString()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(strings.profilesTitle) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = strings.cancel)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Profile Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (it.isFocused) keyboardController?.show() }
            )

            Text(strings.blinkIntervalSec, style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BlinkValidators.BLINK_PRESETS.forEach { preset ->
                    FilterChip(
                        selected = blinkText == preset.toString(),
                        onClick = { blinkText = preset.toString() },
                        label = { Text("${preset}s") }
                    )
                }
            }
            OutlinedTextField(
                value = blinkText,
                onValueChange = { blinkText = it },
                label = { Text(strings.blinkIntervalSec) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (it.isFocused) keyboardController?.show() }
            )

            Text(strings.earlyWarningSec, style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BlinkValidators.EARLY_WARNING_PRESETS.forEach { preset ->
                    FilterChip(
                        selected = earlyText == preset.toString(),
                        onClick = { earlyText = preset.toString() },
                        label = { Text(if (preset == 0) "Off" else "${preset}s") }
                    )
                }
            }
            OutlinedTextField(
                value = earlyText,
                onValueChange = { earlyText = it },
                label = { Text(strings.earlyWarningSec) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (it.isFocused) keyboardController?.show() }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(strings.twentyTwentyTwentyPreset, style = MaterialTheme.typography.bodySmall)
                Switch(
                    checked = twentyPreset,
                    onCheckedChange = {
                        twentyPreset = it
                        if (it) {
                            restIntervalText = "20"
                            restDurationText = "20"
                        }
                    }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = restIntervalText,
                    onValueChange = { restIntervalText = it },
                    label = { Text("Rest (min)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { if (it.isFocused) keyboardController?.show() }
                )
                OutlinedTextField(
                    value = restDurationText,
                    onValueChange = { restDurationText = it },
                    label = { Text("Dur (sec)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { if (it.isFocused) keyboardController?.show() }
                )
            }

            Text(strings.reminderSoundLabel, style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SoundType.entries.forEach { st ->
                    FilterChip(
                        selected = soundType == st,
                        onClick = { soundType = st },
                        label = { Text(st.displayNameEn) }
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = volume,
                    onValueChange = { volume = it },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onPreviewSound(soundType, volume) }) {
                    Icon(Icons.Outlined.VolumeUp, contentDescription = strings.previewSound)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(strings.visualCueToggle, style = MaterialTheme.typography.bodySmall)
                Switch(checked = visualEnabled, onCheckedChange = { visualEnabled = it })
            }

            OutlinedTextField(
                value = snoozeText,
                onValueChange = { snoozeText = it },
                label = { Text(strings.snoozeMinutesLabel) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (it.isFocused) keyboardController?.show() }
            )

            if (errorMsg != null) {
                Text(
                    text = errorMsg!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(strings.cancel)
                }
                Button(
                    onClick = {
                        val blinkVal = blinkText.trim().toIntOrNull() ?: -1
                        val earlyVal = earlyText.trim().toIntOrNull() ?: -1
                        val restIntVal = restIntervalText.trim().toIntOrNull() ?: -1
                        val restDurVal = restDurationText.trim().toIntOrNull() ?: -1
                        val snoozeVal = (snoozeText.trim().toIntOrNull() ?: 5).coerceIn(1, 60)

                        val checks = listOf(
                            BlinkValidators.validateProfileName(name),
                            BlinkValidators.validateBlinkInterval(blinkVal),
                            BlinkValidators.validateEarlyWarning(earlyVal, blinkVal),
                            BlinkValidators.validateEyeRest(restIntVal, restDurVal)
                        )
                        val firstErr = checks.filterIsInstance<ValidationResult.Invalid>().firstOrNull()
                        if (firstErr != null) {
                            errorMsg = firstErr.reason
                            return@Button
                        }

                        val updated = initial.copy(
                            name = name.trim(),
                            blinkIntervalSeconds = blinkVal.toLong(),
                            earlyWarningSeconds = earlyVal.toLong(),
                            eyeRestIntervalMinutes = restIntVal.toLong(),
                            eyeRestDurationSeconds = restDurVal.toLong(),
                            twentyTwentyTwentyEnabled = twentyPreset,
                            soundType = soundType.name,
                            soundVolume = volume.toDouble(),
                            visualReminderEnabled = visualEnabled,
                            snoozeMinutes = snoozeVal.toLong()
                        )
                        onSave(updated) { ok, err ->
                            if (!ok) errorMsg = err
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(strings.save)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleEditorScreen(
    initial: ReminderSchedule,
    profiles: List<ReminderProfile>,
    existingSchedules: List<ReminderSchedule>,
    onFullScreenMode: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSave: (ReminderSchedule, (Boolean, String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onDismiss)
    DisposableEffect(Unit) {
        onFullScreenMode(true)
        onDispose { onFullScreenMode(false) }
    }
    val strings = LocalStrings.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var name by remember { mutableStateOf(initial.scheduleName) }
    var startTime by remember { mutableStateOf(initial.startTime) }
    var endTime by remember { mutableStateOf(initial.endTime) }
    var profileId by remember { mutableStateOf(initial.profileId) }
    var weekdays by remember { mutableStateOf(initial.weekdays.toSet()) }
    var blinkEnabled by remember { mutableStateOf(initial.blinkEnabled) }
    var eyeRestEnabled by remember { mutableStateOf(initial.eyeRestEnabled) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val dayLabels = listOf(1L to "M", 2L to "T", 3L to "W", 4L to "T", 5L to "F", 6L to "S", 7L to "S")

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(strings.schedulesTitle) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = strings.cancel)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Schedule Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (it.isFocused) keyboardController?.show() }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = startTime,
                    onValueChange = { startTime = it },
                    label = { Text("Start (HH:mm)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { if (it.isFocused) keyboardController?.show() }
                )
                OutlinedTextField(
                    value = endTime,
                    onValueChange = { endTime = it },
                    label = { Text("End (HH:mm)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { if (it.isFocused) keyboardController?.show() }
                )
            }
            Text("Profile", style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                profiles.forEach { prof ->
                    FilterChip(
                        selected = profileId == prof.id,
                        onClick = { profileId = prof.id },
                        label = { Text(prof.name) }
                    )
                }
            }
            Text("Days", style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                dayLabels.forEach { (num, label) ->
                    FilterChip(
                        selected = num in weekdays,
                        onClick = {
                            weekdays = if (num in weekdays && weekdays.size > 1) weekdays - num else weekdays + num
                        },
                        label = { Text(label) }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Blink reminders", style = MaterialTheme.typography.bodySmall)
                Switch(checked = blinkEnabled, onCheckedChange = { blinkEnabled = it })
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Eye-rest reminders", style = MaterialTheme.typography.bodySmall)
                Switch(checked = eyeRestEnabled, onCheckedChange = { eyeRestEnabled = it })
            }
            if (errorMsg != null) {
                Text(
                    text = errorMsg!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(strings.cancel)
                }
                Button(
                    onClick = {
                        if (name.trim().isEmpty()) {
                            errorMsg = "Enter a schedule name."
                            return@Button
                        }
                        if (!BlinkValidators.validateTimeHHMM(startTime) || !BlinkValidators.validateTimeHHMM(endTime)) {
                            errorMsg = "Use 24-hour HH:mm format (e.g. 09:00 and 17:30)."
                            return@Button
                        }
                        val candidate = initial.copy(
                            scheduleName = name.trim(),
                            profileId = profileId,
                            startTime = startTime.trim(),
                            endTime = endTime.trim(),
                            weekdays = weekdays.sorted(),
                            blinkEnabled = blinkEnabled,
                            eyeRestEnabled = eyeRestEnabled,
                            timezone = TimeZone.getDefault().id
                        )
                        val conflicts = BlinkValidators.findScheduleConflicts(candidate, existingSchedules)
                        if (conflicts.isNotEmpty()) {
                            errorMsg = "Conflicts with '${conflicts.first().scheduleName}'."
                            return@Button
                        }
                        onSave(candidate) { ok, err ->
                            if (!ok) errorMsg = err
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(strings.save)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
