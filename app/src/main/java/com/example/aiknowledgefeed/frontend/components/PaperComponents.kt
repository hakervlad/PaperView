package com.example.aiknowledgefeed.frontend.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.aiknowledgefeed.frontend.demo.DemoCategory

@Composable
fun BrandHeader(darkTheme: Boolean, onToggleTheme: () -> Unit, modifier: Modifier = Modifier) {
    val largeText = LocalDensity.current.fontScale > 1.2f
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(12.dp)) {
            Icon(PaperIcons.Knowledge, null, Modifier.padding(10.dp).size(22.dp),
                tint = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("PaperView", style = if (largeText) MaterialTheme.typography.titleMedium
                else MaterialTheme.typography.titleLarge)
            if (largeText) Text("WISSEN", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!largeText) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = CircleShape) {
                Text("WISSEN", Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onToggleTheme) {
            Icon(if (darkTheme) PaperIcons.Sun else PaperIcons.Moon,
                contentDescription = if (darkTheme) "Helles Design aktivieren" else "Dunkles Design aktivieren")
        }
    }
}

@Composable
fun CategoryLabel(category: DemoCategory, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (background, foreground) = when (category) {
        DemoCategory.AI, DemoCategory.Science -> colors.primaryContainer to colors.onPrimaryContainer
        DemoCategory.Economy, DemoCategory.Society -> colors.secondaryContainer to colors.onSecondaryContainer
        DemoCategory.Technology -> colors.tertiaryContainer to colors.onTertiaryContainer
    }
    Surface(modifier = modifier, color = background, contentColor = foreground, shape = CircleShape) {
        Text(category.label, Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TopicTags(topics: List<String>, modifier: Modifier = Modifier, onAccent: Boolean = false) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        topics.forEach { topic ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (onAccent) MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                    else MaterialTheme.colorScheme.surfaceContainer,
                contentColor = if (onAccent) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text(topic, Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun KnowledgeConnection(
    topics: List<String>,
    modifier: Modifier = Modifier,
    explanation: String? = null,
    title: String = if (topics.isEmpty()) "Noch keine direkte Wissensverbindung" else "Verknüpft mit deinem Wissen",
) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(PaperIcons.Link, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(title, style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            TopicTags(topics, onAccent = true)
            if (explanation != null) {
                Text(explanation, style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier, eyebrow: String? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (eyebrow != null) Text(eyebrow, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() })
    }
}

@Composable
fun DemoNotice(modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = Color.Transparent,
        shape = MaterialTheme.shapes.small, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Text("DEMO · Frei erfundene Inhalte. Keine aktuellen Nachrichten.",
            Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ConnectionMotif(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    Canvas(modifier.size(64.dp)) {
        val a = Offset(size.width * 0.2f, size.height * 0.25f)
        val b = Offset(size.width * 0.78f, size.height * 0.5f)
        val c = Offset(size.width * 0.3f, size.height * 0.82f)
        drawLine(primary.copy(alpha = 0.4f), a, b, strokeWidth = 1.5.dp.toPx())
        drawLine(primary.copy(alpha = 0.4f), b, c, strokeWidth = 1.5.dp.toPx())
        drawLine(primary.copy(alpha = 0.4f), c, a, strokeWidth = 1.5.dp.toPx())
        listOf(a, b, c).forEachIndexed { index, point ->
            drawCircle(primary, if (index == 1) 9.dp.toPx() else 6.dp.toPx(), point)
            drawCircle(surface, 2.dp.toPx(), point)
        }
    }
}
