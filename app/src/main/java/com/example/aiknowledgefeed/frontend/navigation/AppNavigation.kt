package com.example.aiknowledgefeed.frontend.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.aiknowledgefeed.R
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.aiknowledgefeed.frontend.components.PaperIcons
import com.example.aiknowledgefeed.frontend.demo.DemoContent
import com.example.aiknowledgefeed.frontend.demo.DemoKnowledge
import com.example.aiknowledgefeed.frontend.screens.DetailScreen
import com.example.aiknowledgefeed.frontend.screens.FeedScreen
import com.example.aiknowledgefeed.frontend.screens.KnowledgeScreen

private const val FeedRoute = "discover"
private const val KnowledgeRoute = "knowledge"
private const val DetailRoute = "article/{articleId}"

@Composable
fun AppNavigation(darkTheme: Boolean, onToggleTheme: () -> Unit, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    var knowledgeIds by rememberSaveable {
        mutableStateOf<List<String>>(DemoContent.initialKnowledge.map { it.id })
    }
    val articleKnowledge = DemoContent.articles.map { article ->
        DemoKnowledge(id = "article-${article.id}", title = article.topic,
            category = article.category, description = article.takeaway,
            connections = article.priorKnowledge, articleId = article.id)
    }
    val allKnowledge = DemoContent.initialKnowledge + articleKnowledge
    val knowledge = allKnowledge.filter { it.id in knowledgeIds }
    val savedArticleIds = knowledge.mapNotNull { it.articleId }.toSet()
    // Dient nur der passenden Tab-Markierung auf der Detailseite.
    var detailOrigin by rememberSaveable { mutableStateOf(FeedRoute) }
    val route = backStackEntry?.destination?.route ?: FeedRoute
    val selectedTab = if (route == DetailRoute) detailOrigin else route

    fun openTab(target: String) {
        // Details nicht als gespeicherten Tab-Zustand wiederherstellen.
        if (navController.currentDestination?.route == DetailRoute) navController.popBackStack()
        navController.navigate(target) {
            popUpTo(FeedRoute) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    fun openArticle(id: String, origin: String) {
        detailOrigin = origin
        navController.navigate("article/$id") { launchSingleTop = true }
    }

    Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                NavigationBarItem(selected = selectedTab == FeedRoute,
                    onClick = { openTab(FeedRoute) }, icon = { Icon(PaperIcons.Discover, null) },
                    label = { Text(stringResource(R.string.screen_feed)) })
                NavigationBarItem(selected = selectedTab == KnowledgeRoute,
                    onClick = { openTab(KnowledgeRoute) }, icon = { Icon(PaperIcons.Knowledge, null) },
                    label = { Text(stringResource(R.string.screen_knowledge)) })
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.TopCenter) {
            NavHost(navController = navController, startDestination = FeedRoute,
                modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
                enterTransition = { fadeIn(tween(240)) + slideInHorizontally(tween(240)) { it / 24 } },
                exitTransition = { fadeOut(tween(150)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = { fadeOut(tween(150)) },
            ) {
                composable(FeedRoute) {
                    FeedScreen(knowledgeCount = knowledge.size, savedArticleIds = savedArticleIds,
                        darkTheme = darkTheme, onToggleTheme = onToggleTheme,
                        onOpenArticle = { openArticle(it, FeedRoute) })
                }
                composable(KnowledgeRoute) {
                    KnowledgeScreen(knowledge = knowledge, darkTheme = darkTheme,
                        onToggleTheme = onToggleTheme,
                        onRemove = { id -> knowledgeIds = knowledgeIds.filterNot { it == id } },
                        onOpenArticle = { openArticle(it, KnowledgeRoute) },
                        onDiscover = { openTab(FeedRoute) },
                        onReset = { knowledgeIds = DemoContent.initialKnowledge.map { it.id } })
                }
                composable(DetailRoute, arguments = listOf(navArgument("articleId") { type = NavType.StringType })) { entry ->
                    val article = DemoContent.articles.firstOrNull { it.id == entry.arguments?.getString("articleId") }
                    if (article != null) {
                        DetailScreen(article = article, saved = article.id in savedArticleIds,
                            onBack = { navController.popBackStack() },
                            onSave = {
                                val id = "article-${article.id}"
                                if (id !in knowledgeIds) knowledgeIds = knowledgeIds + id
                            },
                            onOpenKnowledge = { openTab(KnowledgeRoute) })
                    }
                }
            }
        }
    }
}
