package com.example.aiknowledgefeed

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.aiknowledgefeed.backend.api.*
import com.example.aiknowledgefeed.backend.database.LocalDatabase
import com.example.aiknowledgefeed.backend.models.*
import com.example.aiknowledgefeed.backend.repositories.*
import com.example.aiknowledgefeed.backend.services.*
import com.example.aiknowledgefeed.frontend.viewmodels.PaperViewModel
import com.google.gson.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LocalViewModelTest {
    @Test fun failedSecureSettingsLoadDoesNotHideLocalKnowledge() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = LocalDatabase.inMemory(app)
        val repository = PaperViewRepository(app, db = db)
        val known = repository.knowledge.addConcept("Bayes", "Lokal und offline", "confirmed")
        val storage = object : SettingsStorage {
            override suspend fun settings(): Settings = throw java.io.IOException("fixture-sensitive-settings")
            override suspend fun settings(value: Settings) = error("Must not overwrite")
        }
        val vm = PaperViewModel(app, SettingsPersistence(storage), repository)
        val store = ViewModelStore().apply { put("vm", vm) }
        try {
            val state = withTimeout(10000) { vm.state.first { !it.busy } }
            assertTrue(state.data.knowledge.any { it.id == known.id })
            assertTrue(state.message!!.contains("Einstellungen")); assertFalse(state.message!!.contains("fixture-sensitive"))
        } finally { store.clear(); db.close() }
    }
    @Test fun localProfileCanBeSavedWhileFeedIsWaitingForNetwork() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = LocalDatabase.inMemory(app)
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val source = object : PaperSource {
            override suspend fun search(profile: Profile, known: List<Knowledge>, day: LocalDate): PaperSearchResult {
                started.complete(Unit); release.await(); return PaperSearchResult(emptyList())
            }
        }
        val repository = PaperViewRepository(app, db = db, source = source)
        val storage = object : SettingsStorage {
            override suspend fun settings() = Settings()
            override suspend fun settings(value: Settings) { }
        }
        val vm = PaperViewModel(app, SettingsPersistence(storage), repository)
        val store = ViewModelStore().apply { put("vm", vm) }
        try {
            withTimeout(10000) { vm.state.first { !it.busy } }
            vm.generate(); withTimeout(10000) { started.await() }
            assertTrue(vm.state.value.feedGenerating)
            vm.profile(Profile(bachelor_subject = "Offline unabhängig"))
            withTimeout(10000) { vm.state.first { !it.busy && it.data.profile.bachelor_subject == "Offline unabhängig" } }
            assertEquals("Offline unabhängig", repository.knowledge.profile().bachelor_subject)
            assertTrue(vm.state.value.feedGenerating)
            release.complete(Unit); withTimeout(10000) { vm.state.first { !it.feedGenerating } }
            Unit
        } finally { release.complete(Unit); store.clear(); withTimeout(10000) { vm.state.first { !it.feedGenerating } }; db.close() }
    }
    @Test fun olderKnowledgeReopensItsStoredPersonalizedExplanation() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = LocalDatabase.inMemory(app)
        val repository = PaperViewRepository(app, db = db)
        val known = repository.knowledge.addConcept("Bayes", "Bekannte Wahrscheinlichkeit", "confirmed")
        val item = FeedItem("old-item", "old-paper", "Bayes für Biomarker", "Personalisierte frühere Erklärung", listOf(known), "Neue Methode", "Dein bestätigtes Bayes-Wissen erklärt die Unsicherheit der Methode.", "Wissen übertragen", "Bayes bewertet Biomarker.", true, "https://example.org/paper", "2026-11-07", "Wissenschaft")
        repository.knowledge.importSnapshot(Snapshot(feed = Feed("2026-11-07", "success", listOf(item)), knowledge = listOf(known)), "old-issue")
        repository.knowledge.understood(item.paper_id)
        repository.knowledge.importSnapshot(Snapshot(feed = Feed("2026-11-08", "success")), "latest-issue")
        val storage = object : SettingsStorage {
            override suspend fun settings() = Settings()
            override suspend fun settings(value: Settings) { }
        }
        val vm = PaperViewModel(app, SettingsPersistence(storage), repository)
        val store = ViewModelStore().apply { put("vm", vm) }
        try {
            withTimeout(10000) { vm.state.first { !it.busy } }
            assertTrue(vm.state.value.data.feed.items.isEmpty())
            vm.openPaper(item.paper_id)
            val result = withTimeout(10000) { vm.state.first { !it.busy && it.extraFeedItem != null } }
            assertEquals(item.summary, result.extraFeedItem!!.summary)
            assertEquals(item.connection, result.extraFeedItem!!.connection)
            assertEquals(item.paper_id, result.extraFeedItem!!.paper_id)
            assertNull(result.extraPaper)
        } finally { store.clear(); db.close() }
    }
    @Test fun profileKnowledgeAndSettingsWorkWithNoServerAndNoModel() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = LocalDatabase.inMemory(app)
        var calls = 0
        val source = object : PaperSource { override suspend fun search(profile: Profile, known: List<Knowledge>, day: LocalDate): PaperSearchResult { calls++; error("No network for local functions") } }
        val model = object : ModelClient { override suspend fun complete(settings: Settings, instruction: String, data: JsonElement): JsonObject { calls++; error("No model for local functions") } }
        val repository = PaperViewRepository(app, db = db, model = model, source = source)
        val storage = object : SettingsStorage {
            var value = Settings()
            override suspend fun settings() = value
            override suspend fun settings(value: Settings) { this.value = value }
        }
        val vm = PaperViewModel(app, SettingsPersistence(storage), repository)
        val store = ViewModelStore().apply { put("vm", vm) }
        suspend fun idle() = withTimeout(10000) { vm.state.first { !it.busy && !it.settingsSaving } }
        try {
            idle()
            vm.profile(Profile(bachelor_subject = "Informatik", familiar_topics = listOf("Bayes"))); idle()
            withTimeout(10000) { vm.state.first { it.data.knowledge.any { k -> k.name == "Bayes" } } }
            assertEquals("Informatik", vm.state.value.data.profile.bachelor_subject)
            assertEquals(1, vm.state.value.data.knowledge.size)
            vm.configure(Settings(apiKey = "fixture-key"))!!.join()
            assertEquals("fixture-key", storage.value.apiKey)
            assertEquals("Informatik", vm.state.value.data.profile.bachelor_subject)
            assertEquals(1, repository.knowledge.knowledge().size)
            val next = repository.knowledge.addConcept("APIs", "Schnittstellen", "confirmed")
            withTimeout(10000) { vm.state.first { state -> state.data.knowledge.any { it.id == next.id } } }
            vm.removeKnowledge(next.id); idle()
            assertFalse(repository.knowledge.knowledge().any { it.id == next.id })
            assertEquals(0, calls)
        } finally { store.clear(); db.close() }
    }
}
