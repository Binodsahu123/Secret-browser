package com.example.browser

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun ExtensionsOverlay(
    viewModel: BrowserViewModel,
    onDismiss: () -> Unit,
    isGlass: Boolean = false
) {
    val extensionList = remember(viewModel) { getFullExtensionsList(viewModel) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Extensions Management", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(extensionList.size) { index ->
                        val ext = extensionList[index]
                        var isEnabled by remember(ext.id) { mutableStateOf(viewModel.isExtensionEnabled(ext.id)) }

                        ListItem(
                            headlineContent = { Text(ext.name, fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text(ext.description, style = MaterialTheme.typography.bodyMedium) },
                            trailingContent = {
                                Switch(
                                    checked = isEnabled,
                                    onCheckedChange = { checked ->
                                        isEnabled = checked
                                        viewModel.setExtensionEnabled(ext.id, checked)
                                    }
                                )
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
