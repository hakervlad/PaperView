package com.example.aiknowledgefeed.frontend.screens

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.aiknowledgefeed.frontend.demo.DemoContent
import com.example.aiknowledgefeed.frontend.navigation.AppNavigation
import com.example.aiknowledgefeed.frontend.theme.AIKnowledgeFeedTheme

@Preview(name = "Entdecken · Hell", widthDp = 393, heightDp = 852)
@Preview(name = "Entdecken · Dunkel", widthDp = 393, heightDp = 852,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Kleines Display · Große Schrift", widthDp = 320, heightDp = 720, fontScale = 1.5f)
@Preview(name = "Breites Display", widthDp = 900, heightDp = 700)
@Composable
private fun FeedPreview() {
    val darkTheme = isSystemInDarkTheme()
    AIKnowledgeFeedTheme {
        AppNavigation(darkTheme = darkTheme, onToggleTheme = {})
    }
}

@Preview(name = "Details · Hell", widthDp = 393, heightDp = 852)
@Preview(name = "Details · Dunkel", widthDp = 393, heightDp = 852,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DetailPreview() {
    AIKnowledgeFeedTheme {
        Surface {
            DetailScreen(DemoContent.articles.first(), saved = false,
                onBack = {}, onSave = {}, onOpenKnowledge = {})
        }
    }
}

@Preview(name = "Mein Wissen", widthDp = 393, heightDp = 852)
@Preview(name = "Mein Wissen · Dunkel", widthDp = 393, heightDp = 852,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun KnowledgePreview() {
    val darkTheme = isSystemInDarkTheme()
    AIKnowledgeFeedTheme {
        Surface {
            KnowledgeScreen(DemoContent.initialKnowledge, darkTheme = darkTheme,
                onToggleTheme = {}, onRemove = {}, onOpenArticle = {}, onDiscover = {}, onReset = {})
        }
    }
}

@Preview(name = "Leere Sammlung", widthDp = 393, heightDp = 852)
@Composable
private fun EmptyKnowledgePreview() {
    AIKnowledgeFeedTheme {
        Surface {
            KnowledgeScreen(emptyList(), darkTheme = false,
                onToggleTheme = {}, onRemove = {}, onOpenArticle = {}, onDiscover = {}, onReset = {})
        }
    }
}
