package com.example.browser

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
fun PrivacySecurityFragment(
    prefs: PreferenceManager,
    onClearBrowsingData: () -> Unit,
    onAdBlockingSettings: () -> Unit,
    onBack: () -> Unit
) {
    var alwaysHttps by remember {
        mutableStateOf(prefs.getBoolean("always_use_secure_connections", true))
    }
    var doNotTrack by remember {
        mutableStateOf(prefs.getBoolean("do_not_track", false))
    }
    var blockThirdPartyCookies by remember {
        mutableStateOf(prefs.getBoolean("block_third_party_cookies", false))
    }
    var incognitoScreenshots by remember {
        mutableStateOf(prefs.getBoolean("incognito_screenshots", false))
    }
    var safeBrowsingMode by remember {
        mutableStateOf(prefs.getString("safe_browsing_mode", "Enhanced"))
    }

    var showSafeBrowsingDialog by remember { mutableStateOf(false) }

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
                text = "Privacy and security",
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
            // 1. Clear browsing data
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().clickable { onClearBrowsingData() }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Clear browsing data", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Clear history, cookies, cache, and more", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // 2. Safe Browsing mode
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().clickable { showSafeBrowsingDialog = true }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Safe Browsing", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Current level: $safeBrowsingMode protection", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("Edit", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

            // 3. Always use secure connections
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Always use secure connections", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Upgrade http:// loads to https:// automatically", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = alwaysHttps,
                    onCheckedChange = {
                        alwaysHttps = it
                        prefs.setBoolean("always_use_secure_connections", it)
                    }
                )
            }

            // 4. Do Not Track
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Do Not Track", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Sends a DNT request header to web sites", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = doNotTrack,
                    onCheckedChange = {
                        doNotTrack = it
                        prefs.setBoolean("do_not_track", it)
                    }
                )
            }

            // 5. Block third-party cookies
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Block third-party cookies", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Prevent websites from tracking you across sites", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = blockThirdPartyCookies,
                    onCheckedChange = {
                        blockThirdPartyCookies = it
                        prefs.setBoolean("block_third_party_cookies", it)
                    }
                )
            }

            // 6. Ad blocking
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().clickable { onAdBlockingSettings() }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Ad blocking shield", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Configure blocker checklists and white/blacklists", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // 7. Incognito screenshots
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Incognito screenshots", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Allow taking screenshots in incognito browsing tabs", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = incognitoScreenshots,
                    onCheckedChange = {
                        incognitoScreenshots = it
                        prefs.setBoolean("incognito_screenshots", it)
                    }
                )
            }
        }
    }

    if (showSafeBrowsingDialog) {
        AlertDialog(
            onDismissRequest = { showSafeBrowsingDialog = false },
            title = { Text("Safe Browsing Level") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            safeBrowsingMode = "Enhanced"
                            prefs.setString("safe_browsing_mode", "Enhanced")
                            showSafeBrowsingDialog = false
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = (safeBrowsingMode == "Enhanced"), onClick = {
                            safeBrowsingMode = "Enhanced"
                            prefs.setString("safe_browsing_mode", "Enhanced")
                            showSafeBrowsingDialog = false
                        })
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Enhanced protection", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Faster, proactive protection against websites, malware", fontSize = 11.sp, color = Color.Gray)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            safeBrowsingMode = "Standard"
                            prefs.setString("safe_browsing_mode", "Standard")
                            showSafeBrowsingDialog = false
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = (safeBrowsingMode == "Standard"), onClick = {
                            safeBrowsingMode = "Standard"
                            prefs.setString("safe_browsing_mode", "Standard")
                            showSafeBrowsingDialog = false
                        })
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Standard protection", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Default protection against downloaded threats", fontSize = 11.sp, color = Color.Gray)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            safeBrowsingMode = "No"
                            prefs.setString("safe_browsing_mode", "No")
                            showSafeBrowsingDialog = false
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = (safeBrowsingMode == "No"), onClick = {
                            safeBrowsingMode = "No"
                            prefs.setString("safe_browsing_mode", "No")
                            showSafeBrowsingDialog = false
                        })
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("No protection", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Turn off safe checking. (Not recommended)", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSafeBrowsingDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
