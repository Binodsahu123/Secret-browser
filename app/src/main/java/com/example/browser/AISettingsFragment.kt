package com.example.browser

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AISettingsFragment(
    viewModel: BrowserViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val isOrionAssistantEnabled = uiState.orionVoiceWakeWordEnabled
    val settingsManager = remember { AISettingsManager(context) }
    val accountManager = remember { AIAccountManager(context) }
    val loginManager = remember { AILoginManager(context) }
    val scope = rememberCoroutineScope()

    var selectedProvider by remember { mutableStateOf(settingsManager.defaultProvider) }
    var selectedModel by remember { mutableStateOf(settingsManager.defaultModel) }
    var preferredLanguage by remember { mutableStateOf(settingsManager.preferredLanguage) }
    var responseLength by remember { mutableStateOf(settingsManager.responseLength) }
    var responseStyle by remember { mutableStateOf(settingsManager.responseStyle) }
    
    var guestModeEnabled by remember { mutableStateOf(settingsManager.guestModeEnabled) }
    var autoLoginEnabled by remember { mutableStateOf(settingsManager.autoLoginEnabled) }

    var apiKeyText by remember(selectedProvider) { mutableStateOf(settingsManager.getApiKey(selectedProvider)) }
    var apiKeyVisible by remember { mutableStateOf(false) }

    // Account Status state for current provider
    var isLoggedIn by remember(selectedProvider) { mutableStateOf(accountManager.isLoggedIn(selectedProvider)) }
    var activeAccount by remember(selectedProvider) { mutableStateOf(accountManager.getAccount(selectedProvider)) }

    // Dropdown state controls
    var showProviderMenu by remember { mutableStateOf(false) }
    var showModelMenu by remember { mutableStateOf(false) }
    var showLanguageMenu by remember { mutableStateOf(false) }
    var showLengthMenu by remember { mutableStateOf(false) }
    var showStyleMenu by remember { mutableStateOf(false) }

    // Login Dialog states
    var showLoginDialog by remember { mutableStateOf(false) }
    var loginEmail by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var loginLoading by remember { mutableStateOf(false) }

    val modelsList = remember(selectedProvider) {
        listOf("Default") + AIProviderManager.getModelsForProvider(selectedProvider)
    }

    val languageChoices = listOf(
        "Same as Page", "English", "Hindi", "Tamil", "Telugu", "Bengali",
        "Spanish", "French", "German", "Japanese", "Chinese", "Arabic"
    )

    val lengthChoices = listOf("Short", "Medium", "Long")
    val styleChoices = listOf("Balanced", "Creative", "Precise")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Top Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "AI Specialist Settings",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Intro Description Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Next-Gen AI Browser Integration",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Access ChatGPT, Gemini, Claude, and on-device models to summarize pages, ask specialist questions, translate, and cross-reference research.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                ),
                modifier = Modifier.fillMaxWidth(),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Orion Assistant",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Say \"Hello Orion\" to open the assistant offline. Status: ${if (isOrionAssistantEnabled) "ACTIVE" else "INACTIVE"}",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                        Switch(
                            checked = isOrionAssistantEnabled,
                            onCheckedChange = {
                                viewModel.toggleOrionWakeWord()
                            }
                        )
                    }
                }
            }

            Text("Core LLM Preferences", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)

            // 1. Selector for AI Provider
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Default AI Provider", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Box {
                    OutlinedButton(
                        onClick = { showProviderMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(selectedProvider, color = MaterialTheme.colorScheme.onSurface)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.Gray)
                        }
                    }
                    DropdownMenu(
                        expanded = showProviderMenu,
                        onDismissRequest = { showProviderMenu = false }
                    ) {
                        AIProviderManager.getProvidersList().forEach { providerName ->
                            DropdownMenuItem(
                                text = { Text(providerName) },
                                onClick = {
                                    selectedProvider = providerName
                                    settingsManager.defaultProvider = providerName
                                    selectedModel = "Default"
                                    settingsManager.defaultModel = "Default"
                                    showProviderMenu = false
                                }
                            )
                        }
                    }
                }
            }

            // 2. Selector for Default Model
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Default Model Name", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Box {
                    OutlinedButton(
                        onClick = { showModelMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(selectedModel, color = MaterialTheme.colorScheme.onSurface)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.Gray)
                        }
                    }
                    DropdownMenu(
                        expanded = showModelMenu,
                        onDismissRequest = { showModelMenu = false }
                    ) {
                        modelsList.forEach { modelName ->
                            DropdownMenuItem(
                                text = { Text(modelName) },
                                onClick = {
                                    selectedModel = modelName
                                    settingsManager.defaultModel = modelName
                                    showModelMenu = false
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            Text("Guest & Sign-in Management", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)

            // Guest Mode & Auto Login Settings
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Guest Access Mode", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("Use public or on-device backends directly if allowed without login credentials.", fontSize = 11.sp, color = Color.Gray)
                }
                Switch(
                    checked = guestModeEnabled,
                    onCheckedChange = {
                        guestModeEnabled = it
                        settingsManager.guestModeEnabled = it
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto Sign-In Connect", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("Automatically use stored accounts and refresh background session tokens.", fontSize = 11.sp, color = Color.Gray)
                }
                Switch(
                    checked = autoLoginEnabled,
                    onCheckedChange = {
                        autoLoginEnabled = it
                        settingsManager.autoLoginEnabled = it
                    }
                )
            }

            // Provider Authentication Card (Login Mode)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Account Status: $selectedProvider",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            if (isLoggedIn && activeAccount != null) {
                                Text(
                                    text = "Connected as: ${activeAccount?.email}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Text(
                                    text = "Not Connected (Using guest or fallback if allowed)",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        if (isLoggedIn) {
                            Button(
                                onClick = {
                                    loginManager.logout(selectedProvider)
                                    isLoggedIn = false
                                    activeAccount = null
                                    Toast.makeText(context, "$selectedProvider session removed.", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Logout", fontSize = 11.sp)
                            }
                        } else {
                            Button(
                                onClick = { showLoginDialog = true }
                            ) {
                                Text("Connect Account", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            Text("Summary & Response Tuner", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)

            // 3. Response Language Preference
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Preferred Response Language", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Box {
                    OutlinedButton(
                        onClick = { showLanguageMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(preferredLanguage, color = MaterialTheme.colorScheme.onSurface)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.Gray)
                        }
                    }
                    DropdownMenu(
                        expanded = showLanguageMenu,
                        onDismissRequest = { showLanguageMenu = false }
                    ) {
                        languageChoices.forEach { lang ->
                            DropdownMenuItem(
                                text = { Text(lang) },
                                onClick = {
                                    preferredLanguage = lang
                                    settingsManager.preferredLanguage = lang
                                    showLanguageMenu = false
                                }
                            )
                        }
                    }
                }
            }

            // 4. Response Style Preference
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Response Tone Style", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Box {
                    OutlinedButton(
                        onClick = { showStyleMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(responseStyle, color = MaterialTheme.colorScheme.onSurface)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.Gray)
                        }
                    }
                    DropdownMenu(
                        expanded = showStyleMenu,
                        onDismissRequest = { showStyleMenu = false }
                    ) {
                        styleChoices.forEach { style ->
                            DropdownMenuItem(
                                text = { Text(style) },
                                onClick = {
                                    responseStyle = style
                                    settingsManager.responseStyle = style
                                    showStyleMenu = false
                                }
                            )
                        }
                    }
                }
            }

            // 5. Response Length Preference
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Default Summary Length", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Box {
                    OutlinedButton(
                        onClick = { showLengthMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(responseLength, color = MaterialTheme.colorScheme.onSurface)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.Gray)
                        }
                    }
                    DropdownMenu(
                        expanded = showLengthMenu,
                        onDismissRequest = { showLengthMenu = false }
                    ) {
                        lengthChoices.forEach { len ->
                            DropdownMenuItem(
                                text = { Text(len) },
                                onClick = {
                                    responseLength = len
                                    settingsManager.responseLength = len
                                    showLengthMenu = false
                                }
                            )
                        }
                    }
                }
            }

            // Chatbot Role & Grounding Settings
            var settingsChatbotRole by remember { mutableStateOf(settingsManager.chatbotRole) }
            var showSettingsRoleMenu by remember { mutableStateOf(false) }
            var settingsSearchGrounding by remember { mutableStateOf(settingsManager.searchGroundingEnabled) }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Chatbot Specialty Role", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Box {
                    OutlinedButton(
                        onClick = { showSettingsRoleMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(settingsChatbotRole, color = MaterialTheme.colorScheme.onSurface)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.Gray)
                        }
                    }
                    DropdownMenu(
                        expanded = showSettingsRoleMenu,
                        onDismissRequest = { showSettingsRoleMenu = false }
                    ) {
                        listOf("General Assistant", "Web Researcher", "Hindi Specialist", "Code Explainer").forEach { role ->
                            DropdownMenuItem(
                                text = { Text(role) },
                                onClick = {
                                    settingsChatbotRole = role
                                    settingsManager.chatbotRole = role
                                    showSettingsRoleMenu = false
                                }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Google Search Grounding", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("Enable real-time accurate information search with Gemini models.", fontSize = 11.sp, color = Color.Gray)
                }
                Switch(
                    checked = settingsSearchGrounding,
                    onCheckedChange = {
                        settingsSearchGrounding = it
                        settingsManager.searchGroundingEnabled = it
                    }
                )
            }

            HorizontalDivider()

            Text("Provider API Credentials", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)

            // API Key management
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "API Endpoint / Token Key for $selectedProvider",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                
                if (selectedProvider.equals("Gemini", ignoreCase = true)) {
                    Text(
                        text = "Note: If left blank, Gemini will fallback to use the secure key injected during deployment within AI Studio.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                OutlinedTextField(
                    value = apiKeyText,
                    onValueChange = {
                        apiKeyText = it
                        settingsManager.setApiKey(selectedProvider, it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    placeholder = { Text("Enter api token/credential for $selectedProvider") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    trailingIcon = {
                        TextButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                            Text(if (apiKeyVisible) "Hide" else "Show", fontSize = 11.sp)
                        }
                    },
                    visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation()
                )

                Spacer(modifier = Modifier.height(12.dp))
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Credential tokens are stored with high security in private Sandboxed Preferences.",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }

    // Connect Account Dialog (Login Mode Authentication simulation)
    if (showLoginDialog) {
        AlertDialog(
            onDismissRequest = { showLoginDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Connect your $selectedProvider", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = "Authentication required. Your login token is saved locally and never exposed.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )

                    OutlinedTextField(
                        value = loginEmail,
                        onValueChange = { loginEmail = it },
                        label = { Text("E-mail Address") },
                        placeholder = { Text("user@example.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = loginPassword,
                        onValueChange = { loginPassword = it },
                        label = { Text("Custom API Key or Password Token") },
                        placeholder = { Text("Bearer secret key...") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        loginLoading = true
                        scope.launch {
                            val result = loginManager.authenticate(selectedProvider, loginEmail, loginPassword)
                            if (result.isSuccess) {
                                val acc = result.getOrNull()
                                isLoggedIn = true
                                activeAccount = acc
                                // Synchronize to settings token
                                settingsManager.setApiKey(selectedProvider, loginPassword)
                                apiKeyText = loginPassword
                                showLoginDialog = false
                                Toast.makeText(context, "$selectedProvider Connected successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, result.exceptionOrNull()?.localizedMessage ?: "Sign-In Failed", Toast.LENGTH_LONG).show()
                            }
                            loginLoading = false
                        }
                    },
                    enabled = !loginLoading
                ) {
                    if (loginLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                    } else {
                        Text("Connect")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showLoginDialog = false }, enabled = !loginLoading) {
                    Text("Cancel")
                }
            }
        )
    }
}
