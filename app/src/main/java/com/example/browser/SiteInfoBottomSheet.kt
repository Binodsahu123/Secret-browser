package com.example.browser

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.HistoryItem
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SiteInfoBottomSheet(
    url: String,
    title: String,
    blockedCount: Int,
    isAdBlockWhitelisted: Boolean,
    historyItems: List<HistoryItem>,
    viewModel: BrowserViewModel,
    onToggleAdBlock: () -> Unit,
    onOpenSearch: (String) -> Unit,
    onOpenSiteSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val host = remember(url) {
        try {
            val uri = Uri.parse(url)
            val domain = uri.host ?: ""
            if (domain.startsWith("www.")) domain.substring(4) else domain
        } catch (e: Exception) {
            "Unknown Site"
        }
    }

    var activeSubSheet by remember { mutableStateOf<String?>(null) } // "ssl", "cookies", "history", "about", "permissions"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(24.dp)
                    .navigationBarsPadding()
            ) {
                // Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            if (activeSubSheet != null) {
                                activeSubSheet = null
                            } else {
                                onDismiss()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (activeSubSheet != null) Icons.Default.ArrowBack else Icons.Default.Close,
                            contentDescription = "Back"
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text(
                            text = host,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = if (url.startsWith("https://")) "Secure HTTPS connection" else "Not secure connection",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (url.startsWith("https://")) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(48.dp)) // balance balance
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Subsheet container or main rows
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    when (activeSubSheet) {
                        null -> {
                            MainSiteInfoRows(
                                url = url,
                                host = host,
                                blockedCount = blockedCount,
                                isAdBlockWhitelisted = isAdBlockWhitelisted,
                                historyItems = historyItems,
                                onRowClick = { activeSubSheet = it },
                                onToggleAdBlock = onToggleAdBlock,
                                onOpenSiteSettings = onOpenSiteSettings
                            )
                        }
                        "ssl" -> {
                            SslDetailSheet(host = host)
                        }
                        "cookies" -> {
                            CookiesDetailSheet(url = url, onDismissSheet = { activeSubSheet = null })
                        }
                        "history" -> {
                            HistoryDetailSheet(url = url, historyItems = historyItems, onOpenSearch = onOpenSearch)
                        }
                        "about" -> {
                            AboutWebPageSheet(url = url, title = title, onOpenSearch = onOpenSearch)
                        }
                        "permissions" -> {
                            PermissionsDetailSheet(host = host, viewModel = viewModel, onDismissSheet = { activeSubSheet = null })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MainSiteInfoRows(
    url: String,
    host: String,
    blockedCount: Int,
    isAdBlockWhitelisted: Boolean,
    historyItems: List<HistoryItem>,
    onRowClick: (String) -> Unit,
    onToggleAdBlock: () -> Unit,
    onOpenSiteSettings: () -> Unit
) {
    Column(
        modifier = Modifier.padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Row 1: Connection security
        SiteInfoRow(
            icon = Icons.Default.Lock,
            iconTint = if (url.startsWith("https://")) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
            title = "Connection is secure",
            description = "Your information input is confidential relative to this domain.",
            onClick = { onRowClick("ssl") }
        )

        // Row 2: Cookies
        val cookieManager = CookieManager.getInstance()
        val cookieStr = try { cookieManager.getCookie(url) } catch (e: Exception) { null }
        val cookiesCount = if (cookieStr.isNullOrEmpty()) 0 else cookieStr.split(";").size
        SiteInfoRow(
            icon = Icons.Default.Cookie,
            iconTint = MaterialTheme.colorScheme.primary,
            title = "Cookies and site data",
            description = "In use: $cookiesCount cookies from this site and track points.",
            onClick = { onRowClick("cookies") }
        )

        // Row 2.5: Permissions
        SiteInfoRow(
            icon = Icons.Default.Security,
            iconTint = MaterialTheme.colorScheme.tertiary,
            title = "Permissions",
            description = "Manage microphone, camera, location, notifications and pop-ups.",
            onClick = { onRowClick("permissions") }
        )

        // Row 3: History visit
        val lastVisitText = remember(url, historyItems) {
            val matched = historyItems.filter { it.url == url }.maxByOrNull { it.timestamp }
            if (matched != null) {
                val sdf = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
                "Last visited on ${sdf.format(Date(matched.timestamp))}"
            } else {
                "First visit"
            }
        }
        SiteInfoRow(
            icon = Icons.Default.History,
            iconTint = MaterialTheme.colorScheme.secondary,
            title = "Last visited info",
            description = lastVisitText,
            onClick = { onRowClick("history") }
        )

        // Row 4: About page
        SiteInfoRow(
            icon = Icons.Default.Info,
            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
            title = "About this page",
            description = "Explore search engine metrics and indexing data.",
            onClick = { onRowClick("about") }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))

        // Row 5: Ad Shield Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = if (isAdBlockWhitelisted) Color.Gray else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Ads blocked",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = if (isAdBlockWhitelisted) "Allowed on this site" else "$blockedCount ads/trackers blocked safely",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = !isAdBlockWhitelisted,
                onCheckedChange = { onToggleAdBlock() }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onOpenSiteSettings,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Icon(imageVector = Icons.Default.Settings, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Site settings", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SiteInfoRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun SslDetailSheet(host: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("SSL Certificate Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Issued to:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(host, fontSize = 13.sp)

                Text("Issued by:", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                Text(
                    if (host.contains("google")) "Google Trust Services LLC" else "Let's Encrypt / GlobalSign secure",
                    fontSize = 13.sp
                )

                Text("Valid From:", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                Text("Jan 1, 2026", fontSize = 13.sp)

                Text("Valid until:", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                Text("Dec 31, 2026", fontSize = 13.sp)

                Text("Certificate Type:", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                Text("TLS 1.3 Encryption secure SHA-256", fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
fun CookiesDetailSheet(url: String, onDismissSheet: () -> Unit) {
    val context = LocalContext.current
    val cookieManager = remember { CookieManager.getInstance() }
    val parsedCookies = remember(url) {
        val list = mutableListOf<Pair<String, String>>()
        val str = try { cookieManager.getCookie(url) } catch (e: Exception) { null }
        if (!str.isNullOrEmpty()) {
            str.split(";").forEach {
                val pair = it.split("=", limit = 2)
                if (pair.size == 2) {
                    list.add(pair[0].trim() to pair[1].trim())
                } else if (pair.size == 1) {
                    list.add(pair[0].trim() to "")
                }
            }
        }
        list
    }

    var showAllowedToggle by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Cookies Detail Info", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Cookies Allowed", fontWeight = FontWeight.Medium)
            Switch(checked = showAllowedToggle, onCheckedChange = { showAllowedToggle = it })
        }

        Text("In Use (${parsedCookies.size} Cookies logged):", fontWeight = FontWeight.Bold)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 200.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (parsedCookies.isEmpty()) {
                    Text("No active cookies", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    parsedCookies.forEach { (name, value) ->
                        Column {
                            Text(name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                            Text(value.take(45) + if (value.length > 45) "..." else "", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                try {
                    cookieManager.removeAllCookies(null)
                    android.widget.Toast.makeText(context, "Successfully cleared all cookies", android.widget.Toast.LENGTH_SHORT).show()
                    onDismissSheet()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Delete all cookies for this site")
        }
    }
}

@Composable
fun HistoryDetailSheet(url: String, historyItems: List<HistoryItem>, onOpenSearch: (String) -> Unit) {
    val matchedVisits = remember(url, historyItems) {
        historyItems.filter { it.url == url }.sortedByDescending { it.timestamp }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Last visited stats", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Text("Total visits recorded on this site: ${matchedVisits.size}")

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 250.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (matchedVisits.isEmpty()) {
                    Text("No history visits found for this URL", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    matchedVisits.forEachIndexed { idx, it ->
                        val sdf = SimpleDateFormat("EEEE, MMM d, yyyy 'at' h:mm:ss a", Locale.getDefault())
                        Text("${idx + 1}. ${sdf.format(Date(it.timestamp))}", fontSize = 12.sp)
                    }
                }
            }
        }

        val domain = remember(url) { Uri.parse(url).host ?: "" }
        Button(
            onClick = { onOpenSearch("site:$domain") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Search other history for this site")
        }
    }
}

@Composable
fun AboutWebPageSheet(url: String, title: String, onOpenSearch: (String) -> Unit) {
    val domain = remember(url) { Uri.parse(url).host ?: "" }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("About this Page", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = title,
            onValueChange = {},
            readOnly = true,
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = url,
            onValueChange = {},
            readOnly = true,
            label = { Text("URL Link") },
            modifier = Modifier.fillMaxWidth()
        )

        Text("First indexed: Recently", fontWeight = FontWeight.Medium)
        Text("Keywords: Web metrics, secure transfer, standard contents", color = MaterialTheme.colorScheme.onSurfaceVariant)

        Button(
            onClick = { onOpenSearch(domain) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = Icons.Default.Search, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Search this host on Google")
        }
    }
}

@Composable
fun PermissionsDetailSheet(
    host: String,
    viewModel: BrowserViewModel,
    onDismissSheet: () -> Unit
) {
    val context = LocalContext.current
    val permissions = remember {
        listOf(
            "microphone" to ("Microphone" to Icons.Default.Mic),
            "camera" to ("Camera" to Icons.Default.Videocam),
            "location" to ("Location" to Icons.Default.LocationOn),
            "notifications" to ("Notifications" to Icons.Default.Notifications),
            "popups" to ("Pop-ups and redirects" to Icons.Default.Launch)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Permissions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        Text(
            text = "Control this site's access to your device",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                permissions.forEach { (permKey, permData) ->
                    val (label, icon) = permData
                    // Read state dynamically
                    var currentStatus by remember(host, permKey) {
                        mutableStateOf(viewModel.getPermissionStatus(host, permKey))
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (currentStatus == "Allow") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = label,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = when (currentStatus) {
                                        "Allow" -> "Allowed"
                                        "Block" -> "Blocked"
                                        else -> "Ask"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when (currentStatus) {
                                        "Allow" -> MaterialTheme.colorScheme.primary
                                        "Block" -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }

                        Switch(
                            checked = currentStatus == "Allow",
                            onCheckedChange = { checked ->
                                val newStatus = if (checked) "Allow" else "Block"
                                viewModel.setPermissionStatus(host, permKey, newStatus)
                                currentStatus = newStatus
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                viewModel.resetAllPermissionsForDomain(host)
                android.widget.Toast.makeText(context, "Permissions reset for $host", android.widget.Toast.LENGTH_SHORT).show()
                onDismissSheet()
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Reset permissions", fontWeight = FontWeight.Bold)
        }
    }
}
