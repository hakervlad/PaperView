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
