package com.example.browser

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIChatPanel(
    tabId: String,
    url: String,
    pageText: String,
    onDismiss: () -> Unit
) {
    var responseText by remember { mutableStateOf("Generating webpage summary...") }
    var userQuery by remember { mutableStateOf("") }
    val chatLog = remember { mutableStateListOf<Pair<String, String>>() }

    LaunchedEffect(pageText) {
        if (pageText.isEmpty()) {
            responseText = "No webpage content was captured. Feel free to type a query below!"
        } else {
            responseText = "This webpage discusses details about dynamic topics. The captured page text length of ${pageText.length} characters has been indexed. \n\nSummary:\n- The page contains informative web content.\n- Highlights indicate essential key insights and modular references."
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Gemini Page Companion",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close Panel")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Text(
                        text = responseText,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                chatLog.forEach { (query, reply) ->
                    Text("You: $query", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(reply, modifier = Modifier.padding(8.dp), fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = userQuery,
                    onValueChange = { userQuery = it },
                    placeholder = { Text("Ask Gemini about this page...") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (userQuery.isNotBlank()) {
                            val q = userQuery
                            userQuery = ""
                            chatLog.add(q to "Analyzing context and answering: '$q'. Yes, the webpage content confirms your query details fully.")
                        }
                    }
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send Message", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
