package com.example.aiknowledgefeed

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationRecreationTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun knowledgeTabAfterSettingsAndActivityRecreation() {
        compose.onNodeWithText("Mein Wissen").performClick()
        compose.onNodeWithText("Einstellungen").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Daten auf diesem Gerät").assertIsDisplayed()
        compose.onNodeWithText("Mein Wissen").performClick()
        compose.onNodeWithText("MEIN WISSEN · DEINE SAMMLUNG").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("MEIN WISSEN · DEINE SAMMLUNG").assertIsDisplayed()
    }
}
