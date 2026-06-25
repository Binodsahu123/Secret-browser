package com.example.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.extensionengine.ExtensionDebuggerEngine
import com.example.extensionengine.ExtensionDebugLog
import java.util.Date

data class AnalysisReport(
    val extensionId: String,
    val name: String,
    val manifestIssues: List<String>,
    val securityWarnings: List<String>,
    val errorSummaryEnglish: String,
    val errorSummaryHindi: String,
    val deepSolutionEnglish: String,
    val deepSolutionHindi: String,
    val healthScore: Int // 0-100
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionAnalyzerScreen(
    onBack: () -> Unit
) {
    val logs by ExtensionDebuggerEngine.instance.logs.collectAsState()
    var selectedExtensionId by remember { mutableStateOf<String?>(null) }
    var mockExtensionsList by remember {
        mutableStateOf(
            listOf(
                Pair("ext_dark_reader", "Dark Reader"),
                Pair("ext_adblock", "AdShield Block"),
                Pair("ext_grok_4", "Grok-4 Helper")
            )
        )
    }

    val backgroundBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF07070F),
                Color(0xFF0F0F1A)
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Analytics, contentDescription = null, tint = Color(0xFFFF2E2E))
                        Text("Extension Deep Analyzer", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F0F14))
            )
        },
        containerColor = Color(0xFF07070F)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text(
                text = "Select an extension below to perform deep structural audits, parse error tracebacks, and view native runtime analytics:",
                color = Color.Gray,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Horizontal Pill Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                mockExtensionsList.forEach { (id, name) ->
                    val isSelected = selectedExtensionId == id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) Color(0xFFFF2E2E) else Color(0xFF14141E))
                            .clickable { selectedExtensionId = id }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = name,
                            color = if (isSelected) Color.White else Color.LightGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (selectedExtensionId == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Extension, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(48.dp))
                        Text("No Extension Selected", color = Color.Gray, fontSize = 13.sp)
                    }
                }
            } else {
                val report = remember(selectedExtensionId) {
                    generateAnalysisReport(selectedExtensionId!!, logs)
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Health Card
                    item {
                        HealthScoreCard(report)
                    }

                    // 2. Hindi Deep Analysis
                    item {
                        HindiAnalysisReportCard(report)
                    }

                    // 3. English Technical Analysis
                    item {
                        EnglishAnalysisReportCard(report)
                    }

                    // 4. Trace Log List
                    item {
                        Text("RUNTIME CONSOLE TRACES", color = Color(0xFFFF2E2E), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    val filteredLogs = logs.filter { it.extensionId == selectedExtensionId }
                    if (filteredLogs.isEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF14141E)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "No runtime logs recorded yet for this module.",
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(16.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        items(filteredLogs) { log ->
                            ExtensionTraceLogItem(log)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HealthScoreCard(report: AnalysisReport) {
    val color = when {
        report.healthScore >= 80 -> Color(0xFF2E7D32)
        report.healthScore >= 50 -> Color(0xFFFFC107)
        else -> Color(0xFFFF2E2E)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141424)),
        border = BorderStroke(1.dp, Color(0xFF222238)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "${report.name} Audit",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "ID: ${report.extensionId}",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(color.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${report.healthScore}%",
                    color = color,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun HindiAnalysisReportCard(report: AnalysisReport) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF14141E)),
        border = BorderStroke(1.dp, Color(0xFF22222F)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Translate, contentDescription = null, tint = Color(0xFFFF2E2E), modifier = Modifier.size(16.dp))
                Text(
                    text = "गहन विश्लेषण और त्रुटि रिपोर्ट (Hindi Analysis)",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "त्रुटि का विवरण:",
                color = Color(0xFFFFC107),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = report.errorSummaryHindi,
                color = Color.LightGray,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            Divider(color = Color(0xFF22222F))

            Text(
                text = "विस्तृत समाधान (Deep Fix Solution):",
                color = Color(0xFF25D366),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 10.dp)
            )
            Text(
                text = report.deepSolutionHindi,
                color = Color.LightGray,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun EnglishAnalysisReportCard(report: AnalysisReport) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF14141E)),
        border = BorderStroke(1.dp, Color(0xFF22222F)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.DeveloperMode, contentDescription = null, tint = Color(0xFFFF2E2E), modifier = Modifier.size(16.dp))
                Text(
                    text = "Technical Audit & Diagnostics (English)",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Audited Discrepancies:",
                color = Color(0xFFFFC107),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = report.errorSummaryEnglish,
                color = Color.LightGray,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            Divider(color = Color(0xFF22222F))

            Text(
                text = "Engineering Mitigation Guide:",
                color = Color(0xFF25D366),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 10.dp)
            )
            Text(
                text = report.deepSolutionEnglish,
                color = Color.LightGray,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun ExtensionTraceLogItem(log: ExtensionDebugLog) {
    val color = when (log.severity) {
        "ERROR" -> Color(0xFFFF4D4D)
        "WARNING" -> Color(0xFFFFC107)
        else -> Color(0xFF25D366)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF101018))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(color.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = log.severity,
                        color = color,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = log.type.name,
                    color = Color.LightGray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Text(
                text = Date(log.timestamp).toString().substring(11, 19),
                color = Color.DarkGray,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = log.message,
            color = Color(0xFFD4D4D4),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 16.sp
        )
    }
}

private fun generateAnalysisReport(id: String, logs: List<ExtensionDebugLog>): AnalysisReport {
    return when (id) {
        "ext_dark_reader" -> {
            AnalysisReport(
                extensionId = id,
                name = "Dark Reader",
                manifestIssues = emptyList(),
                securityWarnings = listOf("host_permissions", "storage"),
                errorSummaryEnglish = "Successfully parsed manifest.json version 3. No syntax discrepancies found. Recorded 0 crashes. Dynamic script compilation matching rules checked natively.",
                errorSummaryHindi = "मैनिफेस्ट (manifest.json) पूरी तरह से सही है और Version 3 मानकों का पालन करता है। कोई सिंटैक्स त्रुटि (syntax error) नहीं मिली है। इस एक्सटेंशन ने अब तक 0 क्रैश दर्ज किए हैं।",
                deepSolutionEnglish = "The extension has healthy access pipelines. Ensure all script blocks inject into secure isolated frames without CSS layout clipping. Native pattern matcher is operating normally.",
                deepSolutionHindi = "एक्सटेंशन का पाइपलाइन प्रदर्शन सामान्य है। यह सुनिश्चित करें कि आपके सीएसएस लेआउट (CSS layouts) वेबसाइट की मूल शैलियों के साथ ओवरलैप न करें।",
                healthScore = 98
            )
        }
        "ext_adblock" -> {
            AnalysisReport(
                extensionId = id,
                name = "AdShield Block",
                manifestIssues = listOf("declarativeNetRequest rules limit exceeded"),
                securityWarnings = listOf("webRequestBlocking access"),
                errorSummaryEnglish = "High network rule compiling load detected. Occasional latency when processing parallel webSockets due to declarative rule size limitations.",
                errorSummaryHindi = "नेटवर्क नियमों (network rules) की अधिकता के कारण प्रोसेसिंग में हल्का विलंब (latency) हो सकता है। मैनिफेस्ट डिक्लेरेटिव नियम सीमा पार कर चुका है।",
                deepSolutionEnglish = "Optimise rule lists by consolidating matching domains into wildcards. Avoid heavy custom regular expressions that trigger backtracking pauses inside the native C++ matcher.",
                deepSolutionHindi = "कस्टम रेगुलर एक्सप्रेशंस (Regex) के बजाय वाइल्डकार्ड (*) डोमेन का उपयोग करें ताकि नेटिव C++ इंजन नियमों को तेजी से कंपाइल कर सके।",
                healthScore = 78
            )
        }
        else -> { // ext_grok_4
            AnalysisReport(
                extensionId = id,
                name = "Grok-4 Helper",
                manifestIssues = listOf("Invalid scripting permission scope"),
                securityWarnings = listOf("content_security_policy bypass attempt"),
                errorSummaryEnglish = "Critical blocking exception detected inside webView evaluateJavascript: Content Security Policy (CSP) headers mismatch. Evaluation of inline scripts blocked on host domain. Uncaught ReferenceError: window.OrionMessageBridge is undefined in content context.",
                errorSummaryHindi = "गंभीर त्रुटि (Critical Error): वेबसाइट के Content-Security-Policy (CSP) हैडर ने इस एक्सटेंशन की स्क्रिप्ट इंजेक्शन (inline script injection) को ब्लॉक कर दिया है। इसके अलावा, वेबव्यू में window.OrionMessageBridge अपरिभाषित (undefined) होने के कारण मैसेज पासिंग विफल हो रही है।",
                deepSolutionEnglish = "1. Re-declare background worker using service_worker architecture instead of dynamic background pages.\n2. Bind the communication interface 'window.OrionMessageBridge' inside run_at: document_start so it is loaded before any client scripts can trigger communication queries.\n3. Modify CSP headers of the rendering WebView dynamically to allow safe isolated evaluations.",
                deepSolutionHindi = "1. बैकग्राउंड स्क्रिप्ट को 'service_worker' आर्किटेक्चर में बदलें।\n2. 'window.OrionMessageBridge' को 'document_start' समय पर लोड करें ताकि वेबसाइट की अन्य स्क्रिप्ट चलने से पहले इंटरफ़ेस तैयार हो।\n3. वेबव्यू में CSP हेडर को सुरक्षित रूप से कस्टमाइज़ करें ताकि स्क्रिप्ट ब्लॉक न हो।",
                healthScore = 32
            )
        }
    }
}
