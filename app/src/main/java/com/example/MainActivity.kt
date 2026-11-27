package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.BlinkRepository
import com.example.domain.ThemeMode
import com.example.i18n.LocalStrings
import com.example.i18n.stringsForLanguage
import com.example.platform.BlinkScreenService
import com.example.platform.DevicePrefs
import com.example.platform.ReminderNotificationHelper
import com.example.platform.SoundPlayer
import com.example.platform.authStateFlow
import com.example.platform.signOut
import com.example.ui.screens.AboutBlinkScreen
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ProfilesAndSchedulesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StatisticsScreen
import com.example.ui.screens.UserGuideScreen
import com.example.ui.theme.Blink25Theme
import com.example.viewmodel.BlinkEvent
import com.example.viewmodel.BlinkViewModel
import com.example.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppRoot()
        }
    }
}

@Composable
fun AppRoot(auth: FirebaseAuth = Firebase.auth) {
    val context = LocalContext.current
    val devicePrefs = remember(context) { DevicePrefs(context.applicationContext) }
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val user = currentUser
    if (user == null) {
        LaunchedEffect(Unit) {
            BlinkScreenService.syncWithPreference(context, enabled = false, isSignedIn = false)
        }
        Blink25Theme(themeMode = devicePrefs.getCachedThemeMode()) {
            CompositionLocalProvider(LocalStrings provides stringsForLanguage(devicePrefs.getCachedLanguageCode())) {
                AuthScreen(onAuthSuccess = {})
            }
        }
    } else {
        key(user.uid) {
            AuthenticatedBlinkShell(currentUserId = user.uid, devicePrefs = devicePrefs)
        }
    }
}

