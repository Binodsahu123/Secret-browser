package com.example.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PreferenceManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchEngineFragment(prefs: PreferenceManager, onBack: () -> Unit) {
    var selectedEngine by remember { mutableStateOf(prefs.getString("default_search_engine", "Google")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Default Search Engine") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
            val engines = listOf("Google", "Bing", "DuckDuckGo", "Yahoo")
            engines.forEach { engine ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedEngine = engine
                            prefs.setString("default_search_engine", engine)
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (selectedEngine == engine),
                        onClick = {
                            selectedEngine = engine
                            prefs.setString("default_search_engine", engine)
                        }
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(engine, fontSize = 16.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySecurityFragment(
    prefs: PreferenceManager,
    onClearBrowsingData: () -> Unit,
    onAdBlockingSettings: () -> Unit,
    onBack: () -> Unit
) {
    var safeBrowsing by remember { mutableStateOf(prefs.getBoolean("safe_browsing_enabled", true)) }
    var sendDnt by remember { mutableStateOf(prefs.getBoolean("send_dnt_headers", true)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy and Security") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            item {
                ListItem(
                    headlineContent = { Text("Clear browsing data") },
                    supportingContent = { Text("History, cache, cookies, passwords") },
                    leadingContent = { Icon(Icons.Default.Delete, contentDescription = null) },
                    modifier = Modifier.clickable { onClearBrowsingData() }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Ad Blocking Engine") },
                    supportingContent = { Text("Configure ad filters, script blockers, cosmetic layout cleanups") },
                    leadingContent = { Icon(Icons.Default.Shield, contentDescription = null) },
                    modifier = Modifier.clickable { onAdBlockingSettings() }
                )
            }
            item {
                HorizontalDivider()
            }
            item {
                ListItem(
                    headlineContent = { Text("Shield Safe Browsing") },
                    supportingContent = { Text("Protects you and your device from dangerous and malicious sites") },
                    leadingContent = { Icon(Icons.Default.Security, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = safeBrowsing,
                            onCheckedChange = {
                                safeBrowsing = it
                                prefs.setBoolean("safe_browsing_enabled", it)
                            }
                        )
                    }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Send 'Do Not Track'") },
                    supportingContent = { Text("Request websites not to profile or track your web activity") },
                    leadingContent = { Icon(Icons.Default.Block, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = sendDnt,
                            onCheckedChange = {
                                sendDnt = it
                                prefs.setBoolean("send_dnt_headers", it)
                            }
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyCheckFragment(
    prefs: PreferenceManager,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToAdBlock: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Safety Check") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Your Privacy Shield is Active", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("SwiftBrowser regularly monitors and protects your browsing from tracking scripts and telemetry leaks.", fontSize = 14.sp)
                }
            }

            Button(
                onClick = onNavigateToPrivacy,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text("Manage Privacy Settings")
            }

            OutlinedButton(
                onClick = onNavigateToAdBlock,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text("Configure AdBlock Filters")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsSettingsFragment(prefs: PreferenceManager, onBack: () -> Unit) {
    var closeTabsOnExit by remember { mutableStateOf(prefs.getBoolean("close_tabs_on_exit", false)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tabs Layout") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            ListItem(
                headlineContent = { Text("Close tabs on exit") },
                supportingContent = { Text("Automatically close all tabs when exiting the browser") },
                leadingContent = { Icon(Icons.Default.LayersClear, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = closeTabsOnExit,
                        onCheckedChange = {
                            closeTabsOnExit = it
                            prefs.setBoolean("close_tabs_on_exit", it)
                        }
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomepageSettingsFragment(prefs: PreferenceManager, onBack: () -> Unit) {
    var hpType by remember { mutableStateOf(prefs.getString("homepage_type", "ntp")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Homepage Engine") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
            val types = listOf("ntp" to "New Tab Page", "custom" to "Custom URL")
            types.forEach { (type, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            hpType = type
                            prefs.setString("homepage_type", type)
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (hpType == type),
                        onClick = {
                            hpType = type
                            prefs.setString("homepage_type", type)
                        }
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(label, fontSize = 16.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceFragment(prefs: PreferenceManager, onBack: () -> Unit) {
    var themeIndex by remember { mutableIntStateOf(prefs.getInt("app_theme_index", 0)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
            Text("App Theme Mode", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            val themes = listOf("System Adaptive", "Pure Light", "Classic Dark")
            themes.forEachIndexed { idx, theme ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            themeIndex = idx
                            prefs.setInt("app_theme_index", idx)
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (themeIndex == idx),
                        onClick = {
                            themeIndex = idx
                            prefs.setInt("app_theme_index", idx)
                        }
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(theme, fontSize = 16.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibilityFragment(prefs: PreferenceManager, onBack: () -> Unit) {
    var scalePercent by remember { mutableIntStateOf(prefs.getInt("text_scaling", 100)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accessibility") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
            Text("Text scaling: $scalePercent%", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 12.dp))
            Slider(
                value = scalePercent.toFloat(),
                onValueChange = { newVal ->
                    val rounded = newVal.toInt()
                    scalePercent = rounded
                    prefs.setInt("text_scaling", rounded)
                },
                valueRange = 50f..200f,
                steps = 15
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteSettingsFragment(prefs: PreferenceManager, onBack: () -> Unit) {
    var locationState by remember { mutableStateOf(prefs.getString("site_perm_default/location", "Ask")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Site Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            ListItem(
                headlineContent = { Text("Location permission") },
                supportingContent = { Text("Current default policy: $locationState") },
                leadingContent = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                modifier = Modifier.clickable {
                    val next = when (locationState) {
                        "Ask" -> "Allow"
                        "Allow" -> "Block"
                        else -> "Ask"
                    }
                    locationState = next
                    prefs.setString("site_perm_default/location", next)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagesFragment(prefs: PreferenceManager, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Translation Languages") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
            Text("Preferred Lang: English & Hindi", fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Integrated translate engine will suggest Hindi / English automatic translation setups on foreign scripts.", fontSize = 14.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsSettingsFragment(prefs: PreferenceManager, onBack: () -> Unit) {
    var askDownloadDir by remember { mutableStateOf(prefs.getBoolean("ask_download_dir", true)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads Configuration") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            ListItem(
                headlineContent = { Text("Ask where to save files") },
                supportingContent = { Text("Prompt directory and name confirmation before initiating download downloads") },
                leadingContent = { Icon(Icons.Default.Download, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = askDownloadDir,
                        onCheckedChange = {
                            askDownloadDir = it
                            prefs.setBoolean("ask_download_dir", it)
                        }
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AISettingsFragment(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Assistant Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Server-Side Gemini AI Engine is Online", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Webpage highlights, article summaries, translation feeds, and chat prompts utilize cloud-hosted Gemini models seamlessly.", fontSize = 14.sp)
                }
            }
        }
    }
}
