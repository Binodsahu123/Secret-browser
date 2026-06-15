package com.example.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveExtensionsDialog(
    viewModel: BrowserViewModel,
    onDismissRequest: () -> Unit,
    onOpenExtensionPopup: (String) -> Unit,
    onManageExtensions: () -> Unit
) {
    val activeExtensions = remember(viewModel) {
        getFullExtensionsList(viewModel).filter { viewModel.isExtensionEnabled(it.id) }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = {
                onManageExtensions()
                onDismissRequest()
            }) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Manage Extensions")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Close")
            }
        },
        title = {
            Text("Extensions Hub", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (activeExtensions.isEmpty()) {
                    Text("No active extensions. Open settings to install or customize.", fontSize = 14.sp)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 250.dp)) {
                        items(activeExtensions.size) { index ->
                            val ext = activeExtensions[index]
                            ListItem(
                                headlineContent = { Text(ext.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) },
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Default.Extension,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                modifier = Modifier.clickable {
                                    onOpenExtensionPopup(ext.id)
                                    onDismissRequest()
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    )
}
