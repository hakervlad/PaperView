package com.example.aiknowledgefeed

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.aiknowledgefeed.backend.models.Settings
import com.example.aiknowledgefeed.backend.services.SecureStore
import com.example.aiknowledgefeed.frontend.navigation.AppNavigation
import com.example.aiknowledgefeed.frontend.viewmodels.PaperViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import com.example.aiknowledgefeed.backend.services.SettingsPersistence
import com.example.aiknowledgefeed.backend.services.SettingsStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsNavigationRegressionTest {
    @get:Rule val compose = createComposeRule()
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Before fun resetIsolatedStorage() = runBlocking { SecureStore(app).clearCache(); SecureStore(app).settings(Settings()) }

    @Test fun locallySaveKeyWithoutConfiguredBackendAndKeepItAfterReopening() {
        val store=SecureStore(app)
        runBlocking { store.settings(Settings()) }
        val vm=PaperViewModel(app)
        compose.setContent { MaterialTheme { AppNavigation(false, {}, vm = vm) } }
        compose.onNodeWithText("Einstellungen").performClick()
        compose.onNodeWithText("Eigener API-Key (optional)").performScrollTo().performTextInput("fixture-not-a-real-key")
        compose.onNodeWithText("KI-Einstellungen speichern").performScrollTo().performClick()
        compose.waitUntil(10000) { vm.state.value.settings.apiKey == "fixture-not-a-real-key" }
        compose.onNodeWithText("Mein Wissen").performClick()
        compose.onNodeWithText("Einstellungen").performClick()
        compose.onNodeWithText("API-Key gespeichert").performScrollTo().assertIsDisplayed()
        assertEquals("fixture-not-a-real-key", runBlocking { SecureStore(app).settings() }.apiKey)
    }

    @Test fun settingsMustNotBeRestoredAsKnowledgeTabAfterRepeatedSwitchesAndStateRestoration() {
        runBlocking { SecureStore(app).settings(Settings()) }
        val restoration=StateRestorationTester(compose)
        restoration.setContent { MaterialTheme { AppNavigation(false, {}) } }
        repeat(3) {
            compose.onNodeWithText("Mein Wissen").performClick()
            compose.onNodeWithText("MEIN WISSEN · DEINE SAMMLUNG").assertIsDisplayed()
            compose.onNodeWithText("Einstellungen").performClick()
            compose.onNodeWithText("Backend-Verbindung").assertIsDisplayed()
            compose.onNodeWithText("Mein Wissen").performClick()
            compose.onNodeWithText("MEIN WISSEN · DEINE SAMMLUNG").assertIsDisplayed()
            compose.onNodeWithText("Entdecken").performClick()
            compose.onNodeWithText("ENTDECKEN · NEUE PERSPEKTIVEN").assertIsDisplayed()
        }
        compose.onNodeWithText("Mein Wissen").performClick()
        compose.onNodeWithText("Einstellungen").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Mein Wissen").performClick()
        compose.onNodeWithText("MEIN WISSEN · DEINE SAMMLUNG").assertIsDisplayed()
    }

    @Test fun encryptedStoreSurvivesReinitializationAndIsNotPlaintext() = runBlocking {
        val key="fixture-encrypted-secret"
        SecureStore(app).settings(Settings(apiKey=key))
        assertEquals(key, SecureStore(app).settings().apiKey)
        val bytes=java.io.File(app.filesDir,"datastore/connection.preferences_pb").readBytes()
        assertFalse(bytes.toString(Charsets.UTF_8).contains(key))
        val vm=PaperViewModel(app)
        withTimeout(10000) { vm.state.first { it.settings.apiKey == key } }
        // An optional failed connection test must not touch the stored key.
        vm.test()
        withTimeout(10000) { vm.state.first { !it.busy } }
        assertEquals(key, SecureStore(app).settings().apiKey)
    }

    @Test fun storageFailureIsVisibleAndPreviousKeyRemainsAvailable() = runBlocking {
        val storage=object:SettingsStorage {
            override suspend fun settings()=Settings(apiKey="old-fixture")
            override suspend fun settings(value:Settings) { throw java.io.IOException("fixture storage error") }
        }
        val vm=PaperViewModel(app,SettingsPersistence(storage))
        withTimeout(10000) { vm.state.first { !it.busy } }
        compose.setContent { MaterialTheme { AppNavigation(false, {}, vm = vm) } }
        vm.configure(Settings(apiKey="new-fixture"))!!.join()
        compose.onNodeWithText("Sicheres Speichern fehlgeschlagen", substring = true).assertIsDisplayed()
        assertEquals("old-fixture", vm.state.value.settings.apiKey)
        assertTrue(vm.state.value.message!!.contains("Sicheres Speichern fehlgeschlagen"))
        assertFalse(vm.state.value.message!!.contains("new-fixture"))
    }

    @Test fun saveIsNotDiscardedDuringInitialLoading() = runBlocking {
        val storage=object:SettingsStorage {
            var first=true
            var value=Settings()
            override suspend fun settings():Settings {
                if(first) { first=false; kotlinx.coroutines.awaitCancellation() }
                return value
            }
            override suspend fun settings(value:Settings) { this.value=value }
        }
        val vm=PaperViewModel(app,SettingsPersistence(storage))
        // Wait until the first read entered the suspended in-flight operation.
        withTimeout(10000) { while(storage.first) kotlinx.coroutines.yield() }
        assertTrue(vm.state.value.busy)
        vm.configure(Settings(apiKey="fixture-during-load"))!!.join()
        assertEquals("fixture-during-load",vm.state.value.settings.apiKey)
        assertFalse(vm.state.value.settingsSaving)
    }

    @Test fun systemBackReturnsFromSettingsToItsActualOrigin() {
        compose.setContent { MaterialTheme { AppNavigation(false, {}) } }
        compose.onNodeWithText("Mein Wissen").performClick()
        compose.onNodeWithText("Einstellungen").performClick()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithText("MEIN WISSEN · DEINE SAMMLUNG").assertIsDisplayed()
        compose.onNodeWithText("Entdecken").performClick()
        compose.onNodeWithText("Einstellungen").performClick()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithText("ENTDECKEN · NEUE PERSPEKTIVEN").assertIsDisplayed()
    }

    @Test fun legacySavedSettingsCannotReappearAsKnowledgeTab() {
        var controller: androidx.navigation.NavHostController? = null
        compose.setContent {
            val nav = androidx.navigation.compose.rememberNavController()
            androidx.compose.runtime.SideEffect { controller = nav }
            MaterialTheme { AppNavigation(false, {}, navController = nav) }
        }
        compose.runOnIdle {
            val nav = controller!!
            nav.navigate("knowledge")
            nav.navigate("settings")
            // Simulate the saved stack produced by the previously installed version.
            nav.navigate("discover") {
                popUpTo("discover") { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        compose.onNodeWithText("Mein Wissen").performClick()
        compose.onNodeWithText("MEIN WISSEN · DEINE SAMMLUNG").assertIsDisplayed()
    }

    @Test fun delayedSettingsLoadDoesNotClearAnAlreadyTypedKey() {
        val gate=kotlinx.coroutines.CompletableDeferred<Unit>()
        val storage=object:SettingsStorage {
            var value=Settings(provider="loaded-provider")
            override suspend fun settings():Settings { gate.await();return value }
            override suspend fun settings(value:Settings) { this.value=value }
        }
        val vm=PaperViewModel(app,SettingsPersistence(storage))
        compose.setContent { MaterialTheme { AppNavigation(false, {}, vm = vm) } }
        compose.onNodeWithText("Einstellungen").performClick()
        compose.onNodeWithText("Eigener API-Key (optional)").performScrollTo().performTextInput("fixture-typed-before-load")
        gate.complete(Unit)
        compose.waitUntil(10000) { !vm.state.value.busy }
        compose.onNodeWithText("KI-Einstellungen speichern").performScrollTo().performClick()
        compose.waitUntil(10000) { vm.state.value.settings.apiKey == "fixture-typed-before-load" }
    }
}
