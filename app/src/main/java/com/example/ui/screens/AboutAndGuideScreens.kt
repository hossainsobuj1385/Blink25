package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.RemoveRedEye
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.i18n.LocalStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutBlinkScreen(
    languageCode: String,
    onFullScreenMode: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onDismiss)
    DisposableEffect(Unit) {
        onFullScreenMode(true)
        onDispose { onFullScreenMode(false) }
    }
    val strings = LocalStrings.current
    val isBn = languageCode == "bn"

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(strings.aboutTitle) },
                navigationIcon = {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("about_back_button")
                    ) {
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Header Card
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                                )
                            )
                        )
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RemoveRedEye,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Blink25",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = if (isBn) "ভার্সন ১.০ • চোখের সুস্থ অভ্যাসের সঙ্গী" else "Version 1.0 • Healthy Screen Habit Companion",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                    Text(
                        text = if (isBn) {
                            "ফোন বা কম্পিউটারের স্ক্রিনে কাজের সময় চোখে চাপ কমানো ও নিয়মিত পলক ফেলার অভ্যাস গড়ার একটি হালকা, গোপনীয়তা-সুরক্ষিত অ্যাপ।"
                        } else {
                            "A lightweight, battery-friendly, privacy-first companion built to protect your eyes during daily phone and screen time."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            InfoSectionCard(
                icon = Icons.Outlined.Info,
                title = strings.whatIsBlinkTitle,
                body = if (isBn) {
                    "Blink25 হলো একটি স্মার্ট ও মৃদু রিমাইন্ডার অ্যাপ। এটি প্রতি ২৫ সেকেন্ড পরপর (অথবা আপনার পছন্দের বিরতিতে) মৃদু শব্দ বা অটো-সরে যাওয়া নোটিফিকেশনের মাধ্যমে চোখের পলক ফেলতে মনে করিয়ে দেয় এবং প্রতি ২০ মিনিটে ২০-২০-২০ নিয়মে ২০ সেকেন্ড দূরের দৃশ্যে তাকিয়ে চোখকে বিশ্রাম দেওয়ার নির্দেশনা দেয়।"
                } else {
                    "Blink25 gives you gentle audio and auto-dismissing visual reminders to blink naturally every 25 seconds (or your chosen interval) and take 20-second 20-20-20 eye-rest breaks every 20 minutes."
                }
            )

            InfoSectionCard(
                icon = Icons.Outlined.RemoveRedEye,
                title = strings.whyNeededTitle,
                body = if (isBn) {
                    "সাধারণ অবস্থায় মানুষ মিনিটে ১৫–২০ বার চোখের পলক ফেলে। কিন্তু মোবাইল বা স্ক্রিনের দিকে তাকিয়ে থাকলে পলক ফেলার হার প্রায় ৬০% কমে ৪–৭ বারে নেমে আসে! এর ফলে চোখ শুষ্ক হয়ে যাওয়া, জ্বালাপোড়া, ঝাপসা দেখা, মাথাব্যথা এবং দীর্ঘমেয়াদে চোখের ক্লান্তি (Digital Eye Strain) দেখা দেয়।\n\nBlink25 ব্যবহার করলে কাজের মনোযোগ নষ্ট না করেই নিয়মিত পলক ফেলা ও চোখের পেশি শিথিল রাখার সুস্থ অভ্যাস তৈরি হয়।"
                } else {
                    "Normally we blink 15–20 times per minute, but while staring at screens our blink rate drops by nearly 60% to just 4–7 times per minute. That leads to dry eyes, irritation, blurred focus, headaches, and digital eye strain.\n\nBlink25 helps restore natural eye moisture and relax focusing muscles without interrupting your workflow."
                }
            )

            InfoSectionCard(
                icon = Icons.Outlined.BatteryChargingFull,
                title = if (isBn) "ব্যাটারি সাশ্রয় ও ডিভাইস-অন সুরক্ষা" else "Low Battery & Screen-On Intelligence",
                body = if (isBn) {
                    "অ্যাপটি শুধুমাত্র তখনই কাজ করে যখন আপনার ডিভাইসের স্ক্রিন চালু থাকে। স্ক্রিন বন্ধ (Lock) হওয়ার সাথে সাথেই টাইমার অটো থেমে যায়—তাই ফোনের চার্জ অত্যন্ত কম খরচ হয়।"
                } else {
                    "When Auto-Active is enabled, Blink25 runs only while your device screen is ON and pauses immediately when the screen locks, keeping battery consumption minimal."
                }
            )

            InfoSectionCard(
                icon = Icons.Outlined.Shield,
                title = if (isBn) "সম্পূর্ণ গোপনীয়তা ও সতর্কতা" else "Privacy & Wellness Notice",
                body = strings.privacyPolicyBody
            )

            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserGuideScreen(
    languageCode: String,
    onFullScreenMode: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onDismiss)
    DisposableEffect(Unit) {
        onFullScreenMode(true)
        onDispose { onFullScreenMode(false) }
    }
    val strings = LocalStrings.current
    val isBn = languageCode == "bn"

    val steps = if (isBn) {
        listOf(
            GuideStep(
                number = "১",
                icon = Icons.Outlined.Bolt,
                title = "অটো-অ্যাক্টিভ চালু করা (ডিভাইস অন থাকলেই চলবে)",
                location = "কোথায় যাবেন: ড্যাশবোর্ড বা সেটিংস → 'ডিভাইস অন থাকলে অটো চালু'",
                details = "প্রথমবার 'অনুমতি দিন' বাটনে চাপ দিয়ে নোটিফিকেশন পারমিশন দিন এবং 'ডিভাইস অন থাকলে অটো চালু' সুইচটি অন করুন। এরপর থেকে ফোনের স্ক্রিন অন থাকলেই অ্যাপটি নিজে থেকে রিমাইন্ডার চালাবে, আর স্ক্রিন বন্ধ করলে চার্জ বাঁচাতে থেমে যাবে।"
            ),
            GuideStep(
                number = "২",
                icon = Icons.Outlined.RemoveRedEye,
                title = "কাজের সেশন ও চোখের বিশ্রাম নিয়ন্ত্রণ",
                location = "কোথায় যাবেন: ড্যাশবোর্ড (প্রথম ট্যাব)",
                details = "উপরের 'কাজের সেশনের অবস্থা' কার্ডে আপনার বর্তমান সেশনের সময় ও অবস্থা দেখাবে। এখানে 'বিশ্রাম শুরু করুন / থামান' বাটন দিয়ে যেকোনো সময় ২০ সেকেন্ডের চোখের বিশ্রাম শুরু বা বন্ধ করতে পারবেন। নিচের বড় টাইমার থেকে 'শুরু', 'বিরতি', 'স্নুজ' বা 'থামান' ব্যবহার করতে পারবেন।"
            ),
            GuideStep(
                number = "৩",
                icon = Icons.Outlined.Tune,
                title = "পছন্দমতো প্রোফাইল ও সময় সেট করা",
                location = "কোথায় যাবেন: নিচের মেনু → 'প্রোফাইল' ট্যাব",
                details = "Work, Study, Gaming, Meeting, Relax প্রোফাইল থেকে যেকোনোটি বেছে নিন অথবা নিচের '+' (নতুন প্রোফাইল) বাটনে চাপ দিয়ে নিজের মতো ব্লিংক সেকেন্ড (যেমন ১৫–৪০ সেকেন্ড), বিশ্রামের ব্যবধান, ২০-২০-২০ প্রিসেট, সাউন্ড এবং স্নুজ সময় সেট করে সংরক্ষণ করুন।"
            ),
            GuideStep(
                number = "৪",
                icon = Icons.Outlined.Schedule,
                title = "অটো টাইম শিডিউল (সময়সূচি) সেট করা",
                location = "কোথায় যাবেন: 'প্রোফাইল' ট্যাব → 'সাপ্তাহিক সময়সূচি'",
                details = "নতুন সময়সূচি তৈরি করে শুরুর সময় (যেমন 09:00), শেষের সময় (যেমন 17:00), সপ্তাহের দিনগুলো এবং কোন প্রোফাইল চলবে তা সিলেক্ট করুন। শিডিউল অন থাকলে কেবল নির্ধারিত সময়ের মধ্যেই অটো-রিমাইন্ডার কাজ করবে।"
            ),
            GuideStep(
                number = "৫",
                icon = Icons.Outlined.VolumeUp,
                title = "সাউন্ডের ধরন, উচ্চ ভলিউম ও ভাষা পরিবর্তন",
                location = "কোথায় যাবেন: নিচের মেনু → 'সেটিংস' ট্যাব",
                details = "সেটিংস বা প্রোফাইল এডিটর থেকে ১১টির বেশি সাউন্ড (যেমন Crystal Ping, Water Drop, Gentle Bell, Bright Chord) বেছে নিয়ে স্লাইডার দিয়ে ১০০% পর্যন্ত ভলিউম বাড়িয়ে 'শুনুন' আইকনে চাপ দিয়ে টেস্ট করতে পারবেন। এখান থেকেই থিম (Light/Dark), ভাষা (বাংলা/English) ও নীরব সময় (Quiet Hours) সেট করা যায়।"
            ),
            GuideStep(
                number = "৬",
                icon = Icons.Outlined.NotificationsActive,
                title = "অটো-সরে যাওয়া নোটিফিকেশন ও দ্রুত মোড",
                location = "কোথায় যাবেন: ড্যাশবোর্ড → দ্রুত নিয়ন্ত্রণ",
                details = "রিমাইন্ডার নোটিফিকেশনগুলো কয়েক সেকেন্ড পর (এবং চোখের বিশ্রাম শেষ হলেই) নিজে থেকেই সরে যায় যাতে স্ক্রিনে কোনো ঝামেলা না হয়। কাজের চাপে থাকলে ড্যাশবোর্ড থেকে 'ফোকাস মোড' বা 'মিটিং মোড' অন করে শব্দ সাময়িকভাবে বন্ধ রাখতে পারেন।"
            )
        )
    } else {
        listOf(
            GuideStep(
                number = "1",
                icon = Icons.Outlined.Bolt,
                title = "Enable Auto-Active When Device Is On",
                location = "Where: Dashboard banner or Settings → 'Auto-Active When Device Is On'",
                details = "Grant Notification permission once and turn ON 'Auto-Active When Device Is On'. Blink25 will automatically run only while your device screen is ON and pause the instant your screen turns off to preserve battery."
            ),
            GuideStep(
                number = "2",
                icon = Icons.Outlined.RemoveRedEye,
                title = "Control Work Sessions & Eye-Rest Timer",
                location = "Where: Dashboard (first tab)",
                details = "The Work Session Status card displays your active/paused state, profile, schedule, and elapsed time. Tap 'Start Eye Rest' / 'Stop Eye Rest' anytime to toggle a 20-second break, or use the main circular timer to Start, Pause, Snooze, or Stop."
            ),
            GuideStep(
                number = "3",
                icon = Icons.Outlined.Tune,
                title = "Customize Profiles & Preferences",
                location = "Where: Bottom bar → 'Profiles' tab",
                details = "Switch between Work, Study, Gaming, Meeting, or Relax, or tap '+' to create your own profile with custom blink intervals (5–300s), early warnings, 20-20-20 eye rest presets, sound tone, volume, and snooze minutes."
            ),
            GuideStep(
                number = "4",
                icon = Icons.Outlined.Schedule,
                title = "Set Weekly Time Schedules",
                location = "Where: 'Profiles' tab → Weekly Schedules",
                details = "Create a schedule with Start/End time (24h HH:mm), active weekdays, and a linked profile. When schedules are enabled, Auto-Active runs strictly inside your chosen scheduled hours."
            ),
            GuideStep(
                number = "5",
                icon = Icons.Outlined.VolumeUp,
                title = "Choose Louder Sounds, Theme & Language",
                location = "Where: Bottom bar → 'Settings' tab",
                details = "Pick from 11+ crisp tones (such as Crystal Ping, Water Drop, Gentle Bell, Bright Chord), adjust the volume slider up to 100%, and tap the speaker icon to preview. Switch between English/বাংলা, Light/Dark theme, and Quiet Hours."
            ),
            GuideStep(
                number = "6",
                icon = Icons.Outlined.NotificationsActive,
                title = "Auto-Dismissing Notifications & Quick Modes",
                location = "Where: Dashboard → Quick Controls",
                details = "Blink cues auto-dismiss after ~4.5 seconds and eye-rest notifications auto-dismiss as soon as your break finishes so they never clutter your notification bar. Use Focus Mode or Meeting Mode whenever you need quiet concentration."
            )
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(strings.guideTitle) },
                navigationIcon = {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("guide_back_button")
                    ) {
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            steps.forEach { step ->
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = step.number,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Icon(
                                imageVector = step.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = step.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = step.location,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }

                        Text(
                            text = step.details,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun InfoSectionCard(
    icon: ImageVector,
    title: String,
    body: String
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class GuideStep(
    val number: String,
    val icon: ImageVector,
    val title: String,
    val location: String,
    val details: String
)
