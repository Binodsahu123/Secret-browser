package com.example.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PreferenceManager

data class SitePermissionType(
    val id: String,
    val name: String,
    val icon: String,
    val defaultStatus: String // "Allow", "Block", "Ask"
)

val permissionTypes = listOf(
    SitePermissionType("microphone", "🎤 Microphone", "🎤", "Ask"),
    SitePermissionType("camera", "📷 Camera", "📷", "Ask"),
    SitePermissionType("location", "📍 Location", "📍", "Ask"),
    SitePermissionType("notifications", "🔔 Notifications", "🔔", "Ask"),
    SitePermissionType("storage", "📁 Storage (File Access)", "📁", "Ask"),
    SitePermissionType("clipboard", "🖥️ Clipboard", "🖥️", "Ask"),
    SitePermissionType("midi", "🎹 MIDI Sysex", "🎹", "Ask"),
    SitePermissionType("protected_media", "🛡️ Protected Media", "🛡️", "Ask"),
    SitePermissionType("fullscreen", "📺 Fullscreen Mode", "📺", "Ask"),
    SitePermissionType("popups", "🔒 Pop-ups & Redirects", "🔒", "Block")
)

@Composable
fun SiteSettingsFragment(
    prefs: PreferenceManager,
    onBack: () -> Unit
) {
    var activePermissionId by remember { mutableStateOf<String?>(null) }

    if (activePermissionId == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) {
                    Text("← Back", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Site settings",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(permissionTypes) { type ->
                    val defaultVal = prefs.getString("site_perm_default/${type.id}", type.defaultStatus)
                    ListItem(
                        headlineContent = { Text(type.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) },
                        supportingContent = { Text("Default status: $defaultVal", fontSize = 12.sp, color = Color.Gray) },
                        modifier = Modifier.clickable { activePermissionId = type.id }
                    )
                }
            }
        }
    } else {
        val permId = activePermissionId!!
        val typeObj = permissionTypes.first { it.id == permId }
        PermissionDetailView(
            type = typeObj,
            prefs = prefs,
            onBack = { activePermissionId = null }
        )
    }
}

@Composable
fun PermissionDetailView(
    type: SitePermissionType,
    prefs: PreferenceManager,
    onBack: () -> Unit
) {
    var defaultStatus by remember {
        mutableStateOf(prefs.getString("site_perm_default/${type.id}", type.defaultStatus))
    }

    // Load active exception domains stored as a CSV lists or direct lookup list
    var exceptionInputHost by remember { mutableStateOf("") }
    var exceptionInputStatus by remember { mutableStateOf("Allow") }
    var showAddDialog by remember { mutableStateOf(false) }

    // Let's model a mock persistence list matching: "site_perm_exception/{permission}/{domain}"
    var exceptionsList by remember {
        mutableStateOf(
            // Return some default mock or look up inside local storage cache
            listOf(
                "example.com" to "Block",
                "testsite.org" to "Allow"
            ).filter {
                prefs.getString("site_perm_exception/${type.id}/$it", "") != ""
            }
        )
    }

    // Fetch actual matching saved preferences dynamically from SharedPreferences
    val savedExceptions = remember {
        val list = prefs.getAllKeysWithPrefix("site_perm_exception/${type.id}/").toMutableList()
        if (list.isEmpty()) {
            list.add("aistudio.google.com" to "Allow")
            list.add("example.com" to "Block")
        }
        list
    }

    var liveExceptions by remember { mutableStateOf(savedExceptions) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("← Back", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${type.name.substring(2)} Settings",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Default behavior:", fontWeight = FontWeight.Bold, fontSize = 14.sp)

            Column {
                val statuses = listOf("Allow", "Block", "Ask")
                statuses.forEach { statusOption ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                defaultStatus = statusOption
                                prefs.setString("site_perm_default/${type.id}", statusOption)
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (defaultStatus == statusOption),
                            onClick = {
                                defaultStatus = statusOption
                                prefs.setString("site_perm_default/${type.id}", statusOption)
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(statusOption, fontSize = 14.sp)
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Exceptions", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Button(onClick = { showAddDialog = true }, modifier = Modifier.height(34.dp)) {
                    Text("+ Add site", fontSize = 11.sp)
                }
            }

            if (liveExceptions.isEmpty()) {
                Text("No domain exceptions added.", fontSize = 11.sp, color = Color.Gray)
            } else {
                liveExceptions.forEach { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(item.first, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Setting: ${item.second}", fontSize = 12.sp, color = Color.Gray)
                            }
                            IconButton(onClick = {
                                prefs.setString("site_perm_exception/${type.id}/${item.first}", "")
                                liveExceptions = liveExceptions.filter { it.first != item.first }.toMutableList()
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add site exception") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = exceptionInputHost,
                        onValueChange = { exceptionInputHost = it },
                        label = { Text("Site URL domain") },
                        placeholder = { Text("example.com") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Exception action:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)

                    listOf("Allow", "Block").forEach { action ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { exceptionInputStatus = action }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (exceptionInputStatus == action),
                                onClick = { exceptionInputStatus = action }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(action, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val host = exceptionInputHost.trim()
                        if (host.isNotEmpty()) {
                            prefs.setString("site_perm_exception/${type.id}/$host", exceptionInputStatus)
                            liveExceptions = (liveExceptions + (host to exceptionInputStatus)).toMutableList()
                            showAddDialog = false
                            exceptionInputHost = ""
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
