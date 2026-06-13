package com.example.browser

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
    val shortSummary: String = "",
    val summary: String = "",
    val keyPoints: List<String> = emptyList(),
    val highlights: List<String> = emptyList(),
    val pros: List<String> = emptyList(),
    val cons: List<String> = emptyList(),
    val factsAndStats: List<String> = emptyList(),
    val dates: List<String> = emptyList(),
    val peopleAndEntities: List<String> = emptyList(),
    val links: List<Pair<String, String>> = emptyList(),
    val readingTime: Int = 1,
    val rawResponse: String = ""
)

fun parseAnalysis(raw: String): AIAnalysisResult {
    var currentSection = ""
    var mainTopic = ""
    var shortSummary = ""
    var summary = ""
    val keyPoints = mutableListOf<String>()
    val highlights = mutableListOf<String>()
    val pros = mutableListOf<String>()
    val cons = mutableListOf<String>()
    val factsAndStats = mutableListOf<String>()
    val dates = mutableListOf<String>()
    val peopleAndEntities = mutableListOf<String>()
    val links = mutableListOf<Pair<String, String>>()

    val lines = raw.lines()
    var inPros = false
    var inCons = false

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) continue

        if (trimmed.startsWith("[MAIN TOPIC]", ignoreCase = true)) {
            currentSection = "topic"
            continue
        } else if (trimmed.startsWith("[SHORT SUMMARY]", ignoreCase = true)) {
            currentSection = "short_summary"
            continue
        } else if (trimmed.startsWith("[DETAILED SUMMARY]", ignoreCase = true) || trimmed.startsWith("[SUMMARY]", ignoreCase = true)) {
            currentSection = "summary"
            continue
        } else if (trimmed.startsWith("[KEY POINTS]", ignoreCase = true)) {
            currentSection = "key_points"
            continue
        } else if (trimmed.startsWith("[HIGHLIGHTS & TAKEAWAYS]", ignoreCase = true) || trimmed.startsWith("[HIGHLIGHTS]", ignoreCase = true)) {
            currentSection = "highlights"
            continue
        } else if (trimmed.startsWith("[PROS AND CONS]", ignoreCase = true)) {
            currentSection = "pros_cons"
            continue
        } else if (trimmed.startsWith("[IMPORTANT FACTS & STATS]", ignoreCase = true) || trimmed.startsWith("[IMPORTANT FACTS]", ignoreCase = true)) {
            currentSection = "facts"
            continue
        } else if (trimmed.startsWith("[IMPORTANT DATES]", ignoreCase = true)) {
            currentSection = "dates"
            continue
        } else if (trimmed.startsWith("[IMPORTANT PEOPLE & ENTITIES]", ignoreCase = true) || trimmed.startsWith("[IMPORTANT PEOPLE]", ignoreCase = true)) {
            currentSection = "people"
            continue
        } else if (trimmed.startsWith("[IMPORTANT LINKS]", ignoreCase = true)) {
            currentSection = "links"
            continue
        }

        when (currentSection) {
            "topic" -> mainTopic = trimmed
            "short_summary" -> shortSummary = if (shortSummary.isEmpty()) trimmed else "$shortSummary\n$trimmed"
            "summary" -> summary = if (summary.isEmpty()) trimmed else "$summary\n$trimmed"
            "key_points" -> {
                val point = if (trimmed.startsWith("-") || trimmed.startsWith("*")) trimmed.substring(1).trim() else trimmed
                keyPoints.add(point)
            }
            "highlights" -> {
                val pt = if (trimmed.startsWith("-") || trimmed.startsWith("*")) trimmed.substring(1).trim() else trimmed
                highlights.add(pt)
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
            "links" -> {
                val item = if (trimmed.startsWith("-") || trimmed.startsWith("*")) trimmed.substring(1).trim() else trimmed
                val parts = item.split(":", limit = 2)
                if (parts.size == 2) {
                    val titleText = parts[0].trim()
                    val linkText = parts[1].trim()
                    links.add(Pair(titleText, linkText))
                } else {
                    if (item.startsWith("http", ignoreCase = true)) {
                        links.add(Pair("Link Reference", item))
                    } else {
                        links.add(Pair(item, ""))
                    }
                }
            }
        }
    }

    if (summary.isEmpty()) {
        summary = raw
    }

    return AIAnalysisResult(
        mainTopic = mainTopic,
        shortSummary = shortSummary,
        summary = summary,
        keyPoints = keyPoints,
        highlights = highlights,
        pros = pros,
        cons = cons,
        factsAndStats = factsAndStats,
        dates = dates,
        peopleAndEntities = peopleAndEntities,
        links = links,
        readingTime = 1,
        rawResponse = raw
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIChatPanel(
    tabId: String,
    url: String,
    pageText: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { AISettingsManager(context) }
    val scope = rememberCoroutineScope()

    val bridgeSystem = remember { AIWebsiteBridgeSystem.getInstance(context) }
    val currentProvider = settingsManager.defaultProvider
    val isLoginNeeded by bridgeSystem.getLoginNeededFlow(currentProvider).collectAsState()
    val isBridgeProvider = true

    var showProviderWebLoginDialog by remember { mutableStateOf(false) }
    var summaryTriggerKey by remember { mutableStateOf(0) }

    LaunchedEffect(currentProvider) {
        bridgeSystem.getOrCreateWebView(currentProvider)
    }

    // Query active cached session for state persistence within the same tab/website
    val cachedSession = remember(tabId, url) { AISessionManager.getSession(tabId) }

    var activeTab by remember { mutableStateOf("summary") } // "summary" or "chat"
    
    // Summary loading flag and loaded results
    var summaryLoading by remember { mutableStateOf(cachedSession?.parsedSummary == null && pageText.isNotBlank()) }
    var analysisResult by remember { mutableStateOf<AIAnalysisResult?>(cachedSession?.parsedSummary) }
    
    // Initialize stateful chat session histories
    val chatHistory = remember { 
        val list = mutableStateListOf<Pair<String, String>>()
        cachedSession?.chatHistory?.let { list.addAll(it) }
        list
    }
    
    var chatInputText by remember { mutableStateOf("") }
    var chalLoading by remember { mutableStateOf(false) }
    val chatListState = rememberLazyListState()

    // Trigger page summary once on open (re-triggered upon completion of auth/login)
    LaunchedEffect(pageText, tabId, url, summaryTriggerKey) {
        // 1. Check if cachedSession or AISummaryCache already has the completed result
        val sessionItem = AISessionManager.getSession(tabId)
        if (sessionItem != null && sessionItem.websiteUrl == url && sessionItem.parsedSummary != null && summaryTriggerKey == 0) {
            analysisResult = sessionItem.parsedSummary
            summaryLoading = false
            return@LaunchedEffect
        }
        
        val cachedItemFirst = AISummaryCache.get(url)
        if (cachedItemFirst?.analysisResult != null) {
            analysisResult = cachedItemFirst.analysisResult
            summaryLoading = false
            return@LaunchedEffect
        }

        if (url.isBlank() || url.startsWith("orion://") || url.startsWith("about:") || url.startsWith("file://")) {
            summaryLoading = false
            analysisResult = AIAnalysisResult(
                mainTopic = "System Page",
                summary = "The AI Assistant is available on standard web pages. Navigate to a website to see summaries."
            )
            return@LaunchedEffect
        }

        // 2. If background analysis is active, poll AISummaryCache or AISessionManager for up to 5 seconds
        summaryLoading = true
        var pollAttempts = 0
        while (pollAttempts < 10) { // 10 * 500ms = 5 seconds
            val polledItem = AISummaryCache.get(url)
            val polledSession = AISessionManager.getSession(tabId)
            
            if (polledSession != null && polledSession.websiteUrl == url && polledSession.parsedSummary != null) {
                analysisResult = polledSession.parsedSummary
                summaryLoading = false
                return@LaunchedEffect
            }
            if (polledItem?.analysisResult != null) {
                analysisResult = polledItem.analysisResult
                summaryLoading = false
                return@LaunchedEffect
            }
            
            // Wait 500ms before next poll
            kotlinx.coroutines.delay(500)
            pollAttempts++
        }

        // 3. Fallback: Display a pending state without triggering any fresh extraction or scanning
        summaryLoading = false
        analysisResult = AIAnalysisResult(
            mainTopic = cachedItemFirst?.title ?: "Analysis Pending",
            summary = "The page background analysis is taking a moment to complete. Please try again shortly.",
            keyPoints = listOf("Background pre-loading active", "Analysis in progress", "Please try again shortly")
        )
    }

    fun postChatMessage(message: String) {
        if (message.isBlank()) return
        chatHistory.add("user" to message)
        // Store user post in cached session storage
        AISessionManager.updateChatHistory(tabId, chatHistory.toList())
        chatInputText = ""
        chalLoading = true
        
        scope.launch {
            // Scroll to end of list
            if (chatHistory.isNotEmpty()) {
                chatListState.animateScrollToItem(chatHistory.size - 1)
            }
            
            val response = AISummaryEngine.chatSession(chatHistory.toList(), message, settingsManager, context)
            chatHistory.add("assistant" to response)
            // Store assistant response in cached session storage
            AISessionManager.updateChatHistory(tabId, chatHistory.toList())
            chalLoading = false
            
            // Scroll to end of list
            if (chatHistory.isNotEmpty()) {
                chatListState.animateScrollToItem(chatHistory.size - 1)
            }
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
            if (isBridgeProvider) {
                Surface(
                    color = if (isLoginNeeded) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isLoginNeeded) Icons.Default.VpnKey else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isLoginNeeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isLoginNeeded) "$currentProvider Login Required" else "$currentProvider Session Connected",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isLoginNeeded) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        
                        if (isLoginNeeded) {
                            Button(
                                onClick = { 
                                    showProviderWebLoginDialog = true
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(28.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Log In", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            TextButton(
                                onClick = {
                                    showProviderWebLoginDialog = true
                                },
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Manage Session", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (showProviderWebLoginDialog) {
                Dialog(
                    onDismissRequest = { 
                        showProviderWebLoginDialog = false
                        bridgeSystem.checkLoginStateAndUrl(currentProvider, bridgeSystem.getOrCreateWebView(currentProvider).url)
                        summaryTriggerKey++
                    }
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.85f),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("$currentProvider Web Session", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                IconButton(onClick = { 
                                    showProviderWebLoginDialog = false 
                                    bridgeSystem.checkLoginStateAndUrl(currentProvider, bridgeSystem.getOrCreateWebView(currentProvider).url)
                                    summaryTriggerKey++
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close")
                                }
                            }
                            
                            Box(modifier = Modifier.weight(1f)) {
                                AndroidView(
                                    factory = { 
                                        bridgeSystem.getOrCreateWebView(currentProvider)
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Button(
                                onClick = { 
                                    showProviderWebLoginDialog = false 
                                    bridgeSystem.checkLoginStateAndUrl(currentProvider, bridgeSystem.getOrCreateWebView(currentProvider).url)
                                    summaryTriggerKey++
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Finish & Return", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
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
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "Summary" to Icons.Default.Analytics,
                    "Key Points" to Icons.Default.Star,
                    "Ask Question" to Icons.Default.Chat,
                    "Fact Check" to Icons.Default.CheckCircle,
                    "Explain" to Icons.Default.Lightbulb
                ).forEach { (name, icon) ->
                    val selected = when (name) {
                        "Summary" -> activeTab == "summary"
                        "Key Points" -> activeTab == "key_points"
                        "Ask Question" -> activeTab == "chat"
                        "Fact Check" -> activeTab == "fact_check"
                        "Explain" -> activeTab == "explain"
                        else -> false
                    }
                    FilterChip(
                        selected = selected,
                        onClick = {
                            activeTab = when (name) {
                                "Summary" -> "summary"
                                "Key Points" -> "key_points"
                                "Ask Question" -> "chat"
                                "Fact Check" -> "fact_check"
                                "Explain" -> "explain"
                                else -> "summary"
                            }
                        },
                        label = { Text(name, fontSize = 12.sp) },
                        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(top = 4.dp))

            // Tab Contents
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (activeTab != "chat") {
                    if (summaryLoading) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Extracting webpage context & initiating synthesis...",
                                fontSize = 13.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else if (analysisResult != null) {
                        val result = analysisResult!!
                        if (activeTab == "summary") {
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

                            // Reading Time Chip Card
                            if (result.readingTime > 0) {
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier.wrapContentSize()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AccessTime,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "Estimated Reading Time: ${result.readingTime} min",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }

                            // Short Summary Card
                            if (result.shortSummary.isNotEmpty()) {
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.FlashOn,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.secondary
                                                )
                                                Text(
                                                    text = "QUICK TAKE-AWAY",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = result.shortSummary,
                                                fontSize = 13.sp,
                                                lineHeight = 18.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }

                            // Summary Card (Detailed Summary)
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
                                                Text("DETAILED SUMMARY", fontWeight = FontWeight.Bold, fontSize = 11.sp)
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

                            // Highlights and Takeaways Card
                            if (result.highlights.isNotEmpty()) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Lightbulb,
                                                    contentDescription = null,
                                                    tint = Color(0xFFEAB308)
                                                )
                                                Text(
                                                    text = "KEY HIGHLIGHTS & INSIGHTS",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            result.highlights.forEach { h ->
                                                Row(
                                                    modifier = Modifier.padding(vertical = 4.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text("✦", color = Color(0xFFEAB308), fontWeight = FontWeight.Bold)
                                                    Text(text = h, fontSize = 13.sp, lineHeight = 18.sp)
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

                            // Important Page Links Card
                            if (result.links.isNotEmpty()) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Link,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "IMPORTANT PAGE LINKS",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            result.links.forEach { (labelText, urlString) ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp)
                                                        .clickable {
                                                            if (urlString.startsWith("http", ignoreCase = true)) {
                                                                try {
                                                                    val intent = android.content.Intent(
                                                                        android.content.Intent.ACTION_VIEW,
                                                                        android.net.Uri.parse(urlString)
                                                                    )
                                                                    context.startActivity(intent)
                                                                } catch (e: Exception) {
                                                                    android.widget.Toast.makeText(context, "Navigating to: $urlString", android.widget.Toast.LENGTH_SHORT).show()
                                                                }
                                                            }
                                                        },
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ArrowOutward,
                                                        contentDescription = null,
                                                        tint = if (urlString.isNotEmpty()) MaterialTheme.colorScheme.primary else Color.Gray,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Column {
                                                        Text(
                                                            text = labelText,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = if (urlString.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                        )
                                                        if (urlString.isNotEmpty()) {
                                                            Text(
                                                                text = urlString,
                                                                fontSize = 11.sp,
                                                                color = Color.Gray,
                                                                maxLines = 1
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (activeTab == "key_points") {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Reading Time Chip Card
                            if (result.readingTime > 0) {
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier.wrapContentSize()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AccessTime,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "Estimated Reading Time: ${result.readingTime} min",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
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

                            // Highlights and Takeaways Card
                            if (result.highlights.isNotEmpty()) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Lightbulb,
                                                    contentDescription = null,
                                                    tint = Color(0xFFEAB308)
                                                )
                                                Text(
                                                    text = "KEY HIGHLIGHTS & INSIGHTS",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            result.highlights.forEach { h ->
                                                Row(
                                                    modifier = Modifier.padding(vertical = 4.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text("✦", color = Color(0xFFEAB308), fontWeight = FontWeight.Bold)
                                                    Text(text = h, fontSize = 13.sp, lineHeight = 18.sp)
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
                        }
                    } else if (activeTab == "fact_check") {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Text("FACT VERIFIER", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Verify core numbers, dates, claims and statistical statements present on this webpage instantly.", fontSize = 13.sp, color = Color.Gray)
                                }
                            }

                            if (result.factsAndStats.isNotEmpty() || result.dates.isNotEmpty()) {
                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (result.factsAndStats.isNotEmpty()) {
                                        item {
                                            Text("Verified Statistics & Facts", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp))
                                        }
                                        items(result.factsAndStats) { stat ->
                                            Card(modifier = Modifier.fillMaxWidth()) {
                                                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text("📊")
                                                    Text(stat, fontSize = 13.sp, lineHeight = 18.sp)
                                                }
                                            }
                                        }
                                    }

                                    if (result.dates.isNotEmpty()) {
                                        item {
                                            Text("Time Anchor Dates", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp))
                                        }
                                        items(result.dates) { date ->
                                            Card(modifier = Modifier.fillMaxWidth()) {
                                                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text("📅")
                                                    Text(date, fontSize = 13.sp, lineHeight = 18.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No baseline statistics extracted automatically. Use the deep fact check button below to analyze claims.", fontSize = 13.sp, color = Color.Gray, textAlign = TextAlign.Center)
                                }
                            }

                            Button(
                                onClick = {
                                    activeTab = "chat"
                                    postChatMessage("Please execute a detailed fact-check of the main statements, figures, and claims made on this webpage and list anything that requires validation or source citation.")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Insights, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Perform AI Deep Fact-Check", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else if (activeTab == "explain") {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Text("EXPLAIN SIMPLIFIER", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Distill the core concept, technology, news, or subject matter of this webpage into extremely straightforward, layman-friendly concepts.", fontSize = 13.sp)
                                }
                            }

                            Card(
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            ) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize().padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    item {
                                        Text("Layman Distillation Mode", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.height(6.dp))
                                    }
                                    item {
                                        Text(
                                            text = "This mode strips away industry-heavy vocabulary, buzzwords, or complicated terms. It explains the core subject matter of this webpage with simple metaphors and straightforward language.",
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp,
                                            color = Color.DarkGray
                                        )
                                    }
                                    item {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Button(
                                            onClick = {
                                                activeTab = "chat"
                                                postChatMessage("Explain this webpage's core topic in extremely simple terms to me, using direct analogies and avoiding any complex jargon.")
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                        ) {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Explain Simple (ELI5)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                                                        color = if (role == "user") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.secondary
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

                        // Ten custom browser options for webpage actions
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val actionPrompts = listOf(
                                "Summarize Page" to "Perform a clear, readable summary of this whole webpage.",
                                "Explain Page" to "Explain this page in extremely clear terms to me as if explaining to a five-year-old child.",
                                "Key Points" to "Extract the top 5 key takeaways/points from this webpage.",
                                "Fact Check" to "Execute a thorough fact check of the core statistics and statements on this page.",
                                "Important Facts" to "Identify all critical facts and important statistics on this webpage.",
                                "Important People" to "List and describe all the important people and major entities mentioned on this website.",
                                "Important Dates" to "List any important timeline dates mentioned on this webpage.",
                                "Translate Summary" to "Translate the webpage summary into the active model's default output configuration.",
                                "Create Notes" to "Draft a set of structured study notes or quick bullet points based on this page.",
                                "Ask Questions" to "Generate a couple of great context-specific questions that I can follow up with on this webpage."
                            )

                            actionPrompts.forEach { (label, prompt) ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.clickable {
                                        activeTab = "chat"
                                        postChatMessage(prompt)
                                    }
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
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
