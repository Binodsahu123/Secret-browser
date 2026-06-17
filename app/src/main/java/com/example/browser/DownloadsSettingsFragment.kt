package com.example.browser

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PreferenceManager

@Composable
fun DownloadsSettingsFragment(
    prefs: PreferenceManager,
    onBack: () -> Unit
) {
    var downloadLocation by remember {
        mutableStateOf(prefs.getString("download_directory_path", Environment.DIRECTORY_DOWNLOADS))
    }
    var askWhereToSave by remember {
        mutableStateOf(prefs.getBoolean("ask_before_download", false))
    }
    var onlyOnWifi by remember {
        mutableStateOf(prefs.getBoolean("download_only_wifi", false))
    }
    var showNotif by remember {
        mutableStateOf(prefs.getBoolean("show_download_notifications", true))
    }
    var autoOpen by remember {
        mutableStateOf(prefs.getBoolean("auto_open_downloaded_files", false))
    }

    var showEditLocationDialog by remember { mutableStateOf(false) }
    var locationInputText by remember { mutableStateOf(downloadLocation) }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
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
                text = "Downloads Settings",
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
            // 1. Download Location Picker
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().clickable { showEditLocationDialog = true }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Download location", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Saved to: $downloadLocation", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // 2. Ask where to save
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Ask where to save", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Prompt for destination folder before downloading each file", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = askWhereToSave,
                    onCheckedChange = {
                        askWhereToSave = it
                        prefs.setBoolean("ask_before_download", it)
                    }
                )
            }

            // 3. Download only on WiFi
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Download only on WiFi", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Avoids cellular network data usage counts", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = onlyOnWifi,
                    onCheckedChange = {
                        onlyOnWifi = it
                        prefs.setBoolean("download_only_wifi", it)
                    }
                )
            }

            // 4. Show download notifications
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Show download notifications", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Display status in external system drawer during downloads", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = showNotif,
                    onCheckedChange = {
                        showNotif = it
                        prefs.setBoolean("show_download_notifications", it)
                    }
                )
            }

            // 5. Auto-open downloaded files
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-open downloaded files", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Deploy and launch file immediately after finish loading", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = autoOpen,
                    onCheckedChange = {
                        autoOpen = it
                        prefs.setBoolean("auto_open_downloaded_files", it)
                    }
                )
            }
        }
    }

    if (showEditLocationDialog) {
        AlertDialog(
            onDismissRequest = { showEditLocationDialog = false },
            title = { Text("Set folder directory") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = locationInputText,
                        onValueChange = { locationInputText = it },
                        label = { Text("Directory path") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val path = locationInputText.trim()
                        if (path.isNotEmpty()) {
                            downloadLocation = path
                            prefs.setString("download_directory_path", path)
                        }
                        showEditLocationDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditLocationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
