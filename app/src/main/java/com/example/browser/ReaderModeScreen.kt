package com.example.browser

import android.text.Html
import android.widget.TextView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderModeScreen(
    state: BrowserUiState,
    onClose: () -> Unit,
    onUpdateFontSize: (Int) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reader Mode") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close Reader")
                    }
                },
                actions = {
                    IconButton(onClick = { onUpdateFontSize((state.readerFontSize - 2).coerceAtLeast(12)) }) {
                        Icon(Icons.Default.TextDecrease, contentDescription = "Decrease Font Size")
                    }
                    Text("${state.readerFontSize}sp", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 4.dp))
                    IconButton(onClick = { onUpdateFontSize((state.readerFontSize + 2).coerceAtMost(32)) }) {
                        Icon(Icons.Default.TextIncrease, contentDescription = "Increase Font Size")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = state.readerModeTitle,
                fontSize = (state.readerFontSize + 6).sp,
                lineHeight = (state.readerFontSize + 12).sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            val infoLines = mutableListOf<String>()
            state.readerModeDomain?.let { infoLines.add(it) }
            state.readerModeAuthor?.let { infoLines.add("By $it") }
            state.readerModeDate?.let { infoLines.add(it) }
            
            if (infoLines.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = infoLines.joinToString(" • "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            
            val htmlContent = state.readerModeContent
            val textColor = MaterialTheme.colorScheme.onSurface
            
            AndroidView(
                factory = { context ->
                    TextView(context).apply {
                        textSize = state.readerFontSize.toFloat()
                    }
                },
                update = { textView ->
                    textView.textSize = state.readerFontSize.toFloat()
                    // Apply dynamic colors
                    val colorHex = String.format("#%06X", 0xFFFFFF and textColor.hashCode())
                    textView.setTextColor(android.graphics.Color.parseColor(colorHex))
                    textView.text = Html.fromHtml(htmlContent, Html.FROM_HTML_MODE_COMPACT)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
