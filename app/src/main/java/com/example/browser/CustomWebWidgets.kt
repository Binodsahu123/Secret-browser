package com.example.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BasicTextFieldWithoutLabel(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    placeholder: String,
    textStyle: TextStyle,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = textStyle,
        cursorBrush = SolidColor(textStyle.color),
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = { onDone() }
        ),
        modifier = modifier,
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = textStyle.copy(color = textStyle.color.copy(alpha = 0.5f))
                    )
                }
                innerTextField()
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebContextMenuBottomSheet(
    state: ContextMenuState, // Exact ContextMenuState class defined in view model
    onDismiss: () -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onOpenInNewTabGroup: (String) -> Unit,
    onOpenInIncognito: (String) -> Unit,
    onDownloadLink: (String) -> Unit,
    onAddToReadingList: (String, String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            ListItem(
                headlineContent = { Text("Open in New Tab", fontWeight = FontWeight.SemiBold) },
                leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                modifier = Modifier.clickable {
                    onOpenInNewTab(state.url)
                    onDismiss()
                }
            )
            ListItem(
                headlineContent = { Text("Open in New Tab Group", fontWeight = FontWeight.SemiBold) },
                leadingContent = { Icon(Icons.Default.Layers, contentDescription = null) },
                modifier = Modifier.clickable {
                    onOpenInNewTabGroup(state.url)
                    onDismiss()
                }
            )
            ListItem(
                headlineContent = { Text("Open in Incognito Mode", fontWeight = FontWeight.SemiBold) },
                leadingContent = { Icon(Icons.Default.VisibilityOff, contentDescription = null) },
                modifier = Modifier.clickable {
                    onOpenInIncognito(state.url)
                    onDismiss()
                }
            )
            ListItem(
                headlineContent = { Text("Download File Link", fontWeight = FontWeight.SemiBold) },
                leadingContent = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                modifier = Modifier.clickable {
                    onDownloadLink(state.url)
                    onDismiss()
                }
            )
            ListItem(
                headlineContent = { Text("Add Link to Reading List", fontWeight = FontWeight.SemiBold) },
                leadingContent = { Icon(Icons.Default.BookmarkBorder, contentDescription = null) },
                modifier = Modifier.clickable {
                    onAddToReadingList(state.url, state.url)
                    onDismiss()
                }
            )
        }
    }
}

@Composable
fun AboutAppDialog(show: Boolean, onDismiss: () -> Unit) {
    if (show) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("About SwiftBrowser", fontWeight = FontWeight.Bold) },
            text = { Text("SwiftBrowser v1.0.0\nAn advanced modular web browser featuring an integrated high-efficiency adblocker, multi-audio-downloader media engines, and server-side chat summary capabilities powered by Gemini.") },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
        )
    }
}

fun Modifier.frostedGlassBackground(wallpaper: String): Modifier {
    val colors = when (wallpaper) {
        "Frosted Glass" -> listOf(Color(0xFF2B5876), Color(0xFF4E4376))
        "Midnight" -> listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))
        "Aurora" -> listOf(Color(0xFF0575E6), Color(0xFF00F260))
        "Sunset" -> listOf(Color(0xFFF12711), Color(0xFFF5AF19))
        else -> listOf(Color(0xFF333333), Color(0xFF1A1A1A))
    }
    return this.background(Brush.verticalGradient(colors))
}
