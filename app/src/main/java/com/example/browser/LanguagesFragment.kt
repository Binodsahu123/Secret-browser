package com.example.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PreferenceManager
import org.json.JSONArray

val AVAILABLE_LANGUAGES = listOf(
    "English", "Hindi", "Tamil", "Telugu", "Bengali",
    "Marathi", "Gujarati", "Kannada", "Malayalam",
    "Punjabi", "Odia", "Urdu", "Spanish", "French",
    "German", "Japanese", "Chinese", "Arabic", "Russian"
)

@Composable
fun LanguagesFragment(
    prefs: PreferenceManager,
    onBack: () -> Unit
) {
    var offerTranslate by remember {
        mutableStateOf(prefs.getBoolean("offer_to_translate", true))
    }
    var spellCheck by remember {
        mutableStateOf(prefs.getBoolean("spell_check", true))
    }

    // Load active preferred languages JSON array
    val savedLanguagesJson = remember {
        prefs.getString("preferred_languages_list", "[\"English\"]")
    }

    var addedLanguages by remember {
        mutableStateOf(
            try {
                val array = JSONArray(savedLanguagesJson)
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    list.add(array.getString(i))
                }
                if (list.isEmpty()) list.add("English")
                list
            } catch (e: Exception) {
                mutableListOf("English")
            }
        )
    }

    var showPickerDial by remember { mutableStateOf(false) }

    fun saveLanguages(list: List<String>) {
        val array = JSONArray()
        list.forEach { array.put(it) }
        prefs.setString("preferred_languages_list", array.toString())
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
                text = "Languages",
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
            Text("Preferred languages", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("Websites will load in the first preferred language in the list.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // Added languages list
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                addedLanguages.forEachIndexed { index, lang ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}. $lang",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                // Up button (Move up)
                                if (index > 0) {
                                    IconButton(
                                        onClick = {
                                            val newList = addedLanguages.toMutableList()
                                            val tmp = newList[index]
                                            newList[index] = newList[index - 1]
                                            newList[index - 1] = tmp
                                            addedLanguages = newList
                                            saveLanguages(newList)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(16.dp))
                                    }
                                }

                                // Down button (Move down)
                                if (index < addedLanguages.size - 1) {
                                    IconButton(
                                        onClick = {
                                            val newList = addedLanguages.toMutableList()
                                            val tmp = newList[index]
                                            newList[index] = newList[index + 1]
                                            newList[index + 1] = tmp
                                            addedLanguages = newList
                                            saveLanguages(newList)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(16.dp))
                                    }
                                }

                                // Delete button
                                if (addedLanguages.size > 1) {
                                    IconButton(
                                        onClick = {
                                            val newList = addedLanguages.filter { it != lang }.toMutableList()
                                            addedLanguages = newList
                                            saveLanguages(newList)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete language", tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { showPickerDial = true },
                modifier = Modifier.fillMaxWidth().height(40.dp)
            ) {
                Text("+ Add language", fontSize = 13.sp)
            }

            HorizontalDivider()

            Text("Translation settings", fontWeight = FontWeight.Bold, fontSize = 14.sp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Offer to translate pages", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    Text("Offers a quick translation bar on non-preferred language pages", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = offerTranslate,
                    onCheckedChange = {
                        offerTranslate = it
                        prefs.setBoolean("offer_to_translate", it)
                    }
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Spell check", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    Text("Highlights typos inside form entries", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = spellCheck,
                    onCheckedChange = {
                        spellCheck = it
                        prefs.setBoolean("spell_check", it)
                    }
                )
            }
        }
    }

    if (showPickerDial) {
        AlertDialog(
            onDismissRequest = { showPickerDial = false },
            title = { Text("Add language") },
            text = {
                Box(modifier = Modifier.sizeIn(maxHeight = 320.dp)) {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val remaining = AVAILABLE_LANGUAGES.filter { !addedLanguages.contains(it) }
                        if (remaining.isEmpty()) {
                            Text("All available languages have been added.", fontSize = 13.sp, color = Color.Gray)
                        } else {
                            remaining.forEach { language ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val newList = (addedLanguages + language).toMutableList()
                                            addedLanguages = newList
                                            saveLanguages(newList)
                                            showPickerDial = false
                                        }
                                ) {
                                    Text(
                                        text = language,
                                        modifier = Modifier.padding(12.dp),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPickerDial = false }) {
                    Text("Close")
                }
            }
        )
    }
}
