package com.example.browser

import android.text.Html
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderModeScreen(
    state: BrowserUiState,
    onClose: () -> Unit,
    onUpdateFontSize: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Reading styles (Light, Sepia, Dark)
    var readerColorTheme by remember { mutableStateOf("Sepia") }
    // Reading font (Serif, Sans-Serif)
    var isSerif by remember { mutableStateOf(true) }

    val (bgColor, textColor) = when (readerColorTheme) {
        "Light" -> Pair(Color(0xFFFFFFFF), Color(0xFF121212))
        "Sepia" -> Pair(Color(0xFFFDF6E3), Color(0xFF586E75))
        else -> Pair(Color(0xFF121212), Color(0xFFE0E0E0)) // Dark
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Reader Mode",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Exit Reader Mode",
                            tint = textColor
                        )
                    }
                },
                actions = {
                    // Serif/Sans switcher
                    TextButton(
                        onClick = { isSerif = !isSerif }
                    ) {
                        Text(
                            text = if (isSerif) "Sans" else "Serif",
                            color = textColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    // Font Size adjustments
                    IconButton(
                        onClick = {
                            val newSize = if (state.readerFontSize >= 24) 14 else state.readerFontSize + 2
                            onUpdateFontSize(newSize)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatSize,
                            contentDescription = "Adjust Font Size",
                            tint = textColor
                        )
                    }

                    // Color theme picker
                    IconButton(
                        onClick = {
                            readerColorTheme = when (readerColorTheme) {
                                "Light" -> "Sepia"
                                "Sepia" -> "Dark"
                                else -> "Light"
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Change Theme",
                            tint = textColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Article Header Text
            Text(
                text = state.readerModeTitle,
                fontSize = (state.readerFontSize + 6).sp,
                lineHeight = (state.readerFontSize + 12).sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                fontFamily = if (isSerif) FontFamily.Serif else FontFamily.Default,
                modifier = Modifier.fillMaxWidth()
            )

            if (state.readerModeAuthor != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "By ${state.readerModeAuthor}",
                    fontSize = state.readerFontSize.sp,
                    color = textColor.copy(alpha = 0.7f),
                    fontFamily = if (isSerif) FontFamily.Serif else FontFamily.Default,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Divider(color = textColor.copy(alpha = 0.15f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(24.dp))

            // Renders rich styled text using Android TextView Interop
            AndroidView(
                factory = { context ->
                    TextView(context).apply {
                        setTextColor(android.graphics.Color.argb(
                            (textColor.alpha * 255).toInt(),
                            (textColor.red * 255).toInt(),
                            (textColor.green * 255).toInt(),
                            (textColor.blue * 255).toInt()
                        ))
                    }
                },
                update = { textView ->
                    textView.textSize = state.readerFontSize.toFloat()
                    textView.setLineSpacing(0f, 1.4f)
                    if (isSerif) {
                        textView.typeface = android.graphics.Typeface.SERIF
                    } else {
                        textView.typeface = android.graphics.Typeface.DEFAULT
                    }
                    textView.setTextColor(android.graphics.Color.argb(
                        (textColor.alpha * 255).toInt(),
                        (textColor.red * 255).toInt(),
                        (textColor.green * 255).toInt(),
                        (textColor.blue * 255).toInt()
                    ))
                    
                    // Decode content HTML
                    val decoded = state.readerModeContent
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        textView.text = Html.fromHtml(decoded, Html.FROM_HTML_MODE_COMPACT)
                    } else {
                        @Suppress("DEPRECATION")
                        textView.text = Html.fromHtml(decoded)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}
