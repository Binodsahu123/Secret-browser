package com.example.browser

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

data class AIAnalysisResult(
    val mainTopic: String = "",
    val summary: String = "",
    val keyPoints: List<String> = emptyList(),
    val pros: List<String> = emptyList(),
    val cons: List<String> = emptyList(),
    val factsAndStats: List<String> = emptyList(),
    val dates: List<String> = emptyList(),
    val peopleAndEntities: List<String> = emptyList(),
    val rawResponse: String = ""
)

fun parseAnalysis(raw: String): AIAnalysisResult {
    var currentSection = ""
    var mainTopic = ""
    var summary = ""
    val keyPoints = mutableListOf<String>()
    val pros = mutableListOf<String>()
    val cons = mutableListOf<String>()
    val factsAndStats = mutableListOf<String>()
    val dates = mutableListOf<String>()
    val peopleAndEntities = mutableListOf<String>()

    val lines = raw.lines()
    var inPros = false
    var inCons = false

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) continue

        if (trimmed.startsWith("[MAIN TOPIC]", ignoreCase = true)) {
            currentSection = "topic"
            continue
        } else if (trimmed.startsWith("[SUMMARY]", ignoreCase = true)) {
            currentSection = "summary"
            continue
        } else if (trimmed.startsWith("[KEY POINTS]", ignoreCase = true)) {
            currentSection = "key_points"
            continue
        } else if (trimmed.startsWith("[PROS AND CONS]", ignoreCase = true)) {
            currentSection = "pros_cons"
            continue
        } else if (trimmed.startsWith("[IMPORTANT FACTS & STATS]", ignoreCase = true)) {
            currentSection = "facts"
            continue
        } else if (trimmed.startsWith("[IMPORTANT DATES]", ignoreCase = true)) {
            currentSection = "dates"
            continue
        } else if (trimmed.startsWith("[IMPORTANT PEOPLE & ENTITIES]", ignoreCase = true)) {
            currentSection = "people"
            continue
        }

        when (currentSection) {
            "topic" -> mainTopic = trimmed
            "summary" -> summary = if (summary.isEmpty()) trimmed else "$summary\n$trimmed"
            "key_points" -> {
                val point = if (trimmed.startsWith("-") || trimmed.startsWith("*")) trimmed.substring(1).trim() else trimmed
                keyPoints.add(point)
            }
            "pros_cons" -> {
                if (trimmed.startsWith("Pros:", ignoreCase = true)) {
                    inPros = true
                    inCons = false
                } else if (trimmed.startsWith("Cons:", ignoreCase = true)) {
                    inPros = false
                    inCons = true
                } else {
                    val item = if (trimmed.startsWith("-") || trimmed.startsWith("*")) trimmed.substring(1).trim() else trimmed
                    if (inPros) pros.add(item)
                    if (inCons) cons.add(item)
                }
            }
            "facts" -> {
                val item = if (trimmed.startsWith("-") || trimmed.startsWith("*")) trimmed.substring(1).trim() else trimmed
                factsAndStats.add(item)
            }
            "dates" -> {
                val item = if (trimmed.startsWith("-") || trimmed.startsWith("*")) trimmed.substring(1).trim() else trimmed
                dates.add(item)
            }
            "people" -> {
                val item = if (trimmed.startsWith("-") || trimmed.startsWith("*")) trimmed.substring(1).trim() else trimmed
                peopleAndEntities.add(item)
            }
        }
    }

    if (summary.isEmpty()) {
        summary = raw
    }

    return AIAnalysisResult(
        mainTopic = mainTopic,
        summary = summary,
        keyPoints = keyPoints,
        pros = pros,
        cons = cons,
        factsAndStats = factsAndStats,
        dates = dates,
        peopleAndEntities = peopleAndEntities,
        rawResponse = raw
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIChatPanel(
    pageText: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { AISettingsManager(context) }
    val scope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf("summary") } // "summary" or "chat"
    
    // Summary states
    var summaryLoading by remember { mutableStateOf(true) }
    var analysisResult by remember { mutableStateOf<AIAnalysisResult?>(null) }
    
    // Chat states
    var chatHistory = remember { mutableStateListOf<Pair<String, String>>() }
    var chatInputText by remember { mutableStateOf("") }
    var chalLoading by remember { mutableStateOf(false) }
    val chatListState = rememberLazyListState()

    // Trigger page summary once on open
    LaunchedEffect(pageText) {
        if (pageText.isBlank()) {
            summaryLoading = false
            analysisResult = AIAnalysisResult(
                mainTopic = "Empty Webpage",
                summary = "The browser cannot extract any readable text on this blank page or system address page."
            )
            return@LaunchedEffect
        }
        
        summaryLoading = true
        try {
            val result = AISummaryEngine.analyzePage(pageText, settingsManager)
            analysisResult = parseAnalysis(result)
        } catch (e: Exception) {
            analysisResult = AIAnalysisResult(
                mainTopic = "Analysis Error",
                summary = "An unexpected error occurred during page parsing: ${e.localizedMessage}"
            )
        } finally {
            summaryLoading = false
        }
    }

    fun postChatMessage(message: String) {
        if (message.isBlank()) return
        chatHistory.add("user" to message)
        chatInputText = ""
        chalLoading = true
        
        scope.launch {
            // Scroll to end of list
            chatListState.animateScrollToItem(chatHistory.size)
            
            val response = AISummaryEngine.chatSession(chatHistory.toList(), message, settingsManager)
            chatHistory.add("assistant" to response)
            chalLoading = false
            
            // Scroll to end of list
            chatListState.animateScrollToItem(chatHistory.size)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.78f),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        shadowElevation = 16.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Sliding handle & Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp, 4.dp)
                        .background(Color.Gray.copy(alpha = 0.5f), CircleShape)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "SwiftAI Specialist",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close AI panel")
                }
            }

            // Tab Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { activeTab = "summary" },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeTab == "summary") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (activeTab == "summary") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Page Report", fontSize = 13.sp)
                }

                Button(
                    onClick = { activeTab = "chat" },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeTab == "chat") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (activeTab == "chat") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ask Specialist", fontSize = 13.sp)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(top = 4.dp))

            // Tab Contents
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (activeTab == "summary") {
                    if (summaryLoading) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Extracting details and requesting AI synthesis...",
                                fontSize = 13.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else if (analysisResult != null) {
                        val result = analysisResult!!
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Main Topic Card
                            if (result.mainTopic.isNotEmpty()) {
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(Icons.Default.Topic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                Text("PRIMARY SUBJECT", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(result.mainTopic, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            // Summary Card
                            if (result.summary.isNotEmpty()) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(Icons.Default.Description, contentDescription = null)
                                                Text("SUMMARY", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(result.summary, fontSize = 13.sp, lineHeight = 19.sp)
                                        }
                                    }
                                }
                            }

                            // Key Takeaways KeyPoints Card
                            if (result.keyPoints.isNotEmpty()) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFBBF24))
                                                Text("CRITICAL TAKEAWAYS", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            result.keyPoints.forEach { point ->
                                                Row(
                                                    modifier = Modifier.padding(vertical = 4.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text("•", fontWeight = FontWeight.Bold)
                                                    Text(point, fontSize = 13.sp, lineHeight = 18.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Pros and Cons Cards
                            if (result.pros.isNotEmpty() || result.cons.isNotEmpty()) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        if (result.pros.isNotEmpty()) {
                                            Card(
                                                modifier = Modifier.weight(1f),
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFFFEE))
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Text("PROS", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF22C55E))
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    result.pros.forEach { item ->
                                                        Text("• $item", fontSize = 12.sp, lineHeight = 16.sp, color = Color.DarkGray)
                                                    }
                                                }
                                            }
                                        }

                                        if (result.cons.isNotEmpty()) {
                                            Card(
                                                modifier = Modifier.weight(1f),
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1F1))
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Text("CONS", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFEF4444))
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    result.cons.forEach { item ->
                                                        Text("• $item", fontSize = 12.sp, lineHeight = 16.sp, color = Color.DarkGray)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Important Stats Card
                            if (result.factsAndStats.isNotEmpty()) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(Icons.Default.Insights, contentDescription = null)
                                                Text("KEY STATISTICS & FACTS", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            result.factsAndStats.forEach { stat ->
                                                Row(modifier = Modifier.padding(vertical = 3.dp)) {
                                                    Text("📊 ", fontSize = 12.sp)
                                                    Text(stat, fontSize = 13.sp, lineHeight = 18.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Timeline Dates Card
                            if (result.dates.isNotEmpty()) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(Icons.Default.DateRange, contentDescription = null)
                                                Text("CRITICAL TIMELINE DATES", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            result.dates.forEach { date ->
                                                Row(modifier = Modifier.padding(vertical = 3.dp)) {
                                                    Text("📅 ", fontSize = 12.sp)
                                                    Text(date, fontSize = 13.sp, lineHeight = 17.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Important People & Entities
                            if (result.peopleAndEntities.isNotEmpty()) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(Icons.Default.People, contentDescription = null)
                                                Text("PEOPLE & ENTITIES", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            result.peopleAndEntities.forEach { item ->
                                                Row(modifier = Modifier.padding(vertical = 3.dp)) {
                                                    Text("🏢 ", fontSize = 12.sp)
                                                    Text(item, fontSize = 13.sp, lineHeight = 17.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Chat Interface
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Chats Scroll Area
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            if (chatHistory.isEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChatBubbleOutline,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Session Memory Instantiated",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Ask any question, fact check statements, request bullet notes, draft highlights, or translate specific sentences on the page.",
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            } else {
                                LazyColumn(
                                    state = chatListState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp),
                                    contentPadding = PaddingValues(top = 10.dp, bottom = 40.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(chatHistory) { (role, message) ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = if (role == "user") Arrangement.End else Arrangement.Start
                                        ) {
                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (role == "user") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                                ),
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier.widthIn(max = 280.dp)
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Text(
                                                        text = if (role == "user") "You" else "Specialist",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        color = if (role == "user") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(message, fontSize = 13.sp, lineHeight = 18.sp)
                                                }
                                            }
                                        }
                                    }

                                    if (chalLoading) {
                                        item {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.Start
                                            ) {
                                                Card(
                                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                                    shape = RoundedCornerShape(16.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(12.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                        Text("Drafting thinking process...", fontSize = 12.sp, color = Color.Gray)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Short Suggestions Menu
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val suggestions = listOf("Explain page", "Fact check stats", "Draft notes", "Translate page")
                            suggestions.forEach { label ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.clickable {
                                        postChatMessage("Can you: $label inside this webpage?")
                                    }
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // Input bottom bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = chatInputText,
                                onValueChange = { chatInputText = it },
                                placeholder = { Text("Ask about this website...") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(24.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = {
                                    if (chatInputText.isNotBlank()) {
                                        postChatMessage(chatInputText)
                                    }
                                })
                            )

                            FloatingActionButton(
                                onClick = {
                                    if (chatInputText.isNotBlank()) {
                                        postChatMessage(chatInputText)
                                    }
                                },
                                shape = CircleShape,
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "Send")
                            }
                        }
                    }
                }
            }
        }
    }
}

// Simple extension helper
val ButtonDefaults.onColor: Color
    @Composable
    get() = if (MaterialTheme.colorScheme.primary == Color.White) Color.Black else Color.White
