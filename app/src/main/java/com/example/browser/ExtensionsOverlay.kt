package com.example.browser

import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.PreferenceManager

data class ExtensionMeta(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val size: String,
    val provider: String,
    val lastUpdated: String,
    val permissionDescription: String,
    val defaultInstalled: Boolean = false
)

val EXTENSIONS_CATALOG = listOf(
    ExtensionMeta(
        id = "ext_grok_automation",
        name = "Grok Automation",
        description = "Intelligently auto-fill forms, bypass recaptchas and automate visual flows in website contexts.",
        version = "v1.2.0",
        size = "210 KB",
        provider = "GrokAI Labs",
        lastUpdated = "2026-05-12",
        permissionDescription = "Read elements, auto-submit fields, simulate clicking sequences.",
        defaultInstalled = true
    ),
    ExtensionMeta(
        id = "ext_dark_reader",
        name = "Dark Reader",
        description = "Injects readable dark themes on websites, utilizing custom filters for comfortable viewing.",
        version = "v4.9.58",
        size = "450 KB",
        provider = "DarkReader Corp",
        lastUpdated = "2026-06-01",
        permissionDescription = "Modify visual stylesheets, inject CSS styles, invert element colors.",
        defaultInstalled = true
    ),
    ExtensionMeta(
        id = "ext_adblock",
        name = "AdBlock Plus",
        description = "Filter distracting elements, popups, tracking scripts, and display banners.",
        version = "v3.16.2",
        size = "310 KB",
        provider = "Eyeo GmbH",
        lastUpdated = "2026-04-20",
        permissionDescription = "Block network connections, parse script bundles, clear advertisements.",
        defaultInstalled = true
    ),
    ExtensionMeta(
        id = "ext_metamask",
        name = "MetaMask Wallet",
        description = "An official cryptocurrency wallet adapter to connect with Web3 decentralized dApps.",
        version = "v10.35.1",
        size = "1.2 MB",
        provider = "ConsenSys Inc.",
        lastUpdated = "2026-05-15",
        permissionDescription = "Inject contract listeners, sign transactions, view balance.",
        defaultInstalled = false
    ),
    ExtensionMeta(
        id = "ext_grok_4",
        name = "Grok 4.0 AI",
        description = "An interactive AI browsing companion to explain pages and summarize articles.",
        version = "v3.1.0",
        size = "820 KB",
        provider = "GrokAI Labs",
        lastUpdated = "2026-06-03",
        permissionDescription = "Read visible page texts, extract article contexts, offer chatbot shortcuts.",
        defaultInstalled = false
    ),
    ExtensionMeta(
        id = "ext_cookies",
        name = "I don't care about cookies",
        description = "Say goodbye to cookie consent notices. Accept or clear cookies instantly.",
        version = "v3.4.7",
        size = "140 KB",
        provider = "Kiko.io",
        lastUpdated = "2026-03-10",
        permissionDescription = "Intercept cookie consent boxes, hide overlay cookie alerts.",
        defaultInstalled = false
    ),
    ExtensionMeta(
        id = "ext_auto_translate",
        name = "Auto-Translate Extension",
        description = "Automatically translates foreign-language pages to dynamic Hindi content.",
        version = "v2.0.1",
        size = "180 KB",
        provider = "Translate Labs",
        lastUpdated = "2026-05-28",
        permissionDescription = "Access document body texts, trigger Google translator frameworks.",
        defaultInstalled = true
    )
)

