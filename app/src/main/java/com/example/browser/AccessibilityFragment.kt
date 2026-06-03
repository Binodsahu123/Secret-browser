package com.example.browser

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.example.data.PreferenceManager

@Composable
fun AccessibilityFragment(
    prefs: PreferenceManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var textScaling by remember {
        mutableStateOf(prefs.getInt("text_scaling", 100))
    }
    var forceZoom by remember {
        mutableStateOf(prefs.getBoolean("force_enable_zoom", false))
    }
    var simplifiedView by remember {
        mutableStateOf(prefs.getBoolean("simplified_view_webpages", false))
    }
    var highContrastText by remember {
        mutableStateOf(prefs.getBoolean("high_contrast_text", false))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("← Back", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Accessibility",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Text scaling seek representation
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Text scaling", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Current scale: $textScaling%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    val sliderValues = listOf(85, 100, 115, 130, 150, 180, 200)
                    var sliderPos by remember {
                        mutableStateOf(sliderValues.indexOf(textScaling).coerceAtLeast(1).toFloat())
                    }
                    Slider(
                        value = sliderPos,
                        onValueChange = { pos ->
                            sliderPos = pos
                            val valSelected = sliderValues[pos.toInt().coerceIn(0, 6)]
                            textScaling = valSelected
                            prefs.setInt("text_scaling", valSelected)
                        },
                        valueRange = 0f..6f,
                        steps = 5
                    )
                }
            }

            // 2. Force enable zoom
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Force enable zoom", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Override website request to prevent zooming in", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = forceZoom,
                    onCheckedChange = {
                        forceZoom = it
                        prefs.setBoolean("force_enable_zoom", it)
                    }
                )
            }

            // 3. Simplified view for webpages
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Simplified view for webpages", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Clean up pages into single reader column instantly", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = simplifiedView,
                    onCheckedChange = {
                        simplifiedView = it
                        prefs.setBoolean("simplified_view_webpages", it)
                    }
                )
            }

            // 4. High contrast text
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("High contrast text", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Enhances color and text background ratios", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = highContrastText,
                    onCheckedChange = {
                        highContrastText = it
                        prefs.setBoolean("high_contrast_text", it)
                    }
                )
            }

            // 5. Live Caption link
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth().clickable {
                    try {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("System Live Captions", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Manage hearing or audio layout accessibility options", fontSize = 12.sp, color = Color.Gray)
                        }
                        Text("Open", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
