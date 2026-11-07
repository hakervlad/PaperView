package com.example.aiknowledgefeed.frontend.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.aiknowledgefeed.frontend.components.CategoryLabel
import com.example.aiknowledgefeed.frontend.components.DemoNotice
import com.example.aiknowledgefeed.frontend.components.KnowledgeConnection
import com.example.aiknowledgefeed.frontend.components.PaperIcons
import com.example.aiknowledgefeed.frontend.components.SectionHeading
import com.example.aiknowledgefeed.frontend.components.TopicTags
import com.example.aiknowledgefeed.frontend.demo.DemoArticle

@Composable
fun DetailScreen(
    article: DemoArticle,
    saved: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onOpenKnowledge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val buttonColor by animateColorAsState(
        if (saved) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary,
        label = "Speicherstatus",
    )
    LazyColumn(modifier, contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(PaperIcons.Back, "Zurück zur vorherigen Seite") }
                Text("Dein nächster Aha-Moment", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CategoryLabel(article.category)
                Text(article.title, style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.semantics { heading() })
                Text("${article.date} · ${article.readingMinutes} Minuten Lesezeit",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Demo-Quelle: PaperView Ideenredaktion · frei erfunden",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DemoNotice()
            }
        }
        item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionHeading("Dein Vorwissen", eyebrow = "01 · DEIN AUSGANGSPUNKT")
                Text("In diesem Beispiel knüpfen wir an diese vertrauten Themen an:",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TopicTags(article.priorKnowledge)
            }
        }
        item { SectionHeading("Was ist neu?", eyebrow = "02 · EIN NEUER GEDANKE") }
        article.detail.split("\n\n").forEachIndexed { index, paragraph ->
            item(key = "paragraph-$index") {
                Text(paragraph, style = MaterialTheme.typography.bodyLarge)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SectionHeading("Die Verbindung zu deinem Wissen", eyebrow = "03 · JETZT PASST ES ZUSAMMEN")
                KnowledgeConnection(article.priorKnowledge, explanation = article.connection,
                    title = "Bekanntes trifft Neues")
            }
        }
        item {
            Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Das nimmst du mit", style = MaterialTheme.typography.titleMedium)
                    Text(article.takeaway, style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onSave, enabled = !saved,
                    modifier = Modifier.fillMaxWidth().semantics {
                        stateDescription = if (saved) "In der Demo-Wissenssammlung gespeichert" else "Noch nicht gespeichert"
                    },
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor,
                        disabledContainerColor = buttonColor,
                        disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
                    AnimatedContent(targetState = saved, label = "Wissen hinzugefügt") { isSaved ->
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(if (isSaved) PaperIcons.Check else PaperIcons.Plus, null, Modifier.size(22.dp))
                            Text(if (isSaved) "In deinem Wissen gespeichert"
                                else "Ich habe das Thema verstanden – zu meinem Wissen hinzufügen",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite })
                        }
                    }
                }
                Text("Nur in dieser Demo-Sammlung. Keine dauerhafte Speicherung.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (saved) {
                    TextButton(onClick = onOpenKnowledge, modifier = Modifier.fillMaxWidth()) {
                        Text("In meiner Wissenssammlung ansehen")
                    }
                }
            }
        }
    }
}