@Composable
private fun AuthenticatedBlinkShell(
    currentUserId: String,
    devicePrefs: DevicePrefs
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val notificationHelper = remember(context) { ReminderNotificationHelper(context.applicationContext) }
    val snackbarHostState = remember { SnackbarHostState() }

    val viewModel: BlinkViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val repo = BlinkRepository(db)
                BlinkViewModel(
                    repository = repo,
                    currentUserId = currentUserId,
                    soundPlayer = SoundPlayer(),
                    notificationHelper = ReminderNotificationHelper(app),
                    devicePrefs = DevicePrefs(app)
                )
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val runtime by viewModel.runtime.collectAsStateWithLifecycle()
    val autoActiveOnScreenOn by viewModel.autoActiveOnScreenOn.collectAsStateWithLifecycle()
    val statsRange by viewModel.selectedStatsRange.collectAsStateWithLifecycle()
    val adminData by viewModel.adminState.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showAdminScreen by remember { mutableStateOf(false) }
    var showGuideScreen by remember { mutableStateOf(false) }
    var showAboutScreen by remember { mutableStateOf(false) }
    var hideBottomNav by remember { mutableStateOf(false) }
    var exportedJson by remember { mutableStateOf<String?>(null) }

    // Keep screen-on background service synced with user preference on startup
    LaunchedEffect(autoActiveOnScreenOn, currentUserId) {
        BlinkScreenService.syncWithPreference(
            context = context,
            enabled = autoActiveOnScreenOn,
            isSignedIn = currentUserId.isNotBlank()
        )
    }

    // Pause 1-second UI ticker in background to minimize battery; monitor screen ON/OFF interactively
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onUiVisibilityChanged(true)
                Lifecycle.Event.ON_STOP -> viewModel.onUiVisibilityChanged(false)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        val pm = context.getSystemService(PowerManager::class.java)
        viewModel.onScreenInteractiveChanged(pm?.isInteractive ?: true)

        val screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> viewModel.onScreenInteractiveChanged(false)
                    Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> viewModel.onScreenInteractiveChanged(true)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(context, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { context.unregisterReceiver(screenReceiver) }
            viewModel.onUiVisibilityChanged(false)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is BlinkEvent.Toast -> snackbarHostState.showSnackbar(event.message)
                is BlinkEvent.ExportJson -> {
                    exportedJson = event.json
                }
                BlinkEvent.AccountDeleted -> {
                    BlinkScreenService.syncWithPreference(context, enabled = false, isSignedIn = false)
                    signOut(credentialManager, scope)
                }
            }
        }
    }

    val successData = (uiState as? UiState.Success)?.data
    val themeMode = successData?.account?.themeMode?.let(ThemeMode::fromRaw) ?: devicePrefs.getCachedThemeMode()
    val languageCode = successData?.account?.languageCode ?: devicePrefs.getCachedLanguageCode()

    Blink25Theme(themeMode = themeMode) {
        CompositionLocalProvider(LocalStrings provides stringsForLanguage(languageCode)) {
            val strings = LocalStrings.current

            if (showAdminScreen && viewModel.isAdmin) {
                AdminScreen(
                    adminData = adminData,
                    onCreateAnnouncement = viewModel::createAnnouncement,
                    onToggleAnnouncement = viewModel::toggleAnnouncement,
                    onSetUserStatus = viewModel::setUserAccountStatus,
                    onBack = { showAdminScreen = false }
                )
            } else if (showGuideScreen) {
                UserGuideScreen(
                    languageCode = languageCode,
                    onFullScreenMode = { hideBottomNav = it },
                    onDismiss = { showGuideScreen = false }
                )
            } else if (showAboutScreen) {
                AboutBlinkScreen(
                    languageCode = languageCode,
                    onFullScreenMode = { hideBottomNav = it },
                    onDismiss = { showAboutScreen = false }
                )
            } else {
                val navItems = listOf(
                    Triple(strings.navDashboard, Icons.Filled.Home, Icons.Outlined.Home),
                    Triple(strings.navProfiles, Icons.Filled.Tune, Icons.Outlined.Tune),
                    Triple(strings.navStats, Icons.Filled.BarChart, Icons.Outlined.BarChart),
                    Triple(strings.navSettings, Icons.Filled.Settings, Icons.Outlined.Settings)
                )

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val useRail = maxWidth >= 600.dp

                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        contentWindowInsets = WindowInsets.systemBars,
                        bottomBar = {
                            if (!useRail && !hideBottomNav) {
                                NavigationBar(
                                    windowInsets = WindowInsets.navigationBars
                                ) {
                                    navItems.forEachIndexed { index, (label, filled, outlined) ->
                                        NavigationBarItem(
                                            selected = selectedTab == index,
                                            onClick = { selectedTab = index },
                                            icon = {
                                                Icon(
                                                    imageVector = if (selectedTab == index) filled else outlined,
                                                    contentDescription = label
                                                )
                                            },
                                            label = { Text(label) },
                                            modifier = Modifier.testTag("nav_tab_$index")
                                        )
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .consumeWindowInsets(innerPadding)
                        ) {
                            if (useRail && !hideBottomNav) {
                                NavigationRail(
                                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                                ) {
                                    navItems.forEachIndexed { index, (label, filled, outlined) ->
                                        NavigationRailItem(
                                            selected = selectedTab == index,
                                            onClick = { selectedTab = index },
                                            icon = {
                                                Icon(
                                                    imageVector = if (selectedTab == index) filled else outlined,
                                                    contentDescription = label
                                                )
                                            },
                                            label = { Text(label) },
                                            modifier = Modifier.testTag("nav_rail_$index")
                                        )
                                    }
                                }
                            }

                            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                                when (val state = uiState) {
                                    UiState.Loading -> {
                                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                                    }
                                    is UiState.Error -> {
                                        Column(
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Text(state.message)
                                            Button(onClick = { signOut(credentialManager, scope) }) {
                                                Text(strings.signOut)
                                            }
                                        }
                                    }
                                    is UiState.Success -> {
                                        val data = state.data
                                        if (data.account == null) {
                                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                                        } else {
                                            when (selectedTab) {
                                                0 -> DashboardScreen(
                                                    data = data,
                                                    runtime = runtime,
                                                    onStart = viewModel::startSession,
                                                    onPause = viewModel::pauseSession,
                                                    onResume = viewModel::resumeSession,
                                                    onStop = viewModel::stopSession,
                                                    onDismissBlinkCue = viewModel::dismissBlinkCue,
                                                    onSnoozeAll = viewModel::snoozeAll,
                                                    onStartEyeRestNow = viewModel::startEyeRestNow,
                                                    onDismissEyeRest = viewModel::dismissEyeRest,
                                                    onSnoozeEyeRest = viewModel::snoozeEyeRest,
                                                    onSkipEyeRest = viewModel::skipEyeRest,
                                                    onSelectProfile = viewModel::activateProfile,
                                                    onToggleSound = viewModel::toggleSound,
                                                    onToggleFocusMode = viewModel::toggleFocusMode,
                                                    onActivateMeetingMode = viewModel::activateMeetingMode,
                                                    onCancelMeetingMode = viewModel::cancelMeetingMode,
                                                    autoActiveOnScreenOn = autoActiveOnScreenOn,
                                                    hasNotificationPermission = notificationHelper.hasNotificationPermission(),
                                                    onToggleAutoActive = { enabled ->
                                                        viewModel.setAutoActiveOnScreenOn(enabled, context)
                                                    },
                                                    onOpenGuide = { showGuideScreen = true }
                                                )
                                                1 -> ProfilesAndSchedulesScreen(
                                                    data = data,
                                                    onActivateProfile = viewModel::activateProfile,
                                                    onSaveProfile = viewModel::saveProfile,
                                                    onDuplicateProfile = viewModel::duplicateProfile,
                                                    onDeleteProfile = viewModel::deleteProfile,
                                                    onSaveSchedule = viewModel::saveSchedule,
                                                    onDeleteSchedule = viewModel::deleteSchedule,
                                                    onPreviewSound = viewModel::previewSound,
                                                    onFullScreenMode = { hideBottomNav = it }
                                                )
                                                2 -> StatisticsScreen(
                                                    data = data,
                                                    selectedRange = statsRange,
                                                    onSelectRange = viewModel::setStatsRange
                                                )
                                                3 -> SettingsScreen(
                                                    data = data,
                                                    hasNotificationPermission = notificationHelper.hasNotificationPermission(),
                                                    exportedJson = exportedJson,
                                                    onDismissExportedJson = { exportedJson = null },
                                                    onShareExportedJson = { json ->
                                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                            type = "application/json"
                                                            putExtra(Intent.EXTRA_SUBJECT, "Blink25 Wellness Export")
                                                            putExtra(Intent.EXTRA_TEXT, json)
                                                        }
                                                        context.startActivity(Intent.createChooser(shareIntent, "Export Blink25 Data"))
                                                    },
                                                    onUpdateSettings = viewModel::updateSettings,
                                                    onPreviewSound = viewModel::previewSound,
                                                    onExportData = viewModel::exportUserData,
                                                    onSubmitSupportRequest = viewModel::submitSupportRequest,
                                                    onOpenAdminPanel = { showAdminScreen = true },
                                                    onSignOut = {
                                                        BlinkScreenService.syncWithPreference(context, enabled = false, isSignedIn = false)
                                                        signOut(credentialManager, scope)
                                                    },
                                                    onDeleteAccount = { viewModel.deleteAccount {} },
                                                    autoActiveOnScreenOn = autoActiveOnScreenOn,
                                                    onToggleAutoActive = { enabled ->
                                                        viewModel.setAutoActiveOnScreenOn(enabled, context)
                                                    },
                                                    onOpenGuide = { showGuideScreen = true },
                                                    onOpenAbout = { showAboutScreen = true },
                                                    onFullScreenMode = { hideBottomNav = it }
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
        }
    }
}
