package com.example.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PreferenceManager

data class SearchEngine(
    val name: String,
    val searchUrl: String,
    val suggestUrl: String,
    val homepageUrl: String
)

val searchEngines = listOf(
    SearchEngine(
        name = "Google",
        searchUrl = "https://www.google.com/search?q={query}",
        suggestUrl = "https://suggestqueries.google.com/complete/search?client=firefox&q={query}",
        homepageUrl = "https://www.google.com"
    ),
    SearchEngine(
        name = "Bing",
        searchUrl = "https://www.bing.com/search?q={query}",
        suggestUrl = "https://api.bing.com/osjson.aspx?query={query}",
        homepageUrl = "https://www.bing.com"
    ),
    SearchEngine(
        name = "DuckDuckGo",
        searchUrl = "https://duckduckgo.com/?q={query}",
        suggestUrl = "https://duckduckgo.com/ac/?q={query}&type=list",
        homepageUrl = "https://duckduckgo.com"
    ),
    SearchEngine(
        name = "Yahoo",
        searchUrl = "https://search.yahoo.com/search?p={query}",
        suggestUrl = "https://ff.search.yahoo.com/gossip?output=fxjson&command={query}",
        homepageUrl = "https://www.yahoo.com"
    ),
    SearchEngine(
        name = "Brave",
        searchUrl = "https://search.brave.com/search?q={query}",
        suggestUrl = "https://search.brave.com/api/suggest?q={query}",
        homepageUrl = "https://search.brave.com"
    ),
    SearchEngine(
        name = "ChatGPT",
        searchUrl = "https://chatgpt.com/?q={query}",
        suggestUrl = "",
        homepageUrl = "https://chatgpt.com"
    ),
    SearchEngine(
        name = "Claude (Anthropic)",
        searchUrl = "https://claude.ai/search?q={query}",
        suggestUrl = "",
        homepageUrl = "https://claude.ai"
    ),
    SearchEngine(
        name = "Perplexity AI",
        searchUrl = "https://www.perplexity.ai/search?q={query}",
        suggestUrl = "",
        homepageUrl = "https://www.perplexity.ai"
    ),
    SearchEngine(
        name = "Ecosia",
        searchUrl = "https://www.ecosia.org/search?q={query}",
        suggestUrl = "https://ac.ecosia.org/?q={query}",
        homepageUrl = "https://www.ecosia.org"
    ),
    SearchEngine(
        name = "Yandex",
        searchUrl = "https://yandex.com/search/?text={query}",
        suggestUrl = "https://suggest.yandex.com/suggest-ff.cgi?part={query}",
        homepageUrl = "https://yandex.com"
    ),
    SearchEngine(
        name = "Startpage",
        searchUrl = "https://www.startpage.com/search?q={query}",
        suggestUrl = "",
        homepageUrl = "https://www.startpage.com"
    ),
    SearchEngine(
        name = "Qwant",
        searchUrl = "https://www.qwant.com/?q={query}",
        suggestUrl = "https://api.qwant.com/v3/suggest?q={query}",
        homepageUrl = "https://www.qwant.com"
    )
)

@Composable
fun SearchEngineFragment(
    prefs: PreferenceManager,
    onBack: () -> Unit
) {
    var selectedEngine by remember {
        mutableStateOf(prefs.getString("default_search_engine", "Google"))
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
                text = "Search Engine settings",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(searchEngines) { engine ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedEngine = engine.name
                            prefs.setString("default_search_engine", engine.name)
                        }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Colored Circle Initials Logo
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    when (engine.name.first()) {
                                        'G' -> Color(0xFF4285F4)
                                        'B' -> Color(0xFF008080)
                                        'D' -> Color(0xFFDE5833)
                                        'Y' -> Color(0xFF6001D2)
                                        'C' -> Color(0xFF10A37F)
                                        'P' -> Color(0xFF19C1DE)
                                        'E' -> Color(0xFF00A550)
                                        'Q' -> Color(0xFF35495E)
                                        else -> MaterialTheme.colorScheme.primary
                                    }.copy(alpha = 0.2f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = engine.name.substring(0, 1),
                                color = when (engine.name.first()) {
                                    'G' -> Color(0xFF4285F4)
                                    'B' -> Color(0xFF008080)
                                    'D' -> Color(0xFFDE5833)
                                    'Y' -> Color(0xFF6001D2)
                                    'C' -> Color(0xFF10A37F)
                                    'P' -> Color(0xFF19C1DE)
                                    'E' -> Color(0xFF00A550)
                                    'Q' -> Color(0xFF35495E)
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = engine.name,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = engine.homepageUrl,
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }

                    RadioButton(
                        selected = (selectedEngine == engine.name),
                        onClick = {
                            selectedEngine = engine.name
                            prefs.setString("default_search_engine", engine.name)
                        }
                    )
                }
            }
        }
    }
}
