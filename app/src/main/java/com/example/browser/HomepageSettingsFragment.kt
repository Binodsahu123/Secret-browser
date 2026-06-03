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
fun HomepageSettingsFragment(
    prefs: PreferenceManager,
    onBack: () -> Unit
) {
    var showHomeButton by remember {
        mutableStateOf(prefs.getBoolean("show_home_button", true))
    }
    var homepageType by remember {
        mutableStateOf(prefs.getString("homepage_type", "ntp")) // "ntp" or "custom"
    }
    var customUrl by remember {
        mutableStateOf(prefs.getString("homepage_custom_url", "https://www.google.com"))
    }

    var editUrlError by remember { mutableStateOf<String?>(null) }

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
                text = "Homepage",
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
            // 1. Show home button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Show home button", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Allow easy navigation to homepage from toolbar", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = showHomeButton,
                    onCheckedChange = {
                        showHomeButton = it
                        prefs.setBoolean("show_home_button", it)
                    }
                )
            }

            if (showHomeButton) {
                HorizontalDivider()

                Text("On home button tap, load:", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                // Option A: New Tab Page
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            homepageType = "ntp"
                            prefs.setString("homepage_type", "ntp")
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (homepageType == "ntp"),
                        onClick = {
                            homepageType = "ntp"
                            prefs.setString("homepage_type", "ntp")
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("New Tab Page", fontSize = 14.sp)
                }

                // Option B: Custom URL
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            homepageType = "custom"
                            prefs.setString("homepage_type", "custom")
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (homepageType == "custom"),
                        onClick = {
                            homepageType = "custom"
                            prefs.setString("homepage_type", "custom")
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Custom URL address", fontSize = 14.sp)
                }

                if (homepageType == "custom") {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = customUrl,
                        onValueChange = {
                            customUrl = it
                            // Simple validation
                            if (it.isBlank()) {
                                editUrlError = "URL cannot be blank"
                            } else if (!it.contains(".") || it.contains(" ")) {
                                editUrlError = "Please enter a valid website address"
                            } else {
                                editUrlError = null
                                // Auto convert helper
                                val formatted = if (!it.startsWith("http://") && !it.startsWith("https://")) {
                                    "https://$it"
                                } else {
                                    it
                                }
                                prefs.setString("homepage_custom_url", formatted)
                            }
                        },
                        label = { Text("Webpage address") },
                        isError = editUrlError != null,
                        supportingText = editUrlError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }
}
