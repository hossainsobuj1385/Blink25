package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.AccountStatus
import com.example.i18n.LocalStrings
import com.example.viewmodel.AdminPanelData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    adminData: AdminPanelData,
    onCreateAnnouncement: (String, String, Boolean) -> Unit,
    onToggleAnnouncement: (String, Boolean) -> Unit,
    onSetUserStatus: (String, AccountStatus) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val strings = LocalStrings.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var annTitle by remember { mutableStateOf("") }
    var annMessage by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            TopAppBar(
                title = { Text(strings.adminPanelTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Publish Announcement", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = annTitle,
                            onValueChange = { annTitle = it },
                            label = { Text("Title") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { if (it.isFocused) keyboardController?.show() }
                        )
                        OutlinedTextField(
                            value = annMessage,
                            onValueChange = { annMessage = it },
                            label = { Text("Message") },
                            minLines = 2,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Default),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { if (it.isFocused) keyboardController?.show() }
                        )
                        Button(
                            onClick = {
                                if (annTitle.isNotBlank() && annMessage.isNotBlank()) {
                                    onCreateAnnouncement(annTitle, annMessage, true)
                                    annTitle = ""
                                    annMessage = ""
                                }
                            }
                        ) {
                            Text("Publish")
                        }
                    }
                }
            }

            if (adminData.announcements.isNotEmpty()) {
                item { Text("Announcements", style = MaterialTheme.typography.titleMedium) }
                items(adminData.announcements, key = { it.id }) { ann ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ann.title, style = MaterialTheme.typography.titleSmall)
                                Text(ann.message, style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(
                                checked = ann.published,
                                onCheckedChange = { onToggleAnnouncement(ann.id, it) }
                            )
                        }
                    }
                }
            }

            item {
                Text("Users (${adminData.users.size})", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search users") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { if (it.isFocused) keyboardController?.show() }
                )
            }

            val filteredUsers = adminData.users.filter {
                searchQuery.isBlank() ||
                    it.displayName.contains(searchQuery, ignoreCase = true) ||
                    (it.email?.contains(searchQuery, ignoreCase = true) == true)
            }

            items(filteredUsers, key = { it.userId }) { user ->
                val isSuspended = AccountStatus.fromRaw(user.accountStatus) == AccountStatus.SUSPENDED
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(user.displayName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "${user.email ?: user.userId} • ${user.accountStatus}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(
                            onClick = {
                                onSetUserStatus(
                                    user.userId,
                                    if (isSuspended) AccountStatus.ACTIVE else AccountStatus.SUSPENDED
                                )
                            }
                        ) {
                            Text(if (isSuspended) "Reactivate" else "Suspend")
                        }
                    }
                }
            }

            if (adminData.auditLogs.isNotEmpty()) {
                item { Text("Recent Audit Logs", style = MaterialTheme.typography.titleMedium) }
                items(adminData.auditLogs.take(20), key = { it.id }) { log ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(log.action, style = MaterialTheme.typography.labelMedium)
                            Text(log.details, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
