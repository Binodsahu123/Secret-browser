package com.example.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PreferenceManager

@Composable
fun TabsSettingsFragment(
    prefs: PreferenceManager,
    onBack: () -> Unit
) {
    var closeTabsAuto by remember {
        mutableStateOf(prefs.getString("close_tabs_automatically", "Never"))
    }
    var groupTabsAuto by remember {
        mutableStateOf(prefs.getBoolean("group_tabs_automatically", true))
    }
    var showTabCount by remember {
        mutableStateOf(prefs.getBoolean("show_tab_count", true))
    }
    var openLinksNewTab by remember {
        mutableStateOf(prefs.getBoolean("open_links_in_new_tab", false))
    }
    var swipeToClose by remember {
        mutableStateOf(prefs.getBoolean("swipe_to_close_tab", true))
    }

    var showCloseTabsDialog by remember { mutableStateOf(false) }

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
                text = "Tabs and tab groups",
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
            // 1. Close tabs automatically
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().clickable { showCloseTabsDialog = true }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Close tabs automatically", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Automatically close inactive tabs: $closeTabsAuto", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // 2. Tab groups
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Group tabs automatically", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Organize open tabs into groups by domain", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = groupTabsAuto,
                    onCheckedChange = {
                        groupTabsAuto = it
                        prefs.setBoolean("group_tabs_automatically", it)
                    }
                )
            }

            // 3. Show tab count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Show tab count in toolbar", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Display indicator badge for active tabs", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = showTabCount,
                    onCheckedChange = {
                        showTabCount = it
                        prefs.setBoolean("show_tab_count", it)
                    }
                )
            }

            // 4. Open links in new tab
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Open links in new tab", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Forces links to always launch in background tab", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = openLinksNewTab,
                    onCheckedChange = {
                        openLinksNewTab = it
                        prefs.setBoolean("open_links_in_new_tab", it)
                    }
                )
            }

            // 5. Swipe to close tab
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Swipe to close tab", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Dismiss tabs via swipe gesture in switcher", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = swipeToClose,
                    onCheckedChange = {
                        swipeToClose = it
                        prefs.setBoolean("swipe_to_close_tab", it)
                    }
                )
            }
        }
    }

    if (showCloseTabsDialog) {
        AlertDialog(
            onDismissRequest = { showCloseTabsDialog = false },
            title = { Text("Auto-close duration") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val choices = listOf("Never", "After 1 day", "After 7 days", "After 30 days")
                    choices.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    closeTabsAuto = option
                                    prefs.setString("close_tabs_automatically", option)
                                    showCloseTabsDialog = false
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (closeTabsAuto == option),
                                onClick = {
                                    closeTabsAuto = option
                                    prefs.setString("close_tabs_automatically", option)
                                    showCloseTabsDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(option, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCloseTabsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
