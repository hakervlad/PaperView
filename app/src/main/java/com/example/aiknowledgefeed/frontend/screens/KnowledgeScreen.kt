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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.aiknowledgefeed.frontend.components.BrandHeader
import com.example.aiknowledgefeed.frontend.components.CategoryFilters
import com.example.aiknowledgefeed.frontend.components.CategoryLabel
import com.example.aiknowledgefeed.frontend.components.PaperIcons
import com.example.aiknowledgefeed.frontend.components.TopicTags
import com.example.aiknowledgefeed.frontend.demo.DemoCategory
import com.example.aiknowledgefeed.frontend.demo.DemoKnowledge

@Composable
fun KnowledgeScreen(
    knowledge: List<DemoKnowledge>,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onRemove: (String) -> Unit,
    onOpenArticle: (String) -> Unit,
    onDiscover: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    live: Boolean = false,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf<DemoCategory?>(null) }
    val needle = query.trim()
    val filtered = knowledge.filter { topic ->
        (selectedCategory == null || topic.category == selectedCategory) &&
            (listOf(topic.title, topic.description, topic.category.label) + topic.connections)
                .any { it.contains(needle, ignoreCase = true) }
    }

    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "header") {
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                BrandHeader(darkTheme, onToggleTheme)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("MEIN WISSEN · DEINE SAMMLUNG", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                    Text("Was du weißt,\nöffnet neue Türen.", style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier.semantics { heading() })
                    Text("Kein Sammelsurium. Dein Ausgangspunkt für neue Zusammenhänge.",
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("${knowledge.size}", style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("gespeicherte Themen", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("${knowledge.map { it.category }.distinct().size}",
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Wissensbereiche", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
                OutlinedTextField(value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    label = { Text("Wissen durchsuchen") },
                    placeholder = { Text("MCP, Energie, Datenschutz …") },
                    leadingIcon = { Icon(PaperIcons.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                            Icon(PaperIcons.Close, "Suchfeld leeren")
                        }
                    })
            }
        }
        item(key = "filters") { CategoryFilters(selectedCategory, { selectedCategory = it }) }
        item(key = "count") {
            Text(if (needle.isEmpty() && selectedCategory == null) "DEINE WISSENSBAUSTEINE"
                else "${filtered.size} PASSENDE THEMEN",
                Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (filtered.isEmpty()) {
            item(key = "empty") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(if (knowledge.isEmpty()) PaperIcons.Knowledge else PaperIcons.Search,
                        null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(if (knowledge.isEmpty()) "Deine Sammlung beginnt hier" else "Noch keine Verbindung gefunden",
                        style = MaterialTheme.typography.headlineSmall)
                    Text(if (knowledge.isEmpty()) "Öffne einen Impuls unter Entdecken und füge dein erstes neues Thema hinzu."
                        else "Versuche einen anderen Suchbegriff oder wähle einen anderen Wissensbereich.",
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = {
                        if (knowledge.isEmpty()) onDiscover() else { query = ""; selectedCategory = null }
                    }) { Text(if (knowledge.isEmpty()) "Impulse entdecken" else "Suche und Filter zurücksetzen") }
                }
            }
        }
        items(filtered, key = { it.id }) { topic ->
            KnowledgeCard(topic, onRemove = { onRemove(topic.id) },
                onOpenArticle = onOpenArticle, modifier = Modifier.padding(horizontal = 24.dp))
        }
        item(key = "footer") {
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (live) "Dein bestätigtes Wissen · mit Originalquellen gespeichert." else "Demo-Sammlung · Änderungen werden nicht dauerhaft gespeichert.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!live) TextButton(onClick = { onReset(); query = ""; selectedCategory = null }) {
                    Text("Demo-Sammlung zurücksetzen")
                }
            }
        }
    }
}

@Composable
private fun KnowledgeCard(
    topic: DemoKnowledge,
    onRemove: () -> Unit,
    onOpenArticle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { CategoryLabel(topic.category) }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onRemove) {
                    Icon(PaperIcons.Close, "${topic.title} aus der Wissenssammlung entfernen",
                        Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(topic.title, style = MaterialTheme.typography.headlineSmall)
            Text(topic.description, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(PaperIcons.Link, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Verknüpfte Wissensgebiete", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
            TopicTags(topic.connections)
            topic.articleId?.let { articleId ->
                TextButton(onClick = { onOpenArticle(articleId) }) {
                    Text("Quelle und Erklärung erneut lesen")
                    Spacer(Modifier.width(8.dp))
                    Icon(PaperIcons.Arrow, null, Modifier.size(18.dp))
                }
            }
        }
    }
}
