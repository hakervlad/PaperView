package com.example.aiknowledgefeed.frontend.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aiknowledgefeed.backend.models.*
import com.example.aiknowledgefeed.frontend.screens.SettingsScreen
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
import com.example.aiknowledgefeed.frontend.demo.DemoArticle
import com.example.aiknowledgefeed.frontend.demo.DemoContent
import com.example.aiknowledgefeed.frontend.demo.DemoKnowledge
import com.example.aiknowledgefeed.frontend.screens.DetailScreen
import com.example.aiknowledgefeed.frontend.screens.FeedScreen
import com.example.aiknowledgefeed.frontend.screens.KnowledgeScreen

private const val FeedRoute = "discover"
private const val KnowledgeRoute = "knowledge"
private const val DetailRoute = "article/{articleId}"

@Composable
fun AppNavigation(darkTheme: Boolean, onToggleTheme: () -> Unit, modifier: Modifier = Modifier,
    vm: com.example.aiknowledgefeed.frontend.viewmodels.PaperViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    navController: androidx.navigation.NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val state by vm.state.collectAsStateWithLifecycle()
    androidx.lifecycle.compose.LifecycleResumeEffect(vm) { vm.refresh(); onPauseOrDispose { } }
    val knowledge = state.data.knowledge.filter { it.status == "confirmed" }.map { concept -> concept.asCard().copy(connections = concept.connections.mapNotNull { id -> state.data.knowledge.firstOrNull { it.id == id }?.name }) }
    val articles = state.data.feed.items.map { it.asArticle() }
    val savedArticleIds = knowledge.mapNotNull { it.articleId }.toSet()
    // Dient nur der passenden Tab-Markierung auf der Detailseite.
    var detailOrigin by rememberSaveable { mutableStateOf(FeedRoute) }
    val route = backStackEntry?.destination?.route ?: FeedRoute
    val selectedTab = if (route == DetailRoute) detailOrigin else route

    fun openTab(target: String) {
        // Unterseiten (Details/Einstellungen) nicht als Haupt-Tab-Zustand speichern.
        while (navController.currentDestination?.route !in listOf(FeedRoute, KnowledgeRoute)) {
            if (!navController.popBackStack()) break
        }
        navController.navigate(target) {
            popUpTo(FeedRoute) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    fun openArticle(id: String, origin: String) {
        detailOrigin = origin
        vm.openPaper(id)
        navController.navigate("article/${android.net.Uri.encode(id)}") { launchSingleTop = true }
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
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = { vm.generate() }, enabled = !state.busy) { Text("Feed aktualisieren") }
                    TextButton(onClick = { navController.navigate("settings") { launchSingleTop = true } }) { Text("Einstellungen") }
                }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.message?.let { Text((if (state.offline) "Offline · " else "") + it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) }
            NavHost(navController = navController, startDestination = FeedRoute,
                modifier = Modifier.widthIn(max = 760.dp).weight(1f),
                enterTransition = { fadeIn(tween(240)) + slideInHorizontally(tween(240)) { it / 24 } },
                exitTransition = { fadeOut(tween(150)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = { fadeOut(tween(150)) },
            ) {
                composable(FeedRoute) {
                    FeedScreen(knowledgeCount = knowledge.size, savedArticleIds = savedArticleIds,
                        darkTheme = darkTheme, onToggleTheme = onToggleTheme,
                        onOpenArticle = { openArticle(it, FeedRoute) }, sourceArticles = articles, live = true)
                }
                composable(KnowledgeRoute) {
                    KnowledgeScreen(knowledge = knowledge, darkTheme = darkTheme,
                        onToggleTheme = onToggleTheme,
                        onRemove = vm::removeKnowledge,
                        onOpenArticle = { openArticle(it, KnowledgeRoute) },
                        onDiscover = { openTab(FeedRoute) },
                        onReset = {}, live = true)
                }
                composable(DetailRoute, arguments = listOf(navArgument("articleId") { type = NavType.StringType })) { entry ->
                    val id = entry.arguments?.getString("articleId")
                    val article = articles.firstOrNull { it.id == id } ?: state.extraPaper?.takeIf { it.id == id }?.let { paper ->
                        DemoArticle(paper.id, com.example.aiknowledgefeed.frontend.demo.DemoCategory.Science, paper.title, paper.abstract, emptyList(), paper.title, paper.abstract, "Keine gespeicherte personalisierte Erklärung für diese Quelle verfügbar.", "Originalquelle prüfen.", date = paper.published_at, sourceUrl = paper.url, abstractBased = true, isDemo = false)
                    }
                    if (article != null) {
                        DetailScreen(article = article, saved = article.id in savedArticleIds,
                            onBack = { navController.popBackStack() },
                            onSave = { vm.savePaper(article.id) },
                            onOpenKnowledge = { openTab(KnowledgeRoute) }, busy = state.busy)
                    }
                }
                composable("settings") { SettingsScreen(vm, state) }
            }
            }
        }
    }
}
