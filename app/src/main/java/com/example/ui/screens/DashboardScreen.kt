package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.RemoveRedEye
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.AccountStatus
import com.example.i18n.LocalStrings
import com.example.ui.components.WorkSessionStatusCard
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.viewmodel.BlinkDashboardData
import com.example.viewmodel.EngineStatus
import com.example.viewmodel.ReminderRuntimeState
import java.time.LocalDate

@Composable
fun DashboardScreen(
    data: BlinkDashboardData,
    runtime: ReminderRuntimeState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onDismissBlinkCue: () -> Unit,
    onSnoozeAll: (Int) -> Unit,
    onStartEyeRestNow: () -> Unit,
    onDismissEyeRest: () -> Unit,
    onSnoozeEyeRest: () -> Unit,
    onSkipEyeRest: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onToggleSound: () -> Unit,
    onToggleFocusMode: () -> Unit,
    onActivateMeetingMode: (Int) -> Unit,
    onCancelMeetingMode: () -> Unit,
    autoActiveOnScreenOn: Boolean = true,
    hasNotificationPermission: Boolean = true,
    onToggleAutoActive: (Boolean) -> Unit = {},
    onOpenGuide: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val account = data.account
    val activeProfile = data.activeProfile
    val todayKey = remember { LocalDate.now().toString() }
    val todaySessions = remember(data.sessions, todayKey) {
        data.sessions.filter { it.dayKey == todayKey }
    }
    var showMeetingPicker by remember { mutableStateOf(false) }
    var permissionGrantedLocally by remember(hasNotificationPermission) {
        mutableStateOf(hasNotificationPermission)
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGrantedLocally = granted
        onToggleAutoActive(true)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth >= 660.dp

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header + Guide Shortcut + Status
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = strings.appName,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = strings.tagline,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = onOpenGuide,
                            modifier = Modifier.testTag("open_guide_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                                contentDescription = strings.guideTitle,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        StatusBadge(accountStatus = account?.accountStatus, runtime = runtime)
                    }
                }
            }

            // Discreet In-App Blink Cue Banner (never blocks screen or steals focus)
            item {
                AnimatedVisibility(visible = runtime.showBlinkCue) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("blink_cue_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Visibility,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = strings.blinkCueTitle,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = strings.blinkCueSubtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                    )
                                }
                            }
                            TextButton(
                                onClick = onDismissBlinkCue,
                                modifier = Modifier.testTag("dismiss_blink_cue_button")
                            ) {
                                Text(strings.dismiss)
                            }
                        }
                    }
                }
            }

            // Published Announcements (if any)
            if (data.announcements.isNotEmpty()) {
                item {
                    val latest = data.announcements.first()
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Campaign,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Column {
                                Text(
                                    text = latest.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = latest.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }

            // Profile Switcher Chips
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = strings.activeProfile,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        data.profiles.forEach { profile ->
                            val isSelected = profile.id == activeProfile?.id
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSelectProfile(profile.id) },
                                label = { Text("${profile.name} • ${profile.blinkIntervalSeconds}s") },
                                modifier = Modifier.testTag("profile_chip_${profile.id}")
                            )
                        }
                    }
                }
            }

            // Screen-On Auto-Active Card (one-time permission + schedule-aware background guard)
            item {
                AutoActiveScreenGuardCard(
                    enabled = autoActiveOnScreenOn,
                    hasNotificationPermission = permissionGrantedLocally,
                    activeScheduleName = runtime.activeScheduleName,
                    onToggle = { enabled ->
                        if (enabled && !permissionGrantedLocally && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            onToggleAutoActive(enabled)
                        }
                    }
                )
            }

            // Work Session Status + Eye-Rest Timer Start/Stop Card
            item {
                WorkSessionStatusCard(
                    engineStatus = runtime.engineStatus,
                    sessionElapsedSeconds = runtime.sessionElapsedSeconds,
                    profileName = activeProfile?.name ?: "Work",
                    activeScheduleName = runtime.activeScheduleName,
                    isQuietHoursNow = runtime.isQuietHoursNow,
                    accountStatus = account?.accountStatus,
                    isEyeRestActive = runtime.isEyeRestActive,
                    nextEyeRestSeconds = runtime.nextEyeRestSeconds,
                    eyeRestRemainingSeconds = runtime.eyeRestRemainingSeconds,
                    eyeRestDurationSeconds = (activeProfile?.eyeRestDurationSeconds ?: 20L).toInt(),
                    onStartEyeRest = onStartEyeRestNow,
                    onStopEyeRest = onDismissEyeRest
                )
            }

            // Main Adaptive Grid: Timer Hero + Eye-Rest / Quick Settings
            if (isWide) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1.1f),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            BlinkTimerHeroCard(
                                runtime = runtime,
                                profileName = activeProfile?.name ?: "Work",
                                reducedMotion = account?.reducedMotion == true,
                                onStart = onStart,
                                onPause = onPause,
                                onResume = onResume,
                                onStop = onStop,
                                onSnoozeAll = { onSnoozeAll((activeProfile?.snoozeMinutes ?: 5L).toInt()) }
                            )
                            TodayStatsCard(
                                runtime = runtime,
                                pastSessionsToday = todaySessions.size,
                                pastBlinksToday = todaySessions.sumOf { it.blinksTriggered.toInt() },
                                pastRestsToday = todaySessions.sumOf { it.eyeRestsCompleted.toInt() }
                            )
                        }
                        Column(
                            modifier = Modifier.weight(0.9f),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            EyeRestCard(
                                runtime = runtime,
                                eyeRestIntervalMin = (activeProfile?.eyeRestIntervalMinutes ?: 20L).toInt(),
                                eyeRestDurationSec = (activeProfile?.eyeRestDurationSeconds ?: 20L).toInt(),
                                onStartRestNow = onStartEyeRestNow,
                                onDismiss = onDismissEyeRest,
                                onSnooze = onSnoozeEyeRest,
                                onSkip = onSkipEyeRest
                            )
                            QuickControlsCard(
                                soundEnabled = account?.soundEnabled != false,
                                focusModeActive = account?.focusModeActive == true,
                                meetingModeActive = account?.meetingModeActive == true,
                                meetingRemainingSec = runtime.meetingRemainingSeconds,
                                activeScheduleName = runtime.activeScheduleName,
                                onToggleSound = onToggleSound,
                                onToggleFocus = onToggleFocusMode,
                                onMeetingClick = {
                                    if (account?.meetingModeActive == true) onCancelMeetingMode()
                                    else showMeetingPicker = !showMeetingPicker
                                },
                                showMeetingPicker = showMeetingPicker,
                                onSelectMeetingMinutes = { mins ->
                                    showMeetingPicker = false
                                    onActivateMeetingMode(mins)
                                }
                            )
                        }
                    }
                }
            } else {
                item {
                    BlinkTimerHeroCard(
                        runtime = runtime,
                        profileName = activeProfile?.name ?: "Work",
                        reducedMotion = account?.reducedMotion == true,
                        onStart = onStart,
                        onPause = onPause,
                        onResume = onResume,
                        onStop = onStop,
                        onSnoozeAll = { onSnoozeAll((activeProfile?.snoozeMinutes ?: 5L).toInt()) }
                    )
                }
                item {
                    EyeRestCard(
                        runtime = runtime,
                        eyeRestIntervalMin = (activeProfile?.eyeRestIntervalMinutes ?: 20L).toInt(),
                        eyeRestDurationSec = (activeProfile?.eyeRestDurationSeconds ?: 20L).toInt(),
                        onStartRestNow = onStartEyeRestNow,
                        onDismiss = onDismissEyeRest,
                        onSnooze = onSnoozeEyeRest,
                        onSkip = onSkipEyeRest
                    )
                }
                item {
                    QuickControlsCard(
                        soundEnabled = account?.soundEnabled != false,
                        focusModeActive = account?.focusModeActive == true,
                        meetingModeActive = account?.meetingModeActive == true,
                        meetingRemainingSec = runtime.meetingRemainingSeconds,
                        activeScheduleName = runtime.activeScheduleName,
                        onToggleSound = onToggleSound,
                        onToggleFocus = onToggleFocusMode,
                        onMeetingClick = {
                            if (account?.meetingModeActive == true) onCancelMeetingMode()
                            else showMeetingPicker = !showMeetingPicker
                        },
                        showMeetingPicker = showMeetingPicker,
                        onSelectMeetingMinutes = { mins ->
                            showMeetingPicker = false
                            onActivateMeetingMode(mins)
                        }
                    )
                }
                item {
                    TodayStatsCard(
                        runtime = runtime,
                        pastSessionsToday = todaySessions.size,
                        pastBlinksToday = todaySessions.sumOf { it.blinksTriggered.toInt() },
                        pastRestsToday = todaySessions.sumOf { it.eyeRestsCompleted.toInt() }
                    )
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun StatusBadge(accountStatus: String?, runtime: ReminderRuntimeState) {
    val strings = LocalStrings.current
    val (label, containerColor, contentColor) = when {
        AccountStatus.fromRaw(accountStatus) == AccountStatus.SUSPENDED -> Triple(
            strings.statusSuspended,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        runtime.isQuietHoursNow -> Triple(
            strings.statusQuietHours,
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        runtime.engineStatus == EngineStatus.RUNNING -> Triple(
            strings.statusRunning,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        runtime.engineStatus == EngineStatus.PAUSED -> Triple(
            strings.statusPaused,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        else -> Triple(
            strings.statusIdle,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Surface(
        color = containerColor,
        shape = CircleShape
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun BlinkTimerHeroCard(
    runtime: ReminderRuntimeState,
    profileName: String,
    reducedMotion: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onSnoozeAll: () -> Unit
) {
    val strings = LocalStrings.current
    val targetFraction = if (runtime.effectiveBlinkInterval > 0) {
        runtime.nextBlinkSeconds.toFloat() / runtime.effectiveBlinkInterval.toFloat()
    } else 1f
    val progress by animateFloatAsState(
        targetValue = targetFraction.coerceIn(0f, 1f),
        animationSpec = if (reducedMotion) spring(stiffness = Spring.StiffnessHigh)
        else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "blink_ring"
    )

    val ringTrack = MaterialTheme.colorScheme.surfaceVariant
    val ringActive = if (runtime.isEarlyWarningActive) MaterialTheme.colorScheme.tertiary
    else MaterialTheme.colorScheme.primary

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Circular Breath-Pace Countdown
            Box(
                modifier = Modifier.size(188.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 12.dp.toPx()
                    val pad = stroke / 2f
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(
                        color = ringTrack,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(pad, pad),
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = ringActive,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        topLeft = Offset(pad, pad),
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = profileName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatMmSs(runtime.nextBlinkSeconds),
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (runtime.snoozedRemainingSeconds > 0) {
                            "Snoozed ${formatMmSs(runtime.snoozedRemainingSeconds)}"
                        } else if (runtime.isEarlyWarningActive) {
                            strings.earlyWarningSoon
                        } else {
                            "${strings.nextBlinkIn} • ${runtime.effectiveBlinkInterval}s"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (runtime.isEarlyWarningActive) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Primary Playback Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (runtime.engineStatus) {
                    EngineStatus.IDLE -> {
                        Button(
                            onClick = onStart,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("start_timer_button")
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(strings.start)
                        }
                    }
                    EngineStatus.RUNNING -> {
                        FilledTonalButton(
                            onClick = onPause,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("pause_timer_button")
                        ) {
                            Icon(Icons.Filled.Pause, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(strings.pause)
                        }
                        OutlinedButton(
                            onClick = onSnoozeAll,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .height(50.dp)
                                .testTag("snooze_timer_button")
                        ) {
                            Icon(Icons.Outlined.Snooze, contentDescription = strings.snooze)
                        }
                        OutlinedButton(
                            onClick = onStop,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .height(50.dp)
                                .testTag("stop_timer_button")
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = strings.stop)
                        }
                    }
                    EngineStatus.PAUSED -> {
                        Button(
                            onClick = onResume,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("resume_timer_button")
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(strings.resume)
                        }
                        OutlinedButton(
                            onClick = onStop,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .height(50.dp)
                                .testTag("stop_timer_button")
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = strings.stop)
                            Spacer(Modifier.width(6.dp))
                            Text(strings.stop)
                        }
                    }
                }
            }

            // Session elapsed readout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${strings.sessionDuration}: ${formatMmSs(runtime.sessionElapsedSeconds)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${strings.blinksTriggeredLabel}: ${runtime.sessionBlinksTriggered}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EyeRestCard(
    runtime: ReminderRuntimeState,
    eyeRestIntervalMin: Int,
    eyeRestDurationSec: Int,
    onStartRestNow: () -> Unit,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
    onSkip: () -> Unit
) {
    val strings = LocalStrings.current
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (runtime.isEyeRestActive) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(
                painter = painterResource(id = R.drawable.img_eye_rest_sanctuary),
                contentDescription = "Distant horizon illustration for eye rest",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
            )
            Column(
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.eyeRestTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (runtime.isEyeRestActive) strings.eyeRestGuidance
                            else "${strings.nextEyeRestIn}: ${formatMmSs(runtime.nextEyeRestSeconds)} (${eyeRestIntervalMin}m / ${eyeRestDurationSec}s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = if (runtime.isEyeRestActive) formatMmSs(runtime.eyeRestRemainingSeconds)
                        else "${eyeRestDurationSec}s",
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (runtime.isEyeRestActive && eyeRestDurationSec > 0) {
                    LinearProgressIndicator(
                        progress = { (runtime.eyeRestRemainingSeconds.toFloat() / eyeRestDurationSec).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                    )
                }

                Text(
                    text = strings.twentyTwentyTwentyNote,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!runtime.isEyeRestActive) {
                        FilledTonalButton(
                            onClick = onStartRestNow,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("start_eye_rest_button")
                        ) {
                            Text(strings.startRestNow)
                        }
                        OutlinedButton(
                            onClick = onSnooze,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(strings.snooze)
                        }
                    } else {
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("stop_eye_rest_button")
                        ) {
                            Text(strings.stopEyeRest)
                        }
                        OutlinedButton(
                            onClick = onSnooze,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(strings.snooze)
                        }
                        TextButton(
                            onClick = onSkip,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(strings.skip)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickControlsCard(
    soundEnabled: Boolean,
    focusModeActive: Boolean,
    meetingModeActive: Boolean,
    meetingRemainingSec: Int,
    activeScheduleName: String?,
    onToggleSound: () -> Unit,
    onToggleFocus: () -> Unit,
    onMeetingClick: () -> Unit,
    showMeetingPicker: Boolean,
    onSelectMeetingMinutes: (Int) -> Unit
) {
    val strings = LocalStrings.current
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = strings.quickSettings,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Sound toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (soundEnabled) Icons.AutoMirrored.Outlined.VolumeUp else Icons.AutoMirrored.Outlined.VolumeOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (soundEnabled) strings.soundOn else strings.soundMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Switch(
                    checked = soundEnabled,
                    onCheckedChange = { onToggleSound() },
                    modifier = Modifier.testTag("quick_sound_switch")
                )
            }

            // Focus Mode toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CenterFocusStrong,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(strings.focusMode, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            strings.focusModeDesc,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = focusModeActive,
                    onCheckedChange = { onToggleFocus() },
                    modifier = Modifier.testTag("quick_focus_switch")
                )
            }

            // Meeting Mode toggle + duration selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Groups,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(strings.meetingMode, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = if (meetingModeActive && meetingRemainingSec > 0) {
                                "Active • ${formatMmSs(meetingRemainingSec)} left"
                            } else strings.meetingModeDesc,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = meetingModeActive,
                    onCheckedChange = { onMeetingClick() },
                    modifier = Modifier.testTag("quick_meeting_switch")
                )
            }

            AnimatedVisibility(visible = showMeetingPicker && !meetingModeActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(15, 30, 45, 60, 90).forEach { mins ->
                        AssistChip(
                            onClick = { onSelectMeetingMinutes(mins) },
                            label = { Text("${mins}m") }
                        )
                    }
                }
            }

            // Schedule status row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "${strings.currentSchedule}: ${activeScheduleName ?: strings.noActiveSchedule}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TodayStatsCard(
    runtime: ReminderRuntimeState,
    pastSessionsToday: Int,
    pastBlinksToday: Int,
    pastRestsToday: Int
) {
    val strings = LocalStrings.current
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = strings.todaySummary,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricPill(
                    label = strings.totalSessions,
                    value = (pastSessionsToday + if (runtime.sessionElapsedSeconds >= 5) 1 else 0).toString()
                )
                MetricPill(
                    label = strings.blinksTriggeredLabel,
                    value = (pastBlinksToday + runtime.sessionBlinksTriggered).toString()
                )
                MetricPill(
                    label = strings.eyeRestsLabel,
                    value = (pastRestsToday + runtime.sessionEyeRestsCompleted).toString()
                )
            }
        }
    }
}

@Composable
private fun MetricPill(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = value,
            fontFamily = JetBrainsMonoFontFamily,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AutoActiveScreenGuardCard(
    enabled: Boolean,
    hasNotificationPermission: Boolean,
    activeScheduleName: String?,
    onToggle: (Boolean) -> Unit
) {
    val strings = LocalStrings.current
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
            else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("auto_active_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (enabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Bolt,
                        contentDescription = null,
                        tint = if (enabled) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = strings.autoActiveTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            strings.autoActivePermissionHint
                        } else if (enabled && activeScheduleName != null) {
                            "${strings.currentSchedule}: $activeScheduleName"
                        } else {
                            strings.autoActiveDesc
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.testTag("dashboard_auto_active_switch")
            )
        }
    }
}

fun formatMmSs(totalSeconds: Int): String {
    val clamped = totalSeconds.coerceAtLeast(0)
    val mins = clamped / 60
    val secs = clamped % 60
    return "%02d:%02d".format(mins, secs)
}
