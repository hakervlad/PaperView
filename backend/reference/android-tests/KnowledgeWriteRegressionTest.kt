package com.example.aiknowledgefeed

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.aiknowledgefeed.backend.models.*
import com.example.aiknowledgefeed.backend.services.*
import com.example.aiknowledgefeed.frontend.viewmodels.PaperViewModel
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class KnowledgeWriteRegressionTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val gson = Gson()
    private val known = Knowledge("saved", "Bayes", "Unsicherheit bei Biomarkern", "confirmed", source_paper_id = "paper-1")
    @Before fun reset() = runBlocking { SecureStore(app).clearCache(); SecureStore(app).settings(Settings()) }
    private suspend fun idle(vm: PaperViewModel) = withTimeout(10_000) { vm.state.first { !it.busy && !it.settingsSaving } }
    private suspend fun configured(server: MockWebServer, application: Application = app): PaperViewModel {
        val vm = PaperViewModel(application, SettingsPersistence(SecureStore(app)))
        idle(vm)
        vm.configure(Settings(backendUrl = server.url("/").toString(), token = "fixture-backend-token"))!!.join()
        return vm
    }
    private fun json(value: Any, status: Int = 200) = MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(gson.toJson(value))

    @Test fun serverAcknowledgementUpdatesCollectionWithoutDependingOnAnotherGet() = runBlocking {
        MockWebServer().use { server ->
            val vm = configured(server)
            server.enqueue(json(known))
            // Old implementation discards the successful write if this read fails.
            server.enqueue(json(emptyMap<String, String>(), 503))
            vm.savePaper("paper-1"); val result = idle(vm)
            assertEquals(known, result.data.knowledge.single())
            assertTrue(result.message!!.startsWith("Thema dauerhaft"))
            assertEquals("/v1/papers/paper-1/understood", server.takeRequest().path)
            assertEquals(known, SecureStore(app).snapshot().knowledge.single())
            // New process/repository reads the encrypted disk snapshot, not a UI flag.
            assertEquals(known.id, SecureStore(app).snapshot().knowledge.single().id)
        }
    }
    @Test fun profileAcknowledgementSurvivesFailedKnowledgeRefresh() = runBlocking {
        MockWebServer().use { server ->
            val vm = configured(server); val saved = Profile(bachelor_subject = "Informatik")
            server.enqueue(json(saved)); server.enqueue(json(emptyMap<String, String>(), 503))
            vm.profile(saved); val result = idle(vm)
            assertEquals(saved, result.data.profile)
            assertTrue(result.message!!.startsWith("Wissensprofil auf dem Backend gespeichert."))
            assertEquals(saved, SecureStore(app).snapshot().profile)
        }
    }
    @Test fun failedWriteNeverMarksTopicSaved() = runBlocking {
        MockWebServer().use { server ->
            val vm = configured(server)
            server.enqueue(json(mapOf("error" to mapOf("code" to "internal_error", "message" to "DO_NOT_DISPLAY_SECRET")), 500))
            vm.savePaper("paper-1"); val result = idle(vm)
            assertTrue(result.data.knowledge.isEmpty()); assertTrue(SecureStore(app).snapshot().knowledge.isEmpty())
            assertTrue(result.message!!.contains("Serverfehler")); assertFalse(result.message!!.contains("SECRET"))
        }
    }
    @Test fun unavailableBackendNeverMarksTopicSaved() = runBlocking {
        val server = MockWebServer(); val vm = configured(server); server.close()
        vm.savePaper("paper-1"); val result = idle(vm)
        assertTrue(result.data.knowledge.isEmpty()); assertTrue(result.message!!.contains("Backend nicht erreichbar"))
    }
    @Test fun duplicateAcknowledgementDoesNotDuplicateCollection() = runBlocking {
        MockWebServer().use { server ->
            val vm = configured(server)
            repeat(2) { server.enqueue(json(known)); vm.savePaper("paper-1"); idle(vm) }
            assertEquals(listOf(known), vm.state.value.data.knowledge)
        }
    }
    @Test fun cacheFailureDoesNotUndoServerAcknowledgement() = runBlocking {
        val blocked = File(app.cacheDir, "cache-parent-file").apply { writeText("fixture") }
        val application = object : Application() { override fun getNoBackupFilesDir() = File(blocked, "child") }
        try {
            MockWebServer().use { server ->
                val vm = configured(server, application); server.enqueue(json(known))
                vm.savePaper("paper-1"); val result = idle(vm)
                assertEquals(known, result.data.knowledge.single())
                assertTrue(result.message!!.contains("Offline-Cache")); assertTrue(result.message!!.startsWith("Thema dauerhaft"))
            }
        } finally { blocked.delete() }
    }
    @Test fun ownKeyIsNeverSentOverHttpAndRemainsSecurelySavedAfterFailedTest() = runBlocking {
        MockWebServer().use { server ->
            val vm = configured(server)
            vm.configure(vm.state.value.settings.copy(apiKey = "fixture-not-a-real-key"))!!.join()
            server.enqueue(json(mapOf("ok" to true))); server.enqueue(json(Profile()))
            vm.test(); val result = idle(vm)
            assertEquals("Erfolgreich", result.connectionSteps[0].status)
            assertEquals("Erfolgreich", result.connectionSteps[1].status)
            assertTrue(result.connectionSteps[2].detail.contains("HTTPS"))
            assertEquals(2, server.requestCount)
            repeat(2) { assertNull(server.takeRequest().getHeader("X-LLM-Key")) }
            assertEquals("fixture-not-a-real-key", SecureStore(app).settings().apiKey)
        }
    }
    @Test fun busySaveIsExplicitRatherThanSilentlyDiscarded() = runBlocking {
        MockWebServer().use { server ->
            val vm = configured(server)
            server.enqueue(json(known).setBodyDelay(800, TimeUnit.MILLISECONDS))
            vm.savePaper("paper-1")
            vm.savePaper("paper-2")
            assertTrue(vm.state.value.message!!.contains("Vorgang läuft"))
            idle(vm); assertEquals(1, server.requestCount)
        }
    }
}
