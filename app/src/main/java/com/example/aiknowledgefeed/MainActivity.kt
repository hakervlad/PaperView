package com.example.aiknowledgefeed

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import com.example.aiknowledgefeed.frontend.navigation.AppNavigation
import com.example.aiknowledgefeed.frontend.theme.AIKnowledgeFeedTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var themeOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
            val darkTheme = themeOverride ?: isSystemInDarkTheme()
            AIKnowledgeFeedTheme(darkTheme = darkTheme) {
                val systemBarColor = MaterialTheme.colorScheme.background.toArgb()
                SideEffect {
                    val style = if (darkTheme) SystemBarStyle.dark(systemBarColor)
                        else SystemBarStyle.light(systemBarColor, systemBarColor)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                AppNavigation(darkTheme = darkTheme, onToggleTheme = { themeOverride = !darkTheme })
            }
        }
    }
}
