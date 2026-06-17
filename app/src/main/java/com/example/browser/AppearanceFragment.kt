package com.example.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
fun AppearanceFragment(
    prefs: PreferenceManager,
    onBack: () -> Unit
) {
    var themeOption by remember {
        mutableStateOf(prefs.getString("app_theme", "System default"))
    }
    var fontSizePercent by remember {
        mutableStateOf(prefs.getInt("font_size_percent", 100))
    }
    var showHomeButton by remember {
        mutableStateOf(prefs.getBoolean("show_home_button", true))
    }
    var showImages by remember {
        mutableStateOf(prefs.getBoolean("show_images", true))
    }
    var desktopDefault by remember {
        mutableStateOf(prefs.getBoolean("desktop_mode_default", false))
    }
    var toolbarPosition by remember {
        mutableStateOf(prefs.getString("address_bar_position", "top"))
    }

    var showThemeDialog by remember { mutableStateOf(false) }

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
                text = "Appearance",
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
            // 1. Theme Selection
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().clickable { showThemeDialog = true }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Theme", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Color palette: $themeOption", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // 2. Font Size SeekBar representation
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Font size / Text scale", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Scale size of page text: $fontSizePercent%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val sliderValues = listOf(75, 100, 125, 150)
                    var sliderPosition by remember {
                        mutableStateOf(sliderValues.indexOf(fontSizePercent).coerceAtLeast(0).toFloat())
                    }
                    Slider(
                        value = sliderPosition,
                        onValueChange = { pos ->
                            sliderPosition = pos
                            val valSelected = sliderValues[pos.toInt().coerceIn(0, 3)]
                            fontSizePercent = valSelected
                            prefs.setInt("font_size_percent", valSelected)
                        },
                        valueRange = 0f..3f,
                        steps = 2
                    )
                }
            }

            // 3. Show Home Button Shortcut
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Show home button", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Display 🏠 home accessibility icon in menu bar", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = showHomeButton,
                    onCheckedChange = {
                        showHomeButton = it
                        prefs.setBoolean("show_home_button", it)
                    }
                )
            }

            // 4. Toolbar position options (with UI previews as requested in setting "ADDRESS BAR POSITION SETTING")
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Address bar position", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Choose where the main address toolbar sits on screen", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Top Position option
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable {
                                    toolbarPosition = "top"
                                    prefs.setString("address_bar_position", "top")
                                }
                                .width(110.dp)
                                .border(
                                    width = 2.dp,
                                    color = if (toolbarPosition == "top") MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp)
                        ) {
                            // Top preview mock
                            Box(
                                modifier = Modifier
                                    .size(60.dp, 80.dp)
                                    .background(Color.White, RoundedCornerShape(4.dp))
                                    .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
                            ) {
                                // Address Bar at top
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(14.dp)
                                        .background(Color(0xFFEEEEEE))
                                        .border(1.dp, Color.LightGray)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Top (Default)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            RadioButton(
                                selected = (toolbarPosition == "top"),
                                onClick = {
                                    toolbarPosition = "top"
                                    prefs.setString("address_bar_position", "top")
                                }
                            )
                        }

                        // Bottom Position option
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable {
                                    toolbarPosition = "bottom"
                                    prefs.setString("address_bar_position", "bottom")
                                }
                                .width(110.dp)
                                .border(
                                    width = 2.dp,
                                    color = if (toolbarPosition == "bottom") MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp)
                        ) {
                            // Bottom preview mock
                            Box(
                                modifier = Modifier
                                    .size(60.dp, 80.dp)
                                    .background(Color.White, RoundedCornerShape(4.dp))
                                    .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
                            ) {
                                // Address Bar at bottom
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(14.dp)
                                        .align(Alignment.BottomCenter)
                                        .background(Color(0xFFEEEEEE))
                                        .border(1.dp, Color.LightGray)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Bottom", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            RadioButton(
                                selected = (toolbarPosition == "bottom"),
                                onClick = {
                                    toolbarPosition = "bottom"
                                    prefs.setString("address_bar_position", "bottom")
                                }
                            )
                        }
                    }
                }
            }

            // 5. Show images toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Show images", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Loads graphic elements automatically", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = showImages,
                    onCheckedChange = {
                        showImages = it
                        prefs.setBoolean("show_images", it)
                    }
                )
            }

            // 6. Desktop Mode Default toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Desktop mode by default", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Always request desktop layouts from servers", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = desktopDefault,
                    onCheckedChange = {
                        desktopDefault = it
                        prefs.setBoolean("desktop_mode_default", it)
                    }
                )
            }
        }
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Choose theme") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val themes = listOf("System default", "Light", "Dark")
                    themes.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    themeOption = option
                                    prefs.setString("app_theme", option)
                                    applyDarkThemeMode(option)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (themeOption == option),
                                onClick = {
                                    themeOption = option
                                    prefs.setString("app_theme", option)
                                    applyDarkThemeMode(option)
                                    showThemeDialog = false
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
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun applyDarkThemeMode(theme: String) {
    // The main Jetpack Compose theme reads from SharedPreferences and renders light/dark accordingly.
}
