package com.example.aiknowledgefeed.frontend.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.aiknowledgefeed.frontend.components.BrandHeader
import com.example.aiknowledgefeed.frontend.components.CategoryFilters
import com.example.aiknowledgefeed.frontend.components.CategoryLabel
import com.example.aiknowledgefeed.frontend.components.ConnectionMotif
import com.example.aiknowledgefeed.frontend.components.DemoNotice
import com.example.aiknowledgefeed.frontend.components.KnowledgeConnection
import com.example.aiknowledgefeed.frontend.components.PaperIcons
import com.example.aiknowledgefeed.frontend.demo.DemoArticle
import com.example.aiknowledgefeed.frontend.demo.DemoCategory
import com.example.aiknowledgefeed.frontend.demo.DemoContent

@Composable
fun FeedScreen(
    knowledgeCount: Int,
    savedArticleIds: Set<String>,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onOpenArticle: (String) -> Unit,
    modifier: Modifier = Modifier,
    sourceArticles: List<DemoArticle> = DemoContent.articles,
    live: Boolean = false,
) {
    val largeText = LocalDensity.current.fontScale > 1.2f
    var selectedCategory by rememberSaveable { mutableStateOf<DemoCategory?>(null) }
    val articles = sourceArticles.filter { selectedCategory == null || it.category == selectedCategory }

    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "header") {
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                BrandHeader(darkTheme, onToggleTheme)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("ENTDECKEN · NEUE PERSPEKTIVEN", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                    Text("Wissen wächst.\nMit jedem Aha.", style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.semantics { heading() })
                    Text("Neue Impulse, die an dein Wissen anknüpfen.",
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Dein Wissen ist der Anfang.", style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("$knowledgeCount Themen in deiner Sammlung",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        if (!largeText) {
                            Spacer(Modifier.width(12.dp))
                            ConnectionMotif()
                        }
                    }
                }
                if (!live) DemoNotice()
            }
        }
        item(key = "filters") {
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Für deinen Blickwinkel", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f).semantics { heading() })
                    Text("${articles.size} Impulse", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                CategoryFilters(selectedCategory, { selectedCategory = it }, Modifier.padding(top = 12.dp))
            }
        }
        itemsIndexed(articles, key = { _, article -> article.id }) { index, article ->
            NewsCard(article, index == 0, article.id in savedArticleIds,
                onClick = { onOpenArticle(article.id) }, modifier = Modifier.padding(horizontal = 24.dp))
        }
        if (articles.isEmpty()) item { Text("Noch keine passenden Veröffentlichungen. Prüfe dein Wissensprofil und die Einstellungen.", Modifier.padding(24.dp)) }
        item(key = "footer") {
            Text("Ein neuer Gedanke beginnt oft mit etwas, das du schon kennst.",
                Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NewsCard(
    article: DemoArticle,
    featured: Boolean,
    saved: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { CategoryLabel(article.category) }
                if (saved) Icon(PaperIcons.Check, "Bereits in deinem Wissen", Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary)
            }
            Text(article.title,
                style = if (featured) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall)
            Text(article.summary, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            KnowledgeConnection(article.priorKnowledge)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${article.date} · ${if (article.sourceUrl.isNotEmpty()) runCatching { java.net.URI(article.sourceUrl).host }.getOrDefault("") else ""} · ${if (article.isDemo) "Demo" else if (article.abstractBased) "Abstractbasiert" else "Paper"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Icon(PaperIcons.Arrow, null, Modifier.padding(10.dp).size(20.dp),
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
