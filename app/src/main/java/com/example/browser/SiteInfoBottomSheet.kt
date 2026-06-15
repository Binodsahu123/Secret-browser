package com.example.browser

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.HistoryItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteInfoBottomSheet(
    url: String,
    title: String,
    blockedCount: Int,
    isAdBlockWhitelisted: Boolean,
    historyItems: List<HistoryItem>,
    onToggleAdBlock: () -> Unit,
    onOpenSearch: (String) -> Unit,
    onOpenSiteSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text("Site Integrity Info", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            val isHttps = url.startsWith("https://", ignoreCase = true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isHttps) Icons.Default.Lock else Icons.Default.Info,
                    contentDescription = null,
                    tint = if (isHttps) Color(0xFF10B981) else Color(0xFFEF4444),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(if (isHttps) "Secure SSL Connection" else "Unsecure Connection", fontWeight = FontWeight.Bold)
                    Text(if (isHttps) "Your connection is encrypted." else "Avoid entering passwords or credit cards.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = if (isAdBlockWhitelisted) Color.Gray else Color(0xFF6366F1),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (isAdBlockWhitelisted) "Ad Blocker: Off (Whitelisted)" else "Ad Blocker: On ($blockedCount Blocked)", fontWeight = FontWeight.Bold)
                    Text("Saves bandwidth and blocks malware targets.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = !isAdBlockWhitelisted,
                    onCheckedChange = { onToggleAdBlock() }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onOpenSiteSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Site Settings")
            }
        }
    }
}
