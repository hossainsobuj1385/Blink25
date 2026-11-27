package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.BlinkValidators
import com.example.domain.SoundType
import com.example.domain.ThemeMode
import com.example.i18n.LocalStrings
import com.example.viewmodel.BlinkDashboardData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    data: BlinkDashboardData,
    hasNotificationPermission: Boolean,
    exportedJson: String?,
    onDismissExportedJson: () -> Unit,
    onShareExportedJson: (String) -> Unit,
    onUpdateSettings: (Map<String, Any?>) -> Unit,
    onPreviewSound: (SoundType, Float) -> Unit,
    onExportData: () -> Unit,
    onSubmitSupportRequest: (String, String, (Boolean, String?) -> Unit) -> Unit,
    onOpenAdminPanel: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    onFullScreenMode: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val account = data.account ?: return

    var displayNameInput by remember(account.displayName) { mutableStateOf(account.displayName) }
    var volume by remember(account.soundVolume) { mutableFloatStateOf(account.soundVolume.toFloat()) }
    var quietStart by remember(account.quietHoursStart) { mutableStateOf(account.quietHoursStart) }
    var quietEnd by remember(account.quietHoursEnd) { mutableStateOf(account.quietHoursEnd) }
    var quietError by remember { mutableStateOf<String?>(null) }

    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showSupportScreen by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var permissionGranted by remember(hasNotificationPermission) { mutableStateOf(hasNotificationPermission) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            permissionGranted = granted
            onUpdateSettings(mapOf("notificationsEnabled" to granted))
        }
    )

    if (showSupportScreen) {
        SupportFormScreen(
            onFullScreenMode = onFullScreenMode,
            onDismiss = { showSupportScreen = false },
            onSubmit = { subject, message, cb ->
                onSubmitSupportRequest(subject, message) { ok, err ->
                    if (ok) showSupportScreen = false
                    cb(ok, err)
                }
            },
            modifier = modifier
        )
        return
    }

    if (exportedJson != null) {
        ExportDataScreen(
            exportedJson = exportedJson,
            onFullScreenMode = onFullScreenMode,
            onDismiss = onDismissExportedJson,
            onShare = { onShareExportedJson(exportedJson) },
            modifier = modifier
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = strings.settingsTitle,
            style = MaterialTheme.typography.headlineMedium
        )

        // Account Card
        Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(strings.accountSection, style = MaterialTheme.typography.titleMedium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = displayNameInput,
                            onValueChange = { displayNameInput = it },
                            label = { Text("Display Name") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { if (it.isFocused) keyboardController?.show() }
                        )
                        FilledTonalButton(
                            enabled = displayNameInput.trim().length in 1..80 && displayNameInput.trim() != account.displayName,
                            onClick = { onUpdateSettings(mapOf("displayName" to displayNameInput.trim())) }
                        ) {
                            Text(strings.save)
                        }
                    }
                    if (!account.email.isNullOrBlank()) {
                        SelectionContainer {
                            Text(
                                text = account.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onSignOut,
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
                            Text("  ${strings.signOut}")
                        }
                        if (data.isAdmin) {
                            FilledTonalButton(
                                onClick = onOpenAdminPanel,
                                modifier = Modifier.testTag("open_admin_button")
                            ) {
                                Icon(Icons.Outlined.AdminPanelSettings, contentDescription = null)
                                Text("  ${strings.adminPanelTitle}")
                            }
                        }
                    }
                }
        }

        // Appearance & Language
        Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(strings.appearanceSection, style = MaterialTheme.typography.titleMedium)
                    Text(strings.themeLabel, style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val currentTheme = ThemeMode.fromRaw(account.themeMode)
                        listOf(
                            ThemeMode.SYSTEM to strings.themeSystem,
                            ThemeMode.LIGHT to strings.themeLight,
                            ThemeMode.DARK to strings.themeDark
                        ).forEach { (mode, label) ->
                            FilterChip(
                                selected = currentTheme == mode,
                                onClick = { onUpdateSettings(mapOf("themeMode" to mode.name)) },
                                label = { Text(label) }
                            )
                        }
                    }

                    Text(strings.languageLabel, style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = account.languageCode == "en",
                            onClick = { onUpdateSettings(mapOf("languageCode" to "en")) },
                            label = { Text("English") }
                        )
                        FilterChip(
                            selected = account.languageCode == "bn",
                            onClick = { onUpdateSettings(mapOf("languageCode" to "bn")) },
                            label = { Text("বাংলা (Bengali)") }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(strings.reducedMotionLabel, style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = account.reducedMotion,
                            onCheckedChange = { onUpdateSettings(mapOf("reducedMotion" to it)) }
                        )
                    }
                }
        }

        // Audio, Notifications & Quiet Hours
        Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(strings.audioSection, style = MaterialTheme.typography.titleMedium)

                    val selectedSound = SoundType.fromRaw(account.soundType)
                    Text(strings.reminderSoundLabel, style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SoundType.entries.forEach { st ->
                            FilterChip(
                                selected = selectedSound == st,
                                onClick = { onUpdateSettings(mapOf("soundType" to st.name)) },
                                label = { Text(if (account.languageCode == "bn") st.displayNameBn else st.displayNameEn) }
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = volume,
                            onValueChange = { volume = it },
                            onValueChangeFinished = {
                                onUpdateSettings(mapOf("soundVolume" to volume.toDouble()))
                            },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onPreviewSound(selectedSound, volume) }) {
                            Icon(Icons.Outlined.VolumeUp, contentDescription = strings.previewSound)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Notifications, contentDescription = null)
                            Text(strings.notificationsLabel, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (!permissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            FilledTonalButton(onClick = {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }) {
                                Text(strings.grantPermission)
                            }
                        } else {
                            Switch(
                                checked = account.notificationsEnabled,
                                onCheckedChange = { onUpdateSettings(mapOf("notificationsEnabled" to it)) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(strings.quietHoursLabel, style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = account.quietHoursEnabled,
                            onCheckedChange = { onUpdateSettings(mapOf("quietHoursEnabled" to it)) }
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = quietStart,
                            onValueChange = { quietStart = it },
                            label = { Text(strings.quietHoursStart) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { if (it.isFocused) keyboardController?.show() }
                        )
                        OutlinedTextField(
                            value = quietEnd,
                            onValueChange = { quietEnd = it },
                            label = { Text(strings.quietHoursEnd) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { if (it.isFocused) keyboardController?.show() }
                        )
                    }
                    if (quietError != null) {
                        Text(quietError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = {
                        if (!BlinkValidators.validateTimeHHMM(quietStart) || !BlinkValidators.validateTimeHHMM(quietEnd)) {
                            quietError = "Use 24-hour HH:mm format (e.g. 22:00 and 07:00)."
                        } else {
                            quietError = null
                            onUpdateSettings(
                                mapOf(
                                    "quietHoursStart" to quietStart.trim(),
                                    "quietHoursEnd" to quietEnd.trim()
                                )
                            )
                        }
                    }) {
                        Text(strings.save)
                    }
                }
        }

        // Privacy, Export, Support & Delete Account
        Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(strings.privacySecuritySection, style = MaterialTheme.typography.titleMedium)

                    OutlinedButton(
                        onClick = { showPrivacyDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.PrivacyTip, contentDescription = null)
                        Text("  ${strings.privacyPolicyTitle}")
                    }

                OutlinedButton(
                    onClick = onExportData,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_data_button")
                ) {
                        Icon(Icons.Outlined.Download, contentDescription = null)
                        Text("  ${strings.exportData}")
                    }

                    OutlinedButton(
                        onClick = { showSupportScreen = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("support_request_button")
                    ) {
                        Icon(Icons.Outlined.SupportAgent, contentDescription = null)
                        Text("  ${strings.supportTitle}")
                    }

                    Button(
                        onClick = { showDeleteDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("delete_account_button")
                    ) {
                        Icon(Icons.Outlined.DeleteForever, contentDescription = null)
                        Text("  ${strings.deleteAccount}")
                    }

                    Text(
                        text = strings.aboutDisclaimer,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
        }

        Spacer(Modifier.height(12.dp))
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text(strings.privacyPolicyTitle) },
            text = {
                SelectionContainer {
                    Text(strings.privacyPolicyBody, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text(strings.dismiss)
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(strings.deleteAccount) },
            text = { Text(strings.deleteAccountConfirm) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteAccount()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(strings.delete)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportDataScreen(
    exportedJson: String,
    onFullScreenMode: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onDismiss)
    DisposableEffect(Unit) {
        onFullScreenMode(true)
        onDispose { onFullScreenMode(false) }
    }
    val strings = LocalStrings.current
    val clipboardManager = LocalClipboardManager.current
    var copiedFeedback by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(strings.exportData) },
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
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                SelectionContainer {
                    Text(
                        text = exportedJson,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp)
                    )
                }
            }
            if (copiedFeedback) {
                Text(
                    text = "Copied to clipboard",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(exportedJson))
                        copiedFeedback = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                    Text("  Copy")
                }
                Button(
                    onClick = onShare,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null)
                    Text("  Share")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupportFormScreen(
    onFullScreenMode: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: (String, String, (Boolean, String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onDismiss)
    DisposableEffect(Unit) {
        onFullScreenMode(true)
        onDispose { onFullScreenMode(false) }
    }
    val strings = LocalStrings.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(strings.sendSupportRequest) },
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
                value = subject,
                onValueChange = { subject = it },
                label = { Text("Subject") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (it.isFocused) keyboardController?.show() }
            )
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Message") },
                minLines = 4,
                maxLines = 8,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Default),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (it.isFocused) keyboardController?.show() }
            )
            if (err != null) {
                Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
                        onSubmit(subject, message) { ok, e ->
                            if (!ok) err = e
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(strings.save)
                }
            }
        }
    }
}