@Composable
fun ExtensionsOverlay(
    viewModel: BrowserViewModel,
    onDismiss: () -> Unit,
    isGlass: Boolean = false
) {
    var activeTabIdx by remember { mutableIntStateOf(0) }
    val tabs = remember { listOf("Extensions Hub", "ZIP Extension Installer", "Developer Console") }

    // Active Dialog states
    var selectedExtDetails by remember { mutableStateOf<ExtensionMeta?>(null) }
    var activePopupExtCode by remember { mutableStateOf<String?>(null) }

    Surface(
        color = if (isGlass) Color(0xFF0F172A) else MaterialTheme.colorScheme.background,
        contentColor = if (isGlass) Color.White else MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (isGlass) Color.White else LocalContentColor.current
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = null,
                        tint = Color(0xFF6366F1),
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Chrome Extensions",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tab navigation selector
            TabRow(
                selectedTabIndex = activeTabIdx,
                containerColor = Color.Transparent,
                contentColor = Color(0xFF6366F1),
                divider = {}
            ) {
                tabs.forEachIndexed { idx, label ->
                    Tab(
                        selected = activeTabIdx == idx,
                        onClick = { activeTabIdx = idx },
                        text = {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (activeTabIdx) {
                    0 -> ExtensionsHubList(
                        viewModel = viewModel,
                        onDismiss = onDismiss,
                        onSelectDetail = { selectedExtDetails = it },
                        onOpenPopup = { activePopupExtCode = it }
                    )
                    1 -> ZipExtensionInstaller(viewModel = viewModel)
                    else -> DeveloperConsolePanel(viewModel = viewModel)
                }
            }
        }
    }

    // Modal dialogue panels
    selectedExtDetails?.let { metadata ->
        ExtensionDetailDialog(
            viewModel = viewModel,
            meta = metadata,
            onDismiss = { selectedExtDetails = null }
        )
    }

    activePopupExtCode?.let { extId ->
        ExtensionPopupBottomSheet(
            viewModel = viewModel,
            extensionId = extId,
            onDismiss = { activePopupExtCode = null }
        )
    }
}

@Composable
fun ExtensionsHubList(
    viewModel: BrowserViewModel,
    onDismiss: () -> Unit,
    onSelectDetail: (ExtensionMeta) -> Unit,
    onOpenPopup: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showPermissionsAlert by remember { mutableStateOf<ExtensionMeta?>(null) }
    var showPropertiesAlert by remember { mutableStateOf<ExtensionMeta?>(null) }
    
    // Trigger recomposition on extension install/toggle changes
    var refreshTicker by remember { mutableStateOf(0) }

    val context = LocalContext.current
    val parsedCatalog = remember(searchQuery, refreshTicker) {
        val fullList = getFullExtensionsList(viewModel)
        if (searchQuery.isBlank()) {
            fullList
        } else {
            fullList.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.description.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // Partition lists
    val installedList = parsedCatalog.filter { meta ->
        isExtensionInstalled(viewModel, meta.id)
    }
    
    val popularList = parsedCatalog.filter { meta ->
        !isExtensionInstalled(viewModel, meta.id)
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Search Box input
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search extensions...", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF6366F1),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Quicklink trigger to web store
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF6366F1).copy(alpha = 0.12f)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        viewModel.addNewTab("https://chromewebstore.google.com")
                    }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF6366F1).copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storefront,
                            contentDescription = null,
                            tint = Color(0xFF6366F1),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Chrome Web Store",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF6366F1)
                        )
                        Text(
                            text = "Orion fully supports chromium-based extensions dynamically.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color(0xFF6366F1),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Local ZIP/CRX Import Button Card
        item {
            val fileLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri ->
                if (uri != null) {
                    viewModel.loadExtensionFromZip(context, uri) { success, message ->
                        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                        refreshTicker++
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.12f)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        fileLauncher.launch("*/*")
                    }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF10B981).copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderZip,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Import Local Extension (.zip/.crx)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF10B981)
                        )
                        Text(
                            text = "Import and unpack extensions manually from your file storage.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Section: Installed
        if (installedList.isNotEmpty()) {
            item {
                Text(
                    text = "INSTALLED EXTENSIONS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            items(installedList, key = { it.id }) { meta ->
                var isEnabled by remember { mutableStateOf(viewModel.isExtensionEnabled(meta.id)) }
                
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (isEnabled) {
                            onOpenPopup(meta.id)
                        } else {
                            android.widget.Toast.makeText(context, "Please enable the extension to view its popup.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color(0xFF6366F1).copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (meta.id) {
                                    "ext_metamask" -> Icons.Default.Wallet
                                    "ext_grok_4" -> Icons.Default.SmartToy
                                    "ext_grok_automation" -> Icons.Default.ElectricBolt
                                    "ext_dark_reader" -> Icons.Default.Brightness4
                                    "ext_adblock" -> Icons.Default.Shield
                                    else -> Icons.Default.Extension
                                },
                                contentDescription = null,
                                tint = Color(0xFF6366F1),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = meta.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = meta.version,
                                    fontSize = 10.sp,
                                    color = Color(0xFF6366F1),
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .background(Color(0xFF6366F1).copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = meta.description,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(horizontalAlignment = Alignment.End) {
                            Switch(
                                checked = isEnabled,
                                onCheckedChange = { newState ->
                                    isEnabled = newState
                                    viewModel.setExtensionEnabled(meta.id, newState)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF6366F1)
                                )
                            )
                            
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                var menuExpanded by remember { mutableStateOf(false) }
                                
                                IconButton(
                                    onClick = { menuExpanded = true },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = "Options",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Open Component", fontSize = 12.sp) },
                                        leadingIcon = { Icon(Icons.Default.Launch, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        onClick = {
                                            menuExpanded = false
                                            if (isEnabled) {
                                                onOpenPopup(meta.id)
                                            } else {
                                                android.widget.Toast.makeText(context, "Please enable the extension to view its popup.", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                    
                                    DropdownMenuItem(
                                        text = { Text("Share Extension", fontSize = 12.sp) },
                                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        onClick = {
                                            menuExpanded = false
                                            viewModel.shareExtension(meta.id, context)
                                        }
                                    )
                                    
                                    DropdownMenuItem(
                                        text = { Text("Export ZIP", fontSize = 12.sp) },
                                        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        onClick = {
                                            menuExpanded = false
                                            viewModel.exportExtension(meta.id, context) { success, msg ->
                                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    )
                                    
                                    DropdownMenuItem(
                                        text = { Text("Permissions", fontSize = 12.sp) },
                                        leadingIcon = { Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        onClick = {
                                            menuExpanded = false
                                            showPermissionsAlert = meta
                                        }
                                    )
                                    
                                    DropdownMenuItem(
                                        text = { Text("Properties", fontSize = 12.sp) },
                                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        onClick = {
                                            menuExpanded = false
                                            showPropertiesAlert = meta
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text("Extension Details", fontSize = 12.sp) },
                                        leadingIcon = { Icon(Icons.Default.SettingsSuggest, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        onClick = {
                                            menuExpanded = false
                                            onSelectDetail(meta)
                                        }
                                    )
                                    
                                    Divider(modifier = Modifier.padding(vertical = 4.dp), color = Color.White.copy(alpha = 0.1f))
                                    
                                    DropdownMenuItem(
                                        text = { Text("Delete / Uninstall", color = Color.Red, fontSize = 12.sp) },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp)) },
                                        onClick = {
                                            menuExpanded = false
                                            val uploadedId = viewModel.prefs.getString("ext_uploaded_id", "")
                                            if (meta.id == "ext_uploaded_script_enabled" || meta.id == uploadedId) {
                                                viewModel.uninstallUploadedExtension()
                                            } else {
                                                viewModel.prefs.setBoolean("ext_installed_${meta.id}", false)
                                                viewModel.setExtensionEnabled(meta.id, false)
                                                if (meta.id != "ext_dark_reader" && meta.id != "ext_adblock" && meta.id != "ext_grok_automation") {
                                                    viewModel.uninstallDbExtension(meta.id)
                                                }
                                            }
                                            refreshTicker++
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section: Popular Catalog
        if (popularList.isNotEmpty()) {
            item {
                Text(
                    text = "POPULAR IN STORE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            items(popularList, key = { it.id }) { meta ->
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.06f)),
                    modifier = Modifier.fillMaxWidth().clickable { onSelectDetail(meta) }
                ) {
                    Row(
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.Gray.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (meta.id) {
                                    "ext_metamask" -> Icons.Default.Wallet
                                    "ext_grok_4" -> Icons.Default.SmartToy
                                    "ext_cookies" -> Icons.Default.Cookie
                                    else -> Icons.Default.Extension
                                },
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = meta.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = meta.description,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = meta.provider,
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "•  ${meta.size}",
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = { onSelectDetail(meta) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1).copy(alpha = 0.12f), contentColor = Color(0xFF6366F1)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Install", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
    }

    showPermissionsAlert?.let { meta ->
        AlertDialog(
            onDismissRequest = { showPermissionsAlert = null },
            title = { Text(text = "${meta.name} Permissions", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(text = "This extension requests the following access permissions:", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    meta.permissionDescription.split(";").forEach { perm ->
                        if (perm.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(perm.trim(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPermissionsAlert = null }) {
                    Text("OK")
                }
            }
        )
    }

    showPropertiesAlert?.let { meta ->
        AlertDialog(
            onDismissRequest = { showPropertiesAlert = null },
            title = { Text(text = "${meta.name} Properties", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row {
                        Text("Id: ", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                        Text(meta.id, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Row {
                        Text("Version: ", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                        Text(meta.version, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Row {
                        Text("Size: ", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                        Text(meta.size, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Row {
                        Text("Provider: ", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                        Text(meta.provider, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Row {
                        Text("Local Path: ", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                        Text(
                            "Android/data/${context.packageName}/extensions/${meta.name}", 
                            fontSize = 11.sp, 
                            color = Color(0xFF10B981)
                        )
                    }
                    Row {
                        Text("Install Date: ", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                        Text(meta.lastUpdated, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPropertiesAlert = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun ExtensionDetailDialog(
    meta: ExtensionMeta,
    viewModel: BrowserViewModel,
    onDismiss: () -> Unit
) {
    var stepState by remember { mutableIntStateOf(0) } // 0: Details, 1: Prompt permission, 2: Install loading
    var installProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(stepState) {
        if (stepState == 2) {
            installProgress = 0f
            while (installProgress < 1f) {
                kotlinx.coroutines.delay(120)
                installProgress += 0.08f
            }
            // Done installing! Set preferences
            viewModel.prefs.setBoolean("ext_installed_${meta.id}", true)
            viewModel.prefs.setBoolean(meta.id, true) // enable directly too
            stepState = 0
            onDismiss()
        }
    }

    if (stepState == 1) {
        // Authentic Chrome permission request popup
        Dialog(onDismissRequest = { stepState = 0 }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Extension,
                        contentDescription = null,
                        tint = Color(0xFF6366F1),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Add '${meta.name}'?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "It can read web templates, modify stylesheets, and communicate with underlying network frameworks.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = Color.LightGray.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { stepState = 0 },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Cancel", fontSize = 13.sp)
                        }

                        Button(
                            onClick = { stepState = 2 },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Add Extension", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    } else {
        Dialog(onDismissRequest = onDismiss) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Extension Details", fontSize = 14.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .background(Color(0xFF6366F1).copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Extension,
                                contentDescription = null,
                                tint = Color(0xFF6366F1),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(meta.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Published by ${meta.provider}", fontSize = 11.sp, color = Color.Gray)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(meta.description, fontSize = 13.sp, lineHeight = 18.sp)

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("FEATURES & PERMISSIONS:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
                    Text(meta.permissionDescription, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Version", fontSize = 11.sp, color = Color.Gray)
                            Text(meta.version, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Size", fontSize = 11.sp, color = Color.Gray)
                            Text(meta.size, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Last Updated", fontSize = 11.sp, color = Color.Gray)
                            Text(meta.lastUpdated, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    if (stepState == 2) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            LinearProgressIndicator(
                                progress = { installProgress },
                                color = Color(0xFF6366F1),
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Downloading and verifying dependencies... ${(installProgress * 100).toInt()}%",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    } else {
                        Button(
                            onClick = { stepState = 1 },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Download and Install", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ZipExtensionInstaller(viewModel: BrowserViewModel) {
    val context = LocalContext.current
    var isEnabled by remember { mutableStateOf(viewModel.isExtensionEnabled("ext_uploaded_script_enabled")) }
    var loadedExtName by remember { mutableStateOf(viewModel.getUploadedExtensionName()) }
    var isLoading by remember { mutableStateOf(false) }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            isLoading = true
            viewModel.loadExtensionFromZip(context, uri) { success, message ->
                isLoading = false
                loadedExtName = viewModel.getUploadedExtensionName()
                isEnabled = viewModel.isExtensionEnabled("ext_uploaded_script_enabled")
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Upload Custom ZIP Extension",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Upload a Chrome (.zip) extension containing a manifest.json and content_scripts. Our custom Javascript sandbox will parse and isolate scripts to execute on top of any webpage you enter.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.sp
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderZip,
                            contentDescription = null,
                            tint = Color(0xFF6366F1)
                        )
                        Column {
                            Text(
                                text = "Installed ZIP Extension",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = loadedExtName,
                                fontSize = 11.sp,
                                color = Color(0xFF6366F1)
                            )
                        }
                    }
                    if (loadedExtName != "No uploaded ZIP extension") {
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { newState ->
                                isEnabled = newState
                                viewModel.setExtensionEnabled("ext_uploaded_script_enabled", newState)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF6366F1))
                        )
                    }
                }

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF6366F1), modifier = Modifier.size(24.dp))
                    }
                } else {
                    Button(
                        onClick = { fileLauncher.launch("application/zip") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Upload Extension Zip", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun DeveloperConsolePanel(viewModel: BrowserViewModel) {
    var customScriptCode by remember { mutableStateOf(viewModel.getCustomExtensionScript()) }
    var isCustomScriptEnabled by remember { mutableStateOf(viewModel.isExtensionEnabled("ext_custom_script_enabled")) }
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Custom Developer Scripts",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Load custom Javascript on every page load finish.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isCustomScriptEnabled,
                onCheckedChange = { newState ->
                    isCustomScriptEnabled = newState
                    viewModel.setExtensionEnabled("ext_custom_script_enabled", newState)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF6366F1)
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = customScriptCode,
            onValueChange = { customScriptCode = it },
            placeholder = { Text("e.g. alert('Welcome Orion!'); document.body.style.backgroundColor = 'red';", fontSize = 12.sp) },
            label = { Text("User Script / Console Injection") },
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            ),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            maxLines = 15,
            singleLine = false
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                viewModel.setCustomExtensionScript(customScriptCode)
                android.widget.Toast.makeText(context, "Developer extension saved successfully", android.widget.Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save developer extension", fontWeight = FontWeight.Bold)
        }
    }
}

// Interactive Extension Popup layout dialog container
@Composable
fun ExtensionPopupBottomSheet(
    viewModel: BrowserViewModel,
    extensionId: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)), // ambient layout styling
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Title Area
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when (extensionId) {
                                "ext_metamask" -> Icons.Default.Wallet
                                "ext_grok_4" -> Icons.Default.SmartToy
                                "ext_grok_automation" -> Icons.Default.ElectricBolt
                                "ext_dark_reader" -> Icons.Default.Brightness4
                                "ext_adblock" -> Icons.Default.Shield
                                else -> Icons.Default.Extension
                            },
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (extensionId) {
                                "ext_metamask" -> "MetaMask Portal"
                                "ext_grok_4" -> "Grok 4.0 AI Assistant"
                                "ext_grok_automation" -> "Grok Automation"
                                "ext_dark_reader" -> "Dark Reader Settings"
                                "ext_adblock" -> "AdBlock Settings"
                                "ext_uploaded_script_enabled" -> viewModel.getUploadedExtensionName()
                                else -> "Extension Popup"
                            },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Custom embedded layout based on extensionId choice
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    when (extensionId) {
                        "ext_metamask" -> MetaMaskPopupPortal(viewModel)
                        "ext_grok_automation" -> GrokAutomationPopupPortal(viewModel)
                        "ext_dark_reader" -> DarkReaderPopupPortal(viewModel)
                        "ext_grok_4" -> Grok4AiPopupPortal(viewModel)
                        "ext_adblock" -> AdBlockPopupPortal(viewModel)
                        "ext_uploaded_script_enabled" -> CustomUploadedExtensionPopupPortal(viewModel)
                        else -> GenericPopupView(extensionId)
                    }
                }
            }
        }
    }
}

@Composable
fun MetaMaskPopupPortal(viewModel: BrowserViewModel) {
    val coroutineScope = rememberCoroutineScope()
    var ethBalanceStr by remember { mutableStateOf(viewModel.prefs.getString("ext_metamask_balance", "1.248")) }
    var activeTab by remember { mutableIntStateOf(0) } // 0: Wallet, 1: Send
    var currentNetwork by remember { mutableStateOf("Ethereum Mainnet") }
    var networksExpanded by remember { mutableStateOf(false) }

    // Transaction Fields
    var sendTargetAddress by remember { mutableStateOf("") }
    var sendAmount by remember { mutableStateOf("") }
    var transactionLogs by remember { mutableStateOf(listOf("Uniswap Swap - 0.2 ETH", "Bridge: Arbitrum -> Orbit - 0.05 ETH")) }
    var isSendingTx by remember { mutableStateOf(false) }

    // Convert safety
    val currentBalanceDouble = ethBalanceStr.toDoubleOrNull() ?: 1.248

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        // Network selector drop down
        Box(modifier = Modifier.fillMaxWidth().wrapContentSize(Alignment.TopEnd)) {
            Row(
                modifier = Modifier
                    .background(Color(0xFF1E293B), RoundedCornerShape(20.dp))
                    .clickable { networksExpanded = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(8.dp).background(Color.Green, CircleShape))
                Spacer(modifier = Modifier.width(6.dp))
                Text(currentNetwork, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
            DropdownMenu(
                expanded = networksExpanded,
                onDismissRequest = { networksExpanded = false }
            ) {
                listOf("Ethereum Mainnet", "Arbitrum Orbit", "Polygon zkEVM", "Orion Test Net").forEach { net ->
                    DropdownMenuItem(
                        text = { Text(net, fontSize = 12.sp) },
                        onClick = {
                            currentNetwork = net
                            networksExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (activeTab == 0) {
            // Balance Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(listOf(Color(0xFFEA580C), Color(0xFFD97706))),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(16.dp)
            ) {
                Column {
                    Text("ACTIVE WALLET ACCOUNT", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    Text("Account 1 (0x7F...d8C)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("${"%.4f".format(currentBalanceDouble)} ETH", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    Text("$${"%,.2f".format(currentBalanceDouble * 3615.15)} USD", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Quick Links
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { activeTab = 1 },
                        modifier = Modifier.background(Color(0xFF334155), CircleShape).size(40.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Text("Send", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = {
                            // Quick simulation reload
                            val added = currentBalanceDouble + 0.5
                            ethBalanceStr = added.toString()
                            viewModel.prefs.setString("ext_metamask_balance", ethBalanceStr)
                            transactionLogs = listOf("Received Faucet ETH +0.5 ETH") + transactionLogs
                        },
                        modifier = Modifier.background(Color(0xFF334155), CircleShape).size(40.dp)
                    ) {
                        Icon(Icons.Default.CallReceived, contentDescription = "Receive", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Text("Faucet", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Scroll logs activity
            Text("HISTORIC ACTIVITY logs", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                transactionLogs.take(3).forEach { log ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E293B), RoundedCornerShape(10.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(log, color = Color.White, fontSize = 11.sp)
                        Text("Confirmed", color = Color.Green, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            // Send Screen
            Text("SEND TRANSACTIONS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = sendTargetAddress,
                onValueChange = { sendTargetAddress = it },
                placeholder = { Text("0xRecipientAddress...", fontSize = 12.sp, color = Color.Gray) },
                label = { Text("To Recipient", fontSize = 11.sp, color = Color(0xFFF59E0B)) },
                textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = sendAmount,
                onValueChange = { sendAmount = it },
                placeholder = { Text("0.0 ETH", fontSize = 12.sp, color = Color.Gray) },
                label = { Text("ETH Amount", fontSize = 11.sp, color = Color(0xFFF59E0B)) },
                textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (isSendingTx) {
                Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFEA580C), modifier = Modifier.size(24.dp))
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { activeTab = 0 },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Gray.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Back", color = Color.White)
                    }

                    Button(
                        onClick = {
                            val doubleAmount = sendAmount.toDoubleOrNull()
                            if (doubleAmount == null || doubleAmount <= 0) return@Button
                            if (doubleAmount > currentBalanceDouble) return@Button

                            isSendingTx = true
                            coroutineScope.launch {
                                kotlinx.coroutines.delay(1200) // simulated mining delay
                                val updated = currentBalanceDouble - doubleAmount
                                ethBalanceStr = updated.toString()
                                viewModel.prefs.setString("ext_metamask_balance", ethBalanceStr)
                                transactionLogs = listOf("Sent to recipient: -$doubleAmount ETH") + transactionLogs

                                isSendingTx = false
                                activeTab = 0
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Send Transaction", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun GrokAutomationPopupPortal(viewModel: BrowserViewModel) {
    var automationEnabled by remember { mutableStateOf(viewModel.prefs.getBoolean("ext_grok_automation_status", true)) }
    var speedSetting by remember { mutableFloatStateOf(viewModel.prefs.getInt("ext_grok_automation_speed", 2).toFloat()) }
    
    // Live ticking console feed!
    var consoleLogs by remember {
        mutableStateOf(
            listOf(
                "[Status] Grok Automation Initialized.",
                "[Agent] Scanned page anchors: 5 found."
            )
        )
    }

    LaunchedEffect(automationEnabled) {
        if (automationEnabled) {
            while (true) {
                kotlinx.coroutines.delay(1800)
                val newLog = when ((1..5).random()) {
                    1 -> "[Agent] Scanning fields for inputs..."
                    2 -> "[Bypass] Automatically resolving verification challenges..."
                    3 -> "[Auto-Fill] Injected safe test variables in forms."
                    4 -> "[System] Scraping text targets dynamically..."
                    else -> "[Agent] Found submission forms, bypass completed."
                }
                consoleLogs = (listOf(newLog) + consoleLogs).take(6)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("AUTOMATION ENGINE STATUS", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(if (automationEnabled) "Active and Injecting" else "Engine Suspended", color = if (automationEnabled) Color.Green else Color.Red, fontSize = 13.sp)
            }
            Switch(
                checked = automationEnabled,
                onCheckedChange = {
                    automationEnabled = it
                    viewModel.prefs.setBoolean("ext_grok_automation_status", it)
                },
                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF10B981))
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Speed setting slider
        Text("Automation Speed Multiplier: ${speedSetting.toInt()}x", color = Color.White, fontSize = 12.sp)
        Slider(
            value = speedSetting,
            onValueChange = { newValue ->
                speedSetting = newValue
                viewModel.prefs.setInt("ext_grok_automation_speed", newValue.toInt())
            },
            valueRange = 1f..5f,
            steps = 3,
            colors = SliderDefaults.colors(thumbColor = Color(0xFF10B981), activeTrackColor = Color(0xFF10B981))
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Simulated Command logs terminal
        Text("LIVE INJECTION CONSOLE TERMINAL", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black, RoundedCornerShape(10.dp))
                .padding(10.dp)
                .height(110.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            consoleLogs.forEach { log ->
                Text(log, color = Color(0xFF10B981), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun DarkReaderPopupPortal(viewModel: BrowserViewModel) {
    val context = LocalContext.current
    var brightness by remember { mutableFloatStateOf(viewModel.prefs.getInt("ext_dark_reader_brightness", 90).toFloat()) }
    var contrast by remember { mutableFloatStateOf(viewModel.prefs.getInt("ext_dark_reader_contrast", 100).toFloat()) }
    var sepia by remember { mutableFloatStateOf(viewModel.prefs.getInt("ext_dark_reader_sepia", 0).toFloat()) }
    var grayscale by remember { mutableFloatStateOf(viewModel.prefs.getInt("ext_dark_reader_grayscale", 0).toFloat()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("LIVE SHADER STYLING CONTROLS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)

        // Slider 1: Brightness
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Brightness", color = Color.LightGray, fontSize = 11.sp)
                Text("${brightness.toInt()}%", color = Color.White, fontSize = 11.sp)
            }
            Slider(
                value = brightness,
                onValueChange = { 
                    brightness = it
                    viewModel.prefs.setInt("ext_dark_reader_brightness", it.toInt())
                    viewModel.applyDarkReaderLiveFilters()
                },
                valueRange = 20f..200f,
                colors = SliderDefaults.colors(thumbColor = Color(0xFF6366F1), activeTrackColor = Color(0xFF6366F1))
            )
        }

        // Slider 2: Contrast
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Contrast", color = Color.LightGray, fontSize = 11.sp)
                Text("${contrast.toInt()}%", color = Color.White, fontSize = 11.sp)
            }
            Slider(
                value = contrast,
                onValueChange = { 
                    contrast = it
                    viewModel.prefs.setInt("ext_dark_reader_contrast", it.toInt())
                    viewModel.applyDarkReaderLiveFilters()
                },
                valueRange = 20f..200f,
                colors = SliderDefaults.colors(thumbColor = Color(0xFF6366F1), activeTrackColor = Color(0xFF6366F1))
            )
        }

        // Slider 3: Sepia
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Sepia", color = Color.LightGray, fontSize = 11.sp)
                Text("${sepia.toInt()}%", color = Color.White, fontSize = 11.sp)
            }
            Slider(
                value = sepia,
                onValueChange = { 
                    sepia = it
                    viewModel.prefs.setInt("ext_dark_reader_sepia", it.toInt())
                    viewModel.applyDarkReaderLiveFilters()
                },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(thumbColor = Color(0xFF6366F1), activeTrackColor = Color(0xFF6366F1))
            )
        }

        // Reset button
        Button(
            onClick = {
                brightness = 90f
                contrast = 100f
                sepia = 0f
                grayscale = 0f
                viewModel.prefs.setInt("ext_dark_reader_brightness", 90)
                viewModel.prefs.setInt("ext_dark_reader_contrast", 100)
                viewModel.prefs.setInt("ext_dark_reader_sepia", 0)
                viewModel.prefs.setInt("ext_dark_reader_grayscale", 0)
                viewModel.applyDarkReaderLiveFilters()
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Reset Shader Filters", color = Color.White, fontSize = 12.sp)
        }
    }
}

@Composable
fun Grok4AiPopupPortal(viewModel: BrowserViewModel) {
    val coroutineScope = rememberCoroutineScope()
    var textInput by remember { mutableStateOf("") }
    var responsesList by remember {
        mutableStateOf(
            listOf(
                "Grok 4.0 AI" to "Hello! I am your visual Grok AI background assistant. Ask me anything about the active tab or browsing task queries!"
            )
        )
    }
    var isReplying by remember { mutableStateOf(false) }

    // Fetch context URL and title of the active tab!
    val activeTabId = viewModel.uiState.collectAsState().value.activeTabId
    val activeTabName = remember(activeTabId) {
        viewModel.uiState.value.tabs.find { it.id == activeTabId }?.title ?: "Orion Home"
    }
    val activeTabUrl = remember(activeTabId) {
        viewModel.uiState.value.tabs.find { it.id == activeTabId }?.url ?: "orion://newtab"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        // Tab indicator active context
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(6.dp).background(Color(0xFFFFB020), CircleShape))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "Context Tab: $activeTabName",
                fontSize = 10.sp,
                color = Color.LightGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Chat messages box log scrollable
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            responsesList.forEach { (sender, msg) ->
                val isUser = sender == "You"
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isUser) Color(0xFF6366F1) else Color(0xFF1E293B),
                                shape = RoundedCornerShape(
                                    topStart = 12.dp,
                                    topEnd = 12.dp,
                                    bottomStart = if (isUser) 12.dp else 0.dp,
                                    bottomEnd = if (isUser) 0.dp else 12.dp
                                )
                            )
                            .padding(8.dp)
                    ) {
                        Text(msg, color = Color.White, fontSize = 11.sp, lineHeight = 15.sp)
                    }
                }
            }
            if (isReplying) {
                Text("Grok typing...", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Input field and send button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = { Text("Ask Grok...", fontSize = 11.sp, color = Color.Gray) },
                textStyle = TextStyle(color = Color.White, fontSize = 11.sp),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            )

            IconButton(
                onClick = {
                    if (textInput.isBlank()) return@IconButton
                    val prompt = textInput
                    responsesList = responsesList + ("You" to prompt)
                    textInput = ""
                    isReplying = true

                    coroutineScope.launch {
                        kotlinx.coroutines.delay(1000)
                        val reply = when {
                            prompt.contains("explain", ignoreCase = true) || prompt.contains("summarize", ignoreCase = true) -> {
                                "I have scanned the document text on '$activeTabName'. This site refers to web elements, dynamic configurations, or public profiles. It contains 3 key headers."
                            }
                            prompt.contains("wallet", ignoreCase = true) || prompt.contains("crypto", ignoreCase = true) -> {
                                "I integrate with MetaMask adapter perfectly. You can manage coins, transfer ETH, or swap tokens securely."
                            }
                            else -> {
                                "Intelligent response regarding '$prompt' within browsing context '$activeTabUrl' successfully completed."
                            }
                        }
                        responsesList = responsesList + ("Grok 4.0 AI" to reply)
                        isReplying = false
                    }
                },
                modifier = Modifier
                    .background(Color(0xFF6366F1), CircleShape)
                    .size(36.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun GenericPopupView(extensionId: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Background Context Active",
                color = Color.Green,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "The target extensions code operates actively on visited tabs. No visual popup workspace resides in its bundle resource.",
                color = Color.LightGray,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

fun getFullExtensionsList(viewModel: BrowserViewModel): List<ExtensionMeta> {
    val list = EXTENSIONS_CATALOG.toMutableList()
    
    // Add dynamic extensions from database
    try {
        val dbExtensions = viewModel.getInstalledDbExtensions()
        for (ext in dbExtensions) {
            if (list.any { it.id == ext.id }) continue
            list.add(
                ExtensionMeta(
                    id = ext.id,
                    name = ext.name,
                    description = ext.description.ifBlank { "Unpacked Chrome Extension" },
                    version = "v" + ext.version,
                    size = "Dynamic Size",
                    provider = "Chrome Web Store / ZIP",
                    lastUpdated = "Installed",
                    permissionDescription = "Permissions: ${ext.permissions.joinToString(", ").ifBlank { "none" }}" + 
                                            if (ext.hostPermissions.isNotEmpty()) "; Host permissions: ${ext.hostPermissions.joinToString(", ")}" else "",
                    defaultInstalled = true
                )
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    val customName = viewModel.getUploadedExtensionName()
    if (customName != "No uploaded ZIP extension") {
        val uploadedId = viewModel.prefs.getString("ext_uploaded_id", "ext_uploaded_script_enabled")
        if (list.none { it.id == uploadedId || it.id == "ext_uploaded_script_enabled" }) {
            list.add(
                ExtensionMeta(
                    id = uploadedId,
                    name = customName,
                    description = "Custom extension downloaded from Chrome Web Store or uploaded via local ZIP containing isolated scripts.",
                    version = "v1.5.0",
                    size = "142 KB",
                    provider = "Chrome Web Store / ZIP Source",
                    lastUpdated = "Recently Updated",
                    permissionDescription = "Intercept web layouts, inject CSS dynamically, isolate Javascript behaviors.",
                    defaultInstalled = true
                )
            )
        }
    }
    return list
}

fun isExtensionInstalled(viewModel: BrowserViewModel, metaId: String): Boolean {
    val uploadedId = viewModel.prefs.getString("ext_uploaded_id", "ext_uploaded_script_enabled")
    if (metaId == "ext_uploaded_script_enabled" || metaId == uploadedId) {
        return viewModel.getUploadedExtensionName() != "No uploaded ZIP extension"
    }
    val defaultInstalled = EXTENSIONS_CATALOG.find { it.id == metaId }?.defaultInstalled ?: false
    if (viewModel.prefs.getBoolean("ext_installed_$metaId", defaultInstalled)) {
        return true
    }
    return try {
        viewModel.getInstalledDbExtensions().any { it.id == metaId }
    } catch(e: Exception) {
        false
    }
}

@Composable
fun AdBlockPopupPortal(viewModel: BrowserViewModel) {
    var globalAdBlock by remember { mutableStateOf(com.example.engine.AdBlocker.globalAdBlockEnabled) }
    var globalTrackers by remember { mutableStateOf(com.example.engine.AdBlocker.globalTrackersEnabled) }
    var youtubeSkip by remember { mutableStateOf(com.example.engine.AdBlocker.youtubeAdSkipEnabled) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total Blocked Items", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("14,832", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                }
                Box(modifier = Modifier.width(1.dp).height(40.dp).background(Color.White.copy(alpha = 0.1f)))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Active Page Filter", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Secure", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3B82F6))
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("RULE CONTEXTS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(11.dp))
                    Column {
                        Text("Block Advertisements", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Remove popups, overlays & banners", fontSize = 10.sp, color = Color.LightGray)
                    }
                }
                Switch(
                    checked = globalAdBlock,
                    onCheckedChange = {
                        globalAdBlock = it
                        viewModel.setGlobalAdBlockEnabled(it)
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF6366F1))
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF3B82F6), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(11.dp))
                    Column {
                        Text("Block Tracking Scripts", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Shield cookies and host analytics", fontSize = 10.sp, color = Color.LightGray)
                    }
                }
                Switch(
                    checked = globalTrackers,
                    onCheckedChange = {
                        globalTrackers = it
                        viewModel.setGlobalTrackersEnabled(it)
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF6366F1))
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(11.dp))
                    Column {
                        Text("Auto-Skip Video Ads", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Instantly speed up video ads", fontSize = 10.sp, color = Color.LightGray)
                    }
                }
                Switch(
                    checked = youtubeSkip,
                    onCheckedChange = {
                        youtubeSkip = it
                        viewModel.setYoutubeAdSkipEnabled(it)
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF6366F1))
                )
            }
        }
    }
}

@Composable
fun CustomUploadedExtensionPopupPortal(viewModel: BrowserViewModel) {
    var inputCodeToExecute by remember { mutableStateOf("") }
    var executionResultLog by remember { mutableStateOf("Ready to inject command...") }
    val savedScriptCode = remember { viewModel.prefs.getString("ext_uploaded_script_code", "") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.08f)),
            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color(0xFF10B981), CircleShape)
                )
                Column {
                    Text(
                        text = "Active Sandbox Environment",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    )
                    Text(
                        text = "Scripts isolate correctly on top of host WebView DOM.",
                        fontSize = 10.sp,
                        color = Color.LightGray
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "BUNDLE SCRIPT PREVIEW", 
                fontSize = 11.sp, 
                fontWeight = FontWeight.Bold, 
                color = Color.White.copy(alpha = 0.5f)
            )
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 140.dp)
            ) {
                Box(modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
                    Text(
                        text = if (savedScriptCode.isNotBlank()) savedScriptCode else "// No script bundle source detected.",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFF38BDF8)
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "SANDBOX CONSOLE", 
                fontSize = 11.sp, 
                fontWeight = FontWeight.Bold, 
                color = Color.White.copy(alpha = 0.5f)
            )

            OutlinedTextField(
                value = inputCodeToExecute,
                onValueChange = { inputCodeToExecute = it },
                placeholder = { 
                    Text(
                        "document.title; or alert('Injected!');", 
                        fontSize = 12.sp, 
                        color = Color.White.copy(alpha = 0.3f)
                    ) 
                },
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color.White),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF6366F1),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                ),
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        if (inputCodeToExecute.isBlank()) return@Button
                        executionResultLog = "Executing script command..."
                        viewModel.evaluateJavascriptOnActiveWebview(inputCodeToExecute) { res ->
                            executionResultLog = "Status: OK\nResponse: $res"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Execute on Current Tab", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                Button(
                    onClick = {
                        inputCodeToExecute = "document.body.style.filter = 'grayscale(100%)';"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f), contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text("Preset", fontSize = 11.sp)
                }
            }

            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.6f)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("LOG OUTPUT:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = executionResultLog,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color.Green.copy(alpha = 0.82f)
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveExtensionsDialog(
    viewModel: BrowserViewModel,
    onDismissRequest: () -> Unit,
    onOpenExtensionPopup: (String) -> Unit,
    onManageExtensions: () -> Unit
) {
    val parsedCatalog = getFullExtensionsList(viewModel)
    
    // Partition active extensions
    val activeExtensions = parsedCatalog.filter { meta ->
        isExtensionInstalled(viewModel, meta.id) &&
        viewModel.isExtensionEnabled(meta.id)
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)), // ambient layout styling
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Extension,
                            contentDescription = null,
                            tint = Color(0xFF6366F1),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Extensions",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (activeExtensions.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Extension,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.25f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No active extensions found",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Enable any extension to open its popup",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                    ) {
                        items(activeExtensions) { meta ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onDismissRequest()
                                        onOpenExtensionPopup(meta.id)
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Color(0xFF6366F1).copy(alpha = 0.15f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = when (meta.id) {
                                                "ext_metamask" -> Icons.Default.Wallet
                                                "ext_grok_4" -> Icons.Default.SmartToy
                                                "ext_grok_automation" -> Icons.Default.ElectricBolt
                                                "ext_dark_reader" -> Icons.Default.Brightness4
                                                "ext_adblock" -> Icons.Default.Shield
                                                else -> Icons.Default.Extension
                                            },
                                            contentDescription = null,
                                            tint = Color(0xFF818CF8),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = meta.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "v" + meta.version,
                                            fontSize = 10.sp,
                                            color = Color(0xFF818CF8)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Button(
                                        onClick = {
                                            onDismissRequest()
                                            onOpenExtensionPopup(meta.id)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Open", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(
                        onClick = {
                            onDismissRequest()
                            onManageExtensions()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = Color(0xFF818CF8),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Manage Extensions",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF818CF8)
                        )
                    }
                }
            }
        }
    }
}
